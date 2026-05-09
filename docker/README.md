# Docker Environment

This directory contains the Docker configuration for the **QAIRT/SNPE Toolchain**
— an Ubuntu 22.04-based container that provides the
[Qualcomm AI Runtime (QAIRT) SDK](https://www.qualcomm.com/developer/artificial-intelligence),
the Snapdragon Neural Processing Engine (SNPE) SDK, and a JupyterLab server for
running the model optimization notebooks.

---

## ⚠️ Architecture Requirements — Intel/AMD x86_64 Only

> **This Docker environment only works on Intel or AMD x86_64 processors.**
> **It is NOT compatible with Apple Silicon (M1, M2, M3, M4).**

### Why Apple Silicon is not supported

The Qualcomm AI Runtime (QAIRT) SDK is distributed **exclusively as
`x86_64-linux-clang` binaries**. This is reflected directly in the
`Dockerfile`:

```dockerfile
ENV LD_LIBRARY_PATH="${QAIRT_SDK_ROOT}/lib/x86_64-linux-clang"
ENV PATH="${QAIRT_SDK_ROOT}/bin/x86_64-linux-clang:${PATH}"
```

All QAIRT command-line tools (`qairt-converter`, `qairt-quantizer`,
`qairt-dlc-info`, `check-linux-dependency.sh`, `check-python-dependency`)
are compiled x86_64 ELF executables. Qualcomm does not provide an AArch64
Linux build of the SDK.

Apple Silicon Macs (M-series) run Docker on an **AArch64** kernel. While
Docker Desktop offers x86_64 emulation via **Rosetta 2**, this emulation is
not reliable for the QAIRT SDK: the dependency-checking scripts and SDK tools
fail or produce incorrect results under emulation. For this reason, Apple
Silicon hosts are **not supported**.

### Supported host architectures

| Architecture | Support |
|---|---|
| Intel / AMD x86_64 | ✅ Supported |
| Apple Silicon AArch64 (M1/M2/M3/M4) | ❌ Not supported |
| ARM64 Linux | ❌ Not supported |

---

## Tested Platforms

This environment has been built and tested on the following host operating
systems:

| Host OS | Architecture | Docker Runtime |
|---|---|---|
| **Linux Ubuntu 24.04 LTS** | x86_64 | Docker Engine |
| **Windows 11** | x86_64 | Docker Desktop |

---

## Contents

```
docker/
├── Dockerfile             # Image build definition (Ubuntu 22.04 + QAIRT SDK + JupyterLab)
├── docker-compose.yml     # Service configuration (ports, volumes, environment)
├── README.md              # This file
├── models/                # Model files — see models/README.md
└── notebooks/             # Jupyter Notebooks — see notebooks/README.md
```

---

## Prerequisites

- **Docker Engine** (Linux) or **Docker Desktop** (Windows) — [Install Docker](https://docs.docker.com/get-started/get-docker/)
- **Docker Compose** — included with Docker Desktop; for Linux follow the [Compose install guide](https://docs.docker.com/compose/install/)
- A host machine with an **Intel or AMD x86_64 processor** (see [Architecture Requirements](#️-architecture-requirements--intelamd-x86_64-only) above)
- At least **10 GB of free disk space** for the Docker image and downloaded assets

---

## Quick Start

All commands below should be run from the `docker/` directory.

```bash
# Build the image and start the container in the background
docker compose up --build -d
```

Once the container is running, open JupyterLab in your browser:

```
http://localhost:8888
```

No authentication token is required.

---

## ⏱️ First Build Time — Approximately 10 Minutes

> **The first build downloads the QAIRT SDK (~200 MB) and installs all
> dependencies. Expect it to take around 10 minutes** depending on your
> internet connection and hardware.

The breakdown below is based on a measured build log:

| Layer | Time | Description |
|---|---|---|
| Pull `ubuntu:22.04` | ~2 s | Download base image from Docker Hub |
| `apt update && upgrade` | ~7 s | Update package lists and upgrade installed packages |
| `apt install` curl, unzip | ~7 s | Install bootstrap tools |
| Download & unzip QAIRT SDK | ~94 s | Download SDK zip from Qualcomm Software Center and extract |
| `check-linux-dependency.sh` | ~94 s | Install all QAIRT system dependencies via apt |
| `apt install` build-essential, nodejs, npm, python3 | ~32 s | Install development tools |
| Create venv + `check-python-dependency` | ~119 s | Create Python virtual environment and install QAIRT Python deps |
| `pip install` JupyterLab + ML packages | ~94 s | Install project Python packages |
| Export and unpack image layers | ~158 s | Finalize and store the image locally |
| **Total** | **~609 s (~10 min)** | |

Subsequent starts (without `--build`) are immediate — the image is cached
locally and the container starts in under 1 second.

---

## Service Details

| Property | Value |
|---|---|
| Container name | `gn-qairt-toolchain` |
| Image name | `gn-qairt-toolchain` |
| Base image | `ubuntu:22.04` |
| QAIRT SDK version | `v2.41.0.251128` |
| Exposed port | `8888` (JupyterLab) |
| Working directory | `/workspace/notebooks` |
| Restart policy | `unless-stopped` |
| Timezone | `Europe/Copenhagen` |

---

## Volume Mounts

The container mounts the following directories from the host:

| Host path | Container path | Access | Description |
|---|---|---|---|
| `./models` | `/workspace/models` | Read/Write | Model files (`.pt`, `.onnx`, `.dlc`) |
| `./notebooks` | `/workspace/notebooks` | Read/Write | Jupyter Notebooks and generated data |
| `/etc/localtime` | `/etc/localtime` | Read-only | Host timezone data |
| `/etc/timezone` | `/etc/timezone` | Read-only | Host timezone identifier |

---

## Python Environment

The Python virtual environment is located at `/opt/qairt-venv` inside the
container. It is activated automatically via the `PATH` environment variable.

| Package | Version |
|---|---|
| `jupyterlab` | Latest available at build time |
| `libreyolo` | Latest available at build time |
| `onnx` | `1.16.0` |
| `onnxruntime` | `1.18.0` |
| `onnxscript` | `0.1.0` |
| `numpy` | `1.26.4` |

---

## Useful Commands

### Start the container

```bash
docker compose up -d
```

### Stop the container

```bash
docker compose down
```

### Rebuild the image and restart

```bash
docker compose down && docker compose build && docker compose up -d
```

### View container logs

```bash
docker compose logs -f
```

### Open a terminal inside the container

```bash
docker exec -it gn-qairt-toolchain /bin/bash
```

This drops you into a shell at `/workspace/notebooks` with the QAIRT SDK tools
(`qairt-converter`, `qairt-quantizer`, etc.) available on `PATH` and the Python
virtual environment activated.

### Remove the image and free disk space

```bash
docker compose down --rmi all --volumes
```

---

## Accessing JupyterLab

After the container starts, open the following URL in your browser:

```
http://localhost:8888
```

JupyterLab is configured with `--allow-root` and no authentication token
(`--IdentityProvider.token=''`), so no login is required. The notebooks in
`docker/notebooks/` are available immediately in the file browser.

---

## Further Reading

- [`models/README.md`](models/README.md) — model files, formats, and download instructions
- [`notebooks/README.md`](notebooks/README.md) — Jupyter Notebook documentation and pipeline details
