package com.alialashwal.hotelreservation.core.testing.fake

import com.alialashwal.hotelreservation.core.testing.hotelDetail
import com.alialashwal.hotelreservation.core.testing.hotelPage
import com.alialashwal.hotelreservation.domain.repository.HotelFeed
import com.alialashwal.hotelreservation.domain.repository.HotelRepository
import com.alialashwal.hotelreservation.model.AppError
import com.alialashwal.hotelreservation.model.HotelDetail
import com.alialashwal.hotelreservation.model.HotelFilters
import com.alialashwal.hotelreservation.model.Money
import com.alialashwal.hotelreservation.model.Outcome
import com.alialashwal.hotelreservation.model.ValidStay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.Instant

/**
 * An in-memory stand-in that behaves the way the real repository does: reads come from a
 * cache that writes update, so a test can drive a screen through refresh, append and
 * failure without a database or a server.
 */
class FakeHotelRepository : HotelRepository {

    private val feeds = MutableStateFlow<Map<String, HotelFeed>>(emptyMap())
    private val details = MutableStateFlow<Map<String, HotelDetail>>(emptyMap())

    /** How many rows exist per result set. Refresh takes the first page of these. */
    var catalogueSize: Int = 50
    var pageSize: Int = 20

    var refreshError: AppError? = null
    var appendError: AppError? = null
    var detailError: AppError? = null
    var rateResult: Outcome<Money> = Outcome.Success(Money.of("131.28", "USD"))

    var refreshCount: Int = 0
        private set
    var appendCount: Int = 0
        private set
    val refreshedFilters: MutableList<HotelFilters> = mutableListOf()

    override fun observeFeed(filters: HotelFilters): Flow<HotelFeed> =
        feeds.map { it[filters.cacheKey] ?: HotelFeed.empty() }

    override suspend fun refresh(filters: HotelFilters): Outcome<Unit> {
        refreshCount++
        refreshedFilters += filters
        refreshError?.let { return Outcome.Failure(it) }
        val matching = matching(filters)
        val loaded = minOf(pageSize, matching.size)
        putFeed(filters, matching.take(loaded), loaded, total = matching.size)
        return Outcome.Success(Unit)
    }

    override suspend fun loadNextPage(filters: HotelFilters): Outcome<Unit> {
        appendCount++
        appendError?.let { return Outcome.Failure(it) }
        val matching = matching(filters)
        val current = feeds.value[filters.cacheKey]?.hotels.orEmpty()
        if (current.size >= matching.size) return Outcome.Success(Unit)
        val next = minOf(current.size + pageSize, matching.size)
        putFeed(filters, matching.take(next), next, total = matching.size)
        return Outcome.Success(Unit)
    }

    override fun observeDetail(hotelId: String): Flow<HotelDetail?> = details.map { it[hotelId] }

    override suspend fun refreshDetail(hotelId: String): Outcome<Unit> {
        detailError?.let { return Outcome.Failure(it) }
        details.value = details.value + (hotelId to hotelDetail(id = hotelId))
        return Outcome.Success(Unit)
    }

    override suspend fun rateFor(hotelId: String, stay: ValidStay): Outcome<Money> = rateResult

    // ------------------------------------------------------- test-only helpers

    /**
     * Applies the search text the way the real catalogue does, so a test that types into
     * the search field actually sees the list narrow.
     */
    private fun matching(filters: HotelFilters): List<com.alialashwal.hotelreservation.model.HotelSummary> {
        val all = hotelPage(catalogueSize)
        val query = filters.query.trim()
        return if (query.isEmpty()) all else all.filter { it.name.contains(query, ignoreCase = true) }
    }

    /** Puts a result set straight into the cache, bypassing a network round trip. */
    fun seedFeed(filters: HotelFilters, hotels: List<com.alialashwal.hotelreservation.model.HotelSummary>) {
        putFeed(filters, hotels, hotels.size, total = hotels.size)
    }

    fun markStale(filters: HotelFilters) {
        val existing = feeds.value[filters.cacheKey] ?: return
        feeds.value = feeds.value + (filters.cacheKey to existing.copy(isStale = true))
    }

    fun seedDetail(detail: HotelDetail) {
        details.value = details.value + (detail.id to detail)
    }

    private fun putFeed(
        filters: HotelFilters,
        hotels: List<com.alialashwal.hotelreservation.model.HotelSummary>,
        loaded: Int,
        total: Int,
    ) {
        feeds.value = feeds.value + (
            filters.cacheKey to HotelFeed(
                hotels = hotels,
                isLastPage = loaded >= total,
                lastRefreshedAt = Instant.EPOCH,
                isStale = false,
            )
            )
    }
}
