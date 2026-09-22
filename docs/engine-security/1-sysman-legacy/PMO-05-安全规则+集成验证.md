# PMO指令：Phase1-sysman-05 — 安全接入规则 + 集成验证

> **完善计划**: T6 + T7 | **工期**: 1天 | **范围**: 文档 + 验证脚本 | **依赖**: PMO-01 ~ PMO-04

---

## §Task

### T6: 安全接入规则文档

**新建**: `docs/1-sysman/03-安全接入规则.md`

**内容**:

```markdown
# ECOS 安全接入规则

## 原则
1. 所有安全检查统一走 security-engine REST API
2. 各引擎不在自身代码中重复实现权限/脱敏/审计逻辑
3. 审计日志集中写 security-engine，各引擎不维护自己的审计表

## Phase 1 可用端点

| 端点 | 方法 | 用途 | 调用方 |
|------|------|------|--------|
| POST /api/v1/security/policy/evaluate | POST | ABAC策略评估 | 所有引擎 |
| POST /api/v1/security/audit/log | POST | 写审计日志 | 所有引擎 |

### policy/evaluate 请求格式
```json
{
  "subject": {"userId": "u_001", "role": "analyst", "clearanceLevel": 2},
  "resource": {"type": "ontology", "id": "obj_finance", "classification": "内部"},
  "action": "read",
  "context": {"ip": "192.168.1.1", "time": "2026-08-03T09:00:00"}
}
```

### policy/evaluate 响应格式
```json
{"allowed": true, "matchedPolicies": ["POL-001"], "obligations": ["mask_salary"]}
```

## 后续Phase接入指南

| 引擎 | 接入Phase | 接入时机 | 接入端点 |
|------|:--:|------|------|
| data-engine | Phase 2 | 查询前调RLS/CLS，查询后调脱敏 | rls/apply, cls/columns, masking/apply |
| ontology-engine | Phase 3 | Function执行前调权限检查 | policy/evaluate |
| cognitive-engine | Phase 4 | 诊断/推理前调PBAC合规检查 | pbac/check |
| kb-engine | Phase 4 | 知识抽取前调权限检查 | policy/evaluate |
| ai-engine | Phase 5 | Agent工具调用前调策略评估 | policy/evaluate |

## 调用规范
- 请求超时: 5秒（安全引擎应快速响应）
- 失败处理: 安全引擎不可用时默认DENY（宁可误拒不可误放）
- 审计日志: 异步写入，不阻塞主流程
```

**同时更新**: `engine/security-engine/AGENTS.md`，在"我暴露的端点"章节补充Phase 1可用端点和接入规则引用。

### T7: 集成验证脚本

**新建**: `tests/phase1-verify.sh`

```bash
#!/bin/bash
set -e
BASE="http://localhost:8080"
echo "=== Phase 1 集成验证 ==="

# ─── 1. 编译 ───
echo "1/7 后端编译..."
cd /home/guorongxiao/ECOS/ecos_backend
mvn compile -pl gateway -am -DskipTests -q -Dmaven.test.skip=true

echo "2/7 前端编译..."
cd /home/guorongxiao/ECOS/ecos_frontend
npx tsc --noEmit 2>&1 | grep -q "error TS" && exit 1 || true

# ─── 2. 内置角色 ───
echo "3/7 内置角色..."
ROLES=$(psql -h localhost -U postgres -d sys_man -t -c "SELECT count(*) FROM roles" 2>/dev/null | tr -d ' ')
[ "$ROLES" -ge 5 ] || { echo "FAIL: roles=$ROLES"; exit 1; }
echo "  OK: $ROLES roles"

# ─── 3. 登录流程 ───
echo "4/7 登录安全..."
# 正确登录
RESP=$(curl -s -X POST $BASE/api/v1/auth/login -H 'Content-Type: application/json' -d '{"username":"admin","password":"admin123"}')
echo "$RESP" | python3 -c "import json,sys; d=json.load(sys.stdin); assert 'token' in d.get('data',{}), 'No token'"
echo "  OK: admin login"

# ─── 4. 前端文件 ───
echo "5/7 前端文件..."

# 新文件存在
for f in SecurityCenterLayout PreventTab DetectTab AuditTab; do
  [ -f "/home/guorongxiao/ECOS/ecos_frontend/src/pages/security-center/${f}.tsx" ] || { echo "FAIL: $f missing"; exit 1; }
done

# 旧文件已删
[ ! -f "/home/guorongxiao/ECOS/ecos_frontend/src/pages/SecurityCenter.tsx" ] || { echo "FAIL: old SecurityCenter still exists"; exit 1; }
[ ! -f "/home/guorongxiao/ECOS/ecos_frontend/src/pages/business-workbench/SecurityCenterView.tsx" ] || { echo "FAIL: SecurityCenterView still exists"; exit 1; }
[ ! -f "/home/guorongxiao/ECOS/ecos_frontend/src/pages/security-center/SecurityCenter.tsx" ] || { echo "FAIL: third SecurityCenter still exists"; exit 1; }

# 国际化文件存在
[ -f "/home/guorongxiao/ECOS/ecos_frontend/src/locales/zh-CN.json" ] || { echo "FAIL: zh-CN.json missing"; exit 1; }
[ -f "/home/guorongxiao/ECOS/ecos_frontend/src/locales/en.json" ] || { echo "FAIL: en.json missing"; exit 1; }
echo "  OK: all files verified"

# ─── 5. 安全接入规则文档 ───
echo "6/7 安全接入规则..."
[ -f "/home/guorongxiao/ECOS/docs/1-sysman/03-安全接入规则.md" ] || { echo "FAIL: doc missing"; exit 1; }
echo "  OK: doc exists"

# ─── 6. DB变更 ───
echo "7/7 DB字段..."
FIELDS=$(psql -h localhost -U postgres -d sys_man -t -c "SELECT count(*) FROM information_schema.columns WHERE table_name='users' AND column_name IN ('failed_attempts','locked_until','password_change_required')" 2>/dev/null | tr -d ' ')
[ "$FIELDS" -eq 3 ] || { echo "FAIL: users table missing fields ($FIELDS/3)"; exit 1; }
echo "  OK: users table has security fields"

echo ""
echo "=== ALL PASSED ==="
```

**设置权限**: `chmod +x tests/phase1-verify.sh`

---

## §验收

```bash
# 文档存在
ls /home/guorongxiao/ECOS/docs/1-sysman/03-安全接入规则.md

# 验证脚本可执行
bash /home/guorongxiao/ECOS/tests/phase1-verify.sh
# 期望: ALL PASSED
```
