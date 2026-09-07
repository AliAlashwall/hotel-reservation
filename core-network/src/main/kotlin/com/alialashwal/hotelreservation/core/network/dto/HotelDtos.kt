package com.alialashwal.hotelreservation.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * LiteAPI wraps every successful payload in a `data` field. `total` is present on the
 * catalogue endpoint and is the only trustworthy end-of-list signal, because the app
 * filters by price after the fact and can shrink a page the server already sized.
 */
@Serializable
internal data class ListEnvelope<T>(
    val data: List<T> = emptyList(),
    val total: Int? = null,
)

@Serializable
internal data class ObjectEnvelope<T>(
    val data: T,
)

/** Error bodies come back on non-2xx responses, in two slightly different shapes. */
@Serializable
internal data class ErrorEnvelope(
    val error: ErrorBody? = null,
)

@Serializable
internal data class ErrorBody(
    val code: Int? = null,
    val message: String? = null,
    val description: String? = null,
)

/** One row of `GET /data/hotels`. Only the fields the list actually renders. */
@Serializable
internal data class HotelListItemDto(
    val id: String,
    val name: String? = null,
    val city: String? = null,
    val country: String? = null,
    val address: String? = null,
    @SerialName("main_photo") val mainPhoto: String? = null,
    val thumbnail: String? = null,
    val stars: Int? = null,
    val rating: Double? = null,
    val reviewCount: Int? = null,
)

/** `GET /data/hotel`. Note that this endpoint calls the star rating `starRating`,
 *  while the list endpoint calls the same thing `stars`. */
@Serializable
internal data class HotelDetailDto(
    val id: String,
    val name: String? = null,
    val city: String? = null,
    val country: String? = null,
    val address: String? = null,
    val hotelDescription: String? = null,
    val hotelImages: List<HotelImageDto> = emptyList(),
    @SerialName("main_photo") val mainPhoto: String? = null,
    val hotelFacilities: List<String> = emptyList(),
    val starRating: Int? = null,
    val rating: Double? = null,
    val reviewCount: Int? = null,
    val location: LocationDto? = null,
    val checkinCheckoutTimes: CheckTimesDto? = null,
)

@Serializable
internal data class HotelImageDto(
    val url: String? = null,
    val urlHd: String? = null,
    val order: Int? = null,
)

@Serializable
internal data class LocationDto(
    val latitude: Double? = null,
    val longitude: Double? = null,
)

@Serializable
internal data class CheckTimesDto(
    @SerialName("checkin_start") val checkInStart: String? = null,
    val checkout: String? = null,
)
