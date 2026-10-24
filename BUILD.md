# 构建说明

## 环境

- JDK 17
- Android SDK：platform 34、build-tools 34
- Gradle 8.9（或兼容的 Gradle 8.x）

```bash
export JAVA_HOME="<JDK17>"
export ANDROID_HOME="<Android SDK>"
gradle -p app :app:assembleDebug \
  -Pandroid.aapt2FromMavenOverride="<可用的 aapt2 路径>"
```

> `android.aapt2FromMavenOverride` 仅用于 arm64 Linux 等需要替换官方 aapt2 的环境；x86_64/macOS 通常可省略。

## 词典数据

安装包内置的 `app/app/src/main/assets/lexicon.db` 由资源仓库脚本生成，不提交到源码仓库：

```bash
# 1. 获取完整词典库（构建流程生成的 lexicon_ecdict_full.db）
# 2. 生成 App 内置紧凑库
python3 ../OpenDictionary-resource/scripts/build_app_lexicon.py \
  --source <lexicon_ecdict_full.db> \
  --out app/app/src/main/assets/lexicon.db \
  --limit 150000
```

## 本地模型

模型不随 APK 分发；应用内可下载或导入 GGUF。原生库位于 `app/app/src/main/jniLibs/arm64-v8a/`（llama.cpp，MIT）。

## 签名

发布签名使用本地 keystore；构建配置从仓库外的本地文件读取，keystore 与密码不提交。没有本地配置时 release 构建会失败，请使用 debug 构建或自行配置签名。
