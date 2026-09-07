package com.alialashwal.hotelreservation.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class HotelFiltersTest {

    @Test
    fun `the cache key ignores case and surrounding space in the query`() {
        // Otherwise "Hilton", "hilton " and "hilton" would each start their own paged
        // result set and refetch the same rows.
        assertEquals(
            HotelFilters(query = "Hilton").cacheKey,
            HotelFilters(query = "  hilton ").cacheKey,
        )
    }

    @Test
    fun `every filter dimension changes the cache key`() {
        val base = HotelFilters()
        assertNotEquals(base.cacheKey, base.copy(countryCode = "AE").cacheKey)
        assertNotEquals(base.cacheKey, base.copy(city = "Cairo").cacheKey)
        assertNotEquals(base.cacheKey, base.copy(query = "nile").cacheKey)
        assertNotEquals(base.cacheKey, base.copy(minReviewScore = 9.0).cacheKey)
        assertNotEquals(base.cacheKey, base.copy(priceRange = PriceRange(min = 50)).cacheKey)
    }

    @Test
    fun `separate fields cannot collide into the same key`() {
        // Guards against a naive concatenation where city "AB" plus empty query and
        // city "A" plus query "B" would produce the same string.
        assertNotEquals(
            HotelFilters(city = "AB", query = "").cacheKey,
            HotelFilters(city = "A", query = "B").cacheKey,
        )
    }
}
