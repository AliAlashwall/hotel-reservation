package com.alialashwal.hotelreservation.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.alialashwal.hotelreservation.core.data.internal.CachePolicy
import com.alialashwal.hotelreservation.core.data.internal.DefaultHotelRepository
import com.alialashwal.hotelreservation.core.database.AppDatabase
import com.alialashwal.hotelreservation.model.AppError
import com.alialashwal.hotelreservation.model.HotelFilters
import com.alialashwal.hotelreservation.model.Money
import com.alialashwal.hotelreservation.model.Outcome
import com.alialashwal.hotelreservation.model.PriceRange
import com.alialashwal.hotelreservation.model.ValidStay
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DefaultHotelRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var remote: FakeHotelRemoteDataSource
    private lateinit var repository: DefaultHotelRepository

    private var now: Instant = Instant.parse("2026-09-08T09:00:00Z")
    private val clock = object : Clock() {
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId) = this
        override fun instant() = now
    }

    private val filters = HotelFilters(countryCode = "EG", city = "Cairo")

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        remote = FakeHotelRemoteDataSource()
        repository = DefaultHotelRepository(remote, database.hotelDao(), clock)
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ------------------------------------------------------------------ success

    @Test
    fun `a successful refresh caches a page and prices it`() = runTest {
        assertTrue(repository.refresh(filters) is Outcome.Success)

        val feed = repository.observeFeed(filters).first()
        assertEquals(CachePolicy.PAGE_SIZE, feed.hotels.size)
        assertEquals("lp0", feed.hotels.first().id)
        assertEquals(Money.of("100.00", "USD"), feed.hotels.first().nightlyRate)
        assertFalse(feed.isLastPage)
        assertNotNull(feed.lastRefreshedAt)
        assertFalse(feed.isStale)
    }

    @Test
    fun `paging appends and reaches the end exactly once`() = runTest {
        remote.catalogueSize = 45

        repository.refresh(filters)
        repeat(3) { repository.loadNextPage(filters) }

        val feed = repository.observeFeed(filters).first()
        assertEquals(45, feed.hotels.size)
        assertTrue(feed.isLastPage)
        assertEquals(listOf(0, 20, 40), remote.requestedOffsets)
    }

    @Test
    fun `once the end is reached no further call is made`() = runTest {
        remote.catalogueSize = 10
        repository.refresh(filters)
        val callsAfterFirstPage = remote.hotelsCalls.get()

        repeat(5) { repository.loadNextPage(filters) }

        assertEquals(callsAfterFirstPage, remote.hotelsCalls.get())
        assertTrue(repository.observeFeed(filters).first().isLastPage)
    }

    @Test
    fun `loading the next page with nothing cached loads the first page instead`() = runTest {
        repository.loadNextPage(filters)

        assertEquals(listOf(0), remote.requestedOffsets)
        assertEquals(CachePolicy.PAGE_SIZE, repository.observeFeed(filters).first().hotels.size)
    }

    @Test
    fun `refreshing again replaces the result set rather than doubling it`() = runTest {
        repository.refresh(filters)
        repository.loadNextPage(filters)
        assertEquals(40, repository.observeFeed(filters).first().hotels.size)

        repository.refresh(filters)

        val feed = repository.observeFeed(filters).first()
        assertEquals(CachePolicy.PAGE_SIZE, feed.hotels.size)
        assertEquals(feed.hotels.size, feed.hotels.map { it.id }.toSet().size)
    }

    @Test
    fun `different filters keep separate result sets`() = runTest {
        val other = filters.copy(query = "nile")

        repository.refresh(filters)
        repository.refresh(other)

        assertEquals(20, repository.observeFeed(filters).first().hotels.size)
        assertEquals(20, repository.observeFeed(other).first().hotels.size)
    }

    // -------------------------------------------------- overlapping requests

    @Test
    fun `two page loads that overlap result in one network call`() = runTest {
        // The scroll listener can fire twice before the first response lands. Queueing
        // the second would still make two calls; it has to be dropped.
        val gate = CompletableDeferred<Unit>()
        remote.gate = gate

        val first = async { repository.loadNextPage(filters) }
        val second = async { repository.loadNextPage(filters) }
        gate.complete(Unit)
        first.await()
        second.await()

        assertEquals(1, remote.hotelsCalls.get())
    }

    @Test
    fun `an overlapping refresh on different filters is not blocked`() = runTest {
        val gate = CompletableDeferred<Unit>()
        remote.gate = gate

        val first = async { repository.refresh(filters) }
        val second = async { repository.refresh(filters.copy(city = "Alexandria")) }
        gate.complete(Unit)
        first.await()
        second.await()

        assertEquals(2, remote.hotelsCalls.get())
    }

    // ------------------------------------------------------ failure and cache

    @Test
    fun `a failed refresh with nothing cached surfaces the error and leaves the feed empty`() = runTest {
        remote.hotelsError = AppError.NoConnection

        val outcome = repository.refresh(filters)

        assertEquals(AppError.NoConnection, (outcome as Outcome.Failure).error)
        val feed = repository.observeFeed(filters).first()
        assertTrue(feed.isEmpty)
        assertFalse("nothing has ever been cached, which is not the same as an empty result", feed.hasCachedData)
    }

    @Test
    fun `a failed refresh keeps the previously cached page intact`() = runTest {
        repository.refresh(filters)
        val cached = repository.observeFeed(filters).first().hotels

        remote.hotelsError = AppError.NoConnection
        val outcome = repository.refresh(filters)

        assertEquals(AppError.NoConnection, (outcome as Outcome.Failure).error)
        assertEquals(cached, repository.observeFeed(filters).first().hotels)
    }

    @Test
    fun `a failed page load leaves the pages already loaded alone`() = runTest {
        repository.refresh(filters)
        remote.hotelsError = AppError.Timeout

        assertEquals(AppError.Timeout, (repository.loadNextPage(filters) as Outcome.Failure).error)
        assertEquals(20, repository.observeFeed(filters).first().hotels.size)
    }

    @Test
    fun `a failed rates call still caches the hotels, just without prices`() = runTest {
        // Names and photos are worth showing. A slow rates service must not empty the list.
        remote.ratesError = AppError.Timeout

        assertTrue(repository.refresh(filters) is Outcome.Success)

        val feed = repository.observeFeed(filters).first()
        assertEquals(20, feed.hotels.size)
        assertTrue(feed.hotels.all { it.nightlyRate == null })
    }

    // ------------------------------------------------------------- staleness

    @Test
    fun `cached data is not stale inside the window and is stale outside it`() = runTest {
        repository.refresh(filters)
        assertFalse(repository.observeFeed(filters).first().isStale)

        now = now.plus(CachePolicy.STALE_AFTER).plus(Duration.ofMinutes(1))

        val feed = repository.observeFeed(filters).first()
        assertTrue(feed.isStale)
        assertEquals("stale data is still shown, not hidden", 20, feed.hotels.size)
    }

    @Test
    fun `a result set past its time to live is dropped on the next refresh`() = runTest {
        repository.refresh(filters)
        now = now.plus(CachePolicy.RESULT_SET_TTL).plus(Duration.ofHours(1))
        remote.hotelsError = AppError.NoConnection

        repository.refresh(filters)

        assertTrue(repository.observeFeed(filters).first().isEmpty)
    }

    // ----------------------------------------------------------- price filter

    @Test
    fun `hotels outside the price band are not cached, but the offset still advances`() = runTest {
        // The API ignores minPrice and maxPrice, so the band is ours to apply. The next
        // page must start after the whole server page, not after the surviving rows.
        remote.priceFor = { id -> if (id.removePrefix("lp").toInt() % 2 == 0) "100.00" else "900.00" }
        val banded = filters.copy(priceRange = PriceRange(min = 50, max = 200))

        repository.refresh(banded)

        assertEquals(10, repository.observeFeed(banded).first().hotels.size)
        repository.loadNextPage(banded)
        assertEquals(listOf(0, 20), remote.requestedOffsets)
        assertEquals(20, repository.observeFeed(banded).first().hotels.size)
    }

    @Test
    fun `a hotel with no price survives an unbounded band and is dropped by a real one`() = runTest {
        remote.priceFor = { null }

        repository.refresh(filters)
        assertEquals(20, repository.observeFeed(filters).first().hotels.size)

        val banded = filters.copy(priceRange = PriceRange(min = 50))
        repository.refresh(banded)
        assertTrue(repository.observeFeed(banded).first().isEmpty)
    }

    @Test
    fun `filtering everything out still reports the set as unfinished so paging continues`() = runTest {
        remote.priceFor = { "900.00" }
        val banded = filters.copy(priceRange = PriceRange(max = 200))

        repository.refresh(banded)

        val feed = repository.observeFeed(banded).first()
        assertTrue(feed.isEmpty)
        assertFalse("20 of 50 rows were consumed, so there is more to try", feed.isLastPage)
    }

    // ---------------------------------------------------------------- detail

    @Test
    fun `refreshing a detail caches it along with a list row, so deep links work`() = runTest {
        assertTrue(repository.refreshDetail("lp7") is Outcome.Success)

        val detail = repository.observeDetail("lp7").first()
        assertNotNull(detail)
        assertEquals("Free WiFi", detail!!.amenities.single())
        assertEquals(Money.of("100.00", "USD"), detail.nightlyRate)
    }

    @Test
    fun `a detail that has never been fetched observes as null rather than failing`() = runTest {
        assertNull(repository.observeDetail("never-seen").first())
    }

    @Test
    fun `a failed detail refresh returns the error`() = runTest {
        remote.detailError = AppError.NotFound

        assertEquals(AppError.NotFound, (repository.refreshDetail("lp1") as Outcome.Failure).error)
    }

    @Test
    fun `the detail feed re-emits when the hotel row changes`() = runTest {
        repository.refreshDetail("lp3")

        repository.observeDetail("lp3").test {
            assertEquals("Hotel lp3", awaitItem()!!.name)
            database.hotelDao().toggleFavorite("lp3")
            expectNoEvents()   // favouriting does not alter any field the detail exposes
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ------------------------------------------------------------------ rates

    @Test
    fun `the booking rate is asked for on the guest's own check-in date`() = runTest {
        val stay = ValidStay(
            checkIn = LocalDate.of(2026, 12, 24),
            checkOut = LocalDate.of(2026, 12, 27),
            nights = 3,
            rooms = 2,
        )

        assertEquals(Money.of("100.00", "USD"), (repository.rateFor("lp1", stay) as Outcome.Success).value)
    }

    @Test
    fun `no availability is reported as such, not as a zero price`() = runTest {
        remote.priceFor = { null }

        val outcome = repository.rateFor("lp1", anyStay())

        assertEquals(AppError.NoAvailability, (outcome as Outcome.Failure).error)
        assertFalse("retrying will not conjure a room", AppError.NoAvailability.isRetryable)
    }

    @Test
    fun `a failed rate call for a booking is reported rather than swallowed`() = runTest {
        // Unlike the list, a booking cannot proceed on a missing price.
        remote.ratesError = AppError.NoConnection

        assertEquals(AppError.NoConnection, (repository.rateFor("lp1", anyStay()) as Outcome.Failure).error)
    }

    private fun anyStay() = ValidStay(
        checkIn = LocalDate.of(2026, 10, 1),
        checkOut = LocalDate.of(2026, 10, 3),
        nights = 2,
        rooms = 1,
    )
}
