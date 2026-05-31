# Edge AI in Action: Mastering On-Device Inference

> **CVPR 2026 Tutorial**
> Fabricio Batista Narcizo · Elizabete Munzlinger · Sai Narsi Reddy Donthi Reddy · Shan Ahmed Shaffi

This repository contains the companion code for the CVPR 2026 tutorial
*Edge AI in Action: Mastering On-Device Inference*. It demonstrates a full
pipeline — from model optimization to real-time on-device inference — targeting
the **Qualcomm Snapdragon** and **Hailo** platforms.

---

## What is in this repository?

The repository is organized into six independent areas:

| Directory | Description |
|---|---|
| [`android/`](android/) | Four Android apps + llama.cpp build guide for Qualcomm Snapdragon |
| [`docker/`](docker/) | Docker-based toolchain for model optimization (QAIRT / SNPE / QAI Hub) |
| [`hailo/`](hailo/) | LibreYOLOXs compilation and quantization guide for Hailo-8 / Hailo-8L |
| [`qualcomm/`](qualcomm/) | Android 15 BSP build guide for the Qualcomm RB3 Gen2 Vision Dev Kit |
| [`analysis/PersonSense/`](analysis/PersonSense/) | Benchmark notebook and chart scripts for VLM model selection |

---

## Android projects

All four Android apps are written in **Kotlin**, use **Jetpack Compose** for the
UI, **CameraX** for live camera preview, and **Hilt** for dependency injection.
They share a common detection workflow: capture a frame → preprocess → run
inference → postprocess → render bounding-box overlay.

| Project | Runtime | Model | Hardware target |
|---|---|---|---|
| [`EdgeVisionAI-SNPE`](android/EdgeVisionAI-SNPE/) | Qualcomm SNPE (Java AAR) | LibreYOLOXs INT8 `.dlc` | CPU / GPU / DSP |
| [`EdgeVisionAI-PSNPE`](android/EdgeVisionAI-PSNPE/) | Platform SNPE (PSNPE AAR) | LibreYOLOXs INT8 `.dlc` | CPU / GPU / DSP |
| [`EdgeVisionAI-QNN`](android/EdgeVisionAI-QNN/) | Qualcomm QNN (JNI / dlopen) | LibreYOLOXs INT8 `.bin` | CPU / HTP |
| [`EdgeVisionAI-PersonSense`](android/EdgeVisionAI-PersonSense/) | llama.cpp + mtmd (Qwen3-VL) | Qwen3-VL 2B GGUF | CPU / GPU / HTP |

### SNPE vs PSNPE vs QNN

- **SNPE** uses the official `snpe-release.aar` Java wrapper — the simplest
  integration path.
- **PSNPE** adds the `psnpe-release.aar` on top of SNPE, enabling DSP hardware
  runtime and unsigned protected domain execution.
- **QNN** bypasses the Java layer entirely. A custom C++ JNI bridge loads the
  QNN runtime libraries via `dlopen()` at runtime, giving more control over HTP
  (Hexagon Tensor Processor) execution.
- **PersonSense** replaces the classical detector with a vision-language model
  (Qwen3-VL 2B, quantized GGUF) running through `llama.cpp` + the `mtmd`
  multimodal extension.

### llama.cpp build guide

The [`android/llama.cpp/`](android/llama.cpp/) directory provides a step-by-step
guide for compiling `llama.cpp` with **Hexagon DSP/HTP**, **OpenCL GPU**, and
**CPU** support using the Snapdragon Docker toolchain, downloading multimodal
GGUF models from Hugging Face, and running inference directly from an `adb`
shell — without needing the full Android app.

See [`android/llama.cpp/README.md`](android/llama.cpp/README.md) for the
complete instructions.

---

## Hailo-8 / Hailo-8L toolchain

The [`hailo/`](hailo/) directory contains configuration files and scripts for
compiling, quantizing, and converting the **LibreYOLOXs** model to the
**HEF (Hailo Executable Format)** that runs on Hailo-8 and Hailo-8L accelerators
(e.g., the Raspberry Pi 5 AI Hat). It supports both the official Hailo Software
Suite Docker container and a native Ubuntu 22.04 LTS environment.

See [`hailo/README.md`](hailo/README.md) for the full compilation and
quantization workflow.

---

## Docker toolchain

The [`docker/`](docker/) directory provides a reproducible workspace for
converting and quantizing the LibreYOLO model to Qualcomm's DLC and BIN formats.
It bundles JupyterLab, the QAIRT SDK (`2.35.0.250530`), and the QAI Hub Python
client.

See [`docker/README.md`](docker/README.md) for full setup and usage
instructions.

---

## Qualcomm RB3 Gen2 BSP

The [`qualcomm/`](qualcomm/) directory contains a step-by-step guide for
building and flashing Android 15 on the Qualcomm RB3 Gen2 Vision Development
Kit (QCM6490 / QCS6490 platform), along with the patch files referenced in the
guide.

See [`qualcomm/README.md`](qualcomm/README.md) for the full build guide.

---

## PersonSense analysis

The [`analysis/PersonSense/`](analysis/PersonSense/) directory contains the
benchmark data, Jupyter notebook, and chart scripts used to select the best
VLM configuration for the PersonSense app.

See [`analysis/PersonSense/README.md`](analysis/PersonSense/README.md) for
methodology and results.

---

## Repository layout

```text
cvpr2026/
├── android/
│   ├── EdgeVisionAI-SNPE/          # SNPE Java AAR object detection app
│   ├── EdgeVisionAI-PSNPE/         # Platform SNPE (+ DSP) object detection app
│   ├── EdgeVisionAI-QNN/           # QNN JNI bridge object detection app
│   ├── EdgeVisionAI-PersonSense/   # Qwen3-VL VLM person detection app
│   └── llama.cpp/                  # llama.cpp Snapdragon build guide
├── analysis/
│   └── PersonSense/                # VLM benchmark notebook and charts
├── docker/                         # QAIRT / SNPE / QAI Hub optimization toolchain
├── hailo/                          # Hailo-8/8L LibreYOLOXs compilation guide
├── qualcomm/                       # Android 15 BSP guide for RB3 Gen2
├── LICENSE
└── README.md
```

---

## Requirements

### Android apps (common)

| Requirement | Version |
|---|---|
| Android Studio | Meerkat 2024.3.1 or later |
| Android Gradle Plugin | 8.x |
| Kotlin | 1.9 or later |
| Android NDK | r27 or later |
| Target device | ARM64 Android 9 (API 28) or later |

Each project has additional prerequisites (runtime libraries, model files)
described in its own README.

### Docker toolchain

| Requirement | Notes |
|---|---|
| Docker Engine | 24+ |
| Host architecture | x86_64 (Intel / AMD) required for local SNPE/QAIRT workflows |
| Disk space | ≥ 10 GB free for the image and model artifacts |

### Hailo toolchain

| Requirement | Notes |
|---|---|
| Docker Engine | 24+ (for Hailo Software Suite container) |
| Host architecture | x86_64 with AVX instruction set support |
| RAM | 16 GB minimum, 32 GB recommended |
| GPU (optional) | NVIDIA driver 525+, `nvidia-container-toolkit` |
| Python | 3.10 (required by Hailo DFC wheels) |
| Hailo Software Suite | 2026-04 or later — download from [Hailo Developer Zone](https://hailo.ai/developer-zone/) |

---

## License

This project is licensed under the [MIT License](LICENSE).

```
MIT License

Copyright (c) 2026 Fabricio Batista Narcizo
```

---

## Citation

If you use this code in your research, please cite the tutorial:

```bibtex
@inproceedings{narcizo2026edgeai,
  title     = {Edge {AI} in Action: Mastering On-Device Inference},
  author    = {Narcizo, Fabricio Batista and
               Munzlinger, Elizabete and
               {Donthi Reddy}, Sai Narsi Reddy and
               {Shaffi}, Shan Ahmed},
  booktitle = {The IEEE/CVF Conference on Computer Vision
               and Pattern Recognition (CVPR) Tutorials},
  year      = {2026},
}
```
