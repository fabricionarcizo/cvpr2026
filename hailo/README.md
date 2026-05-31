# LibreYOLOXs Model Compilation and Setup Guide for Hailo-8 / Hailo-8L

This directory contains configuration files and guidelines to compile, quantize, and optimize the **LibreYOLOXs** object detection model into the **HEF (Hailo Executable Format)** binary. The guide covers execution within a pre-built Hailo Software Suite Docker container, as well as a native Ubuntu 22.04 LTS environment.

---

## Directory Structure

This folder contains the following configuration files and utility scripts needed for optimization:

*   [`README.md`](README.md) - This documentation.
*   [`prepare_dataset.py`](prepare_dataset.py) - Python script to download, preprocess, and pack the COCO val2017 validation dataset into a `.npy` archive for model quantization.
*   [`LibreYOLOXs.alls`](LibreYOLOXs.alls) - Hailo Dataflow Compiler (DFC) commands script containing calibration specifications and defining the post-processing configuration mapping.
*   [`LibreYOLOXs_nms.json`](LibreYOLOXs_nms.json) - Post-processing NMS (Non-Maximum Suppression) JSON config mapping out anchors, strides, detection heads, and dimensions for the Yolox architecture.

---

## 1. Getting Started with Hailo Software Suite Docker

The easiest way to work with the Hailo toolchain (Dataflow Compiler, HailoRT, Model Zoo) is to use the official pre-configured Docker image containing all dependencies.

### Step A: Download the Docker Suite
1. Go to the [Hailo Developer Zone](https://hailo.ai/developer-zone/) (account registration required).
2. Navigate to the **Software Suite** section and download the **Hailo AI Software Suite Docker** package. Make sure to select Hailo-8 / Hailo-8L and python 3.10.
3. Unzip the package to your preferred workspace. The local path on the host machine is located at:
   `/path/to/hailo_ai_sw_suite_2026-04_docker`
4. The downloaded package contains two main files:
   *   `hailo_ai_sw_suite_2026-04.tar.gz` - The archived Docker image (approx. 13.9 GB).
   *   `hailo_ai_sw_suite_docker_run.sh` - The runner bash script.

### Step B: System Requirements
The runner script performs checkups before launching the container:
*   **Architecture**: `x86_64` CPU.
*   **RAM**: At least 16 GB of physical memory (32 GB recommended).
*   **CPU Flag**: AVX instruction set support (required by TensorFlow).
*   **GPU (Optional)**: If a GPU is present, NVIDIA driver version 525+ and `nvidia-container-toolkit` are required to leverage hardware acceleration.
*   **Docker**: Docker Daemon installed and user added to the `docker` group.

### Step C: Running the Container
To start the container, run the runner script from your terminal:
```bash
cd /path/to/hailo_ai_sw_suite_2026-04_docker
./hailo_ai_sw_suite_docker_run.sh
```

During the first run, the script will automatically invoke `docker load -i hailo_ai_sw_suite_2026-04.tar.gz` to load the image into Docker. This may take several minutes. Once loaded, it spins up a container named `hailo_ai_sw_suite_2026-04_container`.

### Container Management Commands

*   **Resume existing container** (re-connects your session without resetting files):
    ```bash
    ./hailo_ai_sw_suite_docker_run.sh --resume
    ```
*   **Override container** (stops and deletes the existing container to start fresh):
    ```bash
    ./hailo_ai_sw_suite_docker_run.sh --override
    ```
*   **Run with HailoRT service** (enables the daemon to access connected hardware, Hailo-8 only):
    ```bash
    ./hailo_ai_sw_suite_docker_run.sh --hailort-enable-service
    ```
*   **Exit**: Simply type `exit` in the container's shell.

### Directory Mapping
The script creates a directory named `shared_with_docker` in the folder where it is run. This is mapped inside the container as:
`/local/shared_with_docker` (Read/Write)

> [!TIP]
> Use this `shared_with_docker` directory to share files (e.g. models, datasets, config files) between the host system and the running container. Copy this `hailo/` workspace folder into `shared_with_docker/` to make its scripts and configurations accessible inside Docker.

---

## 2. Setting Up Native Installation in Ubuntu 22.04 LTS

If you prefer to install the Hailo Dataflow Compiler (DFC) directly on a native Ubuntu 22.04 LTS machine (or inside a generic Ubuntu WSL/Docker container) without using the pre-built suite, follow these steps.

### Step A: Install System Dependencies
Install compilation tools, Python dependencies, Graphviz (for network visualization), and image processing packages:
```bash
sudo apt update
sudo apt install -y python3-pip python3.10-venv python3.10-dev python3.10-distutils python3-tk libfuse2 graphviz libgraphviz-dev git wget unzip
```

### Step B: Setup Python Virtual Environment
Create a clean environment running Python 3.10 (which is required by the Hailo DFC wheels):
```bash
python3 -m venv hailo_converter
source hailo_converter/bin/activate
pip install --upgrade pip
pip install pygraphviz
```

### Step C: Download and Install DFC Wheel
1. Visit the [Hailo Developer Zone](https://hailo.ai/developer-zone/) and navigate to the Downloads section.
2. Download the Python wheel matching your system architecture (usually `x86_64`) and Python version (`3.10`), e.g., `hailo_dataflow_compiler-3.33.1-py3-none-linux_x86_64.whl`.
3. Install the wheel using pip:
```bash
pip install /path/to/hailo_dataflow_compiler-3.33.1-py3-none-linux_x86_64.whl
```

### Step D: Verification
Verify that the Hailo CLI tool and python package are successfully configured:
```bash
# Check if CLI works
hailo -h

# Verify installed pip packages
pip freeze | grep hailo
```

---

## 3. Downloading and Preparing the Calibration Dataset

The quantization stage requires a calibration dataset representing the model's target inputs to evaluate integer activation clipping. 

### Step A: Download COCO val2017 Dataset
Run the following commands to download and unpack 5,000 COCO validation images:
```bash
wget https://huggingface.co/datasets/LibreYOLO/coco-val2017/resolve/main/coco-val2017.zip
unzip coco-val2017
```

### Step B: Run the Preprocessing Script
Use the provided `prepare_dataset.py` script to resize, transform, and sample the validation dataset:
```bash
python prepare_dataset.py coco-val2017/images/val2017/ --save-name libreyolox_calib.npy
```

#### What the Preprocessing Script Does:
*   Collects up to `1024` random `.jpg`/`.jpeg`/`.png` images.
*   Converts them to a 3-channel RGB representation (converting grayscale images and dropping alpha channels).
*   Resizes images to `640x640` (model input size) using Lanczos filtering to maintain visual fidelity.
*   Packs the resulting tensor into a float32 `.npy` archive with dimensions `(1024, 640, 640, 3)`. Note that inputs are left in the `[0.0, 255.0]` range without division, as expected by Yolox architectures.

---

## 4. Parsing, Quantizing, and Compiling the Model to HEF

With the compiler environment setup and the calibration dataset ready, follow this workflow to transform the model from ONNX format into a hardware-compatible HEF file.

### Step A: Download the LibreYOLOXs ONNX File
```bash
wget https://huggingface.co/fabricionarcizo/LibreYOLOXs/resolve/main/LibreYOLOXs.onnx
```

### Step B: Parse ONNX to HAR (Hailo Archive)
Parse the ONNX computational graph and generate a Hailo representation (.har). Specify the target hardware architecture (`hailo8l` for Raspberry Pi 5 AI Hat, or `hailo8` for standard M.2 modules):
```bash
hailo parser onnx LibreYOLOXs.onnx \
    --hw-arch hailo8l \
    --har-path LibreYOLOXs.har
```

### Step C: Optimize and Quantize the HAR Model
Quantize the floating point weights to INT8 and perform graph optimizations. The step takes the calibration dataset `.npy` file and the Hailo commands script (`LibreYOLOXs.alls`):
```bash
hailo optimize LibreYOLOXs.har \
    --hw-arch hailo8l \
    --calib-set-path libreyolox_calib.npy \
    --model-script LibreYOLOXs.alls \
    --output-har-path LibreYOLOXs_quantized.har
```

#### About LibreYOLOXs.alls & LibreYOLOXs_nms.json:
*   `LibreYOLOXs.alls` sets the calibration batch size and references the `LibreYOLOXs_nms.json` post-processing config.
*   `LibreYOLOXs_nms.json` maps out the bounding box decoders (strides 8, 16, 32 with corresponding regression, classification, and objectness layers) and configures detection metrics (IoU threshold = 0.65, Score threshold = 0.2) to fuse Non-Maximum Suppression (NMS) directly into the compiler output.

### Step D: Compile to HEF
Compile the optimized model into the executable format (.hef) targeted for the Hailo hardware:
```bash
# Compile and output compiling archive
hailo compiler LibreYOLOXs_quantized.har \
    --hw-arch hailo8l \
    --output-har-path LibreYOLOXs_compiled.har

# Extract the HEF binary from the compiled archive
hailo har extract LibreYOLOXs_compiled.har
```

This generates `LibreYOLOXs.hef` (or equivalent matching your configuration) which is ready to be copied to your target edge device (e.g. Raspberry Pi 5 with Hailo-8L) for deployment.
