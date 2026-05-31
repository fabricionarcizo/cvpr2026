# EdgeVisionAI — PersonSense

Android application for real-time **on-device person detection** powered by
**Qwen3-VL 2B**, a vision-language model (VLM) running entirely on-device
through [llama.cpp](https://github.com/ggml-org/llama.cpp) and its multimodal
`mtmd` extension.

This project is part of the CVPR 2026 tutorial
[*Edge AI in Action: Mastering On-Device Inference*](../../README.md).

---

## Overview

Instead of a classical object detector, PersonSense uses a quantized
vision-language model to answer a structured prompt about each camera frame.
The model outputs JSON bounding-box coordinates, which are parsed and overlaid
on the live preview.

The CVPR 2026 operating point is:

| Parameter | Value |
|---|---|
| Model | Qwen3-VL 2B, Q8_0 weights (`Qwen3-VL-2B-Q8_0.gguf`) |
| Vision projector | Q8_0 mmproj (`mmproj-Qwen3VL-2B-Q8_0.gguf`) |
| Backend | CPU (4 threads, no GPU offload) |
| Visual token cap (`--image-max-tokens`) | 72 |
| Measured TTFT on S25 Ultra (CPU) | ~2.8 s |
| Measured mAP@.5 on S25 Ultra (CPU) | 0.48 |

This configuration was selected through the benchmark study in
[`analysis/PersonSense/`](../../analysis/PersonSense/README.md).

| Feature | Details |
|---|---|
| Runtime | llama.cpp (`libllama.so`) + mtmd (`libmtmd.so`) |
| Backends | CPU (`libggml-cpu.so`) · OpenCL GPU (`libggml-opencl.so`) · Hexagon HTP (`libggml-hexagon.so` + per-SoC stubs) |
| VLM service | Android foreground `Service` with AIDL interface (`IVlmService`) |
| UI framework | Jetpack Compose + Material 3 |
| DI | Hilt |
| Camera | CameraX (Camera2 back-end) |
| Architecture | ARM64 (`arm64-v8a`) only |
| Min SDK | 28 (Android 9) |
| Target SDK | 35 (Android 15) |

---

## Project structure

```text
EdgeVisionAI-PersonSense/
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── aidl/
│       │   └── com/jabby/vlm/service/    AIDL interface definitions
│       │       ├── IVlmService.aidl
│       │       ├── IVlmCallback.aidl
│       │       └── (related .aidl files)
│       ├── cpp/
│       │   ├── CMakeLists.txt            JNI bridge build script
│       │   ├── ai_chat.cpp               JNI bridge implementation
│       │   ├── logging.h
│       │   └── prebuilt-include/         llama.cpp / mtmd / ggml headers
│       ├── jniLibs/arm64-v8a/
│       │   ├── libllama.so               ← copy from pkg-snapdragon build
│       │   ├── libmtmd.so
│       │   ├── libggml.so
│       │   ├── libggml-base.so
│       │   ├── libggml-cpu.so
│       │   ├── libggml-hexagon.so
│       │   ├── libggml-htp-v68.so        ← HTP stub for QCM6490 / Fairphone 5
│       │   ├── libggml-htp-v79.so        ← HTP stub for Snapdragon 8 Elite / S25
│       │   ├── (other HTP stubs)
│       │   └── README.txt                ← exact file list and sources
│       ├── java/com/fabricionarcizo/edgevisionai/
│       │   ├── app/
│       │   ├── di/
│       │   ├── feature/detector/         Detection domain, app, infra layers
│       │   ├── ml/                       QwenBBoxParser
│       │   └── ui/
│       └── java/com/jabby/vlm/service/
│           ├── VlmService.kt             Foreground service running the VLM
│           └── VlmServiceConnection.kt
├── build.gradle.kts
├── gradle.properties
├── gradlew / gradlew.bat
└── settings.gradle.kts
```

---

## Prerequisites

### 1. llama.cpp runtime libraries (pkg-snapdragon build)

The `libllama.so`, `libmtmd.so`, and `libggml*.so` shared libraries are built
from the [llama.cpp](https://github.com/ggml-org/llama.cpp) project with the
Qualcomm **pkg-snapdragon** backend enabled. They are **not redistributed**
in this repository.

1. Build llama.cpp from source (March 2026 snapshot or later) with the
   `pkg-snapdragon` CMake options for Snapdragon OpenCL + Hexagon HTP backends,
   **or** download a pre-built package from the llama.cpp release assets.
2. Copy the following files from `<PKG_SNAPDRAGON_ROOT>/llama.cpp/lib/` into
   `app/src/main/jniLibs/arm64-v8a/`:

| File | Purpose |
|---|---|
| `libllama.so` | llama.cpp text backbone |
| `libmtmd.so` | multimodal (image) wrapper |
| `libggml.so` | ggml core |
| `libggml-base.so` | ggml CPU/GPU shared scaffolding |
| `libggml-cpu.so` | ggml CPU backend (i8mm + FlashAttention) |
| `libggml-hexagon.so` | Hexagon backend entry point |
| `libggml-htp-v68.so` | HTP V68 stub — QCM6490 / Fairphone 5 |
| `libggml-htp-v79.so` | HTP V79 stub — Snapdragon 8 Elite / S25 Ultra |
| `libggml-opencl.so` | OpenCL GPU backend (optional) |

See `app/src/main/jniLibs/arm64-v8a/README.txt` for the complete file list
with per-SoC stub details.

> `libOpenCL.so` is **excluded** from the APK (`packaging.jniLibs.excludes`)
> and resolved at runtime against `/vendor/lib64/libOpenCL.so` on Qualcomm
> devices.

### 2. GGUF model files

Push the quantized GGUF model and vision projector to the device's `Downloads/`
directory. The app copies them to app-private storage on first run.

```bash
adb push Qwen3-VL-2B-Q8_0.gguf     /sdcard/Download/
adb push mmproj-Qwen3VL-2B-Q8_0.gguf /sdcard/Download/
```

The GGUF files can be obtained from the
[Hugging Face Qwen3-VL model page](https://huggingface.co/Qwen/Qwen3-VL-2B-Instruct-GGUF).

> The app expects these exact file names. If you use a different quantization,
> update `MODEL_FILENAME` and `MMPROJ_FILENAME` in `BootstrapRepository.kt`.

---

## Build

Run all commands from inside `android/EdgeVisionAI-PersonSense/`.

The CMake build (`ai_chat.cpp` → `libai-chat.so`) is triggered automatically
by Gradle via the `externalNativeBuild` block in `build.gradle.kts`.

### Debug build

```bash
./gradlew assembleDebug
```

### Release build

```bash
./gradlew assembleRelease
```

The APK is written to:

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

1. Push the GGUF model files to the device (see Prerequisites §2).
2. Launch **EdgeVision AI** on the device.
3. Grant the camera, notification, and storage permissions when prompted.
4. The app loads the VLM in the background — a status bar shows the loading
   progress (`Loading text model…`, `Loading vision projector…`, `VLM ready`).
5. Tap the **Capture** button to freeze the current camera frame and send it to
   the VLM.
6. The VLM returns bounding-box coordinates for detected people, which are
   overlaid on the frozen frame.
7. Tap **Capture Again** to return to the live preview.

---

## Architecture

### Service process and performance

`VlmService` runs **in the same process** as the Activity (no
`android:process=":vlm"` isolation). This keeps the service in the `top-app`
cgroup while the Activity is foregrounded, which yields roughly 3× higher
generation token throughput on Snapdragon Elite compared to an isolated
sub-process.

### Detection flow

```
CameraX ImageAnalysis
        │  (stores latest frame in LatestFrameStore)
        │
User taps Capture
        │
        ▼
CameraXController.captureLatestFrame()
        │  square-pads + converts to JPEG bytes
        ▼
VlmServiceConnection (AIDL)
        │  describeImage(imageBytes, prompt, maxTokens)
        ▼
VlmService (foreground service, same process)
        │  InferenceEngine.sendUserPromptWithImage()
        ▼
libai-chat.so (JNI) → libmtmd.so → libllama.so
        │  streams tokens back via IVlmCallback.onToken()
        ▼
QwenBBoxParser           ← parses JSON bbox output from token stream
        │
        ▼
PersonOverlay            ← Compose Canvas bounding-box overlay
```

### AIDL interface summary

| Method | Description |
|---|---|
| `setBackend(backend: String)` | Select compute backend: `"cpu"`, `"gpu"`, or `"htp"` |
| `setImageMaxTokens(maxTokens: Int)` | Cap visual tokens — must be called **before** `loadMmproj()` |
| `loadModel(modelPath: String)` | Load the GGUF LM weights |
| `loadMmproj(mmprojPath: String)` | Load the vision projector GGUF |
| `setSystemPrompt(prompt: String)` | Set the system prompt |
| `describeImage(...)` | Run inference on a single image |
| `describeImages(...)` | Run inference on multiple images in one call |
| `cancelGeneration()` | Cancel the in-progress generation |
| `isReady(): Boolean` | Returns `true` when model + mmproj are loaded |

---

## Configuration

Default values in `BootstrapRepository.kt`:

| Constant | Value | Notes |
|---|---|---|
| `MODEL_FILENAME` | `Qwen3-VL-2B-Q8_0.gguf` | LM backbone |
| `MMPROJ_FILENAME` | `mmproj-Qwen3VL-2B-Q8_0.gguf` | Vision projector |
| `IMAGE_MAX_TOKENS` | `72` | Visual token cap (bench-validated CVPR 2026 operating point) |
| `SYSTEM_PROMPT` | `"You are a helpful assistant."` | Minimal system context |

---

## Tested devices

| Device | SoC | Measured TTFT (CPU) | mAP@.5 |
|---|---|---|---|
| Samsung Galaxy S25 Ultra | Snapdragon 8 Elite (SM8750) | ~2.8 s | 0.48 |
| Fairphone 5 | Snapdragon QCM6490 | ~11 s | 0.43 |

See [`analysis/PersonSense/`](../../analysis/PersonSense/README.md) for the
full benchmark methodology and results.

---

## License

[MIT License](../../LICENSE) — Copyright (c) 2026 Fabricio Batista Narcizo
