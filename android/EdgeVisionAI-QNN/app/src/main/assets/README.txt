1. Required DSP skeleton library (QAIRT SDK v2.46.0.260424)
   ==========================================================

Copy the following file from your QAIRT SDK installation into this directory
before building the project. It is NOT included in the repository.

Source path in QAIRT SDK:
    <QAIRT_SDK_ROOT>/lib/hexagon-v68/unsigned/

Required file:
    libQnnHtpV68Skel.so    — HTP V68 DSP skeleton (unsigned) for QCS6490 / RB3 Gen 2

Notes:
  * This is the unsigned DSP skeleton library that runs on the Hexagon V68 DSP.
    It must be placed in this assets directory so the app can push it to the
    device at runtime.
  * This file is subject to the Qualcomm AI Stack license agreement.
    Do not redistribute without compliance with that license.

2. Required model file
   ====================

The quantised YOLOX-s model is NOT included in the repository due to its size.
Download it and place it in this directory before building the project.

File:
    LibreYOLOXs_int8.bin   — INT8-quantised YOLOX-s model compiled for QNN HTP

Download from Hugging Face:
    https://huggingface.co/fabricionarcizo/LibreYOLOXs

Notes:
  * The model is loaded at runtime directly from the app's assets.
  * Ensure the filename matches exactly: LibreYOLOXs_int8.bin
