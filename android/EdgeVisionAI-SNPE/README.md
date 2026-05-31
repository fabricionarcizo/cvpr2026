# EdgeVisionAI — SNPE

Android application that runs real-time **LibreYOLOXs INT8** object detection
on-device using the **Qualcomm Snapdragon Neural Processing Engine (SNPE)** Java
AAR runtime.

This project is part of the CVPR 2026 tutorial
[*Edge AI in Action: Mastering On-Device Inference*](../../README.md).

---

## Overview

The app opens the rear camera, runs every captured frame through a
640 × 640 preprocessing pipeline, feeds it to LibreYOLOXs via SNPE, and
renders COCO-80 bounding boxes in real time.

| Feature | Details |
|---|---|
| Model | LibreYOLOXs INT8 (DLC format) |
| Runtime | Qualcomm SNPE — `snpe-release.aar` |
| UI framework | Jetpack Compose + Material 3 |
| DI | Hilt |
| Camera | CameraX (Camera2 back-end) |
| Architecture | ARM64 (`arm64-v8a`) only |
| Min SDK | 28 (Android 9) |
| Target SDK | 30 (Android 11) |

---

## Project structure

```text
EdgeVisionAI-SNPE/
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/                     ← place LibreYOLOXs_int8.dlc here
│       ├── libs/
│       │   └── snpe-release.aar        ← SNPE Java runtime library
│       ├── java/com/fabricionarcizo/edgevisionai/
│       │   ├── app/                    EdgeVisionAIApplication (Hilt)
│       │   ├── di/                     Hilt modules (detector, ML)
│       │   ├── ml/                     Inference engine, pipeline, pre/post-processors
│       │   └── ui/                     Compose screens, ViewModel, overlay renderer
│       └── res/
├── build.gradle.kts
├── gradle.properties
├── gradlew / gradlew.bat
└── settings.gradle.kts
```

---

## Prerequisites

### 1. SNPE AAR library

The `snpe-release.aar` is part of the Qualcomm AI Runtime (QAIRT) SDK and is
**not redistributed** in this repository.

1. Download the QAIRT SDK from [Qualcomm AI Hub](https://aihub.qualcomm.com) or
   the [Qualcomm Developer Network](https://developer.qualcomm.com).
2. Locate `snpe-release.aar` inside the SDK package.
3. Copy it to:

```text
app/src/main/libs/snpe-release.aar
```

### 2. Model file

The LibreYOLOXs INT8 DLC model can be generated with the Docker toolchain in
[`docker/`](../../docker/README.md) or downloaded from the QAI Hub model zoo.

Place the model file at:

```text
app/src/main/assets/LibreYOLOXs_int8.dlc
```

### 3. Signing keystore

A release signing configuration is required to sideload the APK onto a
Qualcomm device with SNPE DSP access.

Create (or reuse) a keystore and add the following properties to
`local.properties` in the project root:

```properties
store.file=../Certificate.jks
store.password=your_store_password
key.alias=your_key_alias
key.password=your_key_password
```

> `local.properties` is listed in `.gitignore` and will not be committed.

---

## Build

Run all commands from inside `android/EdgeVisionAI-SNPE/`.

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
3. Point the camera at any scene — bounding boxes for all 80 COCO classes are
   drawn in real time.
4. Use the **confidence threshold slider** at the bottom of the screen to filter
   low-confidence detections.
5. The **FPS indicator** in the top-right corner shows the current inference
   frame rate.

---

## How it works

```
CameraX ImageAnalysis
        │
        ▼
BitmapRgbFloatPreprocessor   ← resize to 640×640, normalise to [0,1]
        │
        ▼
SnpeModel (snpe-release.aar) ← LibreYOLOXs_int8.dlc loaded from assets
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

The SNPE network is built once during `initialize()` and kept alive for the
lifetime of the detector. Initialization is retried up to five times with
exponential back-off to handle intermittent DSP availability.

---

## Configuration

`ModelRegistry.kt` defines the model parameters:

| Parameter | Value |
|---|---|
| File | `LibreYOLOXs_int8.dlc` |
| Input tensor | `images` — shape `[1, 3, 640, 640]` (NCHW) |
| Output tensors | `bboxes` (8400 × 4), `scores` (8400 × 81) |

`DetectionPipelineConfig` holds runtime-adjustable parameters:

| Parameter | Default |
|---|---|
| Object confidence threshold | 0.5 |

---

## License

[MIT License](../../LICENSE) — Copyright (c) 2026 Fabricio Batista Narcizo
