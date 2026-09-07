package com.alialashwal.hotelreservation.domain.usecase

import com.alialashwal.hotelreservation.domain.fake.FakeBookingRepository
import com.alialashwal.hotelreservation.domain.fake.FakeHotelRepository
import com.alialashwal.hotelreservation.domain.fake.hotelDetail
import com.alialashwal.hotelreservation.model.AppError
import com.alialashwal.hotelreservation.model.Money
import com.alialashwal.hotelreservation.model.Outcome
import com.alialashwal.hotelreservation.model.StayError
import com.alialashwal.hotelreservation.model.StayRequest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.random.Random

class ConfirmBookingTest {

    private val today = LocalDate.of(2026, 9, 8)
    private val clock = Clock.fixed(Instant.parse("2026-09-08T09:00:00Z"), ZoneOffset.UTC)

    private val hotels = FakeHotelRepository()
    private val bookings = FakeBookingRepository()

    private val confirm = ConfirmBooking(
        hotels = hotels,
        bookings = bookings,
        validateStay = ValidateStay(clock),
        calculateQuote = CalculateQuote(),
        generateReference = GenerateBookingReference(clock, Random(1)),
        clock = clock,
    )

    @Test
    fun `a valid request is priced from the live rate and persisted`() = runTest {
        hotels.rateResult = Outcome.Success(usd("200.00"))

        val result = confirm(hotelDetail(), request(nights = 2, rooms = 1))

        val booking = assertIs<ConfirmBookingResult.Confirmed>(result).booking
        assertEquals(usd("400.00"), booking.quote.baseAmount)
        assertEquals(usd("60.00"), booking.quote.vatAmount)
        assertEquals(usd("460.00"), booking.quote.total)
        assertEquals(listOf(booking), bookings.saved)
    }

    @Test
    fun `the live rate is requested for the guest's own dates, not the browsing window`() = runTest {
        confirm(hotelDetail(id = "lp19ea6"), request(nights = 4, rooms = 3))

        val (hotelId, stay) = hotels.rateRequests.single()
        assertEquals("lp19ea6", hotelId)
        assertEquals(today, stay.checkIn)
        assertEquals(today.plusDays(4), stay.checkOut)
        assertEquals(3, stay.rooms)
    }

    @Test
    fun `invalid dates are refused before any network call is made`() = runTest {
        val result = confirm(hotelDetail(), StayRequest(checkIn = today, checkOut = today))

        assertEquals(
            listOf(StayError.CheckOutNotAfterCheckIn),
            assertIs<ConfirmBookingResult.Invalid>(result).errors,
        )
        assertTrue("no rate should be fetched for an invalid stay", hotels.rateRequests.isEmpty())
        assertTrue(bookings.saved.isEmpty())
    }

    @Test
    fun `a failed rate call surfaces the error and books nothing`() = runTest {
        hotels.rateResult = Outcome.Failure(AppError.NoConnection)

        val result = confirm(hotelDetail(), request(nights = 2, rooms = 1))

        assertEquals(AppError.NoConnection, assertIs<ConfirmBookingResult.Failed>(result).error)
        assertTrue(bookings.saved.isEmpty())
    }

    @Test
    fun `a price that moved since the guest looked blocks the booking`() = runTest {
        // The guest is holding a quote built from 200.00 a night. The supplier now says 250.00.
        hotels.rateResult = Outcome.Success(usd("200.00"))
        val held = assertIs<ConfirmBookingResult.Confirmed>(
            confirm(hotelDetail(), request(nights = 2, rooms = 1))
        ).booking.quote
        bookings.clearForTest()

        hotels.rateResult = Outcome.Success(usd("250.00"))
        val result = confirm(hotelDetail(), request(nights = 2, rooms = 1), acceptedQuote = held)

        val changed = assertIs<ConfirmBookingResult.PriceChanged>(result)
        assertEquals(usd("460.00"), changed.previous.total)
        assertEquals(usd("575.00"), changed.current.total)
        assertTrue("nothing may be booked at a price the guest has not seen", bookings.saved.isEmpty())
    }

    @Test
    fun `re-confirming the new price goes through`() = runTest {
        hotels.rateResult = Outcome.Success(usd("250.00"))
        val newQuote = CalculateQuote()(usd("250.00"), stayOf(nights = 2, rooms = 1))

        val result = confirm(hotelDetail(), request(nights = 2, rooms = 1), acceptedQuote = newQuote)

        assertEquals(usd("575.00"), assertIs<ConfirmBookingResult.Confirmed>(result).booking.quote.total)
        assertEquals(1, bookings.saved.size)
    }

    @Test
    fun `an unchanged price does not interrupt the guest`() = runTest {
        hotels.rateResult = Outcome.Success(usd("200.00"))
        val held = CalculateQuote()(usd("200.00"), stayOf(nights = 2, rooms = 1))

        val result = confirm(hotelDetail(), request(nights = 2, rooms = 1), acceptedQuote = held)

        assertIs<ConfirmBookingResult.Confirmed>(result)
    }

    private fun request(nights: Int, rooms: Int) =
        StayRequest(checkIn = today, checkOut = today.plusDays(nights.toLong()), rooms = rooms)

    private fun stayOf(nights: Int, rooms: Int) =
        requireNotNull(ValidateStay(clock)(request(nights, rooms)).validStayOrNull)

    private fun usd(amount: String) = Money.of(amount, "USD")

    private inline fun <reified T> assertIs(value: Any?): T {
        assertTrue("expected ${T::class.simpleName} but got $value", value is T)
        return value as T
    }
}
