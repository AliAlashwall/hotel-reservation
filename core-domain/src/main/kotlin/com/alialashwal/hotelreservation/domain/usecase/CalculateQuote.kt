package com.alialashwal.hotelreservation.domain.usecase

import com.alialashwal.hotelreservation.model.BookingQuote
import com.alialashwal.hotelreservation.model.Money
import com.alialashwal.hotelreservation.model.ValidStay
import java.math.BigDecimal
import javax.inject.Inject

/**
 * Turns a nightly rate and a validated stay into the money breakdown a guest confirms.
 *
 * Only accepts a [ValidStay], so there is no path where a quote is produced for
 * reversed dates or zero nights. The type does the guarding, not a runtime check.
 */
class CalculateQuote @Inject constructor() {

    operator fun invoke(
        nightlyRate: Money,
        stay: ValidStay,
        vatPercent: BigDecimal = VAT_PERCENT,
    ): BookingQuote {
        val base = nightlyRate * stay.nights * stay.rooms
        val vat = base.percentOf(vatPercent)
        return BookingQuote(
            nightlyRate = nightlyRate,
            nights = stay.nights,
            rooms = stay.rooms,
            baseAmount = base,
            vatRate = vatPercent,
            vatAmount = vat,
            total = base + vat,
        )
    }

    companion object {
        /** Expressed as a percentage rather than a fraction, so 15 reads as 15%. */
        val VAT_PERCENT: BigDecimal = BigDecimal("15")
    }
}
