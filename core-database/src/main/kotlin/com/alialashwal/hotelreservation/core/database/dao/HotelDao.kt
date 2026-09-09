package com.alialashwal.hotelreservation.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.alialashwal.hotelreservation.core.database.entity.HotelDetailEntity
import com.alialashwal.hotelreservation.core.database.entity.HotelEntity
import com.alialashwal.hotelreservation.core.database.entity.ResultSetEntity
import com.alialashwal.hotelreservation.core.database.entity.ResultSetEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HotelDao {

    // ------------------------------------------------------------------- reads

    /**
     * The rows of one result set, in server order.
     *
     * The join is what separates "which hotels matched these filters, in what order"
     * from "what do we know about this hotel". A hotel cached under one search is
     * reused by another instead of being fetched and stored twice.
     */
    @Query(
        """
        SELECT h.* FROM hotels h
        INNER JOIN result_set_entries e ON e.hotelId = h.id
        WHERE e.cacheKey = :cacheKey
        ORDER BY e.position ASC
        """
    )
    fun observeResultSet(cacheKey: String): Flow<List<HotelEntity>>

    @Query("SELECT * FROM result_sets WHERE cacheKey = :cacheKey")
    fun observeResultSetMeta(cacheKey: String): Flow<ResultSetEntity?>

    @Query("SELECT * FROM result_sets WHERE cacheKey = :cacheKey")
    suspend fun resultSetMeta(cacheKey: String): ResultSetEntity?

    @Query("SELECT * FROM hotels WHERE id = :hotelId")
    fun observeHotel(hotelId: String): Flow<HotelEntity?>

    @Query("SELECT * FROM hotel_details WHERE hotelId = :hotelId")
    fun observeHotelDetail(hotelId: String): Flow<HotelDetailEntity?>

    @Query("SELECT * FROM hotels WHERE isFavorite = 1 ORDER BY name COLLATE NOCASE ASC")
    fun observeFavorites(): Flow<List<HotelEntity>>

    @Query("SELECT id FROM hotels WHERE isFavorite = 1")
    fun observeFavoriteIds(): Flow<List<String>>

    @Query("SELECT COALESCE((SELECT isFavorite FROM hotels WHERE id = :hotelId), 0)")
    fun observeIsFavorite(hotelId: String): Flow<Boolean>

    // ------------------------------------------------------------------ writes

    /**
     * Upsert, not insert. The primary key is the supplier's hotel id, so re-fetching a
     * hotel already cached refreshes it in place. This is the whole duplicate story
     * for the hotels table.
     *
     * Prefer [upsertHotelsPreservingFavorites] for anything coming off the network.
     */
    @Upsert
    suspend fun upsertHotels(hotels: List<HotelEntity>)

    /**
     * The network never knows whether a hotel is a favourite, so a plain upsert of a
     * freshly fetched row would silently clear the flag the moment the user scrolled
     * past their own favourite. This reads the flags first and carries them over.
     */
    @Transaction
    suspend fun upsertHotelsPreservingFavorites(hotels: List<HotelEntity>) {
        val favorites = favoriteIdsNow().toSet()
        upsertHotels(hotels.map { if (it.id in favorites) it.copy(isFavorite = true) else it })
    }

    @Query("SELECT id FROM hotels WHERE isFavorite = 1")
    suspend fun favoriteIdsNow(): List<String>

    @Upsert
    suspend fun upsertHotelDetail(detail: HotelDetailEntity)

    @Upsert
    suspend fun upsertResultSet(resultSet: ResultSetEntity)

    /** Composite key on (cacheKey, hotelId) means a repeated page cannot repeat a row. */
    @Upsert
    suspend fun upsertResultSetEntries(entries: List<ResultSetEntryEntity>)

    @Query("UPDATE hotels SET isFavorite = :favorite WHERE id = :hotelId")
    suspend fun setFavorite(hotelId: String, favorite: Boolean)

    @Query("DELETE FROM result_set_entries WHERE cacheKey = :cacheKey")
    suspend fun clearResultSetEntries(cacheKey: String)

    @Query("DELETE FROM result_sets WHERE cacheKey = :cacheKey")
    suspend fun deleteResultSet(cacheKey: String)

    /**
     * Drops cached hotels nothing refers to any more.
     *
     * Favourites are exempt unconditionally. That exemption is what makes the
     * favourites screen work with no network: its rows can never be evicted by a
     * search the user ran afterwards.
     */
    @Query(
        """
        DELETE FROM hotels
        WHERE isFavorite = 0
          AND id NOT IN (SELECT hotelId FROM result_set_entries)
        """
    )
    suspend fun evictUnreferencedHotels(): Int

    /** Result sets older than the cut-off, favourites unaffected because they are a flag on the hotel. */
    @Query("DELETE FROM result_sets WHERE lastRefreshedAt < :olderThanEpochMillis")
    suspend fun evictResultSetsOlderThan(olderThanEpochMillis: Long): Int

    // --------------------------------------------------------------- composites

    /**
     * Writes a page atomically: the hotels, their membership, and the updated
     * bookkeeping in one transaction.
     *
     * Without the transaction, a crash between the two writes would leave a result set
     * claiming more loaded rows than it has entries for, and pagination would skip
     * hotels from then on.
     */
    @Transaction
    suspend fun insertPage(
        resultSet: ResultSetEntity,
        hotels: List<HotelEntity>,
        entries: List<ResultSetEntryEntity>,
        replaceExisting: Boolean,
    ) {
        if (replaceExisting) clearResultSetEntries(resultSet.cacheKey)
        upsertResultSet(resultSet)
        upsertHotelsPreservingFavorites(hotels)
        upsertResultSetEntries(entries)
    }

    /**
     * Flips the flag on an existing hotel row and returns the state it ended up in.
     *
     * Read and write are one transaction, so a page load running at the same time
     * cannot read the old flag and write it back over the new one.
     *
     * The row is expected to already be there, and every screen that draws a heart is
     * drawing a hotel it read out of this table. If it is missing the update matches
     * no rows and the flag stays off.
     */
    @Transaction
    suspend fun toggleFavorite(hotelId: String): Boolean {
        val current = isFavoriteNow(hotelId)
        setFavorite(hotelId, !current)
        return !current
    }

    @Query("SELECT COALESCE((SELECT isFavorite FROM hotels WHERE id = :hotelId), 0)")
    suspend fun isFavoriteNow(hotelId: String): Boolean

    @Query("SELECT COUNT(*) FROM hotels")
    suspend fun hotelCount(): Int
}
