---
name: reviewer-testcase-audit
description: "Reviewer 测试用例审查 Skill：审查 TEST_CASES/TEST_PLAN 的 PRD 覆盖率、用例粒度、正反边界完备性、测试数据合理性、追溯完整性。区别于 qa-test-planner（QA 自检 PRD 覆盖率），本 Skill 是 Reviewer 对 QA 产出的独立审查。输出 TESTCASE_AUDIT_REPORT + TESTCASE_AUDIT_APPROVAL_RECORD（含 deliverable_allowed）。PM 向 Reviewer 分发用例审查任务或用户说'审查测试用例'、'用例审查'时触发。"
version: 1.0.0
author: Hermes Agent (AI-Native Software Factory)
license: MIT
platforms: [linux, macos, windows]
metadata:
  hermes:
    tags: [reviewer, testcase-audit, test-case-review, coverage, traceability, deliverable-gate]
    related_skills: [reviewer-review-dispatcher, reviewer-requirements-audit, reviewer-arch-design-audit, qa-test-planner]
    artifact_type: TESTCASE_AUDIT_REPORT
    workflow_modes: [L2, L3]
---

# Reviewer Testcase Audit Skill (v1 — 交付门禁版)

## 核心原则

对 QA 产出的测试用例做独立审查，不替代 `qa-test-planner` 的自检。审查完成后必须生成 `TESTCASE_AUDIT_APPROVAL_RECORD`，含明确的 `deliverable_allowed` 判定。`deliverable_allowed=false` 时阻断测试用例进入测试执行阶段，PM 必须等待 QA 修正后重新提交。每个问题必须能追溯到具体用例 ID 和对应 PRD 功能。

## 与相关 Skill 的边界

| Skill | 审查对象 | 视角 | 执行方 |
|-------|---------|------|--------|
| **本 Skill** | TEST_CASES/TEST_PLAN | 用例质量独立审查 | Reviewer |
| `qa-test-planner`（内置自检） | TEST_CASES/TEST_PLAN | PRD 覆盖率自检 | QA 自己 |
| `reviewer-requirements-audit` | PRD | 需求质量 | Reviewer |

**关系**：QA 用 `qa-test-planner` 自检覆盖率通过后提交，Reviewer 用本 Skill 做独立二次审查。自检不替代独立审查。

## 关键机制

### 质量门禁判定

| 门禁 | 判定条件 | 阻断条件 |
|------|---------|---------|
| **COVERAGE_GATE** | PRD 功能覆盖率 = 100% | 存在未覆盖的 PRD 功能 → FAIL |
| **GRANULARITY_GATE** | 用例粒度合理（单用例单场景） | 存在 P0/P1 粒度问题（多场景混杂/步骤不可执行）→ FAIL |
| **BOUNDARY_GATE** | 关键功能有正/反/边界用例 | 关键功能缺边界或异常用例 → FAIL |
| **TRACEABILITY_GATE** | 每个用例可追溯到 PRD 功能 | 存在无追溯的用例 → FAIL |
| **DATA_GATE** | 测试数据合理且充分 | 测试数据不可执行或覆盖不足 → FAIL |

### 缺陷分级

| 级别 | 含义 | 示例 |
|------|------|------|
| **P0** | 阻断：用例无法执行或核心功能未覆盖 | PRD 核心功能无用例、用例步骤缺失无法执行 |
| **P1** | 严重：影响测试有效性 | 边界用例缺失、无追溯、粒度过粗 |
| **P2** | 一般：可优化 | 测试数据不够充分、冗余用例 |
| **P3** | 建议 | 用例命名优化、描述精简 |

### deliverable_allowed 判定

```
deliverable_allowed = true 条件：
  COVERAGE_GATE = PASS
  GRANULARITY_GATE = PASS
  BOUNDARY_GATE = PASS
  TRACEABILITY_GATE = PASS
  DATA_GATE = PASS

deliverable_allowed = false 条件（任一即 FAIL）：
  PRD 功能覆盖率 < 100%
  存在 P0/P1 粒度问题
  关键功能缺边界或异常用例
  存在无追溯的用例
  测试数据不可执行
```

## 触发条件

- PM 向 Reviewer 分发用例审查任务（`role: reviewer`, `task_type: testcase-audit`）
- QA 提交 TEST_CASES/TEST_PLAN 后请求审查
- 用户说"审查测试用例"、"用例审查"、"检查测试覆盖"
- `reviewer-review-dispatcher` 根据 `artifact_type=TEST_CASES/TEST_PLAN` 路由调用

## 输入

| 类型 | 描述 |
|------|------|
| **必需** | TEST_CASES（artifact_ref）、TEST_PLAN（artifact_ref） |
| **必需** | PRD（已 APPROVED，作为追溯基准） |
| **可选** | OpenAPI 规范、ARCH_SPEC（辅助判断接口/边界覆盖） |
| **固定约束** | 测试环境、项目版本 |

## 输出制品

| 制品类型 | 描述 |
|----------|------|
| **TESTCASE_AUDIT_REPORT** | 用例审查报告（含覆盖率、问题清单、追溯表、门禁结果） |
| **TESTCASE_AUDIT_APPROVAL_RECORD** | 审查批准记录（JSON，含 deliverable_allowed） |

### 落盘路径

所有制品除作为 Skill 返回值外，必须同步落盘到 `docs/04测试阶段/{NN}-审查报告/`：

| 制品 | 路径 |
|---|---|
| TESTCASE_AUDIT_REPORT | `docs/04测试阶段/{NN}-审查报告/{task_id}_testcase_audit_report.md` |
| TESTCASE_AUDIT_APPROVAL_RECORD | `docs/04测试阶段/{NN}-审查报告/{task_id}_testcase_audit_approval_record.json` |

> `{NN}` 为审查报告目录序号，**按 `docs/04测试阶段/` 同级目录动态确定**（取当前最大序号 +1，复用已有 `*-审查报告` 目录），详见 docs/AGENTS.md「审查报告子目录」。当前快照：`04-04`。

执行前按动态编号规则解析目录并创建：

```bash
mkdir -p "docs/04测试阶段/{NN}-审查报告"
```

## 执行步骤

### Step 1: 解析用例与 PRD

```python
test_cases = parse_test_cases(TEST_CASES)   # 提取用例 ID、步骤、预期、数据
prd_functions = extract_prd_functions(PRD)  # 复用 qa-test-planner 的提取逻辑
```

### Step 2: 覆盖率独立校验（COVERAGE_GATE）

> 复用 `qa-test-planner/scripts/prd_coverage_validator.py` 做交叉验证，确认 QA 自检结果可信。

1. 对每个 PRD 功能，确认存在至少一个对应用例
2. 识别未覆盖功能、过度覆盖功能（冗余）
3. 输出覆盖率矩阵

### Step 3: 粒度检查（GRANULARITY_GATE）

逐用例检查：

1. **单一场景**：一个用例只验证一个场景，不得混杂多个功能点
2. **步骤可执行**：测试步骤描述清晰、可复现，无歧义动作
3. **预期明确**：预期结果可观测、可判定 pass/fail

### Step 4: 边界与异常检查（BOUNDARY_GATE）

对 PRD 标记的关键功能：

1. **正常流**：有正向用例
2. **异常流**：有异常/失败用例
3. **边界值**：有边界值用例（空、极值、临界）
4. **并发/幂等**（如适用）：有并发或幂等用例

### Step 5: 追溯检查（TRACEABILITY_GATE）

1. 每个用例必须标注对应 PRD 功能 ID
2. 无追溯用例标记为 P1
3. 生成用例 ↔ PRD 追溯表

### Step 6: 测试数据检查（DATA_GATE）

1. **可执行性**：测试数据是否可在测试环境构造
2. **充分性**：是否覆盖等价类、边界值
3. **独立性**：用例间数据是否隔离，避免相互依赖

### Step 7: 生成审查报告

详见 `assets/testcase-audit-report-template.md`。

### Step 8: 生成批准记录

详见 `assets/testcase-audit-approval-record-template.json`。

### Step 9: 通知 PM

```
TESTCASE_AUDIT_DONE: {task_id}
  artifact: TEST_CASES@{hash}
  result: {APPROVED / REJECTED}
  deliverable_allowed: {true/false}
  coverage: {percent}%
  issue_summary:
    p0: {n}
    p1: {n}
    p2: {n}
    p3: {n}
  gates:
    COVERAGE_GATE: {PASS/FAIL}
    GRANULARITY_GATE: {PASS/FAIL}
    BOUNDARY_GATE: {PASS/FAIL}
    TRACEABILITY_GATE: {PASS/FAIL}
    DATA_GATE: {PASS/FAIL}
  next: {进入测试执行 / 等待 QA 修正后重新提交}
```

## 审查规则速览

| 规则 | 核心要求 | 违规级别 | 对应门禁 |
|------|---------|---------|---------|
| R1 功能覆盖 | 每个 PRD 功能至少一个用例 | P0 | COVERAGE_GATE |
| R2 粒度单一 | 单用例单场景 | P0/P1 | GRANULARITY_GATE |
| R3 步骤可执行 | 测试步骤清晰可复现 | P0/P1 | GRANULARITY_GATE |
| R4 预期可判定 | 预期结果可观测 | P0/P1 | GRANULARITY_GATE |
| R5 边界异常 | 关键功能有边界/异常用例 | P0/P1 | BOUNDARY_GATE |
| R6 追溯完整 | 每个用例标 PRD 功能 ID | P1 | TRACEABILITY_GATE |
| R7 数据可执行 | 测试数据可构造 | P1/P2 | DATA_GATE |
| R8 数据充分 | 覆盖等价类与边界值 | P2 | DATA_GATE |

## 验证步骤

1. [ ] TEST_CASES、TEST_PLAN、PRD 已正确解析
2. [ ] 覆盖率独立校验已执行（与 QA 自检交叉验证）
3. [ ] 粒度检查已执行（单一场景、步骤、预期）
4. [ ] 边界与异常检查已执行（正常/异常/边界）
5. [ ] 追溯检查已执行（用例 ↔ PRD 追溯表）
6. [ ] 测试数据检查已执行（可执行性、充分性、独立性）
7. [ ] 问题已正确分级为 P0/P1/P2/P3
8. [ ] 五个门禁判定逻辑正确
9. [ ] 审查报告包含覆盖率矩阵和追溯表
10. [ ] 批准记录的 deliverable_allowed 与门禁结果一致
11. [ ] 结果已通知 PM

## 常见陷阱

| 陷阱 | 描述 | 规避方法 |
|------|------|---------|
| 信任 QA 自检 | 直接采纳 QA 覆盖率不独立校验 | 必须独立跑 prd_coverage_validator 交叉验证 |
| 漏审边界 | 只看正常流用例 | 关键功能强制要求边界/异常用例 |
| 粒度过粗放过 | 一个用例验证多个功能 | 强制单用例单场景 |
| 追溯形同虚设 | 用例标了 PRD ID 但实际不对应 | 抽样核对追溯真实性 |

## TESTCASE_AUDIT_APPROVAL_RECORD 格式

```json
{
  "artifact": "TESTCASE_AUDIT_REPORT",
  "name": "{项目名称} 测试用例审查报告",
  "version": "v{version}",
  "hash": "{内容哈希}",
  "status": "{APPROVED / REJECTED}",
  "workflow_mode": "{L2 / L3}",
  "approvals": [
    {
      "role": "reviewer-testcase-audit",
      "result": "{APPROVED / REJECTED}",
      "timestamp": "{ISO8601}",
      "conditions": []
    }
  ],
  "issue_summary": {
    "total": {n},
    "p0": {n},
    "p1": {n},
    "p2": {n},
    "p3": {n}
  },
  "coverage": "{percent}%",
  "gates_passed": ["COVERAGE_GATE", "GRANULARITY_GATE", "BOUNDARY_GATE", "TRACEABILITY_GATE", "DATA_GATE"],
  "deliverable_allowed": {true/false},
  "test_cases_ref": "TEST_CASES@{hash}",
  "test_plan_ref": "TEST_PLAN@{hash}",
  "prd_ref": "PRD@{hash}",
  "timestamp": "{ISO8601}"
}
```

## 与其他 Skill 的关系

| Skill | 关系 |
|-------|------|
| `reviewer-review-dispatcher` | 本 Skill 的路由入口，按 artifact_type=TEST_CASES 分发到此 |
| `qa-test-planner` | 上游：QA 用此 skill 产出并自检用例，本 Skill 做独立二次审查 |
| `qa-test-executor` | 下游：本 Skill 通过后，用例才可进入测试执行 |
| `reviewer-requirements-audit` | 前置：PRD 审查通过后，用例审查的追溯基准才可靠 |
