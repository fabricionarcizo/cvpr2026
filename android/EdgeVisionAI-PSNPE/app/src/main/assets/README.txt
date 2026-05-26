1. Required model file
   ====================

The quantised YOLOX-s model is NOT included in the repository due to its size.
Download it and place it in this directory before building the project.

File:
    LibreYOLOXs_int8.dlc   — INT8-quantised YOLOX-s model compiled for SNPE DSL

Download from Hugging Face:
    https://huggingface.co/fabricionarcizo/LibreYOLOXs

Notes:
  * The model is loaded at runtime directly from the app's assets.
  * Ensure the filename matches exactly: LibreYOLOXs_int8.dlc

2. Required model configuration file
   =====================================

The model configuration file is NOT included in the repository.
Download it and place it in this directory before building the project.

File:
    model_configs.json   — SNPE model configuration used at runtime by PsnpeModel

Download from Hugging Face:
    https://huggingface.co/fabricionarcizo/LibreYOLOXs

Notes:
  * The configuration is loaded at runtime directly from the app's assets.
  * Ensure the filename matches exactly: model_configs.json
