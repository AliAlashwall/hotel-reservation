package com.alialashwal.hotelreservation.feature.booking

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alialashwal.hotelreservation.domain.repository.HotelRepository
import com.alialashwal.hotelreservation.domain.usecase.CalculateQuote
import com.alialashwal.hotelreservation.domain.usecase.ConfirmBooking
import com.alialashwal.hotelreservation.domain.usecase.ConfirmBookingResult
import com.alialashwal.hotelreservation.domain.usecase.StayValidation
import com.alialashwal.hotelreservation.domain.usecase.ValidateStay
import com.alialashwal.hotelreservation.domain.usecase.allErrors
import com.alialashwal.hotelreservation.model.HotelDetail
import com.alialashwal.hotelreservation.model.StayRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/**
 * The booking form.
 *
 * Holds no arithmetic of its own. Nights, base, VAT and total come from
 * [CalculateQuote], validation from [ValidateStay], and confirmation from
 * [ConfirmBooking]. That is what the "no business logic in Activities or Composables"
 * requirement means in practice: this class marshals input and renders results.
 */
@HiltViewModel
class BookingViewModel @Inject constructor(
    private val hotels: HotelRepository,
    private val validateStay: ValidateStay,
    private val calculateQuote: CalculateQuote,
    private val confirmBooking: ConfirmBooking,
    private val clock: Clock,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    private val hotelId: String = checkNotNull(savedState.get<String>(ARG_HOTEL_ID)) {
        "BookingRoute must carry a hotelId"
    }

    // The form lives in SavedStateHandle as epoch days and an int, so a guest who is
    // interrupted mid-booking comes back to the dates they had picked.
    private val checkInDay = savedState.getStateFlow<Long?>(KEY_CHECK_IN, null)
    private val checkOutDay = savedState.getStateFlow<Long?>(KEY_CHECK_OUT, null)
    private val rooms = savedState.getStateFlow(KEY_ROOMS, 1)

    private val submitStatus = MutableStateFlow(SubmitStatus())

    private val effectChannel = Channel<BookingEffect>(
        capacity = Channel.BUFFERED,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val effects: Flow<BookingEffect> = effectChannel.receiveAsFlow()

    val state: StateFlow<BookingState> = combine(
        hotels.observeDetail(hotelId),
        combine(checkInDay, checkOutDay, rooms) { checkIn, checkOut, roomCount ->
            StayRequest(
                checkIn = checkIn?.let(LocalDate::ofEpochDay),
                checkOut = checkOut?.let(LocalDate::ofEpochDay),
                rooms = roomCount,
            )
        },
        submitStatus,
    ) { hotel, request, status ->
        buildState(hotel, request, status)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        BookingState(hotelId = hotelId),
    )

    init {
        viewModelScope.launch {
            if (hotels.observeDetail(hotelId).first() == null) hotels.refreshDetail(hotelId)
        }
    }

    fun onIntent(intent: BookingIntent) {
        when (intent) {
            is BookingIntent.CheckInSelected -> {
                savedState[KEY_CHECK_IN] = intent.date.toEpochDay()
                // A check-out that is no longer after the new check-in would leave the
                // form in a state the guest did not create. Clearing it is less
                // surprising than silently moving it.
                val checkOut = checkOutDay.value?.let(LocalDate::ofEpochDay)
                if (checkOut != null && !checkOut.isAfter(intent.date)) {
                    savedState[KEY_CHECK_OUT] = null
                }
            }

            is BookingIntent.CheckOutSelected -> savedState[KEY_CHECK_OUT] = intent.date.toEpochDay()

            BookingIntent.RoomAdded ->
                savedState[KEY_ROOMS] = (rooms.value + 1).coerceAtMost(ValidateStay.MAX_ROOMS)

            BookingIntent.RoomRemoved ->
                savedState[KEY_ROOMS] = (rooms.value - 1).coerceAtLeast(1)

            BookingIntent.Confirm -> submit(acceptCurrentPrice = false)
            BookingIntent.AcceptNewPrice -> submit(acceptCurrentPrice = true)
            BookingIntent.DismissPriceChange -> submitStatus.update { it.copy(priceChange = null) }
        }
    }

    private fun submit(acceptCurrentPrice: Boolean) {
        if (submitStatus.value.isSubmitting) return

        // Pressing Confirm is what makes validation messages appear. Set before the
        // coroutine so the form marks its bad fields immediately.
        submitStatus.update { it.copy(showErrors = true) }

        viewModelScope.launch {
            // Everything is recomputed here from the saved-state inputs rather than read
            // out of `state`. `state` is shared WhileSubscribed, so its value is only
            // current while the screen is collecting it, and a confirmation must not
            // depend on who is watching.
            val request = currentRequest()
            val validation = validateStay(request)
            if (validation !is StayValidation.Valid) return@launch

            val hotel = hotels.observeDetail(hotelId).first() ?: return@launch
            val rate = hotel.nightlyRate ?: return@launch

            // On a retry after a price change, the quote the guest has now agreed to is
            // the one the dialog showed them. Captured before the state is cleared.
            val accepted = if (acceptCurrentPrice) {
                submitStatus.value.priceChange?.current ?: calculateQuote(rate, validation.stay)
            } else {
                calculateQuote(rate, validation.stay)
            }

            submitStatus.update { it.copy(isSubmitting = true, submitError = null, priceChange = null) }

            try {
                when (val result = confirmBooking(hotel, request, acceptedQuote = accepted)) {
                    is ConfirmBookingResult.Confirmed ->
                        effectChannel.send(BookingEffect.BookingConfirmed(result.booking.reference))

                    is ConfirmBookingResult.PriceChanged -> submitStatus.update {
                        it.copy(priceChange = PriceChange(result.previous, result.current))
                    }

                    is ConfirmBookingResult.Failed -> submitStatus.update {
                        it.copy(submitError = result.error)
                    }

                    // Cannot happen: the stay was validated above. Handled rather than
                    // ignored so a future change to ValidateStay cannot fail silently.
                    is ConfirmBookingResult.Invalid -> Unit
                }
            } finally {
                submitStatus.update { it.copy(isSubmitting = false) }
            }
        }
    }

    private fun currentRequest() = StayRequest(
        checkIn = checkInDay.value?.let(LocalDate::ofEpochDay),
        checkOut = checkOutDay.value?.let(LocalDate::ofEpochDay),
        rooms = rooms.value,
    )

    private fun buildState(
        hotel: HotelDetail?,
        request: StayRequest,
        status: SubmitStatus,
    ): BookingState {
        val validation = validateStay(request)
        val rate = hotel?.nightlyRate

        // A quote only exists once the stay is valid and a rate is known. The screen
        // shows the summary block only when this is non-null, so there is no path that
        // renders a total built from an invalid range.
        val quote = when {
            rate == null -> null
            validation is StayValidation.Valid -> calculateQuote(rate, validation.stay)
            else -> null
        }

        return BookingState(
            hotelId = hotelId,
            hotel = hotel,
            request = request,
            errors = validation.allErrors,
            showErrors = status.showErrors,
            quote = status.priceChange?.current ?: quote,
            isSubmitting = status.isSubmitting,
            submitError = status.submitError,
            priceChange = status.priceChange,
        )
    }

    /** The earliest date the pickers allow, so a past date cannot be chosen at all. */
    fun today(): LocalDate = LocalDate.now(clock)

    private data class SubmitStatus(
        val isSubmitting: Boolean = false,
        val showErrors: Boolean = false,
        val submitError: com.alialashwal.hotelreservation.model.AppError? = null,
        val priceChange: PriceChange? = null,
    )

    internal companion object {
        const val ARG_HOTEL_ID = "hotelId"
        const val STOP_TIMEOUT_MS = 5_000L

        private const val KEY_CHECK_IN = "checkInEpochDay"
        private const val KEY_CHECK_OUT = "checkOutEpochDay"
        private const val KEY_ROOMS = "rooms"
    }
}
