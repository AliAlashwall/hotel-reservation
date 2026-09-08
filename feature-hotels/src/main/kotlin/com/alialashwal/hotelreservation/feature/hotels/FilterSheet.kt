package com.alialashwal.hotelreservation.feature.hotels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alialashwal.hotelreservation.model.PriceRange
import kotlin.math.roundToInt

/**
 * Country, city, minimum guest rating and price band.
 *
 * Every control writes straight through to the ViewModel rather than holding a local
 * draft, so closing the sheet and reopening it shows what is actually applied, and a
 * process death mid-edit loses nothing. The price slider is the one exception: it holds
 * the drag locally and commits on release, because writing on every pixel of drag would
 * key a new result set per frame.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun FilterSheet(
    state: HotelListState,
    onIntent: (HotelListIntent) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.hotels_filters), style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = { onIntent(HotelListIntent.ClearFilters) }) {
                    Text(stringResource(R.string.hotels_filters_clear))
                }
            }

            CountryPicker(state = state, onIntent = onIntent)
            CityPicker(state = state, onIntent = onIntent)
            RatingPicker(state = state, onIntent = onIntent)
            PricePicker(state = state, onIntent = onIntent)

            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.hotels_filters_done))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CountryPicker(state: HotelListState, onIntent: (HotelListIntent) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selected = state.availableCountries.firstOrNull { it.code == state.filters.countryCode }

    Column {
        Text(stringResource(R.string.filter_country), style = MaterialTheme.typography.labelLarge)
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier.padding(top = 8.dp),
        ) {
            OutlinedTextField(
                value = selected?.name ?: state.filters.countryCode,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(state.availableCountries, key = { it.code }) { country ->
                        DropdownMenuItem(
                            text = { Text(country.name) },
                            onClick = {
                                onIntent(HotelListIntent.CountrySelected(country.code))
                                expanded = false
                            },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CityPicker(state: HotelListState, onIntent: (HotelListIntent) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        Text(stringResource(R.string.filter_city), style = MaterialTheme.typography.labelLarge)
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier.padding(top = 8.dp),
        ) {
            OutlinedTextField(
                value = state.filters.city ?: stringResource(R.string.filter_city_any),
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.filter_city_any)) },
                    onClick = {
                        onIntent(HotelListIntent.CitySelected(null))
                        expanded = false
                    },
                )
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(state.availableCities, key = { it }) { city ->
                        DropdownMenuItem(
                            text = { Text(city) },
                            onClick = {
                                onIntent(HotelListIntent.CitySelected(city))
                                expanded = false
                            },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RatingPicker(state: HotelListState, onIntent: (HotelListIntent) -> Unit) {
    Column {
        Text(stringResource(R.string.filter_min_rating), style = MaterialTheme.typography.labelLarge)
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = state.filters.minReviewScore == null,
                onClick = { onIntent(HotelListIntent.MinScoreSelected(null)) },
                label = { Text(stringResource(R.string.filter_rating_any)) },
            )
            RATING_OPTIONS.forEach { score ->
                FilterChip(
                    selected = state.filters.minReviewScore == score,
                    onClick = { onIntent(HotelListIntent.MinScoreSelected(score)) },
                    label = { Text(stringResource(R.string.filter_rating_value, score.toString())) },
                )
            }
        }
    }
}

@Composable
private fun PricePicker(state: HotelListState, onIntent: (HotelListIntent) -> Unit) {
    val applied = state.filters.priceRange
    var draft by remember(applied) {
        mutableStateOf(
            (applied.min ?: PRICE_FLOOR).toFloat()..(applied.max ?: PRICE_CEILING).toFloat()
        )
    }

    Column {
        Text(stringResource(R.string.filter_price), style = MaterialTheme.typography.labelLarge)
        Text(
            text = if (applied.isUnbounded) {
                stringResource(R.string.filter_price_any)
            } else {
                stringResource(
                    R.string.filter_price_range,
                    draft.start.roundToInt(),
                    draft.endInclusive.roundToInt(),
                )
            },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp),
        )
        RangeSlider(
            value = draft,
            onValueChange = { draft = it },
            // Committed on release only. Writing per frame would key a new cached
            // result set for every pixel the thumb travels.
            onValueChangeFinished = {
                val min = draft.start.roundToInt().takeIf { it > PRICE_FLOOR }
                val max = draft.endInclusive.roundToInt().takeIf { it < PRICE_CEILING }
                onIntent(HotelListIntent.PriceRangeSelected(PriceRange(min, max)))
            },
            valueRange = PRICE_FLOOR.toFloat()..PRICE_CEILING.toFloat(),
        )
        Text(
            text = stringResource(R.string.filter_price_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val RATING_OPTIONS = listOf(7.0, 8.0, 9.0)
private const val PRICE_FLOOR = 0
private const val PRICE_CEILING = 1000
