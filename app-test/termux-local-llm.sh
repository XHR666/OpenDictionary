#!/usr/bin/env bash
# 在 Android 手机上直接跑本地 LLM（Termux 原生，无需 APK）
# 适用：arm64 安卓手机（天玑 9300 等）；一次安装后可持续测试。
set -euo pipefail
MODEL="${1:-qwen3-0.6b}"
WORK="${HOME}/od-llm"
mkdir -p "$WORK" && cd "$WORK"

echo "== 1/4 安装依赖（Termux） =="
pkg update -y
pkg install -y clang cmake git curl

echo "== 2/4 获取 llama.cpp =="
if [ ! -d llama.cpp/.git ]; then
  git clone --depth 1 https://github.com/ggml-org/llama.cpp.git
else
  git -C llama.cpp pull --ff-only || true
fi

echo "== 3/4 编译 llama-cli（arm64，约 5–15 分钟） =="
cmake -S llama.cpp -B llama.cpp/build \
  -DCMAKE_BUILD_TYPE=Release -DLLAMA_CURL=OFF -DLLAMA_OPENSSL=OFF \
  -DLLAMA_BUILD_TESTS=OFF -DGGML_NATIVE=ON
cmake --build llama.cpp/build -j"$(nproc)" --target llama-cli

echo "== 4/4 下载模型（GitHub Releases 优先） =="
bash "$(dirname "$0")/download-model.sh" "$MODEL"

MODEL_FILE="$WORK/$(case "$MODEL" in
  qwen3-0.6b) echo qwen3-0.6b-q8_0.gguf;;
  qwen2.5-1.5b) echo qwen2.5-1.5b-instruct-q4_k_m.gguf;;
  qwen2.5-0.5b) echo qwen2.5-0.5b-instruct-q4_k_m.gguf;;
  *) echo unknown; exit 2;; esac)"
[ -f "$MODEL_FILE" ] || MODEL_FILE="$(ls -t *.gguf | head -1)"

echo "== 启动测试（生成线程固定 4：MediaTek 超过大核数会性能崩塌） =="
exec llama.cpp/build/bin/llama-cli -m "$MODEL_FILE" -t 4 -c 2048 -n 160 \
  --temp 0.7 -p "Explain the English word \"reputation\" in simple Chinese, with 2 example sentences."
