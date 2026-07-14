# ECOS 下阶段工作计划 — 基于场景测试报告

> **PMO**: ECOS-PMO | **日期**: 2026-06-25  
> **输入**: Phase3-场景测试报告-20260625 (DIKW方法论合规度65%)

---

## 一、现状评估

```
DIKW数据层: ████████████████████ 100% (3目标/5实体/44对象/8规则)
  API层:    ████████            40%  (仅 K+I 层可用)
  前端:     ████████            40%  (用户Chrome已验证登录)
```

**核心差距**: 方法论定义的五步法中，**W(目标)/D(质量)/M(监控)** 三层的 API 端点未实现，导致端到端业务流程无法走通。

---

## 二、Phase 4: 方法论闭环 (目标 2 周)

> **主题**: 补齐 DIKW 五步法的 W→D→M 三层 API + 前端页面，实现方法论完整闭环

### Sprint 4.1: World Model API 补齐 (3天)

| # | 任务 | 产出 | 负责 |
|---|------|------|:--:|
| 4.1.1 | 修复 WorldModelController 路径映射 | GET/POST `/api/v1/worldmodel/goals` 返回 200 | BE |
| 4.1.2 | 创建 Goal CRUD 端点 | GET/POST/PUT/DELETE goals + scenarios | BE |
| 4.1.3 | 创建 CausalLink API | GET/POST `/api/v1/worldmodel/causal-links` | BE |
| 4.1.4 | WorldModel 前端页面数据对接 | 战略目标页读取真实 API 数据 | FE |
| 4.1.5 | 编译 + 回归 + curl 验证 | 3 endpoints 全部 200 | QA |

### Sprint 4.2: Data Quality API 补齐 (2天)

| # | 任务 | 产出 | 负责 |
|---|------|------|:--:|
| 4.2.1 | 创建 DqDashboardController | GET `/api/dq/rules` + `/api/dq/issues` | BE |
| 4.2.2 | DQ规则 CRUD | POST/PUT 规则，问题状态流转 | BE |
| 4.2.3 | 前端 DQ Dashboard 数据对接 | 质量仪表盘页读取真实 API | FE |
| 4.2.4 | 编译 + 回归验证 | 2 endpoints 全部 200 | QA |

### Sprint 4.3: Monitoring API 补齐 (2天)

| # | 任务 | 产出 | 负责 |
|---|------|------|:--:|
| 4.3.1 | 创建 MonitoringController | GET `/api/monitor` + `/api/alerts` | BE |
| 4.3.2 | 告警规则 CRUD | GET/POST `/api/alerts/rules` | BE |
| 4.3.3 | 前端监控中心数据对接 | 监控页读取真实告警 + 系统状态 | FE |
| 4.3.4 | 编译 + 回归验证 | 2 endpoints 全部 200 | QA |

### Sprint 4.4: 端到端方法论验证 (3天)

| # | 任务 | 产出 | 负责 |
|---|------|------|:--:|
| 4.4.1 | 五步法全流程 E2E 测试 | 目标→实体→对象→质量→监控 全部 API 200 | QA |
| 4.4.2 | 信科场景实战演练 | 按方法论实操: 创建设备故障响应目标→建模→录入→配置DQ→查看监控 | QA + PM |
| 4.4.3 | 江粮场景数据准备 | 基于江粮需求文档，预置测试数据 | PM + BE |
| 4.4.4 | 全量回归 (50+ endpoints) | P0/P1/P2 修复后全端点 200 | QA |
| 4.4.5 | 方法论合规度重新评分 | 目标: DIKW 100% + API 100% | PMO |

---

## 三、并行工作流

```
Week 1:  Sprint 4.1 (WM API) ─┐
         Sprint 4.2 (DQ API) ─┤ 并行
         Sprint 4.3 (MON API) ─┘
         
Week 2:  Sprint 4.4 (E2E验证 + 场景演练)
```

---

## 四、Hermes 集成 Phase 1 (可穿插)

| # | 任务 | 周期 | 负责 |
|---|------|:--:|:--:|
| H1 | 创建 HermesLLMClient implements LLMClient | 3天 | ARCH + BE |
| H2 | AgentRuntime 注入真实 LLM 后端 | 2天 | BE |
| H3 | 废弃 MockLLMClient | 1天 | BE |

> Hermes 集成与 Phase 4 可独立并行，互不阻塞。

---

## 五、里程碑

| 里程碑 | 日期 | 通过标准 |
|--------|------|---------|
| M1: WM/DQ/MON API 可用 | +5天 | 6 endpoints 全部 200 |
| M2: 前端全页面对接 | +8天 | 用户 Chrome 验证 5步法全部页面 |
| M3: 方法论闭环 | +10天 | DIKW API 100% + E2E 测试通过 |
| M4: Hermes 真实 LLM | +10天 | AgentRuntime 调用真实 LLM 返回有效结果 |

---

## 六、资源分配

| Agent | Phase 4 任务 | 预计工时 |
|-------|-------------|:--:|
| ⚙️ ECOS-BE | 3个Controller + CRUD (4.1-4.3) | 28h |
| 🎨 ECOS-FE | 3个页面数据对接 (4.1-4.3) | 16h |
| 🧪 ECOS-QA | E2E测试 + 回归 (4.4) | 12h |
| 🏗️ ECOS-ARCH | Hermes 集成 Phase 1 | 16h |
| 📋 ECOS-PM | 江粮场景数据准备 | 4h |
| 🎯 ECOS-PMO | 调度 + 方法论合规度评分 | 4h |
| **合计** | | **~80h (~2周)** |
