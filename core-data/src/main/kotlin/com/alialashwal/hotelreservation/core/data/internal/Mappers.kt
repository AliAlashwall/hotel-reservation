package com.alialashwal.hotelreservation.core.data.internal

import com.alialashwal.hotelreservation.core.database.entity.BookingEntity
import com.alialashwal.hotelreservation.core.database.entity.HotelDetailEntity
import com.alialashwal.hotelreservation.core.database.entity.HotelEntity
import com.alialashwal.hotelreservation.model.Booking
import com.alialashwal.hotelreservation.model.BookingQuote
import com.alialashwal.hotelreservation.model.BookingReference
import com.alialashwal.hotelreservation.model.Coordinates
import com.alialashwal.hotelreservation.model.HotelDetail
import com.alialashwal.hotelreservation.model.HotelSummary
import com.alialashwal.hotelreservation.model.Money
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

internal fun HotelEntity.toDomain(): HotelSummary = HotelSummary(
    id = id,
    name = name,
    city = city,
    countryCode = countryCode,
    thumbnailUrl = thumbnailUrl,
    starRating = starRating,
    reviewScore = reviewScore,
    reviewCount = reviewCount,
    nightlyRate = money(priceAmount, priceCurrency),
)

internal fun HotelSummary.toEntity(cachedAt: Long): HotelEntity = HotelEntity(
    id = id,
    name = name,
    city = city,
    countryCode = countryCode,
    thumbnailUrl = thumbnailUrl,
    starRating = starRating,
    reviewScore = reviewScore,
    reviewCount = reviewCount,
    priceAmount = nightlyRate?.amount?.toPlainString(),
    priceCurrency = nightlyRate?.currency,
    // Never written from here. The DAO carries the existing flag across an upsert.
    isFavorite = false,
    cachedAt = cachedAt,
)

/** Detail is stored in two rows, so it is rebuilt from both. */
internal fun HotelDetailEntity.toDomain(summary: HotelEntity): HotelDetail = HotelDetail(
    id = summary.id,
    name = summary.name,
    city = summary.city,
    countryCode = summary.countryCode,
    address = address,
    description = description,
    imageUrls = imageUrls.splitLines(),
    amenities = amenities.splitLines(),
    starRating = summary.starRating,
    reviewScore = summary.reviewScore,
    reviewCount = summary.reviewCount,
    coordinates = coordinatesOf(latitude, longitude),
    checkInFrom = checkInFrom,
    checkOutBy = checkOutBy,
    nightlyRate = money(summary.priceAmount, summary.priceCurrency),
)

internal fun HotelDetail.toDetailEntity(cachedAt: Long): HotelDetailEntity = HotelDetailEntity(
    hotelId = id,
    address = address,
    description = description,
    imageUrls = imageUrls.joinToString(separator = "\n"),
    amenities = amenities.joinToString(separator = "\n"),
    latitude = coordinates?.latitude,
    longitude = coordinates?.longitude,
    checkInFrom = checkInFrom,
    checkOutBy = checkOutBy,
    cachedAt = cachedAt,
)

/** A details response also refreshes the list row, so deep-linking into a hotel the
 *  list has never shown still leaves something for the favourites screen to hold. */
internal fun HotelDetail.toSummaryEntity(cachedAt: Long): HotelEntity = HotelEntity(
    id = id,
    name = name,
    city = city,
    countryCode = countryCode,
    thumbnailUrl = imageUrls.firstOrNull(),
    starRating = starRating,
    reviewScore = reviewScore,
    reviewCount = reviewCount,
    priceAmount = nightlyRate?.amount?.toPlainString(),
    priceCurrency = nightlyRate?.currency,
    isFavorite = false,
    cachedAt = cachedAt,
)

internal fun BookingEntity.toDomain(): Booking = Booking(
    reference = BookingReference(reference),
    hotelId = hotelId,
    hotelName = hotelName,
    hotelCity = hotelCity,
    hotelThumbnailUrl = hotelThumbnailUrl,
    checkIn = LocalDate.ofEpochDay(checkInEpochDay),
    checkOut = LocalDate.ofEpochDay(checkOutEpochDay),
    rooms = rooms,
    quote = BookingQuote(
        nightlyRate = Money.of(BigDecimal(nightlyRate), currency),
        nights = nights,
        rooms = rooms,
        baseAmount = Money.of(BigDecimal(baseAmount), currency),
        vatRate = BigDecimal(vatRate),
        vatAmount = Money.of(BigDecimal(vatAmount), currency),
        total = Money.of(BigDecimal(total), currency),
    ),
    createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
)

internal fun Booking.toEntity(): BookingEntity = BookingEntity(
    reference = reference.value,
    hotelId = hotelId,
    hotelName = hotelName,
    hotelCity = hotelCity,
    hotelThumbnailUrl = hotelThumbnailUrl,
    checkInEpochDay = checkIn.toEpochDay(),
    checkOutEpochDay = checkOut.toEpochDay(),
    rooms = rooms,
    nights = quote.nights,
    currency = quote.total.currency,
    nightlyRate = quote.nightlyRate.amount.toPlainString(),
    baseAmount = quote.baseAmount.amount.toPlainString(),
    vatRate = quote.vatRate.toPlainString(),
    vatAmount = quote.vatAmount.amount.toPlainString(),
    total = quote.total.amount.toPlainString(),
    createdAtEpochMillis = createdAt.toEpochMilli(),
)

private fun coordinatesOf(latitude: Double?, longitude: Double?): Coordinates? =
    if (latitude != null && longitude != null) Coordinates(latitude, longitude) else null

private fun money(amount: String?, currency: String?): Money? =
    if (amount != null && currency != null) Money.of(BigDecimal(amount), currency) else null

private fun String.splitLines(): List<String> =
    if (isBlank()) emptyList() else split('\n').filter { it.isNotBlank() }
