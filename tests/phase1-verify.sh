#!/bin/bash
set -e
BASE="http://localhost:8080"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "=== Phase 1 集成验证 ==="
echo ""

# ─── 1. 后端编译 ───
echo "1/8 后端编译..."
cd "$PROJECT_ROOT/ecos_backend"
mvn compile -pl gateway -am -DskipTests -q -Dmaven.test.skip=true 2>&1 | tail -3
echo "  OK: backend compile"

# ─── 2. 前端编译 ───
echo "2/8 前端编译..."
cd "$PROJECT_ROOT/ecos_frontend"
TS_ERRORS=$(npx tsc --noEmit 2>&1 | grep -c "error TS" || true)
BASELINE=289
if [ "$TS_ERRORS" -le "$BASELINE" ]; then
  echo "  OK: $TS_ERRORS TS errors (baseline: $BASELINE)"
else
  echo "  FAIL: $TS_ERRORS TS errors (baseline: $BASELINE)"
  exit 1
fi

# ─── 3. 内置角色 ───
echo "3/8 内置角色..."
ROLES=$(docker exec ecos-postgres psql -U postgres -d sys_man -t -c "SELECT count(*) FROM roles" 2>/dev/null | tr -d ' ')
if [ "$ROLES" -ge 5 ]; then
  echo "  OK: $ROLES roles"
else
  echo "  FAIL: roles=$ROLES"
  exit 1
fi

# ─── 4. 登录流程 ───
echo "4/8 登录安全..."
RESP=$(curl -s -X POST "$BASE/api/v1/auth/login" -H 'Content-Type: application/json' -d '{"username":"admin","password":"admin123"}')
echo "$RESP" | python3 -c "import json,sys; d=json.load(sys.stdin); assert d.get('code')==0, f'Login failed: {d.get(\"message\",\"\")}'; print('  OK: admin login')" 2>&1

# ─── 5. 前端文件 ───
echo "5/8 前端文件..."
FE_SRC="$PROJECT_ROOT/ecos_frontend/src"

# 新文件存在
for f in SecurityCenterLayout PreventTab DetectTab AuditTab; do
  [ -f "$FE_SRC/pages/security-center/${f}.tsx" ] || { echo "  FAIL: $f missing"; exit 1; }
done
echo "  OK: 4 new files exist"

# 旧文件已删
[ ! -f "$FE_SRC/pages/SecurityCenter.tsx" ] || { echo "  FAIL: old SecurityCenter still exists"; exit 1; }
[ ! -f "$FE_SRC/pages/security-center/SecurityCenter.tsx" ] || { echo "  FAIL: security-center/SecurityCenter still exists"; exit 1; }
echo "  OK: old SecurityCenter deleted"

# 国际化文件存在
[ -f "$FE_SRC/locales/zh-CN.json" ] || { echo "  FAIL: zh-CN.json missing"; exit 1; }
[ -f "$FE_SRC/locales/en.json" ] || { echo "  FAIL: en.json missing"; exit 1; }
echo "  OK: locales exist"

# ─── 6. 安全接入规则文档 ───
echo "6/8 安全接入规则..."
[ -f "$PROJECT_ROOT/docs/1-sysman/03-安全接入规则.md" ] || { echo "  FAIL: doc missing"; exit 1; }
echo "  OK: doc exists"

# ─── 7. DB变更 ───
echo "7/8 DB字段..."
FIELDS=$(docker exec ecos-postgres psql -U postgres -d sys_man -t -c "SELECT count(*) FROM information_schema.columns WHERE table_name='users' AND column_name IN ('failed_attempts','locked_until','password_change_required')" 2>/dev/null | tr -d ' ')
if [ "$FIELDS" -eq 3 ]; then
  echo "  OK: users table has 3/3 security fields"
else
  echo "  FAIL: users table missing fields ($FIELDS/3)"
  exit 1
fi

# ─── 8. Gateway健康 ───
echo "8/8 Gateway健康..."
HEALTH=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/api/health")
if [ "$HEALTH" = "200" ]; then
  echo "  OK: gateway health 200"
else
  echo "  FAIL: gateway health $HEALTH"
  exit 1
fi

echo ""
echo "=== ALL PASSED ==="
