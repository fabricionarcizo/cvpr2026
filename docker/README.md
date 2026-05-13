# Docker Environment

This directory contains the Docker-based workspace used to optimize
**LibreYOLOXs** for Qualcomm(R) Snapdragon devices. The image bundles a local
**Qualcomm AI Runtime (QAIRT) / SNPE** toolchain and the Python dependencies
needed for the tracked notebooks:

- `notebooks/qairt_optimizer.ipynb` - local QAIRT conversion and INT8 quantization
- `notebooks/snpe_optimizer.ipynb` - local SNPE conversion, INT8 quantization, and DLC inspection
- `notebooks/qaihub_optimizer.ipynb` - cloud compilation, quantization, and profiling through QAI Hub

---

## Host requirements

### Local SDK workflows: x86_64 only

The Docker image is built around Qualcomm's `x86_64-linux-clang` SDK binaries:

```dockerfile
ENV LD_LIBRARY_PATH="${QAIRT_SDK_ROOT}/lib/x86_64-linux-clang"
ENV PATH="${QAIRT_SDK_ROOT}/bin/x86_64-linux-clang:${PATH}"
```

That means the local QAIRT and SNPE notebook workflows are supported only on
**Intel/AMD x86_64 Linux or Windows hosts running Docker**.

| Host architecture | QAIRT / SNPE notebooks |
|---|---|
| Intel / AMD x86_64 | Supported |
| Apple Silicon (M1/M2/M3/M4) | Not supported |
| ARM64 Linux | Not supported |

> QAI Hub itself is a cloud service, but this repository still runs its QAI Hub
> notebook inside the same Docker environment for reproducible preprocessing,
> ONNX export, and artifact management.

---

## Directory layout

```text
docker/
|- Dockerfile
|- docker-compose.yml
|- README.md
|- models/
|  `- README.md
`- notebooks/
   |- qaihub_optimizer.ipynb
   |- qairt_optimizer.ipynb
   |- snpe_optimizer.ipynb
   `- README.md
```

See:

- [`models/README.md`](models/README.md) for generated model artifacts and download details
- [`notebooks/README.md`](notebooks/README.md) for notebook-specific workflows

---

## What the image installs

### Base environment

| Component | Value |
|---|---|
| Base image | `ubuntu:22.04` |
| QAIRT SDK version | `2.41.0.251128` |
| JupyterLab port | `8888` |
| Working directory | `/workspace/notebooks` |
| Container name | `gn-qairt-toolchain` |

### Python packages installed in the image

| Package | Notes |
|---|---|
| `jupyterlab` | Notebook UI inside the container |
| `libreyolo` | Loads and exports the LibreYOLO model |
| `onnxsim` | ONNX graph simplification support |
| `qai-hub` | QAI Hub client for remote compile/profile jobs |
| `python-dotenv` | Reads notebook-local QAI Hub token configuration |
| `onnx==1.16.0` | ONNX export and inspection |
| `onnxruntime==1.18.0` | ONNX inference / validation |
| `onnxscript==0.1.0` | ONNX graph tooling support |
| `numpy==1.26.4` | Shared notebook preprocessing and calibration utilities |

---

## Quick start

Run all commands from `docker/`.

### 1. Build and start the container

```bash
docker compose up --build -d
```

### 2. Open JupyterLab

```text
http://localhost:8888
```

JupyterLab is started without an auth token by `docker-compose.yml`, so the
tracked notebooks are available immediately in the file browser.

### 3. Stop the container

```bash
docker compose down
```

---

## QAI Hub configuration

The `qaihub_optimizer.ipynb` notebook requires a QAI Hub API token. The tracked
example file is:

```text
docker/notebooks/.env .example
```

Copy it to `docker/notebooks/.env` and set:

```dotenv
QAI_HUB_API_TOKEN=your_api_token_here
```

The notebook reads that value with `python-dotenv` before creating QAI Hub
compile, quantize, and profile jobs.

---

## Volume mounts

| Host path | Container path | Purpose |
|---|---|---|
| `./models` | `/workspace/models` | Downloaded and generated model artifacts |
| `./notebooks` | `/workspace/notebooks` | Tracked notebooks plus generated notebook data |
| `/etc/localtime` | `/etc/localtime` | Host timezone data |
| `/etc/timezone` | `/etc/timezone` | Host timezone identifier |

---

## Common commands

### Start without rebuilding

```bash
docker compose up -d
```

### Rebuild and restart

```bash
docker compose down && docker compose build && docker compose up -d
```

### View logs

```bash
docker compose logs -f
```

### Open a shell inside the container

```bash
docker exec -it gn-qairt-toolchain /bin/bash
```

### Remove the image and mounted volumes

```bash
docker compose down --rmi all --volumes
```

---

## Notes on generated content

The repository tracks the notebooks and documentation, but most model binaries
and notebook-generated data are created locally when you run the workflows. The
current generated layout is documented in [`notebooks/README.md`](notebooks/README.md)
and [`models/README.md`](models/README.md).
