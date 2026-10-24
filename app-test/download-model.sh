#!/usr/bin/env bash
# 下载本地 LLM（GitHub Releases 优先；开发期回退 ModelScope），并校验 sha256。
# 用法：bash app-test/download-model.sh [qwen3-0.6b|qwen2.5-1.5b|qwen2.5-0.5b]
set -euo pipefail
MODEL="${1:-qwen3-0.6b}"
GH_BASE="${OD_MODEL_GH_BASE:-https://github.com/XHR666/OpenDictionary-resource/releases/download/models-v1}"
MS_BASE="https://modelscope.cn/models"

declare -A NAME FILE SIZE SHA MS_PATH
NAME[qwen3-0.6b]="Qwen3-0.6B Q8_0"
FILE[qwen3-0.6b]="qwen3-0.6b-q8_0.gguf"
SIZE[qwen3-0.6b]="639446688"
SHA[qwen3-0.6b]="9465e63a22add5354d9bb4b99e90117043c7124007664907259bd16d043bb031"
MS_PATH[qwen3-0.6b]="Qwen/Qwen3-0.6B-GGUF/resolve/master/Qwen3-0.6B-Q8_0.gguf"

NAME[qwen2.5-1.5b]="Qwen2.5-1.5B-Instruct Q4_K_M"
FILE[qwen2.5-1.5b]="qwen2.5-1.5b-instruct-q4_k_m.gguf"
SIZE[qwen2.5-1.5b]="1117320736"
SHA[qwen2.5-1.5b]="6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e"
MS_PATH[qwen2.5-1.5b]="Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/master/qwen2.5-1.5b-instruct-q4_k_m.gguf"

NAME[qwen2.5-0.5b]="Qwen2.5-0.5B-Instruct Q4_K_M"
FILE[qwen2.5-0.5b]="qwen2.5-0.5b-instruct-q4_k_m.gguf"
SIZE[qwen2.5-0.5b]="491400032"
SHA[qwen2.5-0.5b]="74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db"
MS_PATH[qwen2.5-0.5b]="Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/master/qwen2.5-0.5b-instruct-q4_k_m.gguf"

if [ -z "${FILE[$MODEL]:-}" ]; then echo "未知模型：$MODEL（可选 qwen3-0.6b / qwen2.5-1.5b / qwen2.5-0.5b）"; exit 2; fi
DEST="${2:-${FILE[$MODEL]}}"
URL="$GH_BASE/${FILE[$MODEL]}"
SHA_EXPECT="${SHA[$MODEL]}"

echo "→ 模型：${NAME[$MODEL]}（${SIZE[$MODEL]} B，Apache-2.0）"
echo "→ 优先 GitHub：$URL"
if ! curl -L --fail -C - --retry 3 --connect-timeout 10 -o "$DEST" "$URL"; then
  echo "⚠ GitHub 不可达（可能尚未创建 Release），回退 ModelScope 开发源"
  curl -L --fail -C - --retry 3 -o "$DEST" "$MS_BASE/${MS_PATH[$MODEL]}"
fi
ACTUAL_SIZE="$(stat -c%s "$DEST")"
if [ "$ACTUAL_SIZE" != "${SIZE[$MODEL]}" ]; then echo "FAIL 大小不符：$ACTUAL_SIZE != ${SIZE[$MODEL]}"; exit 1; fi
if [ -n "$SHA_EXPECT" ]; then
  ACTUAL_SHA="$(sha256sum "$DEST" | awk '{print $1}')"
  if [ "$ACTUAL_SHA" != "$SHA_EXPECT" ]; then echo "FAIL sha256 不符：$ACTUAL_SHA"; exit 1; fi
  echo "OK sha256 校验通过"
else
  echo "⚠ 该模型 sha256 尚未回填（先记录：$(sha256sum "$DEST" | awk '{print $1}')）"
fi
echo "OK 模型就绪：$DEST"
