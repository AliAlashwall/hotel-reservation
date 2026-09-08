package com.alialashwal.hotelreservation.core.testing.fake

import com.alialashwal.hotelreservation.domain.repository.ReferenceDataRepository
import com.alialashwal.hotelreservation.model.AppError
import com.alialashwal.hotelreservation.model.Country
import com.alialashwal.hotelreservation.model.Outcome

class FakeReferenceDataRepository : ReferenceDataRepository {

    var countries: List<Country> = listOf(Country("EG", "Egypt"), Country("AE", "United Arab Emirates"))
    var citiesByCountry: Map<String, List<String>> = mapOf(
        "EG" to listOf("Alexandria", "Aswan", "Cairo"),
        "AE" to listOf("Abu Dhabi", "Dubai"),
    )
    var error: AppError? = null

    override suspend fun countries(): Outcome<List<Country>> =
        error?.let { Outcome.Failure(it) } ?: Outcome.Success(countries)

    override suspend fun cities(countryCode: String): Outcome<List<String>> =
        error?.let { Outcome.Failure(it) } ?: Outcome.Success(citiesByCountry[countryCode].orEmpty())
}
