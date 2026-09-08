package com.alialashwal.hotelreservation.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.alialashwal.hotelreservation.core.database.entity.BookingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookingDao {

    @Upsert
    suspend fun upsert(booking: BookingEntity)

    @Query("SELECT * FROM bookings WHERE reference = :reference")
    fun observeByReference(reference: String): Flow<BookingEntity?>

    @Query("SELECT * FROM bookings ORDER BY createdAtEpochMillis DESC")
    fun observeAll(): Flow<List<BookingEntity>>
}
