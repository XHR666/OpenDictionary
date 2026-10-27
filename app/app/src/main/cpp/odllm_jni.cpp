#include <jni.h>
#include <string>
#include <vector>
#include <mutex>
#include "llama.h"

static llama_model * g_model = nullptr;
static llama_context * g_ctx = nullptr;
static const llama_vocab * g_vocab = nullptr;
static std::mutex g_mutex;

static std::string jstr(JNIEnv * env, jstring s) {
    if (!s) return {};
    const char * c = env->GetStringUTFChars(s, nullptr);
    std::string out(c ? c : "");
    env->ReleaseStringUTFChars(s, c);
    return out;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_io_github_xhr666_opendictionary_llm_LocalLlm_nativeLoad(JNIEnv * env, jobject, jstring path, jint threads, jint nctx) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_ctx) { llama_free(g_ctx); g_ctx = nullptr; }
    if (g_model) { llama_model_free(g_model); g_model = nullptr; }
    g_vocab = nullptr;
    llama_backend_init();
    auto mp = llama_model_default_params();
    std::string p = jstr(env, path);
    g_model = llama_model_load_from_file(p.c_str(), mp);
    if (!g_model) return JNI_FALSE;
    g_vocab = llama_model_get_vocab(g_model);
    auto cp = llama_context_default_params();
    cp.n_ctx = (uint32_t) (nctx > 0 ? nctx : 2048);
    cp.n_threads = (int32_t) (threads > 0 ? threads : 4);
    cp.n_threads_batch = cp.n_threads;
    g_ctx = llama_init_from_model(g_model, cp);
    return g_ctx ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_io_github_xhr666_opendictionary_llm_LocalLlm_nativeGenerate(JNIEnv * env, jobject, jstring prompt, jint maxTokens) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_ctx || !g_vocab) return env->NewStringUTF("");
    std::string user = jstr(env, prompt);
    std::string full = user;
    const char * tmpl = llama_model_chat_template(g_model, nullptr);
    if (tmpl) {
        llama_chat_message msg;
        msg.role = "user";
        msg.content = user.c_str();
        std::vector<char> buf(8192);
        int32_t n = llama_chat_apply_template(tmpl, &msg, 1, true, buf.data(), (int32_t) buf.size());
        if (n > 0) {
            if ((size_t) n > buf.size()) {
                buf.resize(n);
                n = llama_chat_apply_template(tmpl, &msg, 1, true, buf.data(), (int32_t) buf.size());
            }
            if (n > 0) full.assign(buf.data(), buf.data() + n);
        }
    }
    std::vector<llama_token> tokens(full.size() + 64);
    int32_t n_tok = llama_tokenize(g_vocab, full.data(), (int32_t) full.size(), tokens.data(), (int32_t) tokens.size(), true, true);
    if (n_tok < 0) {
        tokens.resize((size_t) (-n_tok));
        n_tok = llama_tokenize(g_vocab, full.data(), (int32_t) full.size(), tokens.data(), (int32_t) tokens.size(), true, true);
    }
    if (n_tok <= 0) return env->NewStringUTF("");
    tokens.resize((size_t) n_tok);
    llama_batch batch = llama_batch_get_one(tokens.data(), (int32_t) tokens.size());
    if (llama_decode(g_ctx, batch) != 0) return env->NewStringUTF("");

    auto sp = llama_sampler_chain_default_params();
    sp.no_perf = true;
    llama_sampler * smpl = llama_sampler_chain_init(sp);
    llama_sampler_chain_add(smpl, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_p(0.95f, 1));
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(0.7f));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    std::string out;
    int32_t limit = maxTokens > 0 ? maxTokens : 160;
    for (int32_t i = 0; i < limit; ++i) {
        llama_token id = llama_sampler_sample(smpl, g_ctx, -1);
        if (llama_vocab_is_eog(g_vocab, id)) break;
        char piece[256];
        int32_t n = llama_token_to_piece(g_vocab, id, piece, (int32_t) sizeof(piece), 0, false);
        if (n > 0) out.append(piece, (size_t) n);
        llama_batch one = llama_batch_get_one(&id, 1);
        if (llama_decode(g_ctx, one) != 0) break;
    }
    llama_sampler_free(smpl);
    return env->NewStringUTF(out.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_xhr666_opendictionary_LocalLlm_nativeFree(JNIEnv *, jobject) { }

extern "C" JNIEXPORT void JNICALL
Java_io_github_xhr666_opendictionary_llm_LocalLlm_nativeFree(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_ctx) { llama_free(g_ctx); g_ctx = nullptr; }
    if (g_model) { llama_model_free(g_model); g_model = nullptr; }
    g_vocab = nullptr;
    llama_backend_free();
}
