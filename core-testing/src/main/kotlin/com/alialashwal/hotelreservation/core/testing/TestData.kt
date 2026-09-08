package com.alialashwal.hotelreservation.core.testing

import com.alialashwal.hotelreservation.model.HotelDetail
import com.alialashwal.hotelreservation.model.HotelSummary
import com.alialashwal.hotelreservation.model.Money

/** Shaped after real LiteAPI responses, so a test reads like the app's actual data. */
fun hotelSummary(
    id: String = "lp516fd",
    name: String = "Kempinski Nile Hotel, Cairo",
    city: String = "Cairo",
    price: String? = "131.28",
) = HotelSummary(
    id = id,
    name = name,
    city = city,
    countryCode = "EG",
    thumbnailUrl = "https://static.cupid.travel/hotels/$id.jpg",
    starRating = 5,
    reviewScore = 9.6,
    reviewCount = 4207,
    nightlyRate = price?.let { Money.of(it, "USD") },
)

fun hotelDetail(
    id: String = "lp516fd",
    name: String = "Kempinski Nile Hotel, Cairo",
    price: String? = "131.28",
) = HotelDetail(
    id = id,
    name = name,
    city = "Cairo",
    countryCode = "EG",
    address = "12 Ahmed Ragheb Street, Garden City",
    description = "On the shore of the Nile, with a rooftop pool.",
    imageUrls = listOf(
        "https://static.cupid.travel/hotels/$id-1.jpg",
        "https://static.cupid.travel/hotels/$id-2.jpg",
    ),
    amenities = listOf("Free WiFi", "Air conditioning", "Fitness center"),
    starRating = 5,
    reviewScore = 9.6,
    reviewCount = 4207,
    coordinates = null,
    checkInFrom = "03:00 PM",
    checkOutBy = "12:00 PM",
    nightlyRate = price?.let { Money.of(it, "USD") },
)

fun hotelPage(count: Int, startAt: Int = 0): List<HotelSummary> =
    (startAt until startAt + count).map { index ->
        hotelSummary(id = "lp$index", name = "Hotel $index")
    }
