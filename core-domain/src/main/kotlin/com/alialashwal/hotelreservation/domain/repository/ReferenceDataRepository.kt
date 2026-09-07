package com.alialashwal.hotelreservation.domain.repository

import com.alialashwal.hotelreservation.model.Country
import com.alialashwal.hotelreservation.model.Outcome

/**
 * The country and city lists that populate the filter controls.
 *
 * Kept apart from [HotelRepository] because it has a different lifetime: this data
 * changes about never, so it is cached indefinitely rather than on the refresh window
 * that hotel results use.
 */
interface ReferenceDataRepository {

    suspend fun countries(): Outcome<List<Country>>

    suspend fun cities(countryCode: String): Outcome<List<String>>
}
