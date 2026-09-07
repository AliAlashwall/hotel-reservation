package com.alialashwal.hotelreservation.core.network.internal

import com.alialashwal.hotelreservation.core.network.dto.CityDto
import com.alialashwal.hotelreservation.core.network.dto.CountryDto
import com.alialashwal.hotelreservation.core.network.dto.HotelDetailDto
import com.alialashwal.hotelreservation.core.network.dto.HotelListItemDto
import com.alialashwal.hotelreservation.core.network.dto.ListEnvelope
import com.alialashwal.hotelreservation.core.network.dto.MinRateDto
import com.alialashwal.hotelreservation.core.network.dto.ObjectEnvelope
import com.alialashwal.hotelreservation.core.network.dto.RatesRequestDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

internal interface LiteApiService {

    /**
     * `minRating` and `starRating` are honoured by the server. `minPrice` and
     * `maxPrice` exist in the documentation but are ignored, which is why they are
     * absent here and the price band is applied in the data layer instead.
     */
    @GET("data/hotels")
    suspend fun hotels(
        @Query("countryCode") countryCode: String,
        @Query("cityName") cityName: String?,
        @Query("hotelName") hotelName: String?,
        @Query("minRating") minRating: Double?,
        @Query("limit") limit: Int,
        @Query("offset") offset: Int,
    ): ListEnvelope<HotelListItemDto>

    @GET("data/hotel")
    suspend fun hotel(
        @Query("hotelId") hotelId: String,
    ): ObjectEnvelope<HotelDetailDto>

    @GET("data/countries")
    suspend fun countries(): ListEnvelope<CountryDto>

    @GET("data/cities")
    suspend fun cities(
        @Query("countryCode") countryCode: String,
    ): ListEnvelope<CityDto>

    @POST("hotels/min-rates")
    suspend fun minRates(
        @Body request: RatesRequestDto,
    ): ListEnvelope<MinRateDto>
}
