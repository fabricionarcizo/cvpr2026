/*
 * MIT License
 *
 * Copyright (c) 2026 Elizabete Munzlinger and Fabricio Batista Narcizo
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
 * associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT
 * NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.fabricionarcizo.edgevisionai.ui.main

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import com.fabricionarcizo.edgevisionai.R
import com.fabricionarcizo.edgevisionai.feature.detector.presentation.ui.viewmodel.DetectorViewModel
import com.fabricionarcizo.edgevisionai.ui.theme.EdgeVisionAITheme

/**
 * An activity class with several methods to manage the main activity of EdgeVision AI application.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    /**
     * The view model that manages the state of the detector.
     */
    private val detectorViewModel: DetectorViewModel by viewModels()

    /**
     * A boolean value indicating whether the camera permission is granted.
     */
    private var hasCameraPermission by mutableStateOf(false)

    /**
     * This object launches a new activity and receives back some result data.
     */
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            hasCameraPermission = isGranted
            if (!isGranted) finish()
        }

    /**
     * Called when the activity is starting. This is where most initialization should go: calling
     * `setContentView(int)` to inflate the activity's UI, using `findViewById()` to
     * programmatically interact with widgets in the UI, calling
     * `managedQuery(android.net.Uri, String[], String, String[], String)` to retrieve cursors for
     * data being displayed, etc.
     *
     * You can call `finish()` from within this function, in which case `onDestroy()` will be
     * immediately called after `onCreate()` without any of the rest of the activity lifecycle
     * (`onStart()`, `onResume()`, onPause()`, etc) executing.
     *
     * <em>Derived classes must call through to the super class's implementation of this method. If
     * they do not, an exception will be thrown.</em>
     *
     * @param savedInstanceState If the activity is being re-initialized after previously being shut
     * down then this Bundle contains the data it most recently supplied in `onSaveInstanceState()`.
     * <b><i>Note: Otherwise it is null.</i></b>
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        hasCameraPermission = checkPermission()

        setContent {
            EdgeVisionAITheme {
                MainScreen(
                    detectorViewModel = detectorViewModel,
                    hasCameraPermission = hasCameraPermission,
                )
            }
        }

        if (!hasCameraPermission) requestCameraPermission()
    }

    /**
     * Requests the camera permission. If the user has previously denied the request, a rationale
     * dialog is shown explaining why the app needs the permission.
     */
    private fun requestCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_DENIED &&
                shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                showPermissionRationaleDialog()
            }
            else -> requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    /**
     * Displays an AlertDialog explaining why the app needs camera permission, with options to
     * grant or deny the request.
     */
    private fun showPermissionRationaleDialog() {
        AlertDialog
            .Builder(this)
            .setTitle(R.string.camera_permission_title)
            .setMessage(R.string.camera_permission_rationale_message)
            .setPositiveButton(R.string.button_ok) { _, _ ->
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }.setNegativeButton(R.string.button_cancel) { dialog, _ ->
                dialog.dismiss()
                finish()
            }.setCancelable(false)
            .show()
    }

    /**
     * This method checks if the user allows the application uses the camera to take photos for this
     * application.
     *
     * @return A boolean value with the user permission agreement.
     */
    private fun checkPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
}
