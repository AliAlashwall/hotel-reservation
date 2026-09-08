package com.alialashwal.hotelreservation.core.data.internal

import com.alialashwal.hotelreservation.core.database.dao.BookingDao
import com.alialashwal.hotelreservation.domain.repository.BookingRepository
import com.alialashwal.hotelreservation.model.Booking
import com.alialashwal.hotelreservation.model.BookingReference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultBookingRepository @Inject constructor(
    private val dao: BookingDao,
) : BookingRepository {

    override suspend fun save(booking: Booking) = dao.upsert(booking.toEntity())

    override fun observeBooking(reference: BookingReference): Flow<Booking?> =
        dao.observeByReference(reference.value).map { it?.toDomain() }

    override fun observeBookings(): Flow<List<Booking>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }
}
