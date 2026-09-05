# PM 跨 Profile 协作契约

> 本文档承载 PM 与下游 Agent（Arch/Fullstack/QA/Reviewer）之间的编排契约：任务分发、状态追踪、汇合门禁、修复任务分发、人工审核节点、异常处理、制品传递。
> 原载于 `profiles/pm/SOUL.md`「协作协议」节，SOUL.md 瘦身为宪法后迁入此处。派发执行机制详见本 Skill 的 `SKILL.md`，复杂度评估与 L1/L2/L3 工作流见 `pm-complexity-assessment` Skill。

## 任务调度机制

PM 是数字员工团队的**唯一调度入口**，负责触发、协调和汇合所有下游 Agent 的工作。

### 1. 分发任务（dispatch）

完成复杂度评估并确认工作流后，PM 按以下顺序触发下游 Agent：

**L1 极简模式**：
```
PM → Fullstack（直接分发）→ Reviewer（必须触发，报告必须产出）
```
L1 模式 Reviewer 执行快速代码检查，输出简化 REVIEW_REPORT，禁止跳过。L1 分发 Fullstack 时 `artifact_ref` 指向需求摘要（无 Arch 环节，不携带 ADR/OpenAPI/DDL）。

**L2 标准模式**：
```
PM → Arch（轻量设计）→ Fullstack（并行分发前端+后端）→ [Reviewer, QA 简化测试]（并行）
```
L2 模式 Reviewer 执行标准代码审查，输出标准 REVIEW_REPORT，禁止跳过；QA 执行简化测试，输出简化 TEST_REPORT，可与 Reviewer 并行或延后。

**L3 复杂模式**：
```
PM → Arch → Fullstack → [Reviewer, QA]（并行）
```

每次分发须携带：
- `task_id`：本次任务的唯一标识（格式：`{project}-{phase}-{seq}`，如 `order-sys-arch-001`，**不含 `/`、空格等路径非法字符**，以保证下游 Reviewer 落盘 `{task_id}_*.md` 时路径合法）
- `artifact_ref`：上游制品引用（含版本哈希），下游 Agent 校验后消费
- `role`：目标 Agent 角色（arch / fullstack / reviewer / qa）
- `delivery`：结果回传地址（另一个 Agent 的 task_id 或自然人审核队列）
- `deadline`：期望完成时间（相对时间，如 `2h`、`30m`）

**Reviewer 入口 skill 对齐（强制）**：向 Reviewer 分发审查任务时，PM 只需按 `artifact_type`（PRD / ARCH_SPEC / OPENAPI / DDL / TEST_CASES / SOURCE_PATCH）携带 `artifact_ref`，由 Reviewer 侧 `reviewer-review-dispatcher` 统一路由到对应专项审查 skill。PM 无需指定具体专项 skill，但应在分发信息中明确 `artifact_type`，确保 dispatcher 路由准确。

### 2. 追踪状态（track）

PM 在分发后持续追踪下游任务状态：

| 状态 | 含义 | PM 动作 |
|------|------|---------|
| DISPATCHED | 已分发，等待启动 | 等待 |
| IN_PROGRESS | 执行中 | 定期查询进度，识别阻塞 |
| STALE | 上游制品更新，下游失效 | 自动重新分发或取消 |
| DONE | 完成，制品已提交 | 汇合结果 |
| FAILED | 执行失败 | 诊断原因，决定重试或降级 |
| BLOCKED | 等待人工审核 | 通知自然人介入 |

追踪频率：每 `5min` 查询一次状态，或收到下游通知时立即处理。

> 下游状态上报字段映射：下游 `STATUS_UPDATE.status` 的 `PARTIAL_DONE` 归入上表 `IN_PROGRESS`（执行中，含部分完成）；完成通知 `TASK_DONE.status` 为门禁层字段（Fullstack=`PENDING_REVIEW`、Reviewer=`AUTO_CLOSED`/`PENDING_HUMAN_REVIEW`），PM 按 `artifact_type` + `deliverable_allowed` 汇合，不直接以该字段做追踪判定。

### 3. 汇合结果（join）

下游 Agent 完成后，PM 负责汇合：

**L3 并行汇合**（Reviewer + QA 同时执行）：
```
Fullstack 输出 SOURCE_PATCH
   ├── 分发 → Reviewer（审查代码）
   └── 分发 → QA（执行测试）

Reviewer → REVIEW_REPORT ─┐
                          ├→ PM 汇合 → 质量裁定
QA → TEST_REPORT ─────────┘

质量裁定结果：
  - 通过 → 交付
  - 不通过 → 打回 Fullstack 修复 → 重新触发 Reviewer + QA
```

**汇合门禁规则**：
- Reviewer 审查是所有工作流模式的**必选项**，PM 必须等待 REVIEW_REPORT 完成才能进行质量裁定（制品名统一为 `REVIEW_REPORT`，不再使用 `REVIEWER_REPORT`/`REVIEWER_REVIEW` 等别名）
- **按 artifact_type 分发审查任务的汇合门禁**：PM 根据 dispatch 时携带的 `artifact_type` 等待对应审查报告，由 Reviewer 侧 `reviewer-review-dispatcher` 路由产出：
  - `PRD` / `USER_STORY` → 等待 `REQUIREMENTS_AUDIT_REPORT`（含 `deliverable_allowed` 字段）
  - `ARCH_SPEC` / `OPENAPI` / `DDL` → 等待 `ARCH_DESIGN_AUDIT_REPORT`（架构设计审计，含 `deliverable_allowed` 字段）
  - `TEST_CASES` / `TEST_PLAN` → 等待 `TESTCASE_AUDIT_REPORT`（含 `deliverable_allowed` 字段）
  - `SOURCE_PATCH` → 等待 `REVIEW_REPORT`（代码审查）
- **deliverable_allowed 门禁**：四类审查报告的 `deliverable_allowed=true` 才允许进入下游阶段——`REQUIREMENTS_AUDIT_REPORT`（PRD→Arch）、`ARCH_DESIGN_AUDIT_REPORT`（设计→开发）、`TESTCASE_AUDIT_REPORT`（TEST_CASES→测试执行）、`REVIEW_REPORT`（代码→交付）；`deliverable_allowed=false` 打回对应角色（PM/Arch/QA/Fullstack）修正后重新提交。PRD 类制品需依次通过两道门：先 Reviewer 需求审查（`deliverable_allowed=true`），再产品负责人 PRD 审批（`APPROVED`），方可进入 Arch
- **修复任务分发**：Reviewer 提交审查任务结果后，PM 按**审查任务类型（`artifact_type`）+ 审查结果（`deliverable_allowed`）**分发修复任务给对应 profile 执行，而非一律打回 Fullstack：

  | 审查任务类型（artifact_type） | 审查报告 | `deliverable_allowed=false` 时 | 修复执行 profile |
  |---|---|---|---|
  | `PRD` / `USER_STORY` | REQUIREMENTS_AUDIT_REPORT | 打回需求修正 | PM |
  | `ARCH_SPEC` / `OPENAPI` / `DDL` | ARCH_DESIGN_AUDIT_REPORT | 打回设计修正 | Arch |
  | `TEST_CASES` / `TEST_PLAN` | TESTCASE_AUDIT_REPORT | 打回用例修正 | QA |
  | `SOURCE_PATCH` | REVIEW_REPORT | 打回代码修正 | Fullstack |

  `deliverable_allowed=true` 时无需修复，直接按上述门禁进入下游阶段。
- L1 模式：QA 全量测试**跳过**（不产出 TEST_REPORT），PM 仅需等待 REVIEW_REPORT（简化版）即可裁定
- L2 模式：必须收到 REVIEW_REPORT，QA 的简化版 TEST_REPORT 可与 Reviewer 并行或延后
- L3 模式：必须同时收到 REVIEW_REPORT 和 TEST_REPORT 才能进行质量裁定
- 任何一方 FAILED 超过 2 次，自动升级为人工介入
- 裁定通过条件：Reviewer 无 P0/P1 缺陷 且 QA 通过率 ≥ 95%

### 4. 人工审核节点

以下节点中，自然人业务审批（PRD 审批 / Arch 审批 / 代码合并审批）须经自然人审核才能继续；需求审查 / 测试用例审查为 Reviewer 自动门禁（`deliverable_allowed`），无需自然人审核，由 PM 按门禁结果推进：

| 节点 | 审核内容 | 审核者 |
|------|---------|--------|
| PRD 审批 | 需求是否完整、可行 | 产品负责人 |
| 需求审查 | REQUIREMENTS_AUDIT_REPORT（deliverable_allowed）| Reviewer 自动门禁，P0 打回 PM |
| Arch 审批 | 架构方案、API 规范 | 架构师/技术负责人 |
| 测试用例审查 | TESTCASE_AUDIT_REPORT（deliverable_allowed）| Reviewer 自动门禁，P0/P1 打回 QA |
| 代码合并审批 | REVIEW_REPORT + QA TEST_REPORT | 技术负责人 |

审核结果：`APPROVED` → 继续流程；`REJECTED` → 打回重做。

### 5. 异常处理

| 异常场景 | 处理策略 |
|---------|---------|
| `.hermes/agents.json` 缺失 | 立即告知用户「项目缺少 agents.json，需先由 workspace 初始化生成（数字员工/项目创建时同步维护）」，停止派发，不静默回退、不猜测 board |
| Arch 执行超时 | PM 生成一份「默认 API 规范」作为降级替代依据（标记 `degraded=true`），随派发一并传递给 Fullstack 与 Reviewer，避免 Reviewer 因 `INSUFFICIENT_BASIS` 卡死（降级模式） |
| Fullstack 发现 Arch 设计不可行 | Fullstack 暂停，通知 PM，PM 请求 Arch 补充设计 |
| Reviewer 发现 P0 缺陷 | 代码冻结，PM 通知 Fullstack 立即修复 |
| QA 发现 P0 缺陷 | 代码冻结，PM 通知 Fullstack 立即修复 |
| Reviewer 或 QA 连续失败 2 次 | 升级人工介入，PM 输出诊断报告 |
| 自然人审核超时（>2h） | PM 发送提醒，每 30min 一次，最多 3 次 |

### 6. 制品传递规则

- 所有制品传递必须携带 `artifact_ref`（内容哈希 + 版本号）
- 下游 Agent 必须验证 `artifact_ref` 与本地缓存一致后才能使用
- 新版本制品产生后，PM 自动标记依赖该旧版本的下游任务为 STALE
- STALE 任务必须使用新版制品重新执行

## 原协作协议保留条款

- 产出的 PRD 和原型需经人工审核批准后，才能作为架构 Agent 的输入
- 通过任务输入绑定机制与下游 Agent 进行制品交接
- 上游制品产生新版本后，相关下游任务会被标记为 STALE
- 根据工作流模板决定是否创建 Arch/QA/Reviewer 任务
