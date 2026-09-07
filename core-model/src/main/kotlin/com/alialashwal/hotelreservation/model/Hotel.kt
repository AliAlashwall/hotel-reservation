package com.alialashwal.hotelreservation.model

/**
 * A hotel as it appears in a list row.
 *
 * [nightlyRate] is nullable because it does not come from the same call as the rest
 * of the row. Names and photos come from the catalogue endpoint; prices come from a
 * separate rates call that can legitimately return nothing for a property on the
 * dates we probe. A missing price is a normal state to render, not an error.
 */
data class HotelSummary(
    val id: String,
    val name: String,
    val city: String,
    val countryCode: String,
    val thumbnailUrl: String?,
    val starRating: Int?,
    val reviewScore: Double?,
    val reviewCount: Int,
    val nightlyRate: Money?,
)

/** Everything the details screen shows. Fetched one hotel at a time. */
data class HotelDetail(
    val id: String,
    val name: String,
    val city: String,
    /** ISO 3166-1 alpha-2, as the API returns it. Rendered through `Locale` so the
     *  screen shows "Egypt" without a second network call to resolve the name. */
    val countryCode: String,
    val address: String,
    val description: String,
    val imageUrls: List<String>,
    val amenities: List<String>,
    val starRating: Int?,
    val reviewScore: Double?,
    val reviewCount: Int,
    val coordinates: Coordinates?,
    val checkInFrom: String?,
    val checkOutBy: String?,
    val nightlyRate: Money?,
)

data class Coordinates(val latitude: Double, val longitude: Double)

data class Country(val code: String, val name: String)
