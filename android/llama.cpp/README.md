# llama.cpp on Snapdragon (Hexagon, GPU, & CPU)

This guide explains how to compile `llama.cpp` with Hexagon DSP/HTP support using the Snapdragon Docker toolchain, download multimodal/vision GGUF models, push them to an Android device, and run inference across **CPU**, **GPU (OpenCL)**, and **HTP (Hexagon Tensor Processor)** backends.

---

## 1. Compilation with Hexagon Support

To compile `llama.cpp` for Snapdragon-based Android devices, use the official Snapdragon toolchain Docker image. This container comes pre-configured with the Android NDK, Hexagon SDK, OpenCL SDK, CMake, and other build prerequisites.

### Step 1: Clone and Prepare llama.cpp
Execute these commands on your host system:
```bash
git clone https://github.com/ggml-org/llama.cpp.git
cd llama.cpp
git submodule update --init --recursive
```

### Step 2: Run the Docker Container
Start the container while mounting the current directory (`llama.cpp`) to `/workspace`:
```bash
docker run -it -u $(id -u):$(id -g) \
  --volume $(pwd):/workspace \
  --platform linux/amd64 \
  ghcr.io/snapdragon-toolchain/arm64-android:v0.3
```
> [!NOTE]
> If `v0.3` is unavailable, you can also use newer versions such as `v0.7`.

### Step 3: Configure and Build Inside Docker
Run the following commands **inside** the running Docker container:
```bash
# Navigate to the workspace
cd /workspace

# Copy the Snapdragon CMake presets to the root directory
cp docs/backend/snapdragon/CMakeUserPresets.json .

# Generate the build files using the snapdragon preset
cmake --preset arm64-android-snapdragon-release -B build-snapdragon

# Build the project
cmake --build build-snapdragon

# Install and package the binaries/libraries
cmake --install build-snapdragon --prefix pkg-snapdragon/llama.cpp
```
This builds and creates an installable package inside `pkg-snapdragon/llama.cpp` containing:
- `bin/`: CLI binaries (`llama-cli`, `llama-mtmd-cli`, etc.)
- `lib/`: Shared libraries (`libggml.so`, `libggml-cpu.so`, `libggml-opencl.so`, `libggml-hexagon.so`, and HTP core libraries such as `libggml-htp-v79.so` / `libggml-htp-v81.so` depending on the Hexagon version).

Exit the Docker container when done:
```bash
exit
```

For more details, see the official [Snapdragon Backend Documentation](https://github.com/ggml-org/llama.cpp/blob/master/docs/backend/snapdragon/README.md).

---

## 2. Downloading GGUF & Vision Models

Multimodal vision models require two components:
1. The **Language Model** quantizations (`IQ4_NL`, `Q4_0`, `Q8_0`).
2. The **Vision Projection Model** (`mmproj`, typically in `fp16` or `bf16`).

Here are examples of how to download these models using either `huggingface-cli` or `curl`.

### Option A: Using `huggingface-cli` (Recommended)
You can download models directly from Hugging Face using the CLI:
```bash
# Install Hugging Face Hub CLI if not already installed
pip install huggingface_hub[cli]

# Create a local directory for models
mkdir -p gguf

# Download Qwen2-VL-2B-Instruct GGUF quantizations
huggingface-cli download Qwen/Qwen2-VL-2B-Instruct-GGUF Qwen2-VL-2B-Instruct-IQ4_NL.gguf --local-dir ./gguf
huggingface-cli download Qwen/Qwen2-VL-2B-Instruct-GGUF Qwen2-VL-2B-Instruct-Q4_0.gguf --local-dir ./gguf
huggingface-cli download Qwen/Qwen2-VL-2B-Instruct-GGUF Qwen2-VL-2B-Instruct-Q8_0.gguf --local-dir ./gguf

# Download the multimodal projector (mmproj) model
huggingface-cli download Qwen/Qwen2-VL-2B-Instruct-GGUF Qwen2-VL-2B-Instruct-mmproj-f16.gguf --local-dir ./gguf
```

### Option B: Using `curl` / `wget`
Alternatively, download the files directly from the Hugging Face hub endpoints:
```bash
mkdir -p gguf

# Download IQ4_NL model
curl -L -o gguf/Qwen2-VL-2B-Instruct-IQ4_NL.gguf \
  "https://huggingface.co/Qwen/Qwen2-VL-2B-Instruct-GGUF/resolve/main/Qwen2-VL-2B-Instruct-IQ4_NL.gguf"

# Download Q4_0 model
curl -L -o gguf/Qwen2-VL-2B-Instruct-Q4_0.gguf \
  "https://huggingface.co/Qwen/Qwen2-VL-2B-Instruct-GGUF/resolve/main/Qwen2-VL-2B-Instruct-Q4_0.gguf"

# Download Q8_0 model
curl -L -o gguf/Qwen2-VL-2B-Instruct-Q8_0.gguf \
  "https://huggingface.co/Qwen/Qwen2-VL-2B-Instruct-GGUF/resolve/main/Qwen2-VL-2B-Instruct-Q8_0.gguf"

# Download fp16 mmproj model
curl -L -o gguf/mmproj-F16-Qwen2VL-2B.gguf \
  "https://huggingface.co/Qwen/Qwen2-VL-2B-Instruct-GGUF/resolve/main/Qwen2-VL-2B-Instruct-mmproj-f16.gguf"
```

---

## 3. Deploying to the Android Device

Use the **host machine's** native `adb` tool (since Docker doesn't map USB debugging bridges by default) to copy files to `/data/local/tmp/`.

```bash
# 1. Push the compiled llama.cpp package (binaries & libs)
adb push pkg-snapdragon/llama.cpp /data/local/tmp/

# 2. Create the gguf directory and push the models
adb shell "mkdir -p /data/local/tmp/gguf"
adb push gguf/Qwen2-VL-2B-Instruct-Q4_0.gguf /data/local/tmp/gguf/
adb push gguf/mmproj-F16-Qwen2VL-2B.gguf /data/local/tmp/gguf/

# 3. Push a sample test image (used as target for vision prompt)
adb push path/to/your/image.jpg /data/local/tmp/gguf/2.jpg
```

---

## 4. Execution Examples (CPU, GPU, and HTP)

To execute inference on the Snapdragon processor, use the environment variables `LD_LIBRARY_PATH` and `ADSP_LIBRARY_PATH` so the binaries can locate the OpenCL and Hexagon libraries in `lib/`.

Set up variables before running, or inline them with the command as shown below.

### A. HTP (Hexagon Tensor Processor) Execution
Full offloading of layers (`-ngl 99`) to the Hexagon NPU:
```bash
adb shell "cd /data/local/tmp/llama.cpp; \
ulimit -c unlimited; \
LD_LIBRARY_PATH=./lib \
ADSP_LIBRARY_PATH=./lib \
GGML_HEXAGON_EXPERIMENTAL=1 \
GGML_HEXAGON_NDEV=1 \
./bin/llama-mtmd-cli \
  --no-mmap \
  -m ../gguf/Qwen2-VL-2B-Instruct-Q4_0.gguf \
  --mmproj ../gguf/mmproj-F16-Qwen2VL-2B.gguf \
  --image ../gguf/2.jpg \
  --poll 1000 \
  -t 6 \
  --cpu-mask 0xfc \
  --cpu-strict 1 \
  --ctx-size 8192 \
  --ubatch-size 256 \
  -fa on \
  -ngl 99 \
  --device \"HTP0\" \
  -n 512 \
  -v \
  -p \"Describe this image in detail.\""
```

### B. GPU (OpenCL) Execution
Offloading to the Adreno GPU via OpenCL (`--device "GPUOpenCL"`):
```bash
adb shell "cd /data/local/tmp/llama.cpp; \
ulimit -c unlimited; \
LD_LIBRARY_PATH=./lib \
ADSP_LIBRARY_PATH=./lib \
GGML_HEXAGON_EXPERIMENTAL=1 \
./bin/llama-mtmd-cli \
  --no-mmap \
  -m ../gguf/Qwen2-VL-2B-Instruct-Q4_0.gguf \
  --mmproj ../gguf/mmproj-F16-Qwen2VL-2B.gguf \
  --image ../gguf/2.jpg \
  --poll 1000 \
  -t 6 \
  --cpu-mask 0xfc \
  --cpu-strict 1 \
  --ctx-size 8192 \
  --ubatch-size 256 \
  -fa on \
  -ngl 99 \
  --device \"GPUOpenCL\" \
  -n 512 \
  -v \
  -p \"Describe this image in detail.\""
```

### C. CPU Execution
Running on CPU threads with no offload (`-ngl 0`):
```bash
adb shell "cd /data/local/tmp/llama.cpp; \
ulimit -c unlimited; \
LD_LIBRARY_PATH=./lib \
ADSP_LIBRARY_PATH=./lib \
GGML_HEXAGON_EXPERIMENTAL=1 \
./bin/llama-mtmd-cli \
  --no-mmap \
  -m ../gguf/Qwen2-VL-2B-Instruct-Q4_0.gguf \
  --mmproj ../gguf/mmproj-F16-Qwen2VL-2B.gguf \
  --image ../gguf/2.jpg \
  --poll 1000 \
  -t 4 \
  --cpu-mask 0xfc \
  --cpu-strict 1 \
  --ctx-size 8192 \
  --ubatch-size 256 \
  -fa on \
  -ngl 0 \
  --device \"GPUOpenCL\" \
  -n 512 \
  -v \
  -p \"Describe this image in detail.\""
```
> [!TIP]
> If `--device "GPUOpenCL"` fails or hangs on CPU-only execution, omit the `--device` flag or set it to `--device none`.
