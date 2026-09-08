package com.alialashwal.hotelreservation.feature.detail

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.alialashwal.hotelreservation.core.testing.MainDispatcherRule
import com.alialashwal.hotelreservation.core.testing.fake.FakeFavoritesRepository
import com.alialashwal.hotelreservation.core.testing.fake.FakeHotelRepository
import com.alialashwal.hotelreservation.core.testing.hotelDetail
import com.alialashwal.hotelreservation.model.AppError
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HotelDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val hotels = FakeHotelRepository()
    private val favorites = FakeFavoritesRepository()

    @Test
    fun `a hotel that is not cached is fetched on open`() = runTest {
        createViewModel()
        advanceUntilIdle()

        assertNotNull(hotels.observeDetail(HOTEL_ID))
    }

    @Test
    fun `a hotel already cached by the list is not fetched again`() = runTest {
        // Arriving from the list means the row was just written. Re-fetching would spend
        // a round trip to redraw the same screen.
        hotels.seedDetail(hotelDetail(id = HOTEL_ID))
        hotels.detailError = AppError.NoConnection   // would fail loudly if a fetch happened

        val viewModel = createViewModel()

        viewModel.state.test {
            advanceUntilIdle()
            val state = expectMostRecentItem()
            assertEquals(DetailPhase.Content, state.phase)
            assertEquals(null, state.error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a failure with nothing cached owns the screen`() = runTest {
        hotels.detailError = AppError.NotFound
        val viewModel = createViewModel()

        viewModel.state.test {
            advanceUntilIdle()
            val state = expectMostRecentItem()

            assertEquals(DetailPhase.Error, state.phase)
            assertEquals(AppError.NotFound, state.error)
            assertFalse("a missing hotel will not appear on retry", AppError.NotFound.isRetryable)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `retry loads the hotel after a failure clears`() = runTest {
        hotels.detailError = AppError.Timeout
        val viewModel = createViewModel()

        viewModel.state.test {
            advanceUntilIdle()
            assertEquals(DetailPhase.Error, expectMostRecentItem().phase)

            hotels.detailError = null
            viewModel.onIntent(HotelDetailIntent.Retry)
            advanceUntilIdle()

            assertEquals(DetailPhase.Content, expectMostRecentItem().phase)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the favourite state comes from the shared repository, not from local state`() = runTest {
        favorites.seed(HOTEL_ID)
        val viewModel = createViewModel()

        viewModel.state.test {
            advanceUntilIdle()
            assertTrue("a hotel favourited elsewhere opens as a favourite", expectMostRecentItem().isFavorite)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `toggling the favourite flips it both ways`() = runTest {
        val viewModel = createViewModel()

        viewModel.state.test {
            advanceUntilIdle()
            assertFalse(expectMostRecentItem().isFavorite)

            viewModel.onIntent(HotelDetailIntent.ToggleFavorite)
            advanceUntilIdle()
            assertTrue(expectMostRecentItem().isFavorite)

            viewModel.onIntent(HotelDetailIntent.ToggleFavorite)
            advanceUntilIdle()
            assertFalse(expectMostRecentItem().isFavorite)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `booking is offered only when a price is known`() = runTest {
        hotels.seedDetail(hotelDetail(id = HOTEL_ID, price = null))
        val viewModel = createViewModel()

        viewModel.state.test {
            advanceUntilIdle()
            assertFalse("there is nothing to quote", expectMostRecentItem().canBook)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `opening the booking screen is a one-shot effect`() = runTest {
        hotels.seedDetail(hotelDetail(id = HOTEL_ID))
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.effects.test {
            viewModel.onIntent(HotelDetailIntent.BookClicked)
            advanceUntilIdle()

            assertEquals(HotelDetailEffect.OpenBooking(HOTEL_ID), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun createViewModel() = HotelDetailViewModel(
        hotels = hotels,
        favorites = favorites,
        savedState = SavedStateHandle(mapOf(HotelDetailViewModel.ARG_HOTEL_ID to HOTEL_ID)),
    )

    private companion object {
        const val HOTEL_ID = "lp516fd"
    }
}
