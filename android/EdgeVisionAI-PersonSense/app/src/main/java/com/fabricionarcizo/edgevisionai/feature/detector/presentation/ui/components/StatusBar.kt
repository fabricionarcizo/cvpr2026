package com.fabricionarcizo.edgevisionai.feature.detector.presentation.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Translucent top-of-screen status pill showing the headline status text and
 * an optional perf subline (e.g. "248 tok / 5.2s gen").
 */
@Composable
fun StatusBar(
    statusText: String,
    subText: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.medium,
        shadowElevation = 4.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(text = statusText, style = MaterialTheme.typography.bodyLarge)
            if (subText.isNotEmpty()) {
                Text(text = subText, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
