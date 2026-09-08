package com.alialashwal.hotelreservation.feature.hotels

import com.alialashwal.hotelreservation.model.AppError
import com.alialashwal.hotelreservation.model.Country
import com.alialashwal.hotelreservation.model.HotelFilters
import com.alialashwal.hotelreservation.model.HotelSummary
import com.alialashwal.hotelreservation.model.PriceRange
import java.time.Instant

/**
 * One immutable value describing the whole screen.
 *
 * This is the reason for choosing MVI here. Search text, four filters, a page cursor,
 * three loading flags and a staleness banner all have to agree with each other, and
 * with MVVM each would be its own observable that a reader has to mentally join. Here
 * an inconsistent screen is an unrepresentable value.
 */
data class HotelListState(
    /** What is in the text field right now, which leads the applied filter by the debounce. */
    val query: String = "",
    val filters: HotelFilters = HotelFilters(),
    val hotels: List<HotelSummary> = emptyList(),
    val favoriteIds: Set<String> = emptySet(),

    val isRefreshing: Boolean = false,
    val isAppending: Boolean = false,
    val isLastPage: Boolean = false,

    /** True when the rows on screen came from cache older than the refresh window. */
    val isStale: Boolean = false,
    val lastRefreshedAt: Instant? = null,
    /** False only before anything has ever been cached, which is what tells an empty
     *  screen apart from a search that genuinely matched nothing. */
    val hasCachedData: Boolean = false,

    /** A failure that owns the whole screen, because there is nothing else to show. */
    val refreshError: AppError? = null,
    /** A failure of the next page only. The rows already loaded stay usable. */
    val appendError: AppError? = null,

    val availableCountries: List<Country> = emptyList(),
    val availableCities: List<String> = emptyList(),
    val isFilterSheetOpen: Boolean = false,
) {

    /**
     * Which of the five list states the task names is currently true.
     *
     * Derived rather than stored, so it can never contradict the fields it is derived
     * from. Content wins over everything: once there are rows, a failed refresh is a
     * message beside the list, not a replacement for it.
     */
    val phase: ListPhase
        get() = when {
            hotels.isNotEmpty() -> ListPhase.Content
            isRefreshing -> ListPhase.Loading
            refreshError != null -> ListPhase.Error
            hasCachedData -> ListPhase.Empty
            else -> ListPhase.Loading
        }

    /**
     * How many filters are on, for the badge. The search text is not one of them: it has
     * its own visible field, so counting it would tell the user something they can
     * already see.
     */
    val activeFilterCount: Int
        get() = listOf(
            filters.city != null,
            filters.minReviewScore != null,
            !filters.priceRange.isUnbounded,
        ).count { it }

    val hasActiveFilters: Boolean get() = activeFilterCount > 0
}

enum class ListPhase { Loading, Content, Empty, Error }

sealed interface HotelListIntent {
    data class QueryChanged(val query: String) : HotelListIntent
    data class CountrySelected(val countryCode: String) : HotelListIntent
    data class CitySelected(val city: String?) : HotelListIntent
    data class MinScoreSelected(val minScore: Double?) : HotelListIntent
    data class PriceRangeSelected(val range: PriceRange) : HotelListIntent
    data object ClearFilters : HotelListIntent
    data class FilterSheetToggled(val open: Boolean) : HotelListIntent

    data object Refresh : HotelListIntent
    /** Sent as the user approaches the end of the list. Safe to send repeatedly. */
    data object LoadMore : HotelListIntent
    data object RetryAppend : HotelListIntent

    data class ToggleFavorite(val hotelId: String) : HotelListIntent
    data class HotelClicked(val hotelId: String) : HotelListIntent
}

/**
 * Things that happen once and must not be replayed on rotation.
 *
 * Navigation is the clearest example: leaving it in the state would reopen the details
 * screen every time the state is re-collected after a configuration change.
 */
sealed interface HotelListEffect {
    data class OpenHotel(val hotelId: String) : HotelListEffect
}
