# 需求审查报告（REQUIREMENTS_AUDIT_REPORT）

> 模板：审查 PRD/USER_STORY 本身质量，由 `reviewer-requirements-audit` 产出。
> 落盘路径：`docs/01需求分析/{NN}-审查报告/{task_id}_requirements_audit_report.md`，批准记录同目录 `_requirements_audit_approval_record.json`。`{NN}` 按 `docs/01需求分析/` 同级目录动态确定（详见 docs/AGENTS.md「审查报告子目录」），当前快照 `01`。

## 1. 审查元信息

| 项 | 值 |
|---|---|
| 项目 | {{项目名称}} |
| PRD 版本 | {{version}}@{{hash}} |
| 工作流模式 | {{L2 / L3}} |
| 审查时间 | {{ISO8601}} |
| 审查结论 | {{APPROVED / REJECTED}} |
| deliverable_allowed | {{true / false}} |

## 2. 门禁结果

| 门禁 | 结果 | 说明 |
|---|---|---|
| AMBIGUITY_GATE | {{PASS/FAIL}} | 歧义检查 |
| ACCEPTANCE_GATE | {{PASS/FAIL}} | 验收标准检查 |
| TESTABILITY_GATE | {{PASS/FAIL}} | 可测性检查 |
| COMPLETENESS_GATE | {{PASS/FAIL}} | 完整性检查 |

## 3. 问题清单

| 编号 | 级别 | PRD 章节/用户故事 | 问题描述 | 违反规则 | 建议修正 |
|---|---|---|---|---|---|
| I-001 | P0 | US-xxx | {{描述}} | R1/R2/... | {{建议}} |

## 4. 问题统计

| 级别 | 数量 |
|---|---|
| P0（阻断） | {{n}} |
| P1（严重） | {{n}} |
| P2（一般） | {{n}} |
| P3（建议） | {{n}} |
| **合计** | {{n}} |

## 5. 追溯表（问题 ↔ PRD 章节）

| 问题编号 | PRD 章节/用户故事 ID | 涉及术语 |
|---|---|---|
| I-001 | US-xxx | {{术语}} |

## 6. 结论与后续动作

- **结论**：{{APPROVED / REJECTED}}
- **deliverable_allowed**：{{true / false}}
- **后续**：{{进入设计阶段 / 等待 PM 修正后重新提交}}
- **修正建议摘要**：{{如 REJECTED，列出 P0 问题修正方向}}
