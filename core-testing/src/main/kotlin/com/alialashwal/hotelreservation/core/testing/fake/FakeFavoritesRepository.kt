package com.alialashwal.hotelreservation.core.testing.fake

import com.alialashwal.hotelreservation.core.testing.hotelSummary
import com.alialashwal.hotelreservation.domain.repository.FavoritesRepository
import com.alialashwal.hotelreservation.model.HotelSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeFavoritesRepository : FavoritesRepository {

    private val ids = MutableStateFlow<Set<String>>(emptySet())

    /** Rows returned by [observeFavorites]. Defaults to a generated row per favourite id. */
    var rowFor: (String) -> HotelSummary = { hotelSummary(id = it, name = "Hotel $it") }

    override fun observeFavorites(): Flow<List<HotelSummary>> =
        ids.map { set -> set.sorted().map(rowFor) }

    override fun observeFavoriteIds(): Flow<Set<String>> = ids

    override fun isFavorite(hotelId: String): Flow<Boolean> = ids.map { hotelId in it }

    override suspend fun toggleFavorite(hotelId: String): Boolean {
        val nowFavorite = hotelId !in ids.value
        ids.value = if (nowFavorite) ids.value + hotelId else ids.value - hotelId
        return nowFavorite
    }

    fun seed(vararg hotelIds: String) {
        ids.value = hotelIds.toSet()
    }
}
