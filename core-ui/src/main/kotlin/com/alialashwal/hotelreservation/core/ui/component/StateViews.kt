package com.alialashwal.hotelreservation.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alialashwal.hotelreservation.core.ui.R
import com.alialashwal.hotelreservation.core.ui.asUserMessage
import com.alialashwal.hotelreservation.model.AppError

/**
 * The five states a data-backed screen can be in, as reusable composables.
 *
 * Kept together so no screen invents its own spelling of "nothing here yet". Each
 * carries a test tag, which is what the end-to-end test asserts against instead of
 * matching on user-visible copy that will change.
 */
object StateTags {
    const val LOADING = "state_loading"
    const val EMPTY = "state_empty"
    const val ERROR = "state_error"
    const val APPENDING = "state_appending"
    const val STALE_BANNER = "state_stale_banner"
    const val REFRESH_ERROR_BAR = "state_refresh_error"
    const val SKELETON = "state_skeleton"
}

@Composable
fun FullScreenLoading(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .semantics { contentDescription = StateTags.LOADING },
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
fun EmptyState(
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
) {
    MessageState(
        icon = Icons.Outlined.SearchOff,
        title = title,
        detail = detail,
        tag = StateTags.EMPTY,
        modifier = modifier,
    )
}

/**
 * A failure the user can act on.
 *
 * The retry button appears only when [AppError.isRetryable] says so. Offering "Retry"
 * for a rejected API key would invite the user to press it forever.
 */
@Composable
fun ErrorState(
    error: AppError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MessageState(
        icon = Icons.Outlined.CloudOff,
        title = error.asUserMessage(),
        detail = null,
        tag = StateTags.ERROR,
        modifier = modifier,
        action = if (error.isRetryable) {
            { Button(onClick = onRetry) { Text(stringResource(R.string.action_retry)) } }
        } else {
            null
        },
    )
}

@Composable
private fun MessageState(
    icon: ImageVector,
    title: String,
    detail: String?,
    tag: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp)
            .semantics { contentDescription = tag },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )
        if (detail != null) {
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (action != null) {
            Box(modifier = Modifier.padding(top = 24.dp)) { action() }
        }
    }
}

/** The footer under a list while the next page is loading. */
@Composable
fun AppendingFooter(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp)
            .semantics { contentDescription = StateTags.APPENDING },
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(28.dp))
    }
}

/** Shown under a list whose next page failed, so the rest of the list stays usable. */
@Composable
fun AppendingError(
    error: AppError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = error.asUserMessage(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onRetry, modifier = Modifier.padding(top = 8.dp)) {
            Text(stringResource(R.string.action_retry))
        }
    }
}
