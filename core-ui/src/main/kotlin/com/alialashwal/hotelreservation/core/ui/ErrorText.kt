package com.alialashwal.hotelreservation.core.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.alialashwal.hotelreservation.model.AppError

/**
 * The one place an [AppError] becomes words a user reads.
 *
 * Centralised deliberately. Every screen that can fail calls this, so the same failure
 * never gets two different explanations, and adding a new error case is a compile error
 * here rather than a silently unhandled branch in four ViewModels.
 */
@Composable
fun AppError.asUserMessage(): String = stringResource(messageRes)

@get:StringRes
val AppError.messageRes: Int
    get() = when (this) {
        AppError.NoConnection -> R.string.error_no_connection
        AppError.Timeout -> R.string.error_timeout
        AppError.Unauthorized -> R.string.error_unauthorized
        AppError.NotFound -> R.string.error_not_found
        AppError.NoAvailability -> R.string.error_no_availability
        AppError.MalformedResponse -> R.string.error_malformed
        is AppError.BadRequest -> R.string.error_bad_request
        is AppError.Server -> R.string.error_server
        is AppError.Unknown -> R.string.error_unknown
    }
