# Sprint 5 团队启动简报

> **日期**: 2026-06-26 | **PMO**: ECOS-PMO
> **来源**: `03-系统设计/ECOS-架构整改方案-20260626.md`
> **总看板**: `00-Kanban/ECOS-总看板.md`

---

## 零、本次 Sprint 核心原则

```
认知引擎（推理） ≠ Agent平台（执行）

认知引擎 = 自研规则引擎，零 LLM，轻量
Agent平台 = Hermes 保留，成熟 Agent 运行时

两层解耦：认知引擎产出 Plan → Hermes 执行 Plan
```

---

## 一、各成员评估 + 本轮注意事项

### 🏗️ ECOS-ARCH

**前期表现**: ⭐⭐⭐⭐
- Sprint 6/7 架构审计报告质量高，设计文档对标全面
- 善于发现设计-实现差距（DDD/Ontology/Event Driven 偏离）
- **待提升**: 有时过于理论化，需紧贴当前 Maven 模块实际结构落地

**本轮任务**: S5-0.3, S5-1.2, S5-4.1
**本轮要求**:
1. 接口契约必须先于代码 — S5-0.3 输出必须包含明确的 Java 接口定义 + JSON 示例
2. 不要设计不存在的模块 — 所有设计基于现有 `databridge-*` 模块
3. `ICognitiveEngineService` 输出 4 个方法签名即可，不需要设计文档套文档

### ⚙️ ECOS-BE

**前期表现**: ⭐⭐⭐
- Controller 开发扎实（DqDashboard / Monitor / WorldModel 均一次通过）
- Git 提交规范，Maven 模块路径正确
- **待提升**:
  - ⚠️ AuditLog-sql.xml 用了 `<properties>` 格式而非 iBATIS `<sqlMap>` — **iBATIS XML 必须用 `<sqlMap namespace>` 根元素 + `<select id>` 子元素，这是硬规范**
  - ⚠️ WorldModel POST 创建返回 500 — 未在提交前 curl 验证
  - ⚠️ Gateway 反复 SIGTERM — 未排查端口/资源冲突

**本轮任务**: S5-0.1, S5-0.2, S5-1.1~S5-1.4, S5-2.1~S5-2.3, S5-3.1~S5-3.3, S5-4.1
**本轮要求**:
1. **提交前必须 curl 验证** — 代码写完后跑 `curl` 确认 200 再交
2. **DDL 用 Flyway migration** — 不走手动 SQL，走 `V5.x__*.sql` 脚本
3. **新建模块先跑 `mvn install`** — 避免 classpath 找不到新模块
4. **iBATIS XML 规范重申**: 根元素 `<sqlMap namespace="X">`，子元素 `<select id="Y">`，参数用 `#[param]`

### 🎨 ECOS-FE

**前期表现**: ⭐⭐⭐
- 页面组件质量好（CognitiveOperatingSystem / SecurityAudit 等）
- API 对接修复快（DQ_BASE / MONITOR_BASE）
- **待提升**:
  - ⚠️ React.lazy 引入导致白屏 — **上线前必须在浏览器实际点击验证，不能只跑 tsc**
  - ⚠️ 数据映射不一致（config_key vs key）— 对接后端前先确认 API 返回格式

**本轮任务**: S5-1.5, S5-2.4, S5-3.4
**本轮要求**:
1. **加页面后立即在浏览器验证** — 不只是 `tsc` 和 `vite build`，打开浏览器点一下
2. **api.ts 新增函数严格按后端接口定义** — 等 ARCH 出契约（S5-1.2）后再写，禁止猜着写
3. **TaskCenter 页面参考现有 Monitoring 页面的数据表格模式**，复用组件

### 🧪 ECOS-QA

**前期表现**: ⭐⭐⭐⭐
- curl 驱动测试执行力强（Phase 4 27 个端点 96% 通过率）
- DIKW E2E 全链路验证完整
- **待提升**:
  - ⚠️ 部分 404 端点未在早期发现（DqDashboard/Monitor 是 PMO 发现的）
  - ⚠️ 测试报告偏重 API 回归，缺少前端页面点击验证

**本轮任务**: S5-2.5, S5-3.5, S5-4.2
**本轮要求**:
1. **每完成一个 BE 任务立即 curl** — 不等 Sprint 结束，当天写当天验
2. **前端回归加浏览器截图** — 不只是 `vite build`，要打开关键页面确认无白屏
3. **S5-4.2 全链路回归标准**: API ≥26/27 + tsc 0新增 + build成功 + 所有新页面可点击

### 📋 ECOS-PM

**前期表现**: ⭐⭐⭐
- PRD 和需求文档齐全
- **本轮任务**: S5-4.3（验收）
- **本轮要求**: 验收时从**产品可用性**角度审视 — 新页面能否独立完成业务流程？

---

## 二、协作流程（强制执行）

```
ARCH 出契约 → PMO 审核 → BE 实现 → FE 对接 → QA 验证 → PM 验收 → PMO 闭合
```

| 阶段 | 交接条件 | 禁止 |
|------|---------|------|
| ARCH→PMO | 接口定义 + JSON 示例 | 只给概念不给签名 |
| PMO→BE | 任务卡明确 + curl 预期结果 | 跳过审核直接写码 |
| BE→FE | 端点 200 + curl 通过 | 没验证就交给前端 |
| FE→QA | 页面可点击 + tsc 零新增 | 仅 build 不浏览器验证 |
| QA→PM | 测试报告 + 截图 | 少一个端点不报 |
| PM→PMO | 验收通过签字 | 走形式不看 |

---

## 三、禁止事项（Sprint 5 重申）

1. ❌ 禁止 `@RequestMapping` 不加路径别名 — 新端点必须同时支持 `/api/v1/ecos/*` 和 `/api/v1/*`
2. ❌ 禁止跳过 Flyway — DDL 必须走 migration 脚本
3. ❌ 禁止提交未 curl 验证的代码
4. ❌ 禁止前端对接时猜 API 格式 — 等 BE 出 curl 结果或 API 文档
5. ❌ 禁止标记 DONE 但未 commit — DONE = commit + curl + tsc

---

## 四、Sprint 5 任务总览

| 阶段 | 任务数 | BE | FE | QA | ARCH | PM |
|:--:|:--:|:--:|:--:|:--:|:--:|:--:|
| 5.0 退役清理 | 3 | 2 | — | — | 1 | — |
| 5.1 骨架 | 5 | 4 | 1 | — | 1 | — |
| 5.2 三推理器MVP | 6 | 4 | 1 | 1 | — | — |
| 5.3 任务中心 | 5 | 3 | 1 | 1 | — | — |
| 5.4 联调验收 | 4 | 1 | — | 1 | 1 | 1 |
| **合计** | **23** | **14** | **3** | **3** | **3** | **1** |

---

> **启动**: PMO 逐阶段下发任务卡，每个 Sprint 完成后再启动下一个。
> **第一棒**: ARCH → S5-0.3 接口契约 + BE → S5-0.1 老工程退役（并行启动）
