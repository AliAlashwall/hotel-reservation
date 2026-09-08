package com.alialashwal.hotelreservation.feature.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.alialashwal.hotelreservation.core.ui.component.ErrorState
import com.alialashwal.hotelreservation.core.ui.component.FavoriteButton
import com.alialashwal.hotelreservation.core.ui.component.FullScreenLoading
import com.alialashwal.hotelreservation.core.ui.component.PriceLabel
import com.alialashwal.hotelreservation.core.ui.component.RatingBadge
import com.alialashwal.hotelreservation.core.ui.component.TagFlowRow
import com.alialashwal.hotelreservation.model.HotelDetail
import kotlinx.coroutines.flow.collectLatest
import java.util.Locale

@Composable
fun HotelDetailRoute(
    onBack: () -> Unit,
    onOpenBooking: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HotelDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is HotelDetailEffect.OpenBooking -> onOpenBooking(effect.hotelId)
            }
        }
    }

    HotelDetailScreen(state = state, onIntent = viewModel::onIntent, onBack = onBack, modifier = modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HotelDetailScreen(
    state: HotelDetailState,
    onIntent: (HotelDetailIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(state.hotel?.name ?: stringResource(R.string.detail_title), maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            stringResource(com.alialashwal.hotelreservation.core.ui.R.string.action_back),
                        )
                    }
                },
                actions = {
                    if (state.hotel != null) {
                        FavoriteButton(
                            isFavorite = state.isFavorite,
                            onToggle = { onIntent(HotelDetailIntent.ToggleFavorite) },
                        )
                    }
                },
            )
        },
        bottomBar = {
            if (state.hotel != null) {
                BookingBar(state = state, onBook = { onIntent(HotelDetailIntent.BookClicked) })
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (state.phase) {
                DetailPhase.Loading -> FullScreenLoading()

                DetailPhase.Error -> ErrorState(
                    error = requireNotNull(state.error),
                    onRetry = { onIntent(HotelDetailIntent.Retry) },
                )

                DetailPhase.Content -> DetailContent(hotel = requireNotNull(state.hotel))
            }
        }
    }
}

@Composable
private fun DetailContent(hotel: HotelDetail) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {

        item(key = "gallery") { Gallery(hotel) }

        item(key = "header") {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(hotel.name, style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = listOfNotNull(hotel.address.takeIf { it.isNotBlank() }, hotel.city, countryName(hotel))
                        .joinToString(separator = ", "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                RatingBadge(
                    starRating = hotel.starRating,
                    reviewScore = hotel.reviewScore,
                    reviewCount = hotel.reviewCount,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }

        if (hotel.amenities.isNotEmpty()) {
            item(key = "amenities") {
                Section(title = stringResource(R.string.detail_amenities)) {
                    // Amenity names range from "Bar" to "Airport shuttle service
                    // (surcharge)". Two fixed columns squeezed the long ones into a
                    // narrow box where the text wrapped a word or two per line. A flow
                    // row gives each label the width it actually needs.
                    TagFlowRow(tags = hotel.amenities)
                }
            }
        }

        if (hotel.description.isNotBlank()) {
            item(key = "about") {
                Section(title = stringResource(R.string.detail_about)) {
                    Text(hotel.description, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        item(key = "location") {
            Section(title = stringResource(R.string.detail_location)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(hotel.address, style = MaterialTheme.typography.bodyMedium)
                    hotel.coordinates?.let { position ->
                        Text(
                            text = stringResource(
                                R.string.detail_coordinates,
                                position.latitude,
                                position.longitude,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (hotel.checkInFrom != null && hotel.checkOutBy != null) {
                        Text(
                            text = stringResource(
                                R.string.detail_check_times,
                                hotel.checkInFrom!!,
                                hotel.checkOutBy!!,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Gallery(hotel: HotelDetail) {
    if (hotel.imageUrls.isEmpty()) return
    val pagerState = rememberPagerState { hotel.imageUrls.size }

    Box {
        HorizontalPager(state = pagerState) { page ->
            AsyncImage(
                model = hotel.imageUrls[page],
                contentDescription = stringResource(R.string.detail_image, page + 1, hotel.imageUrls.size),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }

        // Supplier photos are often bright at the bottom edge, which would swallow the
        // white indicators sitting on top of them.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(88.dp)
                .background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)))
                ),
        )

        // Dots for a handful of photos, a counter once there are too many to dot.
        if (hotel.imageUrls.size in 2..MAX_GALLERY_DOTS) {
            Row(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                repeat(hotel.imageUrls.size) { index ->
                    val selected = index == pagerState.currentPage
                    Box(
                        modifier = Modifier
                            .size(if (selected) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = if (selected) 1f else 0.5f)),
                    )
                }
            }
        } else if (hotel.imageUrls.size > MAX_GALLERY_DOTS) {
            Text(
                text = "${pagerState.currentPage + 1} / ${hotel.imageUrls.size}",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
    }
}

private const val MAX_GALLERY_DOTS = 8

@Composable
private fun BookingBar(state: HotelDetailState, onBook: () -> Unit) {
    Column {
        HorizontalDivider()
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PriceLabel(price = state.hotel?.nightlyRate, modifier = Modifier)
            Button(onClick = onBook, enabled = state.canBook) {
                Text(
                    stringResource(
                        if (state.canBook) R.string.detail_book else R.string.detail_book_unavailable
                    )
                )
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Box(modifier = Modifier.padding(top = 8.dp)) { content() }
    }
}

/** Resolved on device rather than fetched, since the API returns only the country code. */
private fun countryName(hotel: HotelDetail): String? =
    Locale.Builder().setRegion(hotel.countryCode).build().displayCountry
        .takeIf { it.isNotBlank() && it != hotel.countryCode }
