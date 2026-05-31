package com.fabricionarcizo.edgevisionai.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Hilt root for the PersonSense variant of EdgeVision AI.
 *
 * The application class itself stays empty — all wiring lives in the
 * `com.fabricionarcizo.edgevisionai.di.*` Hilt modules.
 */
@HiltAndroidApp
class EdgeVisionAIApplication : Application()
