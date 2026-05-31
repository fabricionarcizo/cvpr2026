#include <android/log.h>
#include <jni.h>
#include <iomanip>
#include <cmath>
#include <cstring>
#include <sstream>
#include <string>
#include <vector>
#include <algorithm>
#include <unistd.h>

#include "logging.h"
#include "llama.h"
#include "mtmd.h"
#include "mtmd-helper.h"

// Stable-ABI wrapper. The prebuilt libllama-common.so from pkg-snapdragon was
// built against a different llama.cpp commit than our submodule headers, so
// common_chat_msg / common_params_sampling struct layouts diverge and calling
// into libllama-common crashes. We avoid common.h entirely and rebuild the
// thin pieces we need (batch helpers, sampler chain, chat formatting) on top
// of llama.h's stable C API.

using llama_tokens = std::vector<llama_token>;

struct chat_msg { std::string role; std::string content; };

template<class T>
static std::string join(const std::vector<T> &values, const std::string &delim) {
    std::ostringstream str;
    for (size_t i = 0; i < values.size(); i++) {
        str << values[i];
        if (i < values.size() - 1) { str << delim; }
    }
    return str.str();
}

/**
 * LLama resources: context, model, batch and sampler
 */
constexpr int   N_THREADS_MIN           = 2;
// S25 Ultra has heterogeneous cores (2 prime + 6 perf @ different freqs). Spawning
// more workers than prime cores makes the Android scheduler interleave threads
// across mismatched cores and the Q4_K_M matmul kernels lose to imbalance —
// we measured 2× slowdown going from 4 threads to 7. We can't taskset-pin from
// an app, so cap at 4 and let the scheduler park us on the fast cluster.
constexpr int   N_THREADS_MAX           = 4;
constexpr int   N_THREADS_HEADROOM      = 2;

constexpr int   DEFAULT_CONTEXT_SIZE    = 8192;
constexpr int   OVERFLOW_HEADROOM       = 4;
constexpr int   BATCH_SIZE              = 512;
constexpr float DEFAULT_SAMPLER_TEMP    = 0.3f;

static llama_model                      * g_model = nullptr;
static llama_context                    * g_context = nullptr;
static llama_batch                        g_batch;
static llama_sampler                    * g_sampler = nullptr;
static std::string                        g_chat_template_str;   // cached from llama_model_chat_template
static bool                               g_has_chat_template = false;
static mtmd_context                     * g_mtmd_ctx = nullptr;
static bool                               g_enable_thinking = false;
static std::string                        g_grammar;

// ---------- Stable-ABI helpers (replacements for common_* utilities) ----------

static void batch_clear(llama_batch &batch) {
    batch.n_tokens = 0;
}

static void batch_add(llama_batch &batch, llama_token id, llama_pos pos,
                      const std::vector<llama_seq_id> &seq_ids, bool logits) {
    const int i = batch.n_tokens;
    batch.token   [i] = id;
    batch.pos     [i] = pos;
    batch.n_seq_id[i] = (int32_t) seq_ids.size();
    for (size_t j = 0; j < seq_ids.size(); ++j) {
        batch.seq_id[i][j] = seq_ids[j];
    }
    batch.logits  [i] = logits;
    batch.n_tokens++;
}

static llama_tokens tokenize_text(llama_context *ctx, const std::string &text,
                                  bool add_special, bool parse_special) {
    const llama_vocab *vocab = llama_model_get_vocab(llama_get_model(ctx));
    int32_t need = -llama_tokenize(vocab, text.data(), (int) text.size(),
                                   nullptr, 0, add_special, parse_special);
    if (need < 0) need = 0;
    llama_tokens out(need);
    int32_t got = llama_tokenize(vocab, text.data(), (int) text.size(),
                                 out.data(), (int) out.size(), add_special, parse_special);
    if (got < 0) got = 0;
    out.resize(got);
    return out;
}

static std::string token_to_piece(llama_context *ctx, llama_token id, bool special = false) {
    const llama_vocab *vocab = llama_model_get_vocab(llama_get_model(ctx));
    char buf[256];
    int32_t n = llama_token_to_piece(vocab, id, buf, sizeof(buf), 0, special);
    if (n < 0) {
        std::vector<char> big(-n);
        n = llama_token_to_piece(vocab, id, big.data(), (int32_t) big.size(), 0, special);
        if (n < 0) return "";
        return std::string(big.data(), n);
    }
    return std::string(buf, n);
}

// Render a chat history through the model's template using the raw llama API.
// Returns empty string on failure; the caller falls back to ChatML.
static std::string apply_template_raw(const std::vector<chat_msg> &msgs, bool add_ass) {
    if (msgs.empty()) return "";
    std::vector<llama_chat_message> raw;
    raw.reserve(msgs.size());
    for (const auto &m : msgs) raw.push_back({m.role.c_str(), m.content.c_str()});
    const char *tmpl = g_chat_template_str.empty() ? nullptr : g_chat_template_str.c_str();
    std::string buf(8192, '\0');
    int32_t n = llama_chat_apply_template(tmpl, raw.data(), raw.size(), add_ass,
                                          buf.data(), (int32_t) buf.size());
    if (n < 0) return "";
    if (n > (int32_t) buf.size()) {
        buf.resize(n);
        n = llama_chat_apply_template(tmpl, raw.data(), raw.size(), add_ass,
                                      buf.data(), (int32_t) buf.size());
        if (n < 0) return "";
    }
    buf.resize(n);
    return buf;
}

// ChatML fallback (Qwen3 / Qwen3-VL native format). Used when the model has
// no chat template metadata or llama_chat_apply_template can't parse it.
static std::string apply_template_chatml(const std::vector<chat_msg> &msgs, bool add_ass) {
    std::string out;
    for (const auto &m : msgs) {
        out += "<|im_start|>" + m.role + "\n" + m.content + "<|im_end|>\n";
    }
    if (add_ass) out += "<|im_start|>assistant\n";
    return out;
}

static std::string apply_template(const std::vector<chat_msg> &msgs, bool add_ass) {
    if (g_has_chat_template) {
        std::string s = apply_template_raw(msgs, add_ass);
        if (!s.empty()) return s;
        LOGw("apply_template: raw failed, falling back to ChatML");
    }
    return apply_template_chatml(msgs, add_ass);
}
// Runtime backend selection: "cpu" (default) | "gpu" (OpenCL) | "htp" (Hexagon NPU).
// Set via Java_..._setBackend before calling load().
static std::string                        g_backend = "cpu";

// Path to the app's native lib dir (saved from init for lazy backend-load in setBackend).
static std::string g_native_lib_dir;

// Image-token cap for mtmd. Set via Java_..._setImageMaxTokens BEFORE
// loadMmproj — read into mtmd_context_params.image_max_tokens at init.
// 0 = use mmproj GGUF metadata default (= natural ~300 tokens for Qwen3-VL).
static int g_image_max_tokens = 0;

extern "C"
JNIEXPORT void JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_init(JNIEnv *env, jobject /*unused*/, jstring nativeLibDir) {
    // Set llama log handler to Android
    llama_log_set(aichat_android_log_callback, nullptr);

    const auto *path_to_backend = env->GetStringUTFChars(nativeLibDir, 0);
    g_native_lib_dir = path_to_backend ? path_to_backend : "";
    // Tell FastRPC where to find the DSP-side libggml-htp-v*.so (required for Hexagon).
    setenv("ADSP_LIBRARY_PATH", path_to_backend, 1);
    LOGi("ADSP_LIBRARY_PATH=%s", path_to_backend);
    env->ReleaseStringUTFChars(nativeLibDir, path_to_backend);

    // Don't call ggml_backend_load_all_from_path() — it loads libggml-hexagon.so which
    // registers a broken HTP device (no DSP access from app sandbox) that crashes
    // ggml_backend_dev_type during model load. Instead we load specific backends
    // lazily in setBackend() based on user choice. The CPU backend is auto-loaded
    // by the dynamic linker via libai-chat.so → libggml-cpu.so dependency.
    llama_backend_init();
    LOGi("Backend initiated (CPU only by default); Log handler set.");
}

extern "C"
JNIEXPORT void JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_nativeSetBackend(JNIEnv *env, jobject /*unused*/, jstring jbackend) {
    const auto *b = env->GetStringUTFChars(jbackend, 0);
    g_backend = b ? b : "cpu";
    env->ReleaseStringUTFChars(jbackend, b);
    LOGi("backend selected: %s", g_backend.c_str());

    // Lazy backend load: only dlopen the .so for the chosen backend. CPU is always
    // already loaded via libai-chat.so's link-time deps.
    if (g_backend == "gpu" && !g_native_lib_dir.empty()) {
        std::string path = g_native_lib_dir + "/libggml-opencl.so";
        if (ggml_backend_load(path.c_str())) {
            LOGi("loaded OpenCL backend: %s", path.c_str());
        } else {
            LOGw("failed to load OpenCL backend at %s", path.c_str());
        }
    } else if (g_backend == "htp" && !g_native_lib_dir.empty()) {
        std::string path = g_native_lib_dir + "/libggml-hexagon.so";
        if (ggml_backend_load(path.c_str())) {
            LOGi("loaded Hexagon backend: %s", path.c_str());
        } else {
            LOGw("failed to load Hexagon backend at %s", path.c_str());
        }
    }
}

// Returns the first ggml device whose name contains `needle` (case-sensitive). NULL otherwise.
static ggml_backend_dev_t find_device(const char *needle) {
    for (size_t i = 0; i < ggml_backend_dev_count(); i++) {
        auto *dev = ggml_backend_dev_get(i);
        const char *name = ggml_backend_dev_name(dev);
        if (name && strstr(name, needle)) {
            LOGi("matched device %s for %s", name, needle);
            return dev;
        }
    }
    LOGw("no device matched %s", needle);
    return nullptr;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_nativeSetImageMaxTokens(JNIEnv * /*env*/, jobject /*unused*/, jint max_tokens) {
    g_image_max_tokens = (int) max_tokens;
    LOGi("image_max_tokens set to: %d (0 = default from mmproj metadata)", g_image_max_tokens);
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_load(JNIEnv *env, jobject, jstring jmodel_path) {
    llama_model_params model_params = llama_model_default_params();

    // Always set model_params.devices explicitly to a single backend, so llama.cpp
    // doesn't enumerate broken devices. When Hexagon session-open fails (signed-PD
    // restriction on production phones), the Hexagon device registers itself but its
    // callbacks crash — iterating across devices hits ggml_abort in ggml_backend_dev_type.
    static ggml_backend_dev_t dev_list[2] = {nullptr, nullptr};
    if (g_backend == "gpu") {
        dev_list[0] = find_device("OpenCL");
        model_params.n_gpu_layers = 999;
    } else if (g_backend == "htp") {
        dev_list[0] = find_device("HTP");
        // n_gpu_layers=0 wins for Q4_K_M models — the DSP doesn't have efficient
        // kernels for K-quants, so n_gpu_layers=999 forces 100+ CPU↔DSP graph
        // splits per token and tok/s collapses. Keep the LM on CPU_REPACK (fast
        // ARMv9 i8mm) and let the OpenCL CLIP encoder give us the real win.
        model_params.n_gpu_layers = 0;
    } else {
        dev_list[0] = find_device("CPU");
        model_params.n_gpu_layers = 0;
    }
    if (!dev_list[0]) {
        LOGw("requested device for '%s' not found; falling back to CPU", g_backend.c_str());
        dev_list[0] = find_device("CPU");
    }
    if (dev_list[0]) {
        model_params.devices = dev_list;
    }

    const auto *model_path = env->GetStringUTFChars(jmodel_path, 0);
    LOGd("%s: Loading model from: \n%s\n", __func__, model_path);

    auto *model = llama_model_load_from_file(model_path, model_params);
    env->ReleaseStringUTFChars(jmodel_path, model_path);
    if (!model) {
        return 1;
    }
    g_model = model;
    return 0;
}

static llama_context *init_context(llama_model *model, const int n_ctx = DEFAULT_CONTEXT_SIZE) {
    if (!model) {
        LOGe("%s: model cannot be null", __func__);
        return nullptr;
    }

    // Multi-threading setup
    const int n_threads = std::max(N_THREADS_MIN, std::min(N_THREADS_MAX,
                                                     (int) sysconf(_SC_NPROCESSORS_ONLN) -
                                                     N_THREADS_HEADROOM));
    LOGi("%s: Using %d threads", __func__, n_threads);

    // Context parameters setup
    llama_context_params ctx_params = llama_context_default_params();
    const int trained_context_size = llama_model_n_ctx_train(model);
    if (n_ctx > trained_context_size) {
        LOGw("%s: Model was trained with only %d context size! Enforcing %d context size...",
             __func__, trained_context_size, n_ctx);
    }
    ctx_params.n_ctx = n_ctx;
    ctx_params.n_batch = BATCH_SIZE;
    ctx_params.n_ubatch = BATCH_SIZE;
    ctx_params.n_threads = n_threads;
    ctx_params.n_threads_batch = n_threads;
    // KV cache + flash-attn settings.
    // - GPU offloads layers to OpenCL → needs F16 KV (no quantized-KV kernels).
    // - HTP path keeps LM on CPU but the prebuilt's Q8_0+flash-attn ENABLED combo
    //   is measurably ~15% slower than F16+AUTO on Snapdragon 8 Elite (likely no
    //   tuned Q8_0+FA kernel for ARMv9 i8mm in this snapshot). Use F16+AUTO.
    // - Pure CPU mode: Q8_0+ENABLED is still the fastest for that path (we use
    //   the from-source built-in kernels there).
    if (g_backend == "gpu" || g_backend == "htp") {
        ctx_params.flash_attn_type = LLAMA_FLASH_ATTN_TYPE_AUTO;
        ctx_params.type_k = GGML_TYPE_F16;
        ctx_params.type_v = GGML_TYPE_F16;
    } else {
        ctx_params.flash_attn_type = LLAMA_FLASH_ATTN_TYPE_ENABLED;
        ctx_params.type_k = GGML_TYPE_Q8_0;
        ctx_params.type_v = GGML_TYPE_Q8_0;
    }
    auto *context = llama_init_from_model(g_model, ctx_params);
    if (context == nullptr) {
        LOGe("%s: llama_new_context_with_model() returned null)", __func__);
    }
    return context;
}

static llama_sampler *new_sampler(float temp) {
    llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    llama_sampler *chain = llama_sampler_chain_init(sparams);
    if (!g_grammar.empty()) {
        const llama_vocab *vocab = llama_model_get_vocab(g_model);
        llama_sampler *gr = llama_sampler_init_grammar(vocab, g_grammar.c_str(), "root");
        if (gr) {
            llama_sampler_chain_add(chain, gr);
            LOGi("new_sampler: GBNF grammar enabled (%zu chars)", g_grammar.size());
        } else {
            LOGw("new_sampler: llama_sampler_init_grammar returned null");
        }
    }
    llama_sampler_chain_add(chain, llama_sampler_init_temp(temp));
    llama_sampler_chain_add(chain, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
    return chain;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_prepare(JNIEnv * /*env*/, jobject /*unused*/) {
    auto *context = init_context(g_model);
    if (!context) { return 1; }
    g_context = context;
    g_batch = llama_batch_init(BATCH_SIZE, 0, 1);

    // Cache the model's chat template metadata so apply_template_raw can use it.
    const char *tmpl = llama_model_chat_template(g_model, nullptr);
    if (tmpl && tmpl[0]) {
        g_chat_template_str = tmpl;
        g_has_chat_template = true;
        LOGi("prepare: cached chat template (%zu chars)", g_chat_template_str.size());
    } else {
        g_chat_template_str.clear();
        g_has_chat_template = false;
        LOGi("prepare: no chat template metadata; will use ChatML fallback");
    }

    g_sampler = new_sampler(DEFAULT_SAMPLER_TEMP);
    return 0;
}

static std::string get_backend() {
    std::vector<std::string> backends;
    for (size_t i = 0; i < ggml_backend_reg_count(); i++) {
        auto *reg = ggml_backend_reg_get(i);
        std::string name = ggml_backend_reg_name(reg);
        if (name != "CPU") {
            backends.push_back(ggml_backend_reg_name(reg));
        }
    }
    return backends.empty() ? "CPU" : join(backends, ",");
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_systemInfo(JNIEnv *env, jobject /*unused*/) {
    return env->NewStringUTF(llama_print_system_info());
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_benchModel(JNIEnv *env, jobject /*unused*/, jint pp, jint tg,
                                                      jint pl, jint nr) {
    auto *context = init_context(g_model, pp);
    if (!context) {
        const auto *const err_msg = "Fail to init_context! Bench aborted.";
        LOGe(err_msg);
        return env->NewStringUTF(err_msg);
    }

    auto pp_avg = 0.0;
    auto tg_avg = 0.0;
    auto pp_std = 0.0;
    auto tg_std = 0.0;

    const uint32_t n_ctx = llama_n_ctx(context);
    LOGi("n_ctx = %d", n_ctx);

    int i, j;
    int nri;
    for (nri = 0; nri < nr; nri++) {
        LOGi("Benchmark prompt processing (pp = %d)", pp);

        batch_clear(g_batch);

        const int n_tokens = pp;
        for (i = 0; i < n_tokens; i++) {
            batch_add(g_batch, 0, i, {0}, false);
        }

        g_batch.logits[g_batch.n_tokens - 1] = true;
        llama_memory_clear(llama_get_memory(context), false);

        const auto t_pp_start = ggml_time_us();
        if (llama_decode(context, g_batch) != 0) {
            LOGe("llama_decode() failed during prompt processing");
        }
        const auto t_pp_end = ggml_time_us();

        // bench text generation

        LOGi("Benchmark text generation (tg = %d)", tg);

        llama_memory_clear(llama_get_memory(context), false);
        const auto t_tg_start = ggml_time_us();
        for (i = 0; i < tg; i++) {
            batch_clear(g_batch);
            for (j = 0; j < pl; j++) {
                batch_add(g_batch, 0, i, {j}, true);
            }

            if (llama_decode(context, g_batch) != 0) {
                LOGe("llama_decode() failed during text generation");
            }
        }
        const auto t_tg_end = ggml_time_us();

        llama_memory_clear(llama_get_memory(context), false);

        const auto t_pp = double(t_pp_end - t_pp_start) / 1000000.0;
        const auto t_tg = double(t_tg_end - t_tg_start) / 1000000.0;

        const auto speed_pp = double(pp) / t_pp;
        const auto speed_tg = double(pl * tg) / t_tg;

        pp_avg += speed_pp;
        tg_avg += speed_tg;

        pp_std += speed_pp * speed_pp;
        tg_std += speed_tg * speed_tg;

        LOGi("pp %f t/s, tg %f t/s", speed_pp, speed_tg);
    }

    llama_free(context);

    pp_avg /= double(nr);
    tg_avg /= double(nr);

    if (nr > 1) {
        pp_std = sqrt(pp_std / double(nr - 1) - pp_avg * pp_avg * double(nr) / double(nr - 1));
        tg_std = sqrt(tg_std / double(nr - 1) - tg_avg * tg_avg * double(nr) / double(nr - 1));
    } else {
        pp_std = 0;
        tg_std = 0;
    }

    char model_desc[128];
    llama_model_desc(g_model, model_desc, sizeof(model_desc));

    const auto model_size = double(llama_model_size(g_model)) / 1024.0 / 1024.0 / 1024.0;
    const auto model_n_params = double(llama_model_n_params(g_model)) / 1e9;

    const auto backend = get_backend();
    std::stringstream result;
    result << std::setprecision(3);
    result << "| model | size | params | backend | test | t/s |\n";
    result << "| --- | --- | --- | --- | --- | --- |\n";
    result << "| " << model_desc << " | " << model_size << "GiB | " << model_n_params << "B | "
           << backend << " | pp " << pp << " | " << pp_avg << " ± " << pp_std << " |\n";
    result << "| " << model_desc << " | " << model_size << "GiB | " << model_n_params << "B | "
           << backend << " | tg " << tg << " | " << tg_avg << " ± " << tg_std << " |\n";
    return env->NewStringUTF(result.str().c_str());
}


/**
 * Completion loop's long-term states:
 * - chat management
 * - position tracking
 */
constexpr const char *ROLE_SYSTEM       = "system";
constexpr const char *ROLE_USER         = "user";
constexpr const char *ROLE_ASSISTANT    = "assistant";

static std::vector<chat_msg> chat_msgs;
static llama_pos system_prompt_position;
static llama_pos current_position;

static void reset_long_term_states(const bool clear_kv_cache = true) {
    chat_msgs.clear();
    system_prompt_position = 0;
    current_position = 0;

    if (clear_kv_cache)
        llama_memory_clear(llama_get_memory(g_context), false);
}

/**
 * TODO-hyin: implement sliding-window version as a better alternative
 *
 * Context shifting by discarding the older half of the tokens appended after system prompt:
 * - take the [system_prompt_position] first tokens from the original prompt
 * - take half of the last (system_prompt_position - system_prompt_position) tokens
 * - recompute the logits in batches
 */
static void shift_context() {
    const int n_discard = (current_position - system_prompt_position) / 2;
    LOGi("%s: Discarding %d tokens", __func__, n_discard);
    llama_memory_seq_rm(llama_get_memory(g_context), 0, system_prompt_position, system_prompt_position + n_discard);
    llama_memory_seq_add(llama_get_memory(g_context), 0, system_prompt_position + n_discard, current_position, -n_discard);
    current_position -= n_discard;
    LOGi("%s: Context shifting done! Current position: %d", __func__, current_position);
}

static std::string chat_add_and_format(const std::string &role, const std::string &content) {
    chat_msg new_msg{role, content};

    // Render past history (no assistant prefix), then past+new (with prefix iff
    // the new message is a user turn). The delta is what the caller needs to
    // tokenize and decode — replicates common_chat_format_single semantics.
    const std::string fmt_past = apply_template(chat_msgs, /*add_ass=*/false);
    std::vector<chat_msg> with_new = chat_msgs;
    with_new.push_back(new_msg);
    const std::string fmt_full = apply_template(with_new, /*add_ass=*/role == ROLE_USER);

    chat_msgs.push_back(new_msg);

    const size_t prefix = std::min(fmt_past.size(), fmt_full.size());
    std::string delta = (prefix < fmt_full.size()) ? fmt_full.substr(prefix) : fmt_full;
    LOGi("%s: Formatted and added %s message:\n%s", __func__, role.c_str(), delta.c_str());
    return delta;
}

/**
 * Completion loop's short-term states:
 * - stop generation position
 * - token chars caching
 * - current assistant message being generated
 */
static llama_pos stop_generation_position;
static std::string cached_token_chars;
static std::ostringstream assistant_ss;

static void reset_short_term_states() {
    stop_generation_position = 0;
    cached_token_chars.clear();
    assistant_ss.str("");
}

static int decode_tokens_in_batches(
        llama_context *context,
        llama_batch &batch,
        const llama_tokens &tokens,
        const llama_pos start_pos,
        const bool compute_last_logit = false) {
    // Process tokens in batches using the global batch
    LOGd("%s: Decode %d tokens starting at position %d", __func__, (int) tokens.size(), start_pos);
    for (int i = 0; i < (int) tokens.size(); i += BATCH_SIZE) {
        const int cur_batch_size = std::min((int) tokens.size() - i, BATCH_SIZE);
        batch_clear(batch);
        LOGv("%s: Preparing a batch size of %d starting at: %d", __func__, cur_batch_size, i);

        // Shift context if current batch cannot fit into the context
        if (start_pos + i + cur_batch_size >= DEFAULT_CONTEXT_SIZE - OVERFLOW_HEADROOM) {
            LOGw("%s: Current batch won't fit into context! Shifting...", __func__);
            shift_context();
        }

        // Add tokens to the batch with proper positions
        for (int j = 0; j < cur_batch_size; j++) {
            const llama_token token_id = tokens[i + j];
            const llama_pos position = start_pos + i + j;
            const bool want_logit = compute_last_logit && (i + j == tokens.size() - 1);
            batch_add(batch, token_id, position, {0}, want_logit);
        }

        // Decode this batch
        const int decode_result = llama_decode(context, batch);
        if (decode_result) {
            LOGe("%s: llama_decode failed w/ %d", __func__, decode_result);
            return 1;
        }
    }
    return 0;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_processSystemPrompt(
        JNIEnv *env,
        jobject /*unused*/,
        jstring jsystem_prompt
) {
    // Reset long-term & short-term states
    reset_long_term_states();
    reset_short_term_states();

    // Obtain system prompt from JEnv
    const auto *system_prompt = env->GetStringUTFChars(jsystem_prompt, nullptr);
    LOGd("%s: System prompt received: \n%s", __func__, system_prompt);
    std::string formatted_system_prompt(system_prompt);
    env->ReleaseStringUTFChars(jsystem_prompt, system_prompt);

    // Format system prompt if applicable
    const bool has_chat_template = g_has_chat_template;
    if (has_chat_template) {
        formatted_system_prompt = chat_add_and_format(ROLE_SYSTEM, system_prompt);
    }

    // Tokenize system prompt
    const auto system_tokens = tokenize_text(g_context, formatted_system_prompt,
                                             has_chat_template, has_chat_template);
    for (auto id: system_tokens) {
        LOGv("token: `%s`\t -> `%d`", token_to_piece(g_context, id).c_str(), id);
    }

    // Handle context overflow
    const int max_batch_size = DEFAULT_CONTEXT_SIZE - OVERFLOW_HEADROOM;
    if ((int) system_tokens.size() > max_batch_size) {
        LOGe("%s: System prompt too long for context! %d tokens, max: %d",
             __func__, (int) system_tokens.size(), max_batch_size);
        return 1;
    }

    // Decode system tokens in batches
    if (decode_tokens_in_batches(g_context, g_batch, system_tokens, current_position)) {
        LOGe("%s: llama_decode() failed!", __func__);
        return 2;
    }

    // Update position
    system_prompt_position = current_position = (int) system_tokens.size();
    return 0;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_processUserPrompt(
        JNIEnv *env,
        jobject /*unused*/,
        jstring juser_prompt,
        jint n_predict
) {
    // Reset short-term states
    reset_short_term_states();

    // Obtain and tokenize user prompt
    const auto *const user_prompt = env->GetStringUTFChars(juser_prompt, nullptr);
    LOGd("%s: User prompt received: \n%s", __func__, user_prompt);
    std::string formatted_user_prompt(user_prompt);
    env->ReleaseStringUTFChars(juser_prompt, user_prompt);

    // Format user prompt if applicable
    const bool has_chat_template = g_has_chat_template;
    if (has_chat_template) {
        formatted_user_prompt = chat_add_and_format(ROLE_USER, user_prompt);

        // Disable thinking by immediately closing the think block.
        // This matches what llama.cpp's Jinja handlers do: the template adds <think>\n
        // and when thinking is disabled, </think> is appended to close it immediately.
        if (!g_enable_thinking) {
            formatted_user_prompt += "<think>\n</think>\n";
            LOGi("%s: Thinking disabled — appended <think></think> block", __func__);
        }
    }

    // Decode formatted user prompts
    auto user_tokens = tokenize_text(g_context, formatted_user_prompt, has_chat_template, has_chat_template);
    for (auto id: user_tokens) {
        LOGv("token: `%s`\t -> `%d`", token_to_piece(g_context, id).c_str(), id);
    }

    // Ensure user prompt doesn't exceed the context size by truncating if necessary.
    const int user_prompt_size = (int) user_tokens.size();
    const int max_batch_size = DEFAULT_CONTEXT_SIZE - OVERFLOW_HEADROOM;
    if (user_prompt_size > max_batch_size) {
        const int skipped_tokens = user_prompt_size - max_batch_size;
        user_tokens.resize(max_batch_size);
        LOGw("%s: User prompt too long! Skipped %d tokens!", __func__, skipped_tokens);
    }

    // Decode user tokens in batches
    if (decode_tokens_in_batches(g_context, g_batch, user_tokens, current_position, true)) {
        LOGe("%s: llama_decode() failed!", __func__);
        return 2;
    }

    // Update position
    current_position += user_prompt_size;
    stop_generation_position = current_position + user_prompt_size + n_predict;
    return 0;
}

static bool is_valid_utf8(const char *string) {
    if (!string) { return true; }

    const auto *bytes = (const unsigned char *) string;
    int num;

    while (*bytes != 0x00) {
        if ((*bytes & 0x80) == 0x00) {
            // U+0000 to U+007F
            num = 1;
        } else if ((*bytes & 0xE0) == 0xC0) {
            // U+0080 to U+07FF
            num = 2;
        } else if ((*bytes & 0xF0) == 0xE0) {
            // U+0800 to U+FFFF
            num = 3;
        } else if ((*bytes & 0xF8) == 0xF0) {
            // U+10000 to U+10FFFF
            num = 4;
        } else {
            return false;
        }

        bytes += 1;
        for (int i = 1; i < num; ++i) {
            if ((*bytes & 0xC0) != 0x80) {
                return false;
            }
            bytes += 1;
        }
    }
    return true;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_generateNextToken(
        JNIEnv *env,
        jobject /*unused*/
) {
    // Infinite text generation via context shifting
    if (current_position >= DEFAULT_CONTEXT_SIZE - OVERFLOW_HEADROOM) {
        LOGw("%s: Context full! Shifting...", __func__);
        shift_context();
    }

    // Stop if reaching the marked position
    if (current_position >= stop_generation_position) {
        LOGw("%s: STOP: hitting stop position: %d", __func__, stop_generation_position);
        return nullptr;
    }

    // Sample next token
    const auto new_token_id = llama_sampler_sample(g_sampler, g_context, -1);
    llama_sampler_accept(g_sampler, new_token_id);

    // Populate the batch with new token, then decode
    batch_clear(g_batch);
    batch_add(g_batch, new_token_id, current_position, {0}, true);
    if (llama_decode(g_context, g_batch) != 0) {
        LOGe("%s: llama_decode() failed for generated token", __func__);
        return nullptr;
    }

    // Update position
    current_position++;

    // Stop if next token is EOG
    if (llama_vocab_is_eog(llama_model_get_vocab(g_model), new_token_id)) {
        LOGd("id: %d,\tIS EOG!\nSTOP.", new_token_id);
        chat_add_and_format(ROLE_ASSISTANT, assistant_ss.str());
        return nullptr;
    }

    // If not EOG, convert to text
    auto new_token_chars = token_to_piece(g_context, new_token_id);
    cached_token_chars += new_token_chars;

    // Create and return a valid UTF-8 Java string
    jstring result = nullptr;
    if (is_valid_utf8(cached_token_chars.c_str())) {
        result = env->NewStringUTF(cached_token_chars.c_str());
        LOGv("id: %d,\tcached: `%s`,\tnew: `%s`", new_token_id, cached_token_chars.c_str(), new_token_chars.c_str());

        assistant_ss << cached_token_chars;
        cached_token_chars.clear();
    } else {
        LOGv("id: %d,\tappend to cache", new_token_id);
        result = env->NewStringUTF("");
    }
    return result;
}


extern "C"
JNIEXPORT jint JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_nativeLoadMmproj(
        JNIEnv *env,
        jobject /*unused*/,
        jstring jmmproj_path
) {
    const auto *mmproj_path = env->GetStringUTFChars(jmmproj_path, nullptr);
    LOGi("%s: Loading mmproj from: %s", __func__, mmproj_path);

    // Route mtmd/clip logs through Android logcat
    mtmd_helper_log_set(aichat_android_log_callback, nullptr);

    struct mtmd_context_params mtmd_params = mtmd_context_params_default();
    // CLIP/vision encoder uses GPU when a GPU/NPU backend is selected.
    mtmd_params.use_gpu = (g_backend == "gpu" || g_backend == "htp");
    mtmd_params.print_timings = true;
    mtmd_params.warmup = false;
    mtmd_params.n_threads = std::max(N_THREADS_MIN, std::min(N_THREADS_MAX,
                                     (int) sysconf(_SC_NPROCESSORS_ONLN) - N_THREADS_HEADROOM));
    // Cap visual-token count if the app requested one. Persists across runs.
    if (g_image_max_tokens > 0) {
        mtmd_params.image_max_tokens = g_image_max_tokens;
        LOGi("%s: applying image_max_tokens=%d to mmproj init", __func__, g_image_max_tokens);
    }

    g_mtmd_ctx = mtmd_init_from_file(mmproj_path, g_model, mtmd_params);
    env->ReleaseStringUTFChars(jmmproj_path, mmproj_path);

    if (!g_mtmd_ctx) {
        LOGe("%s: Failed to init mtmd context!", __func__);
        return 1;
    }
    LOGi("%s: mmproj loaded successfully", __func__);
    return 0;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_nativeProcessUserPromptWithImage(
        JNIEnv *env,
        jobject /*unused*/,
        jstring juser_prompt,
        jbyteArray jimage_bytes,
        jint n_predict
) {
    if (!g_mtmd_ctx) {
        LOGe("%s: mtmd context not loaded!", __func__);
        return 1;
    }

    // Reset short-term states
    reset_short_term_states();

    // Get image bytes
    jsize image_len = env->GetArrayLength(jimage_bytes);
    auto *image_buf = (unsigned char *) env->GetByteArrayElements(jimage_bytes, nullptr);
    LOGi("%s: Decoding image (%d bytes)...", __func__, image_len);

    // Decode image from buffer (supports jpg, png, bmp, etc.)
    mtmd_bitmap *bitmap = mtmd_helper_bitmap_init_from_buf(g_mtmd_ctx, image_buf, image_len);
    env->ReleaseByteArrayElements(jimage_bytes, (jbyte *) image_buf, JNI_ABORT);
    if (!bitmap) {
        LOGe("%s: Failed to decode image!", __func__);
        return 2;
    }

    // Get user prompt and prepend the media marker
    const auto *user_prompt = env->GetStringUTFChars(juser_prompt, nullptr);
    std::string prompt_with_marker = std::string(mtmd_default_marker()) + "\n" + user_prompt;
    env->ReleaseStringUTFChars(juser_prompt, user_prompt);

    // Format with chat template
    std::string formatted_prompt = chat_add_and_format(ROLE_USER, prompt_with_marker);

    // Disable thinking by immediately closing the think block (same as processUserPrompt)
    if (!g_enable_thinking) {
        formatted_prompt += "<think>\n</think>\n";
        LOGi("%s: Thinking disabled — appended <think></think> block", __func__);
    }

    // Tokenize — interleaves text tokens and image embeddings
    mtmd_input_chunks *chunks = mtmd_input_chunks_init();
    mtmd_input_text input_text;
    input_text.text = formatted_prompt.c_str();
    input_text.add_special = true;
    input_text.parse_special = true;

    const mtmd_bitmap *bitmap_ptr = bitmap;
    int32_t tokenize_result = mtmd_tokenize(g_mtmd_ctx, chunks, &input_text, &bitmap_ptr, 1);
    mtmd_bitmap_free(bitmap);
    if (tokenize_result != 0) {
        LOGe("%s: mtmd_tokenize failed: %d", __func__, tokenize_result);
        mtmd_input_chunks_free(chunks);
        return 3;
    }

    // Eval all chunks (text + image) into KV cache
    llama_pos new_n_past = current_position;
    int32_t eval_result = mtmd_helper_eval_chunks(
        g_mtmd_ctx, g_context, chunks,
        current_position,   // n_past
        0,                  // seq_id
        BATCH_SIZE,         // n_batch
        true,               // logits_last
        &new_n_past
    );
    mtmd_input_chunks_free(chunks);
    if (eval_result != 0) {
        LOGe("%s: mtmd_helper_eval_chunks failed: %d", __func__, eval_result);
        return 4;
    }

    // Update position and set stop generation marker
    current_position = new_n_past;
    stop_generation_position = current_position + n_predict;
    LOGi("%s: Image+prompt processed. pos=%d, stop=%d", __func__, current_position, stop_generation_position);
    return 0;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_nativeProcessUserPromptWithImages(
        JNIEnv *env,
        jobject /*unused*/,
        jstring juser_prompt,
        jobjectArray jimage_arrays,
        jint n_predict
) {
    if (!g_mtmd_ctx) {
        LOGe("%s: mtmd context not loaded!", __func__);
        return 1;
    }

    // Reset short-term states
    reset_short_term_states();

    const int n_images = env->GetArrayLength(jimage_arrays);
    if (n_images <= 0) {
        LOGe("%s: No images provided!", __func__);
        return 2;
    }
    LOGi("%s: Processing %d images...", __func__, n_images);

    // Decode all images into bitmaps
    std::vector<mtmd_bitmap *> bitmaps(n_images);
    for (int i = 0; i < n_images; i++) {
        auto jimage_bytes = (jbyteArray) env->GetObjectArrayElement(jimage_arrays, i);
        jsize image_len = env->GetArrayLength(jimage_bytes);
        auto *image_buf = (unsigned char *) env->GetByteArrayElements(jimage_bytes, nullptr);
        LOGi("%s: Decoding image %d/%d (%d bytes)...", __func__, i + 1, n_images, image_len);

        bitmaps[i] = mtmd_helper_bitmap_init_from_buf(g_mtmd_ctx, image_buf, image_len);
        env->ReleaseByteArrayElements(jimage_bytes, (jbyte *) image_buf, JNI_ABORT);
        env->DeleteLocalRef(jimage_bytes);

        if (!bitmaps[i]) {
            LOGe("%s: Failed to decode image %d!", __func__, i + 1);
            // Free already-decoded bitmaps
            for (int j = 0; j < i; j++) {
                mtmd_bitmap_free(bitmaps[j]);
            }
            return 2;
        }
    }

    // Build prompt with N media markers (one per image)
    const auto *user_prompt = env->GetStringUTFChars(juser_prompt, nullptr);
    std::string prompt_with_markers;
    for (int i = 0; i < n_images; i++) {
        prompt_with_markers += mtmd_default_marker();
        prompt_with_markers += "\n";
    }
    prompt_with_markers += user_prompt;
    env->ReleaseStringUTFChars(juser_prompt, user_prompt);

    // Format with chat template
    std::string formatted_prompt = chat_add_and_format(ROLE_USER, prompt_with_markers);

    // Disable thinking by immediately closing the think block (same as processUserPrompt)
    if (!g_enable_thinking) {
        formatted_prompt += "<think>\n</think>\n";
        LOGi("%s: Thinking disabled — appended <think></think> block", __func__);
    }

    // Build const pointer array for mtmd_tokenize
    std::vector<const mtmd_bitmap *> bitmap_ptrs(n_images);
    for (int i = 0; i < n_images; i++) {
        bitmap_ptrs[i] = bitmaps[i];
    }

    // Tokenize — interleaves text tokens and image embeddings
    mtmd_input_chunks *chunks = mtmd_input_chunks_init();
    mtmd_input_text input_text;
    input_text.text = formatted_prompt.c_str();
    input_text.add_special = true;
    input_text.parse_special = true;

    int32_t tokenize_result = mtmd_tokenize(g_mtmd_ctx, chunks, &input_text, bitmap_ptrs.data(), n_images);

    // Free all bitmaps
    for (int i = 0; i < n_images; i++) {
        mtmd_bitmap_free(bitmaps[i]);
    }

    if (tokenize_result != 0) {
        LOGe("%s: mtmd_tokenize failed: %d", __func__, tokenize_result);
        mtmd_input_chunks_free(chunks);
        return 3;
    }

    // Eval all chunks (text + images) into KV cache
    llama_pos new_n_past = current_position;
    int32_t eval_result = mtmd_helper_eval_chunks(
        g_mtmd_ctx, g_context, chunks,
        current_position,   // n_past
        0,                  // seq_id
        BATCH_SIZE,         // n_batch
        true,               // logits_last
        &new_n_past
    );
    mtmd_input_chunks_free(chunks);
    if (eval_result != 0) {
        LOGe("%s: mtmd_helper_eval_chunks failed: %d", __func__, eval_result);
        return 4;
    }

    // Update position and set stop generation marker
    current_position = new_n_past;
    stop_generation_position = current_position + n_predict;
    LOGi("%s: %d images+prompt processed. pos=%d, stop=%d", __func__, n_images, current_position, stop_generation_position);
    return 0;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_nativeUnloadMmproj(JNIEnv * /*unused*/, jobject /*unused*/) {
    if (g_mtmd_ctx) {
        mtmd_free(g_mtmd_ctx);
        g_mtmd_ctx = nullptr;
        LOGi("%s: mmproj unloaded", __func__);
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_unload(JNIEnv * /*unused*/, jobject /*unused*/) {
    // Free mtmd context first
    if (g_mtmd_ctx) {
        mtmd_free(g_mtmd_ctx);
        g_mtmd_ctx = nullptr;
    }

    // Reset long-term & short-term states
    reset_long_term_states();
    reset_short_term_states();

    // Free up resources
    if (g_sampler) { llama_sampler_free(g_sampler); g_sampler = nullptr; }
    g_chat_template_str.clear();
    g_has_chat_template = false;
    llama_batch_free(g_batch);
    if (g_context) { llama_free(g_context); g_context = nullptr; }
    if (g_model)   { llama_model_free(g_model); g_model = nullptr; }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_setEnableThinking(JNIEnv *, jobject /*unused*/, jboolean enable) {
    g_enable_thinking = enable;
    LOGi("%s: enable_thinking = %s", __func__, enable ? "true" : "false");
}

extern "C"
JNIEXPORT void JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_nativeSetGrammar(JNIEnv *env, jobject /*unused*/, jstring jgrammar) {
    if (jgrammar == nullptr) {
        g_grammar.clear();
        LOGi("%s: grammar cleared", __func__);
    } else {
        const auto *grammar = env->GetStringUTFChars(jgrammar, nullptr);
        g_grammar = grammar;
        env->ReleaseStringUTFChars(jgrammar, grammar);
        LOGi("%s: grammar set (%zu chars)", __func__, g_grammar.size());
    }
    // Recreate sampler with new grammar if model is loaded
    if (g_model) {
        if (g_sampler) { llama_sampler_free(g_sampler); g_sampler = nullptr; }
        g_sampler = new_sampler(DEFAULT_SAMPLER_TEMP);
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_shutdown(JNIEnv *, jobject /*unused*/) {
    llama_backend_free();
}
