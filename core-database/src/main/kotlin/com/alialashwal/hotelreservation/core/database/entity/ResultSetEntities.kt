package com.alialashwal.hotelreservation.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Bookkeeping for one paged result set, identified by the filters that produced it.
 *
 * Holding [total] and [loadedCount] in the database rather than in a ViewModel is what
 * lets pagination survive process death: after the app is killed and reopened, the
 * repository knows how far it had got without re-fetching from offset zero.
 */
@Entity(tableName = "result_sets")
data class ResultSetEntity(
    /** `HotelFilters.cacheKey`. Any filter change is a different result set. */
    @PrimaryKey val cacheKey: String,
    val total: Int,
    /**
     * How many rows the server has handed over, which is not the same as how many
     * survived the price filter. The next offset is this, never the visible row count.
     */
    val loadedCount: Int,
    val lastRefreshedAt: Long,
)

/**
 * Membership of a hotel in a result set, with its position.
 *
 * The composite primary key is the duplicate guard: a hotel can appear at most once in
 * a given result set, so a page that overlaps the previous one, or a retry that
 * re-fetches the same offset, cannot produce a repeated row. Room resolves the
 * conflict by replacing, which also repairs the position if the server reordered.
 */
@Entity(
    tableName = "result_set_entries",
    primaryKeys = ["cacheKey", "hotelId"],
    foreignKeys = [
        ForeignKey(
            entity = ResultSetEntity::class,
            parentColumns = ["cacheKey"],
            childColumns = ["cacheKey"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = HotelEntity::class,
            parentColumns = ["id"],
            childColumns = ["hotelId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("hotelId"), Index("cacheKey", "position")],
)
data class ResultSetEntryEntity(
    val cacheKey: String,
    val hotelId: String,
    /** Server order. The list is rendered by this, not by insertion order. */
    val position: Int,
)
