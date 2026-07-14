# ECOS 项目总看板

> 最后更新：2026-06-16 | PMO: ECOS-PMO | 当前阶段：Phase 0（基础建设）
> 📋 [会议纪要](../06-会议纪要/2026-06-16-全体项目启动会.md)

---

## 📋 Backlog（待排期）

| ID | 标题 | 来源 | 优先级 | 预估 |
|----|------|------|--------|------|
| P0-4 | World Model 后端（降级至P1） | MVP v3 | P1 | 10d |

---

## 🗂 TODO（就绪可开工）

| ID | 标题 | 负责人 | 预估 | 依赖 |
|----|------|--------|------|------|
| TASK-001 | IAM: AuthController → JWT 统一 | ECOS-BE | 3d | — |
| TASK-002 | 引入 ESLint + TypeScript strict | ECOS-FE | 1d | — |
| TASK-003 | 统一前端 API 调用层（5种→1种） | ECOS-FE | 1d | — |
| TASK-004 | 建立后端测试框架（JUnit5+Mockito） | ECOS-QA | 2d | — |
| TASK-005 | 建立前端测试框架（Vitest+Playwright） | ECOS-QA | 1d | — |
| TASK-006 | Object Runtime 后端（JSONB三表+动态Schema） | ECOS-BE | 5d | TASK-001 |
| TASK-007 | Ontology Designer 后端持久化迁移 | ECOS-BE | 5d | TASK-001 |
| TASK-008 | Workflow Designer 后端持久化 | ECOS-BE | 3d | TASK-001 |
| TASK-009 | Data Quality 后端 | ECOS-BE | 4d | TASK-006 |
| TASK-010 | Workflow Designer 前端（类型+布局修复） | ECOS-FE | 5d | — |
| TASK-011 | Ontology Designer 前端（React Flow迁移） | ECOS-FE | 7d | — |
| TASK-012 | Object Runtime 前端（关系图增强） | ECOS-FE | 3d | TASK-006 |
| TASK-013 | Data Quality 前端（Tailwind重写） | ECOS-FE | 3d | TASK-009 |
| TASK-014 | IAM 前端页面（用户/角色管理） | ECOS-FE | 2d | TASK-001 |

---

## 🔧 DOING（进行中）

| ID | 标题 | 负责人 | 开始 | 预估 | 备注 |
|----|------|--------|------|------|------|
| — | — | — | — | — | — |

---

## 👀 REVIEW（待审核）

| ID | 标题 | 审核人 | 提交时间 | 备注 |
|----|------|--------|----------|------|
| — | — | — | — | — |

---

## ✅ DONE（已完成）

### 📄 文档迁移（2026-06-16）
| ID | 标题 | 负责人 | 完成时间 |
|----|------|--------|----------|
| DOC-001 | 20个功能需求文档 → `01-产品设计/` | ECOS-PMO | 2026-06-16 |
| DOC-002 | 3版MVP研发计划 → `02-研发计划/` | ECOS-PMO | 2026-06-16 |
| DOC-003 | 18份系统设计文档 + 补充 → `03-系统设计/` | ECOS-PMO | 2026-06-16 |

### 📋 全项目启动会（2026-06-16）
| ID | 标题 | 负责人 | 完成时间 |
|----|------|--------|----------|
| MTG-001 | 5角色现状分析 + 下阶段计划制定 | ECOS-PMO | 2026-06-16 |
| MTG-002 | 决策：World Model 降级至 P1 | 全体 | 2026-06-16 |
| MTG-003 | 决策：JSONB三表架构 | 全体 | 2026-06-16 |

### 🆕 看板管理平台 V1.0（Phase K — 2026-06-16）
| ID | 标题 | 负责人 | 完成时间 | 状态 |
|----|------|--------|----------|:----:|
| PRD-021 | PRD + Excalidraw原型 | ECOS-PMO | 2026-06-16 | ✅ |
| ARCH-001 | HLD + LLD 架构设计 | ECOS-ARCH | 2026-06-16 | ✅ |
| FE-001 | 前端Vue3开发（5页面，0 errors） | ECOS-FE | 2026-06-16 | ✅ |
| **BUG-001** | **权限硬编码修复** | **ECOS-FE** | **2026-06-16** | **✅ 已修复** |
| BUG-005 | 🔴 后端代码持久化 | ECOS-BE | — | ⏳ t_99fd7603 |
| BUG-002/003/004 | 🟡 P1前端Bug修复 | ECOS-FE | — | ⏳ t_697a41aa |
| 测试文档 | 测试计划/用例/Bug报告/验收报告 | ECOS-QA | — | ⏳ t_7d627608 |

---

## 🚫 BLOCKED（阻塞中）

| ID | 标题 | 阻塞原因 | 谁来解 | 阻塞时间 |
|----|------|----------|--------|----------|
| — | — | — | — | — |

---

## 📊 健康度

| 指标 | 当前 | 目标 |
|------|------|------|
| 任务完成率 | 5/19 | >80% |
| Bug重开率 | — | <5% |
| QA一次通过率 | — | >70% |
| 逾期任务数 | 0 | 0 |

---

## 🗺️ 当前计划（Phase 0 ~ Phase 2）

### Phase 0 — 基础建设（Day 1-3）
- IAM JWT统一（BE 3d）
- ESLint + strict模式（FE 1d）
- 统一API调用层（FE 1d）
- 测试框架搭建（QA 3d）

### Phase 1 — 核心闭环（Day 4-15）
| 并行组A | 并行组B |
|---------|---------|
| Object Runtime 后端 (BE 5d) | Workflow Designer 后端 (BE 3d) |
| Ontology Designer 后端 (BE 5d) | Data Quality 后端 (BE 4d) |
| Ontology Designer 前端 (FE 7d) | Workflow Designer 前端 (FE 5d) |
| Object Runtime 前端 (FE 3d) | |

### Phase 2 — 收尾联调（Day 16-22）
- IAM 前端页面 (FE 2d)
- Data Quality 前端 (FE 3d)
- 全链路联调 + QA验收 (全体 3d)

---

## 🔗 快捷链接

- [PRD索引](./PRD-INDEX.md)
- [任务索引](./TASK-INDEX.md)
- [任务模板](./TASK-TEMPLATE.md)
- [MVP研发计划 v3](../02-研发计划/ECOS-MVP研发计划-v3.md)
- [全体启动会纪要](../06-会议纪要/2026-06-16-全体项目启动会.md)
