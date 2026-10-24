# Dashboard（后台工具）· v3（补充工作台 + 公共备注/AI 导入）

本地数据补充工具：总览 / 按 A→Z 逐词补充 / 公共备注 / 发音 / AI 批量导入 / 词书管理 / 上游更新。

## 运行

```bash
python3 tools/dashboard/app.py --port 8765      # http://127.0.0.1:8765/
python3 tools/dashboard/app.py --self-test      # 18 项自检（能失败）
```

- 只读词典库：`../OpenDictionary-resource/work/data/lexicon_core.db`（`--lexicon` / `OD_LEXICON_DB`）
- 本地补充库：`work/dashboard/review.db`（`.gitignore` 排除；`--data` / `OD_DASH_DATA`）
- 只绑 `127.0.0.1`；不包含密钥或令牌。

## 功能（v3）

- **补充 = 给词条补数据/纠错**，没有"通过率"；状态 待补充/补充中/已补充/存疑。
- 词条按 **A→Z 字典序**（首字母栏+分页）；侧边栏搜索框可直接搜词补充（Ctrl+/ 聚焦）。
- 每词两个输入框：① AI 查询请求（预填提示词）② 补充内容（纯文本/JSON/Markdown 自动识别）。
- **公共备注**：写备注 → 导出 `work/dashboard/export/public-notes.json`（随资源仓分发，App 内所有人可见；无备注则不显示）。
- **发音**：词条页 🔊 按钮（浏览器 TTS；不支持时提示）。
- **AI 结果批量导入**：`/import` 粘贴多词回复（JSON 数组/对象、`### word` 标题块、`---` 分隔块），逐块校验写入补充库。
- **猜你需要/缺项提示**：启发式提示缺哪些字段（不判决）。
- **数据质量提示**：qwerty/kajweb 等 issue 教训（音标来源、标签来源、例句版权、百科只给链接）。
- **词书管理**：默认词书（随 APK 压缩包）只读 + 本地词书增删。

## 我的单词本（Web 管理）

启动后访问 `/user-wordbooks`：上传 App「备份与恢复」导出的 JSON，可在浏览器编辑词书名称、增删单词、搜索某词出现在哪些词书，最后导出 JSON 回到 App 导入（合并模式）。数据只在浏览器内存处理，不上传服务器。

## 外链内容缓存（Dashboard 重新抓取）

访问 `/external-cache`：上传 App 备份 JSON，对其中的 external 词条从维基百科重新抓取摘要，可逐条/全部刷新并导出 JSON 回 App「备份与恢复」导入。API：`/api/external/fetch?word=...`；内容 CC BY-SA 4.0，保留原页面链接与抓取时间。

## 我的单词本 / 自定义词条

访问 `/user-wordbooks`：上传 App 备份 JSON，可编辑词书与单词，也可增删改 `customEntries`（App 设置→自定义词条保存/在线查询保存的新词与专业术语），再导出 JSON 回 App 导入。数据只在浏览器内存处理。
