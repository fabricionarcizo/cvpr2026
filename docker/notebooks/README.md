# Jupyter Notebooks

This directory contains the tracked notebooks used to prepare, convert,
quantize, compile, and profile **LibreYOLOXs** for Qualcomm(R) Snapdragon
devices.

All four notebooks share the same preprocessing and ONNX export foundations:

- they download or reuse `../models/LibreYOLOXs.pt`
- they export `../models/LibreYOLOXs.onnx`
- they generate calibration data under `dataset/` and `subset/`
- they preserve the public input tensor `images` with shape `1 x 3 x 640 x 640`

---

## Notebook overview

| Notebook | Workflow | Outputs |
|---|---|---|
| [`qairt_optimizer.ipynb`](qairt_optimizer.ipynb) | Local QAIRT conversion, DLC inspection, and INT8 PTQ | `../models/qairt/*.dlc` |
| [`snpe_optimizer.ipynb`](snpe_optimizer.ipynb) | Local SNPE conversion, DLC inspection, and INT8 PTQ | `../models/snpe/*.dlc` |
| [`qaihub_optimizer.ipynb`](qaihub_optimizer.ipynb) | Cloud QAI Hub compile, quantize, and profile jobs | `../models/qaihub/*.dlc` plus remote profiling results |
| [`models_analysis.ipynb`](models_analysis.ipynb) | Cross-pipeline comparison, validation, and report generation | `reports/figures/*`, `reports/tables/*`, and analysis artifacts under `reports/` |

---

## Prerequisites

1. Start the Docker environment described in [`../README.md`](../README.md).
2. Ensure you have enough local disk space for:
   - the Docker image and SDK
   - the downloaded LibreYOLO checkpoint and ONNX export
   - the COCO validation images and generated calibration subsets
3. For `qaihub_optimizer.ipynb`, create `docker/notebooks/.env` from the tracked
   `docker/notebooks/.env .example` file and set `QAI_HUB_API_TOKEN`.

---

## Shared pipeline

### 1. Preprocessing and calibration data

Each notebook:

- downloads or reuses the COCO 2017 validation images under `dataset/`
- samples a representative subset
- writes calibration `.raw` tensors to `subset/calib/`
- writes validation `.raw` tensors and metadata to `subset/val/`
- generates `subset/calib/filenames.txt` and `subset/val/filenames.txt`

### 2. ONNX export

Each notebook downloads or reuses `../models/LibreYOLOXs.pt`, exports
`../models/LibreYOLOXs.onnx`, and validates the resulting output tensor shapes
before running a backend-specific flow.

### 3. Backend-specific execution

- **QAIRT notebook** uses local CLI tools such as `qairt-converter`,
  `qairt-dlc-info`, and `qairt-quantizer`.
- **SNPE notebook** uses local CLI tools such as `snpe-onnx-to-dlc`,
  `snpe-dlc-info`, and `snpe-dlc-quantize`.
- **QAI Hub notebook** uploads the ONNX model to Qualcomm's cloud service,
  submits compile / quantize / profile jobs, and downloads the resulting DLCs.

---

## Notebook details

### `qairt_optimizer.ipynb`

**Optimizing LibreYOLO for Qualcomm(R) Snapdragon Devices Using QAIRT**

Pipeline summary:

1. Prepare calibration and validation subsets from COCO 2017.
2. Export LibreYOLOXs to ONNX.
3. Validate ONNX output shapes.
4. Convert the ONNX model to FP32 DLC with `qairt-converter`.
5. Inspect the FP32 DLC with `qairt-dlc-info`.
6. Quantize to INT8 with `qairt-quantizer` using `subset/calib/filenames.txt`.
7. Inspect the INT8 DLC.

Outputs:

- `../models/qairt/LibreYOLOXs_fp32.dlc`
- `../models/qairt/LibreYOLOXs_int8.dlc`

### `snpe_optimizer.ipynb`

**Optimizing LibreYOLO for Qualcomm(R) Snapdragon Devices Using SNPE**

Pipeline summary:

1. Prepare calibration and validation subsets from COCO 2017.
2. Export LibreYOLOXs to ONNX.
3. Validate ONNX output shapes.
4. Convert the ONNX model to FP32 DLC with `snpe-onnx-to-dlc`.
5. Inspect the FP32 DLC with `snpe-dlc-info`.
6. Quantize to INT8 with `snpe-dlc-quantize` using `subset/calib/filenames.txt`.
7. Inspect the INT8 DLC.

Outputs:

- `../models/snpe/LibreYOLOXs_fp32.dlc`
- `../models/snpe/LibreYOLOXs_int8.dlc`

### `qaihub_optimizer.ipynb`

**Optimizing LibreYOLO for Qualcomm(R) Snapdragon Devices Using QAI Hub**

Pipeline summary:

1. Prepare calibration and validation subsets from COCO 2017.
2. Export LibreYOLOXs to ONNX.
3. Validate ONNX output shapes.
4. Load `QAI_HUB_API_TOKEN` from `docker/notebooks/.env`.
5. Query supported QAI Hub devices.
6. Upload the ONNX model and compile an FP32 DLC in the cloud.
7. Profile the FP32 DLC remotely.
8. Submit a cloud quantization job using the calibration tensors.
9. Compile and profile the INT8 DLC remotely.

Outputs:

- `../models/qaihub/LibreYOLOXs_fp32.dlc`
- `../models/qaihub/LibreYOLOXs_int8.dlc`
- remote QAI Hub job metadata and profiling results shown in the notebook

### `models_analysis.ipynb`

**LibreYOLOXs Model Analysis for Qualcomm Edge AI**

Pipeline summary:

1. Reuse the shared ONNX export and generated DLC artifacts when available.
2. Collect model metadata, profiling results, output similarity metrics, and accuracy summaries.
3. Generate comparison tables under `reports/tables/`.
4. Render summary figures under `reports/figures/`.

Outputs:

- `reports/figures/*`
- `reports/tables/*`
- additional analysis artifacts under `reports/`

---

## Shared preprocessing contract

| Property | Value |
|---|---|
| Input name | `images` |
| Input shape | `1 x 3 x 640 x 640` |
| Tensor layout | NCHW |
| Data type | `float32` |
| Color order | BGR |
| Resize strategy | Letterbox resize to `640 x 640` |
| Padding value | `114` |
| Normalization | None; values remain in the `0-255` range |

The calibration `.raw` files are written in CHW order and are reused by the
local quantization flow and the QAI Hub cloud quantization flow.

---

## Working directory layout

```text
docker/notebooks/
|- .env
|- .env .example
|- .gitignore
|- README.md
|- dataset/
|- output/
|- subset/
|- weights/
|- models_analysis.ipynb
|- qaihub_optimizer.ipynb
|- qairt_optimizer.ipynb
`- snpe_optimizer.ipynb
```

The generated directories are ignored by Git via `.gitignore`:

- `dataset/`
- `output/`
- `subset/`
- `weights/`

---

## Execution notes

- The notebooks are designed to reuse existing downloads and exported models
  when the expected files are already present.
- Running one notebook first usually prepares shared artifacts (`.pt`, `.onnx`,
  calibration subsets) for the others.
- If you only need cloud compilation and profiling, you can run the QAI Hub
  notebook without using the local QAIRT or SNPE conversion steps.
- `models_analysis.ipynb` is configured for headless Docker execution and saves
  figures with Matplotlib's `Agg` backend instead of relying on inline display.
