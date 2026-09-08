package com.alialashwal.hotelreservation.core.ui

import com.alialashwal.hotelreservation.model.Money
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Formats an amount using the device locale's conventions for the currency it is in,
 * so a US device shows `$131.28` and a German one `131,28 $`.
 *
 * Falls back to `CODE 0.00` for a currency the platform does not recognise, rather than
 * throwing, because the currency string comes from a supplier response.
 */
fun Money.format(locale: Locale = Locale.getDefault()): String = runCatching {
    NumberFormat.getCurrencyInstance(locale).apply {
        this.currency = Currency.getInstance(this@format.currency)
        maximumFractionDigits = Money.SCALE
        minimumFractionDigits = Money.SCALE
    }.format(amount)
}.getOrElse { "$currency ${amount.toPlainString()}" }
