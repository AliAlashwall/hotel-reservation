# Hotel Reservation

An Android app for browsing hotels, searching and filtering them, viewing full details,
saving favourites that work with no internet, and completing a simulated booking with a
VAT breakdown and a local booking reference.

Kotlin, Jetpack Compose, MVI, Clean Architecture across 13 Gradle modules, Room as the
single source of truth, Retrofit against a real hotel API, Hilt throughout.
**165 unit tests, 0 failures.**

---

## Table of contents

1. [Quick start](#1-quick-start)
2. [What the app does](#2-what-the-app-does)
3. [The API](#3-the-api)
4. [Module map](#4-module-map)
5. [How a request flows through the app](#5-how-a-request-flows-through-the-app)
6. [The domain model](#6-the-domain-model)
7. [The database](#7-the-database)
8. [Caching and refresh policy](#8-caching-and-refresh-policy)
9. [Pagination](#9-pagination)
10. [Search and filters](#10-search-and-filters)
11. [Why MVI](#11-why-mvi)
12. [Error handling](#12-error-handling)
13. [Booking and money](#13-booking-and-money)
14. [Navigation](#14-navigation)
15. [Dependency injection](#15-dependency-injection)
16. [UI decisions](#16-ui-decisions)
17. [Configuration changes and process death](#17-configuration-changes-and-process-death)
18. [Testing](#18-testing)
19. [Build configuration](#19-build-configuration)
20. [AI assistance](#20-ai-assistance)

---

## 1. Quick start

### Requirements

| Thing | Version |
|---|---|
| JDK | 17 or newer (the Gradle daemon provisions JDK 25 itself) |
| Android SDK | API 37 installed (`compileSdk = 37`) |
| Device or emulator | API 26 or newer (`minSdk = 26`) |
| Gradle | 9.7.1, supplied by the wrapper — do not install it separately |

### Step 1: get a free API key

The app talks to [LiteAPI](https://nuitee.com). Sign up, open **Developers → API Keys**,
and copy the **sandbox** key. It is free and needs no credit card. The key looks like
`sand_xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx`.

### Step 2: configure it locally

```bash
cp local.properties.example local.properties
```

Then fill in both values:

```properties
sdk.dir=/Users/you/Library/Android/sdk
LITEAPI_KEY=sand_xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
```

`local.properties` is gitignored. The key is read at build time by
`core-network/build.gradle.kts` and reaches the code through `BuildConfig.LITEAPI_KEY`,
so it never appears in a source file, a manifest, a URL or a log line.

**The app still builds with no key.** Every request then returns `401`, which the app
renders as a readable message pointing at this file, rather than crashing. That is a
deliberate choice so a reviewer who has not read this far still gets a working build and
a clear explanation.

### Step 3: build and run

```bash
./gradlew assembleDebug              # app/build/outputs/apk/debug/app-debug.apk (21 MB)
./gradlew assembleRelease            # R8-shrunk, unsigned (2.3 MB)
./gradlew testDebugUnitTest          # all 165 unit tests, no device needed, ~40 s
./gradlew connectedDebugAndroidTest  # 3 end-to-end UI tests, needs a device or emulator
```

Running one module or one class:

```bash
./gradlew :core-domain:test
./gradlew :core-data:testDebugUnitTest --tests "*DefaultHotelRepositoryTest*"
```

The release APK is **unsigned**, because the keystore should not live in the repository.
For installing and demoing, use `app-debug.apk`.

---

## 2. What the app does

### Hotel list

Loads hotels from the API and shows a photo, name, city, star rating, guest score,
review count and nightly price. Pages in twenties as the user approaches the end.
Pull to refresh. A search field filters by hotel name with a 350 ms debounce. A filter
sheet offers country, city, minimum guest rating and a price band, with a badge on the
toolbar showing how many filters are on.

Five distinct states, all reachable and all handled:

| State | What is shown |
|---|---|
| Initial loading | Skeleton placeholder cards shaped like the real ones |
| Content | The list |
| Incremental loading | A spinner in the list footer |
| Empty | "No hotels match your search", with advice to widen the filters |
| Failure | Full-screen error with Retry if nothing is cached; a dismissable bar above the list if rows are already showing |

### Hotel details

A swipeable photo gallery with dot indicators, the name, address and resolved country
name, star rating and guest score, a wrapping list of amenities, the full description
with its HTML stripped, coordinates, check-in and check-out times, and a bottom bar with
the nightly price and a Book button. A heart in the toolbar adds or removes the hotel
from favourites.

### Favourites

Everything the user has hearted, sorted by name. Works with **no internet at all**,
because favourites are exempt from cache eviction. Removing one from here removes it
everywhere immediately.

### Booking

Check-in and check-out date pickers that refuse impossible dates before they can be
tapped, a room stepper, and a live price summary that updates as the guest changes
anything. Confirming re-checks the live price, and if it moved, refuses and shows both
totals. On success it generates a local reference such as `HR-260908-K7XQ4` and shows a
receipt screen that survives the process being killed.

---

## 3. The API

### Why LiteAPI

I checked four options against the fields the task requires. Only one covered all of them.

| API | Key needed | Images | Price | Rating | Amenities | Description |
|---|---|---|---|---|---|---|
| **LiteAPI v3.0** | free sandbox | yes | yes | yes | yes | yes |
| Makcorps free tier | JWT | no | yes | no | no | no |
| Amadeus Self-Service | key + secret | no | yes | yes | thin | thin |
| OpenStreetMap Overpass | **none** | no | **no** | stars only | tags only | no |

Overpass is the only genuinely keyless option, and it has neither prices nor photos, so
two of the five fields the list is required to show would have had to be invented.

### Endpoints used

| Purpose | Call |
|---|---|
| Catalogue page | `GET /v3.0/data/hotels?countryCode&cityName&hotelName&minRating&limit&offset` |
| Hotel details | `GET /v3.0/data/hotel?hotelId` |
| Prices | `POST /v3.0/hotels/min-rates` |
| Country list | `GET /v3.0/data/countries` |
| City list | `GET /v3.0/data/cities?countryCode` |

Auth is an `X-API-Key` header, attached by an OkHttp interceptor in `NetworkModule`.

---

## 4. Module map

13 modules. The graph, not a naming convention, is what enforces the architecture.

```
                        ┌──────────────────────────────┐
                        │            :app              │  composition root
                        │  NavHost, DI assembly, theme │
                        └──────────────┬───────────────┘
                                       │
      ┌──────────────┬─────────────────┼──────────────────┬──────────────┐
      ▼              ▼                 ▼                  ▼              ▼
:feature-hotels :feature-detail :feature-favorites :feature-booking  :core-data
      │              │                 │                  │         (implementations)
      └──────────────┴────────┬────────┴──────────────────┘              │
                              ▼                                          │
                       :core-domain  ◄──────────────────────────────────-┘
                    contracts + use cases                     │
                              │                               ▼
                              ▼                     :core-network  :core-database
                        :core-model
                     pure Kotlin types
```

| Module | Type | Files / lines | Contents |
|---|---|---|---|
| `:core-model` | Kotlin JVM | 7 / 348 | `Money`, `HotelSummary`, `HotelDetail`, `HotelFilters`, `PriceRange`, `AppError`, `Outcome`, `Page`, booking types |
| `:core-domain` | Kotlin JVM | 8 / 384 | 4 repository interfaces, 4 use cases |
| `:core-network` | Android lib | 10 / 585 | Retrofit service, DTOs, mappers, error translation, HTML stripping, DI |
| `:core-database` | Android lib | 8 / 407 | 5 Room entities, 2 DAOs, database, DI |
| `:core-data` | Android lib | 7 / 556 | 4 repository implementations, cache policy, entity mappers, DI |
| `:core-ui` | Android lib | 10 / 994 | Theme, hotel card, state views, skeleton, notice bars, flow row, money formatting, error strings |
| `:core-testing` | Android lib | 6 / 282 | 4 fakes, `MainDispatcherRule`, test data builders |
| `:navigation` | Kotlin JVM | 1 / 32 | 5 `@Serializable` route types |
| `:feature-hotels` | Android lib | 4 / 873 | List contract, ViewModel, screen, filter sheet |
| `:feature-detail` | Android lib | 3 / 448 | Detail contract, ViewModel, screen |
| `:feature-favorites` | Android lib | 3 / 156 | Favourites contract, ViewModel, screen |
| `:feature-booking` | Android lib | 4 / 865 | Booking contract, ViewModel, form screen, confirmation screen |
| `:app` | Android app | 3 / 212 | Application, MainActivity, NavHost |

### The four rules the graph enforces

**Features depend on `:core-domain`, never on `:core-data`.** A feature module can see
the `HotelRepository` interface and has no way to reach `DefaultHotelRepository`. Only
`:app` sees both. That is what makes the whole data layer swappable at one seam, and the
end-to-end UI test uses exactly that seam to run with no network and no database.

**`:core-model` and `:core-domain` are pure Kotlin JVM modules, not Android libraries.**
If either ever needs an Android import, something from an outer layer has leaked inwards
and the build fails. Their tests also run in milliseconds with no Robolectric.

**Features never depend on each other.** `:navigation` holds the route types, so
`:feature-hotels` can navigate to a hotel's details without depending on
`:feature-detail`.

**A route carries an id, never an object.** Routes are written into saved state and
survive process death, so putting a hotel model in one would mean rendering data that
could be hours stale. The id is re-read from the cache instead.

### Where logic lives

No business logic sits in `MainActivity` or in any composable. Nights, base price, VAT and
totals come from `CalculateQuote`; date rules from `ValidateStay`; the confirmation
sequence from `ConfirmBooking`. ViewModels marshal input and render results.

Use cases exist only where there is real logic. Pass-through use cases that forward one
call to one repository method would be ceremony, so screens call repository contracts
directly for plain reads. That is a deliberate departure from textbook Clean Architecture
and it is why there are four use cases rather than fifteen.

---

## 5. How a request flows through the app

Opening the app and scrolling to the second page, end to end.

```
 1  HotelListScreen composes, collects HotelListViewModel.state
 2  ViewModel init: appliedFilters emits HotelFilters(countryCode="EG")
 3  loadIfNeeded() reads the cache first
       └─ nothing cached  →  refresh(filters)
 4  DefaultHotelRepository.refresh
       ├─ locks.getOrPut("EG|||||").tryLock()          ← drops a duplicate call
       ├─ evictExpired()                                ← result sets older than 24 h
       └─ loadPage(filters, offset = 0, replace = true)
 5  remote.hotels(filters, 0, 20)
       └─ LiteApiService.hotels(...)  →  GET /data/hotels?countryCode=EG&limit=20&offset=0
          └─ apiCall { } wraps it: any throwable becomes an AppError
 6  Page(items = 20 HotelSummary with nightlyRate = null, offset = 0, total = 4162)
 7  attachPrices(items)
       └─ POST /hotels/min-rates for those 20 ids, 1 night, 30 days out
          └─ failure here is swallowed: rows keep nightlyRate = null
 8  filters.priceRange.contains(...) drops rows outside the band
 9  dao.insertPage(...)  in one transaction:
       ├─ result_sets      ← total = 4162, loadedCount = 20
       ├─ hotels           ← upsert, favourite flags preserved
       └─ result_set_entries ← (cacheKey, hotelId) composite key kills duplicates
10  Room emits. observeFeed combines rows + metadata into a HotelFeed
11  ViewModel combines HotelFeed + favourite ids + load status into HotelListState
12  The list draws. phase = Content

    ... user scrolls to within 4 items of the end ...

13  LazyColumn's derivedStateOf flips → HotelListIntent.LoadMore
14  claimAppendSlot() takes the slot atomically     ← drops the other frames' calls
15  hotels.observeFeed(...).first().isLastPage?  no
16  loadNextPage → resultSetMeta.loadedCount = 20 → loadPage(offset = 20)
17  same as steps 5-10, replaceExisting = false, positions 20..39
```

Every arrow marked "drops" or "swallowed" is covered by a named test.

---

## 6. The domain model

All in `:core-model`, all pure Kotlin.

### `Money`

Backed by `BigDecimal`, not `Double`. The booking total is a rate multiplied by nights and
rooms with 15% VAT on top, and binary floating point drifts across that chain. Concretely,
`131.29 * 3` as a `Double` is `393.87000000000006`.

Every amount is normalised to two decimal places with `HALF_UP` in `Money.of(...)`, so
equality behaves the way a reader expects: `Money("1.50")` equals `Money("1.5")`, which
raw `BigDecimal` equality would deny. `plus` refuses to combine two currencies rather than
silently producing a wrong number.

`percentOf` keeps 10 intermediate digits and rounds **once** at the end. Rounding the
intermediate first would turn 15% of 0.17 into 0.02 instead of 0.03.

### `Outcome<T>`

```kotlin
sealed interface Outcome<out T> {
    data class Success<T>(val value: T) : Outcome<T>
    data class Failure(val error: AppError) : Outcome<Nothing>
}
```

Preferred over `kotlin.Result` because failures are `AppError` values rather than
throwables, so an unhandled case is a compile error instead of a crash at runtime.

### `AppError`

Nine cases: `NoConnection`, `Timeout`, `Unauthorized`, `BadRequest`, `NotFound`, `Server`,
`MalformedResponse`, `NoAvailability`, `Unknown`. Each carries `isRetryable`, which is
what stops the UI offering a Retry button for a rejected API key.

### `HotelSummary` and `HotelDetail`

`HotelSummary` is a list row. `nightlyRate` is nullable because it does not come from the
same call as the rest of the row — a missing price is a normal state to render, not an
error.

`HotelDetail` carries `countryCode` rather than a country name, because that is all the
API returns. The name is resolved on device through `java.util.Locale`, so no second
network call is needed to show "Egypt".

### `HotelFilters` and its cache key

```kotlin
val cacheKey: String get() = listOf(
    countryCode, city.orEmpty(), query.trim().lowercase(),
    minReviewScore?.toString().orEmpty(),
    priceRange.min?.toString().orEmpty(), priceRange.max?.toString().orEmpty(),
).joinToString(separator = "|")
```

This one string identifies a result set. Normalising case and whitespace means `"Hilton"`,
`"hilton"` and `"  hilton "` share one cache entry rather than fetching the same rows
three times. The `|` separator stops city `"AB"` with an empty query colliding with city
`"A"` and query `"B"` — there is a test for exactly that.

### `PriceRange`

Whole currency units, both ends inclusive, both optional, validated at construction so a
reversed band cannot exist. One subtle rule: a hotel with **no known price** passes an
unbounded band and fails any real band. Hiding unpriced hotels by default would silently
shrink the catalogue; claiming they match a band we cannot check would be a lie.

### `Page<T>`

Carries `items`, `offset` and `total`, **as the server returned them, before any filtering
we do**. This distinction is the whole reason the type exists. See
[Pagination](#9-pagination).

### The repository contracts

Four interfaces in `:core-domain`. The important shape is in `HotelRepository`: reads and
writes are split.

```kotlin
fun observeFeed(filters: HotelFilters): Flow<HotelFeed>    // never fails, never blocks
suspend fun refresh(filters: HotelFilters): Outcome<Unit>  // the only network path
suspend fun loadNextPage(filters: HotelFilters): Outcome<Unit>
```

Reads come from the cache, so a screen always has something to draw. Only writes touch the
network and only they return an `Outcome`. That split makes "show the cache, then say the
refresh failed" the natural shape rather than a special case.

`HotelFeed` bundles rows, the end-of-list marker, the last refresh time and the staleness
flag into one value, so the list, the footer and the banner can never disagree.

### The use cases

| Use case | What it does |
|---|---|
| `ValidateStay` | Turns a `StayRequest` into a `ValidStay` or a list of `StayError`. Reports **every** problem at once, not the first. Takes a `Clock`. |
| `CalculateQuote` | `ValidStay` + `Money` → `BookingQuote`. Only accepts a validated stay, so no code path can quote reversed dates. |
| `GenerateBookingReference` | `HR-260908-K7XQ4`. Takes `Clock` and `Random`, so tests assert exact strings. |
| `ConfirmBooking` | Validate, re-price against the live rate, refuse on a price change, persist. |

---

## 7. The database

Room, five tables, schema version 1, exported to `core-database/schemas/`.

### `hotels` — one row per hotel, for the whole app

```
id (PK) | name | city | countryCode | thumbnailUrl | starRating | reviewScore
        | reviewCount | priceAmount | priceCurrency | isFavorite | cachedAt
```

There is deliberately **no separate favourites table** and no separate search-results
table. A hotel the user favourited, a hotel in the current search and a hotel on the
details screen are the same row. That is what keeps three screens in step with no
synchronisation code at all, and it makes `isFavorite` a single source of truth rather
than a value two tables can disagree about.

The primary key is the supplier's own hotel id, written with `@Upsert`.

`priceAmount` is stored as the plain string of a `BigDecimal` so no precision is lost
going through SQLite.

### `hotel_details` — the heavy part, fetched one hotel at a time

```
hotelId (PK, FK → hotels.id ON DELETE CASCADE) | address | description | imageUrls
        | amenities | latitude | longitude | checkInFrom | checkOutBy | cachedAt
```

Kept apart from `hotels` because a list query has no use for a description or twenty image
URLs. `imageUrls` and `amenities` are newline-separated; a join table for image URLs would
buy nothing here. Cascade delete means evicting a hotel cannot leave an orphan behind.

### `result_sets` — bookkeeping for one paged result set

```
cacheKey (PK) | total | loadedCount | lastRefreshedAt
```

`cacheKey` is `HotelFilters.cacheKey`. Holding `total` and `loadedCount` in the database
rather than in a ViewModel is what lets pagination survive process death: after the app is
killed and reopened, the repository knows how far it had got without starting from offset
zero.

`loadedCount` is how many rows the **server** handed over, which is not the same as how
many survived the price filter.

### `result_set_entries` — membership, with order

```
(cacheKey, hotelId) composite PK | position
Both columns are foreign keys with ON DELETE CASCADE.
```

The composite primary key is the duplicate guard. A hotel can appear at most once in a
given result set, so a page that overlaps the previous one, or a retry that re-fetches the
same offset, cannot produce a repeated row. Room resolves the conflict by replacing, which
also repairs the position if the server reordered.

`position` is the server's order. The list is rendered by it, not by insertion order.

### `bookings`

```
reference (PK) | hotelId | hotelName | hotelCity | hotelThumbnailUrl
              | checkInEpochDay | checkOutEpochDay | rooms | nights | currency
              | nightlyRate | baseAmount | vatRate | vatAmount | total
              | createdAtEpochMillis
```

Every line of the money breakdown is stored rather than recomputed on read, so the receipt
shown after a restart is the arithmetic the guest actually agreed to, even if the VAT rate
or the rounding rules change in a later version of the app.

No foreign key to `hotels`: a booking must outlive cache eviction of the hotel it refers
to, so the fields it needs are copied in.

### One subtlety worth naming

The network has no idea what the user favourited, so a plain upsert of a freshly fetched
row would **clear the flag** the moment the user scrolled past their own favourite.
`upsertHotelsPreservingFavorites` reads the flags first and carries them across. There is a
test named exactly that.

---

## 8. Caching and refresh policy

**Room is the single source of truth.** Reads never touch the network and never fail.

### Two windows, because "warn about" and "throw away" are different questions

| Window | Duration | What happens |
|---|---|---|
| `STALE_AFTER` | 15 minutes | The cache is still served **immediately**. A bar appears above the list saying "Showing saved results" with the age, and a refresh runs behind it. |
| `RESULT_SET_TTL` | 24 hours | On the next refresh, the result set is deleted, and any hotel no longer referenced by a surviving result set is evicted with it. |

Both are in `CachePolicy`, alongside `PAGE_SIZE = 20`, `RATE_PROBE_LEAD_DAYS = 30` and
`CURRENCY = "USD"`.

### Invalidation rules

- **A filter change is a new result set,** keyed by `cacheKey`. It never appends to the
  previous one.
- **Returning to a recent search costs nothing.** `loadIfNeeded` serves a cache inside the
  stale window with no network call at all.
- **Pull to refresh** always replaces the first page (`replaceExisting = true`, which
  clears the old entries inside the same transaction).
- **Favourites are never evicted.** The eviction query is
  `DELETE FROM hotels WHERE isFavorite = 0 AND id NOT IN (SELECT hotelId FROM result_set_entries)`.
  That unconditional `isFavorite = 0` is what makes the favourites screen work offline.

### What the user sees offline

| Situation | Result |
|---|---|
| Cache fresh, no network | Rows shown. Nothing said, because nothing is wrong. |
| Cache stale, no network | Rows shown, "Showing saved results — 3 h ago" bar, plus a failure bar with Retry. |
| No cache, no network | Full-screen error with Retry. |
| Favourites tab, no network, ever | Always works. Those rows cannot be evicted. |

---

## 9. Pagination

### Hand-written, not Paging 3

Paging 3 is what most production apps use and would have been less code. I chose against
it for one concrete reason and three supporting ones.

**The concrete reason: the price filter cannot work inside it.** Because the API ignores
`minPrice` and `maxPrice`, filtering happens after `min-rates` returns. Paging sizes a page
by what the `PagingSource` reports, so a load that returns six rows after filtering is
treated as a nearly empty page and prefetch stops — the user is left staring at a short
list with four thousand hotels unshown. Fixing that means looping inside `load()` until the
page fills, which breaks offset bookkeeping and prefetch distance. That is fighting the
library, not using it.

**Supporting reasons.** `PagingData` is an opaque hot stream that cannot live inside an
immutable state class, so list content would sit outside the MVI state while filters sit
inside it. And the two behaviours the task names explicitly — no duplicate results, no
duplicate pagination requests — would become library internals rather than things a test
can demonstrate.

If price filtering were server-side I would use Paging 3 without hesitation.

### The offset rule

This is the single most important line in the pagination code:

> Advance the offset by the **raw** page size the server returned, never by the number of
> rows that survived filtering.

A page of 20 that yields 6 after the price band still advances the offset by 20. Advancing
by 6 would re-fetch rows 6..19 forever. `Page.nextOffset` is `offset + items.size` on the
unfiltered page, and `ResultSetEntity.loadedCount` stores it.

The end of the list is decided by `loadedCount >= total`, never by the visible row count.

### Preventing overlapping and duplicate requests

Three guards at three levels:

**1. In the ViewModel, synchronously.**

```kotlin
private fun claimAppendSlot(): Boolean =
    !loadStatus.getAndUpdate { it.copy(isAppending = true, appendError = null) }.isAppending
```

The scroll listener can fire several times inside one frame, and every one of those calls
runs to completion before any coroutine body does. Setting the flag inside the coroutine
would let all of them through. **This was a real bug that a test caught**, and the test is
`a second load-more while one is running is ignored`.

**2. In the ViewModel, across filter changes.** The refresh loop uses `collectLatest`, so
a search superseded by more typing is cancelled rather than racing.

**3. In the repository.** One `Mutex` per result set, held with `tryLock`, not `withLock`.
A second request for the same filters while one is in flight is **dropped, not queued**.
Queueing would satisfy the requirement on paper and still fire two network calls.

A failed page load also blocks further attempts until the user retries, so a list parked
at a failed page does not hammer the API once per frame.

---

## 10. Search and filters

### Debounce

350 ms, applied to the **filters** rather than to the text field, so typing stays instant
while the network waits for the user to stop.

```
query ──debounce(350)──▶ distinctUntilChanged ──▶ appliedFilters ──collectLatest──▶ refresh
  │
  └──▶ state.query (immediate, drives the text field)
```

`distinctUntilChanged` after the debounce means typing a letter and deleting it produces
**no request at all**, because the applied filters return to where they started. There is
a test for that.

An empty query skips the debounce entirely (`debounce { if (it.isEmpty()) 0 else 350 }`),
so clearing the search feels instant.

### Which filters run where

| Filter | Where | How |
|---|---|---|
| Hotel name | Server | `&hotelName=` |
| Country | Server | `&countryCode=` (required by the API on every call) |
| City | Server | `&cityName=` |
| Minimum guest rating | Server | `&minRating=` |
| Price band | **Client** | After `min-rates`, because the server ignores the parameters |

Country and city lists come from the API's own `/data/countries` and `/data/cities`,
cached in memory for the process lifetime behind a `Mutex` so opening the filter sheet
twice quickly fetches once. A failed lookup leaves the pickers on their defaults and does
**not** error the screen — the pickers are a convenience, not the content.

The filter sheet writes straight through to the ViewModel with no local draft, so closing
and reopening it shows what is actually applied. The price slider is the one exception: it
holds the drag locally and commits on release, because writing on every pixel would key a
new cached result set per frame.

---

## 11. Why MVI

Every screen is one immutable state class, a sealed intent type, and a `Channel` for
one-shot effects.

**The reason is the hotel list.** It has to hold search text, four filters, a page cursor,
three loading flags, an end-of-list marker and a staleness banner, and all of them have to
agree. With MVVM each of those is its own observable and the reader has to join them
mentally. Here an inconsistent screen is an unrepresentable value.

The clearest payoff is the list phase:

```kotlin
val phase: ListPhase get() = when {
    hotels.isNotEmpty() -> ListPhase.Content
    isRefreshing        -> ListPhase.Loading
    refreshError != null-> ListPhase.Error
    hasCachedData       -> ListPhase.Empty
    else                -> ListPhase.Loading
}
```

Derived, never stored, so it cannot contradict the fields it comes from. Content wins over
everything: once there are rows, a failed refresh is a bar beside the list rather than a
replacement for it. And `hasCachedData` is what distinguishes "this search matched
nothing" from "nothing has loaded yet" — without it the empty state would flash on first
launch.

**One-shot events are effects, not state.** Navigation goes through a `Channel`, so a
rotation cannot replay a screen the user already opened.

**What it costs.** More code than MVVM: a state class, an intent type and a `when` per
screen. For the favourites screen, which has one list and no filters, that is close to
pure overhead. I kept it for consistency rather than mixing two patterns in one app.

---

## 12. Error handling

One translation, at one boundary. `apiCall()` in `:core-network`:

| Thrown | Becomes |
|---|---|
| `UnknownHostException`, `ConnectException`, any other `IOException` | `NoConnection` |
| `SocketTimeoutException` | `Timeout` |
| `SerializationException` | `MalformedResponse` |
| `HttpException` 401 / 403 | `Unauthorized` |
| `HttpException` 404 | `NotFound` |
| `HttpException` 4xx | `BadRequest(apiMessage)` |
| `HttpException` 5xx | `Server(code, apiMessage)` |
| `CancellationException` | **rethrown**, never swallowed |

That last row matters: swallowing cancellation would turn every superseded search into an
error banner and would break structured concurrency.

For 4xx and 5xx the body is parsed too, because LiteAPI puts the real explanation there
rather than in the status line — a bad hotel id comes back as
`"hotelId is missing or invalid"`.

`AppError.messageRes` in `:core-ui` is the single place an error becomes words, so adding a
new error case is a compile error there rather than an unhandled branch in four ViewModels.

### Failures are scoped to what they broke

| Failure | Effect |
|---|---|
| Refresh, nothing cached | Full-screen `ErrorState` with Retry if retryable |
| Refresh, rows on screen | `RefreshErrorBar` above the list, list stays fully usable |
| Next page | Footer message with Retry, loaded rows untouched |
| Rate lookup during a list load | Invisible; row shows "Price unavailable" |
| Rate lookup during confirmation | Fatal to that booking; a booking cannot proceed on a price nobody quoted |
| Countries or cities lookup | Invisible; pickers stay on their defaults |

---

## 13. Booking and money

### The arithmetic

```
base  = nightlyRate × nights × rooms
vat   = base × 15%              (taken once, on the multiplied base)
total = base + vat
```

**VAT is taken once on the base, not per night.** For three nights at 262.57, rounding VAT
per night and summing gives 118.17; taking it once on 787.71 gives 118.16. The second is
correct, and `CalculateQuoteTest` names that exact case.

A worked example, straight out of the tests:

| Line | Value |
|---|---|
| Nightly rate | 200.00 |
| 200.00 × 2 nights × 1 room | **400.00** base |
| VAT 15% | **60.00** |
| Total | **460.00** |

### Validation

`ValidateStay` reports every problem at once so a form can mark all bad fields in one pass.
It covers: missing check-in, missing check-out, check-in in the past, check-out on or
before check-in (one rule, covering both the same-day and reversed cases the task names),
stay longer than 30 nights, fewer than 1 room, more than 8 rooms.

Two layers of defence on dates. The Material date picker refuses unselectable dates, so an
invalid one cannot be tapped: check-in cannot be before today, and check-out cannot be
before check-in plus one. Validation still covers everything, because a date can survive in
saved state from before midnight.

Choosing a check-in **after** the current check-out clears the check-out rather than
silently shifting it, which would put the guest on a date they never picked.

### The price-change guard

The rate shown while browsing is an indicative figure for a fixed probe window, not a quote
for the guest's dates. Confirming without asking again would book a price the supplier
never offered.

```
Confirm ──▶ ConfirmBooking
              ├─ validate                        → Invalid(errors)
              ├─ hotels.rateFor(id, stay)        → Failed(error)
              ├─ quote = calculate(liveRate)
              ├─ acceptedQuote.total != quote.total?  → PriceChanged(previous, current)
              └─ generate reference, persist     → Confirmed(booking)
```

On `PriceChanged` the app shows a dialog with both totals and a "Book at $575.00" button.
Pressing it re-submits with the new quote as the accepted one, so the second attempt goes
through. Silently charging the new price, or silently honouring the stale one, are both
worse.

### Booking references

`HR-260908-K7XQ4`. The alphabet is `ABCDEFGHJKLMNPQRTUVWXY2346789` — it leaves out `O`/`0`,
`I`/`1` and `S`/`5`, because a guest reads this aloud or types it from a screenshot.
`Clock` and `Random` are injected, so a test asserts the exact format and that 500
consecutive references contain no confusable character.

---

## 14. Navigation

Navigation Compose with type-safe routes. Five destinations in `:navigation`:

```kotlin
@Serializable data object HotelsRoute
@Serializable data class  HotelDetailRoute(val hotelId: String)
@Serializable data object FavoritesRoute
@Serializable data class  BookingRoute(val hotelId: String)
@Serializable data class  BookingConfirmationRoute(val reference: String)
```

`:app` is the only module that knows every feature exists. Each feature exposes one entry
composable and takes navigation as lambdas, so no feature calls into another and none of
them touch `NavHostController`.

### Two deliberate back-stack rules

**The bottom bar shows only on Hotels and Favourites.** Details, booking and confirmation
are tasks the user is inside, and offering a tab switch mid-task invites losing a half
filled booking form.

**Confirmation pops the booking form off the stack.**

```kotlin
navController.navigate(BookingConfirmationRoute(reference)) {
    popUpTo<BookingRoute> { inclusive = true }
}
```

Pressing Back from the receipt must not return to a form for a stay that is already
booked.

Tab switching uses `popUpTo(startDestination) { saveState = true }` plus `restoreState`, so
each tab keeps its own scroll position instead of the stack growing on every tap.

---

## 15. Dependency injection

Hilt, `SingletonComponent` throughout, four modules:

| Module | Where | Provides |
|---|---|---|
| `NetworkModule` | `:core-network` | `Json`, the API-key interceptor, `OkHttpClient`, `Retrofit`, `LiteApiService` |
| `NetworkBindings` | `:core-network` | `HotelRemoteDataSource` |
| `DatabaseModule` | `:core-database` | `AppDatabase`, `HotelDao`, `BookingDao` |
| `DataModule` | `:core-data` | The four repository interfaces |
| `SystemModule` | `:core-data` | `Clock`, `Random` |

`SystemModule` is worth explaining: **time and randomness are dependencies, not ambient
facts**. Injecting them is what lets the booking tests assert an exact reference string and
an exact "check-in is in the past" boundary, instead of asserting a pattern and hoping the
suite never runs at midnight.

`DataModule` and the four `Default*Repository` classes are public purely so
`@TestInstallIn` in the app's instrumented tests can name them. Nothing outside DI refers
to those types.

ViewModels use `@HiltViewModel` and are obtained with `hiltViewModel()`, scoped to the
`NavBackStackEntry`, so each destination gets its own and it survives configuration
changes.

---

## 16. UI decisions

Material 3, one fixed palette (teal and sand), full light and dark support.

**No dynamic colour.** Prices, guest scores and the "showing saved results" bar all use
colour to carry meaning, and a wallpaper-derived scheme can land two of those on shades a
user cannot tell apart.

**Amenities wrap, they do not sit in columns.** Amenity names range from "Bar" to "Airport
shuttle service (surcharge)" in the same response. A fixed two-per-row grid squeezed the
long ones into a narrow box where the text broke a word or two per line. `TagFlowRow`
measures each label and breaks the line when the next does not fit, so every label gets the
width it needs. The same component drives the rating filter chips, which previously
scrolled sideways and hid options off-screen.

**A skeleton, not a spinner.** The first load draws placeholder cards shaped like the real
ones, so the page does not jump when content lands. One shared alpha animation drives all
of them rather than a gradient sweep per element, so it costs almost nothing on a low-end
device.

**Persistent bars, not snackbars,** for "showing saved results" and for a failed refresh. A
snackbar disappears, and a user who reaches the screen a few seconds late would never learn
that what they are reading is cached or that the refresh failed.

**Guest score as a filled pill, star rating as stars.** Both are "the rating" and both are
numbers; a solid block of colour separates them at a glance. A short gradient scrim behind
the favourite button and behind the gallery indicators keeps them legible over bright
supplier photos.

**Accessibility.** The favourite button's content description names the *action*
("Add to favourites") rather than the state, because that is what a screen reader user is
choosing to do. Every state view carries a stable test tag, so the instrumented tests
assert on those rather than on user-visible copy that will change.

---

## 17. Configuration changes and process death

**The Activity is allowed to recreate.** There is no `android:configChanges` on
`MainActivity`, so a rotation goes through a real recreation and actually exercises
ViewModel retention. Declaring `configChanges` would have made the app look correct across
rotation without ever proving it.

Three layers survive, and each survives a different thing:

| Layer | Survives rotation | Survives process death | Holds |
|---|---|---|---|
| `ViewModel` | yes | no | Loading flags, in-flight state |
| `SavedStateHandle` | yes | **yes** | Search text, all four filters, booking dates, room count |
| Room | yes | yes | Hotels, favourites, page cursors, bookings |

The list ViewModel stores filters as separate primitives in `SavedStateHandle` rather than
one object, because `SavedStateHandle` persists Bundle types and keeping `HotelFilters`
free of Android types is what lets the domain modules stay pure Kotlin.

Pagination survives process death too, because `loadedCount` and `total` live in the
`result_sets` table rather than in a ViewModel field.

`HotelListViewModelTest` proves this by building a second ViewModel over the same
`SavedStateHandle` — which is exactly what the framework does after the process is killed —
and asserting the query and filters come back.

---

## 18. Testing

165 unit tests, all on the JVM, no device needed, about 40 seconds.

| Module | Class | Tests | What it proves |
|---|---|---|---|
| `:core-model` | `MoneyTest` | 10 | Two-place normalisation, `HALF_UP`, exact multiplication, single-rounding percentages, cross-currency refusal |
| | `PriceRangeTest` | 5 | Inclusive ends, open ends, the unpriced-hotel rule, construction validation |
| | `HotelFiltersTest` | 3 | Case and whitespace normalisation, every dimension changes the key, no field collisions |
| `:core-domain` | `ValidateStayTest` | 8 | Same-day and reversed check-out, past check-in, missing dates, room bounds, 30-night cap, all errors at once |
| | `CalculateQuoteTest` | 7 | Base and VAT, per-night vs per-total rounding, `total == base + vat` across 60 combinations, currency propagation |
| | `GenerateBookingReferenceTest` | 5 | Format, determinism under a seed, no confusable characters across 500 references, no repeats across 200 |
| | `ConfirmBookingTest` | 7 | Live rate uses the guest's dates, no network call for an invalid stay, price-change refusal, re-confirmation |
| `:core-network` | `LiteApiHotelRemoteDataSourceTest` | 19 | Field mapping, query parameters, empty-result handling, image ordering, HTML stripping, every error path |
| | `HtmlTest` | 7 | Paragraph breaks, entities, numeric entities, collapse of empty blocks |
| `:core-database` | `HotelDaoTest` | 18 | Upsert dedupe, overlapping pages, cross-search sharing, server ordering, favourite survival through eviction, cascades |
| `:core-data` | `DefaultHotelRepositoryTest` | 24 | Success, paging to the exact end, overlapping-call drop, network failure with and without cache, staleness, TTL eviction, price filter vs offset |
| `:feature-hotels` | `HotelListViewModelTest` | 26 | Debounce, no-op on reverted typing, filter consistency, pagination guards, all four phases, favourites, effects, process death |
| `:feature-detail` | `HotelDetailViewModelTest` | 8 | Cache-first open, error phases, retry, favourite sync, book gating |
| `:feature-favorites` | `FavoritesViewModelTest` | 4 | Loading vs empty, cross-screen sync, removal, effects |
| `:feature-booking` | `BookingViewModelTest` | 14 | Live quoting, validation gating, date coupling, room bounds, confirmation, price change, process death |

Plus 3 instrumented tests in `:app` (`HotelJourneyTest`): the favourite-sync journey
through real navigation, search narrowing and restoration, and booking validation.

---

## 19. Build configuration

| Setting | Value | Why |
|---|---|---|
| AGP | 9.3.0 | Android Studio rejects 9.4.0. 9.3.1 and 9.3.2 fail the same check. |
| Gradle | 9.7.1 | Wrapper. Daemon runs on a JDK 25 toolchain; modules compile to JVM 17. |
| Kotlin | 2.4.20 | Set once, on the shared buildscript classpath. |
| `compileSdk` | 37 | `androidx.core:core-ktx:1.19.0` and the Compose BOM require it. |
| `targetSdk` | 36 | Unchanged runtime behaviour. |
| `minSdk` | 26 | `java.time` with no desugaring, and adaptive icons only. |
| Compose BOM | 2026.08.00 | |

### One thing that will bite anyone editing the build

**AGP 9 provides Kotlin itself.** Applying `org.jetbrains.kotlin.android` to a module is a
hard build failure. The Kotlin version a module compiles with is whichever Kotlin Gradle
Plugin sits on the shared buildscript classpath. That is why the root `build.gradle.kts`
declares every plugin once with `apply false`: it loads KGP 2.4.20 into the root
classloader so all 13 modules compile with the same Kotlin. Without it, modules that apply
no Kotlin-versioned plugin silently fall back to AGP's bundled Kotlin 2.2.0, and anything
consuming their output fails with a metadata version mismatch.

Adding a module means adding it to `settings.gradle.kts`; adding a plugin means adding it
to the root `plugins` block with `apply false`.

### R8

The release build has `isMinifyEnabled = true` and `isShrinkResources = true`, taking the
APK from 21 MB to 2.3 MB. `app/proguard-rules.pro` keeps two things R8 cannot infer:
kotlinx-serialization's generated serializers, which are found reflectively by name, and
generic signatures, which Retrofit needs to pick a converter for `ListEnvelope<HotelListItemDto>`.

---

## 20. AI assistance

**Tool used.** Claude Code (Anthropic), an agentic CLI assistant, used throughout the
build for implementation, test authoring and documentation.
