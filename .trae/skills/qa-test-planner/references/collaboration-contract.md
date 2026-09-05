# QA 工作流与跨 Profile 协作契约

> 本文档承载 QA 与 PM / Fullstack / Reviewer 之间的编排契约：工作流适配（L1/L2/L3）、输入输出契约、任务接收校验、状态上报、完成通知、缺陷反馈、异常处理、制品保留条款。
> 原载于 `profiles/qa/SOUL.md`，SOUL.md 瘦身为宪法后迁入此处。测试计划/执行/缺陷管理的具体执行步骤见各 SKILL.md（`qa-test-planner` / `qa-test-executor` / `qa-bug-tracker`）。

## 一、工作流适配

### L1 极简工作流
- **状态**：跳过
- **行为**：不执行测试，不产出测试报告
- **说明**：适用于脚本、CLI 小工具，快速交付，由用户自行验证

### L2 标准工作流
- **状态**：简化执行（核心功能测试）
- **行为**：
  - 设计核心功能测试用例
  - 执行基础功能测试
  - 输出简化测试报告（TEST_REPORT）
  - 跳过性能测试和回归测试
- **说明**：适用于 CRUD 应用、管理后台，验证核心功能正确性

### L3 复杂工作流
- **状态**：完整执行
- **行为**：
  - 制定完整测试计划（TEST_PLAN）
  - 设计全面测试用例集（TEST_CASES）
  - 执行单元测试、集成测试、端到端测试
  - 执行性能测试
  - 输出完整测试报告（TEST_REPORT）和缺陷清单（BUG_LIST）
  - 输出质量评估报告（QUALITY_ASSESSMENT）
- **说明**：适用于多模块系统、高并发服务、安全敏感系统

### L3 E2E 路由（Web 应用）

当测试对象为 Web 应用且工作流模式为 L3 时：

1. `qa-test-planner` 产出 TEST_PLAN（含 PRD 追溯表 + 覆盖率验证）
2. 触发 `playwright-test-planner`：浏览器探索页面 → 输出 Web E2E 测试计划
3. 触发 `playwright-test-generator`：基于计划项生成 Playwright `.spec.ts`
4. `qa-test-executor` 执行全部测试（单元/集成/E2E）→ 输出 TEST_REPORT + 门禁判定
5. E2E 失败时触发 `playwright-test-healer`：调试 + 自愈，循环至通过或达上限
6. 未自愈缺陷转 `qa-bug-tracker` 登记，不回滚门禁判定

> 边界：L1/L2 不触发本路由；playwright 三 skill 失败不阻断 `qa-test-executor` 的门禁判定。

### 工作流感知机制
- 在接收任务时，首先检查 PM 指定的工作流模式
- 根据工作流模式调整测试范围和深度
- 被跳过（L1）时，向 PM 发送任务跳过确认，不等待输入

## 二、输入输出契约

### 输入制品
- **必需输入**（按工作流模式）：
  - L1 模式：跳过（不消费任何制品，直接 `WORKFLOW_SKIP`）
  - L2 模式：需求摘要 + 简化版 OpenAPI + 源代码变更（`SOURCE_PATCH`）
  - L3 模式：已批准的 PRD、OpenAPI 规范、源代码变更（`SOURCE_PATCH`）
- **可选输入**：架构设计、UI 规范、测试用例模板、既有 `TEST_CASES`（回归/迭代场景）
- **固定约束**：测试环境、安全规范、项目版本

### 输出制品
- **TEST_PLAN**：测试计划文档（artifact_type: TEST_PLAN）
- **TEST_CASES**：测试用例集（artifact_type: TEST_CASES）
- **TEST_REPORT**：测试执行报告（artifact_type: TEST_REPORT）
- **BUG_LIST**：缺陷清单（artifact_type: BUG_LIST）
- **QUALITY_ASSESSMENT**：质量评估报告（artifact_type: QUALITY_ASSESSMENT）

### 完成条件
- Schema 校验通过
- 测试覆盖率达到要求（L2/L3 模式；L1 模式跳过测试）
- 自动检查通过并进入 Reviewer 用例审查（`TEST_CASES`/`TEST_PLAN` 由 `TESTCASE_AUDIT_REPORT` 的 `deliverable_allowed` 门禁自动闭合，通过后进入测试执行；`TEST_REPORT` 为执行报告，交付 PM 汇合裁定）

## 三、协作协议

### 接收任务指令（并行触发）

Reviewer 和 QA 由 PM **同时触发**，不串行等待。QA 接收指令时校验：

1. L1 模式：不校验，立即回复 PM：`WORKFLOW_SKIP`
2. 解析 `artifact_ref` 的 `artifact_type`（QA 主要消费 `SOURCE_PATCH` / `OPENAPI`；回归/迭代场景消费既有 `TEST_CASES`）
3. 按 `artifact_type` 分支校验：
   - `SOURCE_PATCH`：校验源代码变更版本是否与当前一致
   - `OPENAPI`：校验接口规范版本是否与 Fullstack 消费版本一致
   - `TEST_CASES`：校验既有用例版本是否与本次测试计划一致（回归/迭代场景）
4. 若任一校验失败，回复 PM：`ARTIFACT_MISMATCH`，并附上本地版本信息

### 状态上报

测试执行过程中定期向 PM 报告：

```
STATUS_UPDATE: {task_id}
  phase: qa
  status: IN_PROGRESS | PARTIAL_DONE | DONE
  tests_passed: {n}/{total}
  coverage: {percent}%
  next: 进入 Reviewer 用例审查 / 测试执行
```

### 完成通知

测试完成后通知 PM：

```
TASK_DONE: {task_id}
  artifacts:
    - TEST_PLAN@{hash}
    - TEST_CASES@{hash}
    - TEST_REPORT@{hash}
    - BUG_LIST@{hash}
  summary:
    - total: {count}
    - passed: {count}
    - failed: {count}
    - blocked: {count}
  pass_rate: {percent}%
  quality_assessment: {PASS | FAIL}
  status: PENDING_REVIEW
```

> 状态字段映射：`STATUS_UPDATE.status` 的 `PARTIAL_DONE` 归入 PM 追踪表的 `IN_PROGRESS`（执行中，含部分完成）；`TASK_DONE.status=PENDING_REVIEW` 为门禁层字段，表示 `TEST_CASES`/`TEST_PLAN` 待 Reviewer 用例审查（`TESTCASE_AUDIT_REPORT.deliverable_allowed` 自动闭合），`TEST_REPORT` 直接交付 PM 汇合裁定，PM 按 `TESTCASE_AUDIT_REPORT.deliverable_allowed` + QA 通过率汇合，不直接以该字段做追踪判定。

### 输出反馈给 Fullstack

测试完成后，将缺陷列表写入 `docs/04测试阶段/04-03测试报告/{task_id}_feedback.md`，供 PM 触发修复循环：

```markdown
# Test Feedback - {task_id}

## P0 Bugs (Must Fix)
1. [test_id] description - {severity}

## P1 Bugs
1. [test_id] description

## P2/P3 Issues
...
```

### 异常上报

| 场景 | 上报内容 |
|------|---------|
| 上游制品不可用（PRD/OpenAPI/源代码版本不一致或缺失） | `BLOCKED: ARTIFACT_MISMATCH` + 本地版本信息 + 缺失制品清单 |
| 测试环境不可用 | `BLOCKED: ENV_UNAVAILABLE` + 环境缺失项 + 阻塞用例范围 |
| 发现 P0 阻断缺陷 | `BLOCKED: P0_DEFECT` + 缺陷清单（test_id） + 请求 Fullstack 修复 |
| 连续修复 2 次仍有 P0 | `FAILED: REPAIR_EXHAUSTED` + 历史缺陷摘要 + 升级人工介入 |
| 执行超时 | `FAILED: TIMEOUT` + 已执行用例进度 |

### 原协作协议保留条款

- 只消费满足条件的制品版本：属于当前租户/项目/版本、类型和 Schema 满足任务输入契约、必需制品已通过自然人审核、内容哈希与审批记录一致
- `TEST_CASES`/`TEST_PLAN` 经 Reviewer 用例审查门禁（`TESTCASE_AUDIT_REPORT.deliverable_allowed`）自动闭合，通过后进入测试执行；`TEST_REPORT` 交付 PM 汇合裁定
- 通过任务输入绑定机制与上下游 Agent 进行制品交接
- 上游制品产生新版本后，相关下游任务会被标记为 STALE
