package com.alialashwal.hotelreservation.feature.booking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alialashwal.hotelreservation.core.ui.asUserMessage
import com.alialashwal.hotelreservation.core.ui.component.FullScreenLoading
import com.alialashwal.hotelreservation.core.ui.format
import com.alialashwal.hotelreservation.model.BookingQuote
import com.alialashwal.hotelreservation.model.BookingReference
import com.alialashwal.hotelreservation.model.StayError
import kotlinx.coroutines.flow.collectLatest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun BookingRoute(
    onBack: () -> Unit,
    onConfirmed: (BookingReference) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BookingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is BookingEffect.BookingConfirmed -> onConfirmed(effect.reference)
            }
        }
    }

    BookingScreen(
        state = state,
        today = viewModel.today(),
        onIntent = viewModel::onIntent,
        onBack = onBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BookingScreen(
    state: BookingState,
    today: LocalDate,
    onIntent: (BookingIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.booking_title)) },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            stringResource(com.alialashwal.hotelreservation.core.ui.R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (state.hotel == null) {
            FullScreenLoading(modifier = Modifier.padding(padding))
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(state.hotel.name, style = MaterialTheme.typography.titleLarge)

            DateField(
                label = stringResource(R.string.booking_check_in),
                date = state.request.checkIn,
                // The picker itself refuses past dates, so an invalid one cannot be
                // produced by tapping. Validation still covers it, because a date can
                // also survive from before midnight in saved state.
                earliest = today,
                error = state.errorFor(
                    StayError.CheckInMissing::class.java,
                    StayError.CheckInInPast::class.java,
                ),
                onPick = { onIntent(BookingIntent.CheckInSelected(it)) },
            )

            DateField(
                label = stringResource(R.string.booking_check_out),
                date = state.request.checkOut,
                earliest = state.request.checkIn?.plusDays(1) ?: today.plusDays(1),
                error = state.errorFor(
                    StayError.CheckOutMissing::class.java,
                    StayError.CheckOutNotAfterCheckIn::class.java,
                    StayError.StayTooLong::class.java,
                ),
                onPick = { onIntent(BookingIntent.CheckOutSelected(it)) },
            )

            RoomStepper(
                rooms = state.request.rooms,
                error = state.errorFor(
                    StayError.TooManyRooms::class.java,
                    StayError.RoomsBelowOne::class.java,
                ),
                onAdd = { onIntent(BookingIntent.RoomAdded) },
                onRemove = { onIntent(BookingIntent.RoomRemoved) },
            )

            state.quote?.let { QuoteSummary(it) }

            state.submitError?.let { error ->
                Text(
                    text = error.asUserMessage(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Button(
                onClick = { onIntent(BookingIntent.Confirm) },
                enabled = !state.isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(stringResource(R.string.booking_confirm))
                }
            }
        }
    }

    state.priceChange?.let { change ->
        PriceChangeDialog(
            change = change,
            onAccept = { onIntent(BookingIntent.AcceptNewPrice) },
            onDismiss = { onIntent(BookingIntent.DismissPriceChange) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(
    label: String,
    date: LocalDate?,
    earliest: LocalDate,
    error: StayError?,
    onPick: (LocalDate) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }

    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        OutlinedButton(
            onClick = { showPicker = true },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        ) {
            Icon(Icons.Filled.DateRange, contentDescription = null)
            Text(
                text = date?.format(DATE_FORMAT) ?: stringResource(R.string.booking_pick_date),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        if (error != null) {
            Text(
                text = error.message(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }

    if (showPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date?.toEpochMillis(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    utcTimeMillis >= earliest.toEpochMillis()
            },
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { onPick(it.toLocalDate()) }
                        showPicker = false
                    },
                ) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun RoomStepper(
    rooms: Int,
    error: StayError?,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
) {
    Column {
        Text(stringResource(R.string.booking_rooms), style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            FilledTonalIconButton(onClick = onRemove, enabled = rooms > 1) {
                Icon(Icons.Filled.Remove, stringResource(R.string.booking_remove_room))
            }
            Text(rooms.toString(), style = MaterialTheme.typography.titleMedium)
            FilledTonalIconButton(onClick = onAdd) {
                Icon(Icons.Filled.Add, stringResource(R.string.booking_add_room))
            }
        }
        if (error != null) {
            Text(
                text = error.message(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun QuoteSummary(quote: BookingQuote) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.booking_summary), style = MaterialTheme.typography.titleMedium)

            // The rate line states the multiplication rather than leaving the guest to
            // work out where the base came from.
            QuoteLine(
                label = stringResource(
                    R.string.booking_rate_breakdown,
                    quote.nightlyRate.format(),
                    quote.nights,
                    quote.rooms,
                ),
                value = quote.baseAmount.format(),
            )
            QuoteLine(
                label = stringResource(
                    R.string.booking_vat,
                    quote.vatRate.stripTrailingZeros().toPlainString(),
                ),
                value = quote.vatAmount.format(),
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            QuoteLine(
                label = stringResource(R.string.booking_total),
                value = quote.total.format(),
                emphasise = true,
            )
        }
    }
}

@Composable
private fun QuoteLine(label: String, value: String, emphasise: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = if (emphasise) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            color = if (emphasise) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = if (emphasise) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun PriceChangeDialog(
    change: PriceChange,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.price_changed_title)) },
        text = {
            Text(
                stringResource(
                    R.string.price_changed_body,
                    change.current.total.format(),
                    change.previous.total.format(),
                )
            )
        },
        confirmButton = {
            TextButton(onClick = onAccept) {
                Text(stringResource(R.string.price_changed_accept, change.current.total.format()))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.price_changed_cancel)) }
        },
    )
}

@Composable
private fun StayError.message(): String = when (this) {
    StayError.CheckInMissing -> stringResource(R.string.error_check_in_missing)
    StayError.CheckOutMissing -> stringResource(R.string.error_check_out_missing)
    StayError.CheckInInPast -> stringResource(R.string.error_check_in_past)
    StayError.CheckOutNotAfterCheckIn -> stringResource(R.string.error_check_out_order)
    is StayError.StayTooLong -> stringResource(R.string.error_stay_too_long, maxNights)
    is StayError.TooManyRooms -> stringResource(R.string.error_rooms_too_many, maxRooms)
    StayError.RoomsBelowOne -> stringResource(R.string.error_rooms_below_one)
}

// The Material date picker deals in UTC midnight millis; the app deals in LocalDate.
// Converting through UTC on both sides keeps the day the user tapped the day we store.
private fun LocalDate.toEpochMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
