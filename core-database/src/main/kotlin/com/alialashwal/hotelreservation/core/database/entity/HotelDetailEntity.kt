package com.alialashwal.hotelreservation.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * The heavy part of a hotel, kept apart from [HotelEntity] because it is fetched one
 * hotel at a time and a list query has no use for a description or twenty image URLs.
 *
 * Cascade delete means evicting a hotel cannot leave an orphaned detail row behind.
 */
@Entity(
    tableName = "hotel_details",
    foreignKeys = [
        ForeignKey(
            entity = HotelEntity::class,
            parentColumns = ["id"],
            childColumns = ["hotelId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class HotelDetailEntity(
    @PrimaryKey val hotelId: String,
    val address: String,
    val description: String,
    /** Newline separated. A join table for image URLs would buy nothing here. */
    val imageUrls: String,
    val amenities: String,
    val latitude: Double?,
    val longitude: Double?,
    val checkInFrom: String?,
    val checkOutBy: String?,
    val cachedAt: Long,
)
