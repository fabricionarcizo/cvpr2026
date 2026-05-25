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
package com.fabricionarcizo.edgevisionai.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * My personalized base class for maintaining global application state. You can provide your own
 * implementation by creating a subclass and specifying the fully-qualified name of this subclass as
 * the `android:name` attribute in your `AndroidManifest.xml's` <code>&lt;application&gt;</code>
 * tag. The Application class, or your subclass of the Application class, is instantiated before any
 * other class when the process for your `application/package` is created.
 *
 * <strong>Note: </strong>There is normally no need to subclass Application. In most situations,
 * static singletons can provide the same functionality in a more modular way. If your singleton
 * needs a global context (for example to register broadcast receivers), include
 * `Context.getApplicationContext()` as a `android.content.Context` argument when invoking your
 * singleton's `getInstance()` method.
 *
 * Enables Hilt dependency injection.
 */
@HiltAndroidApp
class EdgeVisionAIApplication : Application()
