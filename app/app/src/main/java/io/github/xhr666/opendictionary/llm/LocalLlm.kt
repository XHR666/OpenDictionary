package io.github.xhr666.opendictionary.llm

/** 本地推理引擎（llama.cpp JNI）；模型由用户下载或导入，不随安装包分发。 */
object LocalLlm {
    private val libs = listOf("c++_shared", "ggml-base", "ggml-cpu", "ggml", "llama", "odllm")
    private var loaded = false
    private var modelLoaded = false

    private external fun nativeLoad(path: String, threads: Int, nctx: Int): Boolean
    private external fun nativeGenerate(prompt: String, maxTokens: Int): String
    private external fun nativeFree()

    @Synchronized
    fun ensureLoaded() {
        if (!loaded) {
            libs.forEach { System.loadLibrary(it) }
            loaded = true
        }
    }

    @Synchronized
    fun loadModel(path: String, threads: Int = 4, nctx: Int = 2048): Boolean {
        ensureLoaded()
        modelLoaded = nativeLoad(path, threads.coerceIn(1, 4), nctx.coerceIn(512, 8192))
        return modelLoaded
    }

    @Synchronized
    fun generate(prompt: String, maxTokens: Int = 180): String {
        if (!modelLoaded) return ""
        return runCatching { nativeGenerate(prompt, maxTokens.coerceIn(16, 1024)) }.getOrDefault("")
    }

    @Synchronized
    fun unload() {
        if (!loaded) return
        runCatching { nativeFree() }
        modelLoaded = false
    }

    val isModelLoaded: Boolean get() = modelLoaded
}
