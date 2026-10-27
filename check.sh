#!/usr/bin/env bash
# 本仓库最小判据入口：存在性 + 后台自检 + 更新契约。
set -u
pass=0; fail=0
ok() { pass=$((pass + 1)); echo "PASS  $1"; }
ng() { fail=$((fail + 1)); echo "FAIL  $1"; }

[ -f app/settings.gradle.kts ] && ok "A01 Android 工程存在" || ng "A01 Android 工程存在"
if python3 tools/dashboard/app.py --self-test >/dev/null 2>&1; then
  ok "A02 数据后台自检通过"
else
  ng "A02 数据后台自检通过"
fi
[ -f UPDATE.md ] && [ -x scripts/publish-apk-release.sh ] && ok "A03 更新契约与发布脚本存在" || ng "A03 更新契约与发布脚本存在"

echo "===== ${pass} 通过 / ${fail} 失败 ====="
[ "$fail" -eq 0 ]
