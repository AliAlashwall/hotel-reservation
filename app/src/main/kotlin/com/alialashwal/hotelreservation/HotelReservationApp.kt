package com.alialashwal.hotelreservation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Hotel
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.alialashwal.hotelreservation.feature.booking.BookingConfirmationRoute
import com.alialashwal.hotelreservation.feature.booking.BookingRoute as BookingScreenRoute
import com.alialashwal.hotelreservation.feature.detail.HotelDetailRoute as HotelDetailScreenRoute
import com.alialashwal.hotelreservation.feature.favorites.FavoritesRoute as FavoritesScreenRoute
import com.alialashwal.hotelreservation.feature.hotels.HotelListRoute
import com.alialashwal.hotelreservation.navigation.BookingConfirmationRoute as ConfirmationDestination
import com.alialashwal.hotelreservation.navigation.BookingRoute
import com.alialashwal.hotelreservation.navigation.FavoritesRoute
import com.alialashwal.hotelreservation.navigation.HotelDetailRoute
import com.alialashwal.hotelreservation.navigation.HotelsRoute
import kotlin.reflect.KClass

/**
 * The composition root for navigation.
 *
 * This is the only place that knows every feature exists. Each feature exposes a single
 * entry composable and takes navigation as lambdas, so no feature can call into another
 * one, and none of them depend on `NavHostController`.
 */
@Composable
fun HotelReservationApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination

    // The bar belongs to the browsing surfaces only. A details, booking or confirmation
    // screen is a task the user is inside, and offering a tab switch mid-task invites
    // losing a half-filled booking form.
    val showBottomBar = TOP_LEVEL.any { entry -> destination?.hasRoute(entry.route) == true }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    TOP_LEVEL.forEach { entry ->
                        val selected = destination?.hasRoute(entry.route) == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navController.navigateToTopLevel(entry) },
                            icon = {
                                Icon(
                                    imageVector = if (selected) entry.selectedIcon else entry.icon,
                                    contentDescription = null,
                                )
                            },
                            label = { Text(stringResource(entry.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = HotelsRoute,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            composable<HotelsRoute> {
                HotelListRoute(
                    onOpenHotel = { hotelId -> navController.navigate(HotelDetailRoute(hotelId)) },
                )
            }

            composable<FavoritesRoute> {
                FavoritesScreenRoute(
                    onOpenHotel = { hotelId -> navController.navigate(HotelDetailRoute(hotelId)) },
                )
            }

            composable<HotelDetailRoute> {
                HotelDetailScreenRoute(
                    onBack = { navController.popBackStack() },
                    onOpenBooking = { hotelId -> navController.navigate(BookingRoute(hotelId)) },
                )
            }

            composable<BookingRoute> {
                BookingScreenRoute(
                    onBack = { navController.popBackStack() },
                    onConfirmed = { reference ->
                        navController.navigate(ConfirmationDestination(reference.value)) {
                            // The booking form must not be reachable with Back from the
                            // confirmation. Coming back to it would invite a second
                            // booking of a stay that is already made.
                            popUpTo<BookingRoute> { inclusive = true }
                        }
                    },
                )
            }

            composable<ConfirmationDestination> {
                BookingConfirmationRoute(
                    onDone = {
                        navController.navigate(HotelsRoute) {
                            popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                )
            }
        }
    }
}

private fun NavHostController.navigateToTopLevel(entry: TopLevelEntry) {
    navigate(entry.destination) {
        // Keep one back stack entry per tab and restore its scroll position, rather than
        // growing the stack every time the user taps between tabs.
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private data class TopLevelEntry(
    val destination: Any,
    val route: KClass<*>,
    val labelRes: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
)

private val TOP_LEVEL = listOf(
    TopLevelEntry(
        destination = HotelsRoute,
        route = HotelsRoute::class,
        labelRes = R.string.nav_hotels,
        icon = Icons.Outlined.Hotel,
        selectedIcon = Icons.Outlined.Hotel,
    ),
    TopLevelEntry(
        destination = FavoritesRoute,
        route = FavoritesRoute::class,
        labelRes = R.string.nav_favorites,
        icon = Icons.Outlined.FavoriteBorder,
        selectedIcon = Icons.Filled.Favorite,
    ),
)
