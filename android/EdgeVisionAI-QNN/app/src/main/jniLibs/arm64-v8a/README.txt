Required QNN runtime libraries for arm64-v8a (QAIRT SDK v2.46.0.260424)
=========================================================================

Copy the following .so files from your QAIRT SDK installation into this directory
before building the project. They are NOT included in the repository.

Source path in QAIRT SDK:
    <QAIRT_SDK_ROOT>/lib/aarch64-android/

Required files:
    libQnnHtp.so           — QNN HTP backend (main inference engine)
    libQnnHtpPrepare.so    — HTP preparation/offline caching helper
    libQnnHtpV73Stub.so    — HTP V73 stub for QCS6490 / RB3 Gen 2
    libQnnSystem.so        — QNN system interface (binary-info queries)

Optional (include for completeness / future devices):
    libQnnHtpV68Stub.so    — HTP V68 stub (SM8350)
    libQnnHtpV75Stub.so    — HTP V75 stub (SM8550)

Notes:
  * libQnnHtp.so and libQnnSystem.so are loaded via dlopen() at runtime from
    the app's native library directory — they do not need to be on the device's
    system partition.
  * libQnnHtpV73Stub.so is the unsigned stub required by the HTP V73 (QCS6490)
    secure DSP kernel. It must be present alongside libQnnHtp.so.
  * These files are subject to the Qualcomm AI Stack license agreement.
    Do not redistribute without compliance with that license.
