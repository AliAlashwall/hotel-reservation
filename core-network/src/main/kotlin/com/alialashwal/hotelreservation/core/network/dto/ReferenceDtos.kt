package com.alialashwal.hotelreservation.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
internal data class CountryDto(
    val code: String,
    val name: String? = null,
)

@Serializable
internal data class CityDto(
    val city: String,
)
