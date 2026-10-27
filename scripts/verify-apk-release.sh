#!/usr/bin/env bash
# 校验 APK 预发布 feed：只应有 prerelease；每个版本须含 update.json + .apk。
set -euo pipefail
REPO="${OD_APK_REPO:-XHR666/OpenDictionary-apk}"
gh api "repos/$REPO/releases" --jq '
  [.[] | select(.prerelease == false and .draft == false) | .tag_name] as $official |
  if ($official | length) > 0 then "FAIL 存在正式 release: \($official)" | halt_error(1) else empty end,
  .[] | select(.prerelease == true and (.tag_name | startswith("models-") | not)) |
  "\(.tag_name) prerelease=\(.prerelease) assets=\([.assets[].name] | join(","))"'
echo OK
