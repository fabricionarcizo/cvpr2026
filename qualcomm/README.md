# Android 13 Compilation and Installation Guide for Qualcomm RB3 Gen2 Vision Development Kit

## Overview

This document describes the complete process for synchronizing, compiling, and preparing Android 13 OS for the Qualcomm RB3 Gen2 Vision Development Kit (QCM6490/QCS6490 platform).

The guide follows a real working setup validated on:

- Ubuntu 20.04
- Qualcomm Android 13 BSP
- QCM6490 LA.QISI.15.0.r2 release
- Qualcomm RB3 Gen2 Vision Development Kit

This document is intended for research, Edge AI, and embedded systems development workflows.

---


> [!WARNING]
> Flashing Qualcomm devices using QDL and rawprogram XML files can overwrite
> critical boot and partition data. Incorrect firmware combinations, incompatible
> firehose programmers, or invalid partition layouts may soft-brick or hard-brick
> the device. Validate all files before flashing and keep backup images whenever possible.

# Hardware and Software Requirements

## Host Machine

Recommended:

- Ubuntu 20.04 LTS
- SSD storage
- 16 GB RAM minimum
- 32 GB RAM recommended
- 300+ GB free disk space

Tested configuration:

- Ubuntu 20.04.6 LTS
- 1TB SSD storage
- 32 GB RAM
- 64 GB swap
- Intel-based laptop

---

# Add Additional Swap (Recommended)

Android 13 Qualcomm builds are memory intensive.

If the machine has 16 GB RAM, additional swap is highly recommended.

Create a 64 GB swapfile:

```bash
sudo fallocate -l 64G /swapfile_android
sudo chmod 600 /swapfile_android
sudo mkswap /swapfile_android
sudo swapon /swapfile_android
```

Verify:

```bash
swapon --show
free -h
```

---

# Step 1 — Install Dependencies

Install required packages using APT:

```bash
sudo apt update

sudo apt install -y \
  git \
  git-core \
  gnupg \
  flex \
  bison \
  build-essential \
  zip \
  curl \
  zlib1g-dev \
  libc6-dev-i386 \
  libncurses5 \
  lib32ncurses5-dev \
  x11proto-core-dev \
  libx11-dev \
  lib32z1-dev \
  libgl1-mesa-dev \
  libxml2-utils \
  xsltproc \
  unzip \
  fontconfig \
  python3.8 \
  python2.7 \
  python-is-python3 \
  diffstat \
  xmlstarlet \
  texinfo \
  chrpath \
  libarchive-dev \
  ssh \
  libselinux1-dev \
  fakechroot \
  libiberty-dev \
  qemu-user-static \
  g++ \
  gawk \
  gcc \
  make \
  libwayland-dev \
  fakeroot \
  libpam0g-dev \
  openjdk-8-jdk-headless \
  binutils-dev \
  util-linux \
  uuid-dev \
  zstd \
  ccache \
  libxml-simple-perl \
  libxml-parser-perl \
  libxml-libxml-perl \
  libjson-perl \
  libswitch-perl \
  libssl-dev \
  openssl \
  libprotobuf-dev \
  protobuf-compiler
```

Install repo manually:

```bash
mkdir -p ~/.local/bin

curl https://storage.googleapis.com/git-repo-downloads/repo \
  > ~/.local/bin/repo

chmod a+x ~/.local/bin/repo
```

```bash
wget https://releases.linaro.org/archive/14.07/components/toolchain/binaries/gcc-linaro-aarch64-none-elf-4.9-2014.07_linux.tar.bz2
tar -jxvf gcc-linaro-aarch64-none-elf-4.9-2014.07_linux.tar.bz2
# Adicione o caminho extraído ao seu PATH exportado
export PATH=<caminho_do_gcc_linaro>/bin:$PATH
```

Prioritize Python 2.7.18:
```bash
sudo ln -sf /usr/bin/python2.7 /usr/local/bin/python
```

Add to the PATH:

```bash
echo 'export PATH=$HOME/.local/bin:$PATH' >> ~/.bashrc
source ~/.bashrc
```

---

# Step 2 — Git Configuration
```
git config --global user.name <YOUR_NAME>
git config --global user.email <YOUR_EMAIL>
git config --global credential.helper store
git config --global http.'https://chipmaster2.qti.qualcomm.com'.followRedirects "true"
```

---

# Step 3 — Download the Qualcomm BSP Package

Download the Android 13 BSP package from Qualcomm CodeLinaro:

Package used in this document:

```qcm6490
qcm6490-la-5-1_ap_standard_oem-r00014.1-b11237e034cb190c1589354ca9b93004a097fc8f.zip
```

Example source:

```text
https://code.qualcomm.com/qualcomm/qcm6490-la-5-1_ap_standard_oem/tree/r00014.1
```

---

# Step 4 — Extract the BSP

```bash
mkdir -p ~/qcm6490/{chipcode,qssi15,vendor,super}

unzip qcm6490-la-5-1_ap_standard_oem-r00014.1-b11237e034cb190c1589354ca9b93004a097fc8f.zip \
  -d ~/qcm6490/chipcode

cd ~/qcm6490/chipcode

mv qcm6490-la-5-1_ap_standard_oem-r00014.1-b11237e034cb190c1589354ca9b93004a097fc8f/* .

rm -rf qcm6490-la-5-1_ap_standard_oem-r00014.1-b11237e034cb190c1589354ca9b93004a097fc8f
```

---

# Step 5 — Navigate to Android 13 BSP

```bash
cd ~/qcm6490/chipcode/QCM6490_apps_qssi15/LINUX/android
```

---


> [!NOTE]
> This guide reflects a real working setup validated on the Qualcomm RB3 Gen2 Vision Kit.
> Some Qualcomm BSP components intentionally combine Android 13 QSSI with vendor/platform
> components derived from the lahaina ecosystem. Always validate compatibility with your
> exact BSP release and AU tags.

# Step 6 — Inspect the Release Descriptor

The Android BSP includes a release descriptor named:

```bash
snap_release.xml
```

Inspect it:

```bash
cat snap_release.xml
```

Relevant fields:

```xml
<?xml version='1.0' encoding='UTF-8'?>
<snap_release>
  <image au_tag="AU_LINUX_ANDROID_LA.QISI.15.0.R2.11.00.00.1343.016" hf_manifest_branch="NA" hf_manifest_git="NA" image_type="SYSTEM" oss_manifest_git="clo/la/la/system/manifest" oss_url="https://git.codelinaro.org" prebuilts_dir="system_prebuilt_dir" prop_url="https://qpm-git.qualcomm.com/home2/git" si_chipcode_path="LA.QISI.15.0/LINUX/android" software_image="LA.QISI.15.0.r2"/>
  <image image_type="combo" all="LA.QISI.15.0.r2"/>
</snap_release>
```

Important values extracted:

| Parameter | Value |
|---|---|
| AU Tag | AU_LINUX_ANDROID_LA.QISI.15.0.R2.11.00.00.1343.016 |
| Branch | LA.QISI.15.0.r2 |
| Manifest | clo/la/la/system/manifest |

---

# Step 7 — Synchronize Android Source Tree

Run the Qualcomm synchronization script.

IMPORTANT:

- Use `sync_snap_v2.sh`
- Do NOT use `sync.sh`
- Do NOT change the repo branch to `main`
- The default branch `aosp-new/stable` is correct

Synchronize the QSSI 15 tree (stand-alone) by running:

```bash
./sync_snap_v2.sh \
  --workspace_path=$HOME/qcm6490/qssi15 \
  --image_type=la \
  --tree_type=la_qssi \
  --prop_opt=chipcode \
  --common_oss_url=https://git.codelinaro.org \
  --qssi_oss_manifest_git=clo/la/la/system/manifest \
  --qssi_chipcode_path=$HOME/qcm6490/chipcode/QCM6490_apps_qssi15/LINUX/android \
  --qssi_au_tag=AU_LINUX_ANDROID_LA.QISI.15.0.R2.11.00.00.1343.016 \
  --repo_url=https://git.codelinaro.org/clo/tools/repo.git \
  --repo_branch=aosp-new/stable
```

Parameter explanation:

| Parameter                 | Value in your command                                      | Description                                                                    |
| ------------------------- | ---------------------------------------------------------- | ------------------------------------------------------------------------------ |
| `--workspace_path`        | `$HOME/qcm6490/qssi15`                                     | Destination workspace where the QSSI Android source tree will be synchronized  |
| `--image_type`            | `la`                                                       | Image/platform type (`la` = Linux Android)                                     |
| `--tree_type`             | `la_qssi`                                                  | Tree type to sync (`la_qssi` = Qualcomm QSSI tree for Linux Android)           |
| `--prop_opt`              | `chipcode`                                                 | Source of proprietary components (`chipcode` proprietary binaries and sources) |
| `--common_oss_url`        | `https://git.codelinaro.org`                               | Base Git server URL used for OSS repositories                                  |
| `--qssi_oss_manifest_git` | `clo/la/la/system/manifest`                                | QSSI OSS manifest repository path                                              |
| `--qssi_chipcode_path`    | `$HOME/qcm6490/chipcode/QCM6490_apps_qssi15/LINUX/android` | Local path to the Qualcomm QSSI chipcode Android source                        |
| `--qssi_au_tag`           | `AU_LINUX_ANDROID_LA.QISI.15.0.R2.11.00.00.1343.016`       | QSSI AU release tag/version to synchronize                                     |
| `--repo_url`              | `https://git.codelinaro.org/clo/tools/repo.git`            | Git repository used to obtain the `repo` tool                                  |
| `--repo_branch`           | `aosp-new/stable`                                          | Branch/version of the `repo` tool to use                                       |

---

# Step 8 — Configure Build Environment

Navigate to the workspace:

```bash
cd ~/qcm6490/qssi15
```

Enable ccache:

```bash
export USE_CCACHE=1
ccache -M 150G
```

Recommended environment variables:

```bash
export LC_ALL=C
ulimit -n 8192
```

```bash
export EXPERIMENTAL_USE_OPENJDK9=1.8
export ALLOW_MISSING_DEPENDENCIES=true
```

---

# Step 9 — Initialize Android Build Environment

```bash
source build/envsetup.sh
```

Expected output:

```text
including vendor/qcom/opensource/core-utils/vendorsetup.sh
including vendor/qcom/proprietary/common/vendorsetup.sh
including vendor/qcom/proprietary/prebuilt_HY11/vendorsetup.sh
Created 41 symlinks out of 72 mapped links..
```

---

# Step 10 — Select Build Target

Launch lunch:

```bash
lunch
```

Select:

```text
qssi-userdebug
```

Or directly:

```bash
lunch qssi-userdebug
```

---

# Step 11 — Build Android 13

Recommended for 32 GB RAM systems:

```bash
bash build.sh -j25 dist --qssi_only 2>&1 | tee qssi15_makelog.txt
```

Avoid:

```bash
bash build.sh -j$(nproc) dist --qssi_only 2>&1 | tee qssi15_build.log
```

because the build may run out of memory.

---

# Step 12 — Navigate to Android Vendor

```bash
cd ~/qcm6490/chipcode/LINUX/android
```

---

# Step 13 — Inspect the Release Descriptor

The Android BSP includes a release descriptor named:

```bash
snap_release.xml
```

Inspect it:

```bash
cat snap_release.xml
```

Relevant fields:

```xml
<?xml version='1.0' encoding='UTF-8'?>
<snap_release>
  <image au_tag="AU_LINUX_ANDROID_LA.QISI.11.0.R1.11.00.00.1315.044" hf_manifest_branch="SRC_History" hf_manifest_git="revision-history/qcm6490-la-4-1-la-qisi-11-0-r1_src_history_manifests.git" image_type="SYSTEM" oss_manifest_git="clo/la/la/system/manifest" oss_url="https://git.codelinaro.org" prebuilts_dir="system_prebuilt_dir" prop_url="https://qpm-git.qualcomm.com/home2/git" si_chipcode_path="LA.QISI.11.0/LINUX/android" software_image="LA.QISI.11.0.r1"/>
  <image au_tag="AU_LINUX_ANDROID_LA.UM.9.14.7.R1.11.00.00.1359.032" hf_manifest_branch="NA" hf_manifest_git="NA" image_type="LA_ANCHOR" oss_manifest_git="clo/la/la/vendor/manifest" oss_url="https://git.codelinaro.org" prebuilts_dir="vendor_prebuilt_dir" prop_url="https://qpm-git.qualcomm.com/home2/git" si_chipcode_path="LA.UM.9.14.7/LINUX/android" software_image="LA.UM.9.14.7.r1"/>
  <image image_type="combo" all="LA.QISI.11.0.r1 LA.UM.9.14.7.r1"/>
</snap_release>
```

Important values extracted:

| Parameter | Value |
|---|---|
| AU Tag | AU_LINUX_ANDROID_LA.QISI.11.0.R1.11.00.00.1315.044 |
| Branch | LA.QSSI.11.0.r1 |
| Manifest | clo/la/la/system/manifest |

---

# Step 14 — Synchronize Android Source Tree

Run the Qualcomm synchronization script.

IMPORTANT:

- Use `sync_snap_v2.sh`
- Do NOT use `sync.sh`
- Do NOT change the repo branch to `main`
- The default branch `aosp-new/stable` is correct

Synchronize the Vendor tree (stand-alone) by running:

```bash
./sync_snap_v2.sh \
  --workspace_path=$HOME/qcm6490/vendor \
  --image_type=la \
  --tree_type=la_vendor \
  --prop_opt=chipcode \
  --common_oss_url=https://git.codelinaro.org \
  --vendor_oss_manifest_git=clo/la/la/vendor/manifest \
  --vendor_chipcode_path=$HOME/qcm6490/chipcode/LINUX/android \
  --vendor_au_tag=AU_LINUX_ANDROID_LA.UM.9.14.7.R1.11.00.00.1359.032 \
  --qssi_chipcode_path=$HOME/qcm6490/chipcode/QCM6490_apps_qssi/LINUX/android \
  --repo_url=https://git.codelinaro.org/clo/tools/repo.git \
  --repo_branch=aosp-new/stable
```

Parameter explanation:

| Parameter                   | Value in your command                                    | Description                                                                    |
| --------------------------- | -------------------------------------------------------- | ------------------------------------------------------------------------------ |
| `--workspace_path`          | `$HOME/qcm6490/vendor`                                   | Destination workspace where the vendor source tree will be synchronized        |
| `--image_type`              | `la`                                                     | Image/platform type (`la` = Linux Android)                                     |
| `--tree_type`               | `la_vendor`                                              | Tree type to sync (`la_vendor` = Vendor tree for Linux Android)                |
| `--prop_opt`                | `chipcode`                                               | Source of proprietary components (`chipcode` proprietary binaries and sources) |
| `--common_oss_url`          | `https://git.codelinaro.org`                             | Base Git server URL used for OSS repositories                                  |
| `--vendor_oss_manifest_git` | `clo/la/la/vendor/manifest`                              | Vendor OSS manifest repository path                                            |
| `--vendor_chipcode_path`    | `$HOME/qcm6490/chipcode/LINUX/android`                   | Local path to the vendor chipcode Android source                               |
| `--vendor_au_tag`           | `AU_LINUX_ANDROID_LA.UM.9.14.7.R1.11.00.00.1359.032`     | Vendor AU release tag/version to synchronize                                   |
| `--qssi_chipcode_path`      | `$HOME/qcm6490/chipcode/QCM6490_apps_qssi/LINUX/android` | Local path to the QSSI chipcode source tree used by the vendor build           |
| `--repo_url`                | `https://git.codelinaro.org/clo/tools/repo.git`          | Git repository used to obtain the `repo` tool                                  |
| `--repo_branch`             | `aosp-new/stable`                                        | Branch/version of the `repo` tool to use                                       |

---

# Step 15 — Synchronize QSSI 15 inside the Vendor


Navigate to the workspace:

```bash
cd ~/qcm6490/vendor
```

Backup the current .repo:

```bash
mv .repo .repo.backup
```

Initialize the QSSI 15:

```bash
repo init \
  -u https://git.codelinaro.org/clo/la/la/system/manifest.git \
  -b release \
  -m AU_LINUX_ANDROID_LA.QISI.11.0.R1.11.00.00.1315.044.xml \
  --repo-url=https://git.codelinaro.org/clo/tools/repo.git \
  --repo-branch=aosp-new/stable \
  2>&1 | tee qss13r1_sync.txt
```

Synchronize it:

```bash
repo sync -q -c --no-tags -j25
```

Copy the proprietary content of QSSI 15 to Vendor:
```bash
cp -rfv \
  ~/qcm6490/chipcode/QCM6490_apps_qssi/LINUX/android/vendor/qcom/proprietary/* \
  vendor/qcom/proprietary/
```

---

# Step 16 — Initialize Android Build Environment

```bash
cd ~/qcm6490/vendor
```

Correct some missing links:
```bash
cd ~/qcm6490/vendor/vendor/qcom/proprietary/platform-boost/

ln -sf platform_boost_hal/platform_boost_product.mk platform_boost_product.mk

cd ~/qcm6490/vendor/vendor/qcom/proprietary/securemsm/

mkdir -p config
REAL_ACVP=$(find . -name acvp_vendor_proprietary_product.mk | head -n 1)
if [ ! -z "$REAL_ACVP" ]; then
    ln -sf ../$REAL_ACVP config/acvp_vendor_proprietary_product.mk
fi

cd ~/qcm6490/vendor/vendor/qcom/proprietary/securemsm/

mkdir -p config

touch config/acvp_vendor_proprietary_product.mk
```

Setup the environment variables:

```bash
source build/envsetup.sh
```

Expected output:

```text
including device/qcom/common/cuttlestone/vendorsetup.sh
including vendor/qcom/opensource/core-utils/vendorsetup.sh
including vendor/qcom/proprietary/common/vendorsetup.sh
including vendor/qcom/proprietary/prebuilt_HY11/vendorsetup.sh
*****Could not create symlink*******
vendor/qcom/proprietary/securemsm/config/acvp_vendor_proprietary_product.mk::vendor/qcom/defs/product-defs/vendor/acvp_vendor_proprietary_product.mk
vendor/qcom/proprietary/platform-boost/platform_boost_product.mk::vendor/qcom/defs/product-defs/vendor/platform_boost_product.mk
****************END******************
Created 110 symlinks out of 158 mapped links..
```

Set important environmental variables for compilation:

```bash
# Snapdragon LLVM
export LLVM_ARM_ROOT=/opt/qcom/Qualcomm_Snapdragon_LLVM_ARM_Toolchain_OEM
export LLVM_ARM_HOST_BIN=$LLVM_ARM_ROOT/bin

# Hexagon DSP for CDSP (SNPE / QNN)
export HEXAGON_ROOT=/opt/qcom/HEXAGON_Tools/8.4.10
export HEXAGON_TOOLS_ROOT=$HEXAGON_ROOT/Tools

# Hexagon DSP for ADSP (Audio / Sensors)
export HEXAGON_ROOT_ADSP=/opt/qcom/HEXAGON_Tools/8.4.07
export HEXAGON_TOOLS_ROOT_ADSP=$HEXAGON_ROOT_ADSP/Tools

# Ignore PATH restrictions to Qualcomm tools
export BUILD_BROKEN_USES_BUILD_COPY_HEADERS=true
export BUILD_BROKEN_DUP_RULES=true
export BUILD_BROKEN_ELF_PREBUILT_PRODUCT_COPY_FILES=true
export BUILD_BROKEN_OUTSIDE_INCLUDE_DIRS=true
export BUILD_BROKEN_NINJA_USES_ENV_VARS=true
export BUILD_BROKEN_VENDOR_PROPERTY_NAMESPACE=true
export BUILD_BROKEN_INCORRECT_PARTITION_IMAGES=true
export BUILD_BROKEN_PREBUILT_ELF_FILES=true
export BUILD_BROKEN_INPUT_DIR_MODULES=true
export BUILD_BROKEN_MISSING_REQUIRED_MODULES=true
export BUILD_BROKEN_DEPFILE=true
export BUILD_BROKEN_USES_NETWORK=true
export BUILD_BROKEN_MISSING_OUTPUTS=true
export BUILD_BROKEN_DISABLE_BAZEL=true
export TEMPORARY_DISABLE_PATH_RESTRICTIONS=true
```

---

# Step 17 — Select Build Target

Launch lunch:

```bash
lunch
```

Select:

```text
lahaina-userdebug
```

Or directly:

```bash
lunch lahaina-userdebug
```

---

# Step 18 - Bug corrections

Execute the following patches to correct some compilation issues:

```bash
patch ~/qcm6490/vendor/device/qcom/vendor-common/lights/Android.bp < ~/qcm6490/patches/qcom-light-hal-ndk-support.patch
patch ~/qcm6490/vendor/device/qcom/lahaina/system.prop < ~/qcm6490/patches/enable-adb-debug-properties.patch
patch ~/qcm6490/vendor/device/qcom/lahaina/init.target.rc < ~/qcm6490/patches/enable-adb-configfs-usb-gadget.patch
patch ~/qcm6490/vendor/kernel/msm-5.4/arch/arm64/configs/vendor/lahaina_QGKI.config < ~/qcm6490/patches/enable-dsp-fastrpc-debugfs.patch
patch ~/qcm6490/vendor/kernel/msm-5.4/arch/arm64/configs/vendor/lahaina_debug.config < ~/qcm6490/patches/enable-dsp-fastrpc-debug-support.patch
patch ~/qcm6490/vendor/kernel/msm-5.4/drivers/char/adsprpc.c < ~/qcm6490/patches/enable-cdsp-unsigned-pd-support.patch
```

# Step 19 — Build Android 13 Vendor

Recommended for 32 GB RAM systems:

```bash
bash build.sh -j25 dist --target_only 2>&1 | tee vendor_makelog.txt
```

Avoid:

```bash
bash build.sh -j$(nproc) dist --target_only 2>&1 | tee vendor_makelog.txt
```

because the build may run out of memory.

---

# Step 20 — Combine the Vendor & QSSI

Navigate to the workspace:

```bash
cd ~/qcm6490/qssi15
```

Run:

```bash
python vendor/qcom/opensource/core-utils/build/build_image_standalone.py \
  --image super \
  --qssi_build_path $HOME/qcm6490/qssi15 \
  --target_build_path $HOME/qcm6490/vendor \
  --merged_build_path $HOME/qcm6490/super \
  --target_lunch lahaina
```

---

# Step 21 - Generate NON-HLOS.bin

Create the final structure used by META build:

```bash
mkdir -p ~/qcm6490/chipcode/common/build/ufs/bin/asic
```

Create the final structure used by META build:

```bash
cp ~/qcm6490/chipcode/QCM6490_modem/modem_proc/build/ms/bin/kodiak.gps.prod/qdsp6sw.mbn \
   ~/qcm6490/chipcode/common/build/ufs/bin/asic/

cp ~/qcm6490/chipcode/aop_proc/build/ms/bin/AAAAANAZO/kodiak/aop.mbn \
   ~/qcm6490/chipcode/common/build/ufs/bin/asic/

cp ~/qcm6490/chipcode/trustzone_images/build/ms/bin/IAGAANAA/tz.mbn \
   ~/qcm6490/chipcode/common/build/ufs/bin/asic/

cp ~/qcm6490/chipcode/trustzone_images/build/ms/bin/IAGAANAA/hypvm.mbn \
   ~/qcm6490/chipcode/common/build/ufs/bin/asic/

cp ~/qcm6490/chipcode/trustzone_images/build/ms/bin/IAGAANAA/devcfg.mbn \
   ~/qcm6490/chipcode/common/build/ufs/bin/asic/
```

Generate NON-HLOS.bin manually:

```bash
cd ~/qcm6490/chipcode/common/build/ufs/bin/asic
cat qdsp6sw.mbn > NON-HLOS.bin
```

---

# Step 22 - Compile boot files

Correct some broken links:
```bash
sudo mkdir -p /pkg/qct/software/llvm/release/arm
sudo ln -sfn /opt/qcom/Snapdragon_SD_LLVM_ARM /pkg/qct/software/llvm/release/arm/10.0.3
```


Set the variables used by Snapdragon LLVM ARM 10.0.3 (install it via Qualcomm Package Manager 3):
```bash
export CLANG100LINUX_BIN=/pkg/qct/software/llvm/release/arm/10.0.3/bin/
export CLANG100LINUX_PREFIX=/pkg/qct/software/llvm/release/arm/10.0.3
```

Correct the Python version issues:
```bash
grep -RIl "tobytes()" \
~/qcm6490/chipcode/boot_images/edk2/BaseTools/Source/Python | \
xargs sed -i 's/\.tobytes()/\.tostring()/g'

grep -RIl "frombytes(" \
~/qcm6490/chipcode/boot_images/edk2/BaseTools/Source/Python | \
xargs sed -i 's/\.frombytes(/\.fromstring(/g'
```

Execute the following patch to correct some compilation issues:

```bash
patch ~/qcm6490/chipcode/boot_images/edk2/BaseTools/Conf/tools_def.template < ~/qcm6490/patches/fix-edk2-clang100-pointer-cast-build.patch
```

Navigate to the workspace:

```bash
cd ~/qcm6490/chipcode
```

Run:

```bash
python boot_images/boot_tools/buildex.py \
  -t kodiak,QcomToolsPkg \
  -v LAA \
  -r RELEASE
```

Set the final paths:

```bash
BOOT_DIR=~/qcm6490/chipcode/boot_images/boot/QcomPkg/SocPkg/Kodiak/Bin/LAA/RELEASE
DEST=~/qcm6490/chipcode/common/build/ufs/bin/asic
```

Copy the files:

```bash
cp $BOOT_DIR/xbl.elf $DEST/
cp $BOOT_DIR/xbl_config.elf $DEST/
cp $BOOT_DIR/prog_firehose_ddr.elf $DEST/
cp $BOOT_DIR/imagefv.elf $DEST/
cp $BOOT_DIR/shrm.elf $DEST/
```

---

# Step 23 - Compile partition files

Navigate to the workspace:

```bash
cd ~/qcm6490/chipcode
```

Create the output folder:

```bash
mkdir -p ~/qcm6490/config/ufs

cp ~/qcm6490/chipcode/common/config/ufs/partition_ext.xml \
   ~/qcm6490/config/ufs/

cp ~/qcm6490/chipcode/common/config/ufs/partition_ext_rfcomm.xml \
   ~/qcm6490/config/ufs/
```

Run:

```bash
./common/build/build.py --imf
```

---

# Step 24 — Create the Build Artifacts

After successful compilation:

```bash
mkdir -p ~/qcm6490/rb3_final_package
```

Define the final environmental variables:

```bash
FINAL=~/qcm6490/rb3_final_package
ASIC=~/qcm6490/chipcode/common/build/ufs/bin/asic
UFS=~/qcm6490/chipcode/ufs
LAHAINA=~/qcm6490/vendor/out/target/product/lahaina
SUPER=~/qcm6490/super/out/target/product/lahaina
```

Copy BOOT / Firehose / NON-HLOS:

```bash
cp $ASIC/aop.mbn $FINAL/
cp $ASIC/devcfg.mbn $FINAL/
cp $ASIC/hypvm.mbn $FINAL/
cp $ASIC/imagefv.elf $FINAL/
cp $ASIC/NON-HLOS.bin $FINAL/
cp $ASIC/prog_firehose_ddr.elf $FINAL/
cp $ASIC/qdsp6sw.mbn $FINAL/
cp $ASIC/shrm.elf $FINAL/
cp $ASIC/tz.mbn $FINAL/
cp $ASIC/xbl.elf $FINAL/
cp $ASIC/xbl_config.elf $FINAL/
```

Copy Android/HLOS:

```bash
cp $LAHAINA/boot.img $FINAL/
cp $LAHAINA/vendor_boot.img $FINAL/
cp $LAHAINA/vbmeta.img $FINAL/
cp $LAHAINA/dtbo.img $FINAL/
cp $LAHAINA/persist.img $FINAL/
cp $LAHAINA/userdata.img $FINAL/
cp $LAHAINA/abl.elf $FINAL/
cp $SUPER/super.img $FINAL/
cp $SUPER/vbmeta_system.img $FINAL/
```

Copy GPT/XML UFS:

```bash
cp $UFS/rawprogram0.xml $FINAL/
cp $UFS/rawprogram1.xml $FINAL/
cp $UFS/rawprogram2.xml $FINAL/
cp $UFS/rawprogram3.xml $FINAL/
cp $UFS/rawprogram4.xml $FINAL/
cp $UFS/rawprogram5.xml $FINAL/
cp $UFS/patch0.xml $FINAL/
cp $UFS/patch1.xml $FINAL/
cp $UFS/patch2.xml $FINAL/
cp $UFS/patch3.xml $FINAL/
cp $UFS/patch4.xml $FINAL/
cp $UFS/patch5.xml $FINAL/
```

Copy extra files:

```bash
cp ~/qcm6490/chipcode/common/build/bin/dspso.bin $FINAL/
cp ~/qcm6490/chipcode/qtee_tas/build/ms/bin/IAGAANAA/km41.mbn $FINAL/
cp ~/qcm6490/chipcode/common/core_qupv3fw/kodiak/qupv3fw.elf $FINAL/
cp ~/qcm6490/chipcode/qtee_tas/build/ms/bin/IAGAANAA/uefi_sec.mbn $FINAL/
cp ~/qcm6490/chipcode/cpucp_proc/kodiak/cpucp/cpucp.elf $FINAL/
cp ~/qcm6490/chipcode/qtee_tas/build/ms/bin/IAGAANAA/featenabler.mbn $FINAL/
cp ~/qcm6490/chipcode/LINUX/android/vendor/qcom/proprietary/prebuilt_HY11/target/product/lahaina/qweslicstore.bin $FINAL/
cp ~/qcm6490/chipcode/boot_images/boot/QcomPkg/Tools/binaries/logfs_ufs_8mb.bin $FINAL/
cp ~/qcm6490/chipcode/qtee_tas/build/ms/bin/IAGAANAA/storsec.mbn $FINAL/
cp ~/qcm6490/chipcode/trustzone_images/build/ms/bin/IAGAANAA/rtice.mbn $FINAL/
```

Copy GPT binaries:

```bash
find ~/qcm6490/chipcode/ufs -name "gpt*.bin" -exec cp {} $FINAL/ \;
```

---

# Step 25 — Flashing Android to Qualcomm RB3 Gen2

## Configure udev rule (IMPORTANT)

```bash
sudo nano /etc/udev/rules.d/51-qcom-usb.rules
```

Add the following rule:

```bash
SUBSYSTEMS=="usb", ATTRS{idVendor}=="05c6", ATTRS{idProduct}=="9008", MODE="0666"
```

Then, execute:

```bash
sudo systemctl restart udev
sudo udevadm control --reload-rules
```

## Stop ModemManager (VERY IMPORTANT)

```bash
sudo systemctl stop ModemManager
sudo systemctl disable ModemManager
```

## Install Qualcomm Device Loader (QDL)

```bash
cd /tmp

wget https://softwarecenter.qualcomm.com/api/download/software/tools/Qualcomm_Device_Loader/Linux/Debian/2.3.9.2/QDL_2.3.9.2_Linux_x64.zip

unzip QDL_2.3.9.2_Linux_x64.zip

cp QDL_2.3.9.2_Linux_x64/qdl ~/.local/bin/
chmod +x ~/.local/bin/qdl
qdl --version

rm -rf QDL_2.3.9.2_Linux_x64*
```

## Flash Images

To flash the CDT, run the following commands:

```bash
sudo ~/.local/bin/qdl prog_firehose_ddr.elf rawprogram*.xml patch*.xml
```

Turn off and turn on the device to enter in fastboot (or press vol- and plug the power cable) and run:

```bash
fastboot flash dsp dspso.bin
fastboot flash modem NON-HLOS.bin

fastboot set_active a

fastboot --disable-verity --disable-verification flash vbmeta vbmeta.img
fastboot --disable-verity --disable-verification flash vbmeta_system vbmeta_system.img

fastboot flash boot boot.img
fastboot flash vendor_boot vendor_boot.img
fastboot flash dtbo dtbo.img
fastboot flash super super.img

fastboot erase metadata
fastboot erase misc
fastboot erase userdata

fastboot reboot
```

P.S.: To update the OS, you can run first: `adb reboot bootloader`.

---

# Notes and Recommendations

- Qualcomm BSP builds are extremely storage intensive
- SSD storage is strongly recommended
- First builds may take several hours
- Incremental builds become significantly faster with ccache
- Qualcomm Android BSPs are sensitive to memory pressure
- Avoid very high `-j` values on 16 GB systems

---

# Useful Monitoring Commands

Monitor RAM:

```bash
watch -n 5 'free -h'
```

Monitor swap:

```bash
watch -n 5 'swapon --show'
```

Monitor CPU:

```bash
htop
```

---

# References

- Qualcomm RB3 Gen2 Vision Development Kit
- Qualcomm CodeLinaro
- Android Open Source Project (AOSP)
- Qualcomm QSSI Android 13 BSP

