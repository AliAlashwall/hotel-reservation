package com.alialashwal.hotelreservation.feature.favorites

import app.cash.turbine.test
import com.alialashwal.hotelreservation.core.testing.MainDispatcherRule
import com.alialashwal.hotelreservation.core.testing.fake.FakeFavoritesRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FavoritesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val favorites = FakeFavoritesRepository()

    @Test
    fun `an empty list is empty only after loading finishes`() = runTest {
        val viewModel = FavoritesViewModel(favorites)

        viewModel.state.test {
            // Before the first emission the screen is loading, not empty. Showing
            // "no favourites yet" for a frame would be a lie.
            assertFalse(awaitItem().isEmpty)

            advanceUntilIdle()
            assertTrue(expectMostRecentItem().isEmpty)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `favourites saved elsewhere appear here without this screen being told`() = runTest {
        favorites.seed("lp1", "lp2")
        val viewModel = FavoritesViewModel(favorites)

        viewModel.state.test {
            advanceUntilIdle()
            assertEquals(listOf("lp1", "lp2"), expectMostRecentItem().hotels.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `removing a favourite drops it from the list immediately`() = runTest {
        favorites.seed("lp1", "lp2")
        val viewModel = FavoritesViewModel(favorites)

        viewModel.state.test {
            advanceUntilIdle()
            viewModel.onIntent(FavoritesIntent.ToggleFavorite("lp1"))
            advanceUntilIdle()

            assertEquals(listOf("lp2"), expectMostRecentItem().hotels.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `opening a hotel is a one-shot effect`() = runTest {
        val viewModel = FavoritesViewModel(favorites)

        viewModel.effects.test {
            viewModel.onIntent(FavoritesIntent.HotelClicked("lp9"))
            advanceUntilIdle()

            assertEquals(FavoritesEffect.OpenHotel("lp9"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
