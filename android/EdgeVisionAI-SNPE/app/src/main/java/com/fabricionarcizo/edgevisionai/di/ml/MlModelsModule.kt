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
package com.fabricionarcizo.edgevisionai.di.ml

import android.app.Application
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.fabricionarcizo.edgevisionai.ml.api.InferenceEngine
import com.fabricionarcizo.edgevisionai.ml.api.TensorOutputs
import com.fabricionarcizo.edgevisionai.ml.config.ModelConfig
import com.fabricionarcizo.edgevisionai.ml.config.ModelRegistry
import com.fabricionarcizo.edgevisionai.ml.engine.SnpeModel
import com.fabricionarcizo.edgevisionai.ml.models.labels.COCO80_LABELS
import javax.inject.Singleton

/**
 * Hilt module that provides dependencies for the application, including model configuration,
 * class names, and the DLCModel instance.
 */
@Module
@InstallIn(SingletonComponent::class)
object MlModelsModule {
    /**
     * Provides the model configuration for object detection
     * [com.fabricionarcizo.edgevisionai.ml.engine.SnpeModel].
     *
     * @return An instance of [com.fabricionarcizo.edgevisionai.ml.config.ModelConfig] for object detection.
     */
    @Provides
    @Singleton
    @ObjectDetection
    fun provideObjectModelConfig(): ModelConfig = ModelRegistry.objectDetectorConfig

    /**
     * Provides the class names for object detection. Hilt will provide this list every time the
     * system requires object detection class names.
     *
     * @return A list of class names used in object detection.
     */
    @Provides
    @Singleton
    @ObjectDetection
    fun provideObjectClassNames(): List<String> = COCO80_LABELS

    /**
     * Provides the [SnpeModel] instance for object detection. Hilt will provide this instance
     * every time the system requires a object detection [SnpeModel].
     *
     * @param application The application context provided by Hilt.
     * @param config The object detection model configuration.
     *
     * @return An instance of [SnpeModel] for object detection.
     */
    @Provides
    @Singleton
    @ObjectDetection
    fun provideObjectSnpeModel(
        application: Application,
        @ObjectDetection config: ModelConfig,
    ) = SnpeModel(
        application = application,
        config = config,
        isUnsignedPD = true,
    )

    /**
     * Provides the inference engine for object detection. Hilt will provide this instance
     * every time the system requires a object detection [InferenceEngine].
     *
     * @param model The object detection [SnpeModel].
     *
     * @return An instance of [InferenceEngine] for object detection.
     */
    @Provides
    @Singleton
    @ObjectDetection
    fun provideObjectInferenceEngine(
        @ObjectDetection model: SnpeModel,
    ): InferenceEngine<TensorOutputs> = model
}
