---
name: reviewer-arch-design-audit
description: "架构设计审计技能：审核PRD、架构设计、数据库Schema、API契约的需求覆盖、技术合规与安全风险。PM分发架构审计任务或用户说'架构审计'时触发。"
version: 3.0.0
author: Hermes Agent (AI-Native Software Factory)
license: MIT
platforms: [linux, macos, windows]
metadata:
  hermes:
    tags: [reviewer, arch-design-audit, architecture-review, schema-audit, api-audit, traceability]
    related_skills: [reviewer-code-review, reviewer-arch-consistency, reviewer-security-audit]
    artifact_type: ARCH_DESIGN_AUDIT_REPORT
    workflow_modes: [L2, L3]
---

# Reviewer Architecture Design Audit Skill

---

## 📋 快速概览

> 渐进式披露：从高层概览开始，逐步深入细节

| 维度 | 内容 |
|------|------|
| **技能定位** | 审核设计成果物，确保需求覆盖、技术合规、安全可靠 |
| **触发时机** | PM分发审计任务 / Arch提交设计物 / 用户说"架构审计" |
| **输入制品** | PRD、ARCH_SPEC、OpenAPI规范、DDL |
| **输出制品** | ARCH_DESIGN_AUDIT_REPORT、ARCH_DESIGN_AUDIT_APPROVAL_RECORD |
| **工作流程** | 5个阶段：解析→校验→分类→判定→报告 |

---

## 🔑 核心原则

- **只审设计不审代码**：聚焦架构设计阶段，代码审查由其他技能负责
- **规则驱动**：所有判定严格依据预设规则，不凭主观判断
- **分级处理**：问题分三级（BLOCKER/WARNING/SUGGESTION），不同级别不同处理策略
- **闭环反馈**：审计结果必须反馈给Arch修正，并抄送PM和Commander

---

## 🎯 触发条件

当满足以下任一条件时，必须调用本技能：

1. **任务分发**：PM向Reviewer分发架构审计任务（`role: reviewer`, `task_type: arch-audit`）
2. **成果物提交**：Arch提交设计成果物后（ARCH_SPEC、OpenAPI、DDL）
3. **用户指令**：用户说"架构审计"、"设计审查"、"审核架构设计"、"审计API设计"

---

## 📥 输入 / 📤 输出

### 输入制品

| 类型 | 名称 | 状态要求 | 说明 |
|------|------|---------|------|
| **必需** | PRD | APPROVED | 产品需求文档 |
| **必需** | ARCH_SPEC | APPROVED | 架构设计规格书 |
| **必需** | OpenAPI | APPROVED | API接口规范 |
| **必需** | DDL | APPROVED | 数据库建表脚本 |
| **可选** | 架构图 | - | 系统架构可视化图 |
| **可选** | 技术选型文档 | - | 技术栈决策依据 |
| **可选** | 非功能性需求 | - | 性能、安全、可用性要求 |

### 输出制品

| 制品类型 | 名称 | 说明 |
|---------|------|------|
| **主输出** | ARCH_DESIGN_AUDIT_REPORT | 架构设计审计报告（含判定结果和问题清单） |
| **记录** | ARCH_DESIGN_AUDIT_APPROVAL_RECORD | 审计批准记录（JSON格式） |

### 落盘路径

所有制品除作为 Skill 返回值外，必须同步落盘到 `docs/02设计阶段/{NN}-审查报告/`：

| 制品 | 路径 |
|---|---|
| ARCH_DESIGN_AUDIT_REPORT | `docs/02设计阶段/{NN}-审查报告/{task_id}_arch_design_audit_report.md` |
| ARCH_DESIGN_AUDIT_APPROVAL_RECORD | `docs/02设计阶段/{NN}-审查报告/{task_id}_arch_design_audit_approval_record.json` |

> `{NN}` 为审查报告目录序号，**按 `docs/02设计阶段/` 同级目录动态确定**（取当前最大序号 +1，复用已有 `*-审查报告` 目录），详见 docs/AGENTS.md「审查报告子目录」。当前快照：`02-06`。

执行前按动态编号规则解析目录并创建：

```bash
mkdir -p "docs/02设计阶段/{NN}-审查报告"
```

`scripts/audit.py` 的输出路径必须按动态编号规则解析（调用 `resolve_review_dir`），禁止写 cwd，禁止硬编码序号（详见 docs/AGENTS.md「审查报告子目录 → 强制规则」第 5 条）。

---

## 📂 脚本结构

本技能的核心逻辑已分离到 `scripts/` 目录中，便于独立维护和测试：

```
scripts/
├── __init__.py          # 包初始化，导出所有模块
├── audit.py             # 审计主入口脚本（命令行运行）
├── utils.py             # 工具函数（命名转换、保留字检查、依赖深度计算）
├── parsers.py           # 输入解析器（PRD/ARCH_SPEC/OpenAPI/DDL解析）
├── validators.py        # 规则校验器（需求覆盖、数据库合规、API契约、安全性能）
├── categorizer.py       # 问题分类器（BLOCKER/WARNING/SUGGESTION分级）
├── decision.py          # 综合决策器（APPROVE/PASS_WITH_CONDITIONS/REJECT）
└── reporter.py          # 报告生成器（审计报告、批准记录）
```

### 脚本调用方式

```bash
# 命令行方式
python scripts/audit.py <task_id> <prd_ref> <arch_spec_ref> <openapi_ref> <ddl_ref>

# Python API 方式
from scripts import run_all_validators, categorize_issues, make_decision, generate_audit_report
```

---

## 📜 审查规则速览

> **详细规则请参见下方「审查规则详解」章节**

| 规则编号 | 规则名称 | 核心要求 | 违规级别 |
|---------|---------|---------|---------|
| **规则1** | 需求闭环规则 | 架构设计必须完全覆盖PRD核心业务流 | BLOCKER |
| **规则2** | 数据库健康红线 | 命名规范、主键索引、数据完整性 | BLOCKER/WARNING |
| **规则3** | API规范与安全红线 | REST规范、幂等性、统一错误响应 | BLOCKER/WARNING |
| **规则4** | 架构解耦约束 | 无循环依赖、高频API有缓存/流控 | BLOCKER/SUGGESTION |

---

## 📊 问题分级标准

| 级别 | 标识 | 含义 | 处理方式 |
|------|------|------|---------|
| **BLOCKER** | 🔴 阻断 | 严重设计缺陷，必须修复 | REJECT（打回重设计） |
| **WARNING** | 🟡 警告 | 设计不够合理，影响质量但不阻塞功能 | PASS_WITH_CONDITIONS（带条件通过） |
| **SUGGESTION** | 🟢 建议 | 优化建议，不影响功能和质量 | APPROVE（直接通过） |

---

## 🔄 五阶段工作流程

```
[开始审计]
   │
   ▼
┌─────────────────────────────────────┐
│ 阶段1：依赖解析与对齐                │
│ (解析 PRD + 架构设计 + 数据库/API)   │
│ 脚本: parsers.py                    │
└─────────────────────────────────────┘
   │
   ▼
┌─────────────────────────────────────┐
│ 阶段2：规则链式校验                  │
│ (需求覆盖 → 数据库合规 → API契约     │
│  → 安全性能)                        │
│ 脚本: validators.py                 │
└─────────────────────────────────────┘
   │
   ▼
┌─────────────────────────────────────┐
│ 阶段3：问题分类与评级                │
│ (标记 BLOCKER / WARNING / SUGGESTION)│
│ 脚本: categorizer.py                │
└─────────────────────────────────────┘
   │
   ▼
┌─────────────────────────────────────┐
│ 阶段4：综合决策判定                  │
│ (BLOCKER≥1→REJECT                   │
│  无BLOCKER有WARNING→PASS_WITH_COND   │
│  仅有SUGGESTION→APPROVE)            │
│ 脚本: decision.py                   │
└─────────────────────────────────────┘
   │
   ▼
┌─────────────────────────────────────┐
│ 阶段5：结构化报告输出                │
│ (生成报告 → 抄送PM/Arch/Commander)   │
│ 脚本: reporter.py                   │
└─────────────────────────────────────┘
   │
   ▼
[审计结束]
```

---

## 📖 审查规则详解

### 规则1：需求闭环规则 (Requirement Closure Rule)

**标准**：架构设计必须完全覆盖 PRD 中的核心业务流。

**判定**：若发现 PRD 中定义的关键业务实体或操作，在架构图、数据库或 API 中没有对应的承载实体或接口，必须标记为 `BLOCKER`（阻断级问题）。

**检查要点**：
- PRD 业务实体 → 数据库表映射
- PRD 业务流程 → API 接口映射
- 检测冗余设计（无需求却设计了模块）

**实现脚本**：`validators.py` → `validate_requirement_coverage()`

### 规则2：数据库设计健康红线 (Database Health Rules)

#### 2.1 命名规范
- 表名和字段名必须统一为小写蛇形命名 `snake_case`
- 严禁使用数据库保留字

#### 2.2 主键与索引
- 每张表必须有明确的主键
- 高频查询条件字段（外键、关联字段、状态过滤字段）必须有索引

#### 2.3 数据完整性
- 核心业务表（订单、用户、资金表）必须有 `create_time` 和 `update_time` 字段

**实现脚本**：`validators.py` → `validate_database_health()`

### 规则3：API规范化与安全红线 (API Standards & Security Rules)

#### 3.1 REST 规范
- 获取数据：`GET`
- 创建数据：`POST`
- 更新数据：`PUT`/`PATCH`
- 删除数据：`DELETE`
- 严禁一律使用 `POST`

#### 3.2 幂等性与安全
- 修改/删除类接口必须考虑幂等性设计
- 敏感数据接口（密码重置、支付）必须包含安全防范机制描述

#### 3.3 异常处理
- API 必须定义统一的错误响应格式（包含 `code`, `message`, `data`）
- 不能仅依赖 HTTP 状态码

**实现脚本**：`validators.py` → `validate_api_contract()`

### 规则4：架构解耦与非功能性约束 (Decoupling & Non-functional Rules)

#### 4.1 无循环依赖
- 服务/模块之间严禁出现 A→B→A 的循环调用

#### 4.2 非功能性考量
- 高频访问 API 必须提及缓存机制（如 Redis）或流控机制

**实现脚本**：`validators.py` → `validate_security_and_performance()`

---

## 🔧 执行步骤详解

### Step 1：依赖解析与对齐 (Input Parsing & Alignment)

**动作**：加载上游 `pm` 的需求文档与 `arch` 提交的技术成果物。

**目标**：将抽象的技术设计图、DML/DDL 脚本、OpenAPI 描述文件等转化为内部结构化表示进行比对。

**脚本**：`parsers.py` → `parse_inputs()`

**解析输出结构**：

```python
{
    "prd": {"requirements": [...], "entities": [...], "flows": [...]},
    "arch": {"modules": [...], "dependencies": [...], "tech_stack": "..."},
    "api": {"endpoints": [...], "schemas": [...]},
    "db": {"tables": [...], "indexes": [...], "constraints": [...]}
}
```

### Step 2：规则链式校验 (Chained Validation)

将设计物送入规则校验引擎，依次执行四个校验器：

| 校验器 | 脚本函数 | 检查内容 |
|--------|---------|---------|
| 需求覆盖校验器 | `validate_requirement_coverage()` | 业务实体→表映射、业务流程→API映射、冗余设计检测 |
| 数据库合规校验器 | `validate_database_health()` | 命名规范、主键索引、时间字段 |
| API 契约校验器 | `validate_api_contract()` | REST规范、幂等性、安全机制、错误响应、分页 |
| 安全性能校验器 | `validate_security_and_performance()` | 循环依赖、深度依赖链、缓存/流控 |

**统一入口**：`validators.py` → `run_all_validators()`

### Step 3：问题分类与评级 (Categorization & Severity Rating)

对校验出的问题进行级别判定。

**脚本**：`categorizer.py` → `categorize_issues()`

**输出结构**：

```python
{
    "blocker": [...],      # BLOCKER级问题列表
    "warning": [...],      # WARNING级问题列表
    "suggestion": [...],   # SUGGESTION级问题列表
    "summary": {
        "total": n,        # 总问题数
        "blocker": n,      # BLOCKER数
        "warning": n,      # WARNING数
        "suggestion": n    # SUGGESTION数
    }
}
```

### Step 4：综合决策判定 (Consensus Decision)

根据问题统计做出判定：

| 条件 | 结果 | 下一步 |
|------|------|--------|
| BLOCKER ≥ 1 | `REJECT` | 通知 Arch 修复后重新提交 |
| 无 BLOCKER，WARNING ≥ 1 | `PASS_WITH_CONDITIONS` | 开发前修正 WARNING |
| 仅有 SUGGESTION 或无问题 | `APPROVE` | 直接进入开发阶段 |

**脚本**：`decision.py` → `make_decision()`

### Step 5：结构化报告输出 (Audit Report Export)

生成架构设计审计报告，包含以下核心内容：

**脚本**：`reporter.py` → `generate_audit_report()`

**报告结构**：
1. **审计概述**：任务ID、执行时间、审计结果统计
2. **问题详情清单**：按 BLOCKER/WARNING/SUGGESTION 分级列出
3. **追溯矩阵**：PRD需求→架构模块→API接口→数据库表的映射关系
4. **结论**：判定结果和下一步建议

**批准记录**：`reporter.py` → `generate_approval_record()`

---

## ✅ 验证步骤

执行本技能后，请按以下清单验证审计完整性：

1. [ ] PRD、ARCH_SPEC、OpenAPI、DDL 已正确解析
2. [ ] 需求覆盖校验器已执行（业务实体和流程映射检查）
3. [ ] 数据库合规校验器已执行（命名、主键、索引、时间字段）
4. [ ] API 契约校验器已执行（REST 规范、幂等性、异常处理）
5. [ ] 安全与性能校验器已执行（循环依赖、缓存/流控）
6. [ ] 问题已正确分类为 BLOCKER/WARNING/SUGGESTION
7. [ ] 综合决策判定逻辑正确
8. [ ] 审计报告包含追溯矩阵和问题详情
9. [ ] 报告已抄送给 PM、Arch、Commander

---

## ⚠️ 常见陷阱

| 陷阱 | 描述 | 规避方法 |
|------|------|---------|
| 需求遗漏 | PRD 业务实体在数据库/API 中无对应设计 | 建立需求-设计映射矩阵 |
| 命名不规范 | 使用驼峰命名而非蛇形命名 | 强制 `snake_case` 规则 |
| 缺少主键 | 核心业务表未定义主键 | 数据库校验器强制检查 |
| REST 规范违反 | 查询接口使用 POST | API 校验器检测并警告 |
| 循环依赖 | 模块间存在 A→B→A | 依赖图遍历检测 |
| 安全隐患 | 敏感接口缺少安全机制 | 敏感关键词匹配检测 |
| 缺少错误响应 | API 未定义统一错误格式 | 检查响应结构完整性 |
| 性能考虑不足 | 高频 API 无缓存/流控 | 高频关键词匹配检测 |

---

## 📄 ARCH_DESIGN_AUDIT_APPROVAL_RECORD 格式

```json
{
  "artifact": "ARCH_DESIGN_AUDIT_REPORT",
  "name": "{项目名称} 架构设计审计报告",
  "version": "v{version}",
  "hash": "{内容哈希}",
  "status": "{APPROVE / PASS_WITH_CONDITIONS / REJECT}",
  "workflow_mode": "{L2 / L3}",
  "approvals": [
    {
      "role": "reviewer-arch-design-audit",
      "result": "{APPROVED / CONDITIONAL_APPROVED / REJECTED}",
      "timestamp": "{ISO8601}",
      "conditions": []
    }
  ],
  "issue_summary": {
    "total": {n},
    "blocker": {n},
    "warning": {n},
    "suggestion": {n}
  },
  "deliverable_allowed": {true/false},
  "prd_ref": "PRD@{hash}",
  "arch_spec_ref": "ARCH_SPEC@{hash}",
  "openapi_ref": "OPENAPI@{hash}",
  "ddl_ref": "DDL@{hash}",
  "timestamp": "{ISO8601}"
}
```

---

## 🔗 与其他 Skill 的关系

| Skill | 职责 | 本 Skill 与它的关系 |
|-------|------|-------------------|
| `reviewer-code-review` | 审查代码实现质量 | 本 Skill 是代码审查的前置步骤 |
| `reviewer-arch-consistency` | 检查代码实现与设计的一致性 | 本 Skill 审计设计本身 |
| `reviewer-security-audit` | 安全专项审计 | 本 Skill 包含基础安全检查 |

**调用顺序**：`reviewer-arch-design-audit` → `reviewer-code-review`（含 `reviewer-arch-consistency` 和 `reviewer-security-audit`）
