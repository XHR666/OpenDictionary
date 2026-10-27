#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""OpenDictionary 数据后台 · v2（本地开发工具，只绑 127.0.0.1）

v2 修订：
 1) 复核语义 = 补充（给词条补数据/纠错），不判断对错，取消"通过率"；
 2) 单词按 A→Z 字母顺序（首字母分栏 + 分页），逐词提交补充；
 3) 每个词两个输入框：① AI 查询请求（可复制）② 补充内容（纯文本/JSON/Markdown 自动识别）；
 4) 词条页显示开源项目 issue/PR 的常见数据错误提示；
 5) 新增词书管理（默认词书只读 + 本地词书增删）；
 6) 界面重做（侧边栏 + 卡片 + 字母栏），去掉通过/打回。
启动：python3 tools/dashboard/app.py --port 8765 → http://127.0.0.1:8765/
自检：python3 tools/dashboard/app.py --self-test
"""
import argparse, datetime as _dt, html, json, os, re, sqlite3, sys, tempfile, urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
DEFAULT_LEXICON = os.path.join(ROOT, "..", "OpenDictionary-resource", "work", "data", "lexicon_core.db")
DEFAULT_DATA = os.path.join(ROOT, "work", "dashboard")
UPSTREAM = {"ECDICT": "skywind3000/ECDICT", "OpenEnglishWordNet": "globalwordnet/english-wordnet"}

KNOWN_ISSUES = [
    ("音标", "qwerty-learner #739：部分词音标/发音不对；音标必须标英/美来源，不能混用。"),
    ("音标", "kajweb/dict #16：不同词典音标不一致；多源必须留 source 字段。"),
    ("释义", "ECDICT 多源拼装、更新停在 2017；释义顺序需按常用度人工复核。"),
    ("考试标签", "标签记录来源与年份（大纲会变），培训词表不得当官方大纲。"),
    ("例句", "禁止复制其他词典/商业词书例句编排（读书郎/《汉语大词典》例证案，索赔 1024 万）。"),
    ("百科", "维基/百度百科只给链接 + 自写摘要（≤120 字）；百度百科许可未核实。"),
    ("短语", "2ndLA 短语是“高中常见短语”，非官方高考大纲；CC BY-SA 4.0 需署名+同许可。"),
    ("词形", "exchange/词形要能回到 lemma；不规则变化单独标注。"),
]


def now():
    return _dt.datetime.now().strftime("%Y-%m-%d %H:%M:%S")


def lex_conn(path):
    if not os.path.isfile(path):
        raise FileNotFoundError("词典库不存在：%s（先跑 build_lexicon_db.py）" % path)
    con = sqlite3.connect("file:%s?mode=ro" % path, uri=True)
    con.row_factory = sqlite3.Row
    return con


def review_conn(data_dir):
    os.makedirs(data_dir, exist_ok=True)
    con = sqlite3.connect(os.path.join(data_dir, "review.db"))
    con.row_factory = sqlite3.Row
    con.executescript("""
        CREATE TABLE IF NOT EXISTS supplements(
            word TEXT PRIMARY KEY,
            status TEXT NOT NULL CHECK(status IN ('pending','partial','done','question')),
            payload TEXT DEFAULT '', payload_kind TEXT DEFAULT 'text',
            ai_request TEXT DEFAULT '', notes TEXT DEFAULT '', reviewer TEXT DEFAULT '',
            updated_at TEXT NOT NULL);
        CREATE TABLE IF NOT EXISTS local_wordbooks(
            book_id TEXT PRIMARY KEY, name TEXT NOT NULL, description TEXT DEFAULT '', created_at TEXT NOT NULL);
        CREATE TABLE IF NOT EXISTS local_wordbook_items(
            book_id TEXT NOT NULL, word TEXT NOT NULL, added_at TEXT NOT NULL, PRIMARY KEY(book_id, word));
        CREATE TABLE IF NOT EXISTS public_notes(
            word TEXT PRIMARY KEY, note TEXT NOT NULL, author TEXT DEFAULT '', updated_at TEXT NOT NULL);
        CREATE TABLE IF NOT EXISTS audit_log(
            id INTEGER PRIMARY KEY AUTOINCREMENT, ts TEXT NOT NULL, action TEXT NOT NULL, word TEXT, detail TEXT);
        CREATE TRIGGER IF NOT EXISTS audit_no_update BEFORE UPDATE ON audit_log
        BEGIN SELECT RAISE(ABORT,'audit_log is append-only'); END;
        CREATE TRIGGER IF NOT EXISTS audit_no_delete BEFORE DELETE ON audit_log
        BEGIN SELECT RAISE(ABORT,'audit_log is append-only'); END;
    """)
    con.commit()
    return con


def audit(con, action, word=None, detail=""):
    con.execute("INSERT INTO audit_log(ts,action,word,detail) VALUES(?,?,?,?)", (now(), action, word, detail))
    con.commit()


def lex_stats(lex_path):
    con = lex_conn(lex_path)
    try:
        has_ph = con.execute("SELECT name FROM sqlite_master WHERE name='phrase_entries'").fetchone()
        return {
            "entries": con.execute("SELECT COUNT(*) FROM entries").fetchone()[0],
            "phrases": con.execute("SELECT COUNT(*) FROM phrase_entries").fetchone()[0] if has_ph else 0,
            "phrase_kinds": {r["kind"]: r["n"] for r in con.execute("SELECT kind,COUNT(*) n FROM phrase_entries GROUP BY kind ORDER BY n DESC")} if has_ph else {},
            "tags": {r["tag"]: r["n"] for r in con.execute("SELECT tag,COUNT(*) n FROM word_tags GROUP BY tag ORDER BY n DESC")},
            "wordbooks": [dict(r) for r in con.execute("SELECT book_id,name,description FROM wordbooks ORDER BY book_id")],
            "db_size_mb": round(os.path.getsize(lex_path) / 1048576, 1),
        }
    finally:
        con.close()


def supplement_stats(rev):
    rows = {r["status"]: r["n"] for r in rev.execute("SELECT status,COUNT(*) n FROM supplements GROUP BY status")}
    return {"total": sum(rows.values()), "pending": rows.get("pending", 0), "partial": rows.get("partial", 0),
            "done": rows.get("done", 0), "question": rows.get("question", 0)}


def letters_with_counts(lex_path):
    con = lex_conn(lex_path)
    try:
        rows = con.execute("SELECT upper(substr(word,1,1)) l, COUNT(*) n FROM entries GROUP BY l ORDER BY l").fetchall()
        return [{"letter": r["l"], "count": r["n"]} for r in rows if r["l"] and r["l"].isalpha()]
    finally:
        con.close()


def list_words(lex_path, letter=None, q=None, page=1, size=50):
    con = lex_conn(lex_path)
    try:
        where, args = [], []
        if q:
            where.append("word LIKE ?"); args.append(q.replace("%", "").replace("_", "") + "%")
        elif letter:
            where.append("upper(substr(word,1,1))=upper(?)"); args.append(letter)
        w = (" WHERE " + " AND ".join(where)) if where else ""
        total = con.execute("SELECT COUNT(*) FROM entries" + w, args).fetchone()[0]
        rows = con.execute("SELECT word,translation,tag FROM entries" + w +
                           " ORDER BY word COLLATE NOCASE LIMIT ? OFFSET ?",
                           args + [size, (page - 1) * size]).fetchall()
        return {"total": total, "page": page, "size": size, "items": [dict(r) for r in rows]}
    finally:
        con.close()


def phrase_stats(lex_path, rev):
    con = lex_conn(lex_path)
    try:
        total = con.execute("SELECT COUNT(*) FROM phrase_entries").fetchone()[0]
        by_book = {r["book_id"]: r["n"] for r in con.execute(
            "SELECT book_id,COUNT(*) n FROM phrase_entries GROUP BY book_id ORDER BY n DESC")}
        by_kind = {r["kind"]: r["n"] for r in con.execute(
            "SELECT kind,COUNT(*) n FROM phrase_entries GROUP BY kind ORDER BY n DESC")}
        words = [r[0] for r in rev.execute("SELECT word FROM supplements")]
        done = 0
        if words:
            marks = ",".join("?" * len(words))
            done = con.execute("SELECT COUNT(*) FROM phrase_entries WHERE phrase IN (%s)" % marks, words).fetchone()[0]
        return {"total": total, "by_book": by_book, "by_kind": by_kind, "supplemented": done}
    finally:
        con.close()


def list_phrases(lex_path, q=None, book=None, page=1, size=50):
    con = lex_conn(lex_path)
    try:
        where, args = [], []
        if q:
            where.append("phrase LIKE ?"); args.append(q.replace("%", "").replace("_", "") + "%")
        if book:
            where.append("book_id=?"); args.append(book)
        w = (" WHERE " + " AND ".join(where)) if where else ""
        total = con.execute("SELECT COUNT(*) FROM phrase_entries" + w, args).fetchone()[0]
        rows = con.execute("SELECT phrase,translation,book_id,kind,source,license FROM phrase_entries" + w +
                           " ORDER BY phrase COLLATE NOCASE LIMIT ? OFFSET ?",
                           args + [size, (page - 1) * size]).fetchall()
        return {"total": total, "page": page, "size": size, "items": [dict(r) for r in rows]}
    finally:
        con.close()


def phrase_detail(lex_path, phrase):
    con = lex_conn(lex_path)
    try:
        r = con.execute("SELECT phrase,translation,book_id,kind,source,license FROM phrase_entries WHERE phrase=? COLLATE NOCASE", (phrase,)).fetchone()
        if not r:
            return None
        books = [x["book_id"] for x in con.execute("SELECT book_id FROM phrase_entries WHERE phrase=? COLLATE NOCASE ORDER BY book_id", (phrase,))]
        return {"entry": dict(r), "books": books}
    finally:
        con.close()


def case_hint_html(lex_path, word):
    """大小写/专名提示（只作补充线索，不自动改词条）。"""
    con = lex_conn(lex_path)
    try:
        lower = word[:1].lower() + word[1:]
        upper = word[:1].upper() + word[1:]
        has_lower = con.execute("SELECT 1 FROM entries WHERE word=?", (lower,)).fetchone() is not None
        has_upper = con.execute("SELECT 1 FROM entries WHERE word=?", (upper,)).fetchone() is not None
    finally:
        con.close()
    tips = []
    if word[:1].isupper() and not has_upper and has_lower:
        tips.append("大写形式无独立词条：App 应显示“以下为 %s 的结果”" % lower)
    if word[:1].isupper() and has_upper and has_lower and word != lower:
        tips.append("大小写均有词条：App 应提示“也可能想找 %s”" % lower)
    if word[:1].islower() and has_upper:
        tips.append("存在大写专名：App 可提示“也可能想找 %s”" % upper)
    if not tips:
        return ""
    return "<div class='muted' style='margin-top:10px'>大小写/专名：" + "；".join(esc(x) for x in tips) + "</div>"


def word_detail(lex_path, word):
    con = lex_conn(lex_path)
    try:
        e = con.execute("SELECT * FROM entries WHERE word=? COLLATE NOCASE", (word,)).fetchone()
        if not e:
            return None
        tags = [r["tag"] for r in con.execute("SELECT tag FROM word_tags WHERE word=?", (word,))]
        books = [r["book_id"] for r in con.execute("SELECT book_id FROM wordbook_entries WHERE word=? ORDER BY book_id", (word,))]
        lm = con.execute("SELECT lemma,kind FROM lemmas WHERE form=?", (word,)).fetchone()
        return {"entry": dict(e), "tags": tags, "books": books, "lemma": dict(lm) if lm else None}
    finally:
        con.close()


def get_supplement(rev, word):
    r = rev.execute("SELECT * FROM supplements WHERE word=?", (word,)).fetchone()
    return dict(r) if r else {"word": word, "status": "pending", "payload": "", "payload_kind": "text",
                              "ai_request": "", "notes": "", "reviewer": "", "updated_at": ""}


def detect_kind(text):
    t = (text or "").strip()
    if t.startswith("{") or t.startswith("["):
        try:
            json.loads(t); return "json"
        except Exception:
            return "text?"
    if re.search(r"^#{1,6}\s|^- ", t, re.M):
        return "markdown"
    return "text"


def save_supplement(rev, word, ai_request, payload, status, notes="", reviewer="local"):
    if status not in ("pending", "partial", "done", "question"):
        raise ValueError("invalid status")
    kind = detect_kind(payload)
    rev.execute("""INSERT INTO supplements(word,status,payload,payload_kind,ai_request,notes,reviewer,updated_at)
        VALUES(?,?,?,?,?,?,?,?)
        ON CONFLICT(word) DO UPDATE SET status=excluded.status,payload=excluded.payload,
        payload_kind=excluded.payload_kind,ai_request=excluded.ai_request,notes=excluded.notes,
        reviewer=excluded.reviewer,updated_at=excluded.updated_at""",
                (word, status, payload, kind, ai_request, notes, reviewer, now()))
    rev.commit()
    audit(rev, "supplement_save", word, json.dumps({"status": status, "kind": kind, "chars": len(payload)}))
    return {"word": word, "status": status, "payload_kind": kind}


def get_note(rev, word):
    r = rev.execute("SELECT * FROM public_notes WHERE word=?", (word,)).fetchone()
    return dict(r) if r else {"word": word, "note": "", "author": "", "updated_at": ""}


def save_note(rev, word, note, author="local"):
    note = (note or "").strip()
    if note:
        rev.execute("""INSERT INTO public_notes(word,note,author,updated_at) VALUES(?,?,?,?)
            ON CONFLICT(word) DO UPDATE SET note=excluded.note,author=excluded.author,updated_at=excluded.updated_at""",
                    (word, note, author, now()))
    else:
        rev.execute("DELETE FROM public_notes WHERE word=?", (word,))
    rev.commit()
    audit(rev, "public_note_save", word, "chars=%d" % len(note))
    return {"word": word, "note": note}


def export_notes(rev, out_dir):
    """导出分片备注（避免单文件过大）：notes/index.json + notes/<letter>.json（按首字母分片）。
    App 先读 index，再按需拉单词首字母分片；index 含每片 sha256/条数。"""
    import hashlib
    base = os.path.join(out_dir, "notes")
    os.makedirs(base, exist_ok=True)
    rows = [dict(r) for r in rev.execute("SELECT word,note,author,updated_at FROM public_notes ORDER BY word")]
    shards = {}
    for r in rows:
        letter = (r["word"][:1] or "_").lower()
        if not letter.isalpha():
            letter = "_"
        shards.setdefault(letter, []).append(r)
    index = {"schema_version": 2, "generated_at": now(), "count": len(rows), "shard_by": "first_letter",
             "letters": {}}
    total_bytes = 0
    for letter, items in shards.items():
        fn = "notes/%s.json" % letter
        payload = {"letter": letter, "count": len(items), "notes": items}
        raw = json.dumps(payload, ensure_ascii=False, indent=2)
        with open(os.path.join(out_dir, fn), "w", encoding="utf-8") as f:
            f.write(raw)
        total_bytes += len(raw.encode("utf-8"))
        index["letters"][letter] = {"file": fn, "count": len(items),
                                    "sha256": hashlib.sha256(raw.encode()).hexdigest()}
    index_path = os.path.join(base, "index.json")
    with open(index_path, "w", encoding="utf-8") as f:
        json.dump(index, f, ensure_ascii=False, indent=2)
    audit(rev, "notes_export", None, "count=%d shards=%d bytes=%d" % (len(rows), len(shards), total_bytes))
    return {"path": index_path, "count": len(rows), "shards": len(shards), "bytes": total_bytes}


def iter_ai_blocks(text):
    """把一整段 AI 批量输出拆成 [(word, payload_text)]。
    支持：① JSON 数组/对象 ② '### word' / '## word' 标题块 ③ 'word:' 行块 ④ '---' 分隔块。"""
    text = (text or "").strip()
    if not text:
        return []
    try:
        data = json.loads(text)
        items = data if isinstance(data, list) else (data.get("entries") or data.get("items") or [data])
        out = []
        for it in items:
            if isinstance(it, dict):
                w = it.get("word") or it.get("headword") or it.get("term")
                if w:
                    out.append((str(w).strip().lower(), json.dumps(it, ensure_ascii=False)))
        if out:
            return out
    except Exception:
        pass
    blocks, cur = [], []
    def flush():
        nonlocal cur
        if not cur:
            return
        head = cur[0].strip()
        m = re.match(r"^#{1,6}\s+([A-Za-z][A-Za-z' -]{0,40})$", head) or \
            re.match(r"^([A-Za-z][A-Za-z' -]{0,40})\s*[:：]?$", head)
        if m:
            blocks.append((m.group(1).strip().lower(), "\n".join(cur)))
        cur = []
    for line in text.splitlines():
        if line.strip() in ("---", "***"):
            flush(); continue
        if re.match(r"^#{1,6}\s+", line.strip()):
            flush()
        cur.append(line)
    flush()
    return blocks


def import_ai_replies(rev, text, status="partial"):
    blocks = iter_ai_blocks(text)
    ok, errors = 0, []
    for word, payload in blocks:
        if not re.fullmatch(r"[A-Za-z][A-Za-z' .-]{0,48}", word):
            errors.append({"word": word, "error": "词形不合法"})
            continue
        save_supplement(rev, word, "", payload, status, "AI 批量导入")
        ok += 1
    audit(rev, "ai_import", None, json.dumps({"blocks": len(blocks), "ok": ok, "errors": len(errors)}))
    return {"blocks": len(blocks), "ok": ok, "errors": errors}


def missing_suggestions(payload):
    """根据已有补充内容，提示“猜你需要补哪些”（启发式，非判决）。"""
    t = (payload or "").lower()
    tips = []
    checks = [("音标", ["/", "phonetic", "ipa"]), ("双语例句", ["例句", "example", "出处"]),
              ("音乐例句", ["音乐", "music", "歌词"]), ("视频例句", ["视频", "video", "外链"]),
              ("网络释义", ["网络释义", "网络", "web"]), ("专业释义", ["专业", "学科", "domain"]),
              ("用户贡献", ["用户贡献", "用户", "备注"]), ("英英释义", ["英英", "english", "definition"]),
              ("同近义词", ["近义", "反义", "synonym", "antonym"]), ("同根词", ["同根", "词根", "派生"]),
              ("词组短语", ["词组", "短语", "phrase", "习语"]), ("词源", ["词源", "etymology", "latin"]),
              ("百科", ["wikipedia", "维基", "百科", "百度百科"])]
    for label, keys in checks:
        if not any(k in t for k in keys):
            tips.append(label)
    return tips


def ai_request_prompt(word):
    return ("请查询英文词条【%s】并按 entry-schema 1.1 输出 JSON，严格按以下 12 段顺序给内容：\n"
            "1 双语例句（2–3 条，来源+许可，含学术例句）\n"
            "2 音乐例句（仅 CC/授权歌词，标注曲名/许可；无则 null）\n"
            "3 视频例句（只给外链+时间戳，不下载）\n"
            "4 网络释义（开放词典/用户投票，标注来源）\n"
            "5 专业释义（学科标注）\n"
            "6 用户贡献（可引用公共备注/词条贡献，标注作者）\n"
            "7 英英释义（Wiktionary 等，标注许可）\n"
            "8 同近义词（近义/反义列表，联想词带中文注释）\n"
            "9 同根词（词根/派生/词形）\n"
            "10 词组短语（短语+中文翻译，按常用度）\n"
            "11 词源（来源+许可）\n"
            "12 百科（维基/百度，只给链接与自写摘要 ≤120 字）\n"
            "另外：音标英/美分开+来源；考试标签+来源/年份；每条信息给 evidence_url。\n"
            "大小写规则：若查询词首字母大写但只有小写词条，标注 case_note=lowercase_result；\n"
            "若大小写均有独立词条，给 alternate 字段（另一个大小写词条）。查不到写 null 并说明。\n"
            "禁止编造/摘抄百科正文/使用非 CC 学术篇目；规范见 docs/proj/03-AI词条补全规范.md。" % word)


def list_wordbooks(lex_path, rev):
    out = []
    con = lex_conn(lex_path)
    try:
        for r in con.execute("SELECT book_id,name,description FROM wordbooks ORDER BY book_id"):
            n = con.execute("SELECT COUNT(*) FROM wordbook_entries WHERE book_id=?", (r["book_id"],)).fetchone()[0]
            out.append({"book_id": r["book_id"], "name": r["name"], "description": r["description"],
                        "count": n, "kind": "默认（随 APK 压缩包）"})
    finally:
        con.close()
    for r in rev.execute("SELECT * FROM local_wordbooks ORDER BY book_id"):
        n = rev.execute("SELECT COUNT(*) FROM local_wordbook_items WHERE book_id=?", (r["book_id"],)).fetchone()[0]
        out.append({"book_id": r["book_id"], "name": r["name"], "description": r["description"],
                    "count": n, "kind": "本地（用户添加）"})
    return out


def create_wordbook(rev, book_id, name, description=""):
    if not re.fullmatch(r"[A-Za-z0-9_\-]{2,32}", book_id or ""):
        raise ValueError("book_id 只能 2–32 位字母/数字/_/-")
    rev.execute("INSERT INTO local_wordbooks VALUES (?,?,?,?)", (book_id, name or book_id, description, now()))
    rev.commit(); audit(rev, "wordbook_create", None, book_id)
    return {"book_id": book_id}


def add_word_to_book(rev, book_id, word):
    rev.execute("INSERT OR IGNORE INTO local_wordbook_items VALUES (?,?,?)", (book_id, word, now()))
    rev.commit(); audit(rev, "wordbook_add", word, book_id)


def check_upstream():
    out = {}
    for name, repo in UPSTREAM.items():
        try:
            req = urllib.request.Request("https://api.github.com/repos/%s/releases/latest" % repo,
                                         headers={"User-Agent": "OpenDictionary-dashboard/0.2"})
            with urllib.request.urlopen(req, timeout=8) as resp:
                d = json.loads(resp.read().decode("utf-8"))
            out[name] = {"status": "ok", "latest_tag": d.get("tag_name"), "published_at": d.get("published_at")}
        except Exception as exc:
            out[name] = {"status": "unknown", "error": type(exc).__name__}
    return out


CSS = """
:root{
  --background:#ffffff; --foreground:#09090b; --card:#ffffff; --card-fg:#09090b;
  --muted:#f4f4f5; --muted-fg:#71717a; --border:#e4e4e7; --input:#e4e4e7;
  --primary:#18181b; --primary-fg:#fafafa; --secondary:#f4f4f5; --secondary-fg:#18181b;
  --accent:#f4f4f5; --accent-fg:#18181b; --destructive:#e11d48;
  --ring:#a1a1aa; --radius:10px; --sidebar:#fafafa; --sidebar-fg:#3f3f46;
  --ok:#16a34a; --warn:#a16207; --danger:#b91c1c;
}
[data-theme="dark"]{
  --background:#09090b; --foreground:#fafafa; --card:#121214; --card-fg:#fafafa;
  --muted:#27272a; --muted-fg:#a1a1aa; --border:#27272a; --input:#3f3f46;
  --primary:#fafafa; --primary-fg:#18181b; --secondary:#27272a; --secondary-fg:#fafafa;
  --accent:#27272a; --accent-fg:#fafafa; --sidebar:#0f0f11; --sidebar-fg:#d4d4d8;
}
*{box-sizing:border-box}
html,body{height:100%}
body{margin:0;background:var(--background);color:var(--foreground);
  font-family:Inter,-apple-system,BlinkMacSystemFont,'Segoe UI','PingFang SC','Noto Sans CJK SC',sans-serif;
  font-size:14px;line-height:1.5;-webkit-font-smoothing:antialiased}
a{color:inherit;text-decoration:none}
.app{display:flex;min-height:100vh}
.side{width:240px;flex:none;background:var(--sidebar);color:var(--sidebar-fg);border-right:1px solid var(--border);
  padding:14px 10px;position:sticky;top:0;height:100vh;display:flex;flex-direction:column;gap:2px}
.side .logo{display:flex;align-items:center;gap:9px;padding:6px 8px 14px;border-bottom:1px solid var(--border);margin-bottom:10px}
.side .logo .b{width:30px;height:30px;border-radius:8px;background:var(--primary);color:var(--primary-fg);
  display:flex;align-items:center;justify-content:center;font-weight:700;font-size:13px;letter-spacing:-.4px}
.side .logo b{font-size:13px;color:var(--foreground);display:block;line-height:1.2}
.side .logo span{font-size:11px;color:var(--muted-fg)}
.side-group{font-size:11px;color:var(--muted-fg);padding:10px 8px 4px;font-weight:600;letter-spacing:.4px}
.side a{display:flex;align-items:center;gap:8px;padding:7px 9px;border-radius:7px;font-size:13px;color:var(--sidebar-fg)}
.side a:hover{background:var(--accent);color:var(--accent-fg)}
.side a.on{background:var(--primary);color:var(--primary-fg);font-weight:600}
.side a .ic{width:16px;text-align:center;opacity:.75;font-size:13px}
.side .side-search{padding:6px 4px}
.side-input{width:100%;background:var(--card);border:1px solid var(--border);color:var(--foreground);
  border-radius:7px;padding:7px 9px;font-size:12px;outline:none}
.side-input:focus{border-color:var(--ring);box-shadow:0 0 0 3px rgba(161,161,170,.2)}
.side .foot{margin-top:auto;padding:12px 8px 4px;border-top:1px solid var(--border);font-size:11px;color:var(--muted-fg);line-height:1.6}
.theme-btn{width:100%;margin-top:8px;background:var(--card);border:1px solid var(--border);color:var(--foreground);
  border-radius:7px;padding:6px 8px;font-size:12px;cursor:pointer}
.main{flex:1;min-width:0;padding:22px 28px 40px}
.top{display:flex;align-items:flex-start;justify-content:space-between;gap:16px;margin-bottom:18px}
.top h1{font-size:20px;font-weight:650;letter-spacing:-.3px;margin:0}
.top .sub{color:var(--muted-fg);font-size:12.5px;margin-top:5px;line-height:1.5}
.btn{display:inline-flex;align-items:center;gap:6px;background:var(--primary);color:var(--primary-fg);
  border:1px solid var(--primary);border-radius:var(--radius);padding:7px 12px;font-size:13px;font-weight:500;cursor:pointer}
.btn:hover{opacity:.9}
.btn.ghost{background:transparent;color:var(--foreground);border-color:var(--border)}
.btn.ghost:hover{background:var(--accent)}
.btn.small{padding:5px 9px;font-size:12px}
.cards{display:grid;grid-template-columns:repeat(auto-fit,minmax(175px,1fr));gap:12px;margin-bottom:18px}
.card{background:var(--card);border:1px solid var(--border);border-radius:12px;padding:16px;color:var(--card-fg)}
.card .k{font-size:12px;color:var(--muted-fg);display:flex;align-items:center;gap:6px}
.card .v{font-size:23px;font-weight:650;letter-spacing:-.4px;margin-top:6px}
.card .s{font-size:11.5px;color:var(--muted-fg);margin-top:3px}
.panel{background:var(--card);border:1px solid var(--border);border-radius:12px;padding:18px;margin-bottom:16px;color:var(--card-fg)}
.panel h2{font-size:13.5px;font-weight:600;margin:0 0 12px;letter-spacing:-.1px}
.letters{display:flex;flex-wrap:wrap;gap:4px;margin:10px 0 12px}
.letters a{min-width:30px;text-align:center;padding:4px 6px;border-radius:6px;font-size:12px;color:var(--muted-fg);
  border:1px solid transparent;background:transparent}
.letters a:hover{background:var(--accent)}
.letters a.on{background:var(--primary);color:var(--primary-fg);font-weight:600}
.letters a .n{display:block;font-size:9px;opacity:.7}
table{width:100%;border-collapse:collapse;font-size:13px}
th,td{text-align:left;padding:9px 10px;border-bottom:1px solid var(--border);vertical-align:top}
th{color:var(--muted-fg);font-size:12px;font-weight:500}
tbody tr:hover td{background:var(--accent)}
.badge{display:inline-flex;align-items:center;gap:4px;font-size:11px;border-radius:999px;padding:2px 8px;
  border:1px solid var(--border);background:var(--muted);color:var(--foreground);margin:1px 2px}
.badge.ok{background:rgba(22,163,74,.1);border-color:rgba(22,163,74,.25);color:var(--ok)}
.badge.warn{background:rgba(161,98,7,.1);border-color:rgba(161,98,7,.25);color:var(--warn)}
.badge.danger{background:rgba(185,28,28,.1);border-color:rgba(185,28,28,.25);color:var(--danger)}
.form label{display:block;font-size:12px;color:var(--muted-fg);margin:12px 0 5px;font-weight:500}
textarea,input[type=text],input[type=search],input[type=file],select{width:100%;background:transparent;color:var(--foreground);
  border:1px solid var(--input);border-radius:8px;padding:9px 11px;font-size:13px;font-family:inherit;outline:none}
textarea:focus,input[type=text]:focus,input[type=search]:focus{border-color:var(--ring);box-shadow:0 0 0 3px rgba(161,161,170,.18)}
textarea{min-height:110px;resize:vertical}
select{background:var(--card)}
.row2{display:grid;grid-template-columns:1fr 1fr;gap:14px}
@media(max-width:1100px){.row2{grid-template-columns:1fr}.side{width:200px}}
.muted{color:var(--muted-fg);font-size:12px;line-height:1.6}
.kv{display:grid;grid-template-columns:88px 1fr;gap:7px 14px;font-size:13px;align-items:baseline}
.kv .k{color:var(--muted-fg);font-size:12px}
.pre{white-space:pre-wrap;background:var(--muted);border:1px solid var(--border);border-radius:8px;
  padding:12px;font-size:12.5px;line-height:1.7}
.toolbar{display:flex;gap:8px;align-items:center;margin:12px 0;flex-wrap:wrap}
.toast{background:rgba(22,163,74,.08);color:var(--ok);border:1px solid rgba(22,163,74,.25);
  border-radius:8px;padding:10px 12px;font-size:13px;margin-bottom:14px}
.issues{font-size:12.5px;color:var(--foreground);line-height:1.9;margin:0;padding-left:18px}
.tabs{display:flex;gap:6px;flex-wrap:wrap;margin:0 0 14px}
.tabs a{font-size:12.5px;padding:6px 10px;border-radius:7px;border:1px solid var(--border);color:var(--muted-fg)}
.tabs a:hover{background:var(--accent)}
.tabs a.on{background:var(--primary);border-color:var(--primary);color:var(--primary-fg);font-weight:500}
.hintbox{background:rgba(37,99,235,.06);border:1px solid rgba(37,99,235,.18);color:var(--foreground);
  border-radius:10px;padding:12px 14px;font-size:12.5px;line-height:1.7}
.cmdk{position:fixed;inset:0;background:rgba(0,0,0,.45);display:none;align-items:flex-start;justify-content:center;padding-top:12vh;z-index:99}
.cmdk.open{display:flex}
.cmdk .box{width:560px;max-width:92vw;background:var(--card);border:1px solid var(--border);border-radius:12px;
  box-shadow:0 24px 80px rgba(0,0,0,.25);overflow:hidden}
.cmdk input{width:100%;border:0;border-bottom:1px solid var(--border);border-radius:0;padding:14px 16px;font-size:14px;background:transparent;color:var(--foreground)}
.cmdk .list{max-height:320px;overflow:auto;padding:6px}
.cmdk .list a{display:flex;align-items:center;justify-content:space-between;padding:9px 10px;border-radius:7px;font-size:13px}
.cmdk .list a:hover{background:var(--accent)}
.cmdk .list a span{color:var(--muted-fg);font-size:11px}
.hidden{display:none}
"""


def esc(x):
    return html.escape("" if x is None else str(x))


def page(title, body, active="home"):
    nav = [("home", "/", "总览", "⌂"), ("words", "/review/words", "单词审核", "Aa"),
           ("phrases", "/review/phrases", "短语审核", "“”"), ("import", "/import", "AI 结果导入", "⇪"),
           ("wordbooks", "/wordbooks", "词书管理", "❏"), ("userbooks", "/user-wordbooks", "我的单词本", "▤"), ("external", "/external-cache", "外链内容缓存", "◇"), ("upstream", "/upstream", "上游更新", "↻"),
           ("issues", "/issues", "数据质量提示", "!")]
    if active == "workbench":
        active = "words"
    links = "".join("<a href='%s' class='%s'><span class='ic'>%s</span>%s</a>" % (h, "on" if k == active else "", ic, l)
                    for k, h, l, ic in nav)
    return """<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><title>%s</title><style>%s</style></head>
<body><div class="app"><aside class="side">
<script>
(function(){var t=localStorage.getItem('od-theme')||'light';document.documentElement.setAttribute('data-theme',t);})();
function toggleTheme(){var d=document.documentElement;var t=d.getAttribute('data-theme')==='dark'?'light':'dark';
  d.setAttribute('data-theme',t);localStorage.setItem('od-theme',t);}
document.addEventListener('keydown',function(e){
  if((e.ctrlKey||e.metaKey)&&e.key.toLowerCase()==='k'){e.preventDefault();var m=document.getElementById('cmdk');if(m)m.classList.toggle('open');}
  if(e.key==='Escape'){var m=document.getElementById('cmdk');if(m)m.classList.remove('open');}
});
function filterCmd(v){v=(v||'').toLowerCase();document.querySelectorAll('#cmdk .list a').forEach(function(a){
  a.style.display=(a.textContent.toLowerCase().indexOf(v)>=0)?'flex':'none';});}
function speak(word){try{if(!window.speechSynthesis){alert('浏览器不支持语音合成');return;}
  speechSynthesis.cancel();var u=new SpeechSynthesisUtterance(word);u.lang='en-US';u.rate=.95;speechSynthesis.speak(u);}catch(err){alert('发音失败：'+err);}}
</script>
<div class="logo"><div class="b">OD</div><div><b>OpenDictionary</b><span>数据审核后台 v4</span></div></div>
<div class="side-search"><form action="/review/words" method="get">
<input name="q" class="side-input" placeholder="搜单词 / 短语…  ⌘K"></form></div>
<div class="side-group">数据</div>
%s
<div class="foot">只读词典库 + 本地补充库<br>127.0.0.1 · 无外部服务
<button class="theme-btn" onclick="toggleTheme()">◐ 切换主题</button></div>
</aside><div class="cmdk" id="cmdk" onclick="if(event.target===this)this.classList.remove('open')">
<div class="box"><input placeholder="跳转：单词审核 / 短语审核 / AI 导入 / 词书…" oninput="filterCmd(this.value)">
<div class="list">%s</div></div></div>
<main class="main">%s</main></div></body></html>""" % (
        esc(title), CSS, links, "".join(
            "<a href='%s'><b>%s</b><span>%s</span></a>" % (h, l, h) for k, h, l, ic in nav), body)


def render_home(ls, ss, letters):
    untouched = max(0, ls["entries"] - ss["total"])
    covered = ss["total"]
    pct = ("%.4f%%" % (100.0 * covered / ls["entries"])) if ls["entries"] else "0%"
    cards = "".join("<div class='card'><div class='k'>%s</div><div class='v'>%s</div><div class='s'>%s</div></div>" % (k, v, sub)
                    for k, v, sub in [
                        ("词条总数", "{:,}".format(ls["entries"]), "ECDICT 77 万 + 外部词表"),
                        ("短语总数", "{:,}".format(ls["phrases"]), "含短语/习语/拉丁语 " + " / ".join("%s %s" % (k, v) for k, v in (ls.get("phrase_kinds") or {}).items())),
                        ("已补充", ss["done"], "人工补充确认"),
                        ("补充中", ss["partial"], "草稿/部分字段"),
                        ("存疑", ss["question"], "标记待查"),
                        ("未开始", "{:,}".format(untouched), "尚未提交过补充"),
                        ("覆盖进度", pct, "已建立补充记录 / 总词条"),
                        ("词典库", "%.1f MB" % ls["db_size_mb"], "SQLite + FTS5"),
                        ("默认词书", len(ls["wordbooks"]), "随 APK 压缩包")])
    tabs = "".join("<a href='/review/words?letter=%s'>%s<span class='n'>%s</span></a>" % (l["letter"], l["letter"], l["count"])
                   for l in letters[:26])
    return page("总览", """
<div class="top"><div><h1>数据补充总览</h1><div class="sub">审核 = 给词条补充数据，不判断对错；没有“通过率”，只有补充进度。</div></div>
<a class="btn" href="/review/words?letter=A">从 A 开始补充</a></div>
<div class="cards">%s</div>
<div class="panel"><h2>猜你需要</h2>
<div class="muted">直接搜索任意单词即可补充（左侧搜索框，Ctrl+/ 聚焦）；AI 批量结果请用「AI 结果导入」；每个词可写“公共备注”（随资源仓分发，App 内所有用户可见）。</div>
<div class="toolbar"><a class="btn ghost small" href="/import">粘贴 AI 批量结果</a>
<a class="btn ghost small" href="/review/phrases">短语审核 →</a>
<a class="btn ghost small" href="/wordbooks">管理词书</a>
<a class="btn ghost small" href="/issues">数据质量检查清单</a></div></div>
<div class="panel"><h2>按首字母进入（A → Z）</h2><div class="letters">%s</div>
<div class="muted">状态：待补充 / 补充中 / 已补充 / 存疑。词条按字典序排列，逐词提交补充。</div></div>
""" % (cards, tabs), "home")


def render_phrases(ps, rev, q, book, data, page_no):
    rows = []
    for it in data["items"]:
        sup = get_supplement(rev, it["phrase"])["status"]
        cls = {"done": "ok", "partial": "warn", "question": "danger"}.get(sup, "")
        rows.append("<tr><td><a href='/phrase/%s'>%s</a></td><td>%s</td><td><span class='badge'>%s</span></td>"
                    "<td>%s</td><td><span class='badge %s'>%s</span></td></tr>" % (
                        esc(it["phrase"]), esc(it["phrase"]), esc(it["translation"] or "-"),
                        esc(it["kind"]), esc(it["book_id"]), cls,
                        esc({"pending": "待补充", "partial": "补充中", "done": "已补充", "question": "存疑"}[sup])))
    rows = "".join(rows) or "<tr><td colspan=5 class='muted'>没有短语</td></tr>"
    books = " ".join("<a href='/review/phrases?book=%s' class='badge'>%s %s</a>" % (esc(b), esc(b), n)
                     for b, n in ps["by_book"].items())
    return page("短语审核", """
<div class="top"><div><h1>短语审核</h1>
<div class="sub">共 %s 条（%s）；按短语字典序、逐条补充；与单词审核分开。</div></div>
<a class="btn ghost small" href="/review/words">去单词审核 →</a></div>
<div class="cards">
<div class="card"><div class="k">短语总数</div><div class="v">%s</div><div class="s">phrase + latin</div></div>
<div class="card"><div class="k">已补充</div><div class="v">%s</div><div class="s">建立补充记录</div></div>
<div class="card"><div class="k">分类</div><div class="v">%s</div><div class="s">%s</div></div>
</div>
<div class="panel"><div class="toolbar">%s</div>
<form method="get" action="/review/phrases" style="display:flex;gap:8px;margin-bottom:10px">
<input name="q" placeholder="搜索短语…" value="%s" style="max-width:320px">
<input type="hidden" name="book" value="%s">
<button class="btn small">搜索</button></form>
<table><tr><th>短语</th><th>中文释义</th><th>类型</th><th>词库</th><th>补充状态</th></tr>%s</table>
<div class="toolbar"><a class="btn ghost small" href="/review/phrases?q=%s&book=%s&page=%d">上一页</a>
<a class="btn ghost small" href="/review/phrases?q=%s&book=%s&page=%d">下一页</a></div></div>
""" % ("{:,}".format(ps["total"]), " / ".join("%s %s" % (k, v) for k, v in ps["by_kind"].items()),
       "{:,}".format(ps["total"]), ps["supplemented"], "{:,}".format(ps["total"]),
       " / ".join("%s %s" % (k, v) for k, v in ps["by_book"].items()), books,
       esc(q or ""), esc(book or ""), rows,
       esc(q or ""), esc(book or ""), max(1, page_no - 1), esc(q or ""), esc(book or ""), page_no + 1), "phrases")


def render_phrase(phrase, d, sup, note):
    label = {"pending": "待补充", "partial": "补充中", "done": "已补充", "question": "存疑"}[sup["status"]]
    return page("短语 " + phrase, """
<div class="top"><div><h1>%s <span class="badge %s">%s</span></h1>
<div class="sub">短语补充：中文释义/近义表达/例句/来源与许可（公共备注同样随资源仓分发）。</div></div>
<div class="toolbar"><button class="btn" type="button" onclick="speak('%s')">🔊 发音</button>
<a class="btn ghost small" href="/review/phrases">返回短语列表</a></div></div>
<div class="row2">
<div class="panel"><h2>短语数据（只读）</h2><div class="kv">
<div class="k">phrase</div><div>%s</div><div class="k">中文释义</div><div>%s</div>
<div class="k">类型</div><div><span class="badge">%s</span></div><div class="k">词库</div><div>%s</div>
<div class="k">来源</div><div>%s</div><div class="k">许可</div><div>%s</div></div></div>
<div class="panel"><h2>数据质量提示</h2><ul class="issues">%s</ul></div></div>
<form class="panel" method="post" action="/api/phrase/note">
<input type="hidden" name="word" value="%s">
<h2>公共备注（随资源仓分发，App 可见；无则不显示）</h2>
<textarea name="note" style="min-height:80px">%s</textarea>
<div class="toolbar"><button class="btn small">保存公共备注</button></div></form>
<form class="panel" method="post" action="/api/phrase/supplement">
<input type="hidden" name="word" value="%s">
<h2>输入框 1 · AI 查询请求（复制给 AI）</h2>
<textarea name="ai_request" style="min-height:110px">%s</textarea>
<h2 style="margin-top:14px">输入框 2 · 补充内容（纯文本/JSON/Markdown）</h2>
<textarea name="payload" style="min-height:160px">%s</textarea>
<label>备注（可选）</label><input type="text" name="notes" value="%s">
<div class="toolbar"><button class="btn" name="status" value="partial">保存草稿</button>
<button class="btn" name="status" value="done">保存并标记已补充</button>
<button class="btn ghost" name="status" value="question">标记存疑</button></div></form>
""" % (esc(phrase), "ok" if sup["status"] == "done" else "warn", label, esc(phrase),
       esc(d["entry"]["phrase"]), esc(d["entry"]["translation"] or "-"), esc(d["entry"]["kind"]),
       " ".join("<span class='badge'>%s</span>" % esc(b) for b in d["books"]),
       esc(d["entry"]["source"]), esc(d["entry"]["license"]),
       "".join("<li>%s</li>" % esc(x[1]) for x in KNOWN_ISSUES), esc(phrase), esc(note.get("note") or ""),
       esc(phrase), esc(sup.get("ai_request") or ai_request_prompt(phrase)), esc(sup.get("payload") or ""),
       esc(sup.get("notes") or "")), "phrases")


def render_workbench(ls, rev, letter, q, data, page_no):
    rows = []
    for it in data["items"]:
        st = get_supplement(rev, it["word"])["status"]
        cls = {"done": "ok", "partial": "warn", "question": "danger"}.get(st, "")
        rows.append("<tr><td><a href='/word/%s'>%s</a></td><td>%s</td><td>%s</td><td><span class='badge %s'>%s</span></td></tr>" % (
            esc(it["word"]), esc(it["word"]), esc((it["translation"] or "").replace("\n", " ")[:90]),
            " ".join("<span class='badge'>%s</span>" % esc(t) for t in (it["tag"] or "").split()),
            cls, esc({"pending": "待补充", "partial": "补充中", "done": "已补充", "question": "存疑"}[st])))
    rows = "".join(rows) or "<tr><td colspan=4 class='muted'>没有词条</td></tr>"
    return page("补充工作台", """
<div class="top"><div><h1>单词审核 · 按字母顺序</h1>
<div class="sub">当前：%s ｜ 共 %s 词 ｜ 第 %d 页（短语审核在另一个页面，见左侧）</div></div>
<a class="btn ghost small" href="/review/phrases">去短语审核 →</a></div>
<form method="get" action="/review/words" style="display:flex;gap:8px">
<input name="q" placeholder="搜索词条" value="%s" style="width:220px"><button class="btn small">搜索</button></form></div>
<div class="panel"><table><tr><th>词条</th><th>现有释义</th><th>标签</th><th>补充状态</th></tr>%s</table>
<div class="toolbar"><a class="btn ghost small" href="/review/words?letter=%s&page=%d">上一页</a>
<a class="btn ghost small" href="/review/words?letter=%s&page=%d">下一页</a></div></div>
""" % (esc(letter or "全部"), "{:,}".format(data["total"]), page_no, esc(q or ""), rows,
       esc(letter or ""), max(1, page_no - 1), esc(letter or ""), page_no + 1), "workbench")


def render_word(word, d, sup, note=None, suggestions=None, case_html=""):
    label = {"pending": "待补充", "partial": "补充中", "done": "已补充", "question": "存疑"}[sup["status"]]
    note = note or {"note": ""}
    suggestions = suggestions or []
    case_html = case_html or ""
    issues = "".join("<li>%s</li>" % esc(x[1]) for x in KNOWN_ISSUES)
    return page("补充 " + word, """
<div class="top"><div><h1>%s <span class="badge %s">%s</span></h1>
<div class="sub">补充 = 补数据/纠错；完成后点“保存并标记已补充”。公共备注会随资源仓分发、App 内所有人可见。</div></div>
<div class="toolbar"><button class="btn" type="button" onclick="speak('%s')">🔊 发音</button>
<a class="btn ghost small" href="/preview/%s">预览 App 效果</a>
<a class="btn ghost small" href="/review/words?letter=%s">返回列表</a></div></div>
<div class="row2">
<div class="panel"><h2>当前词条数据（只读）</h2><div class="kv">
<div class="k">word</div><div>%s</div><div class="k">音标</div><div>%s</div>
<div class="k">释义</div><div class="pre">%s</div><div class="k">标签</div><div>%s</div>
<div class="k">词书</div><div>%s</div><div class="k">lemma</div><div>%s</div></div></div>
<div class="panel"><h2>数据质量提示（issue/PR 教训）</h2><ul class="issues">%s</ul>%s</div></div>
<form class="panel" method="post" action="/api/note/%s"><h2>公共备注（随仓库分发，App 可见）</h2>
<textarea name="note" style="min-height:80px" placeholder="例：口语中 run 表“经营”更常用；注意与 operate 区分">%s</textarea>
<div class="toolbar"><button class="btn small">保存公共备注</button>
<a class="btn ghost small" href="/api/notes/export">导出 public-notes.json</a></div>
<div class="muted">留空保存 = 删除备注；备注不是官方释义，展示时标“用户备注”。</div></form>
<div class="panel"><h2>猜你需要补（启发式提示，不判决）</h2><div class="muted">%s</div></div>
<form class="panel" method="post" action="/api/supplement/%s">
<h2>输入框 1 · AI 查询请求（复制后发给你自己的 AI）</h2>
<textarea name="ai_request" style="min-height:120px">%s</textarea>
<div class="muted">AI 按 03 规范输出 JSON；整段粘贴到输入框 2，系统自动识别纯文本/JSON/Markdown。</div>
<h2 style="margin-top:16px">输入框 2 · 补充内容（纯文本 / JSON / Markdown）</h2>
<textarea name="payload" style="min-height:200px" placeholder="例如：\nv. 跑，奔跑；参加（赛跑）…\nn. 跑步，赛跑…\n初中 / 高中 / CET4 …">%s</textarea>
<label>备注（可选）</label><input type="text" name="notes" value="%s">
<div class="toolbar"><button class="btn" type="submit" name="status" value="partial">保存草稿（补充中）</button>
<button class="btn" type="submit" name="status" value="done">保存并标记已补充</button>
<button class="btn ghost" type="submit" name="status" value="question">标记存疑</button></div>
<div class="muted">保存到本地补充库（work/dashboard/review.db），不改只读词典库。</div></form>
""" % (esc(word), "ok" if sup["status"] == "done" else "warn", label, esc(word), esc(word), esc((word[0] or "A").upper()),
       esc(d["entry"].get("word")), esc(d["entry"].get("phonetic") or ""),
       esc((d["entry"].get("translation") or "").replace("\\n", "\n")),
       " ".join("<span class='badge'>%s</span>" % esc(t) for t in d["tags"]) or "<span class='muted'>无</span>",
       " ".join("<span class='badge'>%s</span>" % esc(b) for b in d["books"]),
       esc(str(d["lemma"]) if d.get("lemma") else "-"), issues, esc(case_html), esc(word), esc(note.get("note") or ""),
       " ｜ ".join("<span class='badge warn'>缺%s</span>" % esc(x) for x in suggestions) or "<span class='badge ok'>字段看起来齐全（仍建议抽检）</span>",
       esc(word), esc(sup.get("ai_request") or ai_request_prompt(word)), esc(sup.get("payload") or ""),
       esc(sup.get("notes") or "")), "workbench")


def render_preview(word, d, sup):
    return page("预览 " + word, """
<div class="top"><div><h1>App 查词效果预览</h1><div class="sub">只展示已有数据；发布后按 06 文档 9 段顺序展示。</div></div>
<a class="btn ghost" href="/word/%s">返回补充</a></div>
<div class="panel"><div style="font-size:30px;font-weight:750">%s</div><div class="muted">%s</div>
<div style="margin:10px 0">%s</div><div class="pre">%s</div></div>
<div class="panel"><h2>用户补充（未发布）</h2><div class="pre">%s</div></div>
""" % (esc(word), esc(d["entry"].get("word")), esc(d["entry"].get("phonetic") or ""),
       " ".join("<span class='badge'>%s</span>" % esc(t) for t in d["tags"]),
       esc((d["entry"].get("translation") or "").replace("\\n", "\n")), esc(sup.get("payload") or "（空）")), "workbench")


def render_import(result=None):
    res = ""
    if result:
        errs = "".join("<li>%s：%s</li>" % (esc(e["word"]), esc(e["error"])) for e in result["errors"])
        res = "<div class='panel'><h2>导入结果</h2><div>识别块 %d，成功 %d，失败 %d</div>%s</div>" % (
            result["blocks"], result["ok"], len(result["errors"]),
            ("<ul class='issues'>%s</ul>" % errs) if errs else "")
    return page("AI 结果导入", """
<div class="top"><div><h1>AI 结果批量导入</h1>
<div class="sub">把 AI 对多个词的回复整段粘贴到这里：支持 JSON 数组/对象、'### word' 标题块、'---' 分隔块。导入后逐条校验，失败会列出。</div></div></div>
%s<form class="panel" method="post" action="/api/import">
<h2>粘贴区（一次多个词）</h2>
<textarea name="payload" style="min-height:320px" placeholder='示例一（JSON）：
[{"word":"run","senses":[{"pos":"v","def":"跑"}]},{"word":"schedule","senses":[...]}]
示例二（标题块）：
### reputation
n. 名气，名声，名誉
近义词：honor, fame...
--- 
### schedule
n. 计划表...'></textarea>
<label>导入状态</label>
<select name="status"><option value="partial">保存为补充中（推荐，待人工确认）</option>
<option value="done">直接标记已补充</option></select>
<div class="toolbar"><button class="btn">解析并导入</button></div>
<div class="muted">规则：词形自动小写；识别失败的块不写入；全部写入审计日志。</div></form>
""" % res, "import")


def render_wordbooks(books):
    rows = "".join("<tr><td>%s</td><td>%s</td><td>%d</td><td>%s</td></tr>" % (
        esc(b["book_id"]), esc(b["name"]), b["count"], esc(b["kind"])) for b in books)
    return page("词书管理", """
<div class="top"><div><h1>词书管理</h1><div class="sub">默认词书随 APK 打成压缩包、首次启动解压；这里管理“本地补充词书”。</div></div></div>
<div class="panel"><table><tr><th>book_id</th><th>名称</th><th>词数</th><th>类型</th></tr>%s</table></div>
<div class="row2">
<form class="panel" method="post" action="/api/wordbooks"><h2>新建词书</h2>
<label>book_id（字母/数字/_/-，2–32 位）</label><input type="text" name="book_id" required>
<label>名称</label><input type="text" name="name" required><label>说明</label><input type="text" name="description">
<div class="toolbar"><button class="btn">创建</button></div></form>
<form class="panel" method="post" action="/api/wordbooks/add"><h2>向词书加入词条</h2>
<label>book_id</label><input type="text" name="book_id" required placeholder="gk_extra">
<label>单词</label><input type="text" name="word" required placeholder="run">
<div class="toolbar"><button class="btn">加入</button></div></form></div>
""" % rows, "wordbooks")


def render_upstream(data):
    rows = "".join("<tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>" % (
        esc(k), esc(v.get("status")), esc(v.get("latest_tag") or "-"), esc(v.get("error") or "")) for k, v in data.items())
    return page("上游更新", """
<div class="top"><div><h1>上游更新检测</h1><div class="sub">失败显示 unknown，不得当作“已是最新”。</div></div>
<a class="btn" href="/upstream">重新检测</a></div>
<div class="panel"><table><tr><th>源</th><th>状态</th><th>最新版本</th><th>错误</th></tr>%s</table></div>
""" % rows, "upstream")


def render_issues():
    rows = "".join("<tr><td>%s</td><td>%s</td></tr>" % (esc(k), esc(v)) for k, v in KNOWN_ISSUES)
    return page("数据质量提示", """
<div class="top"><div><h1>数据质量提示（来自 issue/PR 与案例）</h1>
<div class="sub">补充词条时逐条检查；同类错误在多个开源项目反复出现。</div></div></div>
<div class="panel"><table><tr><th>类别</th><th>教训</th></tr>%s</table></div>
""" % rows, "issues")


def render_user_wordbooks():
    body = """
<div class="pagehead"><div><h1>我的单词本（Web 管理）</h1>
<p class="sub">上传 App「备份与恢复」导出的 JSON，在浏览器里管理词书与单词，再导出回 App 导入。数据只在本页内存中处理，不上传服务器。</p></div></div>
<div class="card"><div class="formrow">
<label class="btn primary">选择备份 JSON<input type="file" id="file" accept="application/json" style="display:none"></label>
<button class="btn" onclick="loadSample()">载入示例</button>
<button class="btn" id="exportBtn" onclick="exportJson()">导出 JSON</button>
</div><p class="muted" id="status">尚未载入备份。</p></div>
<div class="card" id="books"></div>
<div class="card"><h2 style="margin-bottom:8px">自定义词条（customEntries）</h2><p class="muted">App 内“设置→自定义词条”或在线查询保存的词条；可在此增删改后导出回 App。</p><div id="customs"></div><div class="formrow" style="margin-top:8px"><input id="newCustomWord" placeholder="word" style="flex:1"><input id="newCustomTrans" placeholder="释义/简短解释" style="flex:2"><button class="btn" onclick="addCustom()">添加词条</button></div></div>
<div class="card"><div class="formrow"><input id="search" placeholder="搜索单词出现在哪些词书…" oninput="renderBooks()" style="flex:1">
<span class="muted" id="searchResult"></span></div></div>
<script>
var state=null;
function setStatus(t){document.getElementById('status').textContent=t;}
function esc(s){return String(s||'').replace(/[&<>"]/g,function(c){return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c];});}
document.getElementById('file').addEventListener('change',function(e){
  var f=e.target.files[0];if(!f)return;var r=new FileReader();
  r.onload=function(){try{var data=JSON.parse(r.result); if(data.schema!==1) throw new Error('不是 OpenDictionary 备份（schema 1）'); state=data; setStatus('已载入 '+((data.wordbooks||[]).length)+' 个词书'); renderBooks();}catch(err){setStatus('解析失败：'+err.message);}};
  r.readAsText(f);
});
function loadSample(){
  state={schema:1,exportedAt:Date.now(),activity:[],history:[],notes:[],customEntries:[],wordbooks:[{name:'我的生词本',description:'默认单词本',isDefault:true,words:['reputation','weather','schedule']},{name:'阅读中遇到',description:'',isDefault:false,words:['reputation']}]};
  setStatus('示例已载入（导出后可在 App 导入）');renderBooks();
}
function renderBooks(){
  var box=document.getElementById('books');var q=(document.getElementById('search').value||'').trim().toLowerCase();
  if(!state){box.innerHTML='<p class="muted">先选择一个备份 JSON 文件。</p>';return;}
  var hits={};
  if(q){state.wordbooks.forEach(function(b){b.words.forEach(function(w){if(w.toLowerCase().indexOf(q)>=0){(hits[b.name]=hits[b.name]||[]).push(w);}});});
    document.getElementById('searchResult').textContent=Object.keys(hits).length?'出现在：'+Object.keys(hits).map(function(k){return k+'（'+hits[k].length+'）'}).join('、'):'没有找到';}
  else document.getElementById('searchResult').textContent='';
  var html='';
  state.wordbooks.forEach(function(b,bi){
    html+='<div class="bookitem" style="border-top:1px solid var(--line);padding:12px 0"><div class="formrow"><input value="'+esc(b.name)+'" onchange="state.wordbooks['+bi+'].name=this.value" style="flex:1;font-weight:600"><span class="pill">'+(b.isDefault?'默认':'普通')+'</span><button class="btn" onclick="delBook('+bi+')">删除词书</button></div><div style="display:flex;flex-wrap:wrap;gap:6px;margin:8px 0">';
    b.words.forEach(function(w,wi){ if(q&&w.toLowerCase().indexOf(q)<0)return; html+='<span class="chip">'+esc(w)+' <a href="#" onclick="delWord('+bi+','+wi+');return false">×</a></span>';});
    html+=`</div><div class="formrow"><input placeholder="添加单词" onkeydown="if(event.key==='Enter'){addWord(${bi},this.value);this.value='';}" style="flex:1"><button class="btn" onclick="addWord(${bi},this.parentElement.querySelector('input').value)">添加</button></div></div>`;
  });
  box.innerHTML=html||'<p class="muted">备份里没有词书。</p>';
  renderCustoms();
}
function renderCustoms(){
  var box=document.getElementById('customs'); if(!state){box.innerHTML='<p class="muted">先选择备份 JSON。</p>';return;}
  if(!state.customEntries) state.customEntries=[];
  if(!state.customEntries.length){box.innerHTML='<p class="muted">暂无自定义词条。</p>';return;}
  var html='';
  state.customEntries.forEach(function(e,i){
    html+='<div class="formrow" style="border-bottom:1px solid var(--line);padding:7px 0"><input value="'+esc(e.word)+'" onchange="state.customEntries['+i+'].word=this.value" style="flex:1;font-weight:600"><input value="'+esc(e.translation||'')+'" onchange="state.customEntries['+i+'].translation=this.value" style="flex:2"><input value="'+esc(e.definition||'')+'" onchange="state.customEntries['+i+'].definition=this.value" style="flex:2" placeholder="详细释义"><button class="btn" onclick="delCustom('+i+')">删除</button></div>';
  });
  box.innerHTML=html;
}
function addCustom(){
  if(!state)return;
  var w=(document.getElementById('newCustomWord').value||'').trim(); if(!w)return;
  if(!state.customEntries) state.customEntries=[];
  state.customEntries.push({word:w,translation:(document.getElementById('newCustomTrans').value||'').trim(),definition:'',phonetic:'',pos:'',tag:'custom',exchange:'',source:'Dashboard 编辑'});
  document.getElementById('newCustomWord').value='';document.getElementById('newCustomTrans').value='';
  renderCustoms();
}
function delCustom(i){if(confirm('删除词条 '+state.customEntries[i].word+'？')){state.customEntries.splice(i,1);renderCustoms();}}
function addWord(bi,w){w=(w||'').trim();if(!w)return;if(state.wordbooks[bi].words.indexOf(w)<0)state.wordbooks[bi].words.push(w);renderBooks();}
function delWord(bi,wi){state.wordbooks[bi].words.splice(wi,1);renderBooks();}
function delBook(bi){if(confirm('删除词书“'+state.wordbooks[bi].name+'”？')){state.wordbooks.splice(bi,1);renderBooks();}}
function exportJson(){if(!state){setStatus('没有可导出的数据');return;}var a=document.createElement('a');a.href=URL.createObjectURL(new Blob([JSON.stringify(state,null,2)],{type:'application/json'}));a.download='opendictionary-backup-web.json';a.click();setStatus('已导出，可在 App 备份与恢复中导入（合并模式）');}
</script>"""
    return page("我的单词本", body, active="userbooks")


def render_external_cache():
    body = """
<div class="pagehead"><div><h1>外链内容缓存（Dashboard 重新抓取）</h1>
<p class="sub">上传 App「备份与恢复」导出的 JSON，对其中的 external 词条从维基百科重新抓取摘要，更新后导出 JSON 回 App 导入（合并模式，按 fetchedAt 取新）。内容 CC BY-SA 4.0，仅抓取用户主动触发的词条。</p></div></div>
<div class="card"><div class="formrow">
<label class="btn primary">选择备份 JSON<input type="file" id="file" accept="application/json" style="display:none"></label>
<button class="btn" id="refreshAll" onclick="refreshAll()">重新抓取全部</button>
<button class="btn" id="exportBtn" onclick="exportJson()">导出 JSON</button>
</div><p class="muted" id="status">尚未载入备份。</p></div>
<div class="card"><table class="grid"><thead><tr><th>单词</th><th>来源</th><th>摘要</th><th>操作</th></tr></thead><tbody id="rows"></tbody></table></div>
<p class="muted">仅限你有权查看的内容；维基百科文本按 CC BY-SA 4.0 署名，原页面链接随条目保存。</p>
<script>
var state=null;
function setStatus(t){document.getElementById('status').textContent=t;}
function esc(s){return String(s||'').replace(/[&<>"]/g,function(c){return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c];});}
document.getElementById('file').addEventListener('change',function(e){
  var f=e.target.files[0];if(!f)return;var r=new FileReader();
  r.onload=function(){try{var d=JSON.parse(r.result); if(!d.external) d.external=[]; state=d; setStatus('已载入 '+d.external.length+' 条外链缓存'); render();}catch(err){setStatus('解析失败：'+err.message);}};
  r.readAsText(f);
});
function render(){
  var tb=document.getElementById('rows'); if(!state){tb.innerHTML='';return;}
  tb.innerHTML=state.external.map(function(x,i){return '<tr><td><b>'+esc(x.word)+'</b></td><td>'+esc(x.source)+'</td><td style="max-width:480px">'+esc((x.content||'').slice(0,160))+(x.content&&x.content.length>160?'…':'')+'</td><td><button class="btn" onclick="refreshOne('+i+')">刷新</button></td></tr>';}).join('');
}
function apply(word, data){ if(!state||!data||data.error)return false;
  var list=state.external;
  for(var i=0;i<list.length;i++){ if(list[i].word===word){ list[i]=data; return true; } }
  list.push(data); return true;
}
function refreshOne(i){
  var w=state.external[i].word; setStatus('抓取 '+w+'…');
  fetch('/api/external/fetch?word='+encodeURIComponent(w)).then(function(r){return r.json();}).then(function(d){
    if(d.error){setStatus('失败：'+d.error);return;} apply(w,d); render(); setStatus('已更新 '+w);
  }).catch(function(e){setStatus('失败：'+e);});
}
var running=false;
function refreshAll(){
  if(!state||running)return; running=true; var words=state.external.map(function(x){return x.word;}); var i=0;
  function next(){
    if(i>=words.length){running=false;setStatus('全部刷新完成');return;}
    var w=words[i++]; setStatus('抓取 '+i+'/'+words.length+'：'+w);
    fetch('/api/external/fetch?word='+encodeURIComponent(w)).then(function(r){return r.json();}).then(function(d){ if(!d.error) apply(w,d); next(); }).catch(function(){next();});
  }
  next();
}
function exportJson(){ if(!state){setStatus('没有数据');return;} var a=document.createElement('a');a.href=URL.createObjectURL(new Blob([JSON.stringify(state,null,2)],{type:'application/json'}));a.download='opendictionary-backup-external.json';a.click();setStatus('已导出，可回 App 备份与恢复导入');}
</script>"""
    return page("外链内容缓存", body, active="external")


def create_app(lex_path=None, data_dir=None):
    from fastapi import FastAPI, Form
    from fastapi.responses import HTMLResponse, RedirectResponse
    lex_path = lex_path or os.environ.get("OD_LEXICON_DB") or DEFAULT_LEXICON
    data_dir = data_dir or os.environ.get("OD_DASH_DATA") or DEFAULT_DATA
    app = FastAPI(title="OpenDictionary Dashboard v2")

    @app.get("/", response_class=HTMLResponse)
    def home():
        try:
            ls = lex_stats(lex_path)
        except FileNotFoundError as exc:
            return HTMLResponse(page("缺少词典库", "<div class='toast'>%s</div>" % esc(exc)), status_code=503)
        rv = review_conn(data_dir); ss = supplement_stats(rv); rv.close()
        return HTMLResponse(render_home(ls, ss, letters_with_counts(lex_path)))

    @app.get("/workbench")
    def workbench_redirect(letter: str = "", q: str = "", page: int = 1):
        from fastapi.responses import RedirectResponse
        return RedirectResponse("/review/words?letter=%s&q=%s&page=%d" % (letter, q, page), status_code=303)

    @app.get("/review/words", response_class=HTMLResponse)
    def workbench(letter: str = "", q: str = "", page: int = 1):
        rv = review_conn(data_dir)
        data = list_words(lex_path, letter=letter or None, q=q or None, page=max(1, page))
        body = render_workbench(lex_stats(lex_path), rv, letter, q, data, max(1, page))
        rv.close()
        return HTMLResponse(body)

    @app.get("/review/phrases", response_class=HTMLResponse)
    def phrases_page(q: str = "", book: str = "", page: int = 1):
        rv = review_conn(data_dir)
        data = list_phrases(lex_path, q=q or None, book=book or None, page=max(1, page))
        body = render_phrases(phrase_stats(lex_path, rv), rv, q, book, data, max(1, page))
        rv.close()
        return HTMLResponse(body)

    @app.get("/phrase/{phrase}", response_class=HTMLResponse)
    def phrase_page(phrase: str):
        d = phrase_detail(lex_path, phrase)
        if not d:
            return HTMLResponse(page("未找到", "<div class='toast'>没有短语 %s</div>" % esc(phrase)), status_code=404)
        rv = review_conn(data_dir)
        sup = get_supplement(rv, d["entry"]["phrase"])
        note = get_note(rv, d["entry"]["phrase"])
        rv.close()
        return HTMLResponse(render_phrase(d["entry"]["phrase"], d, sup, note))

    def _save_word_payload(word, ai_request, payload, notes, status):
        rv = review_conn(data_dir)
        save_supplement(rv, word, ai_request, payload, status, notes)
        rv.close()

    @app.post("/api/phrase/supplement")
    def phrase_supplement(word: str = Form(...), ai_request: str = Form(""), payload: str = Form(""),
                          notes: str = Form(""), status: str = Form("partial")):
        _save_word_payload(word, ai_request, payload, notes, status)
        return RedirectResponse("/phrase/%s" % word, status_code=303)

    @app.post("/api/phrase/note")
    def phrase_note(word: str = Form(...), note: str = Form("")):
        rv = review_conn(data_dir)
        save_note(rv, word, note); rv.close()
        return RedirectResponse("/phrase/%s" % word, status_code=303)

    @app.get("/word/{word}", response_class=HTMLResponse)
    def word(word: str):
        d = word_detail(lex_path, word)
        if not d:
            return HTMLResponse(page("未找到", "<div class='toast'>词典库没有 %s</div>" % esc(word)), status_code=404)
        rv = review_conn(data_dir)
        w = d["entry"]["word"]
        sup = get_supplement(rv, w)
        note = get_note(rv, w)
        rv.close()
        return HTMLResponse(render_word(w, d, sup, note, missing_suggestions(sup.get("payload")),
                                         case_hint_html(lex_path, w)))

    @app.post("/api/supplement/{word}")
    def supplement(word: str, ai_request: str = Form(""), payload: str = Form(""),
                   notes: str = Form(""), status: str = Form("partial")):
        rv = review_conn(data_dir)
        save_supplement(rv, word, ai_request, payload, status, notes)
        rv.close()
        return RedirectResponse("/word/%s" % word, status_code=303)

    @app.get("/preview/{word}", response_class=HTMLResponse)
    def preview(word: str):
        d = word_detail(lex_path, word)
        if not d:
            return HTMLResponse(page("未找到", "<div class='toast'>无此词</div>"), status_code=404)
        rv = review_conn(data_dir)
        sup = get_supplement(rv, d["entry"]["word"]); rv.close()
        return HTMLResponse(render_preview(d["entry"]["word"], d, sup))

    @app.get("/wordbooks", response_class=HTMLResponse)
    def wordbooks():
        rv = review_conn(data_dir)
        books = list_wordbooks(lex_path, rv); rv.close()
        return HTMLResponse(render_wordbooks(books))

    @app.post("/api/wordbooks")
    def create_book(book_id: str = Form(...), name: str = Form(...), description: str = Form("")):
        rv = review_conn(data_dir)
        try:
            create_wordbook(rv, book_id, name, description)
        finally:
            rv.close()
        return RedirectResponse("/wordbooks", status_code=303)

    @app.post("/api/wordbooks/add")
    def add_book_word(book_id: str = Form(...), word: str = Form(...)):
        rv = review_conn(data_dir)
        add_word_to_book(rv, book_id, word.strip()); rv.close()
        return RedirectResponse("/wordbooks", status_code=303)

    @app.post("/api/note/{word}")
    def save_public_note(word: str, note: str = Form("")):
        rv = review_conn(data_dir)
        save_note(rv, word, note); rv.close()
        return RedirectResponse("/word/%s" % word, status_code=303)

    @app.get("/api/notes/export")
    def export_public_notes():
        rv = review_conn(data_dir)
        res = export_notes(rv, os.path.join(data_dir, "export")); rv.close()
        return {"path": os.path.relpath(res["path"], ROOT), "count": res["count"]}

    @app.get("/import", response_class=HTMLResponse)
    def import_page():
        return HTMLResponse(render_import())

    @app.post("/api/import", response_class=HTMLResponse)
    def do_import(payload: str = Form(""), status: str = Form("partial")):
        rv = review_conn(data_dir)
        result = import_ai_replies(rv, payload, status); rv.close()
        return HTMLResponse(render_import(result))

    @app.get("/upstream", response_class=HTMLResponse)
    def upstream():
        return HTMLResponse(render_upstream(check_upstream()))

    @app.get("/issues", response_class=HTMLResponse)
    def issues():
        return HTMLResponse(render_issues())

    @app.get("/external-cache", response_class=HTMLResponse)
    def external_cache_page():
        return HTMLResponse(render_external_cache())

    @app.get("/api/external/fetch")
    def external_fetch(word: str):
        import urllib.parse, urllib.request, json as _json, time
        encoded = urllib.parse.quote(word.replace(" ", "_"), safe="")
        last = None
        for lang in ("zh", "en"):
            try:
                req = urllib.request.Request("https://%s.wikipedia.org/api/rest_v1/page/summary/%s" % (lang, encoded),
                                             headers={"User-Agent": "OpenDictionary-Dashboard/1.0"})
                with urllib.request.urlopen(req, timeout=15) as resp:
                    obj = _json.loads(resp.read().decode("utf-8"))
                extract = (obj.get("extract") or "").strip()
                if not extract:
                    last = "无摘要内容"; continue
                page_url = ((obj.get("content_urls") or {}).get("desktop") or {}).get("page", "")
                return {"word": word, "source": ("Wikipedia 中文（CC BY-SA 4.0）" if lang == "zh" else "Wikipedia English (CC BY-SA 4.0)"),
                        "title": obj.get("title", word), "content": extract[:4000], "url": page_url, "fetchedAt": int(time.time() * 1000)}
            except Exception as exc:
                last = str(exc)
        return {"error": last or "抓取失败"}

    @app.get("/user-wordbooks", response_class=HTMLResponse)
    def user_wordbooks_page():
        return HTMLResponse(render_user_wordbooks())

    @app.get("/api/stats")
    def api_stats():
        ls = lex_stats(lex_path)
        rv = review_conn(data_dir); ss = supplement_stats(rv)
        notes = rv.execute("SELECT COUNT(*) FROM public_notes").fetchone()[0]
        rv.close()
        return {"lexicon": ls, "supplements": ss, "public_notes": notes}

    return app


def self_test():
    ok = fail = 0

    def chk(name, cond):
        nonlocal ok, fail
        print(("PASS " if cond else "FAIL ") + name)
        ok, fail = (ok + 1, fail) if cond else (ok, fail + 1)

    tmp = tempfile.mkdtemp(prefix="od-dash2-")
    lex = os.path.join(tmp, "lexicon_core.db")
    con = sqlite3.connect(lex)
    con.executescript("""
        CREATE TABLE entries(word TEXT PRIMARY KEY, phonetic TEXT, definition TEXT, translation TEXT, pos TEXT, tag TEXT, exchange TEXT);
        CREATE TABLE word_tags(word TEXT, tag TEXT);
        CREATE TABLE wordbooks(book_id TEXT PRIMARY KEY, name TEXT, description TEXT);
        CREATE TABLE wordbook_entries(book_id TEXT, word TEXT, rank INTEGER);
        CREATE TABLE phrase_entries(phrase TEXT, translation TEXT, book_id TEXT, source TEXT, license TEXT, kind TEXT DEFAULT 'phrase');
        CREATE TABLE lemmas(form TEXT PRIMARY KEY, lemma TEXT, kind TEXT);
        CREATE TABLE meta(key TEXT PRIMARY KEY, value TEXT);
        CREATE VIRTUAL TABLE entries_fts USING fts5(word, translation);
        INSERT INTO entries VALUES('apple','ˈæpl','fruit','苹果','n','zk','');
        INSERT INTO entries VALUES('apply','əˈplaɪ','request','申请；应用','v','gk cet4','');
        INSERT INTO entries VALUES('banana','bəˈnɑːnə','fruit','香蕉','n','zk','');
        INSERT INTO word_tags VALUES('apple','zk'); INSERT INTO word_tags VALUES('apply','gk');
        INSERT INTO wordbooks VALUES('gk','高考',''); INSERT INTO wordbook_entries VALUES('gk','apply',1);
        INSERT INTO lemmas VALUES('applied','apply','d');
    """)
    con.commit(); con.close()
    chk("统计：3 词条", lex_stats(lex)["entries"] == 3)
    lst = list_words(lex, letter="A")
    chk("A 字母过滤且按字母序", lst["total"] == 2 and [x["word"] for x in lst["items"]] == ["apple", "apply"])
    chk("详情含标签", (word_detail(lex, "apply") or {})["tags"] == ["gk"])
    data = os.path.join(tmp, "dash")
    rv = review_conn(data)
    chk("初始补充统计 0", supplement_stats(rv)["total"] == 0)
    save_supplement(rv, "apple", "prompt", "v. 苹果", "partial")
    chk("草稿=partial", supplement_stats(rv)["partial"] == 1)
    save_supplement(rv, "apple", "prompt", '{"word":"apple"}', "done")
    chk("JSON 自动识别", get_supplement(rv, "apple")["payload_kind"] == "json"
        and supplement_stats(rv)["done"] == 1)
    save_supplement(rv, "banana", "", "## 释义\n- 香蕉", "question")
    chk("Markdown 识别", get_supplement(rv, "banana")["payload_kind"] == "markdown")
    try:
        save_supplement(rv, "banana", "", "", "approved"); chk("非法状态被拒", False)
    except ValueError:
        chk("非法状态被拒", True)
    create_wordbook(rv, "mybook", "我的词书"); add_word_to_book(rv, "mybook", "apple")
    books = list_wordbooks(lex, rv)
    chk("词书：默认+本地", len(books) == 2 and any(b["book_id"] == "mybook" and b["count"] == 1 for b in books))
    try:
        create_wordbook(rv, "bad id!", "x"); chk("非法 book_id 被拒", False)
    except ValueError:
        chk("非法 book_id 被拒", True)
    # 公共备注
    save_note(rv, "apple", "苹果口语也可说 apple")
    chk("公共备注保存", get_note(rv, "apple")["note"].startswith("苹果"))
    save_note(rv, "apple", "")
    chk("空备注=删除", get_note(rv, "apple")["note"] == "")
    save_note(rv, "banana", "香蕉备注")
    exp = export_notes(rv, os.path.join(tmp, "export"))
    chk("备注导出 JSON", exp["count"] == 1 and os.path.isfile(exp["path"]))
    # AI 批量导入解析
    blocks = iter_ai_blocks('### run\nv. 跑\n---\n### schedule\nn. 计划')
    chk("标题块解析 2 词", len(blocks) == 2 and blocks[0][0] == "run")
    arr = iter_ai_blocks('[{"word":"apple","x":1},{"word":"banana","x":2}]')
    chk("JSON 数组解析", len(arr) == 2 and arr[1][0] == "banana")
    res = import_ai_replies(rv, '### apply\nv. 申请', "partial")
    chk("批量导入写库", res["ok"] == 1 and get_supplement(rv, "apply")["status"] == "partial")
    chk("缺项提示可运行", isinstance(missing_suggestions(""), list) and len(missing_suggestions("")) > 0)
    try:
        rv.execute("UPDATE audit_log SET action='x'"); rv.commit(); chk("审计不可改", False)
    except sqlite3.Error:
        chk("审计不可改", True)
    rv.close()
    print("\n自检结果：%d 项通过，%d 项失败" % (ok, fail))
    return 0 if fail == 0 else 1


def main():
    ap = argparse.ArgumentParser(description="OpenDictionary 数据补充后台 v2（本地）")
    ap.add_argument("--host", default="127.0.0.1")
    ap.add_argument("--port", type=int, default=8765)
    ap.add_argument("--lexicon", default=None)
    ap.add_argument("--data", default=None)
    ap.add_argument("--self-test", action="store_true")
    args = ap.parse_args()
    if args.self_test:
        sys.exit(self_test())
    try:
        import uvicorn
    except ImportError:
        print("缺少 uvicorn：pip install fastapi uvicorn", file=sys.stderr)
        sys.exit(2)
    app = create_app(args.lexicon, args.data)
    print(f"Dashboard: http://{args.host}:{args.port}/  (Ctrl+C 停止)")
    uvicorn.run(app, host=args.host, port=args.port, log_level="warning")


if __name__ == "__main__":
    main()
