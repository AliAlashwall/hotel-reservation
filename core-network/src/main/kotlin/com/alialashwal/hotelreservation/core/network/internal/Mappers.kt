package com.alialashwal.hotelreservation.core.network.internal

import com.alialashwal.hotelreservation.core.network.dto.CountryDto
import com.alialashwal.hotelreservation.core.network.dto.HotelDetailDto
import com.alialashwal.hotelreservation.core.network.dto.HotelListItemDto
import com.alialashwal.hotelreservation.model.Coordinates
import com.alialashwal.hotelreservation.model.Country
import com.alialashwal.hotelreservation.model.HotelDetail
import com.alialashwal.hotelreservation.model.HotelSummary

/**
 * DTO to domain. Every field the API marks optional is defaulted here rather than in
 * the model, so the rest of the app can rely on a name and a city always being present.
 */
internal fun HotelListItemDto.toDomain(): HotelSummary = HotelSummary(
    id = id,
    name = name?.takeIf { it.isNotBlank() } ?: UNNAMED,
    city = city.orEmpty(),
    countryCode = country.orEmpty().uppercase(),
    thumbnailUrl = thumbnail ?: mainPhoto,
    starRating = stars?.takeIf { it in 1..5 },
    reviewScore = rating?.takeIf { it > 0.0 },
    reviewCount = reviewCount ?: 0,
    nightlyRate = null, // Filled in by the data layer from the rates call.
)

internal fun HotelDetailDto.toDomain(): HotelDetail = HotelDetail(
    id = id,
    name = name?.takeIf { it.isNotBlank() } ?: UNNAMED,
    city = city.orEmpty(),
    countryCode = country.orEmpty().uppercase(),
    address = address.orEmpty(),
    description = hotelDescription.orEmpty().htmlToPlainText(),
    // The catalogue can return several hundred images per hotel and does not always
    // order them. Sorting by `order` keeps the lead photo first; capping stops a
    // details screen from holding an unbounded list of URLs.
    imageUrls = hotelImages
        .sortedBy { it.order ?: Int.MAX_VALUE }
        .mapNotNull { it.urlHd ?: it.url }
        .ifEmpty { listOfNotNull(mainPhoto) }
        .distinct()
        .take(MAX_IMAGES),
    amenities = hotelFacilities.filter { it.isNotBlank() }.distinct(),
    starRating = starRating?.takeIf { it in 1..5 },
    reviewScore = rating?.takeIf { it > 0.0 },
    reviewCount = reviewCount ?: 0,
    coordinates = location?.let { position ->
        val latitude = position.latitude
        val longitude = position.longitude
        if (latitude != null && longitude != null) Coordinates(latitude, longitude) else null
    },
    checkInFrom = checkinCheckoutTimes?.checkInStart?.takeIf { it.isNotBlank() },
    checkOutBy = checkinCheckoutTimes?.checkout?.takeIf { it.isNotBlank() },
    nightlyRate = null,
)

internal fun CountryDto.toDomain(): Country = Country(
    code = code.uppercase(),
    name = name?.takeIf { it.isNotBlank() } ?: code.uppercase(),
)

private const val UNNAMED = "Unnamed property"
private const val MAX_IMAGES = 20
