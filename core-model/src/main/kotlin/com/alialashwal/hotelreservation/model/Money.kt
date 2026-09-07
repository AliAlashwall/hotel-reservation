package com.alialashwal.hotelreservation.model

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * An amount of money in a single currency.
 *
 * Backed by [BigDecimal] rather than [Double] on purpose. The booking screen adds
 * 15% VAT to a base that is itself a rate multiplied by nights and rooms, and
 * binary floating point drifts across that chain. A guest who sees 262.57 must be
 * charged 262.57.
 *
 * Every amount is normalised to two decimal places using [RoundingMode.HALF_UP],
 * so equality and hashing behave the way a reader expects: `Money("1.50")` equals
 * `Money("1.5")`, which raw `BigDecimal` equality would deny.
 */
data class Money(val amount: BigDecimal, val currency: String) : Comparable<Money> {

    init {
        require(currency.isNotBlank()) { "currency must not be blank" }
    }

    operator fun plus(other: Money): Money = of(amount + other.requireSameCurrency(this).amount, currency)

    operator fun times(multiplier: Int): Money = of(amount * multiplier.toBigDecimal(), currency)

    /** The given percentage of this amount. `percentOf(15)` on 100.00 is 15.00. */
    fun percentOf(percent: BigDecimal): Money =
        of(amount * percent.divide(HUNDRED, PERCENT_SCALE, RoundingMode.HALF_UP), currency)

    override fun compareTo(other: Money): Int = amount.compareTo(other.requireSameCurrency(this).amount)

    private fun requireSameCurrency(other: Money): Money = also {
        require(currency == other.currency) {
            "cannot combine $currency with ${other.currency}"
        }
    }

    companion object {
        const val SCALE: Int = 2

        private val HUNDRED = BigDecimal("100")

        /** Extra digits kept while computing a percentage, before the result is rounded once. */
        private const val PERCENT_SCALE = 10

        fun of(amount: BigDecimal, currency: String): Money =
            Money(amount.setScale(SCALE, RoundingMode.HALF_UP), currency)

        fun of(amount: Double, currency: String): Money =
            of(BigDecimal.valueOf(amount), currency)

        fun of(amount: String, currency: String): Money =
            of(BigDecimal(amount), currency)

        fun zero(currency: String): Money = of(BigDecimal.ZERO, currency)
    }
}
