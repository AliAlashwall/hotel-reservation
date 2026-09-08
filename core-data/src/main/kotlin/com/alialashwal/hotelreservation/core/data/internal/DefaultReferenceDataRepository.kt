package com.alialashwal.hotelreservation.core.data.internal

import com.alialashwal.hotelreservation.core.network.HotelRemoteDataSource
import com.alialashwal.hotelreservation.domain.repository.ReferenceDataRepository
import com.alialashwal.hotelreservation.model.Country
import com.alialashwal.hotelreservation.model.Outcome
import com.alialashwal.hotelreservation.model.onSuccess
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Countries and cities, held in memory for the life of the process.
 *
 * Not in Room. This data changes about never, the whole country list is a few kilobytes,
 * and the filter sheet asks for it every time it opens. A table plus a migration plus an
 * eviction rule would buy nothing over a map behind a mutex.
 *
 * The mutex makes the first call authoritative: opening the filter sheet twice quickly
 * fetches once.
 */
@Singleton
class DefaultReferenceDataRepository @Inject constructor(
    private val remote: HotelRemoteDataSource,
) : ReferenceDataRepository {

    private val lock = Mutex()
    private var cachedCountries: List<Country>? = null
    private val cachedCities = mutableMapOf<String, List<String>>()

    override suspend fun countries(): Outcome<List<Country>> = lock.withLock {
        cachedCountries?.let { return@withLock Outcome.Success(it) }
        remote.countries()
            .onSuccess { cachedCountries = it }
    }

    override suspend fun cities(countryCode: String): Outcome<List<String>> = lock.withLock {
        val key = countryCode.uppercase()
        cachedCities[key]?.let { return@withLock Outcome.Success(it) }
        remote.cities(key)
            .onSuccess { cachedCities[key] = it }
    }
}
