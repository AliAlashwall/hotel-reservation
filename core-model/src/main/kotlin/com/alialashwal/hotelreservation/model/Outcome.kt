package com.alialashwal.hotelreservation.model

/**
 * The result of an operation that can fail in a way the user should see.
 *
 * Preferred over `kotlin.Result` because failures here are [AppError] values rather
 * than throwables, so an unhandled case is a compile error instead of a crash, and
 * because `kotlin.Result` cannot be used as a return type on a suspending interface
 * method without warnings.
 */
sealed interface Outcome<out T> {

    data class Success<T>(val value: T) : Outcome<T>

    data class Failure(val error: AppError) : Outcome<Nothing>

    val valueOrNull: T?
        get() = (this as? Success)?.value

    val errorOrNull: AppError?
        get() = (this as? Failure)?.error
}

inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> = when (this) {
    is Outcome.Success -> Outcome.Success(transform(value))
    is Outcome.Failure -> this
}

inline fun <T, R> Outcome<T>.flatMap(transform: (T) -> Outcome<R>): Outcome<R> = when (this) {
    is Outcome.Success -> transform(value)
    is Outcome.Failure -> this
}

inline fun <T> Outcome<T>.onSuccess(action: (T) -> Unit): Outcome<T> = also {
    if (it is Outcome.Success) action(it.value)
}

inline fun <T> Outcome<T>.onFailure(action: (AppError) -> Unit): Outcome<T> = also {
    if (it is Outcome.Failure) action(it.error)
}

fun <T> T.asSuccess(): Outcome<T> = Outcome.Success(this)

fun AppError.asFailure(): Outcome<Nothing> = Outcome.Failure(this)
