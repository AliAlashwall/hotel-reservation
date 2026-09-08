package com.alialashwal.hotelreservation.core.database.di

import android.content.Context
import androidx.room.Room
import com.alialashwal.hotelreservation.core.database.AppDatabase
import com.alialashwal.hotelreservation.core.database.dao.BookingDao
import com.alialashwal.hotelreservation.core.database.dao.HotelDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun appDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME).build()

    @Provides
    fun hotelDao(database: AppDatabase): HotelDao = database.hotelDao()

    @Provides
    fun bookingDao(database: AppDatabase): BookingDao = database.bookingDao()

    private const val DATABASE_NAME = "hotel_reservation.db"
}
