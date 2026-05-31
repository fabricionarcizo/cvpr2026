package com.fabricionarcizo.edgevisionai.ui.main

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.fabricionarcizo.edgevisionai.R
import com.fabricionarcizo.edgevisionai.feature.detector.presentation.ui.viewmodel.DetectorViewModel
import com.fabricionarcizo.edgevisionai.ui.theme.EdgeVisionAITheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-activity entry point. Owns:
 *  - the camera permission flow (rationale + system prompt)
 *  - the sustained-performance / keep-screen-on window flags that close part
 *    of the app-vs-CLI tok/s gap on Snapdragon Elite
 *  - the Compose host that draws [MainScreen]
 *
 * All business logic lives in [DetectorViewModel] / its injected use cases.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: DetectorViewModel by viewModels()

    private var hasCameraPermission by mutableStateOf(false)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Keep the screen on so doze / dim doesn't downclock cores mid-inference.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Sustained-performance mode (API 24+) pins clocks while the Activity is
        // foreground — closes part of the ~3x app-vs-CLI tok/s gap on Snapdragon
        // Elite by stopping Android from scaling down the LM cores.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                window.setSustainedPerformanceMode(true)
            } catch (t: Throwable) {
                Log.w(TAG, "setSustainedPerformanceMode unsupported", t)
            }
        }

        hasCameraPermission = checkCameraPermission()

        setContent {
            EdgeVisionAITheme {
                MainScreen(
                    viewModel = viewModel,
                    hasCameraPermission = hasCameraPermission,
                )
            }
        }

        if (!hasCameraPermission) requestCameraPermission()
    }

    private fun requestCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA,
            ) == PackageManager.PERMISSION_DENIED &&
                shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                showPermissionRationaleDialog()
            }

            else -> requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun showPermissionRationaleDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.camera_permission_title)
            .setMessage(R.string.camera_permission_rationale_message)
            .setPositiveButton(R.string.button_ok) { _, _ ->
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
            .setNegativeButton(R.string.button_cancel) { dialog, _ ->
                dialog.dismiss()
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun checkCameraPermission(): Boolean = ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.CAMERA,
    ) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val TAG = "MainActivity"
    }
}
