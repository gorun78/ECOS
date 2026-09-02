---
name: reviewer-requirements-audit
description: "Reviewer 需求文档审查 Skill：审查 PRD/USER_STORY 的可测性、验收标准明确性、无歧义性、边界完备性。区别于 reviewer-arch-design-audit（把 PRD 当对照基准做需求覆盖校验），本 Skill 审查 PRD 本身的质量。输出 REQUIREMENTS_AUDIT_REPORT + REQUIREMENTS_AUDIT_APPROVAL_RECORD（含 deliverable_allowed）。PM 向 Reviewer 分发需求审查任务或用户说'审查需求'、'PRD 审查'时触发。"
version: 1.0.0
author: Hermes Agent (AI-Native Software Factory)
license: MIT
platforms: [linux, macos, windows]
metadata:
  hermes:
    tags: [reviewer, requirements-audit, prd-review, testability, acceptance-criteria, deliverable-gate]
    related_skills: [reviewer-review-dispatcher, reviewer-arch-design-audit, reviewer-testcase-audit]
    artifact_type: REQUIREMENTS_AUDIT_REPORT
    workflow_modes: [L2, L3]
---

# Reviewer Requirements Audit Skill (v1 — 交付门禁版)

## 核心原则

审查 PRD/USER_STORY 本身的质量，而非把 PRD 当作对照基准（那是 `reviewer-arch-design-audit` 的职责）。审查完成后必须生成 `REQUIREMENTS_AUDIT_APPROVAL_RECORD`，含明确的 `deliverable_allowed` 判定。`deliverable_allowed=false` 时阻断 PRD 进入下游设计阶段，PM 必须等待 PM/需求方修正后重新提交。每个问题必须能追溯到 PRD 具体章节/用户故事。

## 与相关 Skill 的边界

| Skill | 审查对象 | 视角 |
|-------|---------|------|
| **本 Skill** | PRD/USER_STORY 本身 | 需求质量：可测性、无歧义、验收标准、边界 |
| `reviewer-arch-design-audit` | ARCH_SPEC/OpenAPI/DDL | 设计质量：需求覆盖、技术合规、安全 |
| `reviewer-testcase-audit` | TEST_CASES/TEST_PLAN | 用例质量：覆盖率、粒度、追溯 |

**调用顺序**：本 Skill 在 `reviewer-arch-design-audit` **之前**执行（PRD 质量不达标，设计审查无意义）。

## 关键机制

### 质量门禁判定

| 门禁 | 判定条件 | 阻断条件 |
|------|---------|---------|
| **AMBIGUITY_GATE** | 无 P0 歧义问题 | 存在 P0 歧义（关键术语无定义/互相矛盾）→ FAIL |
| **ACCEPTANCE_GATE** | 所有用户故事有可验证的验收标准 | 存在无验收标准或不可验证的用户故事 → FAIL |
| **TESTABILITY_GATE** | 无 P0 不可测需求 | 存在 P0 不可测需求（无明确输入/输出/边界）→ FAIL |
| **COMPLETENESS_GATE** | 无 P0 需求遗漏 | 存在 P0 业务流断点/依赖未声明 → FAIL |

### 缺陷分级

| 级别 | 含义 | 示例 |
|------|------|------|
| **P0** | 阻断：需求无法被设计/测试/实现 | 关键术语无定义、业务流断点、互相矛盾的需求 |
| **P1** | 严重：影响设计或测试质量 | 验收标准不可验证、边界缺失、优先级未声明 |
| **P2** | 一般：可优化 | 表述模糊但可推断、非功能性需求缺失 |
| **P3** | 建议 | 措辞优化、文档结构改进 |

### deliverable_allowed 判定

```
deliverable_allowed = true 条件：
  AMBIGUITY_GATE = PASS
  ACCEPTANCE_GATE = PASS
  TESTABILITY_GATE = PASS
  COMPLETENESS_GATE = PASS

deliverable_allowed = false 条件（任一即 FAIL）：
  存在 P0 歧义
  存在无验收标准或不可验证的用户故事
  存在 P0 不可测需求
  存在 P0 业务流断点/依赖未声明
```

## 触发条件

- PM 向 Reviewer 分发需求审查任务（`role: reviewer`, `task_type: requirements-audit`）
- PM 提交 PRD 后请求质量审查
- 用户说"审查需求"、"PRD 审查"、"需求质量审查"、"检查用户故事"
- `reviewer-review-dispatcher` 根据 `artifact_type=PRD/USER_STORY` 路由调用

## 输入

| 类型 | 描述 |
|------|------|
| **必需** | PRD（artifact_ref）或 USER_STORY 集合 |
| **可选** | 业务背景文档、利益相关方访谈记录、竞品分析 |
| **固定约束** | 项目版本、需求模板规范 |

## 输出制品

| 制品类型 | 描述 |
|----------|------|
| **REQUIREMENTS_AUDIT_REPORT** | 需求审查报告（含问题清单、追溯表、门禁结果） |
| **REQUIREMENTS_AUDIT_APPROVAL_RECORD** | 审查批准记录（JSON，含 deliverable_allowed） |

### 落盘路径

所有制品除作为 Skill 返回值外，必须同步落盘到 `docs/01需求分析/{NN}-审查报告/`：

| 制品 | 路径 |
|---|---|
| REQUIREMENTS_AUDIT_REPORT | `docs/01需求分析/{NN}-审查报告/{task_id}_requirements_audit_report.md` |
| REQUIREMENTS_AUDIT_APPROVAL_RECORD | `docs/01需求分析/{NN}-审查报告/{task_id}_requirements_audit_approval_record.json` |

> `{NN}` 为审查报告目录序号，**按 `docs/01需求分析/` 同级目录动态确定**（01 阶段使用 `{NN}` 形式，取当前最大序号 +1，复用已有 `*-审查报告` 目录），详见 docs/AGENTS.md「审查报告子目录」。当前快照：`01`。

执行前按动态编号规则解析目录并创建：

```bash
mkdir -p "docs/01需求分析/{NN}-审查报告"
```

## 执行步骤

### Step 1: 解析 PRD 结构

提取 PRD 的核心结构化要素：

```python
prd_structure = {
    "user_stories": [...],      # 用户故事列表
    "glossary": [...],          # 术语表
    "business_flows": [...],    # 业务流程
    "non_functional": [...],    # 非功能性需求
    "constraints": [...],       # 约束条件
    "assumptions": [...]        # 假设
}
```

### Step 2: 歧义检查（AMBIGUITY_GATE）

逐项检查：

1. **术语一致性**：核心业务术语是否在术语表中定义，全文是否一致使用
2. **矛盾检测**：不同章节/用户故事之间是否存在互相矛盾的需求
3. **歧义表述**：是否存在"等"、"可能"、"适当"、"高性能"等不可量化表述且无补充定义

### Step 3: 验收标准检查（ACCEPTANCE_GATE）

对每个用户故事检查：

1. **存在性**：是否有明确的验收标准（Given/When/Then 或等价表述）
2. **可验证性**：验收标准是否能被测试用例验证（有明确输入、预期输出、前置条件）
3. **完整性**：验收标准是否覆盖正常流、异常流、边界

### Step 4: 可测性检查（TESTABILITY_GATE）

逐条检查需求是否可测：

1. **输入明确**：是否有明确的输入数据/触发条件
2. **输出明确**：是否有可观测的预期输出/状态变更
3. **边界明确**：是否定义了边界值、空值、极值场景
4. **不可测需求标记**：识别"系统应快速响应"类无量化指标的需求

### Step 5: 完整性检查（COMPLETENESS_GATE）

1. **业务流闭环**：每个业务流是否有起点、终点、异常分支
2. **依赖声明**：需求间的依赖关系是否声明
3. **角色覆盖**：所有涉及的角色是否都有对应需求
4. **异常路径**：关键业务流的异常/失败路径是否定义

### Step 6: 生成审查报告

详见 `assets/requirements-audit-report-template.md`。

### Step 7: 生成批准记录

详见 `assets/requirements-audit-approval-record-template.json`。

### Step 8: 通知 PM

```
REQUIREMENTS_AUDIT_DONE: {task_id}
  artifact: PRD@{hash}
  result: {APPROVED / REJECTED}
  deliverable_allowed: {true/false}
  issue_summary:
    p0: {n}
    p1: {n}
    p2: {n}
    p3: {n}
  gates:
    AMBIGUITY_GATE: {PASS/FAIL}
    ACCEPTANCE_GATE: {PASS/FAIL}
    TESTABILITY_GATE: {PASS/FAIL}
    COMPLETENESS_GATE: {PASS/FAIL}
  next: {进入设计阶段 / 等待 PM 修正后重新提交}
```

## 审查规则速览

| 规则 | 核心要求 | 违规级别 | 对应门禁 |
|------|---------|---------|---------|
| R1 术语定义 | 核心术语必须在术语表定义且全文一致 | P0/P1 | AMBIGUITY_GATE |
| R2 矛盾检测 | 需求间不得互相矛盾 | P0 | AMBIGUITY_GATE |
| R3 量化表述 | "高性能""快速"等必须有量化指标 | P1/P2 | AMBIGUITY_GATE |
| R4 验收标准 | 每个用户故事有可验证的验收标准 | P0/P1 | ACCEPTANCE_GATE |
| R5 输入输出 | 需求有明确输入和可观测输出 | P0/P1 | TESTABILITY_GATE |
| R6 边界定义 | 关键需求定义边界/空值/极值 | P1/P2 | TESTABILITY_GATE |
| R7 业务流闭环 | 业务流有完整起点终点异常分支 | P0 | COMPLETENESS_GATE |
| R8 依赖声明 | 需求间依赖关系显式声明 | P1/P2 | COMPLETENESS_GATE |

## 验证步骤

1. [ ] PRD 已正确解析为结构化要素
2. [ ] 歧义检查已执行（术语、矛盾、歧义表述）
3. [ ] 验收标准检查已执行（存在性、可验证性、完整性）
4. [ ] 可测性检查已执行（输入、输出、边界）
5. [ ] 完整性检查已执行（业务流、依赖、角色、异常路径）
6. [ ] 问题已正确分级为 P0/P1/P2/P3
7. [ ] 四个门禁判定逻辑正确
8. [ ] 审查报告包含追溯表和问题详情
9. [ ] 批准记录的 deliverable_allowed 与门禁结果一致
10. [ ] 结果已通知 PM

## 常见陷阱

| 陷阱 | 描述 | 规避方法 |
|------|------|---------|
| 与设计审计混淆 | 把 PRD 当对照基准做覆盖校验 | 本 Skill 只审 PRD 本身，覆盖校验交给 arch-design-audit |
| 验收标准过宽 | "系统正常运行"类不可验证标准 | 强制 Given/When/Then 结构 |
| 漏审异常路径 | 只审正常流 | 业务流必须包含失败/异常分支 |
| 不可测需求放过 | "系统应快速响应"无指标 | 要求量化（如 P99 < 200ms） |

## REQUIREMENTS_AUDIT_APPROVAL_RECORD 格式

```json
{
  "artifact": "REQUIREMENTS_AUDIT_REPORT",
  "name": "{项目名称} 需求审查报告",
  "version": "v{version}",
  "hash": "{内容哈希}",
  "status": "{APPROVED / REJECTED}",
  "workflow_mode": "{L2 / L3}",
  "approvals": [
    {
      "role": "reviewer-requirements-audit",
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
  "gates_passed": ["AMBIGUITY_GATE", "ACCEPTANCE_GATE", "TESTABILITY_GATE", "COMPLETENESS_GATE"],
  "deliverable_allowed": {true/false},
  "prd_ref": "PRD@{hash}",
  "timestamp": "{ISO8601}"
}
```

## 与其他 Skill 的关系

| Skill | 关系 |
|-------|------|
| `reviewer-review-dispatcher` | 本 Skill 的路由入口，按 artifact_type=PRD 分发到此 |
| `reviewer-arch-design-audit` | 下游：本 Skill 通过后，PRD 才可作为设计审计的对照基准 |
| `reviewer-testcase-audit` | 下游：本 Skill 通过后，PRD 才可作为用例审查的追溯来源 |
