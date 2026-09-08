package com.alialashwal.hotelreservation.core.data

import com.alialashwal.hotelreservation.core.network.HotelRemoteDataSource
import com.alialashwal.hotelreservation.model.AppError
import com.alialashwal.hotelreservation.model.Country
import com.alialashwal.hotelreservation.model.HotelDetail
import com.alialashwal.hotelreservation.model.HotelFilters
import com.alialashwal.hotelreservation.model.HotelSummary
import com.alialashwal.hotelreservation.model.Money
import com.alialashwal.hotelreservation.model.Outcome
import com.alialashwal.hotelreservation.model.Page
import kotlinx.coroutines.CompletableDeferred
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger

/**
 * A catalogue of [catalogueSize] hotels that pages correctly, so a test can assert on
 * real pagination arithmetic rather than on a canned two-item response.
 */
class FakeHotelRemoteDataSource(
    var catalogueSize: Int = 50,
) : HotelRemoteDataSource {

    /** Set to make the catalogue call fail. */
    var hotelsError: AppError? = null

    /** Set to make the rates call fail, leaving hotels priced at null. */
    var ratesError: AppError? = null

    var detailError: AppError? = null

    /** Price for hotel n, keyed by id. Absent means the supplier had no availability. */
    var priceFor: (String) -> String? = { "100.00" }

    /** When set, the catalogue call suspends until this completes. Used to overlap calls. */
    var gate: CompletableDeferred<Unit>? = null

    val hotelsCalls = AtomicInteger(0)
    val ratesCalls = AtomicInteger(0)
    val requestedOffsets = mutableListOf<Int>()

    override suspend fun hotels(filters: HotelFilters, offset: Int, limit: Int): Outcome<Page<HotelSummary>> {
        hotelsCalls.incrementAndGet()
        synchronized(requestedOffsets) { requestedOffsets += offset }
        gate?.await()
        hotelsError?.let { return Outcome.Failure(it) }

        val items = (offset until minOf(offset + limit, catalogueSize)).map { index ->
            HotelSummary(
                id = "lp$index",
                name = "Hotel $index",
                city = "Cairo",
                countryCode = filters.countryCode,
                thumbnailUrl = "https://example.test/$index.jpg",
                starRating = 5,
                reviewScore = 9.0,
                reviewCount = 10,
                nightlyRate = null,
            )
        }
        return Outcome.Success(Page(items = items, offset = offset, total = catalogueSize))
    }

    override suspend fun nightlyRates(
        hotelIds: List<String>,
        probe: LocalDate,
        currency: String,
    ): Outcome<Map<String, Money>> {
        ratesCalls.incrementAndGet()
        ratesError?.let { return Outcome.Failure(it) }
        return Outcome.Success(
            hotelIds.mapNotNull { id -> priceFor(id)?.let { id to Money.of(it, currency) } }.toMap()
        )
    }

    override suspend fun hotelDetail(hotelId: String): Outcome<HotelDetail> {
        detailError?.let { return Outcome.Failure(it) }
        return Outcome.Success(
            HotelDetail(
                id = hotelId,
                name = "Hotel $hotelId",
                city = "Cairo",
                countryCode = "EG",
                address = "12 Ahmed Ragheb Street",
                description = "On the Nile.",
                imageUrls = listOf("https://example.test/$hotelId-1.jpg"),
                amenities = listOf("Free WiFi"),
                starRating = 5,
                reviewScore = 9.6,
                reviewCount = 4207,
                coordinates = null,
                checkInFrom = "03:00 PM",
                checkOutBy = "12:00 PM",
                nightlyRate = null,
            )
        )
    }

    override suspend fun countries(): Outcome<List<Country>> =
        Outcome.Success(listOf(Country("EG", "Egypt")))

    override suspend fun cities(countryCode: String): Outcome<List<String>> =
        Outcome.Success(listOf("Cairo"))
}
