package com.alialashwal.hotelreservation.core.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.alialashwal.hotelreservation.core.ui.R
import com.alialashwal.hotelreservation.core.ui.asUserMessage
import com.alialashwal.hotelreservation.model.AppError
import java.time.Duration
import java.time.Instant

/**
 * A persistent strip above content, for a condition the user has to know about but that
 * does not stop them using the screen.
 *
 * A snackbar was the alternative and is the wrong tool here: it disappears, and a user
 * who reaches the screen a few seconds late would never learn that what they are reading
 * is saved data or that the refresh failed.
 */
@Composable
private fun NoticeBar(
    icon: ImageVector,
    container: Color,
    onContainer: Color,
    title: String,
    detail: String?,
    tag: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(container)
            .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp)
            .semantics { contentDescription = tag },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = onContainer,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = onContainer,
            )
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = onContainer,
                )
            }
        }
        action?.invoke()
    }
}

/** Says plainly that the rows on screen came out of the cache, and how old they are. */
@Composable
fun CachedDataBanner(
    lastRefreshedAt: Instant?,
    now: Instant,
    modifier: Modifier = Modifier,
) {
    NoticeBar(
        icon = Icons.Outlined.CloudOff,
        container = MaterialTheme.colorScheme.secondaryContainer,
        onContainer = MaterialTheme.colorScheme.onSecondaryContainer,
        title = stringResource(R.string.cached_banner),
        detail = lastRefreshedAt?.let { stringResource(R.string.cached_banner_detail, relativeAge(it, now)) },
        tag = StateTags.STALE_BANNER,
        modifier = modifier,
    )
}

/**
 * A refresh that failed while rows are already on screen.
 *
 * Separate from [ErrorState] on purpose. That one owns the screen because there is
 * nothing else to show; this one sits above a list the user can still scroll, search and
 * open. Replacing readable content with a full-screen error because a background refresh
 * failed would be a downgrade, especially offline.
 */
@Composable
fun RefreshErrorBar(
    error: AppError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(),
        exit = shrinkVertically(),
    ) {
        NoticeBar(
            icon = Icons.Outlined.ErrorOutline,
            container = MaterialTheme.colorScheme.errorContainer,
            onContainer = MaterialTheme.colorScheme.onErrorContainer,
            title = error.asUserMessage(),
            detail = null,
            tag = StateTags.REFRESH_ERROR_BAR,
            modifier = modifier,
            action = if (error.isRetryable) {
                {
                    TextButton(onClick = onRetry) {
                        Text(
                            text = stringResource(R.string.action_retry),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            } else {
                null
            },
        )
    }
}

/**
 * Deliberately coarse. "3 h ago" is what a user needs to judge whether a price is worth
 * trusting; a timestamp to the second is noise.
 */
internal fun relativeAge(then: Instant, now: Instant): String {
    val age = Duration.between(then, now)
    return when {
        age.isNegative || age.toMinutes() < 1 -> "just now"
        age.toMinutes() < 60 -> "${age.toMinutes()} min ago"
        age.toHours() < 24 -> "${age.toHours()} h ago"
        age.toDays() == 1L -> "yesterday"
        else -> "${age.toDays()} days ago"
    }
}
