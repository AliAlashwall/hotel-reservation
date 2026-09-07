package com.alialashwal.hotelreservation.domain.usecase

import com.alialashwal.hotelreservation.model.StayError
import com.alialashwal.hotelreservation.model.StayRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class ValidateStayTest {

    // Frozen so "today" never moves under the test.
    private val today = LocalDate.of(2026, 9, 8)
    private val clock = Clock.fixed(Instant.parse("2026-09-08T09:00:00Z"), ZoneOffset.UTC)
    private val validate = ValidateStay(clock)

    @Test
    fun `a normal forward range is valid and counts nights, not days`() {
        val result = validate(request(today, today.plusDays(3)))

        val stay = assertValid(result)
        assertEquals(3, stay.nights)
        assertEquals(1, stay.rooms)
    }

    @Test
    fun `check-out on the same day as check-in is rejected`() {
        // Named explicitly in the task: check-out on or before check-in is invalid.
        assertEquals(
            listOf(StayError.CheckOutNotAfterCheckIn),
            validate(request(today, today)).errorsOf(),
        )
    }

    @Test
    fun `check-out before check-in is rejected`() {
        assertEquals(
            listOf(StayError.CheckOutNotAfterCheckIn),
            validate(request(today.plusDays(5), today.plusDays(2))).errorsOf(),
        )
    }

    @Test
    fun `check-in today is allowed, yesterday is not`() {
        assertTrue(validate(request(today, today.plusDays(1))) is StayValidation.Valid)
        assertTrue(StayError.CheckInInPast in validate(request(today.minusDays(1), today.plusDays(1))).errorsOf())
    }

    @Test
    fun `a missing date is reported as missing rather than as a bad range`() {
        assertEquals(listOf(StayError.CheckInMissing), validate(request(null, today.plusDays(2))).errorsOf())
        assertEquals(listOf(StayError.CheckOutMissing), validate(request(today, null)).errorsOf())

        val bothMissing = validate(request(null, null)).errorsOf()
        assertEquals(listOf(StayError.CheckInMissing, StayError.CheckOutMissing), bothMissing)
    }

    @Test
    fun `room count must be at least one and at most the cap`() {
        assertEquals(listOf(StayError.RoomsBelowOne), validate(request(today, today.plusDays(1), rooms = 0)).errorsOf())
        assertEquals(
            listOf(StayError.TooManyRooms(ValidateStay.MAX_ROOMS)),
            validate(request(today, today.plusDays(1), rooms = ValidateStay.MAX_ROOMS + 1)).errorsOf(),
        )
        assertTrue(validate(request(today, today.plusDays(1), rooms = ValidateStay.MAX_ROOMS)) is StayValidation.Valid)
    }

    @Test
    fun `the maximum stay length is inclusive`() {
        val maxNights = ValidateStay.MAX_NIGHTS
        assertTrue(validate(request(today, today.plusDays(maxNights.toLong()))) is StayValidation.Valid)
        assertTrue(
            StayError.StayTooLong(maxNights) in
                validate(request(today, today.plusDays(maxNights + 1L))).errorsOf(),
        )
    }

    @Test
    fun `every problem is reported at once, not just the first`() {
        // A form should be able to mark all bad fields in one pass.
        val errors = validate(request(today.minusDays(2), today.minusDays(2), rooms = 0)).errorsOf()

        assertTrue(StayError.RoomsBelowOne in errors)
        assertTrue(StayError.CheckInInPast in errors)
        assertTrue(StayError.CheckOutNotAfterCheckIn in errors)
    }

    private fun request(checkIn: LocalDate?, checkOut: LocalDate?, rooms: Int = 1) =
        StayRequest(checkIn = checkIn, checkOut = checkOut, rooms = rooms)

    private fun StayValidation.errorsOf(): List<StayError> = allErrors

    private fun assertValid(result: StayValidation) =
        requireNotNull(result.validStayOrNull) { "expected a valid stay but got $result" }
}
