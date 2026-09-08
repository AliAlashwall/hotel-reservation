package com.alialashwal.hotelreservation.core.data.di

import com.alialashwal.hotelreservation.core.data.internal.DefaultBookingRepository
import com.alialashwal.hotelreservation.core.data.internal.DefaultFavoritesRepository
import com.alialashwal.hotelreservation.core.data.internal.DefaultHotelRepository
import com.alialashwal.hotelreservation.core.data.internal.DefaultReferenceDataRepository
import com.alialashwal.hotelreservation.domain.repository.BookingRepository
import com.alialashwal.hotelreservation.domain.repository.FavoritesRepository
import com.alialashwal.hotelreservation.domain.repository.HotelRepository
import com.alialashwal.hotelreservation.domain.repository.ReferenceDataRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton
import kotlin.random.Random

/**
 * The seam a test replaces. Swapping this module swaps every repository at once, which
 * is what the end-to-end UI test does to run with no network and no database.
 *
 * Public, along with the implementations it binds, purely so `@TestInstallIn` in the
 * app's instrumented tests can name it. Nothing outside dependency injection refers to
 * these types.
 */
@Module
@InstallIn(SingletonComponent::class)
interface DataModule {

    @Binds
    @Singleton
    fun hotelRepository(impl: DefaultHotelRepository): HotelRepository

    @Binds
    @Singleton
    fun favoritesRepository(impl: DefaultFavoritesRepository): FavoritesRepository

    @Binds
    @Singleton
    fun bookingRepository(impl: DefaultBookingRepository): BookingRepository

    @Binds
    @Singleton
    fun referenceDataRepository(impl: DefaultReferenceDataRepository): ReferenceDataRepository
}

/**
 * Time and randomness are dependencies, not ambient facts.
 *
 * Injecting them is what lets the booking tests assert an exact reference string and an
 * exact "check-in is in the past" boundary, instead of asserting a pattern and hoping
 * the suite never runs at midnight.
 */
@Module
@InstallIn(SingletonComponent::class)
object SystemModule {

    @Provides
    @Singleton
    fun clock(): Clock = Clock.systemDefaultZone()

    @Provides
    @Singleton
    fun random(): Random = Random.Default
}
