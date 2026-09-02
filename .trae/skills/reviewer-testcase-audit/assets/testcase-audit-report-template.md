# 测试用例审查报告（TESTCASE_AUDIT_REPORT）

> 模板：审查 TEST_CASES/TEST_PLAN 质量，由 `reviewer-testcase-audit` 产出。
> 落盘路径：`docs/04测试阶段/{NN}-审查报告/{task_id}_testcase_audit_report.md`，批准记录同目录 `_testcase_audit_approval_record.json`。`{NN}` 按 `docs/04测试阶段/` 同级目录动态确定（详见 docs/AGENTS.md「审查报告子目录」），当前快照 `04-04`。

## 1. 审查元信息

| 项 | 值 |
|---|---|
| 项目 | {{项目名称}} |
| TEST_CASES 版本 | {{version}}@{{hash}} |
| TEST_PLAN 版本 | {{version}}@{{hash}} |
| PRD 基准 | PRD@{{hash}} |
| 工作流模式 | {{L2 / L3}} |
| 审查时间 | {{ISO8601}} |
| 审查结论 | {{APPROVED / REJECTED}} |
| deliverable_allowed | {{true / false}} |

## 2. 门禁结果

| 门禁 | 结果 | 说明 |
|---|---|---|
| COVERAGE_GATE | {{PASS/FAIL}} | PRD 功能覆盖率 |
| GRANULARITY_GATE | {{PASS/FAIL}} | 用例粒度 |
| BOUNDARY_GATE | {{PASS/FAIL}} | 边界与异常 |
| TRACEABILITY_GATE | {{PASS/FAIL}} | 追溯完整 |
| DATA_GATE | {{PASS/FAIL}} | 测试数据 |

## 3. PRD 覆盖率矩阵

| PRD 功能 ID | 功能描述 | 对应用例 ID | 覆盖状态 |
|---|---|---|---|
| F-001 | {{描述}} | TC-001, TC-002 | 已覆盖 |
| F-002 | {{描述}} | - | **未覆盖** |

- **PRD 功能覆盖率**：{{已覆盖功能数}} / {{总功能数}} = {{percent}}%

## 4. 问题清单

| 编号 | 级别 | 用例 ID | PRD 功能 ID | 问题描述 | 违反规则 | 建议修正 |
|---|---|---|---|---|---|---|
| I-001 | P1 | TC-xxx | F-xxx | {{描述}} | R5/R6/... | {{建议}} |

## 5. 问题统计

| 级别 | 数量 |
|---|---|
| P0（阻断） | {{n}} |
| P1（严重） | {{n}} |
| P2（一般） | {{n}} |
| P3（建议） | {{n}} |
| **合计** | {{n}} |

## 6. 用例 ↔ PRD 追溯表

| 用例 ID | 用例名称 | PRD 功能 ID | 追溯状态 |
|---|---|---|---|
| TC-001 | {{名称}} | F-001 | 已追溯 |
| TC-002 | {{名称}} | - | **无追溯** |

## 7. 与 QA 自检交叉验证

| 项 | QA 自检结果 | Reviewer 独立校验结果 | 一致性 |
|---|---|---|---|
| PRD 覆盖率 | {{percent}}% | {{percent}}% | {{一致/不一致}} |
| 用例总数 | {{n}} | {{n}} | {{一致/不一致}} |

> 不一致项需标注并要求 QA 说明。

## 8. 结论与后续动作

- **结论**：{{APPROVED / REJECTED}}
- **deliverable_allowed**：{{true / false}}
- **后续**：{{进入测试执行 / 等待 QA 修正后重新提交}}
- **修正建议摘要**：{{如 REJECTED，列出 P0/P1 问题修正方向}}
