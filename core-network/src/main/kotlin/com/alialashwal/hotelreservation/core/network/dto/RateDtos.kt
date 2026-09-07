package com.alialashwal.hotelreservation.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
internal data class RatesRequestDto(
    val hotelIds: List<String>,
    val checkin: String,
    val checkout: String,
    val currency: String,
    val guestNationality: String,
    val occupancies: List<OccupancyDto>,
)

@Serializable
internal data class OccupancyDto(
    val adults: Int,
)

/**
 * `POST /hotels/min-rates`.
 *
 * The app deliberately does not use `POST /hotels/rates`, which returns the full room
 * inventory. That response measures roughly 600 KB per hotel, so a twenty-row page
 * would pull about twelve megabytes to show twenty prices. This endpoint returns the
 * cheapest offer per hotel in about 1.3 KB each.
 *
 * It carries no currency of its own, so the caller pairs the number with the currency
 * it asked for in the request.
 */
@Serializable
internal data class MinRateDto(
    val hotelId: String,
    val price: Double? = null,
)
