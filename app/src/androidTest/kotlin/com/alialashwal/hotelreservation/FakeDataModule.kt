package com.alialashwal.hotelreservation

import com.alialashwal.hotelreservation.core.data.di.DataModule
import com.alialashwal.hotelreservation.core.testing.fake.FakeBookingRepository
import com.alialashwal.hotelreservation.core.testing.fake.FakeFavoritesRepository
import com.alialashwal.hotelreservation.core.testing.fake.FakeHotelRepository
import com.alialashwal.hotelreservation.core.testing.fake.FakeReferenceDataRepository
import com.alialashwal.hotelreservation.domain.repository.BookingRepository
import com.alialashwal.hotelreservation.domain.repository.FavoritesRepository
import com.alialashwal.hotelreservation.domain.repository.HotelRepository
import com.alialashwal.hotelreservation.domain.repository.ReferenceDataRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

/**
 * Replaces every repository with an in-memory fake for the duration of an instrumented
 * test.
 *
 * The whole data layer swaps at one seam, which is the point of having the features
 * depend on `:core-domain` contracts rather than on `:core-data`. The test needs no
 * network, no API key and no database, so it is deterministic and runs offline.
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DataModule::class])
object FakeDataModule {

    @Provides
    @Singleton
    fun hotelRepository(): HotelRepository = FakeHotelRepository()

    @Provides
    @Singleton
    fun favoritesRepository(): FavoritesRepository = FakeFavoritesRepository()

    @Provides
    @Singleton
    fun bookingRepository(): BookingRepository = FakeBookingRepository()

    @Provides
    @Singleton
    fun referenceDataRepository(): ReferenceDataRepository = FakeReferenceDataRepository()
}
