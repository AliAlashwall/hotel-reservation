package com.alialashwal.hotelreservation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The critical journey, end to end through the real navigation graph and the real
 * ViewModels, with only the data layer faked.
 *
 * It follows the requirement the task states most explicitly about cross-screen
 * behaviour: a hotel favourited on the details screen has to be a favourite on the
 * list and on the favourites tab, with no synchronisation code between them.
 */
@HiltAndroidTest
class HotelJourneyTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun favouriting_a_hotel_from_its_details_shows_it_on_the_favourites_tab() {
        composeRule.waitUntil(TIMEOUT_MS) {
            composeRule.onAllNodesWithText("Hotel 0").fetchSemanticsNodes().isNotEmpty()
        }

        // List to details.
        composeRule.onNodeWithText("Hotel 0").performClick()
        composeRule.waitUntil(TIMEOUT_MS) {
            composeRule.onAllNodesWithText(AMENITY).fetchSemanticsNodes().isNotEmpty()
        }

        // Favourite it from the details screen.
        composeRule.onNodeWithContentDescription(ADD_TO_FAVOURITES).performClick()
        composeRule.waitUntil(TIMEOUT_MS) {
            composeRule.onAllNodes(hasContentDescription(REMOVE_FROM_FAVOURITES))
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Back out and switch tabs.
        composeRule.onNodeWithContentDescription(BACK).performClick()
        composeRule.onNodeWithText(FAVOURITES_TAB).performClick()

        // It is there, without the favourites screen having been told anything.
        composeRule.waitUntil(TIMEOUT_MS) {
            composeRule.onAllNodesWithText("Hotel 0").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Hotel 0").assertIsDisplayed()
    }

    @Test
    fun searching_narrows_the_list_and_clearing_it_restores_the_full_list() {
        composeRule.waitUntil(TIMEOUT_MS) {
            composeRule.onAllNodesWithText("Hotel 0").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText(SEARCH_HINT).performTextInput("Hotel 12")

        // Waits for the result rather than sleeping, because the search is debounced.
        composeRule.waitUntil(TIMEOUT_MS) {
            composeRule.onAllNodesWithText("Hotel 12").fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithText("Hotel 0").fetchSemanticsNodes().isEmpty()
        }

        composeRule.onNodeWithContentDescription(CLEAR_SEARCH).performClick()

        composeRule.waitUntil(TIMEOUT_MS) {
            composeRule.onAllNodesWithText("Hotel 0").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Hotel 0").assertIsDisplayed()
    }

    @Test
    fun the_booking_screen_refuses_to_confirm_before_dates_are_chosen() {
        composeRule.waitUntil(TIMEOUT_MS) {
            composeRule.onAllNodesWithText("Hotel 0").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("Hotel 0").performClick()
        composeRule.waitUntil(TIMEOUT_MS) {
            composeRule.onAllNodesWithText(BOOK).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(BOOK).performClick()

        composeRule.waitUntil(TIMEOUT_MS) {
            composeRule.onAllNodesWithText(CONFIRM).fetchSemanticsNodes().isNotEmpty()
        }

        // Pressing Confirm with no dates must surface the validation, not book anything.
        composeRule.onNodeWithText(CONFIRM).performClick()
        composeRule.waitUntil(TIMEOUT_MS) {
            composeRule.onAllNodesWithText(CHECK_IN_MISSING).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithText(CHECK_IN_MISSING).onFirst().assertIsDisplayed()
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L

        const val AMENITY = "Free WiFi"
        const val ADD_TO_FAVOURITES = "Add to favourites"
        const val REMOVE_FROM_FAVOURITES = "Remove from favourites"
        const val BACK = "Back"
        const val FAVOURITES_TAB = "Favourites"
        const val SEARCH_HINT = "Search hotels by name"
        const val CLEAR_SEARCH = "Clear search"
        const val BOOK = "Book this hotel"
        const val CONFIRM = "Confirm booking"
        const val CHECK_IN_MISSING = "Choose a check-in date."
    }
}
