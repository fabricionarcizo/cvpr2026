# Default ProGuard rules for the PersonSense app.
#
# This file is intentionally minimal: debug builds don't ship through
# R8/proguard, and Hilt + Compose + AIDL + llama.cpp JNI all bring their own
# consumer rules.

-keepattributes Signature, *Annotation*, InnerClasses
