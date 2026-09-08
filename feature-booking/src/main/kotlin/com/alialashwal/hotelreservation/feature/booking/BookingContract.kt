package com.alialashwal.hotelreservation.feature.booking

import com.alialashwal.hotelreservation.model.AppError
import com.alialashwal.hotelreservation.model.BookingQuote
import com.alialashwal.hotelreservation.model.BookingReference
import com.alialashwal.hotelreservation.model.HotelDetail
import com.alialashwal.hotelreservation.model.StayError
import com.alialashwal.hotelreservation.model.StayRequest
import java.time.LocalDate

data class BookingState(
    val hotelId: String = "",
    val hotel: HotelDetail? = null,
    val request: StayRequest = StayRequest(checkIn = null, checkOut = null, rooms = 1),

    /**
     * Validation problems for what is currently in the form.
     *
     * Empty until the user has touched the field in question or pressed Confirm, so the
     * screen does not open already shouting about a date nobody has chosen yet.
     */
    val errors: List<StayError> = emptyList(),
    val showErrors: Boolean = false,

    /** The running total, recomputed on every change so the guest sees the effect at once. */
    val quote: BookingQuote? = null,

    val isSubmitting: Boolean = false,
    val submitError: AppError? = null,

    /** Set when the live rate moved after the guest had already seen a total. */
    val priceChange: PriceChange? = null,
) {
    val canSubmit: Boolean
        get() = !isSubmitting && quote != null && errors.isEmpty()

    fun errorFor(vararg kinds: Class<out StayError>): StayError? =
        if (!showErrors) null else errors.firstOrNull { error -> kinds.any { it.isInstance(error) } }
}

data class PriceChange(
    val previous: BookingQuote,
    val current: BookingQuote,
)

sealed interface BookingIntent {
    data class CheckInSelected(val date: LocalDate) : BookingIntent
    data class CheckOutSelected(val date: LocalDate) : BookingIntent
    data object RoomAdded : BookingIntent
    data object RoomRemoved : BookingIntent
    data object Confirm : BookingIntent
    /** Confirm again, this time accepting the rate the supplier just quoted. */
    data object AcceptNewPrice : BookingIntent
    data object DismissPriceChange : BookingIntent
}

sealed interface BookingEffect {
    data class BookingConfirmed(val reference: BookingReference) : BookingEffect
}
