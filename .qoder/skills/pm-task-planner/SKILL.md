---
name: pm-task-planner
description: "任务规划与依赖管理：从 PRD 和 ARCH_SPEC 拆解任务 DAG，建立任务依赖关系，追踪进度。每个任务必须可追溯到 PRD 需求来源。当用户说'任务规划'、'拆分任务'、'排期'时触发。"
version: 2.0.0
author: Hermes Agent (AI-Native Software Factory)
license: MIT
platforms: [linux, macos, windows]
metadata:
  hermes:
    tags: [pm, task-planning, task-dag, dependency, sprint, roadmap, traceability]
    related_skills: [pm-prd-manager, SPACE-roadmap-planner]
    artifact_type: TASK_PLAN
    workflow_modes: [L2, L3]
---

# PM Task Planner Skill (v2 — 需求追溯版)

## 核心原则

每个任务必须可追溯到 PRD 的具体需求来源，不可追溯的任务不允许出现在任务列表中。任务规划完成后，必须有用户确认才可分发给 Fullstack。

## 触发条件

- 用户说"任务规划"、"拆分任务"、"排期"、"任务拆解"
- PM 完成了 PRD/复杂度评估，需要拆解执行任务时
- 需要建立任务依赖关系、制定里程碑时

## 输入

- **必需**：已批准的 PRD（artifact_ref，状态 APPROVED）、复杂度评估结果（COMPLEXITY_ASSESSMENT）
- **可选**：ARCH_SPEC、技术约束、团队产能
- **固定约束**：工作流模式（L1/L2/L3）、项目版本

## 输出制品

- **TASK_PLAN**：任务拆分建议和依赖关系图（artifact_type: TASK_PLAN）
  - 任务列表（含 ID、名称、负责人、预估工时、**需求追溯**）
  - 依赖关系图（DAG）
  - 里程碑节点
  - 风险与缓冲
- **TASK_APPROVAL_RECORD**：任务规划批准记录（artifact_type: APPROVAL_RECORD）

## 执行步骤

### Step 1: 读取并校验 PRD

```markdown
## PRD 校验

收到任务规划请求，校验以下前提条件：

1. [ ] PRD 已通过第三门禁（APPROVED 状态）
2. [ ] PRD 的 artifact_ref 可追溯
3. [ ] PRD 功能清单完整（含 ID/名称/优先级）

如果 PRD 未 APPROVED：
→ 拒绝任务规划请求，提示"PRD 必须先通过审批"

当前 PRD：
- 名称：{name}
- 版本：{version}
- Hash：{hash}
- 状态：{APPROVED/DRAFT}
- 功能数量：{n} 个（P0: {n}, P1: {n}, P2: {n}）
```

### Step 2: 任务拆解（按 PRD 功能逐条追溯）

#### 2.1 任务拆分原则

```
拆分标准：
- 每个任务 2-4 小时的工作量
- 每个任务有明确的输入、输出、验收标准
- 每个任务由单一 Agent 完成
- 任务间无循环依赖
```

#### 2.2 按功能拆解任务（需求追溯）

**核心规则**：每个任务必须指向 PRD 功能 ID，不可凭空创造任务。

```markdown
## 任务拆解 — 需求追溯表

从 PRD 功能清单逐条拆解，产出"需求追溯链"：

| 任务ID | 任务名称 | 对应 PRD 功能 | 需求来源 | 类型 | 预估工时 |
|--------|---------|--------------|---------|------|---------|
| T-001 | 用户注册页面开发 | F1 用户注册 | PRD-F1 | 前端 | 3h |
| T-002 | 用户注册 API 开发 | F1 用户注册 | PRD-F1 | 后端 | 2h |
| T-003 | 邮箱验证功能开发 | F2 邮箱验证 | PRD-F2 | 后端 | 2h |
```

**禁止事项**：
- ❌ 不得创建无法指向 PRD 功能的任务（如"优化公共组件"）
- ❌ 不得将多个 PRD 功能合并为一个任务（除非它们在同一模块且互斥）
- ❌ 不得跳过优先级高的功能先做低的

#### 2.3 按职能拆分任务

```python
# 任务类型映射
TASK_TYPES = {
    "frontend": ["页面开发", "组件开发", "样式开发", "API 对接"],
    "backend": ["API 开发", "业务逻辑", "数据访问", "中间件"],
    "test": ["单元测试", "集成测试", "E2E 测试"],
    "doc": ["API 文档", "数据库文档"],
    "deploy": ["环境部署", "配置变更"],
    "tech-debt": ["代码优化", "重构"],
}
```

对于每个 PRD 功能，按前端→后端→测试的顺序拆解，并记录追溯关系：

```markdown
### F1 用户注册 — 任务拆解

| 任务ID | 任务名称 | 需求来源（PRD） | 前置任务 | 工时 |
|--------|---------|----------------|---------|------|
| T-001 | 用户注册页面 | PRD-F1-用户注册 | - | 3h |
| T-002 | 用户注册 API | PRD-F1-用户注册 | T-001（并行）| 2h |
| T-003 | 邮箱验证 API | PRD-F2-邮箱验证 | T-002 | 2h |
| T-004 | 注册功能单元测试 | PRD-F1-用户注册 | T-002 | 1h |

追溯链：T-001/T-002/T-004 → PRD F1（用户注册）；T-003 → PRD F2（邮箱验证）
```

### Step 3: 建立依赖关系（DAG）

#### 依赖类型

```
Finish-to-Start (FS)：A 完成后才能开始 B（主要）
Start-to-Start (SS)：A 开始后才能开始 B（并行场景）
```

#### 依赖关系图（DAG）示例

```markdown
## 任务 DAG（L3 订单系统示例）

                     ┌──────────────────┐
                     │  T-001 PRD 评审  │
                     └────────┬─────────┘
                              │ FS
                     ┌────────▼─────────┐
                     │  T-010 架构设计  │ ← 追溯：PRD M1
                     └────────┬─────────┘
                              │ FS
              ┌───────────────┼───────────────┐
              │ FS            │ FS            │ FS
    ┌─────────▼──────┐ ┌──────▼──────┐ ┌─────▼──────┐
    │ T-101 用户模块 │ │ T-201 订单模块│ │ T-301 商品模块│
    │ ←追溯：PRD-F1  │ │ ←追溯：PRD-F3 │ │ ←追溯：PRD-F5│
    └─────────┬──────┘ └──────┬──────┘ └─────┬──────┘
              │ SS            │ SS           │
    ┌─────────▼──────┐ ┌──────▼──────┐ ┌─────▼──────┐
    │ T-111 用户测试 │ │ T-211 订单测试│ │ T-311 商品测试│
    └─────────┬──────┘ └──────┬──────┘ └─────┬──────┘
              │               │              │
              └───────────────┼──────────────┘
                              │ FS
                     ┌────────▼─────────┐
                     │  T-400 集成测试  │
                     └────────┬─────────┘
                              │ FS
                     ┌────────▼─────────┐
                     │  T-500 部署上线  │
                     └──────────────────┘
```

#### 任务依赖表（含需求追溯）

```markdown
## 任务依赖表

| 任务ID | 任务名称 | 需求追溯（PRD功能） | 依赖任务 | 产出 | 工时 |
|--------|---------|-------------------|---------|------|------|
| T-001  | PRD 评审通过 | — | — | 评审通过 | 1h |
| T-010  | 架构设计 | M1 需求冻结 | T-001 | ARCH_SPEC | 4h |
| T-101  | 用户模块前端 | F1 用户注册、F2 登录 | T-010 | SOURCE_PATCH | 6h |
| T-102  | 用户模块后端 | F1 用户注册、F2 登录 | T-010 | SOURCE_PATCH | 6h |
| T-111  | 用户模块测试 | F1、F2 | T-101, T-102 | TEST_REPORT | 3h |
| T-201  | 订单模块前端 | F3 订单管理 | T-010 | SOURCE_PATCH | 8h |
| T-202  | 订单模块后端 | F3 订单管理 | T-010 | SOURCE_PATCH | 8h |
| T-211  | 订单模块测试 | F3 | T-201, T-202 | TEST_REPORT | 4h |
| T-400  | 集成测试 | F1、F2、F3、F4 | T-111, T-211 | TEST_REPORT | 4h |
| T-500  | 部署上线 | — | T-400 | BUILD_ARTIFACT | 2h |
```

### Step 4: 估算工时与排期

```python
# 估算方法：经验估算 + 缓冲

BASE_HOURS = {
    "arch-design": 4,
    "frontend-page": 3,      # 每个前端页面
    "backend-api-per-model": 2,  # 每个数据模型的 CRUD API
    "unit-test-per-100loc": 1,   # 每 100 行代码的单元测试
    "integration-test": 4,
    "deployment": 2,
}

BUFFER_FACTOR = {
    "L1": 1.1,
    "L2": 1.2,
    "L3": 1.3,
}

# 追溯完整性检查
TRACEABILITY_CHECK = {
    "all_tasks_have_prd_ref": True,   # 所有任务必须有 PRD 功能引用
    "no_orphan_tasks": True,          # 不得有无 PRD 来源的任务
    "each_prd_function_covered": True, # 每个 PRD 功能都有对应任务
}
```

#### 排期甘特图

```markdown
## 排期甘特图（3人团队，2周 Sprint）

| 任务 | D1 | D2 | D3 | D4 | D5 | D6 | D7 | D8 | D9 | D10 |
|------|----|----|----|----|----|----|----|----|----|-----|
| T-001 PRD评审 | ██ |    |    |    |    |    |    |    |    |     |
| T-010 架构设计 |    | ██ | ██ |    |    |    |    |    |    |     |
| T-101 用户前端 |    |    | ██ | ██ | ██ |    |    |    |    |     |
| T-102 用户后端 |    |    | ██ | ██ | ██ |    |    |    |    |     |
| T-201 订单前端 |    |    |    | ██ | ██ | ██ |    |    |    |     |
| T-202 订单后端 |    |    |    | ██ | ██ | ██ |    |    |    |     |
| T-111+T-211 测试|    |    |    |    |    | ██ | ██ | ██ |    |     |
| T-400 集成测试  |    |    |    |    |    |    |    | ██ | ██ |     |
| T-500 部署上线  |    |    |    |    |    |    |    |    | ██ |     |

**里程碑**：
- D3：架构设计完成（M2）
- D8：开发完成，进入测试（M3）
- D10：上线（M4）
```

### Step 5: 任务规划确认门

```markdown
---

**【任务规划确认门】**

请确认以下内容：
1. 任务拆分是否完整覆盖了 PRD 所有功能（每个功能都有对应任务）？
2. 任务之间的依赖关系是否正确？
3. 追溯关系是否准确（每个任务都能指向 PRD 功能）？
4. 排期和里程碑是否合理？
5. 风险缓冲时间是否足够？

**确认后输出**：任务规划已冻结，`deliverable_allowed=true`，可分发给 Fullstack。
**未确认**：请指出需修改的内容，修订后重新确认。
```

确认后生成 `TASK_APPROVAL_RECORD`：

```json
{
  "artifact": "TASK_PLAN",
  "name": "{项目名称}",
  "version": "{version}",
  "prd_ref": "PRD@{prd_hash}",
  "total_tasks": {n},
  "total_hours": {n},
  "gates": {
    "Gate-1": {
      "name": "任务规划确认",
      "status": "CONFIRMED",
      "timestamp": "{timestamp}",
      "confirmed_by": "用户"
    }
  },
  "deliverable_allowed": true,
  "task_traceability": {
    "all_tasks_traced": true,
    "orphan_tasks": 0,
    "prd_coverage": "100%"
  }
}
```

### Step 6: 风险缓冲策略

```markdown
## 风险与缓冲

| 风险 | 缓解措施 | 缓冲时间 |
|------|---------|---------|
| 第三方 API 延迟 | 提前对接，预留 1 天 | +1d |
| 人员请假 | 交叉培训，关键任务备份人 | +0.5d |
| 需求变更 | 变更评审委员会，锁定 D3 后不允许新增 | +0d |
| 技术难题 | 预留 2 天 spike 时间 | +2d |

**总计缓冲**：3.5 天（占项目总时长 17.5%）
```

## 需求追溯验证

任务规划产出前，必须验证追溯完整性：

```python
def validate_traceability(task_list, prd_functions):
    """
    验证所有任务可追溯到 PRD 功能
    """
    results = {
        "orphan_tasks": [],      # 无 PRD 来源的任务
        "uncovered_functions": [], # 无任务覆盖的 PRD 功能
        "valid": True
    }

    for task in task_list:
        if not task.get("prd_ref"):
            results["orphan_tasks"].append(task["id"])
            results["valid"] = False

    covered_functions = {task["prd_ref"] for task in task_list if task.get("prd_ref")}
    all_functions = {f["id"] for f in prd_functions}

    uncovered = all_functions - covered_functions
    if uncovered:
        results["uncovered_functions"] = list(uncovered)
        results["valid"] = False

    return results
```

## PM 回复模板

### 任务规划完成

```markdown
## 任务规划完成

**项目**：{项目名称}
**工作流**：L3 复杂
**PRD**：{prd_name}@{hash}
**总任务数**：{n} 个
**总工时**：{n}h（含缓冲）

### 需求追溯汇总

| PRD 功能 | 对应任务数 | 状态 |
|---------|-----------|------|
| F1 用户注册 | 4 个任务 | ✅ 已拆解 |
| F2 邮箱验证 | 2 个任务 | ✅ 已拆解 |
| F3 订单管理 | 6 个任务 | ✅ 已拆解 |

### 里程碑

- D3：架构设计完成
- D8：开发完成，进入测试
- D10：上线

### 追溯完整性

- 所有任务均指向 PRD 功能：✅
- 无孤立任务：✅
- PRD 功能 100% 覆盖：✅

是否确认执行分发？
```

## 验证步骤

1. [ ] PRD 已 APPROVED 才能开始任务规划
2. [ ] 每个任务必须有 `prd_ref` 字段指向 PRD 功能 ID
3. [ ] 无孤立任务（orphan_tasks = 0）
4. [ ] 所有 PRD 功能都有对应任务（uncovered_functions = 0）
5. [ ] 依赖关系无环（DAG 校验）
6. [ ] 任务规划确认门已通过
7. [ ] `TASK_APPROVAL_RECORD` 已生成，`deliverable_allowed=true`
8. [ ] 里程碑定义清晰

## 常见陷阱

1. **任务无 PRD 来源**：创建了无法指向需求的任务（如"优化公共组件"）
2. **PRD 功能遗漏**：某些功能没有对应任务，导致交付不完整
3. **依赖关系缺失**：任务间有依赖但未标注，导致执行顺序错误
4. **跳过追溯检查**：没验证就分发，导致 Fullstack 实现范围与 PRD 不符
5. **缓冲不足**：按理想情况排期，不留风险缓冲