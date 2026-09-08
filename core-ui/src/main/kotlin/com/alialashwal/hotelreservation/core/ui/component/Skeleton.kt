package com.alialashwal.hotelreservation.core.ui.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Placeholder cards shaped like the real ones, shown during the first load.
 *
 * Preferred over a centred spinner because it shows what is coming and keeps the page
 * from jumping when content lands. The pulse is one shared animation driving an alpha,
 * not a gradient sweep per element, so it costs almost nothing on a low-end device.
 */
@Composable
fun HotelListSkeleton(
    modifier: Modifier = Modifier,
    itemCount: Int = 4,
) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeletonAlpha",
    )

    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = StateTags.SKELETON },
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        // Nothing here is interactive, and letting it scroll would fight the real list
        // that replaces it.
        userScrollEnabled = false,
    ) {
        items(itemCount) {
            SkeletonCard(alpha = alpha)
        }
    }
}

@Composable
private fun SkeletonCard(alpha: Float) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            Shape(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
                alpha = alpha,
                corner = 0.dp,
            )
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Shape(Modifier.fillMaxWidth(0.7f).height(18.dp), alpha)
                Shape(Modifier.fillMaxWidth(0.35f).height(14.dp), alpha)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Shape(Modifier.width(88.dp).height(24.dp), alpha)
                    Shape(Modifier.width(72.dp).height(24.dp), alpha)
                }
            }
        }
    }
}

@Composable
private fun Shape(modifier: Modifier, alpha: Float, corner: androidx.compose.ui.unit.Dp = 6.dp) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(corner))
            .alpha(alpha)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    )
}

private fun androidx.compose.foundation.lazy.LazyListScope.items(
    count: Int,
    itemContent: @Composable (Int) -> Unit,
) = items(count = count, key = { "skeleton_$it" }) { itemContent(it) }
