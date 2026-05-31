package com.fabricionarcizo.edgevisionai.feature.detector.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fabricionarcizo.edgevisionai.feature.detector.domain.model.Backend

/**
 * Three-way picker for CPU / GPU / HTP. Disabled chips are still tappable but
 * the selected one is highlighted; mirrors the original TextView "tint the
 * selected one white" UX.
 */
@Composable
fun BackendPicker(
    selected: Backend,
    onSelect: (Backend) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Backend.values().forEach { backend ->
            FilterChip(
                selected = selected == backend,
                onClick = { onSelect(backend) },
                label = { Text(backend.name) },
                colors = FilterChipDefaults.filterChipColors(),
            )
        }
    }
}
