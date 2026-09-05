---
name: fullstack-impl
description: "Fullstack 全栈开发工作流编排 Skill：接收 PM 的 dispatch 指令，编排前端/后端并行开发，验证制品校验、状态上报和修复循环。所有产出必须通过 build-verify 并生成 SOURCE_PATCH_APPROVAL_RECORD（deliverable_allowed 判定）后才可分发 Reviewer/QA。当 PM 向 fullstack 分发任务时触发。"
version: 2.0.0
author: Hermes Agent (AI-Native Software Factory)
license: MIT
platforms: [linux, macos, windows]
metadata:
  hermes:
    tags: [fullstack, workflow, orchestration, parallel-development, approval-record]
    related_skills: [frontend-builder, backend-builder, build-verify]
    artifact_type: SOURCE_PATCH
    workflow_modes: [L1, L2, L3]
---

# Fullstack Implementation Skill (v2 — 阶段门禁 + 制品批准记录版)

## 核心原则

所有 Fullstack 产出必须通过 build-verify 并生成 `SOURCE_PATCH_APPROVAL_RECORD`，`deliverable_allowed=true` 才可分发给 Reviewer/QA。L2/L3 必须分阶段确认，阶段间有门禁。

## 关键机制

### 制品批准记录（SOURCE_PATCH_APPROVAL_RECORD）

每个 Fullstack 产出必须携带批准记录：

```json
{
  "artifact": "SOURCE_PATCH",
  "name": "{项目名称} 源代码",
  "version": "v{version}",
  "hash": "{内容哈希}",
  "status": "APPROVED",
  "workflow_mode": "L3",
  "approvals": [
    {
      "role": "build-verify",
      "result": "APPROVED",
      "timestamp": "{timestamp}",
      "conditions": []
    }
  ],
  "gates_passed": ["ARCH_SPEC_GATE", "BUILD_GATE"],
  "deliverable_allowed": true,
  "prd_ref": "PRD@{prd_hash}",
  "arch_ref": "ARCH_SPEC@{arch_hash}",
  "openapi_ref": "OPENAPI@{openapi_hash}",
  "ddl_ref": "DDL@{ddl_hash}",
  "artifacts": {
    "backend": "SOURCE_PATCH@{hash}#backend",
    "frontend": "SOURCE_PATCH@{hash}#frontend",
    "unit_tests": "UNIT_TEST@{hash}"
  },
  "prev_version": null,
  "next_version": null
}
```

### 阶段门禁

| 门禁 | 阶段 | 停止条件 |
|------|------|---------|
| **第一门** | 制品校验完成 | 上游制品（ARCH_SPEC/OpenAPI/DDL）全部 APPROVED |
| **第二门** | 前后端开发完成 | backend-builder + frontend-builder 均报告 DONE |
| **第三门** | 构建验证完成 | build-verify 报告 `deliverable_allowed=true` |

## 触发条件

- PM 向 fullstack 分发任务（`role: fullstack`）
- 收到上游 Arch 的 ADR/OpenAPI/DDL 制品
- 需要编排前端+后端并行开发时

## 输入

- **必需**：ARCH_SPEC（APPROVED）、OpenAPI（APPROVED）、DDL（APPROVED）
- **必需**：工作流模式（L1/L2/L3）
- **可选**：UI 规范、现有代码、测试用例模板
- **固定约束**：技术栈、编码规范、安全规范、项目版本

## 输出制品

- **SOURCE_PATCH**：源代码变更（artifact_type: SOURCE_PATCH）
  - `SOURCE_PATCH@{hash}#backend`：后端代码
  - `SOURCE_PATCH@{hash}#frontend`：前端代码
  - `UNIT_TEST/`：单元测试代码
  - `CHANGE_NOTE`：变更说明
- **SOURCE_PATCH_APPROVAL_RECORD**：产出批准记录（artifact_type: APPROVAL_RECORD）

## 执行步骤

### Step 0: 前置校验 — 上游制品批准记录检查（第一门禁）

收到 PM 的 dispatch 指令后，执行制品校验：

```python
def validate_upstream_artifacts(task_dispatch):
    """
    Fullstack 开始前，必须校验所有上游制品已 APPROVED
    """
    required_artifacts = {
        "ARCH_SPEC": task_dispatch.get("arch_ref"),
        "OPENAPI": task_dispatch.get("openapi_ref"),
        "DDL": task_dispatch.get("ddl_ref")
    }

    results = {}
    all_approved = True

    for artifact_type, artifact_ref in required_artifacts.items():
        approval_record = read_artifact_approval_record(artifact_ref)
        if not approval_record or approval_record["status"] != "APPROVED":
            results[artifact_type] = {
                "status": "REJECTED",
                "reason": f"{artifact_type} {artifact_ref} 未 APPROVED",
                "deliverable_allowed": False
            }
            all_approved = False
        else:
            results[artifact_type] = {
                "status": "APPROVED",
                "version": approval_record["version"],
                "hash": approval_record["hash"],
                "deliverable_allowed": True
            }

    if not all_approved:
        raise ArtifactGateException(
            f"上游制品门禁未通过，禁止开始 Fullstack 开发\n"
            f"{json.dumps(results, indent=2)}"
        )

    return results
```

```markdown
## 制品校验 — 第一门禁

收到任务：{task_id}
上游制品校验：

| 制品 | 状态 | 版本 | Hash | deliverable_allowed |
|------|------|------|------|---------------------|
| ARCH_SPEC | ✅ APPROVED | v1.0.0 | abc123 | true |
| OPENAPI | ✅ APPROVED | v1.0.0 | def456 | true |
| DDL | ✅ APPROVED | v1.0.0 | ghi789 | true |

**第一门禁通过** ✅ — 所有上游制品已批准，可开始 Fullstack 开发

开始解析任务...
```

校验失败时回复 PM：

```json
{
  "artifact": "SOURCE_PATCH",
  "task_id": "{task_id}",
  "status": "BLOCKED",
  "gate": "FIRST_GATE",
  "blocker": "UPSTREAM_ARTIFACT_NOT_APPROVED",
  "failed_artifacts": ["ARCH_SPEC"],
  "deliverable_allowed": false,
  "message": "ARCH_SPEC 状态为 DRAFT，必须 APPROVED 才能开始 Fullstack 开发"
}
```

---

### Step 1: 解析 OpenAPI 生成任务列表

从 OpenAPI 规范解析出 API 列表，按模块分组：

```python
# OpenAPI 解析结果
api_groups = {
    "user-service": [
        {"method": "POST", "path": "/users", "operationId": "createUser"},
        {"method": "GET", "path": "/users/{id}", "operationId": "getUser"},
        {"method": "PUT", "path": "/users/{id}", "operationId": "updateUser"},
        {"method": "DELETE", "path": "/users/{id}", "operationId": "deleteUser"},
    ],
    "order-service": [
        {"method": "POST", "path": "/orders", "operationId": "createOrder"},
        {"method": "GET", "path": "/orders/{id}", "operationId": "getOrder"},
    ]
}

# 任务拆分
tasks = []
for service, apis in api_groups.items():
    tasks.append({
        "type": "backend",
        "service": service,
        "apis": apis
    })
    tasks.append({
        "type": "frontend",
        "service": service,
        "page": f"{service}-list-page"
    })

# PRD → OpenAPI → SOURCE_PATCH 追溯
traceability = {
    "user-service": {
        "prd_functions": ["F1-用户注册", "F2-登录", "F3-个人中心"],
        "apis": ["createUser", "getUser", "updateUser", "deleteUser"],
        "pages": ["user-list-page", "user-detail-page"]
    },
    "order-service": {
        "prd_functions": ["F4-下单", "F5-支付", "F6-取消退款"],
        "apis": ["createOrder", "getOrder"],
        "pages": ["order-list-page", "order-detail-page"]
    }
}
```

---

### Step 2: 第二门禁 — 前后端开发确认

在开始前后端开发前，确认分工和接口契约：

```markdown
---

**【第二门禁 — 前后端开发确认】**

请确认以下内容：
1. 后端负责的 API（user-service/order-service）是否清晰？
2. 前端负责的页面（user-list-page/order-list-page 等）是否清晰？
3. 接口契约（OpenAPI）是否已确认？
4. 依赖关系（后端先完成 API 定义，前端再对接）是否理解？
5. L2 并行策略（时间错位模拟）是否接受？

**确认后输出**：第二门已确认，开始前后端并行开发。
```

---

### Step 3: L2 前后端并行模拟

L2 场景下，通过时间错位模拟并行：

```
Phase 1（0-50% 时间）：
  - 完成后端所有 API 实现
  - 前端准备数据结构和 mock 数据

Phase 2（50-100% 时间）：
  - 前端对接真实 API
  - 完成后端单元测试

实际效果：前后端并行开发体验
```

---

### Step 4: 状态上报

开发过程中定期向 PM 报告：

```markdown
STATUS_UPDATE: {task_id}
  gate: SECOND_GATE
  phase: fullstack
  status: IN_PROGRESS
  backend: 65%
    - user-service: 完成
    - order-service: 4/5 API
  frontend: 30%
    - user-list-page: 完成
    - order-list-page: 开发中
  unit_test_gate: PENDING
  build_gate: PENDING
  next: 前端对接 API → 集成测试
```

分阶段上报：
- 0%：任务开始（第一门通过）
- 25%：后端 API 完成 50%
- 50%：后端 API 全部完成，前端开始对接（第二门确认后）
- 75%：前端对接完成，单元测试中
- 100%：自检完成，准备提交 build-verify

---

### Step 5: 第三门禁 — build-verify 执行

前后端开发完成后，必须调用 build-verify 进行构建验证：

```python
def execute_build_verification(source_code):
    """
    第三门禁：必须通过 build-verify 全部质量门禁
    """
    result = run_build_verify(source_code)

    if result["deliverable_allowed"]:
        return {
            "gate": "THIRD_GATE",
            "status": "PASSED",
            "approval_record": result["BUILD_APPROVAL_RECORD"],
            "deliverable_allowed": True
        }
    else:
        raise BuildGateException(
            f"第三门禁（构建验证）未通过，禁止分发 Reviewer/QA\n"
            f"失败的门禁：{result['failed_gates']}\n"
            f"完整报告：{result['BUILD_REPORT']}"
        )
```

```markdown
## 第三门禁 — build-verify 结果

| 门禁 | 状态 | 详情 |
|------|------|------|
| BUILD_GATE | ✅ PASS | 前端 dist/ + 后端 app.jar 产出成功 |
| TEST_GATE | ✅ PASS | 前端 156/156，后端 156/156，0 失败 |
| COVERAGE_GATE | ✅ PASS | 前端 78.5%，后端 81.2% ≥ 75% |
| LINT_GATE | ✅ PASS | L3 0 errors |
| SECURITY_GATE | ✅ PASS | 0 vulnerabilities |

**第三门禁通过** ✅ — `deliverable_allowed=true`

可生成 SOURCE_PATCH_APPROVAL_RECORD。
```

---

### Step 6: 生成 SOURCE_PATCH_APPROVAL_RECORD

```json
{
  "artifact": "SOURCE_PATCH",
  "name": "{项目名称} 源代码",
  "version": "v{version}",
  "hash": "{内容哈希}",
  "status": "APPROVED",
  "workflow_mode": "L3",
  "approvals": [
    {
      "role": "build-verify",
      "result": "APPROVED",
      "timestamp": "{timestamp}",
      "conditions": []
    }
  ],
  "gates_passed": ["FIRST_GATE", "SECOND_GATE", "THIRD_GATE"],
  "deliverable_allowed": true,
  "prd_ref": "PRD@{prd_hash}",
  "arch_ref": "ARCH_SPEC@{arch_hash}",
  "openapi_ref": "OPENAPI@{openapi_hash}",
  "ddl_ref": "DDL@{ddl_hash}",
  "artifacts": {
    "backend": "SOURCE_PATCH@{backend_hash}#backend",
    "frontend": "SOURCE_PATCH@{frontend_hash}#frontend",
    "unit_tests": "UNIT_TEST@{test_hash}"
  },
  "traceability": {
    "user-service": {
      "prd_functions": ["F1", "F2", "F3"],
      "apis": ["createUser", "getUser", "updateUser", "deleteUser"],
      "pages": ["user-list-page", "user-detail-page"]
    }
  },
  "build_approval": {
    "build_hash": "{build_hash}",
    "test_passed": 312,
    "test_failed": 0,
    "coverage_frontend": 0.785,
    "coverage_backend": 0.812
  },
  "prev_version": null,
  "next_version": null
}
```

---

### Step 7: 修复循环处理

收到 PM 的修复指令时：

```markdown
## 修复循环

收到修复指令：{task_id}
feedback 来源：
- Reviewer：docs/03开发阶段/{NN}-审查报告/{task_id}_feedback.md
- QA：tests/{task_id}_feedback.md

缺陷列表（按优先级排序）：
1. [P0] SQL 注入漏洞 - /users/{id} DELETE
2. [P1] 缺少参数校验 - createUser POST
3. [P2] 日志不规范 - 统一使用 slf4j

开始修复...
```

修复后必须重新通过 build-verify 第三门禁。

---

### Step 8: 完成通知

```markdown
TASK_DONE: {task_id}

  产出：
    - SOURCE_PATCH@{hash}#backend（已通过 build-verify）
    - SOURCE_PATCH@{hash}#frontend（已通过 build-verify）
    - UNIT_TEST@{hash}（312 测试用例）
    - SOURCE_PATCH_APPROVAL_RECORD（已生成）

  质量：
    - 构建：✅ 通过
    - 单元测试：✅ 312/312 通过
    - 覆盖率：前端 78.5% / 后端 81.2%
    - security：✅ 0 vulnerabilities

  **deliverable_allowed: true** ✅

  状态：已分发 Reviewer + QA 审核
```

---

## 异常上报

| 场景 | 上报 | 处理 |
|------|------|------|
| 第一门失败 | `BLOCKED: UPSTREAM_ARTIFACT_NOT_APPROVED` | 等待上游制品 APPROVED |
| Arch 设计不可行 | `BLOCKED: DESIGN_INFEASIBLE` + 原因 | PM 触发 Arch 补充设计 |
| API 规范与实现不符 | `BLOCKED: API_CONTRACT_VIOLATION` | 暂停，通知 PM |
| 构建失败 | `FAILED: BUILD_ERROR` + 日志 | 自检后重试 |
| 单元测试失败 | `FAILED: UNIT_TEST_FAILED` + 列表 | 修复后重试 |
| 第三门失败 | `BLOCKED: BUILD_GATE_FAILED` | 修复问题，重新 build-verify |
| 执行超时 | `FAILED: TIMEOUT` + 已完成部分 | PM 决定降级或延期 |

---

## PM 回复模板

### 任务接收确认

```
✅ 收到任务：{task_id}
  工作流模式：L3
  **第一门禁状态**：所有上游制品 APPROVED ✅

  制品版本：
    - PRD: v1.0.0 @ abc123
    - ARCH_SPEC: v1.0.0 @ def456
    - OPENAPI: v1.0.0 @ ghi789
    - DDL: v1.0.0 @ jkl012

  任务拆解：
    - 后端：2 个服务，6 个 API
    - 前端：2 个页面，4 个组件
    - 测试：单元测试 + 集成测试

  预计完成时间：{deadline}
  **deliverable_allowed: true**（前提：所有上游制品 APPROVED）
  开始执行...
```

### 阶段汇报

```
📊 进度汇报：{task_id}
  门禁状态：
    - 第一门：✅ 通过（上游制品校验）
    - 第二门：⏳ 进行中（前后端开发）
    - 第三门：⏳ 等待（build-verify）

  后端进度：▓▓▓▓▓▓▓░░░ 70%
    - user-service: 完成
    - order-service: 4/5 API

  前端进度：▓▓▓░░░░░░░ 30%
    - user-list-page: 完成
    - order-list-page: 开发中

  下一步：order-list-page 对接 API → build-verify
```

### 完成通知

```
✅ 开发完成：{task_id}

  **第三门禁状态**：build-verify 全部通过 ✅
  **deliverable_allowed: true** ✅

  产出：
    - SOURCE_PATCH#backend：{n} 文件
    - SOURCE_PATCH#frontend：{n} 文件
    - UNIT_TEST：{n} 测试用例

  质量：
    - 构建：✅ 通过
    - 单元测试：✅ 312/312 通过
    - 覆盖率：前端 78.5% / 后端 81.2%

  SOURCE_PATCH_APPROVAL_RECORD：
    - status: APPROVED
    - deliverable_allowed: true
    - 所有门禁通过

  状态：已分发 Reviewer + QA 审核
```

---

## 验证步骤

1. [ ] 第一门：上游制品（ARCH_SPEC/OpenAPI/DDL）全部 APPROVED
2. [ ] 第二门：前后端开发确认（用户确认或自动确认）
3. [ ] 第三门：build-verify 全部门禁通过
4. [ ] SOURCE_PATCH_APPROVAL_RECORD 已生成，deliverable_allowed=true
5. [ ] 制品校验通过（artifact_ref 一致）
6. [ ] 后端 API 与 OpenAPI 规范完全一致
7. [ ] 前端页面与 UI 规范一致
8. [ ] 单元测试覆盖率达标（L2 ≥ 60%，L3 ≥ 75%）
9. [ ] 构建成功，无警告
10. [ ] 状态上报及时（每 20% 进度上报一次）

## 常见陷阱

1. **跳过第一门**：直接使用未批准的上游制品
2. **跳过第三门**：build-verify 未通过就报告 TASK_DONE
3. **前后端串行**：L2 没有模拟并行，导致效率低
4. **忽视单元测试**：只写功能代码不写测试
5. **修复顺序错误**：先修 P3 再修 P0
6. **TASK_DONE 时 deliverable_allowed=false**：第三门未通过就交付

## 参考文档

- `references/collaboration-contract.md`：Fullstack 与 PM / Arch / Reviewer / QA 的工作流适配（L1/L2/L3）、输入输出契约与跨 profile 协作协议（接收校验/状态上报/完成通知/修复循环/异常上报/保留条款）。SOUL.md 瘦身为人格宪法后迁入此处。