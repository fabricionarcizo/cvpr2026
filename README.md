# Edge AI in Action: Mastering On-Device Inference

> **CVPR 2026 Tutorial**
> Fabricio Batista Narcizo · Elizabete Munzlinger · Sai Narsi Reddy Donthi Reddy · Shan Ahmed Shaffi

This repository contains the companion code for the CVPR 2026 tutorial
*Edge AI in Action: Mastering On-Device Inference*. It demonstrates a full
pipeline — from model optimization to real-time on-device inference — targeting
the **Qualcomm Snapdragon** platform.

---

## What is in this repository?

The repository is organized into four independent areas:

| Directory | Description |
|---|---|
| [`android/`](android/) | Four Android apps demonstrating different Qualcomm inference runtimes |
| [`docker/`](docker/) | Docker-based toolchain for model optimization (QAIRT / SNPE / QAI Hub) |
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
│   └── EdgeVisionAI-PersonSense/   # Qwen3-VL VLM person detection app
├── analysis/
│   └── PersonSense/                # VLM benchmark notebook and charts
├── docker/                         # QAIRT / SNPE / QAI Hub optimization toolchain
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
