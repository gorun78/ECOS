# ECOS 下一阶段规划 — 系统审视合成报告

> PMO 合成日期: 2026-06-16  
> 基于: ECOS-PM / ECOS-ARCH / ECOS-BE / ECOS-FE / ECOS-QA 五份审视报告  
> 方法: 跨角色交叉分析，按「影响范围 × 修复成本」优先级矩阵排列

---

## 0. 执行摘要

ECOS P0-P1 共 11 个模块已建成，但审视发现三大类问题：

| 类别 | 严重度 | 数量 | 典型问题 |
|------|--------|------|----------|
| 🔴 **致命缺陷** | 阻塞验证/生产 | 3 | SQL注入、硬编码凭据、无签名Token |
| 🟡 **架构债务** | 阻碍扩展 | 8 | API风格分裂4种、内存存储≈60%、零CI/CD |
| 🟢 **体验/质量** | 影响交付 | 12 | 45%页面Mock数据、零测试覆盖、样式分裂3种 |

**下一阶段建议：分三波（4-6周）**
- **波次1**（第1周）：止血 — 安全 + 部署 + CI
- **波次2**（第2-3周）：骨架 — API统一 + DB持久化 + 测试框架
- **波次3**（第4-6周）：补全 — 真实数据替换Mock + 前端重构 + 集成测试

---

## 1. 跨角色共识问题（2+ Agent 同时指出）

| 问题 | 指出者 | 影响模块 | 共识度 |
|------|--------|----------|--------|
| API 路径风格不统一 | ARCH+BE+PM | 全部后端 | 🔴 4/5 |
| Mock 数据依赖严重 | PM+FE+QA | COG/Pipeline/Monitoring等 | 🔴 3/5 |
| 零单元测试/集成测试 | BE+QA+ARCH | 全部模块 | 🔴 3/5 |
| 无 CI/CD 流水线 | QA+ARCH+BE | DevOps | 🔴 3/5 |
| 前端样式方案分裂 | FE+PM | WorldModel/DataQuality/Glossary | 🟡 2/5 |
| 后端内存存储（重启丢失） | BE+ARCH | Ontology/Workflow/DQ/WorldModel | 🟡 2/5 |
| 数据库选型偏差（设计PG→实际MySQL） | ARCH+BE | 全部后端 | 🟡 2/5 |
| Agent Builder 缺失（只有聊天界面） | PM+FE | Agent Studio | 🟡 2/5 |
| World Model 后端空壳 | PM+BE+ARCH | Goals/Scenarios API | 🔴 3/5 |

---

## 2. P0 — 致命缺陷（必须在本周修复）

| 优先级 | 问题 | Agent | 修复方案 | 估计 |
|--------|------|-------|----------|------|
| **P0-01** | 🔥 SQL注入 — ObjectController 拼接列名 | BE | 白名单映射 + 参数化查询 | 0.5d |
| **P0-02** | 🔥 硬编码凭据 admin/admin123 | BE | application.yml 环境变量 + SSO 准备 | 0.5d |
| **P0-03** | 🔥 Token 无签名 (UUID) | BE | JWT + access_token + refresh_token | 1d |
| **P0-04** | 🔥 后端可部署（当前阻塞） | BE | 修复部署BUG-005，启动脚本 | 0.5d |
| **P0-05** | 🔥 CI/CD 基础流水线 | DEV+QA | GitHub Actions: 编译→测试→报告 | 2d |
| **P0-06** | 🔥 前端测试框架初始化 | FE+QA | Vitest + React Testing Library | 1d |

**总计 P0 工作量：~5.5 人天 → 1 周（1人全职）**

---

## 3. P1 — 架构骨架（第2-3周）

| 优先级 | 问题 | Agent | 修复方案 | 估计 |
|--------|------|-------|----------|------|
| **P1-01** | API 路径统一（4种→1种） | BE(+FE) | 统一 /api/v1/{domain}，网关做重写兼容 | 2d |
| **P1-02** | 内存存储→数据库持久化 | BE | Ontology/Workflow/DQ/WorldModel 迁移到 PostgreSQL | 3d |
| **P1-03** | 统一响应格式 + 全局异常处理 | BE | ApiResponse 统一 + @RestControllerAdvice | 1d |
| **P1-04** | DTO 替代 Map<String,Object> | BE | 每个 Controller 定义 Request/Response DTO | 3d |
| **P1-05** | 前端 API 层合并 | FE | 合并6个自建 apiFetch 到中央 api.ts | 1d |
| **P1-06** | 前端通用组件库初建 | FE | DataTable/Modal/LoadingSkeleton/ErrorBoundary/PageHeader | 2d |
| **P1-07** | 核心模块单元测试补全 | BE+QA | sysman 核心 Service 层 JUnit 5 + Mockito | 3d |
| **P1-08** | Flyway DB Schema 管理 | BE | 创建初始化迁移脚本 | 1d |
| **P1-09** | Agent Builder 后端 API | BE(+FE) | AgentConfig/Tool/Prompt 版本管理 API | 3d |

**总计 P1 工作量：~19 人天 → 2-3 周（前后端并行）**

---

## 4. P2 — 体验与能力补全（第4-6周）

| 优先级 | 问题 | Agent | 修复方案 | 估计 |
|--------|------|-------|----------|------|
| **P2-01** | World Model 后端真实化 | BE | Goals/CausalLinks/Scenarios API 真实实现 | 3d |
| **P2-02** | COG (CognitiveOS) 真实数据 | BE(+FE) | 替换 Mock 为真实 API 数据 | 2d |
| **P2-03** | Pipeline Builder 真实化 | BE(+FE) | DAG 真实 API + 调度执行 | 3d |
| **P2-04** | Agent Studio 升级为 Builder | FE(+BE) | 可视化创建/配置/测试 Agent（配合 P1-09） | 4d |
| **P2-05** | 前端样式统一 | FE | WorldModelViewer/DataQualityDashboard 接入主题 | 1d |
| **P2-06** | 数据库级分页替代内存分页 | BE | 全表搜索优化，OFFSET/LIMIT + GIN索引 | 2d |
| **P2-07** | E2E 测试（核心流程） | QA+FE | Playwright: 登录→看板→Agent执行 | 3d |
| **P2-08** | 集成测试 (Testcontainers) | QA+BE | Database/Cache 集成测试 | 3d |
| **P2-09** | Monitoring 真实数据 | BE(+FE) | 真实系统指标采集/展示 | 2d |
| **P2-10** | Audit 全局接入 | BE | 所有 CUD 操作写入 ecos_audit_log | 2d |

**总计 P2 工作量：~25 人天 → 2-3 周（前后端+QA并行）**

---

## 5. 总体路线图

```
周1          周2          周3          周4          周5          周6
├─P0 止血────┤├─P1 骨架──────────┤├─P2 补全────────────────────────┤
│             │                   │                                  │
│ SQL注入     │ API路径统一       │ World Model 真实化               │
│ 硬编码凭据  │ DB持久化         │ COG 真实数据                    │
│ JWT Token   │ DTO + 异常处理   │ Pipeline 真实化                 │
│ 部署修复    │ 前端API合并       │ Agent Studio → Builder          │
│ CI/CD       │ 通用组件库        │ 前端样式统一                    │
│ 测试框架    │ 单元测试          │ 数据库分页优化                  │
│             │ Flyway Schema     │ E2E + 集成测试                  │
│             │ Agent API         │ Monitoring 真实化               │
│             │                   │ Audit 全局接入                  │
└─────────────┴───────────────────┴─────────────────────────────────┘
                        ↓
             下一阶段目标：ECOS P1.5 "Production Ready"
```

---

## 6. 里程碑

| 里程碑 | 目标日期 | 关键交付物 |
|--------|----------|------------|
| M0.1: 安全修复完成 | 第1周末 | 🟢 无SQL注入/无硬编码凭据/JWT生效 |
| M0.2: CI/CD 就绪 | 第1周末 | 🟢 commit→编译→单元测试→报告 |
| M0.3: 后端可部署运行 | 第1周末 | 🟢 所有服务可正常启动 |
| M1.1: API 统一 | 第3周末 | 🟢 全部 /api/v1/{domain} 风格 |
| M1.2: DB 持久化完成 | 第3周末 | 🟢 无内存存储模块 |
| M1.3: 测试框架就绪 | 第3周末 | 🟢 FE:Vitest + BE:JUnit5 + CI集成 |
| M2.1: 前端重构完成 | 第6周末 | 🟢 统一API层/通用组件库/统一主题 |
| M2.2: 真实数据替换完成 | 第6周末 | 🟢 核心页面数据真实API，无 Mock |
| M2.3: 核心测试覆盖 | 第6周末 | 🟢 Unit≥50% + 集成测试 + E2E主流程 |

---

## 7. 风险

| 风险 | 影响 | 缓解 |
|------|------|------|
| P0 止血期间影响正常功能 | ⚪ 中 | 每次变更加基础验证 |
| 数据库迁移（MySQL→PG）未纳入本规划 | 🟡 大 | 先做应用层适配，DB迁移作为独立项目 |
| World Model / COG 真实化难度超预期 | 🟡 大 | 先实现基础 CRUD，复杂推理延后 |
| 前端重构与功能开发冲突 | ⚪ 中 | 先建组件库/API层，页面重构在组件就绪后 |
