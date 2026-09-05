---
name: qa-test-planner
description: "QA 测试计划与用例设计：基于 PRD/OpenAPI/源码制定测试策略，设计测试用例并追溯 PRD 功能。触发条件：PM 向 QA 分发任务、用户说'设计测试'或'写测试用例'。"
---

# QA Test Planner Skill (v2 — PRD 追溯 + 确认门禁版)

## 核心原则

每个测试用例必须能追溯到 PRD 功能来源。测试计划完成后必须经过 QA + PM 确认，确认门禁通过后才开始执行。测试范围必须有 PRD 覆盖率验证（每个 PRD 功能都有对应测试用例）。

## 关键机制

### PRD 追溯表

详见 `assets/test-case-trace-table.md` 模板。

### PRD 覆盖率验证

详见 `scripts/prd_coverage_validator.py` 验证脚本。

### 测试计划确认门

```
测试计划编写完成
    ↓
QA 自检：PRD 覆盖率 100%？测试用例 ID 唯一？
    ↓
报告 PM：输出测试计划摘要，请求确认
    ↓
PM 确认（"可以执行"）
    ↓
开始测试执行
```

## 触发条件

- PM 向 QA 分发任务（`role: qa`，`phase: test-planning`）
- 用户说"设计测试"、"写测试用例"、"测试计划"
- 需要从 PRD/OpenAPI 提取测试点时

## 输入

| 类型 | 描述 |
|------|------|
| **必需** | PRD（已批准，artifact_ref） |
| **必需** | OpenAPI 规范（APPROVED） |
| **必需** | 源代码变更 |
| **可选** | 架构设计、UI 规范 |
| **固定约束** | 测试环境、安全规范、项目版本 |

## 输出制品

| 制品类型 | 描述 |
|----------|------|
| **TEST_PLAN** | 测试计划文档（测试策略、范围、资源规划、风险分析、PRD 覆盖率） |
| **TEST_CASES** | 测试用例集（单元测试、集成测试、E2E 测试，带 PRD 追溯） |
| **TEST_PLAN_APPROVAL_RECORD** | 测试计划批准记录（确认门禁） |

## 执行步骤

### Step 0: 前置校验 — PRD 批准记录检查

```python
# 详见 scripts/prd_validator.py
```

---

### Step 1: 从 PRD 提取功能清单

```python
# 详见 scripts/prd_functions_extractor.py
# 输出：prd_functions 列表
```

---

### Step 2: 从 OpenAPI 提取 API 清单

```python
# 详见 scripts/openapi_api_extractor.py
# 输出：apis 列表
```

---

### Step 3: 设计测试策略

详见 `assets/test-strategy-template.md`。

---

### Step 4: 编写测试用例（带 PRD 追溯）

详见：
- `assets/unit-test-template.md` - 单元测试用例模板
- `assets/integration-test-template.md` - 集成测试用例模板
- `assets/e2e-test-template.md` - E2E 测试用例模板

---

### Step 5: PRD 覆盖率验证

```python
# 详见 scripts/prd_coverage_validator.py
# 输出：覆盖率报告，必须 100% 才可通过
```

---

### Step 6: 测试数据管理

详见 `assets/test-data-template.md`。

---

### Step 7: 测试计划确认门禁

详见 `assets/approval-gate-template.md`。

---

### Step 8: 生成 TEST_PLAN_APPROVAL_RECORD

详见 `assets/approval-record-template.json`。

---

## PM 回复模板

### 测试计划完成（待确认）

详见 `assets/pm-reply-templates.md#待确认`。

### 测试计划确认通过

详见 `assets/pm-reply-templates.md#确认通过`。

---

## 验证步骤

1. [ ] PRD 校验通过（APPROVED 状态）
2. [ ] 每个 PRD 功能有至少一个测试用例覆盖（PRD 覆盖率 100%）
3. [ ] 每个测试用例有唯一的 ID 和 PRD 追溯字段
4. [ ] P0 功能有单元测试 + 集成测试
5. [ ] 测试数据（fixture）已定义
6. [ ] PM 确认测试计划后（TEST_PLAN_APPROVAL_RECORD 已生成）
7. [ ] 测试用例索引完整（ID / 类型 / 功能点 / 优先级 / 状态 / PRD 追溯）

## 常见陷阱

1. **PRD 覆盖率 < 100%**：有功能没写测试用例就进入执行阶段
2. **用例粒度过粗**：一个用例测 10 步，出问题无法定位
3. **只测正向流程**：缺少异常流程、边界条件测试
4. **测试数据硬编码**：数据库里有数据才能跑，换环境就挂
5. **测试用例无 PRD 追溯**：无法验证"每个 PRD 功能都有测试"
6. **跳过确认门**：没等 PM 确认就进入执行阶段

## 文件结构

```
qa-test-planner/
├── SKILL.md                              # 主文档（本文件）
├── assets/                               # 模板文件
│   ├── test-case-trace-table.md          # PRD 追溯表示例
│   ├── test-strategy-template.md         # 测试策略模板
│   ├── unit-test-template.md             # 单元测试用例模板
│   ├── integration-test-template.md      # 集成测试用例模板
│   ├── e2e-test-template.md              # E2E 测试用例模板
│   ├── test-data-template.md             # 测试数据模板
│   ├── approval-gate-template.md         # 确认门禁模板
│   ├── approval-record-template.json     # 批准记录模板
│   └── pm-reply-templates.md             # PM 回复模板
├── references/                           # 参考文档
│   └── collaboration-contract.md         # QA 工作流与跨 Profile 协作契约
└── scripts/                              # 验证脚本
    ├── prd_validator.py                  # PRD 批准状态校验
    ├── prd_coverage_validator.py         # PRD 覆盖率验证
    ├── prd_functions_extractor.py        # PRD 功能提取
    └── openapi_api_extractor.py          # OpenAPI API 提取
```

## 参考文档

| 文档 | 内容 |
|------|------|
| `references/collaboration-contract.md` | QA 工作流与跨 Profile 协作契约（L1/L2/L3 适配、输入输出契约、接收校验、状态上报、完成通知、缺陷反馈、异常处理） |
