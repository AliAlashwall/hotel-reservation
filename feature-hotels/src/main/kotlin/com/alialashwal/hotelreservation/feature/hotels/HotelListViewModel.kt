package com.alialashwal.hotelreservation.feature.hotels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alialashwal.hotelreservation.domain.repository.FavoritesRepository
import com.alialashwal.hotelreservation.domain.repository.HotelFeed
import com.alialashwal.hotelreservation.domain.repository.HotelRepository
import com.alialashwal.hotelreservation.domain.repository.ReferenceDataRepository
import com.alialashwal.hotelreservation.model.AppError
import com.alialashwal.hotelreservation.model.Country
import com.alialashwal.hotelreservation.model.HotelFilters
import com.alialashwal.hotelreservation.model.Outcome
import com.alialashwal.hotelreservation.model.PriceRange
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class HotelListViewModel @Inject constructor(
    private val hotels: HotelRepository,
    private val favorites: FavoritesRepository,
    private val referenceData: ReferenceDataRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    // Search text and filters live in SavedStateHandle, not in a field.
    //
    // That is what makes the screen come back the way the user left it after the
    // process is killed in the background. Stored as separate primitives rather than
    // one object because SavedStateHandle persists Bundle types, and keeping
    // HotelFilters free of Android types is what lets the domain modules stay pure
    // Kotlin.
    private val query = savedState.getStateFlow(KEY_QUERY, "")
    private val countryCode = savedState.getStateFlow(KEY_COUNTRY, HotelFilters.DEFAULT_COUNTRY_CODE)
    private val city = savedState.getStateFlow<String?>(KEY_CITY, null)
    private val minScore = savedState.getStateFlow<Double?>(KEY_MIN_SCORE, null)
    private val priceMin = savedState.getStateFlow<Int?>(KEY_PRICE_MIN, null)
    private val priceMax = savedState.getStateFlow<Int?>(KEY_PRICE_MAX, null)

    private val loadStatus = MutableStateFlow(LoadStatus())
    private val reference = MutableStateFlow(ReferenceLists())
    private val filterSheetOpen = MutableStateFlow(false)

    private val effectChannel = Channel<HotelListEffect>(
        capacity = Channel.BUFFERED,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val effects: Flow<HotelListEffect> = effectChannel.receiveAsFlow()

    /**
     * The filters actually sent to the repository.
     *
     * The debounce sits here and not on the text field, so typing stays instant while
     * the network waits for the user to stop. `distinctUntilChanged` then stops a
     * search that ends where it started, such as typing a letter and deleting it,
     * from firing a request at all.
     */
    private val appliedFilters: StateFlow<HotelFilters> =
        combine(
            query.debounce { if (it.isEmpty()) 0L else SEARCH_DEBOUNCE_MS },
            combine(countryCode, city, minScore, ::Triple),
            combine(priceMin, priceMax) { min, max -> PriceRange(min, max) },
        ) { text, place, band ->
            HotelFilters(
                countryCode = place.first,
                city = place.second,
                query = text,
                minReviewScore = place.third,
                priceRange = band,
            )
        }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.Eagerly, currentFilters())

    val state: StateFlow<HotelListState> = combine(
        combine(query, appliedFilters, ::Pair),
        appliedFilters.flatMapLatest { hotels.observeFeed(it) },
        favorites.observeFavoriteIds(),
        loadStatus,
        combine(reference, filterSheetOpen, ::Pair),
    ) { (text, filters), feed, favoriteIds, status, (lists, sheetOpen) ->
        HotelListState(
            query = text,
            filters = filters,
            hotels = feed.hotels,
            favoriteIds = favoriteIds,
            isRefreshing = status.isRefreshing,
            isAppending = status.isAppending,
            isLastPage = feed.isLastPage,
            isStale = feed.isStale,
            lastRefreshedAt = feed.lastRefreshedAt,
            hasCachedData = feed.hasCachedData,
            refreshError = status.refreshError,
            appendError = status.appendError,
            availableCountries = lists.countries,
            availableCities = lists.cities,
            isFilterSheetOpen = sheetOpen,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HotelListState())

    init {
        // One refresh loop for every filter change. `collectLatest` cancels the request
        // still in flight when the filters move again, which is the other half of not
        // having overlapping search requests: the repository drops duplicates, and this
        // cancels superseded ones.
        viewModelScope.launch {
            appliedFilters.collectLatest { filters ->
                loadIfNeeded(filters)
            }
        }
        viewModelScope.launch { loadCountries() }
        viewModelScope.launch {
            countryCode.collectLatest { code -> loadCities(code) }
        }
    }

    fun onIntent(intent: HotelListIntent) {
        when (intent) {
            is HotelListIntent.QueryChanged -> savedState[KEY_QUERY] = intent.query

            is HotelListIntent.CountrySelected -> {
                savedState[KEY_COUNTRY] = intent.countryCode
                // A city from the previous country cannot be valid in the new one.
                savedState[KEY_CITY] = null
            }

            is HotelListIntent.CitySelected -> savedState[KEY_CITY] = intent.city
            is HotelListIntent.MinScoreSelected -> savedState[KEY_MIN_SCORE] = intent.minScore

            is HotelListIntent.PriceRangeSelected -> {
                savedState[KEY_PRICE_MIN] = intent.range.min
                savedState[KEY_PRICE_MAX] = intent.range.max
            }

            HotelListIntent.ClearFilters -> {
                savedState[KEY_CITY] = null
                savedState[KEY_MIN_SCORE] = null
                savedState[KEY_PRICE_MIN] = null
                savedState[KEY_PRICE_MAX] = null
            }

            is HotelListIntent.FilterSheetToggled -> filterSheetOpen.value = intent.open

            HotelListIntent.Refresh -> viewModelScope.launch { refresh(appliedFilters.value) }
            HotelListIntent.LoadMore -> loadMore()
            HotelListIntent.RetryAppend -> loadMore(force = true)

            is HotelListIntent.ToggleFavorite -> viewModelScope.launch {
                favorites.toggleFavorite(intent.hotelId)
            }

            is HotelListIntent.HotelClicked -> viewModelScope.launch {
                effectChannel.send(HotelListEffect.OpenHotel(intent.hotelId))
            }
        }
    }

    /**
     * Serves the cache immediately and only goes to the network when the cache is
     * missing or past its refresh window. Going back to a search the user ran a minute
     * ago is instant and costs nothing.
     */
    private suspend fun loadIfNeeded(filters: HotelFilters) {
        val feed: HotelFeed = hotels.observeFeed(filters).first()
        if (!feed.hasCachedData || feed.isStale) refresh(filters)
    }

    private suspend fun refresh(filters: HotelFilters) {
        loadStatus.update { it.copy(isRefreshing = true, refreshError = null, appendError = null) }
        val outcome = hotels.refresh(filters)
        loadStatus.update {
            it.copy(isRefreshing = false, refreshError = (outcome as? Outcome.Failure)?.error)
        }
    }

    private fun loadMore(force: Boolean = false) {
        val status = loadStatus.value
        // A previous append failure blocks further attempts until the user retries.
        // Without it, a list parked at a failed page would hammer the API once per
        // frame for as long as it stayed on screen.
        if (status.isRefreshing) return
        if (!force && status.appendError != null) return

        // Claim the slot before launching, and atomically. The scroll listener can fire
        // several times inside one frame, and every one of those calls runs to
        // completion before any coroutine body does. Setting the flag inside the
        // coroutine would let all of them through.
        if (!claimAppendSlot()) return

        viewModelScope.launch {
            try {
                // Read the end-of-list marker from the cache, not from `state`. `state`
                // is shared WhileSubscribed, so its value is only current while the
                // screen is collecting it, and this must be correct either way.
                if (hotels.observeFeed(appliedFilters.value).first().isLastPage) return@launch

                val outcome = hotels.loadNextPage(appliedFilters.value)
                loadStatus.update { it.copy(appendError = (outcome as? Outcome.Failure)?.error) }
            } finally {
                loadStatus.update { it.copy(isAppending = false) }
            }
        }
    }

    /** True if this call is the one that took the append slot. */
    private fun claimAppendSlot(): Boolean =
        !loadStatus.getAndUpdate { it.copy(isAppending = true, appendError = null) }.isAppending

    private suspend fun loadCountries() {
        val outcome = referenceData.countries()
        if (outcome is Outcome.Success) {
            reference.update { it.copy(countries = outcome.value) }
        }
        // A failed lookup leaves the country picker on its default. It is not worth an
        // error banner over a list of hotels the user can already see.
    }

    private suspend fun loadCities(code: String) {
        reference.update { it.copy(cities = emptyList()) }
        val outcome = referenceData.cities(code)
        if (outcome is Outcome.Success) {
            reference.update { it.copy(cities = outcome.value) }
        }
    }

    private fun currentFilters() = HotelFilters(
        countryCode = countryCode.value,
        city = city.value,
        query = query.value,
        minReviewScore = minScore.value,
        priceRange = PriceRange(priceMin.value, priceMax.value),
    )

    private data class LoadStatus(
        val isRefreshing: Boolean = false,
        val isAppending: Boolean = false,
        val refreshError: AppError? = null,
        val appendError: AppError? = null,
    )

    private data class ReferenceLists(
        val countries: List<Country> = emptyList(),
        val cities: List<String> = emptyList(),
    )

    internal companion object {
        const val SEARCH_DEBOUNCE_MS = 350L

        /** Long enough to survive a rotation without tearing the flows down. */
        const val STOP_TIMEOUT_MS = 5_000L

        /** How close to the end of the list triggers the next page. */
        const val PREFETCH_DISTANCE = 4

        private const val KEY_QUERY = "query"
        private const val KEY_COUNTRY = "countryCode"
        private const val KEY_CITY = "city"
        private const val KEY_MIN_SCORE = "minScore"
        private const val KEY_PRICE_MIN = "priceMin"
        private const val KEY_PRICE_MAX = "priceMax"
    }
}
