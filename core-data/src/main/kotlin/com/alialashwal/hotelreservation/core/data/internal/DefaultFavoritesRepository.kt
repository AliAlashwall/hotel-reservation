package com.alialashwal.hotelreservation.core.data.internal

import com.alialashwal.hotelreservation.core.database.dao.HotelDao
import com.alialashwal.hotelreservation.domain.repository.FavoritesRepository
import com.alialashwal.hotelreservation.model.HotelSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin on purpose. Favourites are a column on the hotel row, so there is no state to
 * reconcile here and nothing to synchronise between screens: every screen observes the
 * same table.
 */
@Singleton
class DefaultFavoritesRepository @Inject constructor(
    private val dao: HotelDao,
) : FavoritesRepository {

    override fun observeFavorites(): Flow<List<HotelSummary>> =
        dao.observeFavorites().map { rows -> rows.map { it.toDomain() } }

    override fun observeFavoriteIds(): Flow<Set<String>> =
        dao.observeFavoriteIds().map { it.toSet() }

    override fun isFavorite(hotelId: String): Flow<Boolean> = dao.observeIsFavorite(hotelId)

    override suspend fun toggleFavorite(hotelId: String): Boolean = dao.toggleFavorite(hotelId)
}
