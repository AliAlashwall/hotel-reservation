package com.alialashwal.hotelreservation.core.network.internal

import com.alialashwal.hotelreservation.core.network.dto.ErrorEnvelope
import com.alialashwal.hotelreservation.model.AppError
import com.alialashwal.hotelreservation.model.Outcome
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * The single place where a transport failure becomes an [AppError].
 *
 * Everything above this line deals in [AppError] and never sees an `IOException`, an
 * HTTP status code, or a Retrofit type. That is what "centralised error handling"
 * means here: one translation, not a `try` block per call site.
 */
internal suspend fun <T> apiCall(json: Json, block: suspend () -> T): Outcome<T> =
    try {
        Outcome.Success(block())
    } catch (e: CancellationException) {
        // A cancelled call is not a failure. Rethrowing keeps structured concurrency
        // intact; swallowing it here would turn every cancelled search into an error
        // banner.
        throw e
    } catch (e: HttpException) {
        Outcome.Failure(e.toAppError(json))
    } catch (e: UnknownHostException) {
        Outcome.Failure(AppError.NoConnection)
    } catch (e: SocketTimeoutException) {
        Outcome.Failure(AppError.Timeout)
    } catch (e: SerializationException) {
        Outcome.Failure(AppError.MalformedResponse)
    } catch (e: IOException) {
        // Covers ConnectException, SSL failures and a socket closed mid-read. From the
        // user's side these are all "the network did not work".
        Outcome.Failure(AppError.NoConnection)
    } catch (e: Throwable) {
        Outcome.Failure(AppError.Unknown(e))
    }

private fun HttpException.toAppError(json: Json): AppError {
    val apiMessage = readApiMessage(json)
    return when (code()) {
        401, 403 -> AppError.Unauthorized
        404 -> AppError.NotFound
        in 400..499 -> AppError.BadRequest(apiMessage)
        else -> AppError.Server(code(), apiMessage)
    }
}

/** LiteAPI describes the real problem in the body, not the status line. Worth reading. */
private fun HttpException.readApiMessage(json: Json): String? = runCatching {
    val body = response()?.errorBody()?.string().orEmpty()
    if (body.isBlank()) return@runCatching null
    val error = json.decodeFromString(ErrorEnvelope.serializer(), body).error
    error?.description ?: error?.message
}.getOrNull()
