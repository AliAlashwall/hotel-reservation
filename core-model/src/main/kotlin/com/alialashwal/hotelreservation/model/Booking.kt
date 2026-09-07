package com.alialashwal.hotelreservation.model

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/** The dates and room count a guest picked. Can hold an invalid combination on purpose,
 *  because the form must be able to render what the user typed before it is judged. */
data class StayRequest(
    val checkIn: LocalDate?,
    val checkOut: LocalDate?,
    val rooms: Int = 1,
)

/** Why a [StayRequest] cannot be turned into a booking. */
sealed interface StayError {
    data object CheckInMissing : StayError
    data object CheckOutMissing : StayError
    data object CheckInInPast : StayError

    /** Covers same-day and reversed ranges, which the task calls out explicitly. */
    data object CheckOutNotAfterCheckIn : StayError
    data class StayTooLong(val maxNights: Int) : StayError
    data class TooManyRooms(val maxRooms: Int) : StayError
    data object RoomsBelowOne : StayError
}

/** A [StayRequest] that has passed validation. Only this can be priced. */
data class ValidStay(
    val checkIn: LocalDate,
    val checkOut: LocalDate,
    val nights: Int,
    val rooms: Int,
)

/**
 * The full money breakdown shown before a guest confirms.
 *
 * Every line is stored rather than recomputed at render time, so the receipt on the
 * success screen is provably the same arithmetic the guest agreed to.
 */
data class BookingQuote(
    val nightlyRate: Money,
    val nights: Int,
    val rooms: Int,
    val baseAmount: Money,
    val vatRate: BigDecimal,
    val vatAmount: Money,
    val total: Money,
)

@JvmInline
value class BookingReference(val value: String)

data class Booking(
    val reference: BookingReference,
    val hotelId: String,
    val hotelName: String,
    val hotelCity: String,
    val hotelThumbnailUrl: String?,
    val checkIn: LocalDate,
    val checkOut: LocalDate,
    val rooms: Int,
    val quote: BookingQuote,
    val createdAt: Instant,
)
