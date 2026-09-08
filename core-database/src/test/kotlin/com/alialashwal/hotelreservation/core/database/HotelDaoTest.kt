package com.alialashwal.hotelreservation.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.alialashwal.hotelreservation.core.database.dao.HotelDao
import com.alialashwal.hotelreservation.core.database.entity.HotelDetailEntity
import com.alialashwal.hotelreservation.core.database.entity.HotelEntity
import com.alialashwal.hotelreservation.core.database.entity.ResultSetEntity
import com.alialashwal.hotelreservation.core.database.entity.ResultSetEntryEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Runs against real SQLite through Robolectric rather than against a fake DAO, because
 * the behaviours under test here are the schema's, not our code's: composite keys,
 * cascade deletes and the order a join returns rows in.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HotelDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: HotelDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.hotelDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    // --------------------------------------------------- duplicate prevention

    @Test
    fun `caching the same hotel twice updates the row instead of adding one`() = runTest {
        dao.upsertHotels(listOf(hotel("lp1", name = "Old name")))
        dao.upsertHotels(listOf(hotel("lp1", name = "New name")))

        assertEquals(1, dao.hotelCount())
        assertEquals("New name", dao.observeHotel("lp1").first()!!.name)
    }

    @Test
    fun `a page that overlaps the previous one cannot repeat a row in the result set`() = runTest {
        // A retry, or a server that shifts results between calls, sends us the same
        // hotel at two offsets. The composite key is what stops it appearing twice.
        writePage(KEY, offset = 0, hotels = listOf(hotel("lp1"), hotel("lp2")))
        writePage(KEY, offset = 2, hotels = listOf(hotel("lp2"), hotel("lp3")))

        assertEquals(listOf("lp1", "lp2", "lp3"), dao.observeResultSet(KEY).first().map { it.id })
    }

    @Test
    fun `re-inserting a hotel at a new position moves it rather than duplicating it`() = runTest {
        writePage(KEY, offset = 0, hotels = listOf(hotel("lp1"), hotel("lp2")))
        dao.upsertResultSetEntries(listOf(ResultSetEntryEntity(KEY, "lp2", position = 0)))
        dao.upsertResultSetEntries(listOf(ResultSetEntryEntity(KEY, "lp1", position = 1)))

        assertEquals(listOf("lp2", "lp1"), dao.observeResultSet(KEY).first().map { it.id })
    }

    @Test
    fun `the same hotel in two searches is stored once and listed in both`() = runTest {
        writePage(KEY, offset = 0, hotels = listOf(hotel("lp1"), hotel("lp2")))
        writePage(OTHER_KEY, offset = 0, hotels = listOf(hotel("lp2"), hotel("lp9")))

        assertEquals(3, dao.hotelCount())
        assertTrue("lp2" in dao.observeResultSet(KEY).first().map { it.id })
        assertTrue("lp2" in dao.observeResultSet(OTHER_KEY).first().map { it.id })
    }

    @Test
    fun `rows come back in server order, not insertion order`() = runTest {
        dao.upsertHotels(listOf(hotel("lp3"), hotel("lp1"), hotel("lp2")))
        dao.upsertResultSet(resultSet(KEY, total = 3, loaded = 3))
        dao.upsertResultSetEntries(
            listOf(
                ResultSetEntryEntity(KEY, "lp3", position = 2),
                ResultSetEntryEntity(KEY, "lp1", position = 0),
                ResultSetEntryEntity(KEY, "lp2", position = 1),
            )
        )

        assertEquals(listOf("lp1", "lp2", "lp3"), dao.observeResultSet(KEY).first().map { it.id })
    }

    @Test
    fun `replacing a result set drops the old rows and keeps the new order`() = runTest {
        writePage(KEY, offset = 0, hotels = listOf(hotel("lp1"), hotel("lp2")))

        dao.insertPage(
            resultSet = resultSet(KEY, total = 1, loaded = 1),
            hotels = listOf(hotel("lp9")),
            entries = listOf(ResultSetEntryEntity(KEY, "lp9", position = 0)),
            replaceExisting = true,
        )

        assertEquals(listOf("lp9"), dao.observeResultSet(KEY).first().map { it.id })
    }

    // ------------------------------------------------------------- favourites

    @Test
    fun `toggling a favourite flips it and reports the state it landed in`() = runTest {
        dao.upsertHotels(listOf(hotel("lp1")))

        assertTrue(dao.toggleFavorite("lp1"))
        assertTrue(dao.observeIsFavorite("lp1").first())
        assertFalse(dao.toggleFavorite("lp1"))
        assertFalse(dao.observeIsFavorite("lp1").first())
    }

    @Test
    fun `favouriting from one screen is observed by the others`() = runTest {
        // This is the whole reason favourites is a column and not a second table.
        dao.upsertHotels(listOf(hotel("lp1", name = "Kempinski")))

        dao.observeFavorites().test {
            assertEquals(emptyList<HotelEntity>(), awaitItem())

            dao.toggleFavorite("lp1")

            assertEquals(listOf("Kempinski"), awaitItem().map { it.name })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `favourites are sorted by name, ignoring case`() = runTest {
        dao.upsertHotels(listOf(hotel("lp1", name = "zeta"), hotel("lp2", name = "Alpha")))
        dao.toggleFavorite("lp1")
        dao.toggleFavorite("lp2")

        assertEquals(listOf("Alpha", "zeta"), dao.observeFavorites().first().map { it.name })
    }

    @Test
    fun `re-fetching a favourited hotel does not clear the flag`() = runTest {
        // The network has no idea what the user favourited. A plain upsert of a fresh
        // row would wipe the flag as soon as the hotel appeared in another search.
        writePage(KEY, offset = 0, hotels = listOf(hotel("lp1", name = "Old name")))
        dao.toggleFavorite("lp1")

        dao.upsertHotelsPreservingFavorites(listOf(hotel("lp1", name = "Refreshed name")))

        assertTrue(dao.observeIsFavorite("lp1").first())
        assertEquals("Refreshed name", dao.observeHotel("lp1").first()!!.name)
    }

    @Test
    fun `asking whether an unknown hotel is a favourite says no rather than failing`() = runTest {
        assertFalse(dao.observeIsFavorite("never-seen").first())
        assertFalse(dao.isFavoriteNow("never-seen"))
    }

    // --------------------------------------------------------------- eviction

    @Test
    fun `eviction removes hotels no search refers to any more`() = runTest {
        writePage(KEY, offset = 0, hotels = listOf(hotel("lp1"), hotel("lp2")))
        dao.deleteResultSet(KEY)

        assertEquals(2, dao.evictUnreferencedHotels())
        assertEquals(0, dao.hotelCount())
    }

    @Test
    fun `a favourited hotel survives eviction, which is what makes favourites work offline`() = runTest {
        writePage(KEY, offset = 0, hotels = listOf(hotel("lp1"), hotel("lp2")))
        dao.toggleFavorite("lp1")
        dao.deleteResultSet(KEY)

        dao.evictUnreferencedHotels()

        assertEquals(listOf("lp1"), dao.observeFavorites().first().map { it.id })
        assertEquals(1, dao.hotelCount())
    }

    @Test
    fun `a hotel still referenced by another search is not evicted`() = runTest {
        writePage(KEY, offset = 0, hotels = listOf(hotel("lp1")))
        writePage(OTHER_KEY, offset = 0, hotels = listOf(hotel("lp1")))

        dao.deleteResultSet(KEY)
        dao.evictUnreferencedHotels()

        assertEquals(1, dao.hotelCount())
    }

    @Test
    fun `deleting a result set cascades to its entries`() = runTest {
        writePage(KEY, offset = 0, hotels = listOf(hotel("lp1")))

        dao.deleteResultSet(KEY)

        assertEquals(emptyList<HotelEntity>(), dao.observeResultSet(KEY).first())
    }

    @Test
    fun `evicting a hotel cascades to its detail row`() = runTest {
        writePage(KEY, offset = 0, hotels = listOf(hotel("lp1")))
        dao.upsertHotelDetail(detail("lp1"))
        dao.deleteResultSet(KEY)

        dao.evictUnreferencedHotels()

        assertNull(dao.observeHotelDetail("lp1").first())
    }

    @Test
    fun `only result sets older than the cut-off are dropped`() = runTest {
        dao.upsertResultSet(resultSet(KEY, total = 1, loaded = 1, refreshedAt = 1_000L))
        dao.upsertResultSet(resultSet(OTHER_KEY, total = 1, loaded = 1, refreshedAt = 5_000L))

        assertEquals(1, dao.evictResultSetsOlderThan(3_000L))
        assertNull(dao.resultSetMeta(KEY))
        assertEquals(5_000L, dao.resultSetMeta(OTHER_KEY)!!.lastRefreshedAt)
    }

    // ------------------------------------------------------------ bookkeeping

    @Test
    fun `loaded count survives a database restart, so paging resumes where it stopped`() = runTest {
        dao.upsertResultSet(resultSet(KEY, total = 4162, loaded = 40))

        val meta = dao.resultSetMeta(KEY)!!
        assertEquals(4162, meta.total)
        assertEquals(40, meta.loadedCount)
    }

    // ------------------------------------------------------------------ setup

    private suspend fun writePage(cacheKey: String, offset: Int, hotels: List<HotelEntity>) {
        dao.insertPage(
            resultSet = resultSet(cacheKey, total = 100, loaded = offset + hotels.size),
            hotels = hotels,
            entries = hotels.mapIndexed { index, h ->
                ResultSetEntryEntity(cacheKey, h.id, position = offset + index)
            },
            replaceExisting = false,
        )
    }

    private fun hotel(id: String, name: String = "Hotel $id") = HotelEntity(
        id = id,
        name = name,
        city = "Cairo",
        countryCode = "EG",
        thumbnailUrl = null,
        starRating = 5,
        reviewScore = 9.0,
        reviewCount = 10,
        priceAmount = "131.28",
        priceCurrency = "USD",
        isFavorite = false,
        cachedAt = 0L,
    )

    private fun detail(hotelId: String) = HotelDetailEntity(
        hotelId = hotelId,
        address = "12 Ahmed Ragheb Street",
        description = "On the Nile.",
        imageUrls = "https://example.test/1.jpg",
        amenities = "Free WiFi",
        latitude = 30.0,
        longitude = 31.0,
        checkInFrom = "03:00 PM",
        checkOutBy = "12:00 PM",
        cachedAt = 0L,
    )

    private fun resultSet(cacheKey: String, total: Int, loaded: Int, refreshedAt: Long = 0L) =
        ResultSetEntity(cacheKey, total = total, loadedCount = loaded, lastRefreshedAt = refreshedAt)

    private companion object {
        const val KEY = "EG||cairo|||"
        const val OTHER_KEY = "EG||nile|||"
    }
}
