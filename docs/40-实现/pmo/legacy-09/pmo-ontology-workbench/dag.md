# 本体工作台完善 — 统一任务 DAG 与波次依赖图

> 来源: PM项目经理（ecos-pm）
> 日期: 2026-09-12
> 责任人: fullstack-implementer (ecos-be) / reviewer (ecos-fe) / qa (ecos-qa)
> 铁律: 继承 [ECOS 架构铁律](../../00-架构/ARCHITECTURE-RULES.md) v1.1 全部
> 项目根目录: D:\workspace\javaprojects\ECOS
> 当前分支: feature/pmo-43-fe-p0-infra-network（新派发改动从此分支拉取 feature/ontology-workbench-20260912）

---

## 一、20 个任务总览

**核心目标**：完善 ontology-engine（金·I 信息本体引擎，端口 18083，由 services/buszhi 聚合）+ 前端 #/ontology_workbench（ecos_frontend/src/main.tsx 第 128 行 OntologyWorkbenchLayoutStandalone），覆盖前端体验、后端架构铁律合规、前后端打通、契约工程化收口四个维度。

| Wave | PMO 指令 | 任务 | 阶段 | 依赖 |
|:--|:--|:--|:--|:--|
| A | PMO-01 | T1,T2,T3,T4,T5,T6 | 前端 | 无（可最先做） |
| B-1 | PMO-02 | T12,T13,T14 | 后端安全合规 | 无（骨架独立） |
| B-2a | PMO-03 | T15,T16,T17 | 后端持久化/DTO | T12（安全拦截器须先扩展） |
| B-2b | PMO-04 | T7,T8 | 后端新端点 | T12,T17（新端点过安全拦截器 + 落库约定） |
| C | PMO-05 | T9,T10,T11 | 前后端打通 | T7（T9函数），T8（T10部分），T17（T11依赖版本表） |
| D | PMO-06 | T18,T19,T20 | 契约工程化 | B 全部完成 |

---

## 二、文本版依赖 DAG

```
                    ┌─────────────────────────────────────────────────┐
                    │  Wave A · 前端体验修复 (PMO-01)                 │
                    │  T1 useOntologyData N+1→批量                    │
                    │  T2 收敛双 API 层 (api.ts→ontologyApi.ts)       │
                    │  T3 Export Modal 硬编码颜色→Theme tokens        │
                    │  T4 Sidebar 硬编码颜色→Theme tokens             │
                    │  T5 business-workbench i18n化 (~28处中文)       │
                    │  T6 死代码清理 (main.tsx未挂载引用+.bak2)        │
                    │  [独立验收: 4主题切换 + 中英切换 + network≤3请求] │
                    └─────────────────────────────────────────────────┘
                                  │
                                  │ (可与 Wave B 并行启动)
                                  ▼
                    ┌─────────────────────────────────────────────────┐
                    │  Wave B-1 · 后端安全合规 (PMO-02)               │
                    │  T12 🔴 安全集成 (OntologySecurityInterceptor)  │
                    │      + 全量接入 security-engine REST             │
                    │      + 修 row.clear() bug + 默认DENY + Kafka审计│
                    │  T13 AutoDiscoverService 跨引擎JDBC→REST        │
                    │  T14 OntologyKgSyncService 直连Neo4j→runtime-access│
                    └─────────────────────────────────────────────────┘
                                  │
                    ┌─────────────┴─────────────┐
                    ▼                           ▼
     ┌─────────────────────────┐   ┌─────────────────────────┐
     │ Wave B-2a · PMO-03     │   │ Wave B-2b · PMO-04      │
     │ T15 对象数据落PG        │   │ (T7/T8依赖T17落库约定)   │
     │ (ConcurrentHashMap→PG)  │   │                         │
     │ T16 Map→DTO/VO (27类)   │   │ T7 新增5类CRUD端点       │
     │ T17 逻辑删除+schema兜底  │   │ (action/interface/      │
     │ (deleteEntity级联→ARCHIVED)│ │  shared_property/       │
     └─────────────────────────┘   │  function/dataset)       │
                                  │ T8 域 CRUD 持久化          │
                                  └─────────────────────────┘
                                  │
                                  ▼
                    ┌─────────────────────────────────────────────────┐
                    │  Wave C · 前后端打通 (PMO-05)                    │
                    │  T9 Function TestTab 去 mock                    │
                    │     (依赖 T7 的 functions 端点)                  │
                    │  T10 导出任务闭环 (list/progress/download)      │
                    │     (依赖 T8 + export 端点)                     │
                    │  T11 版本 diff 联调 (v1/v2 快照对比)             │
                    │     (依赖 T17 的 version 表 + diff 端点)         │
                    └─────────────────────────────────────────────────┘
                                  │
                                  ▼
                    ┌─────────────────────────────────────────────────┐
                    │  Wave D · 契约工程化收口 (PMO-06)                 │
                    │  T18 收敛双路径 Controller (workflows 双副本)     │
                    │  T19 补齐 api-contract.md buszhi 路由表          │
                    │  T20 双套本体表收敛 + MIGRATE 脚本              │
                    └─────────────────────────────────────────────────┘
                                  │
                                  ▼
                    ┌─────────────────────────────────────────────────┐
                    │  终审 · Reviewer (ecos-fe)                       │
                    │  reviewer-arch-design-audit (全量架构合规)       │
                    │  reviewer-security-audit (敏感数据审计)          │
                    │  交付质量门禁判定 PASS/FAIL                      │
                    └─────────────────────────────────────────────────┘
```

**关键依赖说明**：

1. **T12 是 B-2a/B-2b 的安全底座**。OntologySecurityInterceptor 的 pointcut 扩展到本体定义 CRUD（OntologyController/OntologyDomainController）后，T15/T16/T17 改的实体/DTO/逻辑删除才会被拦截器覆盖到，T7/T8 新端点新增时也需过同一拦截器。
2. **T17 是 T7 的落库约定源**。T17 补齐 `CREATE TABLE IF NOT EXISTS ecosystem_ontology_*` 表（ecos_ontology_rule/version/ecos_domain/ecos_ontology 主表），T7 新增 5 类端点落 PG 表时复用同一套 schema（带 create_time/update_time/create_by/update_by/is_deleted）。
3. **T9/T10/T11 是 C 的前后端打通**，依赖 B 对应端点完成（T7→T9 函数端点；T8+export→T10；T17 version 表+diff 端点→T11）。
4. **T18-T20 是 D 的契约工程化**，依赖 B 全部完成（T18 删 ontology-engine-impl 双副本；T19 补路由表反映实际 27 Controller；T20 双套本体表收敛）。

---

## 三、PMO 指令拆单（每个 ≤5 Task，符合铁律 §5.3）

| PMO 指令 | Task 数 | 阶段 | 派发 worker | 派发 verifier | 触发时机 |
|:--|:--|:--|:--|:--|:--|
| PMO-01 本体工作台前端体验修复 | 6 (T1-T6) | Wave A | ecos-be | ecos-fe | 立即（与 B-1 并行） |
| PMO-02 本体后端安全合规整改 | 3 (T12-T14) | Wave B-1 | ecos-be | ecos-fe | 立即（与 A 并行） |
| PMO-03 本体持久化与 DTO 标准化 | 3 (T15-T17) | Wave B-2a | ecos-be | ecos-fe | B-1 完成后 |
| PMO-04 本体新端点（5类+域） | 2 (T7,T8) | Wave B-2b | ecos-be | ecos-fe | B-2a 完成后 |
| PMO-05 前后端打通 | 3 (T9-T11) | Wave C | ecos-be | ecos-fe | B-2b 完成后 |
| PMO-06 契约与工程化收口 | 3 (T18-T20) | Wave D | ecos-be | ecos-fe | 全部 B 完成后 |
| 终审 架构+安全审计 | — | 终审 | ecos-fe (arch) | ecos-fe (security) | 全部 Wave 完成后 |

> **拆单依据**：T1-T6 都是前端独立改动，合并为 1 条 PMO-01（6 Task，符合 ≤5 Task 拆单 + 同模块并行）。T12-T14 是后端安全合规同源改动（都改 ontology-engine-impl 的安全/基础设施层），合并为 PMO-02。T15-T17 是后端持久化/DTO 同源改动，合并为 PMO-03。T7/T8 是新增端点（依赖 T17 落库 + T12 安全），合并为 PMO-04。T9-T11 是前后端打通同源，合并为 PMO-05。T18-T20 是契约收口同源，合并为 PMO-06。
>
> 注：PMO-01 有 6 Task，超出铁律 §5.3 的"单指令 ≤5 Task"限制。处理方式：派发时拆为 PMO-01a (T1-T3 前端性能/主题) + PMO-01b (T4-T6 前端主题/i18n/死代码) 两条 PMO 串行下发，每条 ≤3 Task。

---

## 四、质量门禁（每 Wave 收口判定）

| 门禁项 | Wave A | Wave B-1 | Wave B-2a | Wave B-2b | Wave C | Wave D | 终审 |
|:--|:--|:--|:--|:--|:--|:--|:--|
| V1 文件生存 | find -newer | find -newer | find -newer | find -newer | find -newer | find -newer | — |
| V2 集成点 grep | grep theme/i18n | grep securityEngine | grep PG/DTO/逻辑删除 | grep 新端点/落库 | grep 前端去mock/blob/diff | grep 路由表/CONVERGE | — |
| V3 编译 | tsc + lint 绿 | mvn install 绿 | mvn install 绿 | mvn install 绿 | tsc + mvn install 绿 | mvn install + tsc | 全量 |
| V4 验收 | 4主题+中英+network≤3 | curl 假token 401 + audit 落表 | 重启数据保留 + ArchUnit绿 | curl CRUD 200 + PG 落库 | 函数/导出/diff 链路通 | 单一路径 + 端点数一致 | 综合 |
| 铁律合规 | 颜色/中文 0命中 | 安全集成 grep | 逻辑删除 grep | ApiResult<T> | 无mock | 路由表完整 | — |
| Git commit | 前端 clean commit | 后端 clean commit | 后端 clean commit | 后端 clean commit | 前后端分离 | 文档+代码分离 | — |
| **deliverable_allowed** | **PASS/FAIL** | **PASS/FAIL** | **PASS/FAIL** | **PASS/FAIL** | **PASS/FAIL** | **PASS/FAIL** | **PASS/FAIL** |

**门禁红线**（任一命中 = deliverable_allowed=FALSE）：
- 铁律 §2.4 安全集成 grep 命中 0（T12 强制卡）
- 铁律 §5.1 禁止清单任意一条违规（跨Phase预创建/多Bean冲突/三滤波器缺失/硬编码颜色中文/新建Maven模块等）
- 前端 tsc/lint 未跑即交付
- working tree 未提交（commit hash 不存在 = 不算交付）
- Reviewer 未审查（所有 Wave 必须触发 Reviewer，禁止跳过）

---

## 五、交付物

1. 本文件（DAG + 波次依赖 + 拆单表 + 质量门禁）
2. 每条 PMO 指令的 dispatch 指令（swarm goal 文本，含验收标准）— 由 PM 在派发时动态生成
3. 每个 Wave 的收口报告（commit hash 凭证 + 验证四步法结果 + 遗留风险）— 落在 `docs/04-本体/` 与 `docs/09-PMO指令/`
4. 最终交付质量门禁判定（PASS/FAIL + 理由）— 落在 `docs/08-产品质量/`
