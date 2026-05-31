plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlinAndroidKsp)
    alias(libs.plugins.hiltAndroid)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
}

android {
    namespace = "com.fabricionarcizo.edgevisionai"

    compileSdk = 35

    defaultConfig {
        applicationId = "com.fabricionarcizo.edgevisionai.personsense"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("Boolean", "ENABLE_PERFORMANCE_LOGGING", "false")
        buildConfigField("String", "APP_TAG", "\"PersonSense\"")

        ndk {
            abiFilters.add("arm64-v8a") // Compile the APK only for ARM64 devices.
        }

        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++17", "-O3")
                arguments += listOf("-DANDROID_STL=c++_shared")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    // libOpenCL.so / Hexagon HTP libs ship from the device's /vendor or as packaged
    // extracted libs alongside the APK. Keep parity with personsense-cvpr2026 so
    // the bundled :llm-lib (llama.cpp + OpenCL + HTP) loads cleanly.
    packaging {
        jniLibs {
            excludes += "**/libOpenCL.so"
            useLegacyPackaging = true
            pickFirsts += "**/libllama.so"
            pickFirsts += "**/libllama-common.so"
            pickFirsts += "**/libmtmd.so"
            pickFirsts += "**/libggml.so"
            pickFirsts += "**/libggml-base.so"
            pickFirsts += "**/libggml-cpu.so"
            pickFirsts += "**/libggml-hexagon.so"
            pickFirsts += "**/libggml-htp-*.so"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
        aidl = true
    }

    // Legacy Compose compiler extension — required because we're on Kotlin 1.9.22
    // (the new `kotlin("plugin.compose")` requires Kotlin 2.0+).
    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
    }

    lint {
        disable += "ExpiredTargetSdkVersion"
    }
}

ktlint {
    android.set(true)
    outputToConsole.set(true)
    ignoreFailures.set(true)

    filter {
        exclude("**/build/**")
        exclude("**/generated/**")
    }
}

detekt {
    toolVersion = libs.versions.detekt.get()

    buildUponDefaultConfig = true
    allRules = false
    parallel = false
    ignoreFailures = true
}

tasks.register("fix") {
    group = "verification"
    description = "Auto-fix formatting issues (ktlint)."
    dependsOn("ktlintFormat", "ktlintKotlinScriptFormat")
}

dependencies {
    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // CameraX
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Core
    implementation(libs.androidx.core.ktx)

    // Test
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
