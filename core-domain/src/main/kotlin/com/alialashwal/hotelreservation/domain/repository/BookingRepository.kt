package com.alialashwal.hotelreservation.domain.repository

import com.alialashwal.hotelreservation.model.Booking
import com.alialashwal.hotelreservation.model.BookingReference
import kotlinx.coroutines.flow.Flow

/**
 * Confirmed bookings, stored locally. There is no booking backend in this app, so the
 * local record is the record.
 *
 * Persisting rather than holding in memory is what lets the success screen survive
 * process death: it is addressed by reference and re-reads the row.
 */
interface BookingRepository {

    suspend fun save(booking: Booking)

    fun observeBooking(reference: BookingReference): Flow<Booking?>
}
