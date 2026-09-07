package com.alialashwal.hotelreservation.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.random.Random

class GenerateBookingReferenceTest {

    private val clock = Clock.fixed(Instant.parse("2026-09-08T09:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `the reference carries the booking date so it can be read without a lookup`() {
        val reference = GenerateBookingReference(clock, Random(1))().value

        assertTrue(reference, reference.matches(Regex("^HR-260908-[A-Z2-9]{5}$")))
    }

    @Test
    fun `the same clock and seed always produce the same reference`() {
        // Determinism is what makes the confirmation screen assertable in a test.
        assertEquals(
            GenerateBookingReference(clock, Random(42))().value,
            GenerateBookingReference(clock, Random(42))().value,
        )
    }

    @Test
    fun `different seeds produce different references`() {
        assertNotEquals(
            GenerateBookingReference(clock, Random(1))().value,
            GenerateBookingReference(clock, Random(2))().value,
        )
    }

    @Test
    fun `characters people confuse when reading a code aloud never appear`() {
        // O against 0, I against 1, S against 5. A guest reads this over the phone.
        val confusable = setOf('O', '0', 'I', '1', 'S', '5')
        val generate = GenerateBookingReference(clock, Random(7))

        val suffixes = (1..500).map { generate().value.substringAfterLast('-') }

        val offenders = suffixes.flatMap { it.toList() }.filter { it in confusable }.toSet()
        assertEquals(emptySet<Char>(), offenders)
    }

    @Test
    fun `consecutive calls on one generator do not repeat`() {
        val generate = GenerateBookingReference(clock, Random(3))

        val references = (1..200).map { generate().value }

        assertEquals(references.size, references.toSet().size)
    }
}
