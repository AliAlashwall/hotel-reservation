package com.alialashwal.hotelreservation.domain.usecase

import com.alialashwal.hotelreservation.domain.repository.BookingRepository
import com.alialashwal.hotelreservation.domain.repository.HotelRepository
import com.alialashwal.hotelreservation.model.AppError
import com.alialashwal.hotelreservation.model.Booking
import com.alialashwal.hotelreservation.model.BookingQuote
import com.alialashwal.hotelreservation.model.HotelDetail
import com.alialashwal.hotelreservation.model.Outcome
import com.alialashwal.hotelreservation.model.StayError
import com.alialashwal.hotelreservation.model.StayRequest
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

/**
 * The whole confirmation step: validate, re-price against the live rate, then persist.
 *
 * The re-pricing is the part worth explaining. The rate a guest sees while browsing is
 * an indicative nightly figure fetched for a probe window, not a quote for their dates.
 * Confirming without asking the API again would book a price the supplier never
 * offered. So this asks for the real rate for the real dates and, if the total moved,
 * refuses to book and hands both quotes back. The guest is shown what changed and
 * confirms again, or walks away. Silently charging the new price, or silently honouring
 * the stale one, are both worse.
 */
class ConfirmBooking @Inject constructor(
    private val hotels: HotelRepository,
    private val bookings: BookingRepository,
    private val validateStay: ValidateStay,
    private val calculateQuote: CalculateQuote,
    private val generateReference: GenerateBookingReference,
    private val clock: Clock,
) {

    suspend operator fun invoke(
        hotel: HotelDetail,
        request: StayRequest,
        /**
         * The quote the guest is looking at. Null on the first attempt, when there is
         * nothing to compare against yet. Non-null when they are re-confirming a price
         * change we already showed them, which is what lets the second attempt through.
         */
        acceptedQuote: BookingQuote? = null,
    ): ConfirmBookingResult {
        val stay = when (val validation = validateStay(request)) {
            is StayValidation.Invalid -> return ConfirmBookingResult.Invalid(validation.errors)
            is StayValidation.Valid -> validation.stay
        }

        val liveRate = when (val outcome = hotels.rateFor(hotel.id, stay)) {
            is Outcome.Failure -> return ConfirmBookingResult.Failed(outcome.error)
            is Outcome.Success -> outcome.value
        }

        val quote = calculateQuote(nightlyRate = liveRate, stay = stay)

        if (acceptedQuote != null && acceptedQuote.total != quote.total) {
            return ConfirmBookingResult.PriceChanged(previous = acceptedQuote, current = quote)
        }

        val booking = Booking(
            reference = generateReference(),
            hotelId = hotel.id,
            hotelName = hotel.name,
            hotelCity = hotel.city,
            hotelThumbnailUrl = hotel.imageUrls.firstOrNull(),
            checkIn = stay.checkIn,
            checkOut = stay.checkOut,
            rooms = stay.rooms,
            quote = quote,
            createdAt = Instant.now(clock),
        )
        bookings.save(booking)
        return ConfirmBookingResult.Confirmed(booking)
    }
}

sealed interface ConfirmBookingResult {

    data class Confirmed(val booking: Booking) : ConfirmBookingResult

    data class Invalid(val errors: List<StayError>) : ConfirmBookingResult

    /** The live rate no longer matches what the guest was shown. Needs a second confirm. */
    data class PriceChanged(val previous: BookingQuote, val current: BookingQuote) : ConfirmBookingResult

    data class Failed(val error: AppError) : ConfirmBookingResult
}
