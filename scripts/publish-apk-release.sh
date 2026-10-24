#!/usr/bin/env bash
# 发布 APK 预发布版本（只创建 prerelease，不创建正式 release）。
# 用法: scripts/publish-apk-release.sh <versionCode> <versionName> <apk路径> "<更新说明>"
set -euo pipefail
REPO="${OD_APK_REPO:-XHR666/OpenDictionary-apk}"
PKG="io.github.xhr666.opendictionary"
CODE="${1:?versionCode 必填}"; NAME="${2:?versionName 必填}"; APK="${3:?apk 路径必填}"; NOTES="${4:-预发布版本}"
[ -f "$APK" ] || { echo "找不到 APK: $APK"; exit 2; }
SIZE="$(stat -c%s "$APK")"
SHA="$(sha256sum "$APK" | awk '{print $1}')"
ASSET="opendictionary-${NAME}.apk"
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
cp "$APK" "$TMP/$ASSET"
cat > "$TMP/update.json" <<JSON
{
  "schema": 1,
  "packageName": "$PKG",
  "versionCode": $CODE,
  "versionName": "$NAME",
  "minSdk": 33,
  "apkAsset": "$ASSET",
  "sha256": "$SHA",
  "size": $SIZE,
  "publishedAt": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "notes": "$NOTES"
}
JSON
TAG="pre-v${NAME}"
if gh release view "$TAG" -R "$REPO" >/dev/null 2>&1; then
  echo "已存在 tag: $TAG（不会覆盖，请提升版本号）"; exit 3
fi
gh release create "$TAG" "$TMP/$ASSET" "$TMP/update.json" -R "$REPO" \
  --prerelease --title "OpenDictionary ${NAME}" --notes "$NOTES"
gh release view "$TAG" -R "$REPO" --json tagName,isPrerelease,assets \
  --jq '{tag:.tagName, prerelease:.isPrerelease, assets:[.assets[].name]}'
