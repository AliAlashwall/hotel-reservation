package com.alialashwal.hotelreservation.domain.usecase

import com.alialashwal.hotelreservation.model.StayError
import com.alialashwal.hotelreservation.model.StayRequest
import com.alialashwal.hotelreservation.model.ValidStay
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/**
 * Decides whether a set of dates and a room count can become a booking.
 *
 * Reports every problem it finds rather than only the first one, so a form can mark
 * all the bad fields at once instead of making the user fix them one round trip at a
 * time.
 *
 * Takes a [Clock] rather than calling [LocalDate.now] so "in the past" is testable
 * without waiting for midnight.
 */
class ValidateStay @Inject constructor(
    private val clock: Clock,
) {

    operator fun invoke(request: StayRequest): StayValidation {
        val errors = buildList {
            if (request.rooms < 1) add(StayError.RoomsBelowOne)
            if (request.rooms > MAX_ROOMS) add(StayError.TooManyRooms(MAX_ROOMS))

            val checkIn = request.checkIn
            val checkOut = request.checkOut

            if (checkIn == null) add(StayError.CheckInMissing)
            if (checkOut == null) add(StayError.CheckOutMissing)
            if (checkIn == null || checkOut == null) return@buildList

            if (checkIn.isBefore(LocalDate.now(clock))) add(StayError.CheckInInPast)

            // Covers both the reversed range and the same-day case the task names.
            if (!checkOut.isAfter(checkIn)) {
                add(StayError.CheckOutNotAfterCheckIn)
                return@buildList
            }

            if (nightsBetween(checkIn, checkOut) > MAX_NIGHTS) add(StayError.StayTooLong(MAX_NIGHTS))
        }

        if (errors.isNotEmpty()) return StayValidation.Invalid(errors)

        // Safe: the checks above already proved both dates are present and ordered.
        val checkIn = requireNotNull(request.checkIn)
        val checkOut = requireNotNull(request.checkOut)
        return StayValidation.Valid(
            ValidStay(
                checkIn = checkIn,
                checkOut = checkOut,
                nights = nightsBetween(checkIn, checkOut),
                rooms = request.rooms,
            )
        )
    }

    private fun nightsBetween(checkIn: LocalDate, checkOut: LocalDate): Int =
        java.time.temporal.ChronoUnit.DAYS.between(checkIn, checkOut).toInt()

    companion object {
        const val MAX_NIGHTS: Int = 30
        const val MAX_ROOMS: Int = 8
    }
}

sealed interface StayValidation {
    data class Valid(val stay: ValidStay) : StayValidation
    data class Invalid(val errors: List<StayError>) : StayValidation
}

val StayValidation.validStayOrNull: ValidStay?
    get() = (this as? StayValidation.Valid)?.stay

/** Empty when the stay is valid, so a form can bind to this without a branch. */
val StayValidation.allErrors: List<StayError>
    get() = (this as? StayValidation.Invalid)?.errors.orEmpty()
