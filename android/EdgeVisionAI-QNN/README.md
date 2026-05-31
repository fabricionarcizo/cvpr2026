# EdgeVisionAI — QNN

Android application that runs real-time **LibreYOLOXs INT8** object detection
on-device using the **Qualcomm Neural Network (QNN) SDK** via a custom C++
JNI bridge that loads the QNN runtime libraries dynamically at startup.

This project is part of the CVPR 2026 tutorial
[*Edge AI in Action: Mastering On-Device Inference*](../../README.md).

---

## Overview

Unlike the [SNPE](../EdgeVisionAI-SNPE/README.md) and
[PSNPE](../EdgeVisionAI-PSNPE/README.md) apps — which rely on Qualcomm's Java
AAR wrappers — this app builds a thin C++ JNI bridge (`qnn_inference_jni.cpp`)
that loads `libQnnHtp.so` and `libQnnSystem.so` at runtime via `dlopen()`. This
approach requires no QNN import library at link time; only the QAIRT SDK headers
are needed during compilation.

The benefit is complete control over the QNN context lifecycle and backend
selection, which is useful for research and profiling.

| Feature | Details |
|---|---|
| Model | LibreYOLOXs INT8 (QNN binary `.bin` format) |
| Runtime | Qualcomm QNN — loaded via `dlopen()` at runtime |
| JNI bridge | `libqnn_inference_jni.so` (CMake / NDK build) |
| QAIRT SDK version | 2.46.0.260424 (headers bundled in `include/QNN/`) |
| UI framework | Jetpack Compose + Material 3 |
| DI | Hilt |
| Camera | CameraX (Camera2 back-end) |
| Architecture | ARM64 (`arm64-v8a`) only |
| Min SDK | 28 (Android 9) |
| Target SDK | 30 (Android 11) |

---

## Project structure

```text
EdgeVisionAI-QNN/
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/                         ← place LibreYOLOXs_int8.bin here
│       ├── cpp/
│       │   ├── CMakeLists.txt              JNI bridge build script
│       │   ├── qnn_inference_jni.cpp       JNI bridge implementation
│       │   └── include/
│       │       └── QNN/                    QAIRT SDK headers (compile-time only)
│       ├── jniLibs/arm64-v8a/
│       │   ├── libQnnHtp.so                ← copy from QAIRT SDK (see below)
│       │   ├── libQnnHtpPrepare.so
│       │   ├── libQnnHtpV68Stub.so         ← required for QCS6490 / RB3 Gen 2
│       │   ├── libQnnHtpV69Stub.so
│       │   ├── libQnnHtpV73Stub.so
│       │   ├── libQnnHtpV75Stub.so
│       │   ├── libQnnHtpV79Stub.so
│       │   └── libQnnSystem.so
│       ├── java/com/fabricionarcizo/edgevisionai/
│       │   ├── app/
│       │   ├── di/
│       │   ├── ml/                         QnnModel + shared pipeline
│       │   └── ui/
│       └── res/
├── build.gradle.kts
├── gradle.properties
├── gradlew / gradlew.bat
└── settings.gradle.kts
```

---

## Prerequisites

### 1. QNN runtime libraries

The QNN `.so` libraries are part of the Qualcomm AI Runtime (QAIRT) SDK and
are **not redistributed** in this repository.

1. Download the QAIRT SDK (version ≥ 2.46.0) from
   [Qualcomm AI Hub](https://aihub.qualcomm.com) or the
   [Qualcomm Developer Network](https://developer.qualcomm.com).
2. Copy the following files from `<QAIRT_SDK_ROOT>/lib/aarch64-android/`
   into `app/src/main/jniLibs/arm64-v8a/`:

| File | Purpose |
|---|---|
| `libQnnHtp.so` | QNN HTP backend — main inference engine |
| `libQnnHtpPrepare.so` | HTP preparation / offline cache helper |
| `libQnnHtpV68Stub.so` | HTP V68 stub — required for QCS6490 / RB3 Gen 2 |
| `libQnnHtpV69Stub.so` | HTP V69 stub — Snapdragon 8 Gen 1 (optional) |
| `libQnnHtpV73Stub.so` | HTP V73 stub — SM8350 (optional) |
| `libQnnHtpV75Stub.so` | HTP V75 stub — SM8550 (optional) |
| `libQnnHtpV79Stub.so` | HTP V79 stub — SM8750 Snapdragon 8 Elite (optional) |
| `libQnnSystem.so` | QNN system interface |

A `README.txt` inside `app/src/main/jniLibs/arm64-v8a/` lists the minimum
required files for the target device.

> These files are subject to the Qualcomm AI Stack license agreement. Do not
> redistribute without compliance with that license.

### 2. Model file

The LibreYOLOXs INT8 QNN binary model can be generated with the
[Docker toolchain](../../docker/README.md). Place the model file at:

```text
app/src/main/assets/LibreYOLOXs_int8.bin
```

### 3. Signing keystore

Add the following to `local.properties` in the project root:

```properties
store.file=../Certificate.jks
store.password=your_store_password
key.alias=your_key_alias
key.password=your_key_password
```

> `local.properties` is listed in `.gitignore` and will not be committed.

---

## Build

Run all commands from inside `android/EdgeVisionAI-QNN/`.

The CMake build is triggered automatically by Gradle via the
`externalNativeBuild` block in `build.gradle.kts`.

### Debug build

```bash
./gradlew assembleDebug
```

### Release build

```bash
./gradlew assembleRelease
```

The signed APK is written to:

```text
app/build/outputs/apk/release/app-release.apk
```

### Install on a connected device

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

### Code quality

```bash
./gradlew check          # runs ktlint + detekt
./gradlew fix            # auto-formats with ktlint
```

---

## Using the app

1. Launch **EdgeVision AI** on the device.
2. Grant the camera permission when prompted.
3. Point the camera at any scene — COCO-80 bounding boxes are drawn in real time.
4. Use the **confidence threshold slider** to filter low-confidence detections.
5. The **FPS indicator** shows the current inference frame rate.

---

## How it works

### JNI bridge

The C++ bridge (`qnn_inference_jni.cpp`) performs the following steps at
initialisation:

1. `dlopen("libQnnHtp.so")` and `dlopen("libQnnSystem.so")` from the app's
   native library directory.
2. Resolves QNN function pointers (`QnnInterface_getProviders`, etc.) using
   `dlsym`.
3. Creates a QNN backend, device, and context.
4. Loads the model from the `.bin` file in `assets/`.
5. Composes the compute graph and allocates input / output tensors.

At inference time, the bridge:
1. Accepts a pre-processed float32 tensor (1 × 3 × 640 × 640).
2. Copies it into the QNN input buffer.
3. Executes the graph.
4. Returns the raw `bboxes` and `scores` output tensors to the Kotlin layer.

The QAIRT SDK headers in `include/QNN/` are used at compile time for correct
struct layout definitions. No QNN import library (`.a` or `.so`) is linked.

### Detection pipeline

```
CameraX ImageAnalysis
        │
        ▼
BitmapRgbFloatPreprocessor   ← resize to 640×640, normalise to [0,1]
        │
        ▼
QnnModel (JNI bridge)        ← LibreYOLOXs_int8.bin loaded from assets
        │  outputs: bboxes [8400,4] + scores [8400,81]
        ▼
ObjectPostProcessor          ← decode boxes, confidence filtering, NMS
        │
        ▼
DetectionPipeline / ViewModel
        │
        ▼
ObjectOverlayRenderer        ← Compose Canvas overlay on top of preview
```

`jniLibs.useLegacyPackaging = true` in `build.gradle.kts` extracts the HTP
skel libraries alongside the APK at install time, which is required for the
Hexagon secure DSP kernel to load them.

---

## Configuration

`ModelRegistry.kt` defines the model parameters:

| Parameter | Value |
|---|---|
| File | `LibreYOLOXs_int8.bin` |
| Input tensor | `images` — shape `[1, 3, 640, 640]` (NCHW) |
| Output tensors | `bboxes` (8400 × 4), `scores` (8400 × 81) |

`DetectionPipelineConfig` holds runtime-adjustable parameters:

| Parameter | Default |
|---|---|
| Object confidence threshold | 0.5 |

---

## License

[MIT License](../../LICENSE) — Copyright (c) 2026 Fabricio Batista Narcizo
