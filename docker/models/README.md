---
license: mit
language:
  - en
pipeline_tag: object-detection
tags:
  - object-detection
  - yolox
  - onnx
  - dlc
  - qualcomm
  - snapdragon
  - qairt
  - snpe
  - edge-inference
base_model: LibreYOLO/LibreYOLOXs
datasets:
  - merve/coco
---

# LibreYOLOXs — Qualcomm-Optimized Object Detection Models

> **LibreYOLOXs** is a YOLOX-Small model repackaged for the
> [LibreYOLO](https://github.com/LibreYOLO/libreyolo) framework and further
> optimized for efficient inference on Qualcomm® Snapdragon devices using the
> **Qualcomm AI Runtime (QAIRT) SDK** and the **Snapdragon Neural Processing
> Engine (SNPE) SDK**.

---

## ⚠️ Model Availability

> **The model files are NOT included in the GitHub repository.**

All model files are hosted exclusively on Hugging Face:

🔗 **[https://huggingface.co/fabricionarcizo/LibreYOLOXs](https://huggingface.co/fabricionarcizo/LibreYOLOXs)**

The `docker/models/` directory in the GitHub repository contains only this
`README.md` and a `.gitignore` that prevents binary model files from being
accidentally committed. You must download the models from Hugging Face before
running the notebooks or deploying to a device.

---

## Model Overview

| Property | Value |
|---|---|
| Architecture | YOLOX-Small (YOLOXs) |
| Framework | [LibreYOLO](https://github.com/LibreYOLO/libreyolo) |
| Task | Object Detection |
| Dataset | [COCO 2017](https://cocodataset.org) (80 classes) |
| Input size | 640 × 640 (RGB) |
| Base model | [LibreYOLO/LibreYOLOXs](https://huggingface.co/LibreYOLO/LibreYOLOXs) |
| License | [MIT](https://opensource.org/licenses/MIT) |

---

## Model Files

All files below are available at
[fabricionarcizo/LibreYOLOXs](https://huggingface.co/fabricionarcizo/LibreYOLOXs)
on Hugging Face.

| File | Format | Description |
|---|---|---|
| `LibreYOLOXs.pt` | PyTorch | Pre-trained LibreYOLOXs checkpoint. Downloaded from `LibreYOLO/LibreYOLOXs` and used as the entry point for all downstream conversions. |
| `LibreYOLOXs.onnx` | ONNX (opset 13) | ONNX export of the PyTorch checkpoint with `head.export = True`. Single input `images` (shape `1 × 3 × 640 × 640`), single decoded output `detections` (shape `1 × 8400 × 85`). |
| `qairt/LibreYOLOXs_fp32.dlc` | QAIRT DLC (FP32) | Floating-point DLC converted from the ONNX model using `qairt-converter` (QAIRT SDK v2.41.0.251128). |
| `qairt/LibreYOLOXs_int8.dlc` | QAIRT DLC (INT8) | Post-training quantized DLC produced by `qairt-quantizer` using 1000 COCO 2017 validation images as calibration data. |
| `snpe/LibreYOLOXs_fp32.dlc` | SNPE DLC (FP32) | Floating-point DLC converted from the ONNX model using `snpe-onnx-to-dlc`. |
| `snpe/LibreYOLOXs_int8.dlc` | SNPE DLC (INT8) | Post-training quantized DLC produced by `snpe-dlc-quantize` using 1000 COCO 2017 validation images as calibration data. |
| `snpe/LibreYOLOXs_int8_sm7325.dlc` | SNPE DLC (INT8 + HTP) | INT8 DLC with offline HTP graph compilation for the **Snapdragon 778G (sm7325)**, produced by `snpe-dlc-graph-prepare`. Ready for maximum HTP utilization on-device. |

---

## Model Details

### Input

| Property | Value |
|---|---|
| Name | `images` |
| Shape | `1 × 3 × 640 × 640` (NCHW) |
| Data type | `float32` |
| Value range | `[0.0, 1.0]` |
| Color order | RGB |

Preprocessing steps:
1. Resize the image to **640 × 640** using bilinear interpolation.
2. Convert to RGB and normalize pixel values to `[0, 1]` by dividing by `255.0`.
3. Transpose from `HWC` to `CHW` and add a batch dimension (`NCHW`).

### Outputs

The model uses `head.export = True` to decode grid offsets inside the ONNX graph,
producing a single flat output tensor:

| Output name | Shape | Description |
|---|---|---|
| `detections` | `1 × 8400 × 85` | All decoded detections across all scales |

The 8400 rows correspond to anchor positions across the three detection scales:

| Scale | Grid | Anchors |
|---|---|---|
| Small objects | 80 × 80 | 6400 |
| Medium objects | 40 × 40 | 1600 |
| Large objects | 20 × 20 | 400 |

Each of the 85 values per row is laid out as:

```
[cx, cy, w, h,  objectness,  class_0, class_1, ..., class_79]
  4 bbox coords     1             80 class probabilities
```

- **`cx`, `cy`**: Centre x/y of the bounding box in the resized 640 × 640 coordinate space.
- **`w`, `h`**: Width and height of the bounding box.
- **`objectness`**: Confidence that a object is present (sigmoid applied).
- **`class_0`…`class_79`**: Per-class probabilities for all 80 COCO categories.

Post-processing steps required before use: confidence thresholding, `cxcywh → xyxy`
conversion, scaling back to the original image dimensions, and Non-Maximum Suppression (NMS).

---

## How to Download

### Using the Hugging Face CLI

```bash
# Install the Hugging Face Hub CLI (if not already installed)
pip install huggingface_hub

# Download all model files into docker/models/
huggingface-cli download fabricionarcizo/LibreYOLOXs \
    --local-dir docker/models \
    --exclude "*.gitattributes" "README.md"
```

### Using Python

```python
from huggingface_hub import snapshot_download

snapshot_download(
    repo_id="fabricionarcizo/LibreYOLOXs",
    local_dir="docker/models",
    ignore_patterns=["*.gitattributes", "README.md"],
)
```

### Downloading individual files

```bash
# PyTorch checkpoint
curl -L -o docker/models/LibreYOLOXs.pt \
    "https://huggingface.co/fabricionarcizo/LibreYOLOXs/resolve/main/LibreYOLOXs.pt"

# ONNX model
curl -L -o docker/models/LibreYOLOXs.onnx \
    "https://huggingface.co/fabricionarcizo/LibreYOLOXs/resolve/main/LibreYOLOXs.onnx"

# QAIRT DLC models
curl -L -o docker/models/qairt/LibreYOLOXs_fp32.dlc \
    "https://huggingface.co/fabricionarcizo/LibreYOLOXs/resolve/main/qairt/LibreYOLOXs_fp32.dlc"
curl -L -o docker/models/qairt/LibreYOLOXs_int8.dlc \
    "https://huggingface.co/fabricionarcizo/LibreYOLOXs/resolve/main/qairt/LibreYOLOXs_int8.dlc"

# SNPE DLC models
curl -L -o docker/models/snpe/LibreYOLOXs_fp32.dlc \
    "https://huggingface.co/fabricionarcizo/LibreYOLOXs/resolve/main/snpe/LibreYOLOXs_fp32.dlc"
curl -L -o docker/models/snpe/LibreYOLOXs_int8.dlc \
    "https://huggingface.co/fabricionarcizo/LibreYOLOXs/resolve/main/snpe/LibreYOLOXs_int8.dlc"
curl -L -o docker/models/snpe/LibreYOLOXs_int8_sm7325.dlc \
    "https://huggingface.co/fabricionarcizo/LibreYOLOXs/resolve/main/snpe/LibreYOLOXs_int8_sm7325.dlc"
```

---

## Optimization Pipeline

The model files were generated using the Jupyter notebooks in
`docker/notebooks/` and the Docker environment defined in `docker/Dockerfile`.
The optimization pipeline is illustrated below:

```
LibreYOLO/LibreYOLOXs (HuggingFace)
        │
        ▼
LibreYOLOXs.pt  ──── torch.onnx.export (opset 13) ────► LibreYOLOXs.onnx
                                                                  │
                          ┌───────────────────────────────────────┤
                          │                                       │
                          ▼                                       ▼
              qairt-converter                           snpe-onnx-to-dlc
                          │                                       │
                          ▼                                       ▼
             qairt/LibreYOLOXs_fp32.dlc            snpe/LibreYOLOXs_fp32.dlc
                          │                                       │
                          ▼                                       ▼
              qairt-quantizer (INT8)               snpe-dlc-quantize (INT8)
                          │                                       │
                          ▼                                       ▼
             qairt/LibreYOLOXs_int8.dlc            snpe/LibreYOLOXs_int8.dlc
                                                                  │
                                                                  ▼
                                              snpe-dlc-graph-prepare (sm7325)
                                                                  │
                                                                  ▼
                                              snpe/LibreYOLOXs_int8_sm7325.dlc
```

### Toolchain versions

| Tool | Version |
|---|---|
| Qualcomm AI Runtime (QAIRT) SDK | `v2.41.0.251128` |
| SNPE SDK | Included with QAIRT `v2.41.0.251128` |
| ONNX | `1.16.0` |
| ONNXRuntime | `1.18.0` |
| PyTorch (via LibreYOLO) | As required by `libreyolo` |

---

## Calibration Dataset

INT8 quantization was performed using **post-training quantization (PTQ)** with
a representative calibration dataset derived from the
[COCO 2017 validation set](https://cocodataset.org/#download):

- **Source:** COCO 2017 validation images (~777 MB, 5,000 images)
- **Samples used:** 1000 images selected randomly with `seed=42`
- **Format:** `.raw` binary files — flat `float32` arrays with shape `(1, 3, 640, 640)` in NCHW layout
- **Preprocessing:** resize to 640 × 640 (bilinear), normalize to `[0, 1]`

---

## Target Hardware

The DLC models are designed for deployment on devices powered by
**Qualcomm® Snapdragon** SoCs using the on-chip **Hexagon Tensor Processor (HTP)**:

| Model | Target | Backend |
|---|---|---|
| `qairt/LibreYOLOXs_fp32.dlc` | Any QAIRT-supported device | CPU / GPU / HTP |
| `qairt/LibreYOLOXs_int8.dlc` | Any QAIRT-supported device | HTP (INT8) |
| `snpe/LibreYOLOXs_fp32.dlc` | Any SNPE-supported device | CPU / GPU / HTP |
| `snpe/LibreYOLOXs_int8.dlc` | Any SNPE-supported device | HTP (INT8) |
| `snpe/LibreYOLOXs_int8_sm7325.dlc` | **Snapdragon 778G (sm7325)** | HTP (INT8, pre-compiled) |

The `snpe/LibreYOLOXs_int8_sm7325.dlc` file is the recommended model for
deployment on devices featuring the **Snapdragon 778G** chipset, as it has been
compiled offline to maximize HTP utilization and minimize on-device latency.

---

## License

This repository is released under the [MIT License](https://opensource.org/licenses/MIT).

The base LibreYOLOXs checkpoint is derived from
[Megvii-BaseDetection/YOLOX](https://github.com/Megvii-BaseDetection/YOLOX),
originally licensed under the **Apache License 2.0**.
No learned parameters were modified — only the state-dict key names were remapped
for compatibility with the LibreYOLO module naming scheme (see
[LibreYOLO/LibreYOLOXs](https://huggingface.co/LibreYOLO/LibreYOLOXs)).

---

## Authors

This work was developed by:

- **Fabricio Batista Narcizo** — [fabricionarcizo.com](https://www.fabricionarcizo.com)
- **Elizabete Munzlinger** - [elizabete.com.br](https://www.elizabete.com.br)
- **Sai Narsi Reddy Donthi Reddy** - [LinkedIn](https://linkedin.com/in/itsnarsi)
- **Shan Ahmed Shaffi** - [LinkedIn](https://linkedin.com/in/shanahmedshaffi)
- **Zaheer Ahmed** - [LinkedIn](https://linkedin.com/in/zaheer-ahmed-93366916)
