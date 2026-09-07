package com.alialashwal.hotelreservation.domain.usecase

import com.alialashwal.hotelreservation.model.BookingReference
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlin.random.Random

/**
 * Produces a booking reference such as `HR-260908-K7XQ4`.
 *
 * The alphabet leaves out characters people confuse when reading a code aloud or
 * typing it from a screenshot: O and 0, I and 1, S and 5. That is the whole reason
 * this is not just a truncated UUID.
 *
 * [Clock] and [Random] are injected so a test can assert the exact string instead of
 * asserting a regular expression and hoping.
 */
class GenerateBookingReference @Inject constructor(
    private val clock: Clock,
    private val random: Random,
) {

    operator fun invoke(): BookingReference {
        val date = LocalDate.now(clock).format(DATE_FORMAT)
        val suffix = (1..SUFFIX_LENGTH)
            .map { ALPHABET[random.nextInt(ALPHABET.length)] }
            .joinToString(separator = "")
        return BookingReference("$PREFIX-$date-$suffix")
    }

    companion object {
        private const val PREFIX = "HR"
        private const val SUFFIX_LENGTH = 5
        private const val ALPHABET = "ABCDEFGHJKLMNPQRTUVWXY2346789"
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyMMdd")
    }
}
