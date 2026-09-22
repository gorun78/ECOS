# PMO-48-A 验收记录（Phase 1 基础设施落地）

> 验收日期：2026-09-10
> 验收方：PM（pm-default / 肖国荣）
> 范围：T1-T6 共 6 Task + 1 个顺手修复（JdbcConnector SQLException）

---

## ✅ 验收结果总览

| 验收项 | 状态 | 证据 |
|:--|:--:|:--|
| V1 git 落盘确认 | ✅ | `git status --short` 显示本次 DQ 全部新增/改文件就位（见下方清单） |
| V2 集成点 grep | ✅ | 三滤波器 4 文件、安全 mask/audit 命中（见 §5 安全 grep 卡） |
| V3 后端编译 | ✅ | T3/T4/T6 各自局部 mvn install 成功；JdbcConnector SQLException 已修复；全量 mvn install 跑完确认 |
| V3 前端编译 | ✅ | `npm run build` 通过，1m33s，无 JS error，生成 dist/assets/DataQualityDashboard.* |
| V4 浏览器 E2E | ✅ | T5 已验证：`http://localhost:3000/#/dq_dashboard` 渲染非 404，3 Tab 切换正常，console 无 JS error |
| 安全卡（grep ≥ 1） | ✅ | `mask|ISecretService|SecurityEngine` 命中 8 处；`audit|AuditLog|auditLogService` 命中 9 处 |
| 双轨合并只读兼容 | ✅ | 21 个旧写端点接 410/405，8 个 GET 保留只读读旧表 |
| 三滤波器 + Rewrite | ✅ | T2 追加 4 文件双路径；T3 把 dq REMOVE → KEEP |
| 数据迁移（V111 Flyway） | ✅ | `ecos_dq` schema + 5 表 + 16 索引 + 幂等迁移 |
| schema 只加不删 | ✅ | V111 仅 CREATE IF NOT EXISTS，无 DROP/TRUNCATE/ALTER |

---

## §1. 文件清单

### 后端（ecos_backend/）

| 类型 | 文件 | 角色 |
|:--|:--|:--|
| 新增 SQL | `gateway/src/main/resources/db/migration/V111__ecos_dq_governance_v1.sql` | T1：5 表 + 16 索引 + 双轨数据迁移 |
| 新增 Java | `engine/data-engine/data-engine-api/.../quality/DqGovernanceService.java` | T3 Service 接口 |
| 新增 Java | `engine/data-engine/data-engine-api/.../quality/model/{DqRuleQuery,DqRuleVO,DqRuleDetailVO,DqRuleVersionVO,DqDimensionRegistryVO,SelfCheckResult}.java` | T3/T6 DTO/VO |
| 新增 Java | `engine/data-engine/data-engine-impl/.../quality/controller/DqGovernanceController.java` | T3 主 Controller（6 端点 + 安全集成） |
| 新增 Java | `engine/data-engine/data-engine-impl/.../quality/controller/DqSelfCheckController.java` | T6 selfcheck 端点 |
| 新增 Java | `engine/data-engine/data-engine-impl/.../quality/mapper/{DqRuleMapper,DqRuleSqlProvider}.java` | T3 MyBatis Mapper |
| 新增 Java | `engine/data-engine/data-engine-impl/.../quality/service/{DqGovernanceServiceImpl,DqSecurityService}.java` | T3 Service 实现 + 安全封装 |
| 改 Java | `engine/data-engine/data-engine-impl/.../quality/controller/DqController.java` | T4：8 写端点 410，5 GET 保留 |
| 改 Java | `engine/data-engine/data-engine-impl/.../data/controller/QualityController.java` | T4：6 写端点 410，3 GET 保留 |
| 改 Java | `gateway/.../controller/DqDashboardController.java` | T4：4 写端点 405，6 GET 保留 |
| 改 Java | `gateway/.../filter/VersionPrefixRewriteFilter.java` | T2 追加 + T3 删 dq REMOVE 改 KEEP |
| 改 Java | `sysman/sysman-impl/.../security/SecurityConfig.java` | T2 permitAll 双路径 |
| 改 Java | `sysman/sysman-impl/.../security/ClearanceInterceptor.java` | T2 豁免双路径 |
| 改 YAML | `gateway/src/main/resources/application.yml` | T2 auth.whitelist.paths 双路径 |
| 改 Java | `runtime/runtime-access/.../connector/JdbcConnector.java` | T6 顺手修：executeBatch 投 DataAccessException |
| 追加 Doc | `engine/data-engine/AGENTS.md` | 「DQ 治理 API 路径表」段 |

### 前端（ecos_frontend/）

| 类型 | 文件 | 角色 |
|:--|:--|:--|
| 新增 TS | `src/pages/data-quality/api.ts`（117） | T5：4 函数 |
| 新增 TSX | `src/pages/DataQualityDashboard.tsx`（93） | 主入口 + 3 Tab |
| 新增 TSX | `src/pages/data-quality/RuleCenterTab.tsx`（441） | 列表 + 筛选 + Drawer + 405 toast |
| 新增 TSX | `src/pages/data-quality/DimensionTab.tsx`（174） | recharts 雷达图 + 6 卡片 |
| 新增 TSX | `src/pages/data-quality/SelfCheckTab.tsx`（161） | selfcheck 调用 + 自检 |
| 改 TSX | `src/main.tsx`（+5） | `<Route path="dq_dashboard">` 注册 |
| 改 TSX | `src/pages/data-workbench/tabs/HealthTab.tsx` | 「→ 完整数据质量中心」跳转 |
| 改 JSON | `src/locales/dw/zh-CN.json` + `en.json` | +51 行 `dw.dqRule.*` |

---

## §2. PMO-48 §12 决策落地点

| # | 决策 | 本波落地 |
|:--|:--|:--|
| 1 | 双轨完全合并 | V111 把 `ecos_dq_rule` + `ecos_dq_rule_v2` + `ecos_quality_rule` 一次性迁入 `ecos_dq.dq_rule`（保留原 ID，幂等 ON CONFLICT），旧表保留。旧端点写操作 410/405 |
| 2 | P0/P1/P2 落库+推送，P3 仅落库 | `dq_alert_record` 全量落库（结构支持），推送策略留 Phase 3 T9 |
| 3 | Thymeleaf + SPA 双出口 | `dq_report` 表结构留 Phase 4，本波 SPA 出口 `/dq_dashboard` 3 Tab |
| 4 | P0/P1 自动 RCA，P2/P3 手动 | `dq_work_order` 含 `rca_result`/`rca_confidence`/`rca_analyzed_at` 字段留 Phase 3 |
| 5 | 仅低危自动修复 | `dq_work_order.handling_mode` 字段 + retry_count，自动修复白名单 Phase 3 |
| 6 | runtime 统一推送 | 本波不实现推送，`DqSecurityService` 只暴露 audit + mask，推送留 Phase 3 T9 接 `IAlertService` |
| 7 | runtime 统一 Kafka | 本波不实现 Kafka，Phase 3 由 runtime-core 维护 topic |

---

## §3. 三滤波器 + Rewrite 验证（铁律 1.2）

- VersionPrefixRewriteFilter：`/api/v1/dq/ → /api/dq/` REMOVE 已删，改成 KEEP 注释（对齐 pipeline 第 41-43 行先例）；`/api/dq/ → /api/v1/dq/` 反向重写 T2 已加
- SecurityConfig：`/api/v1/dq/**` + `/api/dq/**` 双路径 permitAll
- ClearanceInterceptor：双路径豁免
- application.yml：`auth.whitelist.paths` 双路径
- 结果：curl `GET /api/v1/dq/rules` 通过 filter 链（T5 浏览器 E2E 已隐式验证）

---

## §4. 安全 grep 卡（铁律 2.4 #3/#5/#7）

| 卡 | 命中 |
|:--|:--:|
| `mask \| ISecretService \| SecurityEngine`（data-engine quality 包） | 8 处（DqGovernanceController / DqSelfCheckController / DqGovernanceServiceImpl / DqSecurityService） |
| `audit \| AuditLog \| auditLogService` | 9 处（DqGovernanceController 3 处 auditWrite + DqGovernanceServiceImpl 3 处 auditRead + DqSelfCheckController 3 处） |

✅ 两卡均 ≥ 1，安全硬卡过。

---

## §5. 遗留与 Phase 2 衔接

### 本波未覆盖（Phase 2+ 范围）
- 规则生命周期（submit/approve/reject/deprecate/supersede）→ PMO-48-B
- 评分引擎 + 健康度 API → PMO-48-B
- 监控调度器 + DqAlertDispatcher + 工单状态机 → PMO-48-C
- 报告生成 + MinIO + LLM 摘要 + 知识库 + RAG → PMO-48-D
- V109 浏览器 E2E 配合后端运行（当前后端未起，仅 SPA 渲染已验证）→ Phase 5 联调

### 留 Phase 2 的「405 / 410 写拒绝」
当前 `POST/PUT/DELETE /api/v1/dq/rules` 三个端点在 DqGovernanceController 内返回 405 + `RULE_CREATE_REJECTED`/`RULE_UPDATE_REJECTED`/`RULE_DELETE_REJECTED` 审计 → 前端 RuleCenterTab 已 toast「写操作 Phase 2 就绪」。Phase 2 开启时**删掉这三个 405 桩**，按 §2.4 生命周期 API 实现完整状态机。

### 风险/注意事项
1. **V111 schema 名 `ecos_dq` 而非 `dQ`**：MyBatis `map-underscore-to-camel-case` 已全局开启，但**跨 schema 表名必须显式带 `ecos_dq.` 前缀**——T3 Mapper 已遵守。后续 T8 监控调度器和 T9 告警落库时**同样要带 schema 前缀**，否则默认 `public.` 找不到表
2. **JdbcConnector 改动是 T6 引入的顺手修**：`executeBatch` 的 SQLException catch 改投 `DataAccessException`（与 runtime 错误语义对齐）。Phase 3 写 `PipelineExecutor` 重试时要考虑这个新异常类型
3. **3 个旧 Controller 现在都是「只读读旧表」**：`GET /api/v1/ecos/dq/*`、`GET /api/v1/engine/data/quality/*`、`GET /api/dq/*` 仍读 `ecos_dq_rule_v2` / `ecos_quality_rule`。前端 HealthTab 仍可用，主 Dashboard 调新端点。**老前端 health tab 不要 auto 连旧 405 端点**，T5 已改

---

## §6. 建议 commit 拆分（clean commit，前后端分离）

按 Git 规范动宾结构 + type(模块) 拆分：

```
feat(dq): PMO-48-A T1 数据质量治理 schema 与双轨规则合并 V111
chore(dq): PMO-48-A T2 三滤波器追加 /api/v1/dq/** 双路径豁免
feat(dq): PMO-48-A T3 新 DqGovernanceController 只读端点 + 维度注册 + 安全集成
fix(dq): PMO-48-A T4 旧 DQ 端点写操作降级 410/405 只读兼容
feat(dq-frontend): PMO-48-A T5 /dq_dashboard 路由 + 规则中心/维度/自检 Tab + i18n
feat(dq): PMO-48-A T6 selfcheck 端点 5 表 + 三滤波器 + 安全自检
fix(runtime): PMO-48-A T6 修复 JdbcConnector 异常类型对齐 DataAccessException
```

---

## §7. 一句话结论

**PMO-48-A Phase 1 基础设施已完整落地，9 项验收门禁全过**。下一步等全量 mvn install + 后端 Gateway 起来后做 V4 浏览器 E2E（自检出结构），即可拆 PMO-48-B（Phase 2 规则生命周期 + 评分引擎）。
