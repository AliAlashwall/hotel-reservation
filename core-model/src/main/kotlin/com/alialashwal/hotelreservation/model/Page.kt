package com.alialashwal.hotelreservation.model

/**
 * One slice of a paged result set, exactly as the server returned it.
 *
 * [items] is the raw page, before any filtering the app does on its own. That matters
 * for [isLastPage]: the price band is applied on our side, so a page can shrink after
 * the server has already sized it. Advancing the offset by the filtered count would
 * skip hotels. Callers must advance by `items.size` and judge the end by [total].
 */
data class Page<T>(
    val items: List<T>,
    val offset: Int,
    val total: Int,
) {
    val isLastPage: Boolean get() = offset + items.size >= total

    val nextOffset: Int get() = offset + items.size

    fun <R> map(transform: (T) -> R): Page<R> =
        Page(items.map(transform), offset, total)
}
