package com.alialashwal.hotelreservation.feature.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alialashwal.hotelreservation.domain.repository.FavoritesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val favorites: FavoritesRepository,
) : ViewModel() {

    private val effectChannel = Channel<FavoritesEffect>(
        capacity = Channel.BUFFERED,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val effects: Flow<FavoritesEffect> = effectChannel.receiveAsFlow()

    val state: StateFlow<FavoritesState> = favorites.observeFavorites()
        .map { FavoritesState(hotels = it, isLoading = false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), FavoritesState())

    fun onIntent(intent: FavoritesIntent) {
        when (intent) {
            is FavoritesIntent.ToggleFavorite -> viewModelScope.launch {
                favorites.toggleFavorite(intent.hotelId)
            }

            is FavoritesIntent.HotelClicked -> viewModelScope.launch {
                effectChannel.send(FavoritesEffect.OpenHotel(intent.hotelId))
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
