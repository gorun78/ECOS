---
name: api-design
description: "RESTful API 规范设计：基于 ARCH_SPEC 输出 OpenAPI 3.0 规范文档。必须追溯到 ARCH_SPEC 模块，带评审确认门。当用户说'设计 API'、'出接口规范'、'写 OpenAPI'时触发。"
version: 2.0.0
author: Hermes Agent (AI-Native Software Factory)
license: MIT
platforms: [linux, macos, windows]
metadata:
  hermes:
    tags: [api-design, openapi, restful, swagger, interface-spec, traceability, gate]
    related_skills: [arch-design, db-design]
    artifact_type: OPENAPI
    workflow_modes: [L2, L3]
---

# API Design Skill (v2 — 模块追溯 + 评审确认门版)

## 核心原则

每个 API 必须可追溯到 ARCH_SPEC 的模块定义。所有 API 完成后必须经过评审确认门（Gate），技术负责人批准后才可分发给 Fullstack。

## 关键机制

### API → 模块追溯

每个 API path/tag 必须指向 ARCH_SPEC 模块：

```markdown
## API → 模块追溯表

| API Path | Method | Summary | 所属模块 | 覆盖 PRD 功能 |
|----------|--------|---------|---------|-------------|
| /users | GET | 查询用户列表 | user-service | F1-用户注册 |
| /users | POST | 创建用户 | user-service | F1-用户注册 |
| /orders | POST | 创建订单 | order-service | F4-下单 |
```

追溯链：`PRD 功能 → ARCH_SPEC 模块 → OpenAPI 接口`

### 评审确认门

| 门禁 | 阶段 | 停止条件 |
|------|------|---------|
| **Gate** | OpenAPI 初稿完成 | 技术负责人审批（APPROVED），deliverable_allowed=true |

**硬门槛**：未经 Gate 确认，OpenAPI 不得分发给 Fullstack。

## 触发条件

- 用户说"设计 API"、"出接口规范"、"写 OpenAPI"、"接口设计"
- PM 分发任务时指定 `role: arch`，需要输出 OpenAPI 规范
- ARCH_SPEC 必须 APPROVED（deliverable_allowed=true）

## 输入

- **必需**：ARCH_SPEC（已批准，artifact_ref，状态 APPROVED）
- **必需**：PRD（用于追溯 API → PRD 功能映射）
- **可选**：PRD 中的接口需求描述、现有 API 规范
- **固定约束**：技术栈（来自 artifact_ref）

## 输出制品

- **OPENAPI**：OpenAPI 3.0 规范文档（artifact_type: OPENAPI）
- **OPENAPI_APPROVAL_RECORD**：API 批准记录（artifact_type: APPROVAL_RECORD）

## 执行步骤

### Step 0: 前置校验 — ARCH_SPEC 批准记录检查

```python
def validate_arch_spec_for_api(arch_ref):
    """API 设计前，必须校验 ARCH_SPEC 已 APPROVED"""
    approval_record = read_artifact_approval_record(arch_ref)
    if not approval_record:
        raise ValueError(f"ARCH_SPEC {arch_ref} 无批准记录，API 设计禁止开始")
    if approval_record["status"] != "APPROVED":
        raise ValueError(f"ARCH_SPEC 状态为 {approval_record['status']}，必须 APPROVED 才能开始 API 设计")
    if not approval_record.get("deliverable_allowed"):
        raise ValueError("ARCH_SPEC deliverable_allowed=false，禁止开始 API 设计")
    return {
        "arch_version": approval_record["version"],
        "arch_hash": approval_record["hash"],
        "modules": approval_record["modules"]  # [{name, covered_prd_functions}]
    }
```

```markdown
## ARCH_SPEC 校验

收到 API 设计请求，校验以下前提条件：

1. [ ] ARCH_SPEC 状态为 APPROVED ✅
2. [ ] ARCH_SPEC 有批准记录 ✅
3. [ ] ARCH_SPEC 的 deliverable_allowed = true ✅

当前 ARCH_SPEC：
- 版本：{version}
- Hash：{hash}
- 状态：APPROVED
- 模块数量：{n} 个

→ ARCH_SPEC 校验通过，可开始 API 设计
```

---

### Step 1: 从 ARCH_SPEC 提取接口需求（带模块追溯）

1. 读取 ARCH_SPEC，确认每个模块提供的功能
2. 读取 PRD，提取每个功能的 API 需求

```python
# API → 模块 → PRD 追溯映射
def build_api_module_prd_mapping(arch_spec, prd):
    """
    建立 API → 模块 → PRD 追溯映射
    """
    mapping = []
    for module in arch_spec["modules"]:
        module_name = module["name"]
        prd_functions = module["covered_prd_functions"]

        # 从 PRD 提取该模块对应的功能需求
        module_prd = [f for f in prd["functions"] if f["id"] in prd_functions]

        # 推导该模块需要暴露的 API
        apis = derive_apis_from_functions(module_prd, module_name)

        mapping.append({
            "module": module_name,
            "prd_functions": prd_functions,
            "apis": apis
        })

    return mapping
```

输出追溯表：

```markdown
## API → 模块 → PRD 追溯表

| API Path | Method | Summary | 所属模块 | 覆盖 PRD 功能 |
|----------|--------|---------|---------|-------------|
| /users | GET | 查询用户列表 | user-service | F1-用户注册, F2-登录 |
| /users | POST | 创建用户 | user-service | F1-用户注册 |
| /users/{id} | GET | 获取用户详情 | user-service | F1-用户注册, F3-个人中心 |
| /users/{id} | PUT | 更新用户 | user-service | F3-个人中心 |
| /users/{id} | DELETE | 删除用户 | user-service | F1-用户注册 |
| /orders | GET | 查询订单列表 | order-service | F4-下单, F6-取消退款 |
| /orders | POST | 创建订单 | order-service | F4-下单 |
```

---

### Step 2: 命名规范（RESTful 原则）

```
资源命名：
- 使用名词，而非动词：/users 而非 /getUsers
- 复数形式：/users 而非 /user
- 小写 + 连字符：/user-profiles 而非 /userProfiles
- 层级嵌套表示关系：/users/{id}/orders

HTTP 方法映射：
GET    - 查询（幂等，不修改数据）
POST   - 创建（ 非幂等）
PUT    - 全量更新（幂等）
PATCH  - 部分更新（幂等）
DELETE - 删除（幂等）
```

---

### Step 3: 编写 OpenAPI 规范（带模块 tag 标注）

每个 API 的 `tags` 字段必须指向 ARCH_SPEC 模块名称：

```yaml
openapi: 3.0.3
info:
  title: 订单系统 API
  version: 1.0.0
  description: 订单系统 RESTful API 规范
  x-arch-ref: ARCH_SPEC@{hash}
  x-prd-ref: PRD@{prd_hash}

servers:
  - url: https://api.example.com/v1
    description: 生产环境
  - url: https://staging-api.example.com/v1
    description: 预发布环境

paths:
  /users:
    get:
      summary: 查询用户列表
      description: 支持分页、过滤、排序
      operationId: listUsers
      tags:
        - user-service    # ← 必须是 ARCH_SPEC 模块名称
      x-module: user-service
      x-prd-functions: [F1, F2]
      parameters:
        - name: page
          in: query
          schema:
            type: integer
            default: 1
          description: 页码
        ...
```

---

### Step 4: 定义错误码规范

```markdown
## 错误码规范

| 错误码 | HTTP Status | 说明 |
|--------|-------------|------|
| INVALID_PARAMETER | 400 | 请求参数错误 |
| UNAUTHORIZED | 401 | 未认证 |
| FORBIDDEN | 403 | 无权限 |
| NOT_FOUND | 404 | 资源不存在 |
| CONFLICT | 409 | 资源冲突 |
| INTERNAL_ERROR | 500 | 内部错误 |

错误响应格式：
```json
{
  "code": "NOT_FOUND",
  "message": "用户不存在",
  "details": [],
  "traceId": "abc123"
}
```

---

### Step 5: 评审确认门

```markdown
---

**【API 设计评审确认门】**

请确认以下内容：
1. 所有 API 都有模块归属（tags 对应 ARCH_SPEC 模块）？
2. 所有 API 都有 PRD 功能来源追溯（x-prd-functions）？
3. RESTful 命名规范是否遵守？
4. 认证/授权定义是否完整？
5. 错误码规范是否统一？
6. OpenAPI 文档是否通过 swagger-cli validate？

**审批人**：技术负责人

**批准结果**：
- APPROVED → 状态改为 APPROVED，生成 Hash，分发给 Fullstack
- REJECTED → 打回修改
```

---

### Step 6: 生成 APPROVAL_RECORD

审批通过后，生成 `OPENAPI_APPROVAL_RECORD`：

```json
{
  "artifact": "OPENAPI",
  "name": "{系统名称} API 规范",
  "version": "v{version}",
  "hash": "{内容哈希}",
  "status": "APPROVED",
  "approvals": [
    {
      "role": "技术负责人",
      "result": "APPROVED",
      "timestamp": "{timestamp}",
      "conditions": []
    }
  ],
  "gates_passed": ["Gate-1"],
  "deliverable_allowed": true,
  "arch_ref": "ARCH_SPEC@{arch_hash}",
  "prd_ref": "PRD@{prd_hash}",
  "api_counts": {
    "user-service": {n},
    "order-service": {n}
  },
  "prev_version": null,
  "next_version": null
}
```

---

### Step 7: 工具验证

```bash
# 安装 swagger-cli
npm install -g swagger-cli

# 验证 OpenAPI 文档
swagger-cli validate docs/architecture/openapi_v{version}.yaml
```

---

## 验证步骤

1. [ ] ARCH_SPEC 校验通过（APPROVED + APPROVAL_RECORD）
2. [ ] 每个 API 有 `x-module` 字段指向 ARCH_SPEC 模块
3. [ ] 每个 API 有 `x-prd-functions` 字段指向 PRD 功能
4. [ ] OpenAPI 通过 swagger-cli validate
5. [ ] 评审确认门已通过（Gate-1 APPROVED）
6. [ ] OPENAPI_APPROVAL_RECORD 已生成，deliverable_allowed=true
7. [ ] API 数量与 ARCH_SPEC 模块功能匹配（无遗漏模块）
8. [ ] RESTful 命名规范 100% 遵守

## 保存位置

- `docs/architecture/openapi_v{version}.yaml`
- 批准记录：`docs/architecture/openapi_v{version}-approval-record.json`

## 常见陷阱

1. **API 无模块归属**：tag 不对应 ARCH_SPEC 模块名称
2. **API 无 PRD 来源**：x-prd-functions 为空，无法追溯
3. **RESTful 不规范**：用动词而非名词（`/getUsers` 而非 `/users`）
4. **过度设计**：L2 场景不要做复杂的过滤、排序、分页组合
5. **跳过评审门**：OpenAPI 未审批就分发给 Fullstack