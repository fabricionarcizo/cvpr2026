# Jupyter Notebooks

This directory contains Jupyter Notebooks that implement the full optimization
pipeline for deploying **LibreYOLOXs** on Qualcomm® Snapdragon devices. Each
notebook converts and quantizes the model using a different Qualcomm SDK,
producing the DLC files that are published on Hugging Face.

| Notebook | SDK | Description |
|---|---|---|
| [`qairt_optimizer.ipynb`](#qairt_optimizerippynb) | Qualcomm AI Runtime (QAIRT) | Converts and quantizes LibreYOLOXs using the QAIRT SDK toolchain |
| [`snpe_optimizer.ipynb`](#snpe_optimizerippynb) | Snapdragon Neural Processing Engine (SNPE) | Converts, quantizes, and compiles LibreYOLOXs for HTP using the SNPE SDK toolchain |

---

## Prerequisites

Before running the notebooks, ensure the following are in place:

1. **Docker and Docker Compose** — Install [Docker Engine](https://docs.docker.com/engine/install/) and [Docker Compose](https://docs.docker.com/compose/install/).
2. **Model files** — Download the pre-trained `LibreYOLOXs.pt` checkpoint into `docker/models/` **or** let the notebook download it automatically on first run. See [`docker/models/README.md`](../models/README.md) for details.
3. **Disk space** — Allow approximately **2 GB** of free disk space:
   - ~777 MB for the COCO 2017 validation set (downloaded automatically)
   - ~200 MB for model files (`.pt`, `.onnx`, `.dlc`)

---

## Starting the Environment

All notebooks run inside the Docker container defined in `docker/Dockerfile`.
The container exposes JupyterLab on port **8888**.

### Build and start

```bash
# From the docker/ directory
cd docker
docker compose up --build -d
```

### Access JupyterLab

Open your browser and navigate to:

```
http://localhost:8888
```

No authentication token is required. The notebooks are available immediately
under the `notebooks/` directory in the JupyterLab file browser.

### Stop the container

```bash
docker compose down
```

---

## `qairt_optimizer.ipynb`

**Optimizing LibreYOLO for Qualcomm® Snapdragon Devices Using QAIRT**

This notebook walks through the full pipeline to convert and quantize
LibreYOLOXs using the **Qualcomm AI Runtime (QAIRT) SDK** (v2.41.0.251128).

### Pipeline

| Step | Description |
|---|---|
| **1. Imports** | Load `glob`, `os`, `random`, `torch`, `uuid`, `numpy`, `libreyolo`, and `PIL`. |
| **2. Preprocessing function** | Define `preprocess()` — resize to 640 × 640 (bilinear), convert to RGB, normalize to `[0, 1]`, transpose HWC → CHW, add batch dimension. |
| **3. Calibration dataset** | Download the COCO 2017 validation set (~777 MB), randomly sample 100 images (`seed=42`), save each as a `.raw` binary file, and generate `raw/filenames.txt` for `qairt-quantizer`. |
| **4. ONNX export** | Download `LibreYOLOXs.pt` from Hugging Face (if absent), load it with `LibreYOLO`, and export to ONNX (opset 18) with input `images` (1 × 3 × 640 × 640) and outputs `output_small`, `output_medium`, `output_large`. |
| **5. FP32 DLC conversion** | Convert the ONNX model to a floating-point DLC using `qairt-converter`. |
| **6. FP32 DLC inspection** | Inspect the FP32 DLC graph (layer names, tensor shapes, backends) using `qairt-dlc-info`. |
| **7. INT8 quantization** | Apply post-training quantization (PTQ) using `qairt-quantizer` and the calibration `.raw` samples to produce an INT8 DLC. |
| **8. INT8 DLC inspection** | Verify the INT8 DLC using `qairt-dlc-info` to confirm quantized layer types and reduced model size. |

### CLI Tools

| Tool | Purpose |
|---|---|
| `qairt-converter` | Converts an ONNX model to QAIRT's DLC format (FP32) |
| `qairt-dlc-info` | Inspects DLC graph structure, tensor shapes, and supported backends |
| `qairt-quantizer` | Applies post-training INT8 quantization using a calibration dataset |

### Output Files

All files are written to `../models/` relative to the notebook working directory
(i.e., `docker/models/` in the repository).

| File | Description |
|---|---|
| `LibreYOLOXs.pt` | Pre-trained PyTorch checkpoint (downloaded) |
| `LibreYOLOXs.onnx` | ONNX export of the model (opset 18) |
| `qairt/LibreYOLOXs_fp32.dlc` | QAIRT floating-point DLC |
| `qairt/LibreYOLOXs_int8.dlc` | QAIRT INT8 quantized DLC |

---

## `snpe_optimizer.ipynb`

**Optimizing LibreYOLO for Qualcomm® Snapdragon Devices Using SNPE**

This notebook walks through the full pipeline to convert, quantize, and compile
LibreYOLOXs using the **Snapdragon Neural Processing Engine (SNPE) SDK**. The
SNPE CLI flow is used because it is still widely deployed for DLC-based
on-device inference. For newer projects, the QAIRT tools offer a more unified
interface.

### Pipeline

| Step | Description |
|---|---|
| **1. Imports** | Same libraries as the QAIRT notebook. |
| **2. Preprocessing function** | Same `preprocess()` function as the QAIRT notebook. |
| **3. Calibration dataset** | Same COCO 2017 val download, 100-image sampling, and `.raw` / `filenames.txt` generation (idempotent — reuses existing files). |
| **4. ONNX export** | Same PyTorch → ONNX export as the QAIRT notebook (idempotent — skips if `LibreYOLOXs.onnx` already exists). |
| **5. FP32 DLC conversion** | Convert the ONNX model to a floating-point DLC using `snpe-onnx-to-dlc`. |
| **6. FP32 DLC inspection** | Inspect the FP32 DLC using `snpe-dlc-info`. |
| **7. INT8 quantization** | Apply post-training quantization using `snpe-dlc-quantize` and the calibration `.raw` samples. |
| **8. INT8 DLC inspection** | Verify the INT8 DLC with `snpe-dlc-info`. |
| **9. HTP graph compilation** | Compile the INT8 DLC offline for the **Hexagon Tensor Processor (HTP)** on the **Snapdragon 778G (sm7325)** using `snpe-dlc-graph-prepare`. Produces a pre-compiled DLC for maximum HTP utilization on-device. |
| **10. HTP DLC inspection** | Verify the compiled DLC with `snpe-dlc-info` to confirm HTP-specific optimizations. |

### CLI Tools

| Tool | Purpose |
|---|---|
| `snpe-onnx-to-dlc` | Converts an ONNX model to SNPE's DLC format (FP32) |
| `snpe-dlc-info` | Inspects DLC graph structure, tensor shapes, and supported backends |
| `snpe-dlc-quantize` | Applies post-training INT8 quantization using a calibration dataset |
| `snpe-dlc-graph-prepare` | Compiles the DLC graph offline for a specific Snapdragon HTP SoC |

### Output Files

All files are written to `../models/` relative to the notebook working directory
(i.e., `docker/models/` in the repository).

| File | Description |
|---|---|
| `LibreYOLOXs.pt` | Pre-trained PyTorch checkpoint (shared with QAIRT notebook) |
| `LibreYOLOXs.onnx` | ONNX export of the model (shared with QAIRT notebook) |
| `snpe/LibreYOLOXs_fp32.dlc` | SNPE floating-point DLC |
| `snpe/LibreYOLOXs_int8.dlc` | SNPE INT8 quantized DLC |
| `snpe/LibreYOLOXs_int8_sm7325.dlc` | SNPE INT8 DLC with offline HTP compilation for Snapdragon 778G |

---

## Shared Resources

Both notebooks share the following resources and conventions.

### `preprocess()` function

```python
def preprocess(original_image: np.ndarray, size: int = 640) -> np.ndarray:
    image = Image.fromarray(original_image).convert("RGB")
    image = image.resize((size, size), Image.BILINEAR)
    image = np.asarray(image).astype(np.float32) / 255.0
    image = np.transpose(image, (2, 0, 1))  # HWC -> CHW
    image = np.expand_dims(image, axis=0)   # CHW -> NCHW
    return image
```

| Property | Value |
|---|---|
| Output shape | `(1, 3, 640, 640)` (NCHW) |
| Data type | `float32` |
| Value range | `[0.0, 1.0]` |
| Resize method | Bilinear interpolation |

### Calibration dataset

| Property | Value |
|---|---|
| Source | COCO 2017 validation set |
| Download size | ~777 MB |
| Images sampled | 100 |
| Random seed | 42 |
| Format | `.raw` (flat `float32` binary, NCHW layout) |
| Manifest | `raw/filenames.txt` |

### Idempotency

Both notebooks are designed to be **re-runnable without side effects**. Each
step checks whether its output already exists before executing:
- The COCO validation set is not re-downloaded if `val/` exists.
- The `.raw` calibration files are not regenerated if `raw/` exists.
- `LibreYOLOXs.pt` is not re-downloaded if it already exists in `../models/`.
- `LibreYOLOXs.onnx` is not re-exported if it already exists.

---

## Directory Structure

```
docker/notebooks/
├── qairt_optimizer.ipynb       # QAIRT optimization pipeline
├── snpe_optimizer.ipynb        # SNPE optimization pipeline
├── README.md                   # This file
├── val/                        # COCO 2017 val images (auto-downloaded, ~777 MB)
├── raw/                        # Calibration .raw files + filenames.txt (auto-generated)
├── output/                     # Notebook output artefacts
├── .ipynb_checkpoints/         # JupyterLab checkpoint files (auto-managed)
└── .pkl_memoize_py3/           # Memoization cache (auto-generated by libreyolo)
```

> **Note:** The `val/`, `raw/`, `output/`, `.ipynb_checkpoints/`, and
> `.pkl_memoize_py3/` directories are listed in `.gitignore` and are not
> tracked by Git. They are created automatically when the notebooks are run.

---

## Recommended Execution Order

Run the notebooks in the following order to avoid redundant downloads and
exports, since both share `LibreYOLOXs.pt` and `LibreYOLOXs.onnx`:

```
1. qairt_optimizer.ipynb   ← downloads .pt, generates .onnx, produces QAIRT DLCs
2. snpe_optimizer.ipynb    ← reuses .pt and .onnx, produces SNPE DLCs
```

If you only need SNPE models, run `snpe_optimizer.ipynb` first — it will
download and export the model as part of its own pipeline.
