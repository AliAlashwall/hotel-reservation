package com.alialashwal.hotelreservation.domain.repository

import com.alialashwal.hotelreservation.model.HotelDetail
import com.alialashwal.hotelreservation.model.HotelFilters
import com.alialashwal.hotelreservation.model.HotelSummary
import com.alialashwal.hotelreservation.model.Money
import com.alialashwal.hotelreservation.model.Outcome
import com.alialashwal.hotelreservation.model.ValidStay
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * What the app can ask about hotels. Deliberately says nothing about HTTP, Room, or
 * paging libraries, so the data source can be replaced without touching a screen.
 *
 * Reads and writes are split on purpose. [observeFeed] and [observeDetail] never fail
 * and never block: they read the local cache, which is the single source of truth.
 * [refresh], [loadNextPage] and [refreshDetail] are the only calls that touch the
 * network, and they are the only ones that return an [Outcome]. A screen therefore
 * always has something to render, even while a refresh is failing.
 */
interface HotelRepository {

    /** Cached hotels for these filters, re-emitted whenever the cache changes. */
    fun observeFeed(filters: HotelFilters): Flow<HotelFeed>

    /** Replaces the cached result set for these filters with a fresh first page. */
    suspend fun refresh(filters: HotelFilters): Outcome<Unit>

    /**
     * Appends the next page. Safe to call repeatedly: overlapping calls for the same
     * filters collapse into one, and a call past the end of the result set does nothing.
     */
    suspend fun loadNextPage(filters: HotelFilters): Outcome<Unit>

    fun observeDetail(hotelId: String): Flow<HotelDetail?>

    suspend fun refreshDetail(hotelId: String): Outcome<Unit>

    /**
     * The live rate for a specific stay, as opposed to the indicative nightly rate
     * shown in a list row. Used at confirmation time to catch a price that moved
     * between browsing and booking.
     */
    suspend fun rateFor(hotelId: String, stay: ValidStay): Outcome<Money>
}

/**
 * Everything a list screen needs in one value, so the rows, the end-of-list marker and
 * the staleness banner can never disagree with each other.
 */
data class HotelFeed(
    val hotels: List<HotelSummary>,
    val isLastPage: Boolean,
    /** When this result set was last written from the network. Null means never. */
    val lastRefreshedAt: Instant?,
    /** True when what is on screen came from cache older than the refresh window. */
    val isStale: Boolean,
) {
    val isEmpty: Boolean get() = hotels.isEmpty()

    /** Null when nothing has ever been cached, which is what separates "empty result" from "no data yet". */
    val hasCachedData: Boolean get() = lastRefreshedAt != null

    companion object {
        fun empty(): HotelFeed = HotelFeed(
            hotels = emptyList(),
            isLastPage = false,
            lastRefreshedAt = null,
            isStale = false,
        )
    }
}
