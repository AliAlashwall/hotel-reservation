package com.alialashwal.hotelreservation.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class MoneyTest {

    @Test
    fun `amounts are normalised to two places so equal values compare equal`() {
        // Raw BigDecimal would call these different. Money must not.
        assertEquals(Money.of("1.50", USD), Money.of("1.5", USD))
        assertEquals(Money.of("1.50", USD), Money.of(1.5, USD))
    }

    @Test
    fun `a third of a cent rounds half up, not toward zero`() {
        assertEquals(Money.of("0.13", USD), Money.of("0.125", USD))
        assertEquals(Money.of("0.12", USD), Money.of("0.124", USD))
    }

    @Test
    fun `adding keeps the currency and the scale`() {
        assertEquals(Money.of("300.00", USD), Money.of("131.28", USD) + Money.of("168.72", USD))
    }

    @Test
    fun `multiplying by a night count is exact`() {
        // The failure this guards: 131.29 * 3 as a Double is 393.87000000000006.
        assertEquals(Money.of("393.87", USD), Money.of("131.29", USD) * 3)
    }

    @Test
    fun `fifteen percent of a real rate rounds to the cent`() {
        // 262.57 * 0.15 = 39.3855, which must present as 39.39.
        assertEquals(Money.of("39.39", USD), Money.of("262.57", USD).percentOf(VAT))
    }

    @Test
    fun `percent is taken on the exact amount, then rounded once`() {
        // Rounding the intermediate to 2 places first would give 0.02 here, not 0.03.
        assertEquals(Money.of("0.03", USD), Money.of("0.17", USD).percentOf(BigDecimal("15")))
    }

    @Test
    fun `zero percent is zero and one hundred percent is the whole amount`() {
        val amount = Money.of("742.10", USD)
        assertEquals(Money.zero(USD), amount.percentOf(BigDecimal.ZERO))
        assertEquals(amount, amount.percentOf(BigDecimal("100")))
    }

    @Test
    fun `comparison orders by amount`() {
        assertTrue(Money.of("10.00", USD) < Money.of("10.01", USD))
        assertEquals(0, Money.of("10.00", USD).compareTo(Money.of("10.000", USD)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `adding across currencies is refused rather than silently wrong`() {
        Money.of("10.00", USD) + Money.of("10.00", "EUR")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a blank currency is refused`() {
        Money.of("10.00", " ")
    }

    private companion object {
        const val USD = "USD"
        val VAT = BigDecimal("15")
    }
}
