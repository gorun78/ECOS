# PMO-48-D: 报告生成 + 知识库沉淀 + E2E 真实建表（Phase 4）

> **架构铁律**: 必须遵循 [ECOS架构铁律](../../.trae/rules/架构铁律.md)
> 来源: 肖国荣
> 日期: 2026-09-10
> 上游方案: PMO-48 数据质量管理方案 v1.0
> 子指令: PMO-48-A/B/C（Phase 1-3 已落地：规则生命周期 + 评分引擎 + 调度告警工单）
> 状态: **执行中**
> 铁律:
>   1. 报告双出口：路由注册 reCAPTCHA + SPA /dq_dashboard 双出口（PMO-48 §6）
>   2. 新表/新列走 Flyway V114，schema 只加不删
>   3. 写操作必走 auditLogger（铁律 2.4 #5）
>   4. 报告生成走 MinIO 对象存储，不直建文件系统目录（铁律 2.4 #10）
>   5. 知识库沉淀走 kb-engine 实体接口，不自建 RAG（铁律 2.5 #2）
>   6. AI 摘要走 cognitive-engine `POST /api/v1/cognitive/agent/invoke`（铁律 2.5 #4）
>   7. 不 new JdbcTemplate / RestTemplate / Runtime（构造器注入或 ObjectProvider）
>   8. 控制流 if/for/while 必须花括号

---

## §背景

Phase 1-3 已完成：
- DB 5 表 + 调度器 + 6 维评分 SPI + 告警分发 + 工单状态机（Phase 1-3 管道闭环）
- 前端 5 Tab（规则中心 / 6 维度评估 / 监控调度 / 告警中心 / 工单中心）
- `DqRcaService` stub 已建（`POST /api/v1/cognitive/agent/invoke` 桩未真接）
- 后端全编译通过 + 前端 tsc + E2E 空态验证通过

Phase 4 目标：**让闭环从能跑 → 真正跑出报告 + 沉淀知识 + 验证生产建表**。

| 子域 | 内容 | 对应方案章节 |
|:--|:--|:--|
| 报告生成 | 日报/周报/月报，PDF/HTML 双格式，MinIO 持久化，SPA 报告列表页，AI 摘要 | PMO-48 §6 |
| 知识库沉淀 | 规则/告警/工单 → kb-engine 知识条目，供 RAG + RCA stub 替换 | PMO-48 §7 |
| E2E 建表 | V114 Flyway DDL + 5 表真实建表 + 3 fixture 业务场景端到端 | PMO-48 §9（测试） |

---

## §禁止清单

1. 不跨 Phase 预建 Phase 5 功能（Phase 5 是生产 PMO 运营看板 + 自愈合）
2. 不 new JdbcTemplate / RestTemplate / Runtime（构造器注入）
3. 不 delete 已有表/列；新表只加 V114，schema 只加不删
4. 不在 `quality` 包 `@Scheduled`（铁律 2.5 #3，用 `DqScheduledTask` 里已有的 cron）
5. 不直接调 cognitive-engine node，必须走 `POST /api/v1/cognitive/agent/invoke`（铁律 2.5 #4），复用 Phase 3 已建的 `DqRcaService` 桩替换为真实调用
6. 报告 MinIO bucket 名固定 `dq-reports`，不创建新 bucket
7. 报告命名 `{type}_{scope}_{start}_{end}_{hash8}.html/.pdf`（type=daily|weekly|monthly）
8. 不建嵌入式数据库，全部走 PG（铁律 3.2）
9. 不新增 npm 依赖（前端用已有 `recharts`/`antd`/`lucide-react`）
10. 不 `implements` 已有 Service 接口（铁律 1.3）

---

## §Task

| Task | 文件/路径 | 操作 |
|:--|:--|:--|
| **T15 报告生成与出口** | `data-engine-impl/.../quality/report/DqReportService.java`（接口，api 模块）+ `DqReportServiceImpl.java`（impl，`@Service("ecosDqReportService")`）+ `DqReportController.java`（`@RequestMapping("/api/v1/dq/reports")`）+ `DqReportSchedulerStep.java`（嵌入 DqScheduledTask 月末触发的钩子，不改 @Scheduled）+ `Thymeleaf` 模板 `templates/dq_report.html`（只读，只渲染不建 HTML Builder）+ MinIO `MinIOClient` 构造器注入（复用已有 `MinioHelper`） | ① T15a：V114 SQL（`ecos_dq.dq_report` 表：id/report_type/daily|weekly|monthly/scope NAS/OFC/ALL/startDate/endDate/payloadJson HTML/payloadPdf bytes@MinIO/objectKey/llmSummary/createdAt/createdBy）；② T15b：`DqReportServiceImpl.generateDaily(scope)` / `generateWeekly()` / `generateMonthly()`，查 `dq_rule_check`（最近 N 天）+ `dq_alert_record` + 6 维 `dq_score_asset` → 生 HTML 模板（Thymeleaf `html/thymeleaf` 模板引擎，Spring 已配 `ThymeleafTemplateEngine`，不新建）→ `MinioHelper.putObject("dq-reports", objectKey, htmlBytes)` → 写回 `dq_report.payloadJson`（HTML 小）/ payloadObjectKey（PDF 大）；③ T15c：`GET /api/v1/dq/reports?type=&scope=&startDate=&endDate=&page=` 列表；④ `GET /api/v1/dq/reports/{id}/html` 返回 HTML（Content-Type: text/html）；⑤ `GET /api/v1/dq/reports/{id}/pdf` 返回 PDF（Content-Type: application/pdf）；⑥ `POST /api/v1/dq/reports/generate?scope=&type=` 手动触发；⑦ AI 摘要：`DqLlmBlock.callCognitiveAgentInvoke(sceneId="dq_report_summary", input={...})` 存 `llmSummary` 字段（复用 Phase 3 已建 DqRcaService 的 cognitive 调用模式，桩可保留 stub） |
| **T16 知识库沉淀** | `data-engine-impl/.../quality/kb/DqKnowledgeSinkService.java`（接口，api）+ `DqKnowledgeSinkServiceImpl.java`（impl，`@Service("ecosDqKnowledgeSinkService")`）**新方法**，不 implements 已有 DqKnowledgeSinkService（铁律 1.3） | ① T16a：V114 SQL 加 `ecos_dq.dq_knowledge_entry`（id/source_type RULE|ALERT|WORK_ORDER/entry_id 外键/category 实体知识点/summary title/content_vector_vector(1024)/embedding vector(1024)/metadata jsonb/createdAt）；② T16b：`DqKnowledgeSinkServiceImpl.ingestFromRule(ruleId)` / `ingestFromAlert(alertId)` / `ingestFromWorkOrder(orderId)`：从 `dq_rule` / `dq_alert_record` / `dq_work_order` 抽实体（`ruleName/targetTable/targetField/errorCode/alertLevel`）→ 拼 `content`（摘要）→ 调 `vision-embedding`（`POST /api/v1/vision-embedding/embed` 得 1024 维向量，复用生态已有 embedding 服务，若未部署则 fallback text2vector 哈希向量）→ 写 `dq_knowledge_entry`；③ T16c：`GET /api/v1/dq/kb/similar?query=&top=3` 近邻检索（`ORDER BY embedding <=> query_embedding LIMIT 3`）；④ **替换 `DqRcaServiceImpl` stub**：读 `dq_work_order` 最近 RAG 相似 3 条 knowledge_entry → 拼上下文传给 cognitive `invoke`，替换 Phase 3 stub 中的静态 `rootCause="STUB: 根因分析引擎待接入"`；⑤ 触发时机：`DqAlertDispatcher` 落库 `dq_alert_record` 后 fire-and-forget 调 `sink.ingestFromAlert`，`DqWorkOrderServiceImpl.close()` 成功后 fire-and-forget 调 `ingestFromWorkOrder` |
| **T17 E2E 建表 + fixture** | `gateway/src/main/resources/db/migration/V114__ecos_dq_phase4.sql`（统一写 T15+T16 两张新表）+ `data-engine-impl/src/test/java/.../quality/DqPhase4E2eIT.java`（`@SpringBootTest` + `FlywayMigrate` 跑 V111→V114 真实建表）+ 3 个 fixture 场景（正常规则通过 / P1 告警触发 + 工单创建 / 报告生成） | ① T17a：V114 含 `dq_report` + `dq_knowledge_entry` + `dq_work_order.reject_note` 列补齐（Phase 3 遗留）；② T17b：`DqPhase4E2eIT` 用 `@MockBean JdbcTemplate`（不真连 PG，测 schema 校验 + Flyway 版本号）+ 3 fixture：① DRAFT→APPROVE 后 `dq_rule_check` 写入正确 ② FAILED check → `dq_alert_record` P1 + `dq_work_order` PENDING ③ `generateDaily("ALL")` 后 `dq_report` 1 行 `llmSummary` stub 非空；③ T17c：`mvn test -pl data-engine-impl` 断言 `dq_report.reportType NOT IN ('DRAFT')` 且 `payloadJson IS NOT NULL`（验证报告落库成功） |

---

### §Task 工期

| Task | 人日 |
|:--|:--:|
| T15 报告生成（V114 部分 + Thymeleaf + MinIO + LLM 摘要 stub） | 3 |
| T16 知识库沉淀（V114 部分 + embedding + RAG + RCA 替换） | 3 |
| T17 E2E 建表 + fixture | 2 |
| 合计 | **8 人日** |

### §Task 依赖

```
T15 (报告+MinIO) ──────┐
                       ├─→ T17 (E2E 建表 + fixture)
T16 (知识库+RAG+RCA) ──┘

T15、T16 并行（文件无冲突，都只加 API model + impl + 共用 V114 SQL）
T17 依赖 T15+T16 完成（IT 断言两张新表都有数据）
```

### §安全自检卡

| 卡 | 验收命令 | 阈值 |
|:--|:--|:--:|
| 报告审计 | grep DqReportServiceImpl 中 "auditWrite\|auditRead" | ≥ 3（GENERATE/DOWNLOAD_HTML/DOWNLOAD_PDF） |
| MinIO 无裸路径 | grep DqReportServiceImpl 中 "MinioHelper\|minioHelper" 不直接 new | = 1+（构造器注入）|
| cognitive agent 调用 | grep DqRcaServiceImpl 中 "agent/invoke" 或 "T6\|t6" | ≥ 1（替换 stub 后） |
| 知识库 vector 列 | grep V114 SQL 中 "vector(1024)" | ≥ 1 |
| 三滤波器 | grep GatewaySecurityConfig 中 `/api/v1/dq/reports` | 已有 `/api/v1/dq/**` 通配覆盖，确认 = 1 |

### §风险与回退

| 风险 | 概率 | 缓解 |
|:--|:--|:--:|
| MinIO `dq-reports` bucket 不存在 | 中 | `MinioHelper.ensureBucketExists("dq-reports")` 幂等创建（guard） |
| Thymeleaf 模板渲染大报告 OOM | 低 | `scope=NATIVE|OFI` 默认限制，不分则限 5000 条规则 |
| 无 vision-embedding 服务部署 | 高 | `DqKnowledgeSinkServiceImpl` 用 `ObjectProvider` 注入 EmbeddingProvider，fallback `text2vector`（MD5 截取前 128 字符 hash 映射 1024 维） |
| 替换 RCA stub 后 cognitive 超时 | 中 | `@Async` 异步调，超时 15s 兜底返回 stub 根因 "cognitive 调用超时" |

### §Phase 4 验收门禁

| 章 | 内容 | 验收命令/证据 |
|:--|:--|:--|
| V1 | T15 报告端点 | `curl POST /api/v1/dq/reports/generate?scope=ALL&type=daily` 返 200 + `GET /api/v1/dq/reports/{id}/html` 返 text/html |
| V2 | T16 知识检索 | `curl GET /api/v1/dq/kb/similar?query=管道断裂&top=3` 返 ≥ 1 条（fallback 时可能对全零，≥ 1 即可）|
| V3 | RCA 不再 stub | `curl POST /api/v1/dq/work-orders/{id}/run-rca` 返 200 + `rcaResult` 字段非 "STUB:" |
| V4 | T17 IT 全绿 | `mvn test -pl engine/data-engine/data-engine-impl -Dtest=DqPhase4E2eIT` 3 fixture 全过 |
| V5 | 门禁 grep | §安全自检卡全项 |

### §产出物清单

**后端（ecos_backend/）**
- `gateway/src/main/resources/db/migration/V114__ecos_dq_phase4.sql`（`dq_report` + `dq_knowledge_entry` + `dq_work_order.reject_note` 补列）
- `data-engine-api/.../quality/DqReportService.java`
- `data-engine-api/.../quality/model/DqReportVO.java`
- `data-engine-impl/.../quality/report/DqReportServiceImpl.java`
- `data-engine-impl/.../quality/report/DqReportController.java`
- `engine/data-engine/data-engine-impl/src/main/resources/templates/dq_report.html`（Thymeleaf 模板）
- `data-engine-api/.../quality/DqKnowledgeSinkService.java`（新增，非既有 implements）
- `data-engine-impl/.../quality/kb/DqKnowledgeSinkServiceImpl.java`
- `data-engine-impl/.../quality/kb/DqKnowledgeController.java`（`/api/v1/dq/kb/*`）
- `data-engine-impl/.../quality/service/DqRcaServiceImpl.java`（改：stub → 真实 cognitive + KB 上下文）
- `data-engine-impl/.../quality/service/DqAlertServiceImpl.java`（改：落库后调 `sink.ingestFromAlert`，+1 行）
- `data-engine-impl/.../quality/service/DqWorkOrderServiceImpl.java`（改：close 后调 `sink.ingestFromWorkOrder`，+1 行）

**前端（ecos_frontend/）**
- `src/pages/data-quality/ReportListTab.tsx`（新，约 300 行，嵌入 Dasboard 第 6 Tab）
- `src/pages/data-quality/api.ts`（新 `fetchDqReports` / `fetchDqReportHtml` / `fetchDqKnowledgeSimilar` 等 3-4 函数）
- `src/locales/dw/zh-CN.json` + `en.json`（`dw.dqRule.report.*` + `dw.dqRule.kb.*` 各组 ≥ 8 key）

**测试（data-engine-impl）**
- `DqPhase4E2eIT.java`（3 fixture）

**AGENTS.md 追加**
- `engine/data-engine/AGENTS.md` 新节「报告 API 路径表 + 知识沉淀 API 路径表」

---

### §分发策略（PM 侧）

| 波次 | Task | 说明 |
|:--|:--|:--|
| 第 1 波 | T15（报告 + T16 前半） | 先 V114 + 报告 + MinIO，T16 知识库表也一起建（V114 一次） |
| 第 2 波 | T16（知识 + RCA 替换） | 依赖 V114 已建 + T15 cognitive 接口可复用 |
| 第 3 波 | T17 E2E IT | 依赖 T15+T16 完成，验收门禁单次过 |
