package com.alialashwal.hotelreservation.feature.detail

import com.alialashwal.hotelreservation.model.AppError
import com.alialashwal.hotelreservation.model.HotelDetail

data class HotelDetailState(
    val hotelId: String = "",
    val hotel: HotelDetail? = null,
    val isFavorite: Boolean = false,
    val isLoading: Boolean = false,
    val error: AppError? = null,
) {
    /**
     * Content wins over a failure whenever a cached copy exists. That is the offline
     * story on this screen: a hotel opened before is readable with no network, and the
     * refresh failure stays quiet rather than blanking a page the user can already read.
     */
    val phase: DetailPhase
        get() = when {
            hotel != null -> DetailPhase.Content
            isLoading -> DetailPhase.Loading
            error != null -> DetailPhase.Error
            else -> DetailPhase.Loading
        }

    /** A booking needs a price. Without one there is nothing to quote. */
    val canBook: Boolean get() = hotel?.nightlyRate != null
}

enum class DetailPhase { Loading, Content, Error }

sealed interface HotelDetailIntent {
    data object Retry : HotelDetailIntent
    data object ToggleFavorite : HotelDetailIntent
    data object BookClicked : HotelDetailIntent
}

sealed interface HotelDetailEffect {
    data class OpenBooking(val hotelId: String) : HotelDetailEffect
}
