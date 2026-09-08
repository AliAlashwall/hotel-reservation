package com.alialashwal.hotelreservation.core.testing.fake

import com.alialashwal.hotelreservation.domain.repository.BookingRepository
import com.alialashwal.hotelreservation.model.Booking
import com.alialashwal.hotelreservation.model.BookingReference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeBookingRepository : BookingRepository {

    private val stored = MutableStateFlow<List<Booking>>(emptyList())

    val saved: List<Booking> get() = stored.value

    override suspend fun save(booking: Booking) {
        stored.value = stored.value.filterNot { it.reference == booking.reference } + booking
    }

    override fun observeBooking(reference: BookingReference): Flow<Booking?> =
        stored.map { list -> list.firstOrNull { it.reference == reference } }
}
