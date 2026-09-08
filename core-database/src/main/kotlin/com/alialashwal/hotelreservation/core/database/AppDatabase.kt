package com.alialashwal.hotelreservation.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.alialashwal.hotelreservation.core.database.dao.BookingDao
import com.alialashwal.hotelreservation.core.database.dao.HotelDao
import com.alialashwal.hotelreservation.core.database.entity.BookingEntity
import com.alialashwal.hotelreservation.core.database.entity.HotelDetailEntity
import com.alialashwal.hotelreservation.core.database.entity.HotelEntity
import com.alialashwal.hotelreservation.core.database.entity.ResultSetEntity
import com.alialashwal.hotelreservation.core.database.entity.ResultSetEntryEntity

@Database(
    entities = [
        HotelEntity::class,
        HotelDetailEntity::class,
        ResultSetEntity::class,
        ResultSetEntryEntity::class,
        BookingEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun hotelDao(): HotelDao
    abstract fun bookingDao(): BookingDao
}
