---
name: reviewer-review-dispatcher
description: "Reviewer 审查路由 Skill：按 artifact_type 将审查请求分发到对应专项审查 Skill。统一入口，避免 PM 需要记住每个 Skill 名称。输出 dispatch_plan，由编排逻辑按计划调用各专项 Skill 并汇总结果。PM 向 Reviewer 分发任何审查任务或用户说'审查'、'review'时触发。"
version: 1.0.0
author: Hermes Agent (AI-Native Software Factory)
license: MIT
platforms: [linux, macos, windows]
metadata:
  hermes:
    tags: [reviewer, dispatcher, router, review-routing, orchestration]
    related_skills: [reviewer-requirements-audit, reviewer-arch-design-audit, reviewer-testcase-audit, reviewer-code-review, reviewer-arch-consistency, reviewer-security-audit, open-code-review]
    artifact_type: DISPATCH_PLAN
    workflow_modes: [L1, L2, L3]
---

# Reviewer Review Dispatcher Skill (v1 — 路由分发版)

## 核心原则

作为 Reviewer 所有审查任务的统一入口。按输入制品类型（`artifact_type`）将任务路由到对应专项审查 Skill。本 Skill 只负责"分发"，不负责"审查"——审查逻辑在各专项 Skill 内。分发后由编排逻辑按 `dispatch_plan` 调用各 Skill，汇总各 Skill 的 APPROVAL_RECORD 做最终交付门禁判定。

## 路由表

| 输入 artifact_type | 路由到 | 审查视角 | 工作流 |
|---|---|---|---|
| `PRD` / `USER_STORY` | `reviewer-requirements-audit` | 需求质量：可测性、无歧义、验收标准 | L2, L3 |
| `ARCH_SPEC` / `OPENAPI` / `DDL` | `reviewer-arch-design-audit` | 设计质量：需求覆盖、技术合规、安全 | L2, L3 |
| `TEST_CASES` / `TEST_PLAN` | `reviewer-testcase-audit` | 用例质量：覆盖率、粒度、追溯 | L2, L3 |
| `SOURCE_PATCH` | `reviewer-code-review` | 代码质量：缺陷、安全、架构 | L2, L3 |
| `SOURCE_PATCH`（架构一致性补充） | `reviewer-arch-consistency` | 架构一致性：契约/边界/数据模型 | L3 |
| `SOURCE_PATCH`（安全补充） | `reviewer-security-audit` | 安全：OWASP、权限、密钥 | L3 |

## 触发条件

- PM 向 Reviewer 分发审查任务（`role: reviewer`）
- 用户说"审查"、"review"、"代码审查"、"需求审查"、"用例审查"、"架构审计"
- 任何未明确指定专项 Skill 的审查请求

## 输入

| 类型 | 描述 |
|------|------|
| **必需** | 待审查制品（artifact_ref + artifact_type） |
| **必需** | workflow_mode（L1/L2/L3） |
| **可选** | 上下文制品（如审查代码时的 PRD/OpenAPI） |
| **固定约束** | 项目版本 |

## 输出制品

| 制品类型 | 描述 |
|----------|------|
| **DISPATCH_PLAN** | 路由计划（含目标 Skill 列表、调用顺序、并行/串行标记） |

## 执行步骤

### Step 1: 解析输入

```python
dispatch_input = {
    "artifact_type": "{PRD / ARCH_SPEC / OPENAPI / DDL / TEST_CASES / TEST_PLAN / SOURCE_PATCH / USER_STORY}",
    "artifact_ref": "{artifact}@{hash}",
    "workflow_mode": "{L1 / L2 / L3}",
    "context_artifacts": [...],   # 可选上下文制品
    "task_id": "{task_id}"
}
```

### Step 2: 按路由表确定目标 Skill

查路由表，确定一个或多个目标 Skill。

**单制品单 Skill 示例**（审查 PRD）：
```json
{
  "targets": [
    {"skill": "reviewer-requirements-audit", "artifact": "PRD@{hash}", "mode": "serial"}
  ]
}
```

**单制品多 Skill 示例**（L3 审查代码变更）：
```json
{
  "targets": [
    {"skill": "reviewer-code-review", "artifact": "SOURCE_PATCH@{hash}", "mode": "parallel"},
    {"skill": "reviewer-arch-consistency", "artifact": "SOURCE_PATCH@{hash}", "mode": "parallel"},
    {"skill": "reviewer-security-audit", "artifact": "SOURCE_PATCH@{hash}", "mode": "parallel"}
  ]
}
```

### Step 3: 确定调用顺序

| 场景 | 顺序 |
|------|------|
| 审查 PRD | 串行：requirements-audit |
| 审查设计文档 | 串行：arch-design-audit |
| 审查测试用例 | 串行：testcase-audit |
| L2 审查代码 | 串行：code-review |
| L3 审查代码 | 并行：code-review + arch-consistency + security-audit |
| 多制品组合审查 | 按依赖串行：先 requirements（若含 PRD）→ 再 arch-design（若含设计）→ 再 testcase（若含用例）→ 最后 code（若含代码） |

### Step 4: 应用工作流过滤

| 工作流 | 行为 |
|--------|------|
| **L1** | 仅分发快速代码检查（单 Skill：`reviewer-code-review`，快速模式），输出简化 `REVIEW_REPORT`。**Reviewer 审查不可跳过** |
| **L2** | 仅分发核心审查（单 Skill，不含 arch-consistency/security-audit） |
| **L3** | 全量分发（含并行补充审查） |

### Step 5: 输出 DISPATCH_PLAN

详见 `assets/dispatch-plan-template.json`。

#### 落盘路径

DISPATCH_PLAN 除作为 Skill 返回值外，必须同步落盘为 JSON：

| 制品 | 路径 |
|---|---|
| DISPATCH_PLAN | `docs/03开发阶段/{NN}-审查报告/{task_id}_dispatch_plan.json` |

> `{NN}` 为审查报告目录序号，**按 `docs/03开发阶段/` 同级目录动态确定**（取当前最大序号 +1，复用已有 `*-审查报告` 目录），详见 docs/AGENTS.md「审查报告子目录」。当前快照：`03-03`。

执行前按动态编号规则解析目录并创建：

```bash
mkdir -p "docs/03开发阶段/{NN}-审查报告"
```

### Step 6: 移交编排

DISPATCH_PLAN 输出后，由编排逻辑（reviewer 主循环或 `reviewer-code-review` 的编排段）按计划调用各 Skill。本 Skill 的职责到此结束。

### Step 7: 汇总门禁判定（编排侧）

编排逻辑收集各 Skill 的 APPROVAL_RECORD 后做最终判定：

```
final_deliverable_allowed = true 条件：
  所有被调用 Skill 的 deliverable_allowed = true

final_deliverable_allowed = false 条件（任一即 FAIL）：
  任一被调用 Skill 的 deliverable_allowed = false
```

### Step 8: 通知 PM

```
REVIEW_DISPATCHED: {task_id}
  artifact: {artifact}@{hash}
  workflow_mode: {L1/L2/L3}
  targets:
    - {skill_1}
    - {skill_2}
  next: 等待各专项审查完成
```

## 路由决策树

```
输入 artifact_type?
├─ PRD / USER_STORY
│   └─ reviewer-requirements-audit（串行）
├─ ARCH_SPEC / OPENAPI / DDL
│   └─ reviewer-arch-design-audit（串行）
├─ TEST_CASES / TEST_PLAN
│   └─ reviewer-testcase-audit（串行）
├─ SOURCE_PATCH
│   ├─ L1 → reviewer-code-review（快速模式，简化 REVIEW_REPORT）
│   ├─ L2 → reviewer-code-review（串行）
│   └─ L3 → reviewer-code-review + reviewer-arch-consistency + reviewer-security-audit（并行）
└─ 多制品组合
    └─ 按依赖顺序串行分发各专项
```

## 验证步骤

1. [ ] 输入 artifact_type 已正确解析
2. [ ] workflow_mode 已确认
3. [ ] 路由表查询正确，目标 Skill 列表准确
4. [ ] 调用顺序（串行/并行）符合场景
5. [ ] L1 工作流已分发快速代码检查（非跳过）
6. [ ] DISPATCH_PLAN 已输出
7. [ ] 已通知 PM 分发结果

## 常见陷阱

| 陷阱 | 描述 | 规避方法 |
|------|------|---------|
| 需求审查与设计审计混淆 | PRD 路由到 arch-design-audit | PRD/USER_STORY 一律路由到 requirements-audit |
| L3 漏并行 | L3 代码审查只调 code-review | L3 必须并行调 arch-consistency + security-audit |
| L1 误跳过 | L1 错误返回 SKIPPED 不分发 | L1 必须分发 reviewer-code-review（快速模式），Reviewer 审查不可跳过 |
| 越权审查 | 路由器自己执行审查逻辑 | 本 Skill 只分发，审查在各专项 Skill 内 |

## DISPATCH_PLAN 格式

```json
{
  "task_id": "{task_id}",
  "artifact_type": "{type}",
  "artifact_ref": "{artifact}@{hash}",
  "workflow_mode": "{L1/L2/L3}",
  "targets": [
    {
      "skill": "{skill_name}",
      "artifact": "{artifact}@{hash}",
      "context": ["{context_artifact_refs}"],
      "mode": "{serial/parallel}",
      "order": {n}
    }
  ],
  "expected_outputs": ["{APPROVAL_RECORD_TYPES}"],
  "final_gate_logic": "AND_ALL",
  "timestamp": "{ISO8601}"
}
```

## 与其他 Skill 的关系

本 Skill 是所有 reviewer-*-audit / reviewer-code-review / reviewer-arch-consistency / reviewer-security-audit / open-code-review 的统一上游入口。
