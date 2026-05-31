# EdgeVisionAI — PSNPE

Android application that runs real-time **LibreYOLOXs INT8** object detection
on-device using the **Qualcomm Platform Snapdragon Neural Processing Engine
(PSNPE)** runtime, which adds access to the device DSP and unsigned protected
domain execution on top of standard SNPE.

This project is part of the CVPR 2026 tutorial
[*Edge AI in Action: Mastering On-Device Inference*](../../README.md).

---

## Overview

PSNPE extends SNPE by bundling the Platform SDK (`psnpe-release.aar`) alongside
the base runtime (`snpe-release.aar`). This unlocks the Hexagon DSP hardware
runtime and the unsigned protected domain (UPD) execution path, which can
deliver lower latency and higher throughput than CPU or GPU backends on
Qualcomm Snapdragon SoCs.

The detection workflow, model, and UI are identical to the
[EdgeVisionAI-SNPE](../EdgeVisionAI-SNPE/README.md) project. Only the
inference engine layer and packaging configuration differ.

| Feature | Details |
|---|---|
| Model | LibreYOLOXs INT8 (DLC format) |
| Runtime | Qualcomm PSNPE — `psnpe-release.aar` + `snpe-release.aar` |
| UI framework | Jetpack Compose + Material 3 |
| DI | Hilt |
| Camera | CameraX (Camera2 back-end) |
| Architecture | ARM64 (`arm64-v8a`) only |
| Min SDK | 28 (Android 9) |
| Target SDK | 30 (Android 11) |

---

## Differences from EdgeVisionAI-SNPE

| Aspect | SNPE | PSNPE |
|---|---|---|
| AAR libraries | `snpe-release.aar` | `snpe-release.aar` + `psnpe-release.aar` |
| DSP runtime | Available when SNPE SDK permits | Enabled via Platform SDK UPD path |
| `jniLibs.useLegacyPackaging` | `true` | `true` |
| JNI / NDK bridge | None (pure Java AAR) | None (pure Java AAR) |
| Inference engine class | `SnpeModel` | `PsnpeModel` |
| Model file | `LibreYOLOXs_int8.dlc` | `LibreYOLOXs_int8.dlc` (same) |

---

## Project structure

```text
EdgeVisionAI-PSNPE/
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/                     ← place LibreYOLOXs_int8.dlc here
│       ├── libs/
│       │   ├── snpe-release.aar        ← SNPE base runtime
│       │   └── psnpe-release.aar       ← Platform SNPE extension
│       ├── java/com/fabricionarcizo/edgevisionai/
│       │   ├── app/
│       │   ├── di/
│       │   ├── ml/                     PsnpeModel + shared pipeline
│       │   └── ui/
│       └── res/
├── build.gradle.kts
├── gradle.properties
├── gradlew / gradlew.bat
└── settings.gradle.kts
```

---

## Prerequisites

### 1. SNPE and PSNPE AAR libraries

Both libraries are part of the Qualcomm AI Runtime (QAIRT) SDK and are **not
redistributed** in this repository.

1. Download the QAIRT SDK from [Qualcomm AI Hub](https://aihub.qualcomm.com) or
   the [Qualcomm Developer Network](https://developer.qualcomm.com).
2. Locate `snpe-release.aar` and `psnpe-release.aar` inside the SDK package.
3. Copy them to:

```text
app/src/main/libs/snpe-release.aar
app/src/main/libs/psnpe-release.aar
```

### 2. Model file

Place the LibreYOLOXs INT8 DLC model (generated with the
[Docker toolchain](../../docker/README.md) or downloaded from QAI Hub) at:

```text
app/src/main/assets/LibreYOLOXs_int8.dlc
```

### 3. Signing keystore

A release signing configuration is required to sideload the APK.

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

Run all commands from inside `android/EdgeVisionAI-PSNPE/`.

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
PsnpeModel                   ← LibreYOLOXs_int8.dlc loaded from assets
  (psnpe-release.aar)           via Platform SNPE UPD / DSP runtime
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

`jniLibs.useLegacyPackaging = true` in `build.gradle.kts` ensures that the
DSP skel libraries bundled inside the PSNPE AAR are extracted to the file system
at install time, which is required for the Hexagon runtime to load them.

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
