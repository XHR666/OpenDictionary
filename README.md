# OpenDictionary

离线优先的 Android 英语学习应用。无广告、无账号、不主动收费；词典与例句以本地数据为主，可选装本地 LLM。

当前为 **0.6.105 预发布测试版**。主要能力：

- 离线词典：15 万词条、前缀/包含/后缀/纠错查询、中文反查、大小写处理、12 分区词条页、发音与词形/熟词生义/口语音标
- 学习：多词书、卡片刷词、折叠法、SM-2 复习、背诵（音→词/中→词，慢/正常/快语速）、每日提醒、学习热力图
- 阅读：RSS 时文与自动练习、txt/md/PDF/EPUB 导入、点词查义、高亮/下划线
- 词条：结构化 JSON 查看/复制；维基百科外链摘要抓取并本地缓存（可刷新）
- 练习：CET 翻译、IELTS 阅读/听力样例、写作与手写（橡皮擦/撤销）、本地 AI 批改
- 词书：资源包下载（SHA-256/大小/词数校验）、文件导入、Read Me、重命名/删除
- 数据：JSON 备份/恢复、Web 词书管理页、GitHub Releases 预发布更新与断点续传
- 其他：本地 LLM 推理（模型按需下载）、动画程度与 Liquid Glass 三档（基于 Apache-2.0 的 AndroidLiquidGlass 实时模糊/折射）、四套主题配色、原声语料来源页、题库与阅读（内置 4990 篇高考/IELTS/AGIEval/SQuAD 阅读与试题）、补充词书/短语、构词法解剖、平板双栏词条页、宽屏导航栏、设置分类双栏、内容与数据来源页、TalkBack/字号缩放、自定义字体与背景、许可页

## 构建

需要 JDK 17、Android SDK 36（platform/build-tools）与 Gradle 8.14.3。

```bash
export JAVA_HOME="<JDK17>"
export ANDROID_HOME="<Android SDK>"
gradle -p app :app:assembleDebug \
  -Pandroid.aapt2FromMavenOverride="<可用的 aapt2 路径>"
```

- Kotlin + Jetpack Compose；本地 LLM 使用预编译 arm64 JNI `.so`（`app/src/main/jniLibs/arm64-v8a`），因此发布 APK 仅支持 arm64-v8a。
- 在 arm64 Linux 上构建时，需提供一个可用的 aapt2 并通过 `-Pandroid.aapt2FromMavenOverride` 传入。
- 单元测试与 lint：`gradle -p app :app:testDebugUnitTest :app:lintDebug`。

## 构建

见 `BUILD.md`。

## 更新

应用内"检查更新"只读取 GitHub Releases 中的**预发布版本（prerelease）**，且要求版本资产同时包含 `update.json` 与 `.apk`。提交代码、修改 README 或推送分支都不会触发更新检测。契约与发布流程见 `UPDATE.md`；预发布更新源：https://github.com/XHR666/OpenDictionary-apk

## 目录

| 路径 | 内容 |
|---|---|
| `app/` | Android 应用源码（Kotlin + Jetpack Compose） |
| `app-test/` | 本地测试构建产物（已 gitignore） |
| `scripts/` | 预发布发布/校验脚本 |
| `NOTICE` / `LICENSES/` | 第三方归属与许可 |
| `tools/dashboard/` | 本地数据审核工具 |
| `UPDATE.md` | 更新契约 |

## 许可

- 代码：AGPL-3.0-or-later，见 `LICENSE`。
- 项目自编内容：CC BY-NC-SA 4.0；第三方内容保留原许可，见 `NOTICE`。
- 第三方数据、模型、字体等按各自许可提供，不被本仓库重新许可。
- 已发布的 APK 对应源码可在本仓库获取。
