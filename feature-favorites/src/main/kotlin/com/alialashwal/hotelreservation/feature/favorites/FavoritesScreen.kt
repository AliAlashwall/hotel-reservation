package com.alialashwal.hotelreservation.feature.favorites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alialashwal.hotelreservation.core.ui.component.EmptyState
import com.alialashwal.hotelreservation.core.ui.component.FullScreenLoading
import com.alialashwal.hotelreservation.core.ui.component.HotelCard
import com.alialashwal.hotelreservation.model.HotelSummary
import kotlinx.coroutines.flow.collectLatest

@Composable
fun FavoritesRoute(
    onOpenHotel: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FavoritesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is FavoritesEffect.OpenHotel -> onOpenHotel(effect.hotelId)
            }
        }
    }

    FavoritesScreen(state = state, onIntent = viewModel::onIntent, modifier = modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FavoritesScreen(
    state: FavoritesState,
    onIntent: (FavoritesIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.favorites_title)) }) },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                state.isLoading -> FullScreenLoading()

                state.isEmpty -> EmptyState(
                    title = stringResource(R.string.favorites_empty_title),
                    detail = stringResource(R.string.favorites_empty_detail),
                )

                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.hotels, key = HotelSummary::id) { hotel ->
                        HotelCard(
                            hotel = hotel,
                            // Every row here is a favourite by definition.
                            isFavorite = true,
                            onClick = { onIntent(FavoritesIntent.HotelClicked(hotel.id)) },
                            onToggleFavorite = { onIntent(FavoritesIntent.ToggleFavorite(hotel.id)) },
                        )
                    }
                }
            }
        }
    }
}
