#!/usr/bin/env bash
# 把本地模型上传到 resource 仓 GitHub Releases（models-v1），供 App/用户从 GitHub 拉取。
# 前置：gh 已登录；resource 仓已 push（默认 XHR666/OpenDictionary-resource）。
# 用法：bash app-test/publish-models-release.sh [路径前缀，默认 ./models]
set -euo pipefail
REPO="${OD_RESOURCE_REPO:-XHR666/OpenDictionary-resource}"
TAG="${OD_MODEL_TAG:-models-v1}"
DIR="${1:-./models}"
FILES=(
  "qwen3-0.6b-q8_0.gguf"
  "qwen2.5-1.5b-instruct-q4_k_m.gguf"
  "qwen2.5-0.5b-instruct-q4_k_m.gguf"
)
if ! gh release view "$TAG" -R "$REPO" >/dev/null 2>&1; then
  gh release create "$TAG" -R "$REPO" --title "本地 LLM 模型（Apache-2.0）" \
    --notes "Qwen3-0.6B(Q8_0)/Qwen2.5-1.5B(Q4_K_M)/Qwen2.5-0.5B(Q4_K_M)，Apache-2.0；App 按 manifest 下载并校验 sha256。"
fi
for f in "${FILES[@]}"; do
  if [ -f "$DIR/$f" ]; then gh release upload "$TAG" "$DIR/$f" -R "$REPO" --clobber; else echo "跳过（不存在）：$DIR/$f"; fi
done
echo "OK Releases 资产："; gh release view "$TAG" -R "$REPO" --json assets --jq '.assets[].name + "  " + (.size|tostring)'
