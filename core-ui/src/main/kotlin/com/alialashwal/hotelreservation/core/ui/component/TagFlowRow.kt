package com.alialashwal.hotelreservation.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * Labels that wrap onto as many lines as they need.
 *
 * The reason this exists: amenity names vary wildly in length. "Bar" and "Airport
 * shuttle service (surcharge)" come back in the same list, and any layout that assumes a
 * fixed number of items per row squeezes the long ones into a narrow column where the
 * text wraps character by character. [FlowRow] measures each label and breaks the line
 * when the next one does not fit, so every label gets exactly the width it needs.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagFlowRow(
    tags: List<String>,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tags.forEach { tag -> Tag(tag) }
    }
}

@Composable
private fun Tag(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        // A plain styled Text rather than an AssistChip: a chip implies something
        // happens when you press it, and nothing does.
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}
