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
  - qai-hub
  - edge-inference
base_model: LibreYOLO/LibreYOLOXs
datasets:
  - merve/coco
---

# LibreYOLOXs Qualcomm-Optimized Model Artifacts

This directory is the shared artifact location for the notebooks in
[`../notebooks/`](../notebooks/). The repository tracks the documentation and
ignore rules here; model binaries are downloaded or generated locally when you
run the workflows.

The same source model is used for three deployment paths:

- **QAIRT** local conversion and quantization
- **SNPE** local conversion and quantization
- **QAI Hub** cloud compilation, quantization, and profiling

---

## Model overview

| Property | Value |
|---|---|
| Architecture | YOLOX-Small (LibreYOLOXs) |
| Framework | [LibreYOLO](https://github.com/LibreYOLO/libreyolo) |
| Task | Object detection |
| Base checkpoint | [LibreYOLO/LibreYOLOXs](https://huggingface.co/LibreYOLO/LibreYOLOXs) |
| Input tensor | `images` |
| Input shape | `1 x 3 x 640 x 640` |
| Input format | BGR `float32`, NCHW, values in `0-255` |

---

## Artifact flow

```text
LibreYOLOXs.pt
    |
    `---> LibreYOLOXs.onnx
             |\
             | `---> qairt/LibreYOLOXs_fp32.dlc
             |        `---> qairt/LibreYOLOXs_int8.dlc
             |
             | `---> snpe/LibreYOLOXs_fp32.dlc
             |        `---> snpe/LibreYOLOXs_int8.dlc
             |
             `---> qaihub/LibreYOLOXs_fp32.dlc
                      `---> qaihub/LibreYOLOXs_int8.dlc
```

All three notebooks reuse the same exported ONNX model.

---

## Expected files

| Path | Produced by | Description |
|---|---|---|
| `LibreYOLOXs.pt` | All notebooks | Base PyTorch checkpoint, downloaded on demand |
| `LibreYOLOXs.onnx` | All notebooks | Shared ONNX export used by all backend flows |
| `qairt/LibreYOLOXs_fp32.dlc` | QAIRT notebook | FP32 DLC produced by `qairt-converter` |
| `qairt/LibreYOLOXs_int8.dlc` | QAIRT notebook | INT8 DLC produced by `qairt-quantizer` |
| `snpe/LibreYOLOXs_fp32.dlc` | SNPE notebook | FP32 DLC produced by `snpe-onnx-to-dlc` |
| `snpe/LibreYOLOXs_int8.dlc` | SNPE notebook | INT8 DLC produced by `snpe-dlc-quantize` |
| `qaihub/LibreYOLOXs_fp32.dlc` | QAI Hub notebook | FP32 DLC downloaded from a cloud compile job |
| `qaihub/LibreYOLOXs_int8.dlc` | QAI Hub notebook | INT8 DLC downloaded from cloud quantize + compile jobs |

---

## Tensor contract

### Input

| Property | Value |
|---|---|
| Name | `images` |
| Shape | `1 x 3 x 640 x 640` |
| Layout | NCHW |
| Data type | `float32` |
| Color order | BGR |

Preprocessing used by the notebooks:

1. Letterbox-resize to `640 x 640`.
2. Pad with value `114`.
3. Keep BGR channel order.
4. Keep values in the `0-255` range.
5. Transpose from HWC to CHW before serialization / inference input assembly.

### Outputs

The exported ONNX model is validated in each notebook before backend-specific
compilation. The documentation in this directory assumes the shared decoded
output layout used by the notebooks:

| Output | Shape | Description |
|---|---|---|
| `bboxes` | `1 x 8400 x 4` | Bounding-box coordinates |
| `scores` | `1 x 8400 x 81` | Objectness plus 80 COCO class scores |

---

## Calibration data

INT8 flows use representative data prepared by the notebooks from COCO 2017
validation images:

- source images are staged under `docker/notebooks/dataset/`
- calibration tensors are written to `docker/notebooks/subset/calib/`
- validation tensors and metadata are written to `docker/notebooks/subset/val/`
- manifests are written to `subset/calib/filenames.txt` and `subset/val/filenames.txt`

The local QAIRT and SNPE quantizers read those manifests directly. The QAI Hub
notebook loads the same `.raw` tensors into NumPy arrays and submits them to the
cloud quantization job.

---

## Downloading published artifacts

Published files are hosted on Hugging Face:

- [fabricionarcizo/LibreYOLOXs](https://huggingface.co/fabricionarcizo/LibreYOLOXs)

Example download:

```bash
huggingface-cli download fabricionarcizo/LibreYOLOXs \
    --local-dir docker/models \
    --exclude "*.gitattributes" "README.md"
```

You can also generate the same artifact layout locally by running the notebooks
in [`../notebooks/`](../notebooks/).

---

## Related documentation

- [`../README.md`](../README.md) - Docker environment and setup
- [`../notebooks/README.md`](../notebooks/README.md) - notebook workflows and generated data layout
