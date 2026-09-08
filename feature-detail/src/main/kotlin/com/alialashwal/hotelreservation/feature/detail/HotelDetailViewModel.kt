package com.alialashwal.hotelreservation.feature.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alialashwal.hotelreservation.domain.repository.FavoritesRepository
import com.alialashwal.hotelreservation.domain.repository.HotelRepository
import com.alialashwal.hotelreservation.model.AppError
import com.alialashwal.hotelreservation.model.Outcome
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
import javax.inject.Inject

@HiltViewModel
class HotelDetailViewModel @Inject constructor(
    private val hotels: HotelRepository,
    private val favorites: FavoritesRepository,
    savedState: SavedStateHandle,
) : ViewModel() {

    // Supplied by the type-safe route, which stores its arguments under the property
    // names of HotelDetailRoute.
    private val hotelId: String = checkNotNull(savedState.get<String>(ARG_HOTEL_ID)) {
        "HotelDetailRoute must carry a hotelId"
    }

    private val loadStatus = MutableStateFlow(LoadStatus())

    private val effectChannel = Channel<HotelDetailEffect>(
        capacity = Channel.BUFFERED,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val effects: Flow<HotelDetailEffect> = effectChannel.receiveAsFlow()

    val state: StateFlow<HotelDetailState> = combine(
        hotels.observeDetail(hotelId),
        favorites.isFavorite(hotelId),
        loadStatus,
    ) { hotel, isFavorite, status ->
        HotelDetailState(
            hotelId = hotelId,
            hotel = hotel,
            isFavorite = isFavorite,
            isLoading = status.isLoading,
            error = status.error,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        HotelDetailState(hotelId = hotelId),
    )

    init {
        viewModelScope.launch { loadIfNeeded() }
    }

    fun onIntent(intent: HotelDetailIntent) {
        when (intent) {
            HotelDetailIntent.Retry -> viewModelScope.launch { refresh() }

            HotelDetailIntent.ToggleFavorite -> viewModelScope.launch {
                favorites.toggleFavorite(hotelId)
            }

            HotelDetailIntent.BookClicked -> viewModelScope.launch {
                effectChannel.send(HotelDetailEffect.OpenBooking(hotelId))
            }
        }
    }

    /**
     * Refreshes only when nothing is cached.
     *
     * A hotel arrived at from the list has just been written by that list's page load,
     * so re-fetching it on open would spend a round trip to redraw the same screen.
     */
    private suspend fun loadIfNeeded() {
        if (hotels.observeDetail(hotelId).first() == null) refresh()
    }

    private suspend fun refresh() {
        loadStatus.update { it.copy(isLoading = true, error = null) }
        val outcome = hotels.refreshDetail(hotelId)
        loadStatus.update {
            it.copy(isLoading = false, error = (outcome as? Outcome.Failure)?.error)
        }
    }

    private data class LoadStatus(
        val isLoading: Boolean = false,
        val error: AppError? = null,
    )

    internal companion object {
        const val ARG_HOTEL_ID = "hotelId"
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
