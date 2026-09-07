package com.alialashwal.hotelreservation.model

/**
 * Everything that narrows the hotel list. Held as one value so that search text and
 * filters can never drift apart, and so a change to any part is a single state update.
 */
data class HotelFilters(
    val countryCode: String = DEFAULT_COUNTRY_CODE,
    val city: String? = null,
    val query: String = "",
    val minReviewScore: Double? = null,
    val priceRange: PriceRange = PriceRange.Unbounded,
) {
    /**
     * Identifies the result set these filters describe.
     *
     * The cache is keyed by this, so changing any filter starts a separate paged
     * result set rather than appending to the previous one.
     */
    val cacheKey: String
        get() = listOf(
            countryCode,
            city.orEmpty(),
            query.trim().lowercase(),
            minReviewScore?.toString().orEmpty(),
            priceRange.min?.toString().orEmpty(),
            priceRange.max?.toString().orEmpty(),
        ).joinToString(separator = "|")

    companion object {
        const val DEFAULT_COUNTRY_CODE: String = "EG"
    }
}

/**
 * A price band in whole currency units, both ends inclusive and both optional.
 *
 * Applied in our own code, not by the API. The catalogue endpoint accepts `minPrice`
 * and `maxPrice` but ignores them, so filtering has to happen after the rates call
 * returns. See the README for what that costs.
 */
data class PriceRange(val min: Int? = null, val max: Int? = null) {

    init {
        require(min == null || min >= 0) { "min must not be negative" }
        require(max == null || max >= 0) { "max must not be negative" }
        require(min == null || max == null || min <= max) { "min must not exceed max" }
    }

    val isUnbounded: Boolean get() = min == null && max == null

    fun contains(money: Money?): Boolean {
        // A hotel with no known price is kept while the band is unbounded and dropped
        // once the user actually asks for a band, because we cannot claim it matches.
        if (money == null) return isUnbounded
        if (min != null && money.amount < min.toBigDecimal()) return false
        if (max != null && money.amount > max.toBigDecimal()) return false
        return true
    }

    companion object {
        val Unbounded = PriceRange()
    }
}
