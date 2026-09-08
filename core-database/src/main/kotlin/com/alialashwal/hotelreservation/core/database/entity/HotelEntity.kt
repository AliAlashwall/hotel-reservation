package com.alialashwal.hotelreservation.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row per hotel, for the whole app.
 *
 * There is deliberately no second table for favourites and no third for search
 * results. A hotel the user favourited, a hotel in the current search, and a hotel on
 * the details screen are the same row. That is what keeps the three screens in step
 * with no synchronisation code, and it is what makes [isFavorite] a single source of
 * truth rather than a value two tables can disagree about.
 *
 * The primary key is the supplier's own hotel id, so re-fetching a page that overlaps
 * one already cached updates rows instead of duplicating them.
 */
@Entity(
    tableName = "hotels",
    indices = [Index("isFavorite")],
)
data class HotelEntity(
    @PrimaryKey val id: String,
    val name: String,
    val city: String,
    val countryCode: String,
    val thumbnailUrl: String?,
    val starRating: Int?,
    val reviewScore: Double?,
    val reviewCount: Int,
    /** Stored as the plain string of a BigDecimal, so no precision is lost on the way to SQLite. */
    val priceAmount: String?,
    val priceCurrency: String?,
    @ColumnInfo(defaultValue = "0") val isFavorite: Boolean,
    /** Epoch millis of the last write from the network. Drives the staleness banner. */
    val cachedAt: Long,
)
