package com.alialashwal.hotelreservation.feature.favorites

import com.alialashwal.hotelreservation.model.HotelSummary

/**
 * No error field, and that is deliberate. This screen reads the local database only.
 * Favourites are exempt from cache eviction, so there is no state in which the list
 * cannot be produced and nothing for the user to retry.
 */
data class FavoritesState(
    val hotels: List<HotelSummary> = emptyList(),
    val isLoading: Boolean = true,
) {
    val isEmpty: Boolean get() = !isLoading && hotels.isEmpty()
}

sealed interface FavoritesIntent {
    data class ToggleFavorite(val hotelId: String) : FavoritesIntent
    data class HotelClicked(val hotelId: String) : FavoritesIntent
}

sealed interface FavoritesEffect {
    data class OpenHotel(val hotelId: String) : FavoritesEffect
}
