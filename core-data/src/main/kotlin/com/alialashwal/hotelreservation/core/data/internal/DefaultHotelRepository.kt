package com.alialashwal.hotelreservation.core.data.internal

import com.alialashwal.hotelreservation.core.database.dao.HotelDao
import com.alialashwal.hotelreservation.core.database.entity.ResultSetEntity
import com.alialashwal.hotelreservation.core.database.entity.ResultSetEntryEntity
import com.alialashwal.hotelreservation.core.network.HotelRemoteDataSource
import com.alialashwal.hotelreservation.domain.repository.HotelFeed
import com.alialashwal.hotelreservation.domain.repository.HotelRepository
import com.alialashwal.hotelreservation.model.AppError
import com.alialashwal.hotelreservation.model.HotelDetail
import com.alialashwal.hotelreservation.model.HotelFilters
import com.alialashwal.hotelreservation.model.HotelSummary
import com.alialashwal.hotelreservation.model.Money
import com.alialashwal.hotelreservation.model.Outcome
import com.alialashwal.hotelreservation.model.ValidStay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cache-first, network-second.
 *
 * Reads never touch the network and never fail: [observeFeed] is a query over Room, so
 * a screen always has something to draw. Writes are the only calls that reach the API,
 * and only they can return a failure. That split is what makes "show the cache, then
 * tell the user the refresh failed" the natural shape rather than a special case.
 */
@Singleton
class DefaultHotelRepository @Inject constructor(
    private val remote: HotelRemoteDataSource,
    private val dao: HotelDao,
    private val clock: Clock,
) : HotelRepository {

    /**
     * One lock per result set, so a search and a scroll on different filter sets do not
     * block each other while still being serialised against themselves.
     *
     * Held with `tryLock`, never `withLock`. A second request that arrives while one is
     * in flight for the same filters is dropped, not queued. Queueing would answer the
     * "no duplicate pagination requests" requirement on paper and still fire two calls;
     * dropping actually fires one.
     */
    private val locks = ConcurrentHashMap<String, Mutex>()

    override fun observeFeed(filters: HotelFilters): Flow<HotelFeed> {
        val key = filters.cacheKey
        return combine(
            dao.observeResultSet(key),
            dao.observeResultSetMeta(key),
        ) { rows, meta ->
            HotelFeed(
                hotels = rows.map { it.toDomain() },
                // The end is decided by what the server said the set holds, compared
                // against how many rows it has handed over. The visible row count is
                // smaller whenever the price band dropped something, and using it here
                // would stop paging early.
                isLastPage = meta != null && meta.loadedCount >= meta.total,
                lastRefreshedAt = meta?.let { Instant.ofEpochMilli(it.lastRefreshedAt) },
                isStale = meta != null && isStale(meta.lastRefreshedAt),
            )
        }.distinctUntilChanged()
    }

    override suspend fun refresh(filters: HotelFilters): Outcome<Unit> =
        withResultSetLock(filters.cacheKey) {
            evictExpired()
            loadPage(filters, offset = 0, replaceExisting = true)
        }

    override suspend fun loadNextPage(filters: HotelFilters): Outcome<Unit> =
        withResultSetLock(filters.cacheKey) {
            val meta = dao.resultSetMeta(filters.cacheKey)
                // Nothing cached yet, so "next page" means the first one.
                ?: return@withResultSetLock loadPage(filters, offset = 0, replaceExisting = true)

            if (meta.loadedCount >= meta.total) {
                // Already at the end. Not a failure, just nothing to do.
                return@withResultSetLock Outcome.Success(Unit)
            }
            loadPage(filters, offset = meta.loadedCount, replaceExisting = false)
        }

    override fun observeDetail(hotelId: String): Flow<HotelDetail?> =
        combine(
            dao.observeHotel(hotelId),
            dao.observeHotelDetail(hotelId),
        ) { summary, detail ->
            if (summary == null || detail == null) null else detail.toDomain(summary)
        }.distinctUntilChanged()

    override suspend fun refreshDetail(hotelId: String): Outcome<Unit> {
        val detail = when (val outcome = remote.hotelDetail(hotelId)) {
            is Outcome.Failure -> return outcome
            is Outcome.Success -> outcome.value
        }

        // Best effort. A hotel with no rate is still worth showing, so a failed price
        // lookup must not fail the whole screen.
        val rate = remote
            .nightlyRates(listOf(hotelId), probeDate(), CachePolicy.CURRENCY)
            .valueOrNull
            ?.get(hotelId)

        val now = clock.millis()
        val priced = detail.copy(nightlyRate = rate ?: detail.nightlyRate)
        dao.upsertHotelsPreservingFavorites(listOf(priced.toSummaryEntity(now)))
        dao.upsertHotelDetail(priced.toDetailEntity(now))
        return Outcome.Success(Unit)
    }

    override suspend fun rateFor(hotelId: String, stay: ValidStay): Outcome<Money> {
        val rates = when (val outcome = remote.nightlyRates(listOf(hotelId), stay.checkIn, CachePolicy.CURRENCY)) {
            is Outcome.Failure -> return outcome
            is Outcome.Success -> outcome.value
        }
        return rates[hotelId]
            ?.let { Outcome.Success(it) }
            ?: Outcome.Failure(AppError.NoAvailability)
    }

    // ------------------------------------------------------------------ paging

    private suspend fun loadPage(
        filters: HotelFilters,
        offset: Int,
        replaceExisting: Boolean,
    ): Outcome<Unit> {
        val page = when (val outcome = remote.hotels(filters, offset, CachePolicy.PAGE_SIZE)) {
            is Outcome.Failure -> return outcome
            is Outcome.Success -> outcome.value
        }

        val priced = attachPrices(page.items)

        // The API accepts minPrice and maxPrice and then ignores them, so the band is
        // applied here. Rows that fall outside it are simply not cached; the offset
        // still advances by the full page the server sent, which is why loadedCount
        // and the visible row count are tracked separately.
        val matching = priced.filter { filters.priceRange.contains(it.nightlyRate) }

        val now = clock.millis()
        dao.insertPage(
            resultSet = ResultSetEntity(
                cacheKey = filters.cacheKey,
                total = page.total,
                loadedCount = page.nextOffset,
                lastRefreshedAt = now,
            ),
            hotels = matching.map { it.toEntity(now) },
            entries = matching.mapIndexed { index, hotel ->
                ResultSetEntryEntity(filters.cacheKey, hotel.id, position = offset + index)
            },
            replaceExisting = replaceExisting,
        )
        return Outcome.Success(Unit)
    }

    /**
     * Prices come from a second endpoint, so a page is two calls.
     *
     * A failure here is swallowed on purpose: names, photos and ratings are still worth
     * showing, and the row renders "price unavailable" rather than the whole list
     * collapsing into an error because the rates service was slow.
     */
    private suspend fun attachPrices(hotels: List<HotelSummary>): List<HotelSummary> {
        if (hotels.isEmpty()) return hotels
        val rates = remote
            .nightlyRates(hotels.map { it.id }, probeDate(), CachePolicy.CURRENCY)
            .valueOrNull
            .orEmpty()
        return hotels.map { it.copy(nightlyRate = rates[it.id]) }
    }

    private suspend fun withResultSetLock(
        cacheKey: String,
        block: suspend () -> Outcome<Unit>,
    ): Outcome<Unit> {
        val lock = locks.getOrPut(cacheKey) { Mutex() }
        // Dropped, not queued. The call already running will write the same page.
        if (!lock.tryLock()) return Outcome.Success(Unit)
        return try {
            block()
        } finally {
            lock.unlock()
        }
    }

    // ------------------------------------------------------------------ policy

    private suspend fun evictExpired() {
        val cutoff = clock.millis() - CachePolicy.RESULT_SET_TTL.toMillis()
        dao.evictResultSetsOlderThan(cutoff)
        dao.evictUnreferencedHotels()
    }

    private fun isStale(lastRefreshedAt: Long): Boolean =
        clock.millis() - lastRefreshedAt > CachePolicy.STALE_AFTER.toMillis()

    private fun probeDate(): LocalDate =
        LocalDate.now(clock).plusDays(CachePolicy.RATE_PROBE_LEAD_DAYS)
}
