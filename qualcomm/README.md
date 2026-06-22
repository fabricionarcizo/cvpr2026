# Android 15 Compilation and Installation Guide for Qualcomm RB3 Gen2 Vision Development Kit

## Overview

This document describes the complete process for synchronizing, compiling, and preparing Android 15 OS for the Qualcomm RB3 Gen2 Vision Development Kit (QCM6490/QCS6490 platform).

The guide follows a real working setup validated on:

- Ubuntu 20.04
- Qualcomm Android 15 BSP
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

Android 15 Qualcomm builds are memory intensive.

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
mkdir -p $HOME/.local/bin

curl https://storage.googleapis.com/git-repo-downloads/repo \
  > $HOME/.local/bin/repo

chmod a+x $HOME/.local/bin/repo
```

Add to the PATH:

```bash
echo 'export PATH=$HOME/.local/bin:$PATH' >> $HOME/.bashrc
source $HOME/.bashrc
```

Install Arm GNU Toolchain manually:

```bash
wget hhttps://developer.arm.com/-/media/Files/downloads/gnu/15.2.rel1/binrel/arm-gnu-toolchain-15.2.rel1-x86_64-aarch64-none-elf.tar.xz
tar xf arm-gnu-toolchain-15.2.rel1-x86_64-aarch64-none-elf.tar.xz -C $HOME/.local
```

Add to the PATH:

```bash
echo 'export PATH=$HOME/.local/arm-gnu-toolchain-15.2.rel1-x86_64-aarch64-none-elf/bin:$PATH' >> $HOME/.bashrc
source $HOME/.bashrc
```

Prioritize Python 2.7.18:
```bash
sudo ln -sf /usr/bin/python2.7 /usr/local/bin/python
```

---

# Step 2 — Git Configuration

Edit the file `$HOME/.gitconfig` with the following content:

```
[user]
	name = <YOUR_NAME>
	email = <YOUR_EMAIL>
[credential]
	helper = store
[http "https://chipmaster2.qti.qualcomm.com"]
	followRedirects = true
[http "https://qpm-git.qualcomm.com"]
	followRedirects = true
[core]
	compression = 0
	symlinks = true
[color]
	ui = auto
[http]
	followRedirects = true
	postBuffer = 1048576000
	maxRequestBuffer = 1048576000
[https]
	postBuffer = 1048576000
```

---

# Step 3 — Download the Qualcomm BSP Package

Download the Android 15 BSP package from Qualcomm CodeLinaro:

```text
https://code.qualcomm.com/qualcomm/qcm6490-la-5-1_ap_standard_oem/tree/r00014.1
```

---

# Step 4 — Extract the BSP

```bash
mkdir -p $HOME/qcm6490/{ssi15,vendor}
cd $HOME/qcm6490

git clone -b r00014.1 --depth 1 https://qpm-git.qualcomm.com/home2/git/qualcomm/qcm6490-la-5-1_ap_standard_oem.git $HOME/qcm6490/chipcode
cd $HOME/qcm6490/chipcode
```

---

# Step 5 — Navigate to Android 15 BSP

```bash
cd $HOME/qcm6490/chipcode/QCM6490_apps_qssi15/LINUX/android
```

---


> [!NOTE]
> This guide reflects a real working setup validated on the Qualcomm RB3 Gen2 Vision Kit.
> Some Qualcomm BSP components intentionally combine Android 15 QSSI with vendor/platform
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

Synchronize the QSSI 15 tree (stand-alone) by running:

```bash
cd $HOME/qcm6490/chipcode/LINUX/android
./sync_snap.sh \
  -D $HOME/qcm6490/qssi15 \
  -T qt \
  -R ch \
  -a AU_LINUX_ANDROID_LA.QISI.15.0.R2.11.00.00.1343.016 \
  -c clo/la/la/system/manifest \
  -l https://git.codelinaro.org \
  -h $HOME/qcm6490/chipcode/QCM6490_apps_qssi15/LINUX/android
```

Parameter explanation:

| Parameter | Value in your command                                      | Description                                                              |
| --------- | ---------------------------------------------------------- | ------------------------------------------------------------------------ |
| `-D`      | `$HOME/qcm6490/qssi15`                                     | Destination workspace where the Android source tree will be synchronized |
| `-T`      | `qt`                                                       | Tree type (`qt` = QSSI Tree)                                             |
| `-R`      | `ch`                                                       | Source of proprietary components (`ch` = chipcode)                       |
| `-a`      | `AU_LINUX_ANDROID_LA.QISI.15.0.R2.11.00.00.1343.016`       | QSSI AU release tag/version to synchronize                               |
| `-c`      | `clo/la/la/system/manifest`                                | QSSI manifest repository path                                            |
| `-l`      | `https://git.codelinaro.org`                               | Git server URL hosting the manifest repositories                         |
| `-h`      | `$HOME/qcm6490/chipcode/QCM6490_apps_qssi15/LINUX/android` | Local path to the QSSI chipcode Android source tree                      |

---

# Step 8 — Configure Build Environment

Navigate to the workspace:

```bash
cd $HOME/qcm6490/qssi15
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

# Step 11 — Build Android 15

Recommended for 32 GB RAM systems:

```bash
bash build.sh -j25 dist --qssi_only EXPERIMENTAL_USE_OPENJDK9=1.8 2>&1 | tee qssi15_makelog.txt
```

Avoid:

```bash
bash build.sh -j$(nproc) dist --qssi_only EXPERIMENTAL_USE_OPENJDK9=1.8 2>&1 | tee qssi15_build.log
```

because the build may run out of memory.

---

# Step 12 — Navigate to Android Vendor

```bash
cd $HOME/qcm6490/chipcode/LINUX/android
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

Synchronize the Vendor tree (stand-alone) by running:

```bash
cd $HOME/qcm6490/chipcode/LINUX/android
./sync_snap.sh \
  -D $HOME/qcm6490/vendor \
  -T sg \
  -R ch \
  -A AU_LINUX_ANDROID_LA.UM.9.14.7.R1.11.00.00.1359.032 \
  -C clo/la/la/vendor/manifest \
  -L https://git.codelinaro.org \
  -H $HOME/qcm6490/chipcode/LINUX/android \
  -h $HOME/qcm6490/chipcode/QCM6490_apps_qssi/LINUX/android \
  -c clo/la/la/system/manifest \
  -l https://git.codelinaro.org \
  -n qc-stable
```

Parameter explanation:

| Parameter | Value in your command                                    | Description                                                                        |
| --------- | -------------------------------------------------------- | ---------------------------------------------------------------------------------- |
| `-D`      | `$HOME/qcm6490/vendor`                                   | Destination workspace where the vendor source tree will be synchronized            |
| `-T`      | `sg`                                                     | Tree type (`sg` = Vendor/System Integration tree)                                  |
| `-R`      | `ch`                                                     | Source of proprietary components (`ch` = chipcode)                                 |
| `-A`      | `AU_LINUX_ANDROID_LA.UM.9.14.7.R1.11.00.00.1359.032`     | Vendor AU release tag/version to synchronize                                       |
| `-C`      | `clo/la/la/vendor/manifest`                              | Vendor manifest repository path                                                    |
| `-L`      | `https://git.codelinaro.org`                             | Git server URL hosting the vendor manifest repository                              |
| `-H`      | `$HOME/qcm6490/chipcode/LINUX/android`                   | Local path to the vendor chipcode Android source tree                              |
| `-h`      | `$HOME/qcm6490/chipcode/QCM6490_apps_qssi/LINUX/android` | Local path to the QSSI chipcode Android source tree used during vendor integration |
| `-c`      | `clo/la/la/system/manifest`                              | QSSI/system manifest repository path                                               |
| `-l`      | `https://git.codelinaro.org`                             | Git server URL hosting the QSSI/system manifest repository                         |
| `-n`      | `qc-stable`                                              | Repository branch/profile used by the synchronization script                       |

---

# Step 15 — Initialize Android Build Environment

```bash
cd $HOME/qcm6490/vendor
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

---

# Step 16 — Select Build Target

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

# Step 17 — Build Android 15 Vendor

Recommended for 32 GB RAM systems:

```bash
bash build.sh -j25 dist --target_only EXPERIMENTAL_USE_OPENJDK9=1.8 2>&1 | tee vendor_makelog.txt
```

Avoid:

```bash
bash build.sh -j$(nproc) dist --target_only EXPERIMENTAL_USE_OPENJDK9=1.8 2>&1 | tee vendor_makelog.txt
```

because the build may run out of memory.

---

# Step 18 — Combine the Vendor & QSSI

Navigate to the workspace:

```bash
cd $HOME/qcm6490/vendor
```

Run:

```bash
python vendor/qcom/opensource/core-utils/build/build_image_standalone.py \
  --image super \
  --qssi_build_path $HOME/qcm6490/qssi15 \
  --target_build_path $HOME/qcm6490/vendor \
  --merged_build_path $HOME/qcm6490/vendor \
  --target_lunch lahaina
```

Create a symbolic link to the artefacts in the meta tree:
```bash
mkdir -p $HOME/qcm6490/chipcode/LINUX/android/out/target/product
ln -s $HOME/qcm6490/vendor/out/target/product/lahaina \
  $HOME/qcm6490/chipcode/LINUX/android/out/target/product
```

---

# Step 19 - Generate NON-HLOS.bin

Create an environment variable to the sectools:
```bash
export SECTOOLS=$HOME/qcm6490/chipcode/common/sectools
export SECTOOLS_DIR=$HOME/qcm6490/chipcode/common/sectools
```

Create a symbolic link to Snapdragon® LLVM Toolchain for Arm® Technology (Archive) v10.0.3:
```bash
sudo mkdir -p /pkg/qct/software/llvm/release/arm
sudo ln -s /opt/qcom/Snapdragon_SD_LLVM_ARM /pkg/qct/software/llvm/release/arm/10.0.3
```

Build the Qualcomm boot-firmware components and flashing tools for the QCM6490/RB3 Gen 2 platform:
```bash
cd $HOME/qcm6490/chipcode
python boot_images/boot_tools/buildex.py -t kodiak,QcomToolsPkg -v LAA -r RELEASE
```

## 19.1 AOP — LLVM 4.0.12, Python 2.7.6

```bash
cd <target_root>/aop_proc
./build_kodiak.sh
```

Create a symbolic link to Snapdragon® LLVM Toolchain for Arm® Technology (Archive) v4.0.12:
```bash
sudo ln -s /opt/qcom/Qualcomm_Snapdragon_LLVM_ARM_Toolchain_OEM/4.0.12.1 /pkg/qct/software/llvm/release/arm/4.0.12
sudo chmod 777 -R /opt/qcom/Qualcomm_Snapdragon_LLVM_ARM_Toolchain_OEM/4.0.12.1/
export SD_LLVM_ROOT=/opt/qcom/Qualcomm_Snapdragon_LLVM_ARM_Toolchain_OEM/4.0.12.1
```

Build the AOP firmware for the Qualcomm Kodiak platform:
```bash
cd $HOME/qcm6490/chipcode/aop_proc/build
./build_kodiak.sh
```

## 19.2 TZ e Hypervisor — LLVM 10.0.9, Python 2.7.17 (somente Linux)

Create a symbolic link to Snapdragon® LLVM Toolchain for Arm® Technology (Archive) v10.0.9:
```bash
sudo ln -s /opt/qcom/SnapdragonLLVMARM/10.0.9.0 /pkg/qct/software/llvm/release/arm/10.0.9
export LLVMBIN=/opt/qcom/SnapdragonLLVMARM/10.0.9.0
sudo chmod 777 -R /pkg/qct/software/llvm/release/arm/10.0.9/
```

Correct a Python 2.7 issue:
```bash
sudo mkdir -p /pkg/qct/software/python/2.7/bin
sudo ln -sfn \
  "$(command -v python2.7)" \
  /pkg/qct/software/python/2.7/bin/python
```

Correct the Linaro Toolchain symbolic links:
```bash
sudo mkdir -p /pkg/qct/software/arm/linaro-toolchain/aarch64-none-elf
sudo ln -s /home/fabricio/.local/arm-gnu-toolchain-15.2.rel1-x86_64-aarch64-none-elf /pkg/qct/software/arm/linaro-toolchain/aarch64-none-elf/4.9-2014.07
```

Build the Qualcomm TrustZone and secure-firmware package for the Kodiak/QCM6490 platform:
```bash
cd $HOME/qcm6490/chipcode/trustzone_images/build/ms
python build_all.py -b TZ.XF.5.0 CHIPSET=kodiak
```

## 19.3 ADSP — Hexagon 8.4.07, Python 2.7.6

Export the Snapdragon Hexagon path:
```bash
export HEXAGON_ROOT=/opt/qcom/HEXAGON_Tools
```

Install nanopb dependency:
```bash
wget https://jpa.kapsi.fi/nanopb/download/nanopb-0.3.9.5-linux-x86.tar.gz
mv nanopb-0.3.9.5-linux-x86.tar.gz $HOME/qcm6490/chipcode/adsp_proc/ssc_api
cd $HOME/qcm6490/chipcode/adsp_proc
python ssc_api/build/config_nanopb_dependency.py -f nanopb-0.3.9.5-linux-x86
```

Builds the ADSP firmware for the Qualcomm Kodiak/QCM6490 platform:
```bash
cd build/ms
python ./build_variant.py kodiak.adsp.prod
```

### 19.4 cDSP (Compute DSP / HTP)

Builds the Compute DSP firmware for the Qualcomm Kodiak/QCM6490 platform:
```bash
cd $HOME/qcm6490/chipcode/cdsp_proc/build/ms
python ./build_variant.py kodiak.cdsp.prod
```

### 19.5 META build

For RB3 Vision Kit with **QCS/HSP (GPS)** variant:

```bash
cd $HOME/qcm6490/chipcode
cp contents.xml{,.bak}
cp common/config/contents_QCS.xml contents.xml

cd common/build
python build.py --imf
```

---

# Step 20 - Copy boot files

Define the final environmental variables:

```bash
BOOT_DIR=$HOME/qcm6490/chipcode/boot_images/boot/QcomPkg/SocPkg/Kodiak/Bin/LAA/RELEASE
LAHAINA=$HOME/qcm6490/chipcode/LINUX/android/out/target/product/lahaina
ASIC=$HOME/qcm6490/chipcode/common/build/ufs/bin/asic
UFS=$HOME/qcm6490/chipcode/ufs
```

Copy the files:

```bash
cp $BOOT_DIR/xbl.elf $LAHAINA/
cp $BOOT_DIR/xbl_config.elf $LAHAINA/
cp $BOOT_DIR/prog_firehose_ddr.elf $LAHAINA/
cp $BOOT_DIR/imagefv.elf $LAHAINA/
cp $BOOT_DIR/shrm.elf $LAHAINA/
```

---

# Step 21 - Compile partition files

Navigate to the workspace:

```bash
cd $HOME/qcm6490/chipcode
```

Create the output folder:

```bash
mkdir -p $HOME/qcm6490/config/ufs

cp $HOME/qcm6490/chipcode/common/config/ufs/partition_ext.xml \
   $HOME/qcm6490/config/ufs/

cp $HOME/qcm6490/chipcode/common/config/ufs/partition_ext_rfcomm.xml \
   $HOME/qcm6490/config/ufs/
```

Run:

```bash
./common/build/build.py --imf
```

---

# Step 24 — Copy the Build Artifacts


Copy BOOT / Firehose / NON-HLOS:

```bash
cp $HOME/qcm6490/chipcode/aop_proc/build/ms/bin/AAAAANAZO/kodiak/aop.mbn $LAHAINA
cp $HOME/qcm6490/chipcode/trustzone_images/build/ms/bin/IAGAANAA/tz.mbn $LAHAINA
cp $HOME/qcm6490/chipcode/trustzone_images/build/ms/bin/IAGAANAA/hypvm.mbn $LAHAINA
cp $HOME/qcm6490/chipcode/trustzone_images/build/ms/bin/IAGAANAA/devcfg.mbn $LAHAINA
cp $HOME/qcm6490/chipcode/common/build/ufs/bin/asic/NON-HLOS.bin $LAHAINA
```

Copy GPT/XML UFS:

```bash
cp $UFS/rawprogram0.xml $LAHAINA
cp $UFS/rawprogram1.xml $LAHAINA
cp $UFS/rawprogram2.xml $LAHAINA
cp $UFS/rawprogram3.xml $LAHAINA
cp $UFS/rawprogram4.xml $LAHAINA
cp $UFS/rawprogram5.xml $LAHAINA
cp $UFS/patch0.xml $LAHAINA
cp $UFS/patch1.xml $LAHAINA
cp $UFS/patch2.xml $LAHAINA
cp $UFS/patch3.xml $LAHAINA
cp $UFS/patch4.xml $LAHAINA
cp $UFS/patch5.xml $LAHAINA
```

Copy extra files:

```bash
cp $HOME/qcm6490/chipcode/common/build/bin/dspso.bin $LAHAINA
cp $HOME/qcm6490/chipcode/qtee_tas/build/ms/bin/IAGAANAA/km41.mbn $LAHAINA
cp $HOME/qcm6490/chipcode/common/core_qupv3fw/kodiak/qupv3fw.elf $LAHAINA
cp $HOME/qcm6490/chipcode/qtee_tas/build/ms/bin/IAGAANAA/uefi_sec.mbn $LAHAINA
cp $HOME/qcm6490/chipcode/cpucp_proc/kodiak/cpucp/cpucp.elf $LAHAINA
cp $HOME/qcm6490/chipcode/qtee_tas/build/ms/bin/IAGAANAA/featenabler.mbn $LAHAINA
cp $HOME/qcm6490/chipcode/LINUX/android/vendor/qcom/proprietary/prebuilt_HY11/target/product/lahaina/qweslicstore.bin $LAHAINA
cp $HOME/qcm6490/chipcode/boot_images/boot/QcomPkg/Tools/binaries/logfs_ufs_8mb.bin $LAHAINA
cp $HOME/qcm6490/chipcode/qtee_tas/build/ms/bin/IAGAANAA/storsec.mbn $LAHAINA
cp $HOME/qcm6490/chipcode/trustzone_images/build/ms/bin/IAGAANAA/rtice.mbn $LAHAINA
cp $HOME/qcm6490/chipcode/common/build/ufs/bin/BTFM.bin $LAHAINA
cp $HOME/qcm6490/chipcode/common/build/bin/multi_image.mbn $LAHAINA
cp $HOME/qcm6490/chipcode/common/build/bin/apdp/apdp.mbn $LAHAINA
```

Copy GPT binaries:

```bash
find $HOME/qcm6490/chipcode/ufs -name "gpt*.bin" -exec cp {} $LAHAINA/ \;
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
- Qualcomm QSSI Android 15 BSP

