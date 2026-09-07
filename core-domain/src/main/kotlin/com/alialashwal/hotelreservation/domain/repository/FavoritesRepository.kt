package com.alialashwal.hotelreservation.domain.repository

import com.alialashwal.hotelreservation.model.HotelSummary
import kotlinx.coroutines.flow.Flow

/**
 * Favourites are a flag on the single cached hotel row, not a second copy of it.
 *
 * That is what keeps the list, the details screen and the favourites screen in step:
 * they are three queries over one table, so a toggle anywhere is observed everywhere
 * with no synchronisation code. It also means a favourited hotel is exempt from cache
 * eviction, which is what makes favourites work with no network.
 */
interface FavoritesRepository {

    fun observeFavorites(): Flow<List<HotelSummary>>

    fun observeFavoriteIds(): Flow<Set<String>>

    fun isFavorite(hotelId: String): Flow<Boolean>

    /** Returns the state the hotel ended up in. */
    suspend fun toggleFavorite(hotelId: String): Boolean
}
