Required llama.cpp + mtmd runtime libraries for arm64-v8a
==========================================================

Copy the following .so files from a pkg-snapdragon llama.cpp build (or any
llama.cpp build with the OpenCL + Hexagon backends enabled for Snapdragon)
into this directory before building the project. They are NOT included in
the repository.

Source path in pkg-snapdragon SDK:
    <PKG_SNAPDRAGON_ROOT>/llama.cpp/lib/

Required files (the LM + vision-projector + ggml backends):
    libllama.so            — llama.cpp text backbone
    libmtmd.so             — multimodal (image) wrapper
    libggml.so             — ggml core
    libggml-base.so        — ggml CPU/GPU shared scaffolding
    libggml-cpu.so         — ggml CPU backend with i8mm + FlashAttention
    libggml-hexagon.so     — Hexagon backend entry point (CPU side)
    libggml-htp-v68.so     — HTP V68 stub (Snapdragon QCM6490 / Fairphone 5)
    libggml-htp-v69.so     — HTP V69 stub
    libggml-htp-v73.so     — HTP V73 stub
    libggml-htp-v75.so     — HTP V75 stub
    libggml-htp-v79.so     — HTP V79 stub (Snapdragon 8 Elite / S25 Ultra)
    libggml-htp-v81.so     — HTP V81 stub

Optional (for full backend coverage):
    libggml-opencl.so      — OpenCL backend (Adreno GPU). The app is configured
                             CPU-only (-ngl 0 --no-mmproj-offload) so this is
                             not strictly required, but the AndroidManifest
                             declares libOpenCL.so as a dependency so include
                             it if you want to A/B-test the GPU path.

Notes:
  * libllama.so / libmtmd.so / libggml*.so are loaded by libai-chat.so (built
    by the project's CMake) via the linker. They are bundled into the APK and
    extracted to /data/app/.../lib/arm64-v8a/ on install.
  * libOpenCL.so is excluded from the APK and resolved at runtime against
    /vendor/lib64/libOpenCL.so on Qualcomm devices. See app/build.gradle.kts
    for the packaging.jniLibs.excludes rule.
  * libggml-htp-v*.so are Hexagon DSP6 ELFs (NOT arm64). The Android dynamic
    linker would refuse to dlopen them, but the FastRPC driver reads them
    from disk via the ADSP_LIBRARY_PATH environment variable instead. The
    `useLegacyPackaging = true` flag in app/build.gradle.kts is required so
    these libs end up as extracted files on disk rather than staying packed
    inside the APK.
  * These files are under the llama.cpp MIT license and Qualcomm's AI Stack
    license (for the Hexagon HTP stubs). Do not redistribute without
    compliance with those licenses.
