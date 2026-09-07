package com.alialashwal.hotelreservation.core.network

import com.alialashwal.hotelreservation.model.Country
import com.alialashwal.hotelreservation.model.HotelDetail
import com.alialashwal.hotelreservation.model.HotelFilters
import com.alialashwal.hotelreservation.model.HotelSummary
import com.alialashwal.hotelreservation.model.Money
import com.alialashwal.hotelreservation.model.Outcome
import com.alialashwal.hotelreservation.model.Page
import java.time.LocalDate

/**
 * The only thing the data layer sees of the network. Speaks in domain types, so no DTO
 * and no Retrofit type ever escapes this module, and swapping LiteAPI for another
 * supplier is a change confined to `:core-network`.
 */
interface HotelRemoteDataSource {

    /**
     * One page of the catalogue. Rows come back with [HotelSummary.nightlyRate] unset,
     * because prices live behind a second call. Pairing them is the data layer's job.
     */
    suspend fun hotels(filters: HotelFilters, offset: Int, limit: Int): Outcome<Page<HotelSummary>>

    /**
     * The cheapest nightly rate per hotel, for one room on one night starting at
     * [probe]. Hotels with no availability are absent from the map rather than present
     * with a zero.
     *
     * A single night is asked for on purpose. It gives a per-night figure the app can
     * multiply by nights and rooms itself, so the total a guest sees always equals the
     * arithmetic printed next to it. Asking for the real multi-night stay would return
     * a supplier total that no visible sum reproduces.
     */
    suspend fun nightlyRates(hotelIds: List<String>, probe: LocalDate, currency: String): Outcome<Map<String, Money>>

    suspend fun hotelDetail(hotelId: String): Outcome<HotelDetail>

    suspend fun countries(): Outcome<List<Country>>

    suspend fun cities(countryCode: String): Outcome<List<String>>
}
