package com.fabricionarcizo.edgevisionai.feature.detector.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fabricionarcizo.edgevisionai.R

/**
 * Bottom action bar: a flip-camera button and the primary capture button.
 */
@Composable
fun CaptureControls(
    captureEnabled: Boolean,
    captureLabel: String,
    onCapture: () -> Unit,
    onFlip: () -> Unit,
    flipEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        OutlinedButton(
            onClick = onFlip,
            enabled = flipEnabled,
        ) {
            Text(stringResource(R.string.button_flip_camera))
        }
        Button(
            onClick = onCapture,
            enabled = captureEnabled,
        ) {
            Text(captureLabel)
        }
    }
}
