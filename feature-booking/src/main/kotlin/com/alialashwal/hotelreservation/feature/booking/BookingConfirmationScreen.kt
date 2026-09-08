package com.alialashwal.hotelreservation.feature.booking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.alialashwal.hotelreservation.core.ui.format
import com.alialashwal.hotelreservation.domain.repository.BookingRepository
import com.alialashwal.hotelreservation.model.Booking
import com.alialashwal.hotelreservation.model.BookingReference
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject

/**
 * Reads the booking back out of the database by its reference rather than being handed
 * the object through navigation.
 *
 * That is what makes the success screen survive process death: the route carries only a
 * short string, and the receipt is rebuilt from storage. Passing the booking itself
 * would mean serialising it into the back stack and losing it whenever the system
 * decided to reclaim the process.
 */
@HiltViewModel
class BookingConfirmationViewModel @Inject constructor(
    bookings: BookingRepository,
    savedState: SavedStateHandle,
) : ViewModel() {

    private val reference = BookingReference(
        checkNotNull(savedState.get<String>(ARG_REFERENCE)) {
            "BookingConfirmationRoute must carry a reference"
        }
    )

    val state: StateFlow<ConfirmationState> = bookings.observeBooking(reference)
        .map { ConfirmationState(booking = it, isLoading = false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConfirmationState())

    internal companion object {
        const val ARG_REFERENCE = "reference"
    }
}

data class ConfirmationState(
    val booking: Booking? = null,
    val isLoading: Boolean = true,
)

@Composable
fun BookingConfirmationRoute(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BookingConfirmationViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BookingConfirmationScreen(state = state, onDone = onDone, modifier = modifier)
}

@Composable
internal fun BookingConfirmationScreen(
    state: ConfirmationState,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val booking = state.booking
        if (booking == null) {
            if (!state.isLoading) {
                Text(stringResource(R.string.confirmation_missing), textAlign = TextAlign.Center)
            }
            return@Column
        }

        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.confirmation_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 16.dp),
        )

        Card(modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(stringResource(R.string.confirmation_reference), style = MaterialTheme.typography.labelMedium)
                Text(booking.reference.value, style = MaterialTheme.typography.headlineSmall)

                Text(booking.hotelName, style = MaterialTheme.typography.titleMedium)
                Text(booking.hotelCity, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = stringResource(
                        R.string.confirmation_dates,
                        booking.checkIn.format(DATE_FORMAT),
                        booking.checkOut.format(DATE_FORMAT),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )

                // The stored breakdown, not a recomputation. What the guest agreed to.
                ReceiptLine(stringResource(R.string.booking_base), booking.quote.baseAmount.format())
                ReceiptLine(
                    stringResource(
                        R.string.booking_vat,
                        booking.quote.vatRate.stripTrailingZeros().toPlainString(),
                    ),
                    booking.quote.vatAmount.format(),
                )
                ReceiptLine(
                    label = stringResource(R.string.booking_total),
                    value = booking.quote.total.format(),
                    emphasise = true,
                )
            }
        }

        Button(onClick = onDone, modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
            Text(stringResource(R.string.confirmation_done))
        }
    }
}

@Composable
private fun ReceiptLine(label: String, value: String, emphasise: Boolean = false) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = if (emphasise) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = value,
            style = if (emphasise) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
        )
    }
}

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
