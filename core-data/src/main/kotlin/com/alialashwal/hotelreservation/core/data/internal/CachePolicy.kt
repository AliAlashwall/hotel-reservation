package com.alialashwal.hotelreservation.core.data.internal

import java.time.Duration

/**
 * How long cached hotel data is trusted, and what the app does once it is not.
 *
 * Two separate windows, because "old enough to warn about" and "old enough to throw
 * away" are different questions.
 *
 * - Inside [STALE_AFTER] the cache is served silently.
 * - Past it, the cache is still served immediately, the list shows a "showing saved
 *   results" banner, and a refresh runs in the background. The user never waits on a
 *   spinner for data the app already has.
 * - Past [RESULT_SET_TTL] the result set is deleted outright, and any hotel no longer
 *   referenced by a surviving result set is evicted with it. Favourites are exempt.
 */
internal object CachePolicy {

    val STALE_AFTER: Duration = Duration.ofMinutes(15)

    val RESULT_SET_TTL: Duration = Duration.ofHours(24)

    /** Rows per network page. Large enough that scrolling rarely waits, small enough
     *  that the follow-up rates call for the page stays under a second. */
    const val PAGE_SIZE: Int = 20

    /**
     * List prices are quoted for a night this far ahead.
     *
     * A fixed lead is what makes prices comparable between rows: every hotel is priced
     * for the same night. Quoting "tonight" would compare a sold-out property against
     * an empty one. Booking re-prices for the guest's real date, which is where a
     * changed price is caught.
     */
    const val RATE_PROBE_LEAD_DAYS: Long = 30

    /** The app quotes one currency. Multi-currency is listed as future work in the README. */
    const val CURRENCY: String = "USD"
}
