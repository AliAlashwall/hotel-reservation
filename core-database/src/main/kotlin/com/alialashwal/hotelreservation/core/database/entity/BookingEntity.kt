package com.alialashwal.hotelreservation.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A confirmed booking.
 *
 * Every line of the money breakdown is stored rather than recomputed on read, so the
 * receipt shown after a restart is the arithmetic the guest actually agreed to, even
 * if the VAT rate or the rounding rules change in a later version of the app.
 *
 * No foreign key to `hotels`: a booking must outlive cache eviction of the hotel it
 * refers to, so the fields it needs are copied in.
 */
@Entity(tableName = "bookings")
data class BookingEntity(
    @PrimaryKey val reference: String,
    val hotelId: String,
    val hotelName: String,
    val hotelCity: String,
    val hotelThumbnailUrl: String?,
    val checkInEpochDay: Long,
    val checkOutEpochDay: Long,
    val rooms: Int,
    val nights: Int,
    val currency: String,
    val nightlyRate: String,
    val baseAmount: String,
    val vatRate: String,
    val vatAmount: String,
    val total: String,
    val createdAtEpochMillis: Long,
)
