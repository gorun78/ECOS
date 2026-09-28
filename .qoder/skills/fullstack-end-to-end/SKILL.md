---
name: fullstack-end-to-end
description: "Fullstack 端到端开发 Skill：从需求文档找到对应描述和原型页面，保存追溯记录，委托 backend-builder 开发后端(Java)、vue3-frontend-builder 开发前端(Vue3)，生成前后端单元测试和集成测试，验证交付门禁。当用户提交需求进行端到端开发时触发。"
version: 1.0.0
author: Hermes Agent (AI-Native Software Factory)
license: MIT
platforms: [linux, macos, windows]
metadata:
  hermes:
    tags: [fullstack, end-to-end, requirement-matching, traceability, integration-testing, delivery-gate]
    related_skills: [vue3-frontend-builder, backend-builder, fullstack-impl]
    artifact_type: SOURCE_PATCH
    workflow_modes: [L1, L2, L3]
---

# Fullstack End-to-End Skill

## 概览

本技能实现完整的端到端开发流程：从用户提交的需求出发，通过需求匹配引擎找到 PRD 文档中的描述和原型系统中的对应页面，保存追溯记录，然后委托专业技能进行前后端开发，并生成集成测试验证接口一致性，最后通过交付门禁确认质量。

**核心差异化能力：需求到实现的完整追溯链**

不同于单一的前端或后端开发技能，本技能提供：
1. **需求匹配引擎** — 自动从 PRD 提取需求条目，与原型页面进行相似度匹配
2. **追溯记录系统** — 保存需求与页面的映射关系，支持审批流程
3. **技能委托机制** — 后端开发委托给 `backend-builder`，前端开发委托给 `vue3-frontend-builder`
4. **集成测试生成** — 自动生成前后端集成测试和 API 契约测试
5. **交付门禁验证** — 四重大门确保交付质量

## 核心原则

1. **证据驱动的需求转化** — 每个需求条目必须可追溯到 PRD 文档和原型页面
2. **委托优先** — 前后端开发委托给专业技能，不重复造轮子
3. **追溯优先** — 先建立需求追溯记录，再开始开发
4. **测试保障** — 集成测试覆盖所有 API 端点，契约测试验证前后端一致性
5. **门禁控制** — 四重大门确保交付质量

## 强制流程

| 阶段 | 必须产出 | 停止条件 | 执行脚本 |
|---|---|---|---|
| 0. 需求匹配与追溯 | 需求匹配结果、追溯记录、追溯矩阵 | 追溯记录已生成，匹配率 ≥ 80% | `scripts/requirement_matcher.py` + `scripts/traceability_recorder.py` |
| 1. 后端开发 | 后端代码（Controller/Service/Repository/Entity/DTO） | backend-builder 报告 DONE | 委托 `backend-builder` |
| 2. 前端开发 | 前端代码（Vue3 组件/页面/Pinia/Vue Router） | vue3-frontend-builder 报告 DONE | 委托 `vue3-frontend-builder` |
| 3. 集成测试生成 | 前端集成测试、后端集成测试、API 契约测试 | 测试代码已生成 | `scripts/integration_test_generator.py` |
| 4. 交付门禁验证 | 交付报告、交付许可判定 | 四重大门全部通过 | `scripts/delivery_gate.py` |

## 📂 脚本结构

本技能的核心逻辑已分离到 `scripts/` 目录中，便于独立维护和测试：

```
scripts/
├── __init__.py                    # 包初始化，导出所有模块
├── main.py                        # 端到端工作流主入口（命令行运行）
├── utils.py                       # 工具函数（哈希计算、文件操作、字符串处理）
├── requirement_matcher.py         # 需求匹配器（PRD解析、原型扫描、相似度匹配）
├── traceability_recorder.py       # 追溯记录器（追溯记录创建、保存、审批）
├── integration_test_generator.py  # 集成测试生成器（前端/Vitest、后端/JUnit、契约测试）
└── delivery_gate.py               # 交付门禁（前端门禁、后端门禁、测试门禁、追溯门禁）
```

### 脚本调用方式

**命令行方式**：
```bash
python scripts/main.py \
  --prd path/to/prd.md \
  --prototype path/to/prototype \
  --openapi path/to/openapi.json \
  --backend-output ../backend \
  --frontend-output ../frontend \
  --output ./output
```

**Python API 方式**：
```python
from scripts import match_requirements, record_traceability
from scripts import generate_integration_tests, run_delivery_gates

# 阶段 0: 需求匹配与追溯
match_result = match_requirements(prd_path, prototype_path)
trace_result = record_traceability(match_result)

# 阶段 3: 集成测试生成
tests_result = generate_integration_tests(api_endpoints)

# 阶段 4: 交付门禁验证
delivery_result = run_delivery_gates(frontend_path, backend_path, tests_path, trace_path)
```

## 第零步：需求匹配与追溯记录

### 第零步硬性要求

1. 必须读取 PRD 文档和原型系统路径
2. 必须从 PRD 中提取需求条目（按章节分割）
3. 必须扫描原型系统中的页面列表
4. 必须计算需求与页面的相似度（Jaccard 系数）
5. 必须生成追溯记录并保存
6. 必须输出未匹配的需求和页面清单

### 第零步工作顺序

1. **加载 PRD** — 读取 PRD 文档内容
2. **提取需求** — 按章节分割，提取功能点 ID 和页面名称
3. **加载原型** — 扫描原型目录，识别页面文件
4. **相似度匹配** — 计算每个需求与页面的相似度
5. **生成记录** — 创建追溯记录，包含置信度评估
6. **保存记录** — 保存追溯记录到 `traceability/` 目录

### 第零步确认输出

- 需求匹配率（目标 ≥ 80%）
- 追溯记录文件路径
- 未匹配需求清单
- 未匹配页面清单
- 追溯矩阵

## 第一步：后端开发（委托）

### 第一步硬性要求

1. 必须委托给 `backend-builder` skill
2. 必须使用 OpenAPI 规范作为接口契约
3. 必须遵循 Spring Boot 3.2.3 + Java 17 技术栈
4. 必须使用 KingbaseES 数据库 + Flyway 迁移
5. 必须生成完整的后端代码结构（Controller/Service/Repository/Entity/DTO）
6. 必须包含单元测试

### 第一步技术栈

| 类别 | 技术 | 版本 |
|---|---|---|
| 框架 | Spring Boot | 3.2.3 |
| 语言 | Java | 17 |
| 数据库 | KingbaseES | 8.6.0 |
| ORM | Spring Data JPA | - |
| 迁移 | Flyway | - |
| HTTP | OpenFeign | - |
| 文档 | SpringDoc OpenAPI | 2.3.0 |
| 测试 | JUnit 5 | - |

## 第二步：前端开发（委托）

### 第二步硬性要求

1. 必须委托给 `vue3-frontend-builder` skill
2. 必须遵循 Vue3 + TypeScript + Tailwind CSS 技术栈
3. 必须实现组件抽象分析（原子/分子/有机/页面级）
4. 必须生成完整的前端代码结构
5. 必须包含单元测试（Vitest）
6. 必须通过设计还原度门禁

### 第二步技术栈

| 类别 | 技术 | 版本 |
|---|---|---|
| 框架 | Vue | 3.4+ |
| 语言 | TypeScript | 5.0+ |
| 样式 | Tailwind CSS | 3.4+ |
| 状态 | Pinia | - |
| 路由 | Vue Router | 4 |
| 构建 | Vite | 5 |
| 测试 | Vitest | - |

## 第三步：集成测试生成

### 第三步硬性要求

1. 必须为每个 API 端点生成前端集成测试（Vitest）
2. 必须为每个 API 端点生成后端集成测试（JUnit 5）
3. 必须生成 API 契约测试（验证前后端接口一致性）
4. 测试代码必须覆盖成功场景和异常场景

### 第三步测试类型

| 测试类型 | 技术 | 目的 |
|---|---|---|
| 前端集成测试 | Vitest | 验证前端 API 调用逻辑 |
| 后端集成测试 | JUnit 5 + MockMvc | 验证后端 Controller 行为 |
| API 契约测试 | Vitest + Axios | 验证前后端接口一致性 |

## 第四步：交付门禁验证

### 第四步硬性要求

1. 必须运行四重大门检查
2. 任一门禁未通过不得交付
3. 必须生成交付报告（JSON + Markdown）

### 四重大门

| 门禁 | 检查项 | 硬门槛 |
|---|---|---|
| **FRONTEND_DELIVERY** | 前端目录结构、关键文件、单元测试 | 所有文件存在，测试数量 > 0 |
| **BACKEND_DELIVERY** | 后端目录结构、关键文件、单元测试 | 所有文件存在，测试数量 > 0 |
| **INTEGRATION_TESTS** | 前端集成测试、后端集成测试、契约测试 | 三类测试文件均存在 |
| **TRACEABILITY** | 追溯记录、批准状态 | 所有记录已批准 |

## 交付标准

所有阶段完成后必须满足：

1. **需求追溯率 ≥ 80%** — 追溯记录中的匹配率
2. **后端代码完整** — Controller/Service/Repository/Entity/DTO 齐全
3. **前端代码完整** — 组件/页面/Pinia/Vue Router 齐全
4. **单元测试覆盖** — 前后端核心逻辑有测试
5. **集成测试覆盖** — 所有 API 端点有集成测试
6. **契约测试通过** — 前后端接口一致
7. **门禁全部通过** — 四重大门均为 PASS
8. **deliverable_allowed = true** — 交付许可判定

## 技术栈

### 后端（委托 backend-builder）

| 类别 | 技术 | 版本 |
|---|---|---|
| 框架 | Spring Boot | 3.2.3 |
| 语言 | Java | 17 |
| 数据库 | KingbaseES | 8.6.0 |
| ORM | Spring Data JPA | - |
| 迁移 | Flyway | - |
| HTTP | OpenFeign | - |
| 文档 | SpringDoc OpenAPI | 2.3.0 |
| 测试 | JUnit 5 | - |

### 前端（委托 vue3-frontend-builder）

| 类别 | 技术 | 版本 |
|---|---|---|
| 框架 | Vue | 3.4+ |
| 语言 | TypeScript | 5.0+ |
| 样式 | Tailwind CSS | 3.4+ |
| 状态 | Pinia | - |
| 路由 | Vue Router | 4 |
| 构建 | Vite | 5 |
| 测试 | Vitest | - |

### 技能内部

| 类别 | 技术 | 说明 |
|---|---|---|
| 语言 | Python 3 | 脚本实现语言 |
| 相似度 | Jaccard 系数 | 需求与页面匹配算法 |
| 数据格式 | JSON | 追溯记录格式 |

## 验证步骤

1. [ ] 阶段 0：需求匹配率 ≥ 80%
2. [ ] 阶段 0：追溯记录已生成并保存
3. [ ] 阶段 1：后端代码完整（Controller/Service/Repository/Entity/DTO）
4. [ ] 阶段 1：后端单元测试通过
5. [ ] 阶段 2：前端代码完整（组件/页面/Pinia/Vue Router）
6. [ ] 阶段 2：前端单元测试通过
7. [ ] 阶段 3：集成测试代码已生成
8. [ ] 阶段 3：API 契约测试已生成
9. [ ] 阶段 4：FRONTEND_DELIVERY 门禁通过
10. [ ] 阶段 4：BACKEND_DELIVERY 门禁通过
11. [ ] 阶段 4：INTEGRATION_TESTS 门禁通过
12. [ ] 阶段 4：TRACEABILITY 门禁通过
13. [ ] deliverable_allowed = true

## 常见陷阱

1. **跳过需求匹配**：直接进入开发，导致需求无法追溯
2. **匹配率过低**：未解决未匹配需求就进入开发
3. **重复造轮子**：在本技能中实现前后端开发，而不是委托给专业技能
4. **忽视集成测试**：只写单元测试，不验证前后端接口一致性
5. **跳过交付门禁**：未通过门禁就报告完成
6. **追溯记录未批准**：未获得批准就进入开发

## 输出制品

| 制品 | 路径 | 说明 |
|---|---|---|
| 需求匹配结果 | `output/traceability/match-result.json` | PRD 与原型的匹配结果 |
| 追溯记录 | `output/traceability/TRC_*.json` | 需求追溯记录（含审批状态） |
| 追溯矩阵 | 追溯记录中生成 | 需求-页面映射矩阵 |
| 前端集成测试 | `output/tests/frontend/*.spec.ts` | Vue3 集成测试 |
| 后端集成测试 | `output/tests/backend/*Test.java` | Java 集成测试 |
| API 契约测试 | `output/tests/contract/` | 接口契约测试 |
| 交付报告（JSON） | `output/delivery/delivery-report.json` | 交付门禁结果 |
| 交付报告（MD） | `output/delivery/delivery-report.md` | 交付报告文档 |
| 工作流报告 | `output/E2E_*_report.json` | 完整工作流报告 |

## 与其他技能的关系

```
fullstack-end-to-end (本技能)
├── 委托 → backend-builder (后端开发)
├── 委托 → vue3-frontend-builder (前端开发)
└── 关联 → fullstack-impl (全栈工作流编排)
```

**职责边界**：
- `fullstack-end-to-end`：端到端流程编排、需求追溯、集成测试、交付门禁
- `backend-builder`：后端代码实现、单元测试
- `vue3-frontend-builder`：前端代码实现、组件抽象、单元测试
- `fullstack-impl`：上游制品校验、前后端并行编排
