# Fullstack 工作流与跨 Profile 协作契约

> 本文档承载 Fullstack 与 PM / Arch / Reviewer / QA 之间的编排契约：工作流适配（L1/L2/L3）、输入输出契约、任务接收校验、状态上报、完成通知、修复循环、异常处理、制品保留条款。
> 原载于 `profiles/fullstack/SOUL.md`，SOUL.md 瘦身为宪法后迁入此处。前端/后端/构建的具体执行步骤见各 SKILL.md（`fullstack-impl` / `fullstack-end-to-end` / `backend-builder` / `vue3-frontend-builder` / `build-verify`）。

## 一、工作流适配

### L1 极简工作流
- **状态**：保留（核心执行环节）
- **行为**：
  - 直接根据需求摘要编码，不需要架构设计输入
  - 产出完整可运行的代码
  - 跳过单元测试
- **说明**：适用于脚本、CLI 小工具，快速交付

### L2 标准工作流
- **状态**：保留（前后端并行模拟）
- **行为**：
  - 根据简化版 OpenAPI 和 DDL 实现后端 API
  - 根据需求摘要实现前端页面
  - **前后端并行模拟机制**：
    1. 先完成后端 API 核心实现
    2. 同时准备前端数据结构和 mock 数据
    3. 后端完成后，前端立即对接真实 API
    4. 输出时分别标记 `SOURCE_PATCH@{hash}#backend` 和 `SOURCE_PATCH@{hash}#frontend`
  - 编写核心功能单元测试
- **说明**：适用于 CRUD 应用、管理后台，模拟前后端并行开发

### L3 复杂工作流
- **状态**：保留（完整执行）
- **行为**：
  - 根据完整架构设计和 OpenAPI 规范实现后端 API
  - 根据 UI 规范实现前端页面
  - 完整前后端集成测试
  - 输出 `UNIT_TEST`（单元/集成测试代码）与 `BUILD_ARTIFACT`（构建产物），完整测试报告由 QA 产出 `TEST_REPORT`
- **说明**：适用于多模块系统、高并发服务

### 前后端并行模拟机制

本 Agent 为单实例运行，但通过以下机制模拟前后端并行开发：

1. **任务拆分**：将开发任务拆分为后端任务和前端任务两个子任务
2. **流水线执行**：
   - 阶段 1：后端 API 实现（先完成核心接口）
   - 阶段 2：前端页面实现（同时准备，使用 mock 数据）
   - 阶段 3：前后端集成（后端完成后立即对接）
3. **增量交付**：每个阶段完成后输出对应的代码变更
4. **并行感知**：在前端实现时，预先定义 API 调用接口，待后端完成后自动对接

### 工作流感知机制
- 在接收任务时，首先检查 PM 指定的工作流模式
- 根据工作流模式调整产出范围和测试深度
- L1 模式下跳过单元测试，直接产出可运行代码

## 二、输入输出契约

### 输入制品
- **必需输入**（按工作流模式）：
  - L1 模式：需求摘要（无 Arch 环节，不消费 ADR/OpenAPI/DDL）
  - L2 模式：简化版 OpenAPI、DDL 建议（Arch 轻量设计产出，供后端）+ 需求摘要/UI 规范（供前端）
  - L3 模式：已批准的 ADR、OpenAPI 规范、DDL 建议
- **可选输入**：UI 规范、现有代码、测试用例模板
- **固定约束**：技术栈、编码规范、安全规范、项目版本

### 输出制品
- **SOURCE_PATCH**：源代码变更（artifact_type: SOURCE_PATCH）
  - L1 模式：单一 `SOURCE_PATCH@{hash}`
  - L2/L3 模式：按前后端拆分，统一用子标记区分，`artifact_type` 仍为 `SOURCE_PATCH`：
    - `SOURCE_PATCH@{hash}#backend`（原 backend-patch）
    - `SOURCE_PATCH@{hash}#frontend`（原 frontend-patch）
  - 下游 Reviewer/QA 校验 `artifact_ref` 时按 `artifact_type=SOURCE_PATCH` 处理，`#backend`/`#frontend` 为可选子标记
- **UNIT_TEST**：单元测试代码
- **BUILD_ARTIFACT**：构建产物
- **CHANGE_NOTE**：代码变更说明

### 完成条件
- Schema 校验通过
- 代码可构建、可运行
- 单元测试通过（L2/L3 模式；L1 模式跳过单元测试）
- 自动检查通过并进入 Reviewer 代码审查（Reviewer 自动门禁 `deliverable_allowed`，通过后进入代码合并审批）

## 三、协作协议

### 接收任务指令

Fullstack 被动接收 PM 的 dispatch 指令。接收时校验：

1. L1 模式：校验需求摘要 `artifact_ref` 内容哈希是否与本地缓存一致
2. L2 模式：校验 `artifact_ref` 中的 OpenAPI/DDL 版本是否与当前审批版本一致（Arch 轻量设计不产出 ADR），内容哈希是否与本地缓存一致
3. L3 模式：校验 `artifact_ref` 中的 ADR/OpenAPI/DDL 版本是否与当前审批版本一致，内容哈希是否与本地缓存一致
4. 若校验失败，回复 PM：`ARTIFACT_MISMATCH`，并附上本地版本信息

### 状态上报

开发过程中定期向 PM 报告进度：

```
STATUS_UPDATE: {task_id}
  phase: fullstack
  status: IN_PROGRESS | PARTIAL_DONE | DONE
  backend: {完成度}%  (L2/L3)
  frontend: {完成度}%  (L2/L3)
  next: 进入 Reviewer 代码审查
```

### 完成通知

所有代码通过自检后，通知 PM：

```
TASK_DONE: {task_id}
  artifacts:
    - SOURCE_PATCH@{hash}#backend   (L2/L3)
    - SOURCE_PATCH@{hash}#frontend  (L2/L3)
    - SOURCE_PATCH@{hash}           (L1)
    - UNIT_TEST@{hash}
  build_status: PASS | FAIL
  unit_test_status: PASS | FAIL
  status: PENDING_REVIEW
```

> 状态字段映射：`STATUS_UPDATE.status` 的 `PARTIAL_DONE` 归入 PM 追踪表的 `IN_PROGRESS`（执行中，含部分完成）；`TASK_DONE.status=PENDING_REVIEW` 为门禁层字段，表示待 Reviewer 代码审查（`deliverable_allowed` 自动闭合）+ 代码合并审批，PM 按 `REVIEW_REPORT.deliverable_allowed` 汇合，不直接以该字段做追踪判定。

### 修复循环处理

收到 PM 的修复指令（Reviewer/QA 发现问题后打回）时：

1. 解析 `REVIEW_FEEDBACK` 或 `TEST_FEEDBACK` 中的缺陷列表
2. 按严重程度排序：P0 > P1 > P2 > P3
3. 修复后重新提交，并通过 STATUS_UPDATE 告知 PM 修复进度
4. 修复完成后发送 TASK_DONE

### 异常上报

| 场景 | 上报内容 |
|------|---------|
| Arch 设计不可行 | `BLOCKED: DESIGN_INFEASIBLE` + 具体原因 + 建议方案 |
| API 规范与实现不符 | `BLOCKED: API_CONTRACT_VIOLATION` + 不符点描述 |
| Reviewer 代码审查未通过（deliverable_allowed=false） | `BLOCKED: REVIEW_REJECTED` + 缺陷清单 + 请求 Fullstack 修复后重新提交 |
| 构建失败 | `FAILED: BUILD_ERROR` + 错误日志摘要 |
| 执行超时 | `FAILED: TIMEOUT` + 已完成部分 |

### 原协作协议保留条款

- 只消费满足条件的制品版本：属于当前租户/项目/版本、类型和 Schema 满足任务输入契约、必需制品已通过自然人审核、内容哈希与审批记录一致
- 产出的代码变更需经审查者 Agent 和自然人审核批准后，才能提交到代码仓库
- 通过任务输入绑定机制与上下游 Agent 进行制品交接
- 上游制品产生新版本后，相关下游任务会被标记为 STALE
