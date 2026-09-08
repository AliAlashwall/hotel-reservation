package com.alialashwal.hotelreservation.feature.hotels

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.alialashwal.hotelreservation.core.testing.MainDispatcherRule
import com.alialashwal.hotelreservation.core.testing.fake.FakeFavoritesRepository
import com.alialashwal.hotelreservation.core.testing.fake.FakeHotelRepository
import com.alialashwal.hotelreservation.core.testing.fake.FakeReferenceDataRepository
import com.alialashwal.hotelreservation.core.testing.hotelPage
import com.alialashwal.hotelreservation.model.AppError
import com.alialashwal.hotelreservation.model.HotelFilters
import com.alialashwal.hotelreservation.model.PriceRange
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HotelListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val hotels = FakeHotelRepository()
    private val favorites = FakeFavoritesRepository()
    private val referenceData = FakeReferenceDataRepository()

    // ------------------------------------------------------------ first load

    @Test
    fun `the screen loads on creation without waiting for the user`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(1, hotels.refreshCount)
        assertEquals(HotelFilters(), hotels.refreshedFilters.single())
    }

    @Test
    fun `rows reach the state and the phase becomes content`() = runTest {
        val viewModel = createViewModel()

        viewModel.state.test {
            skipItems(1)
            advanceUntilIdle()
            val state = expectMostRecentItem()

            assertEquals(20, state.hotels.size)
            assertEquals(ListPhase.Content, state.phase)
            assertFalse(state.isLastPage)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ------------------------------------------------------- search debounce

    @Test
    fun `typing quickly produces one search, not one per keystroke`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        val afterFirstLoad = hotels.refreshCount

        "Kempinski".forEachIndexed { index, _ ->
            viewModel.onIntent(HotelListIntent.QueryChanged("Kempinski".take(index + 1)))
            advanceTimeBy(50)
        }
        advanceUntilIdle()

        assertEquals(1, hotels.refreshCount - afterFirstLoad)
        assertEquals("Kempinski", hotels.refreshedFilters.last().query)
    }

    @Test
    fun `the search waits for the debounce before firing at all`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        val afterFirstLoad = hotels.refreshCount

        viewModel.onIntent(HotelListIntent.QueryChanged("Nile"))
        advanceTimeBy(HotelListViewModel.SEARCH_DEBOUNCE_MS - 50)

        assertEquals("nothing should have been sent yet", afterFirstLoad, hotels.refreshCount)

        advanceUntilIdle()
        assertEquals(afterFirstLoad + 1, hotels.refreshCount)
    }

    @Test
    fun `typing a letter and deleting it sends no request at all`() = runTest {
        // The applied filters return to where they started, so distinctUntilChanged
        // swallows it. Without that, every corrected typo would cost a round trip.
        val viewModel = createViewModel()
        advanceUntilIdle()
        val afterFirstLoad = hotels.refreshCount

        viewModel.onIntent(HotelListIntent.QueryChanged("K"))
        advanceTimeBy(100)
        viewModel.onIntent(HotelListIntent.QueryChanged(""))
        advanceUntilIdle()

        assertEquals(afterFirstLoad, hotels.refreshCount)
    }

    @Test
    fun `the text field updates immediately even though the search is debounced`() = runTest {
        val viewModel = createViewModel()

        viewModel.state.test {
            skipItems(1)
            advanceUntilIdle()
            viewModel.onIntent(HotelListIntent.QueryChanged("Ni"))
            advanceTimeBy(10)

            assertEquals("Ni", expectMostRecentItem().query)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --------------------------------------------------------------- filters

    @Test
    fun `each filter change starts its own result set`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(HotelListIntent.CitySelected("Cairo"))
        advanceUntilIdle()
        viewModel.onIntent(HotelListIntent.MinScoreSelected(9.0))
        advanceUntilIdle()
        viewModel.onIntent(HotelListIntent.PriceRangeSelected(PriceRange(50, 200)))
        advanceUntilIdle()

        val last = hotels.refreshedFilters.last()
        assertEquals("Cairo", last.city)
        assertEquals(9.0, last.minReviewScore!!, 0.001)
        assertEquals(PriceRange(50, 200), last.priceRange)
        assertEquals(4, hotels.refreshCount)
    }

    @Test
    fun `choosing a different country drops the city, which cannot exist in it`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onIntent(HotelListIntent.CitySelected("Cairo"))
        advanceUntilIdle()

        viewModel.onIntent(HotelListIntent.CountrySelected("AE"))
        advanceUntilIdle()

        val last = hotels.refreshedFilters.last()
        assertEquals("AE", last.countryCode)
        assertNull(last.city)
    }

    @Test
    fun `clearing filters keeps the country and the search text`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onIntent(HotelListIntent.CountrySelected("AE"))
        viewModel.onIntent(HotelListIntent.QueryChanged("Nile"))
        viewModel.onIntent(HotelListIntent.CitySelected("Dubai"))
        viewModel.onIntent(HotelListIntent.MinScoreSelected(9.0))
        advanceUntilIdle()

        viewModel.onIntent(HotelListIntent.ClearFilters)
        advanceUntilIdle()

        val last = hotels.refreshedFilters.last()
        assertEquals("AE", last.countryCode)
        assertEquals("Nile", last.query)
        assertNull(last.city)
        assertNull(last.minReviewScore)
        assertTrue(last.priceRange.isUnbounded)
    }

    @Test
    fun `the filter badge counts filters, and neither the country nor the search`() = runTest {
        val viewModel = createViewModel()

        viewModel.state.test {
            skipItems(1)
            advanceUntilIdle()

            viewModel.onIntent(HotelListIntent.QueryChanged("Nile"))
            viewModel.onIntent(HotelListIntent.CountrySelected("AE"))
            advanceUntilIdle()
            expectMostRecentItem().let { state ->
                // Both have their own visible control, so counting them would tell the
                // user something they can already see.
                assertEquals(0, state.activeFilterCount)
                assertFalse(state.hasActiveFilters)
            }

            viewModel.onIntent(HotelListIntent.MinScoreSelected(9.0))
            advanceUntilIdle()
            assertEquals(1, expectMostRecentItem().activeFilterCount)

            viewModel.onIntent(HotelListIntent.CitySelected("Dubai"))
            viewModel.onIntent(HotelListIntent.PriceRangeSelected(PriceRange(50, 200)))
            advanceUntilIdle()
            expectMostRecentItem().let { state ->
                assertEquals(3, state.activeFilterCount)
                assertTrue(state.hasActiveFilters)
            }

            viewModel.onIntent(HotelListIntent.ClearFilters)
            advanceUntilIdle()
            assertEquals(0, expectMostRecentItem().activeFilterCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ------------------------------------------------------------- cache use

    @Test
    fun `a fresh cache is served without a network call`() = runTest {
        hotels.seedFeed(HotelFilters(), hotelPage(20))

        createViewModel()
        advanceUntilIdle()

        assertEquals("going back to a recent search should cost nothing", 0, hotels.refreshCount)
    }

    @Test
    fun `a stale cache is shown first and refreshed behind it`() = runTest {
        hotels.seedFeed(HotelFilters(), hotelPage(20))
        hotels.markStale(HotelFilters())

        createViewModel()
        advanceUntilIdle()

        assertEquals(1, hotels.refreshCount)
    }

    // ------------------------------------------------------------ pagination

    @Test
    fun `loading more appends the next page`() = runTest {
        val viewModel = createViewModel()

        viewModel.state.test {
            skipItems(1)
            advanceUntilIdle()
            assertEquals(20, expectMostRecentItem().hotels.size)

            viewModel.onIntent(HotelListIntent.LoadMore)
            advanceUntilIdle()

            assertEquals(40, expectMostRecentItem().hotels.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a second load-more while one is running is ignored`() = runTest {
        // The scroll listener fires repeatedly. Only one request may go out.
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(HotelListIntent.LoadMore)
        viewModel.onIntent(HotelListIntent.LoadMore)
        viewModel.onIntent(HotelListIntent.LoadMore)
        advanceUntilIdle()

        assertEquals(1, hotels.appendCount)
    }

    @Test
    fun `no request is made once the last page is reached`() = runTest {
        hotels.catalogueSize = 15   // one page holds everything
        val viewModel = createViewModel()
        advanceUntilIdle()

        repeat(5) { viewModel.onIntent(HotelListIntent.LoadMore) }
        advanceUntilIdle()

        assertEquals(0, hotels.appendCount)
    }

    @Test
    fun `paging stops exactly at the end of the catalogue`() = runTest {
        hotels.catalogueSize = 45
        val viewModel = createViewModel()

        viewModel.state.test {
            skipItems(1)
            advanceUntilIdle()
            repeat(4) {
                viewModel.onIntent(HotelListIntent.LoadMore)
                advanceUntilIdle()
            }

            val state = expectMostRecentItem()
            assertEquals(45, state.hotels.size)
            assertTrue(state.isLastPage)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a failed append blocks further attempts until the user retries`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        hotels.appendError = AppError.Timeout

        viewModel.onIntent(HotelListIntent.LoadMore)
        advanceUntilIdle()
        // Without the guard, a list parked at a failed page would retry every frame.
        repeat(5) { viewModel.onIntent(HotelListIntent.LoadMore) }
        advanceUntilIdle()

        assertEquals(1, hotels.appendCount)

        hotels.appendError = null
        viewModel.onIntent(HotelListIntent.RetryAppend)
        advanceUntilIdle()

        assertEquals(2, hotels.appendCount)
    }

    @Test
    fun `an append failure is reported without discarding the rows already shown`() = runTest {
        val viewModel = createViewModel()

        viewModel.state.test {
            skipItems(1)
            advanceUntilIdle()
            hotels.appendError = AppError.NoConnection

            viewModel.onIntent(HotelListIntent.LoadMore)
            advanceUntilIdle()

            val state = expectMostRecentItem()
            assertEquals(AppError.NoConnection, state.appendError)
            assertEquals(20, state.hotels.size)
            assertEquals(ListPhase.Content, state.phase)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---------------------------------------------------------------- phases

    @Test
    fun `a failure with nothing cached owns the screen`() = runTest {
        hotels.refreshError = AppError.NoConnection
        val viewModel = createViewModel()

        viewModel.state.test {
            skipItems(1)
            advanceUntilIdle()
            val state = expectMostRecentItem()

            assertEquals(ListPhase.Error, state.phase)
            assertEquals(AppError.NoConnection, state.refreshError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a failure with rows already on screen does not replace them`() = runTest {
        val viewModel = createViewModel()

        viewModel.state.test {
            skipItems(1)
            advanceUntilIdle()
            hotels.refreshError = AppError.NoConnection

            viewModel.onIntent(HotelListIntent.Refresh)
            advanceUntilIdle()

            val state = expectMostRecentItem()
            assertEquals(ListPhase.Content, state.phase)
            assertEquals(20, state.hotels.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a search that matches nothing is empty, not an error and not still loading`() = runTest {
        hotels.catalogueSize = 0
        val viewModel = createViewModel()

        viewModel.state.test {
            skipItems(1)
            advanceUntilIdle()
            val state = expectMostRecentItem()

            assertEquals(ListPhase.Empty, state.phase)
            assertTrue(state.hasCachedData)
            assertNull(state.refreshError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ------------------------------------------------------------ favourites

    @Test
    fun `toggling a favourite is reflected in the state`() = runTest {
        val viewModel = createViewModel()

        viewModel.state.test {
            skipItems(1)
            advanceUntilIdle()
            assertEquals(emptySet<String>(), expectMostRecentItem().favoriteIds)

            viewModel.onIntent(HotelListIntent.ToggleFavorite("lp3"))
            advanceUntilIdle()
            assertEquals(setOf("lp3"), expectMostRecentItem().favoriteIds)

            viewModel.onIntent(HotelListIntent.ToggleFavorite("lp3"))
            advanceUntilIdle()
            assertEquals(emptySet<String>(), expectMostRecentItem().favoriteIds)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --------------------------------------------------------------- effects

    @Test
    fun `opening a hotel is an effect, so a rotation cannot replay it`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.effects.test {
            viewModel.onIntent(HotelListIntent.HotelClicked("lp7"))
            advanceUntilIdle()

            assertEquals(HotelListEffect.OpenHotel("lp7"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // -------------------------------------------------------- process death

    @Test
    fun `search text and filters come back after the process is recreated`() = runTest {
        // What SavedStateHandle survives, not just what a rotation survives.
        val savedState = SavedStateHandle()
        val first = createViewModel(savedState)
        advanceUntilIdle()
        first.onIntent(HotelListIntent.QueryChanged("Kempinski"))
        first.onIntent(HotelListIntent.CountrySelected("AE"))
        first.onIntent(HotelListIntent.MinScoreSelected(9.0))
        advanceUntilIdle()

        // A new ViewModel over the same handle is what the framework builds after the
        // process is killed and the user returns to the app.
        val restored = createViewModel(savedState)

        restored.state.test {
            skipItems(1)
            advanceUntilIdle()
            val state = expectMostRecentItem()

            assertEquals("Kempinski", state.query)
            assertEquals("AE", state.filters.countryCode)
            assertEquals(9.0, state.filters.minReviewScore!!, 0.001)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the country and city pickers are populated from reference data`() = runTest {
        val viewModel = createViewModel()

        viewModel.state.test {
            skipItems(1)
            advanceUntilIdle()
            val state = expectMostRecentItem()

            assertEquals(listOf("EG", "AE"), state.availableCountries.map { it.code })
            assertEquals(listOf("Alexandria", "Aswan", "Cairo"), state.availableCities)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a failed reference lookup leaves the list working`() = runTest {
        // The filter pickers are a convenience. Losing them must not error the screen.
        referenceData.error = AppError.Timeout
        val viewModel = createViewModel()

        viewModel.state.test {
            skipItems(1)
            advanceUntilIdle()
            val state = expectMostRecentItem()

            assertEquals(ListPhase.Content, state.phase)
            assertTrue(state.availableCountries.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun createViewModel(savedState: SavedStateHandle = SavedStateHandle()) =
        HotelListViewModel(
            hotels = hotels,
            favorites = favorites,
            referenceData = referenceData,
            savedState = savedState,
        )
}
