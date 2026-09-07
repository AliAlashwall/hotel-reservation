package com.alialashwal.hotelreservation.model

/**
 * Every failure the app can show a user, named in terms the app cares about rather
 * than in terms of the transport that produced it.
 *
 * Exceptions and HTTP codes are translated into these once, at the network boundary,
 * so no layer above has to know what an `IOException` or a 401 is. The UI layer maps
 * these to strings in exactly one place.
 */
sealed interface AppError {

    /** No usable network at all. The cache, if any, should be shown instead. */
    data object NoConnection : AppError

    /** The network was there but the call did not finish in time. */
    data object Timeout : AppError

    /** The API key is missing, wrong, or expired. Retrying will not help. */
    data object Unauthorized : AppError

    /** The server understood the request and rejected it. */
    data class BadRequest(val apiMessage: String?) : AppError

    /** The thing we asked for is not there. */
    data object NotFound : AppError

    /** The server failed. Retrying may help. */
    data class Server(val code: Int, val apiMessage: String?) : AppError

    /** The response arrived but did not have the shape we expect. */
    data object MalformedResponse : AppError

    /** The supplier has no rooms for the dates asked about. Not an error in the app. */
    data object NoAvailability : AppError

    data class Unknown(val cause: Throwable?) : AppError

    /** True when showing the user a retry button is honest. */
    val isRetryable: Boolean
        get() = when (this) {
            NoConnection, Timeout, MalformedResponse -> true
            is Server -> true
            is Unknown -> true
            Unauthorized, NotFound, NoAvailability, is BadRequest -> false
        }
}
