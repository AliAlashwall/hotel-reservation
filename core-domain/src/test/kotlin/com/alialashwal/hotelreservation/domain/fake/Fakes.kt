package com.alialashwal.hotelreservation.domain.fake

import com.alialashwal.hotelreservation.domain.repository.BookingRepository
import com.alialashwal.hotelreservation.domain.repository.HotelFeed
import com.alialashwal.hotelreservation.domain.repository.HotelRepository
import com.alialashwal.hotelreservation.model.AppError
import com.alialashwal.hotelreservation.model.Booking
import com.alialashwal.hotelreservation.model.BookingReference
import com.alialashwal.hotelreservation.model.HotelDetail
import com.alialashwal.hotelreservation.model.HotelFilters
import com.alialashwal.hotelreservation.model.Money
import com.alialashwal.hotelreservation.model.Outcome
import com.alialashwal.hotelreservation.model.ValidStay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map

/**
 * Kept in this module's own test source set rather than in :core-testing, because
 * :core-testing depends on :core-domain and the reverse edge would be a cycle.
 */
class FakeHotelRepository(
    var rateResult: Outcome<Money> = Outcome.Success(Money.of("100.00", "USD")),
) : HotelRepository {

    var rateRequests: MutableList<Pair<String, ValidStay>> = mutableListOf()

    override fun observeFeed(filters: HotelFilters): Flow<HotelFeed> = emptyFlow()

    override suspend fun refresh(filters: HotelFilters): Outcome<Unit> = Outcome.Success(Unit)

    override suspend fun loadNextPage(filters: HotelFilters): Outcome<Unit> = Outcome.Success(Unit)

    override fun observeDetail(hotelId: String): Flow<HotelDetail?> = emptyFlow()

    override suspend fun refreshDetail(hotelId: String): Outcome<Unit> = Outcome.Success(Unit)

    override suspend fun rateFor(hotelId: String, stay: ValidStay): Outcome<Money> {
        rateRequests += hotelId to stay
        return rateResult
    }
}

class FakeBookingRepository : BookingRepository {

    private val stored = MutableStateFlow<List<Booking>>(emptyList())

    val saved: List<Booking> get() = stored.value

    override suspend fun save(booking: Booking) {
        stored.value += booking
    }

    override fun observeBooking(reference: BookingReference): Flow<Booking?> =
        stored.map { bookings -> bookings.firstOrNull { it.reference == reference } }

    fun clearForTest() {
        stored.value = emptyList()
    }
}

fun hotelDetail(id: String = "lp516fd", name: String = "Kempinski Nile Hotel, Cairo") = HotelDetail(
    id = id,
    name = name,
    city = "Cairo",
    countryCode = "eg",
    address = "12 Ahmed Ragheb Street",
    description = "On the shore of the Nile.",
    imageUrls = listOf("https://example.test/1.jpg", "https://example.test/2.jpg"),
    amenities = listOf("Free WiFi", "Air conditioning"),
    starRating = 5,
    reviewScore = 9.6,
    reviewCount = 4207,
    coordinates = null,
    checkInFrom = "03:00 PM",
    checkOutBy = "12:00 PM",
    nightlyRate = Money.of("131.28", "USD"),
)

fun networkFailure(error: AppError = AppError.NoConnection): Outcome<Money> = Outcome.Failure(error)
