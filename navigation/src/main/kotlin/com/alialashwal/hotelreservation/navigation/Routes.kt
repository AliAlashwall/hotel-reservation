package com.alialashwal.hotelreservation.navigation

import kotlinx.serialization.Serializable

/**
 * Every destination in the app, as type-safe Navigation Compose routes.
 *
 * They live in their own module so a feature can navigate to another feature without
 * depending on it. `:feature-hotels` needs [HotelDetailRoute] to open a hotel; it does
 * not need, and must not have, a dependency on `:feature-detail`.
 *
 * Arguments are the smallest thing that identifies the destination, never a whole
 * object. A route survives process death by being written into the saved state, so
 * putting a hotel in one would mean serialising a model into the back stack and then
 * rendering data that may be hours out of date. An id is re-read from the cache.
 */

@Serializable
data object HotelsRoute

@Serializable
data class HotelDetailRoute(val hotelId: String)

@Serializable
data object FavoritesRoute

@Serializable
data class BookingRoute(val hotelId: String)

@Serializable
data class BookingConfirmationRoute(val reference: String)

