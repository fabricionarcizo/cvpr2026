# Dockerfile
#
# MIT License
#
# Copyright (c) 2026 Fabricio Batista Narcizo, Elizabete Munzlinger, Sai Narsi
# Reddy Donthi Reddy, Shan Ahmed Shaffi, and Zaheer Ahmed.
#
# Permission is hereby granted, free of charge, to any person obtaining a copy
# of this software and associated documentation files (the "Software"), to deal
# in the Software without restriction, including without limitation the rights
# to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
# copies of the Software, and to permit persons to whom the Software is
# furnished to do so, subject to the following conditions:
#
# The above copyright notice and this permission notice shall be included in
# all copies or substantial portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
# THE SOFTWARE.
#

# Sets the base image for the Docker build to Ubuntu 22.04.
FROM ubuntu:22.04

# Adds metadata to the Docker image.
LABEL maintainer="https://www.fabricionarcizo.com"
LABEL version="2.0"
LABEL description="Ubuntu 22.04 Docker image to be the base for QNN SDK"

# Updates the package lists, upgrades installed packages, and removes
# unnecessary packages to keep the image clean and up-to-date.
RUN \
    apt update && \
    apt upgrade -y && \
    apt full-upgrade -y && \
    apt autoremove -y

# Installs necessary packages for downloading and setting up the Qualcomm AI
#Runtime SDK.
RUN \
    apt install -y --no-install-recommends \
        ca-certificates \
        curl \
        unzip

# Environment variables for the Qualcomm AI Runtime SDK.
ENV SDK_URL="https://softwarecenter.qualcomm.com/api/download/software/sdks/Qualcomm_AI_Runtime_Community/All/2.41.0.251128/v2.41.0.251128.zip"
ENV SDK_DIR="/qairt"
ENV SDK_VERSION="2.41.0.251128"
ENV ZIP_FILE="${SDK_VERSION}.zip"
ENV QAIRT_SDK_ROOT="${SDK_DIR}/${SDK_VERSION}"
ENV PYTHONPATH="${QAIRT_SDK_ROOT}/lib/python"
ENV LD_LIBRARY_PATH="${QAIRT_SDK_ROOT}/lib/x86_64-linux-clang"
ENV PATH="${QAIRT_SDK_ROOT}/bin/x86_64-linux-clang:${PATH}"

# Installs necessary dependencies for downloading and setting up the SDK.
RUN set -e; \
    if [ -d "${QAIRT_SDK_ROOT}" ]; then \
        echo "SDK version ${SDK_VERSION} already exists in ${SDK_DIR}."; \
    else \
        echo "Downloading Qualcomm Neural Processing SDK for AI..."; \
        curl -L -o "${ZIP_FILE}" "${SDK_URL}"; \
        echo "Unzipping SDK..."; \
        unzip -q "${ZIP_FILE}"; \
        rm -f "${ZIP_FILE}"; \
    fi

# Checks for the necessary dependencies required by the Qualcomm AI Runtime SDK.
RUN \
    "${QAIRT_SDK_ROOT}/bin/check-linux-dependency.sh"

# Installs necessary packages for running JupyterLab and Python development.
RUN \
    apt install -y --no-install-recommends \
        build-essential \
        nodejs \
        npm \
        python3 \
        python3-dev \
        python3-pip \
        python3-venv

# Sets environment variables for the Python virtual environment and updates the
# PATH to include the virtual environment's bin directory.
ENV VIRTUAL_ENV=/opt/qairt-venv
ENV PATH="$VIRTUAL_ENV/bin:$PATH"

# Creates a Python virtual environment, upgrades pip, activates the virtual
# environment, and checks for the necessary Python dependencies required by the
# Qualcomm AI Runtime SDK, including optional dependencies.
RUN \
    python3 -m venv ${VIRTUAL_ENV} && \
    ${VIRTUAL_ENV}/bin/pip install --no-cache-dir --upgrade pip && \
    . ${VIRTUAL_ENV}/bin/activate && \
    "${QAIRT_SDK_ROOT}/bin/check-python-dependency" --with_optional

# Installs the necessary Python packages for the project using pip.
RUN \
    ${VIRTUAL_ENV}/bin/pip install --no-cache-dir \
        jupyterlab \
        libreyolo \
        onnx==1.16.0 \
        onnxruntime==1.18.0 \
        onnxscript==0.1.0 \
        numpy==1.26.4

# Sets the default command to keep the container running indefinitely.
CMD ["tail", "-f", "/dev/null"]
