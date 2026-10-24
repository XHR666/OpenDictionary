# 本地 LLM 测试 APK（llama.cpp + Qwen2.5-0.5B）

> 目的：先跑通"手机上完全离线的本地大模型"，用于验证速度/内存/发热与词义解释效果；正式 App 通过 `llm/engine-api` 接入。

## 文件

| 文件 | 说明 |
|---|---|
| `llama-test.apk` | 测试 APK（debug，minSdk 33，arm64-v8a；基于 llama.cpp 官方 `examples/llama.android`） |
| `download-model.sh` | **GitHub Releases 优先**下载模型（回退 ModelScope 开发源）+ sha256 校验；可选 qwen3-0.6b / qwen2.5-1.5b / qwen2.5-0.5b |
| `publish-models-release.sh` | 把模型上传到 resource 仓 GitHub Releases（models-v1） |

模型：**Qwen2.5-0.5B-Instruct GGUF Q4_K_M**
- sha256：`74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db`
- 许可：Apache-2.0（ModelScope LICENSE 已核实）
- 大小：491,400,032 B

## 安装与测试

```bash
# 1) 手机开启 USB 调试，安装 APK
adb install -r app-test/llama-test.apk

# 2) 下载模型（电脑）
bash app-test/download-model.sh

# 3) 推到手机
adb push qwen2.5-0.5b-instruct-q4_k_m.gguf /sdcard/Download/

# 4) 手机上打开 "AiChat" → 选择 GGUF 文件 → 等待加载 → 输入问题测试
```

## 请回填的测试数据

| 指标 | 实测 | 备注 |
|---|---|---|
| 首 token 延迟 | ___ ms | 加载完成后第一次提问 |
| 生成速度 | ___ tok/s | 20–50 字回复平均 |
| 峰值内存 | ___ MB | 设置→开发者→内存 / adb shell dumpsys meminfo |
| 发热/掉电 | ___ | 连续 5 分钟对话 |
| Android 版本/机型 | ___ | 天玑 9300 目标机优先 |

## 说明与限制

- 0.5B 模型能力有限：适合**词义解释草稿、例句改写、简单问答**，不适合长文批改；
- 纯 CPU 推理，无 GMS 依赖；首次加载需数秒；
- APK 为 llama.cpp 官方示例，仅用于本地推理能力验证；正式应用将使用独立的模型管理界面。

## 备用测试路径：Termux 原生（无需 APK）

如果暂时没有 APK，可先在手机上用 Termux 原生运行 llama.cpp 验证本地 LLM：

```bash
# 手机安装 Termux（F-Droid），执行：
bash app-test/termux-local-llm.sh qwen3-0.6b   # 或 qwen2.5-1.5b
```

记录首 token、tok/s、内存与发热；正式构建步骤见仓库 README。

