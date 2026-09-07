package com.alialashwal.hotelreservation.domain.usecase

import com.alialashwal.hotelreservation.model.Money
import com.alialashwal.hotelreservation.model.ValidStay
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class CalculateQuoteTest {

    private val calculate = CalculateQuote()

    @Test
    fun `base is rate times nights times rooms, and VAT is fifteen percent on top`() {
        val quote = calculate(nightlyRate = usd("100.00"), stay = stay(nights = 3, rooms = 2))

        assertEquals(usd("600.00"), quote.baseAmount)
        assertEquals(usd("90.00"), quote.vatAmount)
        assertEquals(usd("690.00"), quote.total)
        assertEquals(3, quote.nights)
        assertEquals(2, quote.rooms)
    }

    @Test
    fun `a single night in a single room is the rate plus its VAT`() {
        val quote = calculate(nightlyRate = usd("262.57"), stay = stay(nights = 1, rooms = 1))

        assertEquals(usd("262.57"), quote.baseAmount)
        assertEquals(usd("39.39"), quote.vatAmount)   // 39.3855 rounds up
        assertEquals(usd("301.96"), quote.total)
    }

    @Test
    fun `VAT is taken on the multiplied base, not per night`() {
        // Rounding VAT per night and summing would give 39.39 * 3 = 118.17 here.
        // Taking it once on 787.71 gives 118.16. The second is the correct total.
        val quote = calculate(nightlyRate = usd("262.57"), stay = stay(nights = 3, rooms = 1))

        assertEquals(usd("787.71"), quote.baseAmount)
        assertEquals(usd("118.16"), quote.vatAmount)
        assertEquals(usd("905.87"), quote.total)
    }

    @Test
    fun `total always equals base plus VAT exactly`() {
        listOf("0.01", "1.99", "33.33", "262.57", "999.95").forEach { rate ->
            (1..4).forEach { nights ->
                (1..3).forEach { rooms ->
                    val quote = calculate(usd(rate), stay(nights, rooms))
                    assertEquals(
                        "rate=$rate nights=$nights rooms=$rooms",
                        quote.baseAmount + quote.vatAmount,
                        quote.total,
                    )
                }
            }
        }
    }

    @Test
    fun `the VAT rate is carried on the quote so a receipt can state it`() {
        assertEquals(BigDecimal("15"), calculate(usd("10.00"), stay(1, 1)).vatRate)
    }

    @Test
    fun `a different VAT rate is honoured`() {
        val quote = calculate(usd("100.00"), stay(1, 1), vatPercent = BigDecimal("20"))

        assertEquals(usd("20.00"), quote.vatAmount)
        assertEquals(usd("120.00"), quote.total)
    }

    @Test
    fun `the currency of the rate carries through every line`() {
        val quote = calculate(Money.of("50.00", "EUR"), stay(2, 1))

        assertEquals("EUR", quote.baseAmount.currency)
        assertEquals("EUR", quote.vatAmount.currency)
        assertEquals("EUR", quote.total.currency)
    }

    private fun usd(amount: String) = Money.of(amount, "USD")

    private fun stay(nights: Int, rooms: Int): ValidStay {
        val checkIn = LocalDate.of(2026, 9, 8)
        return ValidStay(
            checkIn = checkIn,
            checkOut = checkIn.plusDays(nights.toLong()),
            nights = nights,
            rooms = rooms,
        )
    }
}
