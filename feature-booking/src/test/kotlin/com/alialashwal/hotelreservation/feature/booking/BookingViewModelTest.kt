package com.alialashwal.hotelreservation.feature.booking

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.alialashwal.hotelreservation.core.testing.MainDispatcherRule
import com.alialashwal.hotelreservation.core.testing.fake.FakeBookingRepository
import com.alialashwal.hotelreservation.core.testing.fake.FakeHotelRepository
import com.alialashwal.hotelreservation.core.testing.hotelDetail
import com.alialashwal.hotelreservation.domain.usecase.CalculateQuote
import com.alialashwal.hotelreservation.domain.usecase.ConfirmBooking
import com.alialashwal.hotelreservation.domain.usecase.GenerateBookingReference
import com.alialashwal.hotelreservation.domain.usecase.ValidateStay
import com.alialashwal.hotelreservation.model.AppError
import com.alialashwal.hotelreservation.model.Money
import com.alialashwal.hotelreservation.model.Outcome
import com.alialashwal.hotelreservation.model.StayError
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
class BookingViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today = LocalDate.of(2026, 9, 8)
    private val clock = Clock.fixed(Instant.parse("2026-09-08T09:00:00Z"), ZoneOffset.UTC)

    private val hotels = FakeHotelRepository()
    private val bookings = FakeBookingRepository()

    // ------------------------------------------------------------------ quoting

    @Test
    fun `no quote is shown until both dates are chosen`() = runTest {
        val viewModel = createViewModel()

        viewModel.state.test {
            advanceUntilIdle()
            assertNull(expectMostRecentItem().quote)

            viewModel.onIntent(BookingIntent.CheckInSelected(today))
            advanceUntilIdle()
            assertNull("one date is not a stay", expectMostRecentItem().quote)

            viewModel.onIntent(BookingIntent.CheckOutSelected(today.plusDays(2)))
            advanceUntilIdle()
            assertNotNull(expectMostRecentItem().quote)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the quote updates live as dates and rooms change`() = runTest {
        val viewModel = createViewModel(rate = "200.00")

        viewModel.state.test {
            advanceUntilIdle()
            viewModel.onIntent(BookingIntent.CheckInSelected(today))
            viewModel.onIntent(BookingIntent.CheckOutSelected(today.plusDays(2)))
            advanceUntilIdle()

            expectMostRecentItem().quote!!.let { quote ->
                assertEquals(2, quote.nights)
                assertEquals(usd("400.00"), quote.baseAmount)
                assertEquals(usd("60.00"), quote.vatAmount)
                assertEquals(usd("460.00"), quote.total)
            }

            viewModel.onIntent(BookingIntent.RoomAdded)
            advanceUntilIdle()

            expectMostRecentItem().quote!!.let { quote ->
                assertEquals(2, quote.rooms)
                assertEquals(usd("800.00"), quote.baseAmount)
                assertEquals(usd("120.00"), quote.vatAmount)
                assertEquals(usd("920.00"), quote.total)
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a hotel with no known rate cannot produce a quote`() = runTest {
        val viewModel = createViewModel(rate = null)

        viewModel.state.test {
            advanceUntilIdle()
            viewModel.onIntent(BookingIntent.CheckInSelected(today))
            viewModel.onIntent(BookingIntent.CheckOutSelected(today.plusDays(2)))
            advanceUntilIdle()

            val state = expectMostRecentItem()
            assertNull(state.quote)
            assertFalse(state.canSubmit)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --------------------------------------------------------------- validation

    @Test
    fun `validation messages stay hidden until the guest presses Confirm`() = runTest {
        // Opening the form should not immediately complain about dates nobody has picked.
        val viewModel = createViewModel()

        viewModel.state.test {
            advanceUntilIdle()
            val before = expectMostRecentItem()
            assertFalse(before.showErrors)
            assertNull(before.errorFor(StayError.CheckInMissing::class.java))

            viewModel.onIntent(BookingIntent.Confirm)
            advanceUntilIdle()

            val after = expectMostRecentItem()
            assertTrue(after.showErrors)
            assertEquals(StayError.CheckInMissing, after.errorFor(StayError.CheckInMissing::class.java))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `confirming with no dates books nothing`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(BookingIntent.Confirm)
        advanceUntilIdle()

        assertTrue(bookings.saved.isEmpty())
    }

    @Test
    fun `check-out on the same day as check-in is refused`() = runTest {
        val viewModel = createViewModel()

        viewModel.state.test {
            advanceUntilIdle()
            viewModel.onIntent(BookingIntent.CheckInSelected(today))
            viewModel.onIntent(BookingIntent.CheckOutSelected(today))
            viewModel.onIntent(BookingIntent.Confirm)
            advanceUntilIdle()

            val state = expectMostRecentItem()
            assertTrue(StayError.CheckOutNotAfterCheckIn in state.errors)
            assertNull(state.quote)
            assertTrue(bookings.saved.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `moving check-in past the chosen check-out clears the check-out`() = runTest {
        // Silently shifting it would put the guest on a date they never picked.
        val viewModel = createViewModel()

        viewModel.state.test {
            advanceUntilIdle()
            viewModel.onIntent(BookingIntent.CheckInSelected(today))
            viewModel.onIntent(BookingIntent.CheckOutSelected(today.plusDays(2)))
            advanceUntilIdle()

            viewModel.onIntent(BookingIntent.CheckInSelected(today.plusDays(5)))
            advanceUntilIdle()

            val state = expectMostRecentItem()
            assertEquals(today.plusDays(5), state.request.checkIn)
            assertNull(state.request.checkOut)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the room stepper stays inside its bounds`() = runTest {
        val viewModel = createViewModel()

        viewModel.state.test {
            advanceUntilIdle()
            viewModel.onIntent(BookingIntent.RoomRemoved)
            advanceUntilIdle()
            assertEquals(1, expectMostRecentItem().request.rooms)

            repeat(ValidateStay.MAX_ROOMS + 3) { viewModel.onIntent(BookingIntent.RoomAdded) }
            advanceUntilIdle()
            assertEquals(ValidateStay.MAX_ROOMS, expectMostRecentItem().request.rooms)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ------------------------------------------------------------- confirmation

    @Test
    fun `a valid booking is saved and reported as an effect`() = runTest {
        val viewModel = createViewModel(rate = "200.00")
        advanceUntilIdle()
        viewModel.onIntent(BookingIntent.CheckInSelected(today))
        viewModel.onIntent(BookingIntent.CheckOutSelected(today.plusDays(2)))
        advanceUntilIdle()

        viewModel.effects.test {
            viewModel.onIntent(BookingIntent.Confirm)
            advanceUntilIdle()

            val effect = awaitItem() as BookingEffect.BookingConfirmed
            val booking = bookings.saved.single()
            assertEquals(booking.reference, effect.reference)
            assertEquals(usd("460.00"), booking.quote.total)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a failed rate lookup at confirmation is reported and books nothing`() = runTest {
        val viewModel = createViewModel(rate = "200.00")
        advanceUntilIdle()
        viewModel.onIntent(BookingIntent.CheckInSelected(today))
        viewModel.onIntent(BookingIntent.CheckOutSelected(today.plusDays(2)))
        advanceUntilIdle()
        hotels.rateResult = Outcome.Failure(AppError.NoConnection)

        viewModel.state.test {
            viewModel.onIntent(BookingIntent.Confirm)
            advanceUntilIdle()

            assertEquals(AppError.NoConnection, expectMostRecentItem().submitError)
            assertTrue(bookings.saved.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // -------------------------------------------------------------- price change

    @Test
    fun `a price that moved since browsing stops the booking and shows both totals`() = runTest {
        // The cached rate says 200. The supplier now says 250.
        val viewModel = createViewModel(rate = "200.00")
        advanceUntilIdle()
        viewModel.onIntent(BookingIntent.CheckInSelected(today))
        viewModel.onIntent(BookingIntent.CheckOutSelected(today.plusDays(2)))
        advanceUntilIdle()
        hotels.rateResult = Outcome.Success(usd("250.00"))

        viewModel.state.test {
            viewModel.onIntent(BookingIntent.Confirm)
            advanceUntilIdle()

            val change = expectMostRecentItem().priceChange!!
            assertEquals(usd("460.00"), change.previous.total)
            assertEquals(usd("575.00"), change.current.total)
            assertTrue("nothing may be booked at a price the guest has not seen", bookings.saved.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `accepting the new price books at it`() = runTest {
        val viewModel = createViewModel(rate = "200.00")
        advanceUntilIdle()
        viewModel.onIntent(BookingIntent.CheckInSelected(today))
        viewModel.onIntent(BookingIntent.CheckOutSelected(today.plusDays(2)))
        advanceUntilIdle()
        hotels.rateResult = Outcome.Success(usd("250.00"))
        viewModel.onIntent(BookingIntent.Confirm)
        advanceUntilIdle()

        viewModel.onIntent(BookingIntent.AcceptNewPrice)
        advanceUntilIdle()

        assertEquals(usd("575.00"), bookings.saved.single().quote.total)
    }

    @Test
    fun `dismissing the price change leaves the guest on the form with nothing booked`() = runTest {
        val viewModel = createViewModel(rate = "200.00")
        advanceUntilIdle()
        viewModel.onIntent(BookingIntent.CheckInSelected(today))
        viewModel.onIntent(BookingIntent.CheckOutSelected(today.plusDays(2)))
        advanceUntilIdle()
        hotels.rateResult = Outcome.Success(usd("250.00"))
        viewModel.onIntent(BookingIntent.Confirm)
        advanceUntilIdle()

        viewModel.state.test {
            viewModel.onIntent(BookingIntent.DismissPriceChange)
            advanceUntilIdle()

            assertNull(expectMostRecentItem().priceChange)
            assertTrue(bookings.saved.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // -------------------------------------------------------------- persistence

    @Test
    fun `dates and room count survive the process being recreated`() = runTest {
        val savedState = SavedStateHandle(mapOf(BookingViewModel.ARG_HOTEL_ID to HOTEL_ID))
        val first = createViewModel(savedState = savedState)
        advanceUntilIdle()
        first.onIntent(BookingIntent.CheckInSelected(today))
        first.onIntent(BookingIntent.CheckOutSelected(today.plusDays(3)))
        first.onIntent(BookingIntent.RoomAdded)
        advanceUntilIdle()

        val restored = createViewModel(savedState = savedState)

        restored.state.test {
            advanceUntilIdle()
            val state = expectMostRecentItem()

            assertEquals(today, state.request.checkIn)
            assertEquals(today.plusDays(3), state.request.checkOut)
            assertEquals(2, state.request.rooms)
            assertNotNull("the quote is rebuilt, not restored", state.quote)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ------------------------------------------------------------------ helpers

    private fun createViewModel(
        rate: String? = "131.28",
        savedState: SavedStateHandle = SavedStateHandle(mapOf(BookingViewModel.ARG_HOTEL_ID to HOTEL_ID)),
    ): BookingViewModel {
        hotels.seedDetail(hotelDetail(id = HOTEL_ID, price = rate))
        rate?.let { hotels.rateResult = Outcome.Success(usd(it)) }

        return BookingViewModel(
            hotels = hotels,
            validateStay = ValidateStay(clock),
            calculateQuote = CalculateQuote(),
            confirmBooking = ConfirmBooking(
                hotels = hotels,
                bookings = bookings,
                validateStay = ValidateStay(clock),
                calculateQuote = CalculateQuote(),
                generateReference = GenerateBookingReference(clock, Random(1)),
                clock = clock,
            ),
            clock = clock,
            savedState = savedState,
        )
    }

    private fun usd(amount: String) = Money.of(amount, "USD")

    private companion object {
        const val HOTEL_ID = "lp516fd"
    }
}
