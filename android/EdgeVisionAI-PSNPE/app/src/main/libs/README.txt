1. Required SNPE runtime library
   =============================

This directory must contain the following file before building the project.
It is NOT included in the repository.

Required file:
    snpe-release.aar  — Qualcomm SNPE Android runtime (AAR package)

How to obtain it:
    Download the Qualcomm SNPE SDK from the Qualcomm AI Hub:
        https://qpm.qualcomm.com/

    Locate the AAR inside the SDK archive:
        <SNPE_SDK_ROOT>/android/snpe-release.aar

    Copy it into this directory so the path matches:
        app/src/main/libs/snpe-release.aar

Build integration:
    The library is referenced as a local-file dependency in app/build.gradle.kts:
        implementation(files("src/main/libs/snpe-release.aar"))

Notes:
  * snpe-release.aar is subject to the Qualcomm SNPE license agreement.
    Do not redistribute without compliance with that license.

2. Required PSNPE runtime library
   ================================

This directory must contain the following file before building the project.
It is NOT included in the repository.

Required file:
    psnpe-release.aar  — Qualcomm Parallel SNPE Android runtime (AAR package)

How to obtain it:
    Download the Qualcomm SNPE SDK from the Qualcomm AI Hub:
        https://qpm.qualcomm.com/

    Locate the AAR inside the SDK archive:
        <SNPE_SDK_ROOT>/android/psnpe-release.aar

    Copy it into this directory so the path matches:
        app/src/main/libs/psnpe-release.aar

Build integration:
    The library is referenced as a local-file dependency in app/build.gradle.kts:
        implementation(files("src/main/libs/psnpe-release.aar"))

Notes:
  * psnpe-release.aar is subject to the Qualcomm SNPE license agreement.
    Do not redistribute without compliance with that license.
