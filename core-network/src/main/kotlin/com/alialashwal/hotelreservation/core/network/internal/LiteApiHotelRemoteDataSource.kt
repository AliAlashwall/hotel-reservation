package com.alialashwal.hotelreservation.core.network.internal

import com.alialashwal.hotelreservation.core.network.HotelRemoteDataSource
import com.alialashwal.hotelreservation.core.network.dto.OccupancyDto
import com.alialashwal.hotelreservation.core.network.dto.RatesRequestDto
import com.alialashwal.hotelreservation.model.Country
import com.alialashwal.hotelreservation.model.HotelDetail
import com.alialashwal.hotelreservation.model.HotelFilters
import com.alialashwal.hotelreservation.model.HotelSummary
import com.alialashwal.hotelreservation.model.Money
import com.alialashwal.hotelreservation.model.Outcome
import com.alialashwal.hotelreservation.model.Page
import com.alialashwal.hotelreservation.model.map
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class LiteApiHotelRemoteDataSource @Inject constructor(
    private val service: LiteApiService,
    private val json: Json,
) : HotelRemoteDataSource {

    override suspend fun hotels(
        filters: HotelFilters,
        offset: Int,
        limit: Int,
    ): Outcome<Page<HotelSummary>> = apiCall(json) {
        val envelope = service.hotels(
            countryCode = filters.countryCode,
            cityName = filters.city?.takeIf { it.isNotBlank() },
            hotelName = filters.query.trim().takeIf { it.isNotBlank() },
            minRating = filters.minReviewScore,
            limit = limit,
            offset = offset,
        )
        Page(
            items = envelope.data,
            offset = offset,
            // The catalogue omits `total` on an empty result. Falling back to the
            // offset marks the set finished, which is exactly right when nothing came
            // back, and never claims there is more to fetch than there is.
            total = envelope.total ?: (offset + envelope.data.size),
        )
    }.map { page -> page.map { it.toDomain() } }

    override suspend fun nightlyRates(
        hotelIds: List<String>,
        probe: LocalDate,
        currency: String,
    ): Outcome<Map<String, Money>> {
        if (hotelIds.isEmpty()) return Outcome.Success(emptyMap())

        return apiCall(json) {
            service.minRates(
                RatesRequestDto(
                    hotelIds = hotelIds,
                    checkin = probe.format(API_DATE),
                    checkout = probe.plusDays(1).format(API_DATE),
                    currency = currency,
                    guestNationality = GUEST_NATIONALITY,
                    occupancies = listOf(OccupancyDto(adults = ADULTS_PER_ROOM)),
                )
            )
        }.map { envelope ->
            envelope.data
                .mapNotNull { rate ->
                    val price = rate.price?.takeIf { it > 0.0 } ?: return@mapNotNull null
                    rate.hotelId to Money.of(price, currency)
                }
                .toMap()
        }
    }

    override suspend fun hotelDetail(hotelId: String): Outcome<HotelDetail> =
        apiCall(json) { service.hotel(hotelId).data.toDomain() }

    override suspend fun countries(): Outcome<List<Country>> =
        apiCall(json) { service.countries().data.map { it.toDomain() } }

    override suspend fun cities(countryCode: String): Outcome<List<String>> =
        apiCall(json) {
            service.cities(countryCode).data
                .map { it.city }
                .filter { it.isNotBlank() }
                .distinct()
                .sorted()
        }

    private companion object {
        val API_DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

        /**
         * Rates are quoted per nationality. The sandbox needs a value and the app has
         * no sign-in, so one is fixed here. A real app would take it from the account.
         */
        const val GUEST_NATIONALITY = "US"

        /** Standard double occupancy, so a quoted room rate is comparable across hotels. */
        const val ADULTS_PER_ROOM = 2
    }
}
