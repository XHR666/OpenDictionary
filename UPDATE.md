# 更新机制（UPDATE.md）

本 App 的"检查更新"只读取 **APK 发布仓库的 GitHub Releases**，并要求该 Release：

1. 标记为 **prerelease**（预发布），不是正式 release；
2. 资产（assets）里同时有 **`update.json`** 和 **`.apk`** 文件；
3. `update.json` 合法且 `versionCode` 大于当前 App 的 `versionCode`。

因此 **提交代码、修改 README、push 分支都不会触发更新**——只有按契约创建的预发布版本才会被检测到。

## update.json 契约

```json
{
  "schema": 1,
  "packageName": "io.github.xhr666.opendictionary",
  "versionCode": 2,
  "versionName": "0.1.1-pre",
  "minSdk": 33,
  "apkAsset": "opendictionary-0.1.1-pre.apk",
  "sha256": "<64 位十六进制>",
  "size": 12345678,
  "publishedAt": "2026-10-22T00:00:00Z",
  "notes": "本版更新说明"
}
```

校验规则：

- `packageName` 必须等于 `io.github.xhr666.opendictionary`；
- `versionCode > 当前版本`，`minSdk <= 当前设备 SDK`；
- `sha256` 为 64 位十六进制、`size > 0`；
- APK 资产名必须与 `apkAsset` 一致，且文件与 `sha256`/`size` 对应。

## 发布预发布版本

```bash
# 只创建 prerelease，不创建正式 release
scripts/publish-apk-release.sh <versionCode> <versionName> <apk路径> "<更新说明>"
```

脚本会：计算 APK 的 sha256/size → 生成 `update.json` → `gh release create --prerelease` → 上传 APK + update.json。
发布后可用 `gh release view` 或 `scripts/verify-apk-release.sh` 校验资产。
