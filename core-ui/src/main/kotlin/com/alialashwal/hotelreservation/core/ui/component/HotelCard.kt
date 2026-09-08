package com.alialashwal.hotelreservation.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.alialashwal.hotelreservation.core.ui.R
import com.alialashwal.hotelreservation.core.ui.format
import com.alialashwal.hotelreservation.core.ui.theme.PriceTextStyle
import com.alialashwal.hotelreservation.model.HotelSummary
import com.alialashwal.hotelreservation.model.Money
import java.util.Locale

/**
 * One row of the hotel list, reused unchanged by the favourites screen.
 *
 * Takes [isFavorite] as a parameter rather than reading it from the hotel, so the
 * favourites screen, where every row is a favourite by definition, needs no per-row
 * lookup.
 */
@Composable
fun HotelCard(
    hotel: HotelSummary,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column {
            Box {
                AsyncImage(
                    model = hotel.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )

                // Photos are supplier-supplied and can be bright at the top, which
                // leaves a white heart invisible. A short scrim guarantees contrast
                // without darkening the whole image.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent),
                            )
                        ),
                )

                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        // The label names the action, not the state, because that is
                        // what a screen reader user is choosing to do.
                        contentDescription = stringResource(
                            if (isFavorite) R.string.remove_from_favorites else R.string.add_to_favorites
                        ),
                        tint = if (isFavorite) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.size(20.dp),
                    )
                }

                hotel.starRating?.let { stars ->
                    StarBadge(
                        stars = stars,
                        modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
                    )
                }
            }

            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = hotel.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = hotel.city.ifBlank { countryName(hotel.countryCode) ?: hotel.countryCode },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    ScorePill(
                        reviewScore = hotel.reviewScore,
                        reviewCount = hotel.reviewCount,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    PriceLabel(price = hotel.nightlyRate)
                }
            }
        }
    }
}

/** The property class, over the photo, where hotel apps conventionally put it. */
@Composable
private fun StarBadge(stars: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Star,
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            tint = Color(0xFFFFB95C),
        )
        Text(
            text = stars.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            modifier = Modifier.padding(start = 3.dp),
        )
    }
}

/**
 * The guest score as a filled pill, the way booking sites present it.
 *
 * A number out of ten in plain body text is easy to confuse with the star rating. Giving
 * it a solid block of colour separates the two at a glance.
 */
@Composable
fun ScorePill(
    reviewScore: Double?,
    reviewCount: Int,
    modifier: Modifier = Modifier,
) {
    if (reviewScore == null) return

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = formatScore(reviewScore),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomEnd = 8.dp))
                .background(MaterialTheme.colorScheme.primary)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
        if (reviewCount > 0) {
            Text(
                text = pluralStringResource(R.plurals.reviews_only, reviewCount, reviewCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
fun FavoriteButton(
    isFavorite: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onToggle, modifier = modifier) {
        Icon(
            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = stringResource(
                if (isFavorite) R.string.remove_from_favorites else R.string.add_to_favorites
            ),
            tint = if (isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
fun RatingBadge(
    starRating: Int?,
    reviewScore: Double?,
    reviewCount: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (starRating != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                repeat(starRating) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        }
        ScorePill(reviewScore = reviewScore, reviewCount = reviewCount)
    }
}

@Composable
fun PriceLabel(
    price: Money?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.End) {
        if (price == null) {
            // A hotel the rates service had nothing for. Saying so beats showing a zero.
            Text(
                text = stringResource(R.string.price_unavailable),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text = price.format(),
                style = PriceTextStyle,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
            Text(
                text = stringResource(R.string.per_night),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** One decimal place, and no trailing `.0` on a whole score. */
internal fun formatScore(score: Double): String =
    if (score % 1.0 == 0.0) score.toInt().toString() else String.format(Locale.US, "%.1f", score)

internal fun countryName(countryCode: String): String? =
    Locale.Builder().setRegion(countryCode).build().displayCountry
        .takeIf { it.isNotBlank() && it != countryCode }
