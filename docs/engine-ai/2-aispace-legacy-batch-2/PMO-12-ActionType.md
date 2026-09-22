# PMO指令：Phase2-2-ActionType — Ontology ActionType实现

> 来源: 完善计划 Phase 2-2 T4 | 工期: 2周 | 范围: ontology-engine后端 + 前端本体工作台 | 依赖: Phase 2-1完成

---

## §背景

ontology-engine有ObjectType+LinkType+Function，缺ActionType。Agent想操作数据只能调通用SQL/REST工具，无前置条件校验、无后置动作、无审计约束。需要让Ontology从"CRUD对象模型"升级为"Agent可执行的业务语义层"。

---

## §关于本次改动的上下文

改动涉及：
- **ontology-engine**：新增ActionType数据模型、Controller、Service、前置条件引擎、后置动作执行器
- **security-engine**：ActionType执行时自动调权限校验（已有端点，只需接入）
- **前端**：本体工作台新增ActionType Tab

---

## §禁止清单

1. ❌ 不改已有ObjectType/LinkType的API签名
2. ❌ 不新增Maven模块
3. ❌ ActionType执行失败必须返回明确错误原因（前置条件不满足/权限不足/后置失败）
4. ❌ 每次ActionType.execute() 强制写审计日志（不可绕过）

---

## §Task

### T4a: ActionType数据模型（2天）

**DB表**:
```sql
CREATE TABLE IF NOT EXISTS ecos_action_type (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(128) NOT NULL UNIQUE,
    description TEXT,
    object_type_id VARCHAR(64) NOT NULL,
    preconditions TEXT,         -- JSON数组: [{"field":"status","op":"eq","value":"pending"}]
    post_actions TEXT,          -- JSON数组: [{"type":"update_field","field":"status","value":"approved"},{"type":"notify","target":"applicant"}]
    audit_required BOOLEAN DEFAULT true,
    enabled BOOLEAN DEFAULT true,
    created_by VARCHAR(64),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);
```

**MyBatis Mapper**: 新建 `mapper/ActionTypeMapper.xml`

**API接口**: 在 `ontology-engine-api` 新增 `ActionTypeService` 接口

### T4b: ActionType Controller + Service（3天）

**文件**: `ontology-engine-impl/.../controller/ActionTypeController.java`

| 端点 | 方法 | 用途 |
|------|------|------|
| `/api/v1/ontology/actions` | POST | 创建 |
| `/api/v1/ontology/actions` | GET | 列表（可按objectTypeId筛选）|
| `/api/v1/ontology/actions/{id}` | GET | 详情 |
| `/api/v1/ontology/actions/{id}` | PUT | 更新 |
| `/api/v1/ontology/actions/{id}` | DELETE | 删除 |
| `/api/v1/ontology/actions/{id}/execute` | POST | 执行 |

**execute请求格式**:
```json
{"objectId": "po_001", "context": {"userId": "admin", "reason": "审批通过"}}
```

**execute响应格式**:
```json
{
  "code": 0,
  "data": {
    "actionId": "approve_order",
    "objectId": "po_001",
    "preconditionCheck": {"passed": true, "checks": [{"field":"status","expected":"pending","actual":"pending","passed":true}]},
    "execution": {"success": true, "changes": [{"field":"status","from":"pending","to":"approved"}]},
    "postActions": [{"type":"notify","target":"applicant","status":"sent"}],
    "auditId": "audit_xxx"
  }
}
```

### T4c: 前置条件引擎（2天）

**文件**: 新建 `PreconditionEngine.java`

**支持的op类型**: `eq`/`neq`/`in`/`gt`/`lt`/`contains`/`regex`/`hasRole`

**执行流程**:
1. 解析 `preconditions` JSON
2. 对每个条件：查ontology对象当前状态 → 比对
3. 调 `POST /api/security/policy/evaluate` 检查权限
4. 所有条件通过才执行

### T4d: 后置动作执行器（2天）

**文件**: 新建 `PostActionExecutor.java`

**支持的type**: `update_field`/`notify`/`trigger_pipeline`/`write_audit`

**执行**: 异步(`@Async`)，失败不阻塞主流程但记录到audit

### T4e: 前端ActionType Tab（2天）

**文件**: `components/aiworkbench/ontology/ActionTypeTab.tsx`

**功能**:
- ActionType列表（名称/对象类型/启用状态/操作）
- 新建/编辑Modal（前置条件JSON编辑器 + 后置动作配置）
- 执行测试面板（选择ActionType→输入objectId→调execute→实时显示前置检查+执行结果）

**验收**:
```bash
curl -X POST /api/v1/ontology/actions \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"name":"approve_order","objectTypeId":"PurchaseOrder","preconditions":[{"field":"status","op":"eq","value":"pending"}],"postActions":[{"type":"update_field","field":"status","value":"approved"}],"auditRequired":true}'

curl -X POST /api/v1/ontology/actions/approve_order/execute \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"objectId":"po_001","context":{"userId":"admin"}}'
# 期望: preconditionCheck.passed=true, execution.success=true, auditId非空
```
