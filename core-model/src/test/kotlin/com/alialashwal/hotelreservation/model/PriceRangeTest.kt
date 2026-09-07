package com.alialashwal.hotelreservation.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PriceRangeTest {

    @Test
    fun `both ends are inclusive`() {
        val band = PriceRange(min = 100, max = 200)
        assertTrue(band.contains(usd("100.00")))
        assertTrue(band.contains(usd("200.00")))
        assertFalse(band.contains(usd("99.99")))
        assertFalse(band.contains(usd("200.01")))
    }

    @Test
    fun `an open end does not bound that side`() {
        assertTrue(PriceRange(min = 100).contains(usd("99999.00")))
        assertFalse(PriceRange(min = 100).contains(usd("99.99")))
        assertTrue(PriceRange(max = 100).contains(usd("0.01")))
        assertFalse(PriceRange(max = 100).contains(usd("100.01")))
    }

    @Test
    fun `a hotel with no known price survives only while no band is set`() {
        // The rates call legitimately returns nothing for some hotels. Hiding those
        // by default would silently shrink the catalogue, so they stay until the user
        // actually asks for a price band, at which point we cannot claim they match.
        assertTrue(PriceRange.Unbounded.contains(null))
        assertFalse(PriceRange(min = 50).contains(null))
        assertFalse(PriceRange(max = 50).contains(null))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a reversed band is refused at construction`() {
        PriceRange(min = 200, max = 100)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a negative bound is refused at construction`() {
        PriceRange(min = -1)
    }

    private fun usd(amount: String) = Money.of(amount, "USD")
}
