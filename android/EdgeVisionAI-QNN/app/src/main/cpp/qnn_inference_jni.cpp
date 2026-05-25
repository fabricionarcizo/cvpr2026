/*
 * MIT License
 *
 * Copyright (c) 2026 Elizabete Munzlinger and Fabricio Batista Narcizo
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

/**
 * qnn_inference_jni.cpp  —  v5 (prime graphExecute inside nativeInit)
 *
 * History of fixes:
 *   v1: SIGBUS — flat QnnFunctionPointers struct assumed offset 0 = backendCreate.
 *       Real QnnInterface_t has {backendId[4+4pad], providerName*[8],
 *       Qnn_ApiVersion_t[24]} = 40 bytes before the impl struct.
 *
 *   v2: All dlsym() failed — libQnnHtp.so exports only QnnInterface_getProviders.
 *       Individual API names are NOT exported as plain C symbols.
 *
 *   v3: Wrong RTLD flags and wrong libQnnSystem.so access pattern.
 *       - libQnnHtp.so loaded RTLD_LOCAL (should be RTLD_GLOBAL per Qualcomm SampleApp).
 *       - libQnnSystem.so accessed via individual dlsym (same issue as v2 — it also
 *         uses QnnSystemInterface_getProviders, not individual exports).
 *       - libQnnHtpPrepare.so not loaded before libQnnHtp.so (required on QCS6490).
 *       Result: DSP transport error 0x36b1 (createUnsignedPD fails).
 *
 *   v4: V3 BinaryInfo parsing bug — reinterpret_cast<BinaryInfoV1*>(&binaryInfoV3)
 *       read garbage (V3 has extra socVersion/contextBlobSize fields before numGraphs).
 *       Fixed via version-specific dispatch; also added tensor name deep-copy to
 *       prevent dangling pointers after systemContextFree.
 *
 *   v5: SIGBUS from QNN HTP background thread that races with the JVM allocator
 *       after contextCreateFromBinary returns.  Fix: call graphExecute once with
 *       zeroed buffers inside nativeInit (after graphRetrieve) to flush all
 *       pending async DSP/HTP initialisation before handing the handle to Kotlin.
 *       Also added graphFinalize call before prime for backends that require it.
 *
 * QNN API version : QAIRT 2.46.0.260424  (QNN API v2.35, System API v1.10)
 * Target device   : Qualcomm QCS6490 / RB3 Gen 2 (HTP V69)
 * ABI             : arm64-v8a
 */

// ─── Standard + Android headers ──────────────────────────────────────────────
#include <jni.h>
#include <android/log.h>
#include <dlfcn.h>

#include <chrono>
#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <string>
#include <vector>

// ─── Real QAIRT 2.46 headers (copied to cpp/include/QNN) ─────────────────────
// Provides correct struct layouts: Qnn_Version_t (3 fields),
// Qnn_QuantizeParams_t (2 enum + union), QnnInterface_t, QnnSystemInterface_t.
#include "QNN/QnnInterface.h"
#include "QNN/System/QnnSystemInterface.h"  // also pulls in QnnSystemContext.h

// ─── Logging ─────────────────────────────────────────────────────────────────
#define LOG_TAG "QnnInferenceJni"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// ─── JNI helpers ─────────────────────────────────────────────────────────────
#define THROW_AND_RETURN(env, msg, retval)                              \
    do {                                                                \
        LOGE("%s", (msg));                                              \
        (env)->ThrowNew(                                                \
            (env)->FindClass("java/lang/RuntimeException"), (msg));     \
        return (retval);                                                \
    } while (0)

#define QNN_CHECK(env, ret, ctx_msg, retval)                            \
    do {                                                                \
        if ((ret) != QNN_SUCCESS) {                                     \
            char _buf[256];                                             \
            snprintf(_buf, sizeof(_buf), "%s  (QNN error 0x%x)",        \
                     (ctx_msg),                                         \
                     static_cast<unsigned>((ret) & 0xFFFFFFFFu));       \
            THROW_AND_RETURN((env), _buf, (retval));                    \
        }                                                               \
    } while (0)

// QNN_SUCCESS is defined in QnnCommon.h as 0. No redefinition needed.

// ─── Function pointer types ───────────────────────────────────────────────────
//
// libQnnHtp.so  → QnnInterface_getProviders (only symbol exported)
// libQnnSystem.so → QnnSystemInterface_getProviders (only symbol exported)
// Both follow the identical provider pattern: call getProviders, then access
// all API functions via the returned interface struct.

typedef Qnn_ErrorHandle_t (*QnnInterface_getProviders_fn)(
    const QnnInterface_t*** providers, uint32_t* numProviders);

typedef Qnn_ErrorHandle_t (*QnnSystemInterface_getProviders_fn)(
    const QnnSystemInterface_t*** providers, uint32_t* numProviders);

// ═══════════════════════════════════════════════════════════════════════════════
// NativeQnnContext — one instance per nativeInit() call
// ═══════════════════════════════════════════════════════════════════════════════

struct NativeQnnContext {
    // dlopen handles
    void* prepLib   = nullptr;   // libQnnHtpPrepare.so (must outlive htpLib)
    void* htpLib    = nullptr;
    void* systemLib = nullptr;

    // Interface — obtained via QnnInterface_getProviders.
    // Pointer is valid for the lifetime of htpLib.
    const QnnInterface_t* iface = nullptr;

    // QNN object handles (opaque void*)
    Qnn_LogHandle_t     logger  = nullptr;
    Qnn_BackendHandle_t backend = nullptr;
    Qnn_DeviceHandle_t  device  = nullptr;
    Qnn_ContextHandle_t context = nullptr;
    Qnn_GraphHandle_t   graph   = nullptr;

    // Tensor descriptors owned by us (client buffers attached)
    std::vector<Qnn_Tensor_t>           inputTensors;
    std::vector<Qnn_Tensor_t>           outputTensors;
    std::vector<std::vector<uint8_t>>   inputBuffers;
    std::vector<std::vector<uint8_t>>   outputBuffers;
    std::vector<std::vector<uint32_t>>  inputDimensions;   // kept alive for .v1.dimensions
    std::vector<std::vector<uint32_t>>  outputDimensions;
    std::vector<size_t>                 outputElementCounts;
    std::vector<std::string>            outputNames;
    // Deep-copied tensor name strings — t.v1.name must NOT point into sysCtx or
    // model binary memory (both may be freed/moved after init). We own these strings.
    std::vector<std::string>            inputNameStorage;
    std::vector<std::string>            outputNameStorage;

    // The raw context binary. Kept alive as an extra safety measure; the primary
    // protection is the name deep-copy above.
    std::vector<uint8_t>                modelBinary;
};

// ═══════════════════════════════════════════════════════════════════════════════
// Utilities
// ═══════════════════════════════════════════════════════════════════════════════

static size_t tensorElementCount(const Qnn_Tensor_t& t) {
    size_t n = 1;
    for (uint32_t d = 0; d < t.v1.rank; ++d) n *= t.v1.dimensions[d];
    return n;
}

static size_t dataTypeByteSize(Qnn_DataType_t dt) {
    switch (dt) {
        case QNN_DATATYPE_INT_8:
        case QNN_DATATYPE_UINT_8:
        case QNN_DATATYPE_SFIXED_POINT_8:
        case QNN_DATATYPE_UFIXED_POINT_8:
        case QNN_DATATYPE_BOOL_8:          return 1;
        case QNN_DATATYPE_INT_16:
        case QNN_DATATYPE_UINT_16:
        case QNN_DATATYPE_FLOAT_16:
        case QNN_DATATYPE_SFIXED_POINT_16:
        case QNN_DATATYPE_UFIXED_POINT_16: return 2;
        default:                           return 4;
    }
}

static void dequantize(const uint8_t* src, float* dst, size_t count,
                       float scale, int32_t offset, bool isSigned) {
    if (isSigned) {
        const auto* s8 = reinterpret_cast<const int8_t*>(src);
        for (size_t i = 0; i < count; ++i)
            dst[i] = (static_cast<int32_t>(s8[i]) - offset) * scale;
    } else {
        for (size_t i = 0; i < count; ++i)
            dst[i] = (static_cast<int32_t>(src[i]) - offset) * scale;
    }
}

static void logTensorInfo(const char* prefix, const Qnn_Tensor_t& t) {
    std::string shape;
    for (uint32_t d = 0; d < t.v1.rank; ++d) {
        if (d) shape += "x";
        shape += std::to_string(t.v1.dimensions[d]);
    }
    bool hasQ = (t.v1.quantizeParams.encodingDefinition == QNN_DEFINITION_DEFINED);
    if (hasQ && t.v1.quantizeParams.quantizationEncoding
                == QNN_QUANTIZATION_ENCODING_SCALE_OFFSET) {
        LOGD("%s '%s' id=%u shape=[%s] dtype=0x%x scale=%.6f offset=%d",
             prefix, t.v1.name ? t.v1.name : "?", t.v1.id, shape.c_str(),
             (unsigned)t.v1.dataType,
             t.v1.quantizeParams.scaleOffsetEncoding.scale,
             t.v1.quantizeParams.scaleOffsetEncoding.offset);
    } else {
        LOGD("%s '%s' id=%u shape=[%s] dtype=0x%x (no quant)",
             prefix, t.v1.name ? t.v1.name : "?", t.v1.id, shape.c_str(),
             (unsigned)t.v1.dataType);
    }
}

static std::vector<uint8_t> readFile(const std::string& path) {
    FILE* f = fopen(path.c_str(), "rb");
    if (!f) { LOGE("Cannot open: %s", path.c_str()); return {}; }
    fseek(f, 0, SEEK_END);
    long sz = ftell(f);
    fseek(f, 0, SEEK_SET);
    if (sz <= 0) { fclose(f); return {}; }
    std::vector<uint8_t> buf(static_cast<size_t>(sz));
    size_t rd = fread(buf.data(), 1, buf.size(), f);
    fclose(f);
    if (rd != buf.size()) { LOGE("Short read on %s", path.c_str()); return {}; }
    LOGI("Read model: %s (%zu bytes)", path.c_str(), buf.size());
    return buf;
}

// ─── Tensor setup helpers ─────────────────────────────────────────────────────

/**
 * Build a Qnn_Tensor_t with a raw client buffer, copying dimension data so that
 * the dimensions pointer stays valid for the lifetime of NativeQnnContext.
 * The tensor name is deep-copied into nameStorage so the pointer remains valid
 * even after QnnSystemContext_free releases the binary-info memory.
 */
static Qnn_Tensor_t makeTensorWithBuffer(
        const Qnn_Tensor_t& src,
        std::vector<uint32_t>& dimStorage,
        std::vector<uint8_t>&  bufStorage,
        std::string&           nameStorage) {

    // Deep-copy the name string so we do not depend on sysCtx lifetime.
    nameStorage = src.v1.name ? src.v1.name : "";

    dimStorage.assign(src.v1.dimensions, src.v1.dimensions + src.v1.rank);

    size_t elemCount = 1;
    for (uint32_t d : dimStorage) elemCount *= d;
    size_t totalBytes = elemCount * dataTypeByteSize(src.v1.dataType);

    bufStorage.assign(totalBytes, 0);

    Qnn_Tensor_t t  = src;   // shallow copy (id, type, quant params, rank, version)
    t.v1.name               = nameStorage.empty() ? nullptr : nameStorage.c_str();
    t.v1.dimensions         = dimStorage.data();
    t.v1.memType            = QNN_TENSORMEMTYPE_RAW;
    t.v1.clientBuf.data     = bufStorage.data();
    t.v1.clientBuf.dataSize = static_cast<uint32_t>(totalBytes);
    return t;
}

/**
 * Fallback hardcoded tensor layout when QnnSystem binary info is unavailable.
 * Assumes float32 I/O and YOLOX-S dimensions.
 */
static bool buildHardcodedTensors(
        NativeQnnContext* ctx,
        const std::vector<std::string>& outputNames) {

    LOGW("QnnSystem unavailable — using hardcoded float32 YOLOX-S tensor layout");

    // ── Input (images: 1×3×640×640 float32) ─────────────────────────────────
    {
        Qnn_Tensor_t t = QNN_TENSOR_INIT;
        t.version              = QNN_TENSOR_VERSION_1;
        t.v1.id                = 0;
        t.v1.name              = "images";
        t.v1.type              = QNN_TENSOR_TYPE_APP_WRITE;
        t.v1.dataFormat        = QNN_TENSOR_DATA_FORMAT_FLAT_BUFFER;
        t.v1.dataType          = QNN_DATATYPE_FLOAT_32;
        t.v1.quantizeParams    = QNN_QUANTIZE_PARAMS_INIT;
        t.v1.rank              = 4;
        ctx->inputDimensions.push_back({1, 3, 640, 640});
        t.v1.dimensions        = ctx->inputDimensions.back().data();

        size_t bytes = 1 * 3 * 640 * 640 * 4;
        ctx->inputBuffers.push_back(std::vector<uint8_t>(bytes, 0));
        t.v1.memType           = QNN_TENSORMEMTYPE_RAW;
        t.v1.clientBuf.data    = ctx->inputBuffers.back().data();
        t.v1.clientBuf.dataSize = static_cast<uint32_t>(bytes);
        ctx->inputTensors.push_back(t);
    }

    // ── Outputs: bboxes (8400×4) and scores (8400×81) float32 ───────────────
    struct OutSpec { const char* name; uint32_t d0; uint32_t d1; };
    const OutSpec specs[] = {{"bboxes", 8400, 4}, {"scores", 8400, 81}};

    for (size_t oi = 0; oi < 2 && oi < outputNames.size(); ++oi) {
        Qnn_Tensor_t t = QNN_TENSOR_INIT;
        t.version              = QNN_TENSOR_VERSION_1;
        t.v1.id                = static_cast<uint32_t>(oi) + 1;
        t.v1.name              = specs[oi].name;
        t.v1.type              = QNN_TENSOR_TYPE_APP_READ;
        t.v1.dataFormat        = QNN_TENSOR_DATA_FORMAT_FLAT_BUFFER;
        t.v1.dataType          = QNN_DATATYPE_FLOAT_32;
        t.v1.quantizeParams    = QNN_QUANTIZE_PARAMS_INIT;
        t.v1.rank              = 2;
        ctx->outputDimensions.push_back({specs[oi].d0, specs[oi].d1});
        t.v1.dimensions        = ctx->outputDimensions.back().data();

        size_t elemCount = specs[oi].d0 * specs[oi].d1;
        size_t bytes     = elemCount * 4;
        ctx->outputBuffers.push_back(std::vector<uint8_t>(bytes, 0));
        ctx->outputElementCounts.push_back(elemCount);
        t.v1.memType           = QNN_TENSORMEMTYPE_RAW;
        t.v1.clientBuf.data    = ctx->outputBuffers.back().data();
        t.v1.clientBuf.dataSize = static_cast<uint32_t>(bytes);
        ctx->outputTensors.push_back(t);
    }
    return true;
}

// ═══════════════════════════════════════════════════════════════════════════════
// JNI — nativeInit
// ═══════════════════════════════════════════════════════════════════════════════

extern "C" JNIEXPORT jlong JNICALL
Java_com_fabricionarcizo_edgevisionai_ml_engine_QnnModel_nativeInit(
    JNIEnv* env, jobject /*thiz*/,
    jstring jModelPath, jobjectArray jOutputNames)
{
    LOGI("nativeInit() v5 — prime graphExecute inside nativeInit");

    // ── 1. Convert Java strings ──────────────────────────────────────────────
    const char* rawPath = env->GetStringUTFChars(jModelPath, nullptr);
    std::string modelPath(rawPath);
    env->ReleaseStringUTFChars(jModelPath, rawPath);

    jint jOutCount = env->GetArrayLength(jOutputNames);
    std::vector<std::string> outputNames;
    for (jint i = 0; i < jOutCount; ++i) {
        auto js = reinterpret_cast<jstring>(
            env->GetObjectArrayElement(jOutputNames, i));
        const char* cs = env->GetStringUTFChars(js, nullptr);
        outputNames.emplace_back(cs);
        env->ReleaseStringUTFChars(js, cs);
        env->DeleteLocalRef(js);
    }

    // ── 2. Allocate context struct ───────────────────────────────────────────
    auto* ctx = new (std::nothrow) NativeQnnContext();
    if (!ctx) THROW_AND_RETURN(env, "OOM: NativeQnnContext", 0L);
    ctx->outputNames = outputNames;

    // ── 3. Set ADSP_LIBRARY_PATH so the DSP loader can find libQnnHtpV68Skel.so ─
    //
    // The DSP skeleton library (libQnnHtpV68Skel.so) must be reachable by the
    // FastRPC runtime on the CDSP side.  By default it searches vendor paths
    // that an untrusted app cannot write to.  Setting ADSP_LIBRARY_PATH to the
    // app's filesDir (the directory of modelPath) makes the skel findable when
    // copied there from assets by QnnModel.kt at startup.
    {
        size_t sl = modelPath.rfind('/');
        std::string modelDir = (sl != std::string::npos) ? modelPath.substr(0, sl) : ".";
        if (setenv("ADSP_LIBRARY_PATH", modelDir.c_str(), 1) == 0)
            LOGI("ADSP_LIBRARY_PATH=%s", modelDir.c_str());
        else
            LOGW("setenv(ADSP_LIBRARY_PATH) failed");
    }

    // ── 4. Load libQnnHtpPrepare.so FIRST (initialises the HTP/DSP session) ───
    //
    // Must be opened before libQnnHtp.so.  RTLD_LOCAL is fine — the library
    // auto-registers its stubs via a constructor function when loaded.
    ctx->prepLib = dlopen("libQnnHtpPrepare.so", RTLD_NOW | RTLD_LOCAL);
    if (!ctx->prepLib)
        LOGW("dlopen(libQnnHtpPrepare.so): %s — HTP DSP session may be unstable", dlerror());
    else
        LOGI("Loaded libQnnHtpPrepare.so");

    // ── 5. dlopen libQnnHtp.so — MUST use RTLD_GLOBAL ───────────────────────
    //
    // Per the official Qualcomm QNN SampleApp (DynamicLoadUtil.cpp):
    //   dlOpen(backendPath, DL_NOW | DL_GLOBAL)
    // RTLD_GLOBAL makes the backend symbols visible to subsequently-loaded
    // stub libraries (libQnnHtpV68Stub.so, etc.) which need to call back into
    // libQnnHtp.so.  Using RTLD_LOCAL breaks this and causes DSP transport
    // errors (0x36b1 / 14001).
    ctx->htpLib = dlopen("libQnnHtp.so", RTLD_NOW | RTLD_GLOBAL);
    if (!ctx->htpLib) {
        std::string msg = std::string("dlopen(libQnnHtp.so): ") + dlerror();
        delete ctx;
        THROW_AND_RETURN(env, msg.c_str(), 0L);
    }
    LOGI("Loaded libQnnHtp.so");

    // ── 6. dlopen libQnnSystem.so (optional) ─────────────────────────────────
    ctx->systemLib = dlopen("libQnnSystem.so", RTLD_NOW | RTLD_LOCAL);
    if (!ctx->systemLib)
        LOGW("dlopen(libQnnSystem.so): %s — tensor metadata disabled", dlerror());
    else
        LOGI("Loaded libQnnSystem.so");

    // ── 7. Resolve the HTP interface via QnnInterface_getProviders ───────────
    //
    // The real QnnInterface_t layout (arm64, QAIRT 2.46):
    //   offset  0: uint32_t  backendId      [4 B] (e.g. 6 for HTP)
    //   offset  4: [padding]
    //   offset  8: const char* providerName [8 B] ("HTP_QTI_AISW" or NULL)
    //   offset 16: Qnn_ApiVersion_t         [24 B] ({coreApi={2,35,0}, backendApi={}})
    //   offset 40: QNN_INTERFACE_VER_NAME   (= v2_35 field in union)
    //
    // QNN_INTERFACE_VER_NAME expands to v2_35 (QNN_API_VERSION = 2.35.0).
    // Its first member is propertyHasCapability, then backendCreate at offset 8, etc.
    {
        auto getProviders = reinterpret_cast<QnnInterface_getProviders_fn>(
            dlsym(ctx->htpLib, "QnnInterface_getProviders"));
        if (!getProviders) {
            delete ctx;
            THROW_AND_RETURN(env, "dlsym(QnnInterface_getProviders) failed", 0L);
        }

        const QnnInterface_t** providers = nullptr;
        uint32_t numProviders = 0;
        Qnn_ErrorHandle_t ret = getProviders(&providers, &numProviders);
        if (ret != QNN_SUCCESS || numProviders == 0 || !providers) {
            delete ctx;
            THROW_AND_RETURN(env, "QnnInterface_getProviders returned no providers", 0L);
        }

        ctx->iface = providers[0];
        LOGI("Interface: backendId=%u  providerName=%s  coreApi=%u.%u.%u",
             ctx->iface->backendId,
             ctx->iface->providerName ? ctx->iface->providerName : "null",
             ctx->iface->apiVersion.coreApiVersion.major,
             ctx->iface->apiVersion.coreApiVersion.minor,
             ctx->iface->apiVersion.coreApiVersion.patch);
    }

    // Convenience alias — QNN_INTERFACE_VER_NAME expands to v2_35
    const auto& fn = ctx->iface->QNN_INTERFACE_VER_NAME;

    // ── 8. Create logger (optional) ─────────────────────────────────────────
    if (fn.logCreate) {
        Qnn_ErrorHandle_t ret =
            fn.logCreate(nullptr, QNN_LOG_LEVEL_WARN, &ctx->logger);
        if (ret != QNN_SUCCESS) {
            LOGW("QnnLog_create failed 0x%x — proceeding without logger",
                 (unsigned)ret);
            ctx->logger = nullptr;
        } else {
            LOGI("QNN logger created");
        }
    }

    // ── 9. Create backend ────────────────────────────────────────────────────
    {
        Qnn_ErrorHandle_t ret =
            fn.backendCreate(ctx->logger, nullptr, &ctx->backend);
        QNN_CHECK(env, ret, "QnnBackend_create failed", 0L);
        LOGI("QNN backend created");
    }

    // ── 10. Create device (optional) ─────────────────────────────────────────
    if (fn.deviceCreate) {
        Qnn_ErrorHandle_t ret =
            fn.deviceCreate(ctx->logger, nullptr, &ctx->device);
        if (ret != QNN_SUCCESS) {
            LOGW("QnnDevice_create returned 0x%x — using null device",
                 (unsigned)ret);
            ctx->device = nullptr;
        } else {
            LOGI("QNN device created");
        }
    }

    // ── 11. Read model binary ─────────────────────────────────────────────────
    std::vector<uint8_t> binary = readFile(modelPath);
    if (binary.empty()) {
        delete ctx;
        THROW_AND_RETURN(env, "Failed to read model binary", 0L);
    }

    // ── 12. Read tensor metadata via QnnSystemInterface ──────────────────────
    //
    // libQnnSystem.so does NOT export individual C symbols like QnnSystemContext_create.
    // Like libQnnHtp.so, it exports ONLY QnnSystemInterface_getProviders.
    // Access all system functions through the interface struct field
    // QNN_SYSTEM_INTERFACE_VER_NAME (= v1_10 for System API v1.10).
    std::string graphName;
    bool systemInfoOk = false;

    if (ctx->systemLib) {
        auto getSysProviders = reinterpret_cast<QnnSystemInterface_getProviders_fn>(
            dlsym(ctx->systemLib, "QnnSystemInterface_getProviders"));
        if (!getSysProviders) {
            LOGW("dlsym(QnnSystemInterface_getProviders): %s", dlerror());
        } else {
            const QnnSystemInterface_t** sysProviders = nullptr;
            uint32_t numSysProviders = 0;
            if (getSysProviders(&sysProviders, &numSysProviders) != QNN_SUCCESS
                || numSysProviders == 0 || !sysProviders) {
                LOGW("QnnSystemInterface_getProviders returned no providers");
            } else {
                LOGI("QnnSystem provider: backendId=%u  name=%s",
                     sysProviders[0]->backendId,
                     sysProviders[0]->providerName ? sysProviders[0]->providerName : "null");

                const auto& sfn = sysProviders[0]->QNN_SYSTEM_INTERFACE_VER_NAME;

                if (!sfn.systemContextCreate || !sfn.systemContextGetBinaryInfo || !sfn.systemContextFree) {
                    LOGW("QnnSystem interface missing required functions");
                } else {
                    QnnSystemContext_Handle_t sysCtx = nullptr;
                    if (sfn.systemContextCreate(&sysCtx) != QNN_SUCCESS || !sysCtx) {
                        LOGW("QnnSystemContext_create failed");
                    } else {
                        const QnnSystemContext_BinaryInfo_t* binaryInfo = nullptr;
                        Qnn_ContextBinarySize_t infoSize = 0;

                        Qnn_ErrorHandle_t ret = sfn.systemContextGetBinaryInfo(
                            sysCtx,
                            binary.data(),
                            static_cast<uint64_t>(binary.size()),
                            &binaryInfo,
                            &infoSize);

                        if (ret == QNN_SUCCESS && binaryInfo) {
                            // Extract numGraphs / graphs pointer by version.
                            // IMPORTANT: V1, V2, V3 have completely different struct
                            // layouts — reinterpret_cast across versions is WRONG and
                            // was the cause of "8 graphs, graph[0] has 0 inputs".
                            uint32_t numGraphs = 0;
                            const QnnSystemContext_GraphInfo_t* graphs = nullptr;

                            if (binaryInfo->version == QNN_SYSTEM_CONTEXT_BINARY_INFO_VERSION_1) {
                                numGraphs = binaryInfo->contextBinaryInfoV1.numGraphs;
                                graphs    = binaryInfo->contextBinaryInfoV1.graphs;
                            } else if (binaryInfo->version == QNN_SYSTEM_CONTEXT_BINARY_INFO_VERSION_2) {
                                numGraphs = binaryInfo->contextBinaryInfoV2.numGraphs;
                                graphs    = binaryInfo->contextBinaryInfoV2.graphs;
                            } else if (binaryInfo->version == QNN_SYSTEM_CONTEXT_BINARY_INFO_VERSION_3) {
                                numGraphs = binaryInfo->contextBinaryInfoV3.numGraphs;
                                graphs    = binaryInfo->contextBinaryInfoV3.graphs;
                            }

                            LOGI("Binary info v%u: %u graph(s)",
                                 (unsigned)binaryInfo->version, numGraphs);

                            // Enumerate ALL graphs; pick the first one with I/O tensors.
                            for (uint32_t gi = 0; gi < numGraphs && graphs; ++gi) {
                                const auto& g = graphs[gi];
                                const char*     gname = nullptr;
                                uint32_t        nIn   = 0, nOut = 0;
                                const Qnn_Tensor_t* gIn  = nullptr;
                                const Qnn_Tensor_t* gOut = nullptr;

                                // GraphInfo V1/V2/V3 all start with the same fields:
                                // graphName, numGraphInputs, graphInputs,
                                // numGraphOutputs, graphOutputs — cast is safe here.
                                if (g.version == QNN_SYSTEM_CONTEXT_GRAPH_INFO_VERSION_1) {
                                    gname = g.graphInfoV1.graphName;
                                    nIn   = g.graphInfoV1.numGraphInputs;
                                    gIn   = g.graphInfoV1.graphInputs;
                                    nOut  = g.graphInfoV1.numGraphOutputs;
                                    gOut  = g.graphInfoV1.graphOutputs;
                                } else if (g.version == QNN_SYSTEM_CONTEXT_GRAPH_INFO_VERSION_2) {
                                    gname = g.graphInfoV2.graphName;
                                    nIn   = g.graphInfoV2.numGraphInputs;
                                    gIn   = g.graphInfoV2.graphInputs;
                                    nOut  = g.graphInfoV2.numGraphOutputs;
                                    gOut  = g.graphInfoV2.graphOutputs;
                                } else if (g.version == QNN_SYSTEM_CONTEXT_GRAPH_INFO_VERSION_3) {
                                    gname = g.graphInfoV3.graphName;
                                    nIn   = g.graphInfoV3.numGraphInputs;
                                    gIn   = g.graphInfoV3.graphInputs;
                                    nOut  = g.graphInfoV3.numGraphOutputs;
                                    gOut  = g.graphInfoV3.graphOutputs;
                                }

                                LOGI("Graph[%u]: name='%s' inputs=%u outputs=%u",
                                     gi, gname ? gname : "null", nIn, nOut);

                                // Use the first graph that has non-zero I/O tensors.
                                if (!systemInfoOk && nIn > 0 && nOut > 0 && gIn && gOut) {
                                    if (gname) graphName = gname;

                                    ctx->inputTensors.resize(nIn);
                                    ctx->inputBuffers.resize(nIn);
                                    ctx->inputDimensions.resize(nIn);
                                    ctx->inputNameStorage.resize(nIn);
                                    for (uint32_t ii = 0; ii < nIn; ++ii) {
                                        logTensorInfo("INPUT ", gIn[ii]);
                                        ctx->inputTensors[ii] = makeTensorWithBuffer(
                                            gIn[ii], ctx->inputDimensions[ii],
                                            ctx->inputBuffers[ii],
                                            ctx->inputNameStorage[ii]);
                                    }

                                    ctx->outputTensors.resize(nOut);
                                    ctx->outputBuffers.resize(nOut);
                                    ctx->outputDimensions.resize(nOut);
                                    ctx->outputElementCounts.resize(nOut);
                                    ctx->outputNameStorage.resize(nOut);
                                    for (uint32_t oi = 0; oi < nOut; ++oi) {
                                        logTensorInfo("OUTPUT", gOut[oi]);
                                        ctx->outputTensors[oi] = makeTensorWithBuffer(
                                            gOut[oi], ctx->outputDimensions[oi],
                                            ctx->outputBuffers[oi],
                                            ctx->outputNameStorage[oi]);
                                        ctx->outputElementCounts[oi] =
                                            tensorElementCount(ctx->outputTensors[oi]);
                                    }
                                    systemInfoOk = true;
                                }
                            }
                        } else {
                            LOGW("QnnSystemContext_getBinaryInfo returned 0x%x", (unsigned)ret);
                        }
                        sfn.systemContextFree(sysCtx);
                    }
                }
            }
        }
    }

    if (!systemInfoOk) {
        buildHardcodedTensors(ctx, outputNames);
    }

    // ── 13. Create context from binary ───────────────────────────────────────
    {
        Qnn_ErrorHandle_t ret = fn.contextCreateFromBinary(
            ctx->backend, ctx->device, nullptr,
            binary.data(), static_cast<uint64_t>(binary.size()),
            &ctx->context, nullptr);
        QNN_CHECK(env, ret, "QnnContext_createFromBinary failed", 0L);
        LOGI("QNN context created from binary");
    }
    // Move the binary buffer into the context: tensor name pointers (t.v1.name)
    // from systemContextGetBinaryInfo point into this buffer.  Freeing it here
    // would leave those pointers dangling and cause SIGBUS in graphExecute.
    ctx->modelBinary = std::move(binary);
    // binary is now empty (no need to shrink)

    // ── 14. Retrieve graph ───────────────────────────────────────────────────
    {
        // Build candidate list: info-name → filename stem → common defaults
        std::string stem;
        {
            size_t sl = modelPath.rfind('/');
            std::string fn2 = (sl == std::string::npos) ? modelPath
                                                        : modelPath.substr(sl + 1);
            size_t dp = fn2.rfind('.');
            stem = (dp == std::string::npos) ? fn2 : fn2.substr(0, dp);
        }
        std::vector<std::string> candidates;
        if (!graphName.empty())   candidates.push_back(graphName);
        if (graphName != stem)    candidates.push_back(stem);
        candidates.push_back("main_graph");
        candidates.push_back("graph");

        bool found = false;
        for (const auto& name : candidates) {
            Qnn_ErrorHandle_t ret =
                fn.graphRetrieve(ctx->context, name.c_str(), &ctx->graph);
            if (ret == QNN_SUCCESS && ctx->graph) {
                LOGI("Graph retrieved: '%s'", name.c_str());
                found = true;
                break;
            }
            LOGD("graphRetrieve('%s') -> 0x%x", name.c_str(), (unsigned)ret);
        }
        if (!found) {
            delete ctx;
            THROW_AND_RETURN(env,
                "Could not retrieve QNN graph — check graph name in binary", 0L);
        }
    }

    // ── 15. Finalize graph (required by some backends before execution) ─────────
    if (fn.graphFinalize) {
        Qnn_ErrorHandle_t ret = fn.graphFinalize(ctx->graph, nullptr, nullptr);
        if (ret != QNN_SUCCESS)
            LOGW("graphFinalize returned 0x%x (non-fatal)", (unsigned)ret);
        else
            LOGI("graphFinalize OK");
    }

    // ── 16. Prime execution — flush any async HTP/DSP startup work ───────────
    //
    // QNN HTP spawns background DSP threads inside contextCreateFromBinary.
    // Those threads can race with the JVM allocator and crash (SIGBUS) if
    // graphExecute is never called first.  One synchronous graphExecute here
    // forces all pending async DSP initialisation to complete before we hand
    // the handle back to Kotlin.  The input buffers are already zeroed, so
    // this is a safe no-op inference from the model's perspective.
    LOGI("nativeInit() priming HTP with zero input...");
    {
        Qnn_ErrorHandle_t ret = fn.graphExecute(
            ctx->graph,
            ctx->inputTensors.data(),  static_cast<uint32_t>(ctx->inputTensors.size()),
            ctx->outputTensors.data(), static_cast<uint32_t>(ctx->outputTensors.size()),
            nullptr, nullptr);
        if (ret != QNN_SUCCESS)
            LOGW("nativeInit() prime graphExecute returned 0x%x (non-fatal — HTP may still be loading)",
                 (unsigned)ret);
        else
            LOGI("nativeInit() HTP prime succeeded");
    }

    LOGI("nativeInit() complete — handle=%p  inputs=%zu  outputs=%zu",
         static_cast<void*>(ctx),
         ctx->inputTensors.size(), ctx->outputTensors.size());
    return reinterpret_cast<jlong>(ctx);
}

// ═══════════════════════════════════════════════════════════════════════════════
// JNI — nativeRun
// ═══════════════════════════════════════════════════════════════════════════════

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_fabricionarcizo_edgevisionai_ml_engine_QnnModel_nativeRun(
    JNIEnv* env, jobject /*thiz*/,
    jlong handle, jfloatArray jInput)
{
    LOGI("nativeRun() start: handle=%p", reinterpret_cast<void*>(handle));
    if (handle == 0L) THROW_AND_RETURN(env, "nativeRun: null handle", nullptr);
    auto* ctx = reinterpret_cast<NativeQnnContext*>(handle);
    if (ctx->inputTensors.empty())
        THROW_AND_RETURN(env, "nativeRun: no input tensors", nullptr);

    const auto& fn = ctx->iface->QNN_INTERFACE_VER_NAME;

    // ── Write input data ─────────────────────────────────────────────────────
    {
        Qnn_Tensor_t& t = ctx->inputTensors[0];
        jsize len = env->GetArrayLength(jInput);
        LOGI("nativeRun() input dtype=0x%x len=%d buf=%p",
             (unsigned)t.v1.dataType, (int)len,
             ctx->inputBuffers[0].data());

        if (t.v1.dataType == QNN_DATATYPE_FLOAT_32) {
            float* dst = reinterpret_cast<float*>(ctx->inputBuffers[0].data());
            env->GetFloatArrayRegion(jInput, 0, len, dst);
        } else {
            // Quantize float → INT8/UINT8
            bool hasQ = (t.v1.quantizeParams.encodingDefinition == QNN_DEFINITION_DEFINED)
                     && (t.v1.quantizeParams.quantizationEncoding
                         == QNN_QUANTIZATION_ENCODING_SCALE_OFFSET);
            float   sc  = hasQ ? t.v1.quantizeParams.scaleOffsetEncoding.scale  : 1.0f;
            int32_t off = hasQ ? t.v1.quantizeParams.scaleOffsetEncoding.offset : 0;
            LOGI("nativeRun() quantizing: hasQ=%d scale=%.6f offset=%d", (int)hasQ, sc, off);

            std::vector<float> floatBuf(static_cast<size_t>(len));
            env->GetFloatArrayRegion(jInput, 0, len, floatBuf.data());

            size_t  elemCount = tensorElementCount(t);
            uint8_t* dst      = ctx->inputBuffers[0].data();
            bool isSigned = (t.v1.dataType == QNN_DATATYPE_INT_8 ||
                             t.v1.dataType == QNN_DATATYPE_SFIXED_POINT_8);

            for (size_t i = 0; i < std::min(elemCount, (size_t)len); ++i) {
                float q = floatBuf[i] / sc + static_cast<float>(off);
                if (isSigned) {
                    int32_t v = static_cast<int32_t>(q + 0.5f);
                    v = v < -128 ? -128 : (v > 127 ? 127 : v);
                    reinterpret_cast<int8_t*>(dst)[i] = static_cast<int8_t>(v);
                } else {
                    int32_t v = static_cast<int32_t>(q + 0.5f);
                    v = v < 0 ? 0 : (v > 255 ? 255 : v);
                    dst[i] = static_cast<uint8_t>(v);
                }
            }
        }
    }

    // ── Execute ──────────────────────────────────────────────────────────────
    LOGI("nativeRun() calling graphExecute: graph=%p in=%zu out=%zu",
         ctx->graph,
         ctx->inputTensors.size(), ctx->outputTensors.size());
    auto t0 = std::chrono::steady_clock::now();

    Qnn_ErrorHandle_t ret = fn.graphExecute(
        ctx->graph,
        ctx->inputTensors.data(),  static_cast<uint32_t>(ctx->inputTensors.size()),
        ctx->outputTensors.data(), static_cast<uint32_t>(ctx->outputTensors.size()),
        nullptr, nullptr);

    double ms = std::chrono::duration<double, std::milli>(
        std::chrono::steady_clock::now() - t0).count();
    LOGI("nativeRun() graphExecute returned 0x%x in %.2f ms", (unsigned)ret, ms);

    if (ret != QNN_SUCCESS) {
        char msg[128];
        snprintf(msg, sizeof(msg), "QnnGraph_execute failed: 0x%x", (unsigned)ret);
        THROW_AND_RETURN(env, msg, nullptr);
    }
    LOGD("QnnGraph_execute: %.2f ms", ms);

    // ── Package outputs as Array<FloatArray> ──────────────────────────────────
    jclass floatArrayClass = env->FindClass("[F");
    jsize  numOutputs      = static_cast<jsize>(ctx->outputTensors.size());
    jobjectArray result = env->NewObjectArray(numOutputs, floatArrayClass, nullptr);
    env->DeleteLocalRef(floatArrayClass);

    for (jsize i = 0; i < numOutputs; ++i) {
        const Qnn_Tensor_t& t   = ctx->outputTensors[static_cast<size_t>(i)];
        size_t              cnt = ctx->outputElementCounts[static_cast<size_t>(i)];
        const uint8_t*      raw = ctx->outputBuffers[static_cast<size_t>(i)].data();

        LOGD("Output[%d] '%s': %zu elements dtype=0x%x",
             i, t.v1.name ? t.v1.name : "?", cnt, (unsigned)t.v1.dataType);

        jfloatArray jf  = env->NewFloatArray(static_cast<jsize>(cnt));
        float*      dst = env->GetFloatArrayElements(jf, nullptr);

        bool hasQ = (t.v1.quantizeParams.encodingDefinition == QNN_DEFINITION_DEFINED)
                 && (t.v1.quantizeParams.quantizationEncoding
                     == QNN_QUANTIZATION_ENCODING_SCALE_OFFSET);
        float   sc  = hasQ ? t.v1.quantizeParams.scaleOffsetEncoding.scale  : 1.0f;
        int32_t off = hasQ ? t.v1.quantizeParams.scaleOffsetEncoding.offset : 0;

        switch (t.v1.dataType) {
            case QNN_DATATYPE_FLOAT_32:
                memcpy(dst, raw, cnt * sizeof(float));
                break;
            case QNN_DATATYPE_INT_8:
            case QNN_DATATYPE_SFIXED_POINT_8:
                dequantize(raw, dst, cnt, sc, off, true);
                break;
            case QNN_DATATYPE_UINT_8:
            case QNN_DATATYPE_UFIXED_POINT_8:
                dequantize(raw, dst, cnt, sc, off, false);
                break;
            case QNN_DATATYPE_FLOAT_16: {
                const uint16_t* f16 = reinterpret_cast<const uint16_t*>(raw);
                for (size_t e = 0; e < cnt; ++e) {
                    uint16_t h = f16[e];
                    uint32_t s = (h >> 15) & 1u;
                    uint32_t ex = (h >> 10) & 0x1Fu;
                    uint32_t mn = h & 0x3FFu;
                    uint32_t fb;
                    if      (ex == 0)  fb = s<<31 | (mn ? ((127-14)<<23|(mn<<13)) : 0);
                    else if (ex == 31) fb = s<<31 | 0x7F800000u | (mn ? 0x400000u : 0u);
                    else               fb = s<<31 | ((ex+112)<<23) | (mn<<13);
                    memcpy(&dst[e], &fb, 4);
                }
                break;
            }
            default:
                LOGW("Unhandled dtype 0x%x on output[%d] — zeroing",
                     (unsigned)t.v1.dataType, i);
                memset(dst, 0, cnt * sizeof(float));
                break;
        }

        env->ReleaseFloatArrayElements(jf, dst, 0);
        env->SetObjectArrayElement(result, i, jf);
        env->DeleteLocalRef(jf);
    }
    return result;
}

// ═══════════════════════════════════════════════════════════════════════════════
// JNI — nativeRelease
// ═══════════════════════════════════════════════════════════════════════════════

extern "C" JNIEXPORT void JNICALL
Java_com_fabricionarcizo_edgevisionai_ml_engine_QnnModel_nativeRelease(
    JNIEnv* /*env*/, jobject /*thiz*/, jlong handle)
{
    if (handle == 0L) { LOGW("nativeRelease: null handle"); return; }
    auto* ctx = reinterpret_cast<NativeQnnContext*>(handle);
    LOGI("nativeRelease() — releasing QNN resources");

    // Null out client buffer pointers before the runtime accesses the tensors.
    for (auto& t : ctx->inputTensors)  { t.v1.clientBuf.data = nullptr; t.v1.clientBuf.dataSize = 0; }
    for (auto& t : ctx->outputTensors) { t.v1.clientBuf.data = nullptr; t.v1.clientBuf.dataSize = 0; }

    if (ctx->iface) {
        const auto& fn = ctx->iface->QNN_INTERFACE_VER_NAME;
        if (ctx->context && fn.contextFree)
            fn.contextFree(ctx->context, nullptr);
        if (ctx->device && fn.deviceFree)
            fn.deviceFree(ctx->device);
        if (ctx->backend && fn.backendFree)
            fn.backendFree(ctx->backend);
        if (ctx->logger && fn.logFree)
            fn.logFree(ctx->logger);
    }

    if (ctx->htpLib)    { dlclose(ctx->htpLib);    LOGI("Closed libQnnHtp.so"); }
    if (ctx->systemLib) { dlclose(ctx->systemLib); LOGI("Closed libQnnSystem.so"); }
    // Close prepLib last — it must outlive htpLib
    if (ctx->prepLib)   { dlclose(ctx->prepLib);   LOGI("Closed libQnnHtpPrepare.so"); }

    // Release the model binary last — tensor name pointers were referencing it.
    ctx->modelBinary.clear();
    ctx->modelBinary.shrink_to_fit();

    delete ctx;
    LOGI("nativeRelease() complete");
}
