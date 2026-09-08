package com.alialashwal.hotelreservation.core.network

import com.alialashwal.hotelreservation.core.network.di.NetworkModule
import com.alialashwal.hotelreservation.core.network.internal.LiteApiHotelRemoteDataSource
import com.alialashwal.hotelreservation.core.network.internal.LiteApiService
import com.alialashwal.hotelreservation.model.AppError
import com.alialashwal.hotelreservation.model.HotelFilters
import com.alialashwal.hotelreservation.model.Money
import com.alialashwal.hotelreservation.model.Outcome
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.net.HttpURLConnection
import java.time.LocalDate

/**
 * Driven by JSON captured from the live LiteAPI sandbox, not by JSON invented to match
 * the parser. If the supplier renames a field, these fail.
 */
class LiteApiHotelRemoteDataSourceTest {

    private lateinit var server: MockWebServer
    private lateinit var dataSource: LiteApiHotelRemoteDataSource

    private val json = NetworkModule.json()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        dataSource = LiteApiHotelRemoteDataSource(retrofit.create(LiteApiService::class.java), json)
    }

    @After
    fun tearDown() {
        server.close()
    }

    // ---------------------------------------------------------------- catalogue

    @Test
    fun `a catalogue page maps every field the list renders`() = runTest {
        enqueue("hotels_page.json")

        val page = dataSource.hotels(HotelFilters(), offset = 0, limit = 2).expectSuccess()

        assertEquals(4162, page.total)
        assertEquals(0, page.offset)
        val first = page.items.first()
        assertEquals("lp516fd", first.id)
        assertEquals("Kempinski Nile Hotel, Cairo", first.name)
        assertEquals("Cairo", first.city)
        assertEquals("EG", first.countryCode)               // API sends "eg"
        assertEquals(5, first.starRating)
        assertEquals(9.6, first.reviewScore!!, 0.001)
        assertEquals(4207, first.reviewCount)
        assertTrue(first.thumbnailUrl!!.startsWith("https://"))
        assertNull("price comes from a separate call", first.nightlyRate)
    }

    @Test
    fun `filters are sent as query parameters, and the ignored price params are not`() = runTest {
        enqueue("hotels_page.json")

        dataSource.hotels(
            HotelFilters(countryCode = "AE", city = "Dubai", query = " Marriott ", minReviewScore = 8.5),
            offset = 40,
            limit = 20,
        )

        val url = server.takeRequest().url
        assertEquals("AE", url.queryParameter("countryCode"))
        assertEquals("Dubai", url.queryParameter("cityName"))
        assertEquals("Marriott", url.queryParameter("hotelName"))   // trimmed
        assertEquals("8.5", url.queryParameter("minRating"))
        assertEquals("40", url.queryParameter("offset"))
        assertEquals("20", url.queryParameter("limit"))
        assertNull("the server ignores these, so we never send them", url.queryParameter("minPrice"))
    }

    @Test
    fun `a blank city or query is left out rather than sent empty`() = runTest {
        enqueue("hotels_page.json")

        dataSource.hotels(HotelFilters(city = "", query = "   "), offset = 0, limit = 20)

        val url = server.takeRequest().url
        assertNull(url.queryParameter("cityName"))
        assertNull(url.queryParameter("hotelName"))
    }

    @Test
    fun `an empty result set reports itself as finished instead of asking for more`() = runTest {
        // The catalogue omits `total` when nothing matches. Treating that as zero more
        // to fetch is what stops an endless pagination loop on a bad search.
        server.enqueue(MockResponse(code = 200, body = """{"data":[]}"""))

        val page = dataSource.hotels(HotelFilters(), offset = 60, limit = 20).expectSuccess()

        assertEquals(emptyList<Any>(), page.items)
        assertEquals(60, page.total)
        assertTrue(page.isLastPage)
    }

    // -------------------------------------------------------------------- rates

    @Test
    fun `rates are paired with the currency that was requested`() = runTest {
        enqueue("min_rates.json")

        val rates = dataSource
            .nightlyRates(listOf("lp1d1f4", "lp19ea6", "lp516fd"), PROBE, "USD")
            .expectSuccess()

        assertEquals(Money.of("262.57", "USD"), rates["lp1d1f4"])
        assertEquals(Money.of("725.23", "USD"), rates["lp19ea6"])
        assertEquals(Money.of("538.20", "USD"), rates["lp516fd"])
    }

    @Test
    fun `hotels with no price and hotels priced at zero are left out of the map`() = runTest {
        // Absent is meaningful: the list renders "price unavailable" rather than free.
        enqueue("min_rates.json")

        val rates = dataSource.nightlyRates(listOf("lpNoAvail", "lpZero"), PROBE, "USD").expectSuccess()

        assertNull(rates["lpNoAvail"])
        assertNull(rates["lpZero"])
    }

    @Test
    fun `the rate request asks for exactly one night starting at the probe date`() = runTest {
        enqueue("min_rates.json")

        dataSource.nightlyRates(listOf("lp516fd"), LocalDate.of(2026, 11, 10), "EUR")

        val body = server.takeRequest().body!!.utf8()
        assertTrue(body, """"checkin":"2026-11-10"""" in body)
        assertTrue(body, """"checkout":"2026-11-11"""" in body)
        assertTrue(body, """"currency":"EUR"""" in body)
    }

    @Test
    fun `asking for no hotels makes no network call at all`() = runTest {
        val rates = dataSource.nightlyRates(emptyList(), PROBE, "USD").expectSuccess()

        assertEquals(emptyMap<String, Money>(), rates)
        assertEquals(0, server.requestCount)
    }

    // ------------------------------------------------------------------- detail

    @Test
    fun `detail strips the HTML the API sends in the description`() = runTest {
        enqueue("hotel_detail.json")

        val detail = dataSource.hotelDetail("lp516fd").expectSuccess()

        assertTrue(detail.description, "<p>" !in detail.description)
        assertTrue(detail.description, "<strong>" !in detail.description)
        assertTrue(detail.description, detail.description.startsWith("Luxurious Accommodation"))
    }

    @Test
    fun `detail images are ordered by the API's order field and prefer the HD variant`() = runTest {
        enqueue("hotel_detail.json")

        val detail = dataSource.hotelDetail("lp516fd").expectSuccess()

        assertEquals(
            listOf(
                "https://static.cupid.travel/hotels/a-hd.jpg",   // order 0
                "https://static.cupid.travel/hotels/b.jpg",      // order 1, no HD variant
                "https://static.cupid.travel/hotels/c-hd.jpg",   // order 2
            ),
            detail.imageUrls,
        )
    }

    @Test
    fun `detail amenities drop blanks and duplicates`() = runTest {
        enqueue("hotel_detail.json")

        val amenities = dataSource.hotelDetail("lp516fd").expectSuccess().amenities

        assertEquals(amenities.size, amenities.toSet().size)
        assertTrue(amenities.none { it.isBlank() })
        assertTrue("Free WiFi" in amenities)
    }

    @Test
    fun `detail maps rating, coordinates and check-in times`() = runTest {
        enqueue("hotel_detail.json")

        val detail = dataSource.hotelDetail("lp516fd").expectSuccess()

        assertEquals(5, detail.starRating)
        assertEquals(9.6, detail.reviewScore!!, 0.001)
        assertEquals(30.038658, detail.coordinates!!.latitude, 0.000001)
        assertEquals("03:00 PM", detail.checkInFrom)
        assertEquals("12:00 PM", detail.checkOutBy)
        assertEquals("EG", detail.countryCode)
    }

    // --------------------------------------------------------------- reference

    @Test
    fun `a country with no name falls back to its code`() = runTest {
        enqueue("countries.json")

        val countries = dataSource.countries().expectSuccess()

        assertEquals("EG", countries[0].code)
        assertEquals("Egypt", countries[0].name)
        assertEquals("XX", countries[2].name)
    }

    @Test
    fun `cities are deduplicated, blank-filtered and sorted`() = runTest {
        enqueue("cities.json")

        assertEquals(listOf("Alexandria", "Aswan", "Cairo"), dataSource.cities("EG").expectSuccess())
    }

    // ------------------------------------------------------------------ errors

    @Test
    fun `a rejected key becomes Unauthorized, which the UI must not offer to retry`() = runTest {
        enqueueError(HttpURLConnection.HTTP_UNAUTHORIZED, "error_unauthorized.json")

        val error = dataSource.hotels(HotelFilters(), 0, 20).expectFailure()

        assertEquals(AppError.Unauthorized, error)
        assertTrue(!error.isRetryable)
    }

    @Test
    fun `a 400 carries the API's own explanation through to the user`() = runTest {
        enqueueError(HttpURLConnection.HTTP_BAD_REQUEST, "error_bad_request.json")

        val error = dataSource.hotelDetail("nope123").expectFailure()

        assertEquals(AppError.BadRequest("hotelId is missing or invalid"), error)
    }

    @Test
    fun `a 500 is retryable and keeps the status code`() = runTest {
        server.enqueue(MockResponse(code = 503, body = ""))

        val error = dataSource.hotels(HotelFilters(), 0, 20).expectFailure()

        assertEquals(AppError.Server(503, null), error)
        assertTrue(error.isRetryable)
    }

    @Test
    fun `a body that is not the shape we expect is reported as malformed, not as a crash`() = runTest {
        server.enqueue(MockResponse(code = 200, body = """{"data": "not-a-list"}"""))

        assertEquals(AppError.MalformedResponse, dataSource.hotels(HotelFilters(), 0, 20).expectFailure())
    }

    @Test
    fun `a dropped connection is reported as no connection`() = runTest {
        server.close()

        assertEquals(AppError.NoConnection, dataSource.hotels(HotelFilters(), 0, 20).expectFailure())
    }

    // ------------------------------------------------------------------ helpers

    private fun enqueue(fixture: String) {
        server.enqueue(MockResponse(code = 200, body = readFixture(fixture)))
    }

    private fun enqueueError(code: Int, fixture: String) {
        server.enqueue(MockResponse(code = code, body = readFixture(fixture)))
    }

    private fun readFixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/$name")) { "missing fixture $name" }
            .bufferedReader()
            .use { it.readText() }

    private fun <T> Outcome<T>.expectSuccess(): T = when (this) {
        is Outcome.Success -> value
        is Outcome.Failure -> error("expected success but failed with ${this.error}")
    }

    private fun <T> Outcome<T>.expectFailure(): AppError = when (this) {
        is Outcome.Failure -> error
        is Outcome.Success -> error("expected failure but succeeded with $value")
    }

    private companion object {
        val PROBE: LocalDate = LocalDate.of(2026, 11, 10)
    }
}
