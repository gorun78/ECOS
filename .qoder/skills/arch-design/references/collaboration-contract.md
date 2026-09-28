# Arch 工作流与跨 Profile 协作契约

> 本文档承载 Arch 与 PM / Reviewer / Fullstack 之间的编排契约：工作流适配（L1/L2/L3）、输入输出契约、任务接收校验、状态上报、完成通知、异常处理、制品保留条款。
> 原载于 `profiles/arch/SOUL.md`，SOUL.md 瘦身为宪法后迁入此处。架构/接口/数据库/决策记录的具体设计步骤见各 SKILL.md（`arch-design` / `api-design` / `db-design` / `adr-writer` / `design-software-architecture` / `system-design`）。

## 一、工作流适配

### L1 极简工作流
- **状态**：跳过
- **行为**：不执行架构设计，不产出任何架构制品
- **说明**：适用于脚本、CLI 小工具等简单任务，直接由开发 Agent 根据需求摘要编码

### L2 标准工作流
- **状态**：简化执行（架构轻量设计）
- **行为**：
  - 输出简化版 API 规范（OpenAPI）
  - 输出核心数据模型（DDL）
  - 跳过完整架构方案文档（ARCH_SPEC）
  - 跳过架构决策记录（ADR）
- **说明**：适用于 CRUD 应用、管理后台，快速出 API 规范供开发使用

### L3 复杂工作流
- **状态**：完整执行
- **行为**：
  - 输出完整架构方案（ARCH_SPEC）
  - 输出架构决策记录（ADR）
  - 输出完整 OpenAPI 规范
  - 输出完整数据库设计（DDL）
- **说明**：适用于多模块系统、高并发服务、安全敏感系统

### 工作流感知机制
- 在接收任务时，首先检查 PM 指定的工作流模式
- 根据工作流模式调整产出范围和深度
- 被跳过（L1）时，向 PM 发送任务跳过确认，不等待输入

## 二、输入输出契约

### 输入制品
- **必需输入**（按工作流模式）：
  - L1 模式：跳过（不消费任何制品，直接 `WORKFLOW_SKIP`）
  - L2 模式：需求摘要（PM 提供，无完整 PRD）
  - L3 模式：已批准的 PRD、产品原型、验收标准
- **可选输入**：UI 规范、现有系统架构、历史 ADR、代码索引
- **固定约束**：技术栈、安全规范、租户隔离规则、项目版本

### 输出制品
- **ADR**：架构决策记录（artifact_type: ADR）
- **OPENAPI**：接口规范（artifact_type: OPENAPI）
- **DDL**：数据库设计建议（artifact_type: DDL）
- **ARCH_SPEC**：架构方案、模块边界、接口清单、风险清单（artifact_type: ARCH_SPEC）

### 完成条件
- Schema 校验通过
- 引用完整
- 自动检查通过并进入 Reviewer 架构设计审计（`ARCH_DESIGN_AUDIT_REPORT` 的 `deliverable_allowed` 门禁，通过后进入 Arch 审批）

> 架构制品需依次通过两道门：先 Reviewer 架构设计审计（`deliverable_allowed=true`），再 Arch 审批（自然人 `APPROVED`），方可进入开发阶段。

## 三、协作协议

### 接收任务指令

Arch 被动接收 PM 的 dispatch 指令，不主动拉取任务。接收时校验：

1. L1 模式：不校验，立即回复 PM：`WORKFLOW_SKIP`
2. L2 模式：校验需求摘要 `artifact_ref` 内容哈希是否与本地缓存一致
3. L3 模式：校验 `artifact_ref` 中的 PRD/原型版本是否与当前审批版本一致，内容哈希是否与本地缓存一致
4. 若校验失败，回复 PM：`ARTIFACT_MISMATCH`，并附上本地版本信息

### 工作流感知

- **L1**：收到 L1 分发时，立即回复 PM：`WORKFLOW_SKIP`，不执行架构设计
- **L2**：仅输出 OpenAPI + 核心 DDL，跳过 ARCH_SPEC 和 ADR
- **L3**：完整输出 ARCH_SPEC + ADR + OpenAPI + DDL

### 状态上报

每完成一个输出制品（ADR/OpenAPI/DDL/ARCH_SPEC），立即向 PM 报告进度：

```
STATUS_UPDATE: {task_id}
  phase: arch
  status: IN_PROGRESS | PARTIAL_DONE | DONE
  artifact: {artifact_type}@{version_hash}
  next: 等待人工审核
```

### 完成通知

所有制品通过 Schema 校验和自检后，通知 PM：

```
TASK_DONE: {task_id}
  artifacts:
    - ADR@{hash}
    - OPENAPI@{hash}
    - DDL@{hash}
    - ARCH_SPEC@{hash}  (L3 only)
  status: PENDING_REVIEW
```

> 状态字段映射：`STATUS_UPDATE.status` 的 `PARTIAL_DONE` 归入 PM 追踪表的 `IN_PROGRESS`（执行中，含部分完成）；`TASK_DONE.status=PENDING_REVIEW` 为门禁层字段，表示待 Reviewer 架构设计审计 + Arch 审批闭合，PM 按 `ARCH_DESIGN_AUDIT_REPORT.deliverable_allowed` + Arch 审批结果汇合，不直接以该字段做追踪判定。

### 异常上报

| 场景 | 上报内容 |
|------|---------|
| 上游制品不可用（版本不一致/缺失） | `BLOCKED: ARTIFACT_MISMATCH` + 本地版本信息 + 缺失制品清单 |
| PRD 设计存在矛盾 | `BLOCKED: PRD_CONTRADICTION` + 矛盾点描述 |
| 技术选型超出约束 | `BLOCKED: TECH_CONSTRAINT_VIOLATION` + 可选方案 |
| 架构设计审计未通过（deliverable_allowed=false） | `BLOCKED: ARCH_AUDIT_REJECTED` + 缺陷清单 + 请求 Arch 修正后重新提交 |
| 执行超时 | `FAILED: TIMEOUT` + 已完成部分 + 待完成部分 |

### 原协作协议保留条款

- 只消费满足条件的制品版本：属于当前租户/项目/版本、类型和 Schema 满足任务输入契约、必需制品已通过自然人审核、内容哈希与审批记录一致
- 产出的 ADR、OpenAPI 和 DDL 需依次经 Reviewer 架构设计审计（`deliverable_allowed=true`）与自然人 Arch 审批（`APPROVED`）后，才能作为开发 Agent 的输入
- 通过任务输入绑定机制与下游 Agent 进行制品交接
- 上游制品产生新版本后，相关下游任务会被标记为 STALE
