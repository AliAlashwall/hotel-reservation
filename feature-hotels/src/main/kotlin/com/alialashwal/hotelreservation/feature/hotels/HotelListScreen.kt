package com.alialashwal.hotelreservation.feature.hotels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alialashwal.hotelreservation.core.ui.component.AppendingError
import com.alialashwal.hotelreservation.core.ui.component.AppendingFooter
import com.alialashwal.hotelreservation.core.ui.component.CachedDataBanner
import com.alialashwal.hotelreservation.core.ui.component.EmptyState
import com.alialashwal.hotelreservation.core.ui.component.ErrorState
import com.alialashwal.hotelreservation.core.ui.component.HotelListSkeleton
import com.alialashwal.hotelreservation.core.ui.component.RefreshErrorBar
import com.alialashwal.hotelreservation.core.ui.component.HotelCard
import com.alialashwal.hotelreservation.model.HotelSummary
import kotlinx.coroutines.flow.collectLatest
import java.time.Instant

@Composable
fun HotelListRoute(
    onOpenHotel: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HotelListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // One-shot effects are collected here rather than read out of the state, so a
    // rotation cannot replay a navigation the user already performed.
    androidx.compose.runtime.LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is HotelListEffect.OpenHotel -> onOpenHotel(effect.hotelId)
            }
        }
    }

    HotelListScreen(state = state, onIntent = viewModel::onIntent, modifier = modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HotelListScreen(
    state: HotelListState,
    onIntent: (HotelListIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.hotels_title)) },
                actions = {
                    BadgedBox(
                        badge = {
                            if (state.activeFilterCount > 0) {
                                Badge { Text(state.activeFilterCount.toString()) }
                            }
                        },
                    ) {
                        IconButton(onClick = { onIntent(HotelListIntent.FilterSheetToggled(true)) }) {
                            Icon(Icons.Outlined.FilterList, stringResource(R.string.hotels_filters))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {

            SearchField(
                query = state.query,
                onQueryChange = { onIntent(HotelListIntent.QueryChanged(it)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )

            // Both bars sit above the list rather than over it, so neither can hide a
            // row and neither can be missed by a user who arrives late.
            if (state.isStale) {
                CachedDataBanner(lastRefreshedAt = state.lastRefreshedAt, now = Instant.now())
            }

            // A refresh that failed while rows are on screen. Without this the most
            // common real case, going offline with a list already loaded, was silent:
            // the user pulled to refresh, nothing changed, and nothing said why.
            state.refreshError?.let { error ->
                if (state.hotels.isNotEmpty()) {
                    RefreshErrorBar(error = error, onRetry = { onIntent(HotelListIntent.Refresh) })
                }
            }

            PullToRefreshBox(
                isRefreshing = state.isRefreshing && state.hotels.isNotEmpty(),
                onRefresh = { onIntent(HotelListIntent.Refresh) },
                modifier = Modifier.fillMaxSize(),
            ) {
                when (state.phase) {
                    ListPhase.Loading -> HotelListSkeleton()

                    ListPhase.Error -> ErrorState(
                        error = requireNotNull(state.refreshError),
                        onRetry = { onIntent(HotelListIntent.Refresh) },
                    )

                    ListPhase.Empty -> EmptyState(
                        title = stringResource(R.string.hotels_empty_title),
                        detail = stringResource(R.string.hotels_empty_detail),
                    )

                    ListPhase.Content -> HotelList(state = state, onIntent = onIntent)
                }
            }
        }
    }

    if (state.isFilterSheetOpen) {
        FilterSheet(
            state = state,
            onIntent = onIntent,
            onDismiss = { onIntent(HotelListIntent.FilterSheetToggled(false)) },
        )
    }
}

@Composable
private fun HotelList(
    state: HotelListState,
    onIntent: (HotelListIntent) -> Unit,
) {
    val listState = rememberLazyListState()

    // Reading the layout inside derivedStateOf means this recomputes on scroll but only
    // recomposes when the answer flips, instead of on every frame.
    val shouldLoadMore by remember(listState) {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            lastVisible >= listState.layoutInfo.totalItemsCount - HotelListViewModel.PREFETCH_DISTANCE
        }
    }

    androidx.compose.runtime.LaunchedEffect(shouldLoadMore, state.isLastPage) {
        if (shouldLoadMore && !state.isLastPage) onIntent(HotelListIntent.LoadMore)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(
            items = state.hotels,
            // Keying by hotel id keeps scroll position and image loading stable when a
            // page is appended or a favourite is toggled.
            key = HotelSummary::id,
        ) { hotel ->
            HotelCard(
                hotel = hotel,
                isFavorite = hotel.id in state.favoriteIds,
                onClick = { onIntent(HotelListIntent.HotelClicked(hotel.id)) },
                onToggleFavorite = { onIntent(HotelListIntent.ToggleFavorite(hotel.id)) },
            )
        }

        item(key = "footer") {
            when {
                state.appendError != null -> AppendingError(
                    error = state.appendError,
                    onRetry = { onIntent(HotelListIntent.RetryAppend) },
                )

                state.isAppending -> AppendingFooter()

                state.isLastPage -> Text(
                    text = stringResource(R.string.hotels_end_of_list),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                )
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        placeholder = { Text(stringResource(R.string.hotels_search_hint)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Close, stringResource(R.string.hotels_clear_search))
                }
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    )
}
