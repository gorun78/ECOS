# 详细设计 02 — 数据域（data-engine + datanet：数据源/管道/血缘/DQ/五层载体 + 业务事实模型）

> 来源: AI Agent（资深软件系统设计师） | 日期: 2026-09-28 | 责任人: AI Agent
> 版本: v1.1 | 工作流等级: L3（v1.1 2026-09-29：随**需求检视报告 §十四.1** 批量批准定版——**R-1 a／R-1b ②／R-2 a／R-3 ②+禁静默** 四项全部按本册推荐项批准；R-1b ② 的规则回写已落到《数据库访问规范》v1.3 ST07 区块。Gate-1 已签字、Gate-2 已通过、`deliverable_allowed=true`。**批准 ≠ 已实现 ≠ 已验收**；存量物理迁移与实跑库属**待授权执行**（§14.4））
> 上游依据: `docs/10-架构/架构设计说明书-2026-09-28.md` v1.2（§二 承流警示 / §四 数据架构 / §五.2 数据链路 / §六 分层 / §十一 C1~C29）；`.trae/rules/架构铁律.md` v2.0（§2.5 底座收口 / §3.1 DDL 单源）；`数据库访问规范.md` v1.2（IR01~IR06 / DR01~DR08 / ST03~ST08 / MC01~MC06 + §四 DDL 模板 v1.2 纠错）；`数据湖存储分层规范.md` v2.0（数据域二分 + 五层 + 知识双形态）
> 批准凭证: `docs/20-需求/需求检视报告-2026-09-28.md` §十二（Q1~Q14 + E2E 补记）**+ §十四.1（本册 R-1 a／R-1b ②／R-2 a／R-3 ②+禁静默 已批准）+ §十四.3（Gate-1 已签字／Gate-2 已通过），用户批准 2026-09-29**
> 主责 REQ: REQ-DATA-01~08、REQ-FC-06（主数据/事实）、REQ-DB-05/06、REQ-NF-07（数据链路可观测）、REQ-API-01/03（前缀与错误码数据域扩展）
> 对应需求分册: `docs/20-需求/PRD-02-data-engine需求规格-2026-09-28.md`
> 前置依赖: 分册 00 C.2（信任链/上下文还原）+ C.2.3（两态寻址）、分册 01 C.2（`decide` 单入口）——**本册所有查询/导入/导出通道以分册 01 的裁决管道为强制前置；W02/W31 未关闭前 F02-06/F02-07 不得上线**
> 关联 ADR: ADR-15（承流口径）、ADR-3（单库 5+1）、ADR-12（主控制 schema 三步走）、ADR-13（Doris∨ClickHouse）、ADR-14（确定性计算产物经 data-engine 写通道落业务域）、ADR-10（唯一入口）
> 编号接续: 本册改造项从 **W41** 起，回填 ARCH_SPEC §十一 从 **C30** 起（00 册 W01~W15 / 01 册 W29~W40）

---

## 〇、本册定位

| 消费者 | 读什么 |
|:--|:--|
| 03/04/05/06 册（所有取数的引擎） | C.7 跨引擎读数据契约 + D.2 REST 清单（唯一取数面）+ E.1 schema 归属裁定 |
| 09 册（预测场景验收） | E.2 事实五表 DDL + C.5 DQ 门禁流 + A 章 F02-06/F02-07 验收标识 |
| 08 册（前端） | B 章 6 组界面规格 + 前缀收敛表 C.2.2（`route-manifest.json` 数据域条目） |
| 02 册自身开发 | 全部 |
| 运维 / DBA | C.8 降级矩阵、E.5 版本脚本清单、F 章 M0 阻断项 |

**本册不含**：安全裁决管道本体（分册 01 C.2，本册只定调用点）、runtime-access Driver 本体（分册 00 C.4，本册定清偿清单与验收）、口径/本体（03 册）、预测运行编排（05/07/09 册）。

---

## 一、追溯与现状事实（全部实测，2026-09-28）

### 1.1 实测事实表 D-1 ~ D-24

> 取证方法：源码 grep（排除 `/target/`）+ MyBatis Mapper 扫描 + **运行中的 gateway :8080 curl 实测** + **运行中 PG（ecos-postgres / sys_man）系统表实查**。所有行/表计数为实查数字，非估算。

| # | 事实 | 证据 | 判定 |
|:--:|:--|:--|:--|
| D-1 | data-engine 有 **37 个 Controller**（controller 24 + pipeline 3 + quality 9 + transform 1），今日实测**全部由 gateway :8080 宿主**；`netstat` 显示只有 8080 与 3000 在听，**datanet :18082 未运行** | `find engine/data-engine -name '*Controller.java'` = 37；`netstat -ano \| grep -E ":8080\|:18082"` 仅 8080 | 承流=单体，按 ADR-15 S0；W41 |
| D-2 | datanet 自身仅 3 个 Controller，前缀为 `/api/datalake`、`/api/dq`、`/api/v1/ecos/git`，与 data-engine 的 `/api/v1/dq`、`/api/v1/ecos/dq`、`/api/v1/datanet/datalake` **语义重叠**；且 `DatanetServiceApplication` 用 `excludeFilters` **排除 `DataEngineConfigController`**，注释自认"独立启动下 `/api/v1/data-engine/config/*` 暂下" | `services/datanet/.../DatanetServiceApplication.java:56-62`；`controller/{DataLake,DqDashboard,Git}Controller.java:23/24/31` | S3 切流即端点缺失；W42 |
| D-3 | **同一资源 5 套前缀并存且同日同进程都返回 200**：实测 `/api/v1/ecos/dq/rules`(200 legacy)、`/api/v1/datanet/assets`(200)、`/api/v1/pipeline/definitions`(200)、`/api/v1/engine/data/pipeline/tasks`(200)、`/api/v1/engine/data/layers`(200)、`/api/v1/pipeline/node-types`(200) | 2026-09-28 18:35 curl 记录（`_win_tasks/logs/gateway-stdout.log` 同刻） | 无单源路由清单；W43 |
| D-4 | **管道双模型并存且都有数据**：JSON DAG 模型 `PipelineController`+`PipelineExecutionService`（12 节点类型 switch）持 `ecos_pipeline_definition` **51 行**；YAML 模型 `PipelineTaskController`+`PipelineExecutionEngine` 持 `ecos_pipeline_task` **4 行**（列含 `yaml_content`）。前端 `api.ts` 同时调 `/api/v1/pipeline/definitions` 与 `/api/v1/engine/data/pipeline/tasks` | pg 计数 51/4；`pages/data-workbench/api.ts` 两处 URL；`PipelineExecutionService.java:297-325`；`PipelineExecutionEngine.java:60` | 双执行器=必然漂移；W44（**裁决项 R-2**） |
| D-5 | **DQ 双模型，新模型零数据**：治理模型 `ecos_dq.dq_rule/dq_rule_version/dq_rule_check/dq_alert_record/dq_work_order/dq_schedule/dq_score_snapshot/dq_report/dq_throttle/dq_knowledge_entry` 全部 **0 行**；旧模型 `public.ecos_dq_rule` **17 行** + `public.ecos_dq_issue` **21 行** | pg `count(*)` 实查 8 张治理表 =0；legacy 17/21 | 治理栈从未投产；W45 |
| D-6 | **治理栈端点三处实测失败且被掩蔽为 404**（同日 gateway 日志原文）：<br>a) `GET /api/v1/dq/rules` → `MyBatisSystemException`（`MapperMethod.executeForMany`，msg 为空）<br>b) `GET /api/v1/dq/work-orders` → `BadSqlGrammarException`，SQL 实为 `SELECT <列清单> WHERE is_deleted = FALSE ORDER BY … LIMIT ? OFFSET ?`——**缺 FROM 子句**（`DqWorkOrderServiceImpl.java:242` 只拼 `WO_COLUMNS + whereSql + ORDER BY`，未拼 `FROM ecos_dq.dq_work_order`）<br>c) `GET /api/v1/dq/scores/system` → `BadSqlGrammarException: SELECT AVG(overall_score) FROM ecos_dq.dq_score_asset …`，实查 **`ecos_dq.dq_score_asset` 表不存在**（V112 声明该表，库内 0 命中）<br>三者在 `GlobalExceptionHandler.java:201` 一律被翻译为 `code=404 "端点暂未开放或服务未就绪"` | `_win_tasks/logs/gateway-stdout.log` 18:35:29/18:35:31/18:35:32；`DqWorkOrderServiceImpl.java:124-135,242`；pg 表存在性实查 present=0 | **P0**：5xx 被掩蔽成 404 → 前端"回落 legacy"掩盖缺陷；W46/W47/W48 |
| D-7 | **同名表双份存在，写入全部落在 `public`**：`td_datasource/td_data_resource/td_data_field/ecos_pipeline_definition/ecos_pipeline_task/ecos_pipeline_run/ecos_dq_rule/ecos_quality_rule` 在 `public` 与 `ecos_data`/`ecos_dq` **各有一份**；实查 `public.td_datasource=5 / ecos_data.td_datasource=0`、`public.ecos_pipeline_definition=51 / ecos_data=0`、`public.ecos_pipeline_task=4 / ecos_dq=0`；会话 `search_path = "$user", public`；且 SQL 一律**裸表名**（`FROM ecos_pipeline_task`） | pg `pg_tables` 实查 + `SHOW search_path`；`DataLineageService.java:60,204,235`；`PipelineExecutionEngine.java:60`；违反 §四 附则 1（裸表名=FAIL）+ MC06 | 引擎 schema 是空镜像；W49（**裁决项 R-1**） |
| D-8 | 库内实有 **16 个 schema**：`public/ecos_data/ecos_ontology/ecos_knowledge/ecos_ai/ecos_cognitive/ecos_dw/ecos_dq/ecos_infra/ecos_security/ecos_sysman/ecos_datanet/ecos_buszhi/ecos_dccheng/ecos_aiming/ecos_demo`。其中 `ecos_dq` 不在 ST07「5+1」清单，而 ST07 明文把 **dq 归主控制 schema**（规范 L97） | `pg_namespace` 实查；`.trae/rules/数据库访问规范.md` L97 | 规范与实现互相违背；W50 |
| D-9 | **五层载体空心化**：`td_data_resource` 393 行全部 `resource_type='TABLE'`，`layer` 分布 SOURCE=4 / RAW=375 / CURATED=14 / **SEMANTIC=0 / APPLICATION=0**；`/api/v1/engine/data/layers` 实测原样返回该分布。`ecos_dw` 只有 `doc`、`doc_chunk` 两张表 | pg 分组计数；`DataLayerController` 200 响应体；`pg_tables schemaname='ecos_dw'` | 湖规范 v2.0 五层中两层无载体；W51 |
| D-10 | **业务事实五表全仓零存在**：`grep -r "ecos_biz_stage_fact\|ecos_biz_cost_fact\|ecos_biz_resource_fact\|ecos_biz_project_attribution\|ecos_forecast_input_snapshot"` 对 `*.java/*.xml/*.sql/*.ts/*.tsx`（含 `ecos-sql/`、`database/seed.sql`）**0 命中** | 全仓 grep（含 ecos_frontend/ecos-tests） | REQ-DATA-01 未起工；本册 E.2 首次落地（与**卷 09 C212** 交叉互认，同一前置不重复立项） |
| D-11 | **数据源凭证明文入库**：`td_datasource.connection_config TEXT` 内含 `"password":"…"`，实查 **5 行中 2 行命中**，`length(password)=8`（即 `"postgres"` 明文）；data-engine `datasource/**` 包内 `grep encrypt\|decrypt\|AES\|cipher` 与 password 同现 = **0 命中** | pg `regexp_match` 实查；包内 grep 零命中 | ST03 控制域永不可豁免（ST03-A ④）；**W52（P0 安全）** |
| D-12 | `DriverManager` 违规点与 PRD-02 §五 清单**逐条对上**：`QueryExecutionServiceImpl:69,141`、`DataDescriptionDiscoveryImpl:64`、`MetadataRowCountService:117`（另 67/97 经私有 `DriverManagerConnection`）、`BaseJdbcAdapter:99` | 全量 grep 命中 10 行 | REQ-DATA-05 全部未做；W53 |
| D-13 | **Service/Controller 层直接持有 JdbcTemplate**：data-engine 内 `JdbcTemplate` 命中 **51 个文件**，其中文件名含 `Service` 的 **37 个**；规范 §五 明令"Service 禁 JdbcTemplate 直接连"、IR01 令 Controller 禁连 | `grep -rl JdbcTemplate \| wc -l` = 51；`\| grep -c Service` = 37 | 三层穿透；W54 |
| D-14 | **跨引擎取数默认指向未运行的 :18082 并静默降级**：`@Value("${ecos.datanet.base-url:http://localhost:18082}")` 命中 3 处（`KbEntityInstanceExtractionService:103`、`KnowledgeDocIngestService:106`、`DataNetResourceClient:39`）；`DataNetResourceClient` 类注释自认"**不可达或端点 404 时返回空列表**"，且其调用路径 `/api/v1/datanet/resources/{id}/fields` **在 data-engine 内无对应映射**（实际为 `/assets/{assetId}/fields` 与 `/metadata/fields/{resourceId}`） | grep 命中；`DataNetResourceClient.java:21-28,58`；`AssetController.java:118`、`MetadataController.java:148` | 与分册 00 C.2.3 两态寻址冲突 + 结构必 404 + 静默空值；**W55（P0）** |
| D-15 | 网关侧跨模块直查数据域表：`MonitorService.java:56` `SELECT count(*) FROM ecos_dq_issue …`、`TenantAwareJdbcTemplate.java:44` 把 `ecos_dq_rule` 写死进租户表清单 | gateway 源码 | 违反 IR01（gateway 不得直持 PG 改写业务域）；W56 |
| D-16 | 节点目录与执行器**已同源但含未实现项**：`PipelineNodeTypesCatalog` 登记 `SOURCE_CDC`，执行器 `PipelineExecutionService:298-303` 对 `SOURCE_CDC` **显式抛 BusinessException**（不落 default，好），但目录条目未标 `available=false` → 用户可在画布拖出必然失败的节点 | `PipelineExecutionService.java:297-325`；`GET /api/v1/pipeline/node-types` 200 响应含该条目 | 目录/能力不一致；W57 |
| D-17 | **DDL 单源缺表**：`ecos_pipeline_definition` / `ecos_pipeline_execution` 的 `CREATE TABLE` **只在 `ecos-sql/{mysql,oracle,postgresql}/03_ecos_data.sql`**（L136/145/233/267），gateway 单源 migration 目录零命中 → 按铁律 §3.1 重建空库即缺表 | 全仓 grep（`*.sql` 186 个文件） | 双 DDL 源；W58 |
| D-18 | **规范模板自身纠错前被成片复制**：单源 125 个 `.sql` 中 `gen_random_uuid()` **4 个文件**（V155/V156/V46/V48）、`JSONB` **49 个文件**、`BIGSERIAL/SERIAL` 主键 ≥20 个文件（含 V111/V112 DQ 治理表）。另 `V6__ecos_data_quality.sql` 在 gateway 与 `services/sysman/impl/sysman-boot` **同名不同内容并存**（md5 d8d0f9… vs ebca3c…，同建 `ecos_dq_rule`） | grep 计数 + `md5sum` | MC01/MC02/MC05 + 铁律 §3.1；W59（**与 01 册 W38 同族**） |
| D-19 | **审计列漂移**：`ecos_dq.dq_rule` 实列 `created_by/updated_by/created_at/updated_at/deleted_at/is_deleted/version`，违反 DR06（`create_time/update_time/create_by/update_by/is_deleted`）与 DR07（`version_no VARCHAR(20)`）；`td_datasource` 用 `create_by/create_time/update_by/update_time` 缺 `is_deleted`/`version_no`/`domain` | pg `information_schema.columns` 实查 | DR06/DR07/DR08；W60 |
| D-20 | `DqScheduleServiceImpl#buildContext` 确证空转：`setConnectionConfig(new LinkedHashMap<>())`、`setSampleFailures(new LinkedHashMap<>())`、`setTotalRows(0L)`、`setFailedRows(0L)`、`setLastCheck(new LinkedHashMap<>())`——即 PRD-02 §四-1 所述。但同文件 `dispatchAlert` → `DqAlertServiceImpl:173,284` **确已推 runtime `IAlertService`**（`ObjectProvider` 可选注入，不可用时"降级仅落库"） | `DqScheduleServiceImpl.java:324-339,307-321`；`DqAlertServiceImpl.java:28,85,173,290` | REQ-DATA-04 部分已闭合，缺"升级时限"；W61 |
| D-21 | **DQ 告警升级定时器未实现**：`grep -rn "@Scheduled" quality/` **0 命中**；只有手动 `POST /api/v1/dq/work-orders/{id}/escalate` 端点与 `escalated_to` 列 → "P2 5min 未 ack→P1、P1 15min→P0" 无人驱动 | grep 零命中；`DqWorkOrderController.java:202` | REQ-DATA-04-2 未落；W62 |
| D-22 | **PRD-02 §六（REQ-DATA-06）所述缺陷实际已闭合**：`GitRepoRootResolver` 已有 `requireSafeRoot`（绝对路径 + 禁 `..` + normalize）、`requireSafeSegment`（禁 `..`/`/`/`\`/`:`）、`resolveUnderRoot`（逐段校验 + `startsWith(base)`）、`requireInsideRoot`；`MetadataCollectGitArchive:81` 对 `datasourceId` 调 `requireSafeSegment`。残余=①repoRoot 存在性未校验 ②symlink（`toRealPath`）未加固 ③datanet `GitController` 侧未复用该 resolver 单测 | `service/GitRepoRootResolver.java:22-95`；`metadata/MetadataCollectGitArchive.java:78-88` | 需求过期；W63（转加固项）+ PRD 回写 |
| D-23 | **血缘从 YAML 反向解析**：`DataLineageService` 用 `WHERE yaml_content ILIKE ?` 全表扫 `ecos_pipeline_task`（:60,204,235）并 `"… LIMIT " + taskLimit` 拼接（:370）；血缘表 `ecos_data_lineage_node` 8 行 / `edge` 7 行，`public` 与 `ecos_data` 双份 | `DataLineageService.java:60,364-373`；pg 计数 | IR05 模式 + 模型耦合（绑定 D-4 的 YAML 侧）；W64 |
| D-24 | **REQ-SEC-03 已闭合**：`ecos_frontend/src/services/auth.ts` 存在且为全仓唯一实现（`grep 'function authHeaders\|const authHeaders' src` = auth.ts 1 处 + 2 处 `.test.ts` stub），token 键位采用 union 兼容四套历史 key。A3 退出仍未做：`KbDocChunkMapper.java:22` 仍 `INSERT INTO ecos_knowledge.kb_doc_chunk` | grep 命中/零命中 | PRD-07 §3.2 替换清单作废；REQ-DATA-08 仍开放；W65 |

### 1.2 事实导致的一句话结论

> 数据域当前是**"五套前缀 × 两套管道模型 × 两套 DQ 模型 × 双层 schema 镜像"的四重孪生结构**：新模型代码齐备但零数据且端点被 404 掩蔽，旧模型有数据但无治理语义；引擎 schema 的 23/20 张表是空镜像，真实读写全部经裸表名落在 `public`。本册的主任务不是"加功能"，而是**收敛孪生、补齐零存在的事实模型、把掩蔽的失败暴露出来**。

### 1.3 模块 → REQ → 落点追溯

| REQ | 优先级 | 本册落点 | 现状缺口 |
|:--|:--|:--|:--|
| DATA-01 事实模型 | P0 | A/F02-06 + E.2 五表 DDL + C.5 门禁 | 零存在（D-10） |
| DATA-02 DQ 发布门禁 | P0 | A/F02-07 + C.5 状态机 + E.3 | 双模型零数据（D-5/D-6） |
| DATA-03 导入与模板 | P0 | A/F02-06 + B.5 向导 + D.2 | 端点不存在 |
| DATA-04 DQ 告警接 monitor | P1 | A/F02-09/F02-10 + C.5.4 | 上下文空转 + 无升级定时器（D-20/21） |
| DATA-05 DriverManager 清偿 | P1 | A/F02-13 + C.3.4 | 5 处未清（D-12） |
| DATA-06 Git path-traversal | P1 | A/F02-12（转加固） | 已闭合，需加固（D-22） |
| DATA-07 parquet writer | P2 | C.3.5 + E.5 无 DDL | 未起 |
| DATA-08 A3 退出 | P1 | A/F02-14 + C.6.3 | kb 仍写 chunk（D-24） |
| DB-05/06、NF-07 | P0/P1 | E.1 归属 + C.4.5 事件 | 双镜像 + 裸表名（D-7） |
| 新增（本册提出） | P0 | F02-15 跨引擎契约 | D-14 静默空值 |

---

## 二、A 章 功能设计

> 每功能给出：意图 / 规格 / **可执行验收标识**（`mvn -Dtest=` 或 `*.spec.ts`，禁写"人工确认"）/ 依赖。

### F02-01 数据源接入与凭证治理（P0）

**规格**
1. **单套 CRUD**：收敛到 `/api/v1/datanet/datasource`（`DataSourceController`）为唯一承流面；`PMO45DataSourceController`（`/api/v1/datasource`、`/datasource`）保留为**兼容别名**，内部委托同一 `IDataSourceService`，响应体加 `deprecated=true`，兼容 ≥2 迭代后删除。`VersionPrefixRewriteFilter` 的 `/api/v1/datasource/ ↔ /datasource/` 双向条目（:58/:86）保留至别名删除同期。
2. **凭证治理**：`connection_config` 拆两列——`connection_config TEXT`（非敏感：jdbcUrl/schema/driver/props）+ `credential_encrypted TEXT`（AES-256-GCM，密文自带 12B IV，密钥经 01 册 `ecos_security_crypto_key` 主密钥引用，**禁自动建钥**）。写入路径只在"新建/改密"时接受明文 `password`，立即加密；读路径**永不回显**密码（返回 `credentialPresent: true|false`）。
3. 存量 2 行明文密码：迁移脚本用同一 GCM 密钥"读明文→加密→回写并置空 config 内 password"，脚本幂等（`WHERE credential_encrypted IS NULL`），迁移后 `grep '"password"' td_datasource` 实查 = 0。
4. 连接测试改 `runtime-access` 的 `testConnection(url, props)`（PRD-02 §五 裁定项，若缺方法先补 runtime-access，不豁免）。

**验收**
- `DatasourceCredentialCipherTest`：写入后 DB 实查无明文；回显 VO 无 password 字段；GCM 密文可解且 IV 每次不同。
- `DatasourceLegacyPasswordMigrationTest`：以 2 行明文样例跑迁移，断言幂等 + 断言 `connection_config LIKE '%password%'` = 0。
- `DatasourceSingleSurfaceArchTest`：源码断言 `/api/v1/datasource` 与 `/api/v1/datanet/datasource` 的 handler 均委托同一 service 方法（禁两份 SQL）。
- curl：`POST /api/v1/datanet/datasource` → `GET .../datasource/{id}` 响应无 `password` 键。

### F02-02 元数据采集与版本比对（P1）

**规格**：保留 `MetadataController` 全 17 个端点签名不变（API 只增不改）；`collect-async` 提交给 runtime-task（禁引擎自建线程池）；采集日志落 `ecos_metadata_collect_log`（V106 已有）；`version-history`/`version-diff` 的数据源改为**Git 归档单源**（F02-12），不再从库内快照表读。
**验收**：`MetadataCollectIdempotencyTest`（同 ds 二次采集 diff 为空）、`MetadataHistoryFromGitTest`（断言 history 列表来自 `{repoRoot}/metadata/{dsId}/history/`）。

### F02-03 管道 DAG 单模型收敛（P0，**R-2 a 已批准**）

**推荐目标态**：以 **JSON DAG 模型**（`ecos_pipeline_definition/node/edge` + `PipelineExecutionService` 12 节点执行器 + `/api/v1/pipeline/**`）为唯一承流模型。理由：①有数据（51 vs 4）；②前端画布与调试器（`PipelineFlowEditor`、`pipelineDebugApi`）绑定它；③节点目录 `PipelineNodeTypesCatalog` 与执行器 switch 已同源（铁律 §4.8.2）；④YAML 模型（`ecos_pipeline_task.yaml_content`）把 DAG 存成大文本，既不能校验也不能派生血缘（D-23）。
**收敛动作（只停写不删，IR03）**
1. `PipelineTaskController` 6 端点：GET 保留（只读，`deprecated=true`），POST/PUT/DELETE 返回 `ECOS-DATA-011` 并提示改用 definitions；
2. `PipelineGitService`（读 `ecos_pipeline_task.yaml_content`，:157/:227）改为读 `ecos_pipeline_definition` 的规范化 JSON（Git 归档内容 = 定义 JSON，见 F02-12）；
3. 4 行存量 task → 一次性脚本 `V168__migrate_yaml_pipeline_task.sql` 转成 definition（YAML→JSON 解析失败则整批拒绝并出报告，禁静默跳过）；
4. `PipelineExecutionEngine`（YAML 执行器）标 `@Deprecated`，`/definitions/{id}/execute` 不再回落它；
5. 血缘改从 definition 派生（F02-11）。
**验收**：`PipelineSingleModelArchTest`（源码断言 `PipelineExecutionEngine` 无 `/execute` 引用链）、`YamlTaskReadOnlyTest`（POST → 409/`ECOS-DATA-011`）、`MigrateFourYamlTasksTest`（4 行样例 → 4 definition，node/edge 计数一致）。

### F02-04 节点目录与能力一致性（P1）

**规格**：`PipelineNodeTypesCatalog` 每条目增 `available`（该类型在执行器 switch 是否有实现）与 `unavailableReason`；`available=false` 的节点在**创建时即拒**（`ECOS-DATA-012`），而非执行时才拒；`SOURCE_CDC` 当前 `available=false`（保留执行器显式拒绝为第二道）。目录版本号 `2.1.0` 递增，与 `GET /api/v1/pipeline/node-types` 响应同源。
**验收**：`NodeCatalogParityTest`（反射枚举执行器 switch 分支 ↔ 目录条目集合相等；不一致即 FAIL）、`UnavailableNodeRejectedAtSaveTest`。

### F02-05 五层载体登记与写入通道（P0，ADR-14 落点）

**规格**
1. `td_data_resource` 增列（只加）：`layer VARCHAR(16) NOT NULL DEFAULT 'RAW'`（已有列，改**非空约束**由新列 `layer_required_flag` 渐进——IR03 禁 ALTER 既有列，故用校验器承担）、`layer_bucket VARCHAR(30)`（SOURCE/RAW/CURATED/SEMANTIC/APPLICATION 的载体子类）、`carrier_ref VARCHAR(255)`（对象 key / 表名 / 视图名 / 图谱 label）、`storage_kind VARCHAR(20)`（PG/MINIO/DORIS/CLICKHOUSE/NEO4J/PG_VECTOR）；
2. **载体存在性校验**：每层至少 1 个已登记载体方可对外声称该层可用。SEMANTIC 当前 0 载体 → `/api/v1/engine/data/layers` 响应增 `declared:false`（与 D-9 对齐），禁前端显示为"有数据"；
3. **唯一写通道**：其他引擎写业务域数据一律 `POST /api/v1/datanet/write-channel`（见 D.2），入参必带 `layer` + `carrierRef` + `traceId` + `idempotencyKey`；data-engine 负责：schema 限定名解析（MC06）、列类型白名单校验（MC02）、审计事件（ST06）、`PipelineEvent` 发布。cognitive 的确定性预测产物（ADR-14）即经此通道落 `APPLICATION`；
4. Doris∨ClickHouse 按 `dw.olap.engine` 二选一（MC04），仅承载业务域 DW/APPLICATION，控制域禁用。
**验收**：`LayerCarrierPresenceTest`（无载体层必须 `declared=false`）、`WriteChannelRejectsUnlistedColumnTest`（裸 JSON 列参与 WHERE → 拒）、`NonOlapControlWriteGuardTest`（控制域写 Doris → 拒，MC04）、`IdempotentWriteReplayTest`（同 key 重放不产生第二行）。

### F02-06 业务事实模型五表 + 导入/模板/批次/action-outcomes（P0）

**规格**（DDL 见 E.2，规则见 PRD-02 §1.3，本册改写其违规项）
1. 五表落 **业务域 `ecos_dw`**（CURATED 层），非 `ecos_data`（后者是控制域引擎 schema）——`ecos_biz_project_attribution / ecos_biz_stage_fact / ecos_biz_resource_fact / ecos_biz_cost_fact` 四表 + `ecos_forecast_input_snapshot`（写权归 05 册预测运行，物理归本域）；
2. 端点：`POST /api/v1/datanet/facts/{factType}/import`、`GET .../facts/{factType}/template`、`GET .../facts/{factType}`、`GET .../facts/batches/{batchId}`、`POST .../facts/action-outcomes`，全部过三滤波器 + 分册 01 `decide` 管道；
3. **活跃唯一性方案**（取代 PRD 草案的裸唯一索引，兼容"更正=新行+旧行逻辑删除"规则 1）：新列 `is_active SMALLINT`——存活行 =1、被替代行置 NULL；唯一索引 `uniq_*_active (业务键…, is_active)`，NULL 不参与唯一判定（PG/MySQL/达梦一致语义），从而"一个业务键只有一行活跃 + 无限历史行"；
4. `dq_status` 状态机 `PENDING→PASSED→PUBLISHED / →REJECTED`，`PUBLISHED` 后业务字段 UPDATE → `ECOS-DATA-021`（409）；
5. 关账期写入拒绝（联动 09 册 FC-05 period lock），错误码 `ECOS-DATA-022`；
6. `staff_ref` 工号哈希脱敏存储；金额列**不加密**，但**必须**先在 `docs/40-实现/列级加密豁免登记表-2026-09-28.md` 逐列登记（ST03-A，未登记即视同违规）。
**验收**：`FactImportAcceptRejectTest`（216 行样例全 PUBLISHED）、`FactBadDataTripleRejectTest`（DQ-F01/F02/F09 各 1 行，rejected 含 rowNo/field/ruleId/suggestion）、`PublishedRowImmutableTest`（409）、`ClosedPeriodWriteDeniedTest`、`ActiveFlagUniquenessTest`（同键第二行存活 → 索引拒；旧行置 NULL 后新行可入）、`AmountExemptionRegisteredTest`（登记表缺条目 → 启动校验 FAIL）。

### F02-07 DQ 发布前门禁 11 规则 + 待处理队列（P0）

**规格**：11 条规则以 **DQ 治理单模型**实现（`dq_rule` 定义态 + `dq_rule_check` 运行态），规则类型 = 内置枚举（`UNIQUENESS/VALIDITY/CONSISTENCY/COMPLETENESS/ACCURACY/FRESHNESS` 六维 + `DQ-F01~F11` 参数化模板），**不引入表达式沙箱**；导入链路 = 行级校验（DQ-F01~F11）→ rejected 不入库直接返回 → accepted 落表 `PASSED` → 批次完整性（覆盖率阈值）→ publish。异常行进**待处理队列**（`dq_work_order` 简化态），可在线改后重传，重传幂等（同 batchId 按业务键去重提示）。**红线**：禁任何"补默认值/补 0 以通过校验"的代码路径（ArchTest 守）。
**验收**：`DqRuleF01ToF11Test`（11×2=22 用例）、`NoSilentDefaultFillArchTest`（源码禁 `putIfAbsent(.*, 0)` 类填充在 fact 写入路径）、`PendingQueueActionableTest`（队列行含 ruleId + 定位字段 + 修复入口）。

### F02-08 DQ 治理单模型收敛与错误掩蔽修复（P0）

**规格**
1. **先暴露再修复**：`GlobalExceptionHandler` 对 `DataAccessException/MyBatisException` **不得**再落 404；改 `500 + ECOS-DATA-031`（附 traceId），仅真 `NoHandlerFound/NoResourceFound` 才 404（分册 00 D.5 错误码表同步）；
2. 修 `DqWorkOrderServiceImpl:242` 缺 `FROM ecos_dq.dq_work_order`；
3. 补 `ecos_dq.dq_score_asset`（实查缺表，V112 声明未落库）→ 新增 `V165.1__dq_score_asset_repair.sql`（幂等 `IF NOT EXISTS`）；
4. 17 条 legacy 规则迁入治理模型：`V166__dq_rule_legacy_migration.sql` 从 `public.ecos_dq_rule` 映射（`rule_type→category/rule_type`、`params→parameters_json`、`target_entity/field→target_table/target_field`），状态一律 `DRAFT` 待人工签核，**不做语义等价保证**，迁移报告入库；
5. `public.ecos_dq_rule`/`ecos_dq_issue`：读端点 `/api/v1/ecos/dq/**` 保留 ≥2 迭代 + `deprecated=true`；写端点停用；
6. 前端 `pages/data-quality/api.ts` 删除"404 回落 legacy"分支（改为显式错误提示）。
**验收**：`NoFourOhFourMaskingTest`（gateway：注入一个必失败 SQL 的探针端点 → 断言响应 500 且 body 有 `ECOS-DATA-031` + traceId）、`WorkOrderListSqlContractTest`（对 `WO_COLUMNS + whereSql` 组装结果做字符串断言含 `FROM ecos_dq.dq_work_order`，并跑真实查询）、`DqGovernanceEndpointReachabilityTest`（`/api/v1/dq/rules|work-orders|scores/system|health/selfcheck` 四路 200 或明确业务错误，禁 404）、`DqLegacyMigrationParityTest`（17→17，字段映射逐列断言）。

### F02-09 DQ 评分上下文真实化（P1）

**规格**（保持"Phase 2 只读 `dq_rule_check` 不拉样本"裁定不变）：`buildContext` 的 `connectionConfig/sampleFailures/lastCheck/totalRows/failedRows` 从**上一次 `dq_rule_check` 行 + 管道执行统计**取真值，禁空 Map；目标表不可达时显式置 `contextIncomplete=true` 并使评分降级为 `UNKNOWN`（不是"高分通过"）。
**验收**：`ScoringContextHydrationTest`（预置 1 条 check 行 → 断言 ctx 各字段来自该行，非空）、`IncompleteContextUnknownScoreTest`。

### F02-10 DQ 告警升级时限状态机（P1）

**规格**：升级驱动**归 runtime-task**（分册 00 C.4，禁 data-engine 自建 `@Scheduled`）：注册周期任务 `dq-alert-escalation`（每 1min 扫 `status='OPEN'/'ACKED'` 且超时的告警），P3 24h/P2 1h/P1 15min/P0 5min 响应时限，`P2 超 5min 未 ack → P1`、`P1 超 15min 未 ack → P0`，升级动作经 runtime `IAlertService` 推送并在 `dq_alert_record.escalated_to` 留痕；runtime-monitor 不可用时按分册 00 降级矩阵仅落库 + 补投。
**验收**：`DqAlertEscalationTimerTest`（可控时钟推进 6min → 断言 P2→P1 且推送调用 1 次）、`EscalationSchedulerRegisteredInRuntimeTaskTest`（断言 data-engine 内无 `@Scheduled`）。

### F02-11 血缘自动派生（P1）

**规格**：血缘**从 definition 派生**，不再扫 YAML：管道执行成功后发 `PipelineEvent`，消费端把 `source(table) → node → sink(table)` 落成 `ecos_data_lineage_node/edge`（限定名 + `#{}` 参数化，消除 `ILIKE` 全表扫与 `LIMIT` 拼接）；`/api/v1/engine/data/lineage/topology/rebuild` 保留但改为增量重建 + 分页参数化。跨引擎只读经 `/api/v1/engine/data/lineage` 契约。
**验收**：`LineageDerivationTest`（跑一条 SOURCE_JDBC→TRANSFORM_SQL→SINK 管道，断言生成 3 节点 2 边）、`NoYamlLineageScanArchTest`（禁 `yaml_content ILIKE`）。

### F02-12 Git 归档加固（P1，原 REQ-DATA-06）

**规格**：`GitRepoRootResolver` 增 ①`Files.exists/isDirectory/writable` 存在性校验（加载时）②`toRealPath` symlink 解析后再 `startsWith`；`MetadataCollectGitArchive`、`PipelineGitService`、datanet `GitController` **三处全部改走 resolver**（现 resolver 已被归档侧使用，需补 `GitController` 与 pipeline 侧覆盖）；拒绝时 warn 日志含 traceId + 业务异常（不裸 500）。
**验收**：`RepoRootSymlinkEscapeTest`（symlink 指向根外 → 拒）、`RepoRootMissingDirectoryTest`、`AllGitCallersUseResolverArchTest`（源码禁 `Paths.get(repoRoot` 于 resolver 之外）。

### F02-13 SQL 控制台与元数据探查经 runtime-access（P1）

**规格**：D-12 的 5 处 `DriverManager` 全部替换为 `runtime-access` 的 `JdbcConnector.executeQuery / metadata / testConnection`；`MetadataRowCountService` 私有 `DriverManagerConnection` 删除；EXACT 模式行计数走 `JdbcConnector`；ArchUnit 增规则 **ARCH-06**：业务代码禁 `java.sql.DriverManager`（合法白名单 = `runtime-access/**`）。
**验收**：`NoDriverManagerArchTest`、`SqlConsoleRegressionPgTest` + `SqlConsoleRegressionDorisTest`（环境可用时）、`ConnectionTestViaRuntimeTest`。

### F02-14 RAW parquet 与 A3 过渡态退出（P2 / P1）

**规格**
1. `SINK_MINIO` 节点 `format: csv|parquet`：`parquet` → 经 runtime-access DuckDB `COPY (…) TO 'x.parquet' (FORMAT PARQUET)` 到临时文件再上传 MinIO；未指定 → csv + warn（既有行为保持）；其他显式值 → 明确拒绝；`dw.lake.storage_format=parquet` 时管道默认 parquet。**不引入 parquet-mr 新依赖**。key 规范不变 `raw/structured/{source}/{table}/dt=YYYY-MM-DD/{table}_{ts}.parquet`；
2. A3 退出（REQ-DATA-08）：`KbDocChunkMapper.java:22` 的 `INSERT INTO ecos_knowledge.kb_doc_chunk` 停写；kb 改读 `ecos_dw.doc_chunk`（REST），仅保留向量索引写入；双跑 1 批次比对 chunk 数量 + 内容 hash，diff=0 才切；回滚开关 `ecos.kb.ingest.legacy_chunk_write=true`（过渡期后删）。
**验收**：`ParquetRoundtripTest`（DuckDB 读回行数/列型一致 + key 合规）、`KbChunkWriteGoneArchTest`（grep `INSERT INTO ecos_knowledge.kb_doc_chunk` = 0）、`DualRunDiffZeroTest`。

### F02-15 跨引擎读数据契约与"禁静默空值"（P0，本册新增需求）

**规格**：①所有跨引擎取数 URL 改 `ServiceEndpointResolver` 两态寻址（monolith :8080 / service :18082），删除 `ecos.datanet.base-url` 的 `:18082` 默认值；②`DataNetResourceClient` 路径改真实端点 `/api/v1/datanet/assets/{assetId}/fields`；③**调用失败必须 fail-loud**：抛 `ECOS-DATA-041`（依赖不可用），**禁"返回空列表"**（D-14 现状会让本体/知识构建在缺数据时"看起来成功"）；④调用方需在 UI 明示降级。
**验收**：`CrossEngineFailLoudTest`（对端 503 → 断言异常而非空集合）、`EndpointResolverUsageArchTest`（禁 `http://localhost:180` 字面量于引擎内）、`DataNetResourcePathReachableTest`（curl 真断言 200）。

---

## 三、B 章 界面设计（数据工作台 + DQ 中心）

> 规范：8 要素文本/表格（R5 禁图片入库）；每 Tab 独立文件、单文件 ≤800 行（现状 `api.ts` 1656 行、`ConnectionsTab.tsx` 1227 行 **违规**→W66）；`useTheme()` token、`lucide-react` 图标、`t("data.*")` i18n。

### B.1 连接管理 Tab（`tabs/ConnectionsTab.tsx`，拆为 4 文件）

| 要素 | 规格 |
|:--|:--|
| 目的 | 数据源全生命周期（建/测/用/停） |
| 入口 | 数据工作台 → 「连接」Tab（HashRouter `#/data-workbench/connections`） |
| 布局 | 列表（名称/类型/主机掩码/状态/最近测试/引用管道数）+ 右侧抽屉详情 + 顶部「新建」向导按钮 |
| 内容 | 主机/库/schema 展示，**凭据区仅显示"已配置/未配置"两态**，永不回显密码（F02-01）；`引用管道数>0` 时删除按钮禁用并提示阻塞项 |
| 交互 | 新建=3 步向导（连接信息→凭证→测试确认）；测试=行内按钮显示耗时与错误摘要；失败态可复制诊断码 |
| 状态 | 空态（引导新建）/加载（骨架行）/错误（`ECOS-DATA-0xx` + 复制 traceId）/无权限（`ECOS-AUTH-*` → 提示申请准入）/离线（BFF 不可达横幅）/并发冲突（`version_no` 不符 → 409 提示刷新）/待测（新建未测标 `UNTESTED`） |
| i18n/主题 | `data.connections.*` 全 key 化；颜色走 token；4 主题均需对比度达标 |
| 验收 | `connections.spec.ts`：新建→测试→编辑→删除阻塞 4 段；断言响应体无 `password` 键且页面 DOM 无密码文本 |

### B.2 管道编排（`pipeline-editor/`，画布 + 属性面板 + 调试面板）

| 要素 | 规格 |
|:--|:--|
| 目的 | 以节点连边表达数据流，可调试、可版本化 |
| 入口 | 「管道」Tab → 选择/新建定义 → 画布 `#/data-workbench/pipeline/{definitionId}` |
| 布局 | 左节点面板（按 `available` 分组；不可用节点灰显 + tooltip 原因）/中画布（`@xyflow/react`）/右属性面板（选中节点配置表单，字段由 `node-types` 的 schema 驱动）/底调试与运行日志抽屉 |
| 内容 | 节点名/类型/必填标记/上游行数；边=数据流方向；保存时展示 DAG 校验结果（环/悬空/必填缺失） |
| 交互 | 拖入→自动选中→右侧填参；`step/continue/reset/preview` 调试走 `/api/v1/pipeline/debug/sessions`；保存调 `PUT /definitions/{id}`；Git 提交/分支/版本对比入口（联动 F02-12 与 08 册 `gitService.ts` 单通道） |
| 状态 | 空画布引导 / 校验失败（定位到节点）/ 运行中（进度条 + 可取消）/ 单节点失败（红标 + 错误行内联）/ 不可用节点拖入（即时拒绝，不进入保存）/ 并发编辑冲突（409 提示他人版本）/ 调试会话过期（提示重开） |
| 约束 | 单模型（F02-03）：UI 不再出现"任务(YAML)"入口；`SOURCE_CDC` 灰显 |
| 验收 | `pipeline-editor.spec.ts`：拖 3 节点连 2 边→保存→执行→查看 step；再拖 `SOURCE_CDC` 断言即时拒绝 + console 无 error + network 无意外 4xx/5xx（铁律 V4 三项） |

### B.3 DQ 规则中心 / 告警中心 / 工单（`pages/data-quality/`，9 Tab 保留）

| 要素 | 规格 |
|:--|:--|
| 目的 | 规则治理（定义→签核→生效→退役）+ 异常闭环（告警→工单→修复→验证→关闭） |
| 入口 | 「质量」Tab 内 6 子页（规则中心/维度/调度/告警中心/工单/自检），报表与版本时间线为抽屉 |
| 布局 | 列表 + 过滤器（category/domain/status）；规则详情含版本时间线；工单详情含状态机动作条 |
| 内容 | 规则：编码/维度/目标表字段/参数（**仅允许绑定变量，禁自由 SQL**，对齐 01 册谓词编辑器约束）；告警：级别/响应时限/是否升级/traceId/修复入口链接；工单：受理人/修复动作/验证结论/RCA 摘要 |
| 交互 | 提交→审批→生效/废弃/替代；告警 ack/resolve；工单 assign/start/resolve/verify/close/reject/escalate/run-rca；全部动作二次确认 |
| 状态 | **新模型零数据是现状**（D-5）：首屏必须显示"治理模型尚未有数据（17 条旧规则待迁移）"引导，而非空表；接口失败显示真实错误码（禁再回落 legacy，F02-08-6）/加载/空/无权限/待审队列高亮/升级倒计时 |
| i18n/主题 | `dq.*` |
| 验收 | `dq-rule-center.spec.ts`（草稿→签核→生效→替代 全链 + 版本时间线断言）、`dq-alert-escalation.spec.ts`（P2 超时后 UI 显示已升级 P1） |

### B.4 五层载体浏览器（新增，落 `tabs/` 新文件）

| 要素 | 规格 |
|:--|:--|
| 目的 | 让"数据湖五层"从口号变成可核对的载体清单 |
| 入口 | 「资产」→ 分层视图（`GET /api/v1/engine/data/layers`） |
| 布局 | 5 个层卡片（声明态/载体数/样例数/最近更新时间）+ 展开后载体表 + 行采样抽屉 |
| 内容 | `declared=false` 的层（SEMANTIC/APPLICATION，现状 0 载体）必须显示"该层暂无登记载体"；每载体显示 `storage_kind` 与 `carrier_ref`；行采样前先 `decide`（RLS/CLS/mask） |
| 交互 | 点层→列载体→点载体→采样行/元数据；注册载体（管理员，走 D.2 `registerLayerCarrier`） |
| 状态 | 未声明层（灰 + 说明）/采样超限提示/采样为空但表存在（区分"无行"与"无权")/加载/错误/离线 |
| 验收 | `layer-carrier-browser.spec.ts`：SEMANTIC 层断言文案为"未声明"而非"0 条数据"；采样请求断言携带 Bearer 且响应列被裁剪 |

### B.5 事实导入向导（新增，场景批次 A 主入口）

| 要素 | 规格 |
|:--|:--|
| 目的 | 让业务把 216 行经营事实导入并通过 DQ 门禁 |
| 入口 | 场景工作台 → 数据准备 → 「导入事实」（四类：归属/环节/资源/成本） |
| 布局 | 步骤条（选类型→下载模板→上传→逐行错误表→确认发布）+ 批次列表 |
| 内容 | 模板首行表头含单位、次行示例、必填标记；错误表列 = 行号/字段/规则 ID/消息/修复建议，可下载（原行 + `error_rule`/`error_message` 两列） |
| 交互 | 在线逐行改后重传（幂等）；发布仅对 `PASSED` 行；关账期行显著标灰且不可提交 |
| 状态 | 全拒（不入库，直接展示错误）/部分接受（两态分别统计）/批次进行中（轮询 `batches/{batchId}`）/重复上传（去重提示）/无权限导出（`decide` 拒绝时禁用下载） |
| 验收 | `fact-import.spec.ts`：正常 216 行发布 + 坏数据三连拒 + 错误行下载含原因列 + 修复后重传 accepted（对应 PRD-02 §1.5/§三 验收） |

### B.6 血缘视图（`lineage/nodes.tsx` 保留）

| 要素 | 规格 |
|:--|:--|
| 目的 | 表级/字段级上下游与影响面 |
| 入口 | 「血缘」Tab；影响分析 = 右键"影响分析" |
| 布局 | 中心节点 + 上游/下游分层；深度滑杆（默认 3）；命中路径高亮 |
| 内容 | 节点显示 `schema.table`（限定名，来自 F02-11 派生）+ 载体层标签；边显示来源管道 ID（可跳定义） |
| 交互 | 拓扑重建按钮（异步 + 进度）；影响分析返回受影响定义清单 |
| 状态 | 派生为空（提示"该表未被任何管道引用"，区别于"血缘服务不可用"）/大图截断（限节点数 + 提示）/加载/错误/权限裁剪（无权表以占位节点显示"存在但不可见"→ 由 01 册决定，禁泄露存在性时显示为完全缺失） |
| 验收 | `lineage.spec.ts`：跑一条管道后断言 3 节点 2 边可见；无权表断言按 `denyIfEmpty` 完全缺失 |

### B.7 前端文件与规模约束（改造项 W66）

| 现状 | 目标 |
|:--|:--|
| `data-workbench/api.ts` **1656 行** | 按域拆 5 个：`apiDatasource.ts`/`apiMetadata.ts`/`apiPipeline.ts`/`apiFacts.ts`/`apiLayers.ts`，共享 `httpClient.ts` |
| `tabs/ConnectionsTab.tsx` **1227 行** | 拆 `ConnectionsTab`(容器)+`ConnectionsTable`+`DatasourceWizard`+`CredentialPanel` |
| `pipeline-editor/PropertyPanel.tsx` 714 / `PipelineFlowEditor.tsx` 683 / `FlowCanvas.tsx` 596 | 均 <800，保持；新增节点配置表单按 `node-types` schema 驱动（禁为新节点类型加 `if` 分支） |

---

## 四、C 章 技术设计（控制流 / 数据流）

### C.1 分层与承流

```
ecos_frontend ──(BFF server.ts / vite dev)──▶ gateway :8080 ─┬─ 认证/信任头/限流/重写/审计埋点（00 册）
                                                            ├─ @ComponentScan engine.data.*  ← 今日唯一承流态（ADR-15 S0）
                                                            └─ (S3 目标) 路由 → datanet :18082
datanet :18082（制品）= data-engine-impl 全量 + runtime 横切 + 自身 3 controller
                         现状缺口：excludeFilters 掉 DataEngineConfigController（D-2）
```

**S2/S3 前置门禁（本册对 ADR-15 的补强要求）**：datanet 独立承流前必须完成 ①信任链 `HeaderAuthInterceptor`（00 册 W02）②端点差集清零（`DataEngineConfigController` 依赖的 `SysConfigService` 改 sysman REST 门面，见 00 册 C.5）③匿名/准入清单以裸路径登记（01 册 W39 机制）。验收 = `DatanetEndpointParityTest`：对 route-manifest 数据域全部路径，分别在 :8080 与 :18082 上断言"非 404 且响应体结构一致"。

### C.2 前缀与路由单源

**C.2.1 现状 5 套前缀 → 目标 2 套**（登记进 `route-manifest.json`，gateway/BFF/vite/tests 同源消费）

| 目标前缀 | 承流内容 | 被合并的现状前缀 | 兼容期 |
|:--|:--|:--|:--|
| `/api/v1/datanet/**` | 数据源/元数据/目录/资产/采集/湖/事实/写通道 | `/api/v1/datanet/*`、`/datanet/*`、`/api/datanet/metadata/*`、`/api/v1/datasource/*`、`/datasource/*` | 别名保留 ≥2 迭代（`deprecated=true`） |
| `/api/v1/engine/data/**` | 引擎自身：health/status/settings/layers/lineage/copilot/functions/query/transform/udf/pipeline(task 侧只读) | 同左（已符合） | — |
| `/api/v1/pipeline/**` | **DAG 定义 + 执行 + 调试**（保持，因已承流） | `/api/v1/engine/data/pipeline/tasks`（转只读别名） | 见 F02-03 |
| `/api/v1/dq/**` | DQ 治理（规则/检查/告警/工单/评分/调度/报表/自检） | `/api/v1/ecos/dq/**`（legacy 只读）、`/api/dq/**`（datanet 副本） | legacy ≥2 迭代 |
| `/api/v1/lineage/**`? | **不新增**：血缘统一挂 `/api/v1/engine/data/lineage`，`/api/lineage` 保留兼容 | `/api/lineage/impact`、`/api/lineage/parse` | ≥2 迭代 |

**C.2.2 三滤波器写法（数据域每个新端点必过）**：①`VersionPrefixRewriteFilter` 是否需要映射（数据域新裸前缀一律**不**加条目，直接用 `/api/v1/...`，避免再制造双向映射）；②`SecurityConfig` 匿名面**不加**（默认 DENY）；③`ClearanceInterceptor` 豁免表**不加**——数据域全部要求认证 + 准入等级（`/api/v1/datanet/facts/**` 要求 L2，`/api/v1/dq/**` L1，`/api/v1/engine/data/settings` L3）。验收 = `DataEndpointTriFilterTest`（对 route-manifest 每条数据域路径断言：无 token→403、L 不足→403、带权→非 403）。

**C.2.3 两态寻址**（消 D-14/D-5 之 S-5）：所有引擎内 `http://localhost:808x` 字面量 → `ServiceEndpointResolver.resolve("security"|"datanet"|"workspace", path)`；`monolith` 态返回 :8080，`service` 态返回 180xx；由 `ecos.route.<service>=monolith|service` 控制，默认 monolith。

### C.3 五层数据流（端到端）

```
[外部源库]
   │ SOURCE_JDBC/SOURCE_REST/SOURCE_CSV（经 runtime-access JdbcConnector，F02-13）
   ▼
SOURCE 登记（td_data_resource, layer=SOURCE, carrier_ref=jdbc:table）
   │ 管道 executeSinkMinio（近源层不可变留痕）
   ▼
RAW  MinIO 对象 raw/structured/{source}/{table}/dt=YYYY-MM-DD/{table}_{ts}.{csv|parquet}
   │ + td_data_resource(layer=RAW, storage_kind=MINIO)
   │ TRANSFORM_SQL / TRANSFORM_UDF / JOIN / TRANSFORM_DOC_PARSE
   ▼
CURATED  ecos_dw.<table>（业务域 PG；非结构化 = ecos_dw.doc / doc_chunk）
   │ 本体绑定（03 册）+ 向量索引（04 册）
   ▼
SEMANTIC  I 层：ecos_ontology.*（关系表）∨ Neo4j（enterprise+）；载体登记 storage_kind=NEO4J|PG
   │ ADR-14：cognitive 确定性结果经 /write-channel 落
   ▼
APPLICATION  ecos_dw.<app_table>（预测结果/报表视图），storage_kind=PG|DORIS|CLICKHOUSE（dw.olap.engine 二选一）
```

**C.3.4 Driver 收口**：本域禁一切 `DriverManager`/自建池；MinIO/Doris/CH/Git/DuckDB 全经 `runtime-access`（铁律 §2.5-6）。
**C.3.5 parquet 分支**：见 F02-14-1。
**C.3.6 写入通道唯一性**：外部引擎**不得**直连业务域表；`/write-channel` 是唯一入口，内部再做 MC02 类型校验、schema 限定名注入（MC06）、ST06 审计、`PipelineEvent` 发布。验收 `BusinessDomainWriteArchTest`（ArchUnit：非 data-engine 模块禁有 `ecos_dw` 的 Mapper）。

### C.4 管道执行控制流

```
POST /api/v1/pipeline/definitions/{id}/execute
  ├─ 1 载入 definition + node/edge（限定名，Mapper，非 JdbcTemplate）
  ├─ 2 DAG 静态校验：环 / 孤立节点 / 必填配置 / 节点 available → 失败 ECOS-DATA-012/013
  ├─ 3 幂等：traceId 贯穿；执行记录 run=PENDING 入库
  ├─ 4 拓扑排序逐节点执行（PipelineExecutionService switch，12 类型）
  │     ├─ 节点前后写 ecos_pipeline_step_run（行数/耗时/错误）
  │     ├─ SOURCE_* 经 runtime-access；SINK_MINIO → MinIO；SINK → DW 表
  │     └─ 每节点成功发 PipelineEvent（Kafka ecos.* topic，runtime-event 收口）
  ├─ 5 失败：节点标红 + run=FAILED + 错误码 + 可取消；不吞异常
  ├─ 6 血缘派生（消费 PipelineEvent，F02-11）
  └─ 7 审计：POST security /audit（eventType=PIPELINE_EXECUTE），写动作必发 ecos.audit
调试会话（/pipeline/debug/sessions）与主执行器**同源复用**（现状已如此：PipelineDebugService:91 注释），禁第二套实现。
```

### C.5 DQ 门禁控制流

```
import(≤100 行/批 EN03) → 逐行 DQ-F01~F11（内置规则枚举，禁表达式）
   ├─ rejected：不入库，返回 [{rowNo,field,ruleId,message,suggestion}]
   └─ accepted：落表 dq_status=PASSED（is_active=1）
批次完整性：覆盖率 = 已提交期间/目标期间，缺口>10% → WARN 入待处理队列（不阻断）
publish：仅 PASSED→PUBLISHED；PUBLISHED 行 UPDATE → 新行 + 旧行 is_active=NULL + 审计
预测快照引用：仅 PUBLISHED（05 册消费时 SQL 带 dq_status='PUBLISHED'，由 decide 管道叠加 RLS）
告警：check 未通过 → dq_alert_record(P 级) → runtime IAlertService → runtime-monitor 通道
      升级：runtime-task 周期任务按超时改级（F02-10）
```

### C.6 元数据与 Git 归档数据流

1. 采集：`collect-async` → runtime-task → 元数据快照 JSON；
2. 归档：`{repoRoot}/metadata/{datasourceId}/metadata.json` + `history/{yyyyMMdd_HHmmss}.json`（resolver 单源 + symlink 加固）；
3. 版本比对：`version-history`/`version-diff` **只读 Git 归档**（库内不落历史版本，铁律 §2.5 版本×Git 归档）；
4. 管道定义归档：`{repoRoot}/pipeline/{definitionId}`（改造后，F02-03-2）；
5. 前端 Git UI 仅经 `services/gitService.ts` 且只传 `repositoryId`（前端规范 §十）。

### C.7 跨引擎读数据契约（消 D-14）

| 调用方 | 需要的数据 | 目标契约 | 失败语义 |
|:--|:--|:--|:--|
| ontology（03 册） | 表字段/资源元数据 | `GET /api/v1/datanet/assets/{assetId}/fields` | `ECOS-DATA-041` fail-loud |
| kb（04 册） | `doc_chunk` 读 + 登记原文/文本 | `GET /api/v1/datanet/datalake/objects`、`POST .../datalake/unstructured`、`POST .../metadata/resources` | 同上（登记失败必须中断摄取，不得"继续但无数据"） |
| cognitive（05 册） | 事实取数 + 结果写入 | `decide` → 查询；`POST /api/v1/datanet/write-channel` | 快照引用不到 PUBLISHED 行 → 运行失败非零结果 |
| workspace/场景（07/09） | 事实分页 + 导出 + 审计包 | `GET .../facts/{factType}` + 01 册导出通道 | 无权空集不泄露存在性 |

### C.8 降级与失败语义矩阵

| 依赖不可用 | 本域行为 | 错误码 | 是否允许"成功" |
|:--|:--|:--|:--|
| security-engine | 查询/导入/导出全部 DENY（01 册 §1.5-2） | `ECOS-SEC-501` | **禁** |
| runtime-access（JDBC Driver） | 管道/SQL 控制台直接失败 | `ECOS-DATA-051` | 禁 |
| MinIO（RAW 层） | 采集/SINK_MINIO 失败；管道整体 FAILED，不留半对象 | `ECOS-DATA-052` | 禁 |
| Doris/CH（ultimate 档 OLAP） | 降级读 PG CURATED（性能告警），写 APPLICATION 失败即失败 | `ECOS-DATA-053` | 写禁、读可降级并标 `degraded=true` |
| Neo4j（enterprise 档 SEMANTIC） | 降级 PG 关系表（ADR-11 双形态） | — | 可，需标形态 |
| Kafka（事件/审计） | 审计走 `ecos_runtime_audit_retry`（00 册 E），SLA ≤15min | `ECOS-DATA-054` | 可（落库 + 重试） |
| runtime-monitor | DQ 告警仅落库 + 补投（00 册降级矩阵） | `ECOS-DATA-055` | 可 |
| Git 仓库根不可用 | 元数据/管道归档失败；版本读失败但运行不阻断 | `ECOS-DATA-056` | 可（明确提示） |
| DuckDB（parquet） | 拒绝 parquet，维持 csv | `ECOS-DATA-057` | 可 |

### C.9 性能与容量基线

| 项 | 基线 |
|:--|:--|
| 导入批量 | ≤100 行/批（EN03）；216 行样例 <3s |
| 事实分页 | 默认 20，上限 200；RLS 谓词经绑定参数，禁全表扫 |
| 管道节点数 | 定义 ≤100 节点；执行 step_run 行数采样 ≤1000/节点 |
| 血缘重建 | 单次 ≤2000 定义；增量优先；`LIMIT` 参数化 |
| DQ 调度 | 单批 ≤200 规则；`dq_rule_check` 保留 90 天（只加列 `expired_flag`，不删） |

---

## 五、D 章 接口设计

### D.1 错误码（数据域扩展，接 00 册 D.5 / 01 册 D.1）

| 码 | HTTP | 语义 |
|:--|:--:|:--|
| `ECOS-DATA-011` | 409 | YAML task 模型只读，请改用 pipeline definitions |
| `ECOS-DATA-012` | 400 | 节点类型不可用（未实现）/必填配置缺失 |
| `ECOS-DATA-013` | 400 | DAG 非法（环/孤立） |
| `ECOS-DATA-021` | 409 | PUBLISHED 事实行不可改业务字段（更正=新行） |
| `ECOS-DATA-022` | 409 | 期间已关账，拒绝写入 |
| `ECOS-DATA-023` | 400 | DQ 门禁拒绝（携带 rejected 明细） |
| `ECOS-DATA-031` | 500 | 数据层 SQL/映射异常（**不再掩蔽为 404**，附 traceId） |
| `ECOS-DATA-041` | 503 | 跨引擎取数依赖不可用（fail-loud，禁空值降级） |
| `ECOS-DATA-05x` | 503 | 降级矩阵（access/MinIO/OLAP/DQ 告警/Git/DuckDB） |
| `ECOS-DQ-101` | 409 | 规则状态机非法迁移（DRAFT→APPROVED 需签核人） |
| `ECOS-DQ-102` | 400 | 规则表达式含禁用构造（自由 SQL/裸表名） |
| `ECOS-DQ-111` | 404 | 治理记录不存在（真实 404，与 031 严格区分） |

### D.2 REST 清单（新增只增不改；operationId 小驼峰唯一）

| Method & Path | operationId | 备注 |
|:--|:--|:--|
| `POST /api/v1/datanet/datasource` | createDatasource | 密码即刻加密 |
| `GET /api/v1/datanet/datasource` | listDatasources | 不回显凭据 |
| `POST /api/v1/datanet/datasource/test` | testDatasourceConnection | 经 runtime-access |
| `POST /api/v1/datanet/ingest/run` | runSourceIngest | 既有，保持 |
| `POST /api/v1/datanet/facts/{factType}/import` | importBusinessFacts | ≤100 行 |
| `GET /api/v1/datanet/facts/{factType}/template` | getFactImportTemplate | CSV+单位+示例 |
| `GET /api/v1/datanet/facts/{factType}` | listFacts | decide→RLS/CLS/mask |
| `GET /api/v1/datanet/facts/batches/{batchId}` | getFactBatch | 含错误行下载 |
| `POST /api/v1/datanet/facts/action-outcomes` | writeActionOutcome | W→D 反馈链 |
| `POST /api/v1/datanet/write-channel` | writeBusinessDomainRows | 唯一外部写入口（layer+carrier+idempotencyKey） |
| `GET /api/v1/engine/data/layers` | getLayerCarriers | 增 `declared` |
| `POST /api/v1/engine/data/layers/{layer}/carriers` | registerLayerCarrier | L3 |
| `GET /api/v1/engine/data/lineage` | getLineageGraph | 参数化 |
| `POST /api/v1/engine/data/lineage/topology/rebuild` | rebuildLineageTopology | 增量 |
| `GET /api/v1/pipeline/node-types` | getPipelineNodeTypes | 增 available/unavailableReason |
| `PUT /api/v1/pipeline/definitions/{id}` | savePipelineDefinition | 单模型 |
| `POST /api/v1/pipeline/definitions/{id}/execute` | executePipelineDefinition | — |
| `POST /api/v1/dq/rules` | upsertDqRule | 定义态 |
| `POST /api/v1/dq/rules/{id}/submit` | submitDqRule | 状态机 |
| `POST /api/v1/dq/rules/{id}/approve` | approveDqRule | 双人分离（Reviewer 核对） |
| `POST /api/v1/dq/evaluate` | evaluateDqRule | ctx 真实化 |
| `GET /api/v1/dq/work-orders` | listDqWorkOrders | **修 FROM** |
| `POST /api/v1/dq/work-orders/{id}/escalate` | escalateDqWorkOrder | 手动补充自动 |
| `GET /api/v1/dq/scores/system` | getDqSystemScore | 补表后应 200 |

### D.3 OpenAPI 片段（关键三个，其余按上表同构补齐）

```yaml
paths:
  /api/v1/datanet/facts/{factType}/import:
    post:
      operationId: importBusinessFacts
      parameters:
        - in: path
          name: factType
          required: true
          schema: { type: string, enum: [attribution, stage, resource, cost] }
      requestBody:
        required: true
        content:
          application/json:
            schema: { $ref: '#/components/schemas/FactImportRequest' }
      responses:
        '200': { description: 批次受理结果, content: { application/json: { schema: { $ref: '#/components/schemas/FactImportResult' } } } }
        '400': { description: 整批非法, content: { application/json: { schema: { $ref: '#/components/schemas/ApiResponse' } } } }
        '403': { description: 默认 DENY / 准入不足, content: { application/json: { schema: { $ref: '#/components/schemas/ApiResponse' } } } }
        '409': { description: 关账或 PUBLISHED 冲突, content: { application/json: { schema: { $ref: '#/components/schemas/ApiResponse' } } } }
components:
  schemas:
    FactImportRequest:
      type: object
      required: [batchName, rows]
      properties:
        batchName: { type: string, maxLength: 128 }
        currency:  { type: string, maxLength: 3, example: CNY }
        rows:
          type: array
          maxItems: 100
          items: { type: object, additionalProperties: true }
    FactImportResult:
      type: object
      required: [batchId, accepted, rejected]
      properties:
        batchId: { type: string, maxLength: 36 }
        accepted: { type: integer, format: int32 }
        rejected:
          type: array
          items:
            type: object
            properties:
              rowNo:    { type: integer, format: int32 }
              field:    { type: string }
              ruleId:   { type: string, example: DQ-F02 }
              message:  { type: string }
              suggestion: { type: string }
        traceId: { type: string }
    WriteChannelRequest:
      type: object
      required: [layer, carrierRef, rows, idempotencyKey]
      properties:
        layer:          { type: string, enum: [RAW, CURATED, SEMANTIC, APPLICATION] }
        carrierRef:     { type: string, example: "ecos_dw.ecos_biz_stage_fact" }
        idempotencyKey: { type: string, maxLength: 64 }
        traceId:        { type: string }
        rows:
          type: array
          maxItems: 500
          items: { type: object, additionalProperties: true }
```

### D.4 兼容性处置表

| 现状端点 | 处置 | 期限 |
|:--|:--|:--|
| `/api/v1/ecos/dq/**`（legacy，实测 200） | 读保留 + `deprecated=true`；写停用 | ≥2 迭代 |
| `/api/v1/engine/data/pipeline/tasks` 的 POST/PUT/DELETE | 返回 `ECOS-DATA-011` | 立即 |
| `/api/v1/datasource`、`/datasource`（PMO45 别名） | 委托同一 service，`deprecated=true` | ≥2 迭代 |
| `/api/dq`、`/api/datalake`（datanet 副本） | S3 前保留；切流后由 route-manifest 统一到 `/api/v1/...` | S3 |
| `/api/lineage/**` | 保留，内部委托 `/api/v1/engine/data/lineage` | ≥2 迭代 |
| `POST /api/security/rls/apply`（01 册 W30 相关） | 不在本册范围，见 01 册 D.4 | N/A（归 01 册处置） |

---

## 六、E 章 数据设计

### E.1 schema 归属裁定（**R-1 a／R-1b ② 已批准 2026-09-29**）

| 数据 | 域 | ST07 权威归属 | 现状 | 处置建议 |
|:--|:--|:--|:--|:--|
| `td_datasource` / `td_data_resource` / `td_data_field` / `td_catalog_item` / `td_data_category` / `ecos_data_asset(_field)` / `ecos_data_level_def` / `ecos_data_category_tree` / `ecos_data_lineage_node/edge` / `ecos_query_template/history` | 控制域 | **`ecos_data`**（data-engine 引擎 schema） | 有数据的在 `public`，`ecos_data` 为空镜像（D-7） | 三选一：a) Mapper 加 schema 限定 + `search_path` 注入 → 新写落 `ecos_data`，`public` 只停写（推荐，符合"只停写不迁"）；b) `ALTER TABLE … SET SCHEMA` 物理搬（PG 专属，违反 MC 且 0 行镜像意义不大）；c) 把 `public` 视为主控制桶承认现状（放弃 5+1 对数据域的承诺） |
| 管道 DAG（definition/node/edge/task/run/step/step_run/function/udf） | 控制域 | `ecos_data` | 现在 `public` + `ecos_data` + `ecos_dq` **三处** | 同上 a；`ecos_dq.ecos_pipeline_*` 属错放（DQ schema 里放管道表） |
| DQ 治理（dq_rule/version/check/alert/work_order/schedule/score/report/throttle/knowledge） | 控制域 | 规范 L97 明列 **dq → 主控制**；但实现自建 `ecos_dq` | `ecos_dq.*` 0 行 + 1 表缺失 | 二选一（**R-1 子项**）：① 严守规范，dq 全表迁主控制；② 修订 ST07：dq 归 data-engine 引擎 schema（dq 是数据域治理，非平台控制），把 `ecos_dq` 承认并入 `ecos_data`。**已批准 ②（2026-09-29 §十四.1）**：dq 生命周期属数据域；规范回写已落《数据库访问规范》v1.3 ST07「DQ 归属」条目，08/09 册引用同步。**存量 `ecos_dq` 只停写、不物理迁移**（执行待授权 §14.4） |
| 业务事实五表 + doc/doc_chunk + 预测结果 | 业务域 | **`ecos_dw`**（CURATED/APPLICATION） | `ecos_dw` 仅 doc/doc_chunk | E.2/E.3 新表一律 `ecos_dw`；`dw.schema.application` 经 MC06 配置化 |
| `ecos_demo` 208 表 | — | knownLegacy | 有数据 | 只停写不迁（不改本册动作） |
| `ecos_sysman/datanet/buszhi/dccheng/aiming/infra/security` | — | knownLegacy（ST07 明文"不存在每 service 一 schema"） | 51/39/20… 表 | 只停写不迁；本册不新增此类 schema |

**统一约束**：所有新表/新 SQL **必须**带 schema 限定名且限定名来自配置（MC06：`ecos.db.control-schema` / `ecos.db.engine-schema.data` / `ecos.dw.*`）；裸表名 = FAIL（§四 附则 1）。

### E.2 业务事实五表 DDL（`V167__biz_fact_model.sql`，改写 PRD-02 §1.2 草案的违规项）

> **【2026-09-30 收口对照】** 本节五表已按 **DR05**（布尔列必带 `is_` 前缀）把草案列名 `active_flag`→**`is_active`**、`pool_flag`→**`is_pool`**（含 E.5 清单与 V171 同批；实测五表在库内不存在、全仓代码零引用 ⇒ 改名零回归面）。文件名与号段以单源目录为准：本批因与既有号段冲突而取 `.1` 子版本（`V164.1` / `V165.1` / `V167.1` / `V168.1` / `V169.1` / `V170.1` / `V171.1`），定版规则见报告 §14.4 与落地登记清单。

> 与草案的差异：① 主键去 `gen_random_uuid()`（MC01，应用侧 UUID）；② `JSONB`→`TEXT`（MC02，且这些 json 列不参与 WHERE/索引）；③ `COALESCE(project_id,'POOL')` 表达式唯一索引 → 独立 `is_pool` 列 + 组合唯一索引（MC04 + "表达式索引不可移植"）；④ 补 DR06 审计 5 字段 + DR07 `version_no` + DR08 `domain`；⑤ 唯一性走 `is_active` 方案（F02-06-3）。

```sql
CREATE SCHEMA IF NOT EXISTS ecos_dw;

-- 表 1：项目经营归属
CREATE TABLE IF NOT EXISTS ecos_dw.ecos_biz_project_attribution (
    id                 VARCHAR(36) PRIMARY KEY,           -- MC01：应用侧 UUID
    project_id         VARCHAR(36)  NOT NULL,
    contract_id        VARCHAR(36)  NOT NULL,
    department_id      VARCHAR(36)  NOT NULL,
    attribution_ratio  NUMERIC(5,4) NOT NULL,             -- 金额/比率唯一形态 NUMERIC(p,s)
    attribution_type   VARCHAR(20)  NOT NULL DEFAULT 'PRIMARY',
    effective_from     VARCHAR(7)   NOT NULL,
    effective_to       VARCHAR(7),
    source_evidence    VARCHAR(255),
    dq_status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    is_active        SMALLINT     NOT NULL DEFAULT 1,   -- 1=存活，替代后置 NULL
    batch_id           VARCHAR(36),
    source_ref         VARCHAR(255),
    evidence_ref       VARCHAR(255),
    domain             VARCHAR(50)  NOT NULL DEFAULT 'forecast',
    version_no         VARCHAR(20)  NOT NULL,
    is_deleted         SMALLINT     NOT NULL DEFAULT 0,
    create_time        TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_time        TIMESTAMP    NOT NULL DEFAULT NOW(),
    create_by          VARCHAR(100),
    update_by          VARCHAR(100),
    CONSTRAINT ck_bpa_ratio CHECK (attribution_ratio >= 0 AND attribution_ratio <= 1)
);
CREATE UNIQUE INDEX IF NOT EXISTS uniq_bpa_active
    ON ecos_dw.ecos_biz_project_attribution(project_id, contract_id, department_id, effective_from, is_active);
CREATE INDEX IF NOT EXISTS idx_bpa_project ON ecos_dw.ecos_biz_project_attribution(project_id);
CREATE INDEX IF NOT EXISTS idx_bpa_dept    ON ecos_dw.ecos_biz_project_attribution(department_id);
```

其余四表同构（列清单如下，均含同一套审计/`is_active`/`domain`/`version_no`）：

| 表 | 业务列（含类型要点） | 唯一活跃键 |
|:--|:--|:--|
| `ecos_dw.ecos_biz_stage_fact` | project_id, department_id, period VARCHAR(7), stage VARCHAR(20), fact_type VARCHAR(10), contract_base NUMERIC(18,2), attribution_ratio NUMERIC(5,4), realization_rate NUMERIC(5,4), amount NUMERIC(18,2), currency VARCHAR(3), source_type VARCHAR(20), source_ref, evidence_ref, dq_status | (project_id, department_id, period, stage, fact_type, is_active) |
| `ecos_dw.ecos_biz_resource_fact` | project_id, department_id, period, staff_ref_hash VARCHAR(64)（**工号哈希，替代草案的 staff_ref 明文**）, staff_hash_algo VARCHAR(20), fte NUMERIC(5,2), work_hours NUMERIC(8,1), hourly_rate NUMERIC(18,2), cost_category VARCHAR(20), fact_type, source_ref, dq_status | (project_id, department_id, period, staff_ref_hash, fact_type, is_active) |
| `ecos_dw.ecos_biz_cost_fact` | project_id（可空）, is_pool SMALLINT NOT NULL DEFAULT 0（project_id 为空时=1，**取代 COALESCE 索引**）, department_id, period, cost_category, direct_or_allocated VARCHAR(10), allocation_rule_ref VARCHAR(36), amount NUMERIC(18,2), currency, fact_type, evidence_ref, dq_status | (project_id, is_pool, department_id, period, cost_category, direct_or_allocated, fact_type, is_active) |
| `ecos_dw.ecos_forecast_input_snapshot` | snapshot_id 即 id；forecast_run_id VARCHAR(36) NOT NULL, as_of_time TIMESTAMP NOT NULL, scope_hash VARCHAR(64) NOT NULL, fact_refs_json TEXT, metric_versions_json TEXT, profile_versions_json TEXT, assumption_versions_json TEXT, caliber_id VARCHAR(36), caliber_version VARCHAR(20), checksum VARCHAR(64) | uniq (forecast_run_id)；`*_json` 仅描述用，禁 WHERE/索引（MC02） |

**ST03-A 登记**（必做，否则视同违规）：`amount`/`hourly_rate`/`contract_base` 四张表的金额列在 `docs/40-实现/列级加密豁免登记表-2026-09-28.md` 逐列登记（表/列/理由=聚合计算需要/防护=RLS+CLS+mask/批准人/日期），登记条目 ID 记入本册追溯表。**`staff_ref_hash` 不是豁免而是脱敏存储**（不可逆）。

### E.3 DQ 治理表收敛（`V165` 补表 + `V166` 迁移）

> **【2026-09-30 收口对照】** 依已批准 **R-1b ②**（DQ 归数据引擎 schema），补表落点为 `ecos_data.`，并按 **DR02** 定名 **`ecos_data.ecos_dq_score_asset`**（脚本 `V165.1__dq_score_asset_repair.sql`；旧 `ecos_dq.dq_score_asset` = knownLegacy 只停写不迁）。消费侧 Mapper 改限定名属业务代码改造（§14.4 未授权项③），本批不动代码。差异登记：V112 声明列 `rolled_up_scores`/`last_evaluated_at` vs 代码 `SELECT AVG(rolled_score) FROM ecos_dq.dq_score_asset` vs 本节字面块 `dimensions_json`/`snapshot_at` 三者互不一致，已作为 DDL 单源漂移实证记入报告。

```sql
-- V165.1__dq_score_asset_repair.sql：实查缺失表补齐（V112 声明未落库）
CREATE TABLE IF NOT EXISTS {control}.dq_score_asset (
    id            VARCHAR(36) PRIMARY KEY,
    asset_type    VARCHAR(20) NOT NULL,
    asset_id      VARCHAR(64) NOT NULL,
    overall_score NUMERIC(5,2) NOT NULL,
    grade         VARCHAR(4)   NOT NULL,
    dimensions_json TEXT,                      -- MC02：TEXT，不参与检索
    snapshot_at   TIMESTAMP    NOT NULL,
    domain        VARCHAR(50)  NOT NULL DEFAULT 'dq',
    version_no    VARCHAR(20)  NOT NULL,
    is_deleted    SMALLINT     NOT NULL DEFAULT 0,
    create_time   TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_time   TIMESTAMP    NOT NULL DEFAULT NOW(),
    create_by     VARCHAR(100),
    update_by     VARCHAR(100)
);
CREATE INDEX IF NOT EXISTS idx_dsa_asset ON {control}.dq_score_asset(asset_type, asset_id);
```
> `{control}` 在脚本头部以 `\set` 从 `ecos.db.control-schema` 注入（MC06），并在 `ecos-sql/{mysql,oracle,postgresql}/` 提供方言镜像；`AVG(overall_score)` 侧同步把 `SELECT … FROM ecos_dq.dq_score_asset` 改为限定名 + 配置注入（消 D-6c/MC06）。

**legacy 规则迁移**（`V166__dq_rule_legacy_migration.sql`，幂等 `WHERE NOT EXISTS`）：`public.ecos_dq_rule(17 行)` → 治理表，状态 `DRAFT`；`params`（TEXT/JSON 混用）解析失败 → 不迁移、写入迁移报告表并告警（禁静默）；同时新增 `dq_rule.legacy_rule_id VARCHAR(36)` 追溯列（只加列）。
**DR06/DR07 漂移**：治理表现有 `created_at/created_by/version` **不 ALTER**（IR03），改为**新增**规范列 `create_time/update_time/create_by/update_by/version_no/domain`（可空），Mapper 新写一律用规范列，并在 §6.6 登记为 knownLegacy 列名，终态由后续版本以视图收敛。

### E.4 载体登记扩展（`V169__layer_carrier_registry.sql`）

只加列：`{data}.td_data_resource` 增 `layer_bucket VARCHAR(30)`、`carrier_ref VARCHAR(255)`、`storage_kind VARCHAR(20)`、`declared_flag SMALLINT NOT NULL DEFAULT 1`；新唯一索引 `(layer, carrier_ref, storage_kind, is_deleted)`；对 SEMANTIC/APPLICATION 各插入 ≥1 行登记（否则 `/layers` 报 `declared=false`）。

### E.5 版本脚本清单与纪律

| 脚本 | 内容 | 纪律 |
|:--|:--|:--|
| `V164.1__datasource_credential_split.sql` | `td_datasource` 加 `credential_encrypted TEXT` + 明文迁移 UPDATE（幂等） | 只加列；迁移脚本自带回滚说明 |
| `V165.1__dq_score_asset_repair.sql` | 补缺失表（E.3） | `IF NOT EXISTS` |
| `V166__dq_rule_legacy_migration.sql` | 17 条规则迁治理模型 + `legacy_rule_id` 列 | 幂等 + 迁移报告 |
| `V167__biz_fact_model.sql` | 事实五表（E.2） | MC01/MC02/DR06~08 |
| `V168__migrate_yaml_pipeline_task.sql` | 4 行 YAML task → definition | 失败即整批拒 |
| `V169__layer_carrier_registry.sql` | 载体列扩展 + SEMANTIC/APPLICATION 登记 | 只加 |
| `V170__pipeline_definition_single_source.sql` | `ecos_pipeline_definition/execution` **补写进单源目录**（现仅存 `ecos-sql/`，D-17） | 与 `ecos-sql` 内容一致性由 lint 校验 |
| `V171__dq_active_flag_and_indexes.sql` | DQ/事实相关 `is_active`（若 E.2 未含）与索引 | 只加 |
| `database/seed.sql` | 每新字典/规则模板最小 3 行，`ON CONFLICT DO NOTHING`，与 V* 同 commit | 规范 §四 附则 |

**lint 纪律**：`_win_tasks/db-migration-lint.ps1` 增 3 项检查——①裸表名（无 schema 限定）②`gen_random_uuid()`/`JSONB`/裸 `NUMERIC`/`BIGSERIAL` ③`ecos-sql` 与单源目录同名表定义存在性配对。FAIL 阻断 pr-gate。

### E.6 多库与档位形态

| 项 | standard | enterprise | ultimate |
|:--|:--|:--|:--|
| CURATED/APPLICATION | PG `ecos_dw` | PG `ecos_dw` | 大表可切 Doris∨CH（`dw.olap.engine`，禁双写 MC04） |
| SEMANTIC 载体 | PG 关系表（`ecos_ontology`） | + Neo4j（经 runtime-access 单池） | 同 enterprise |
| RAW | MinIO csv（parquet 由 `dw.lake.storage_format`） | 同 | 同 |
| 方言分支 | 全部 SQL 走 MyBatis `databaseId`；`::text`/`ILIKE`/`ON CONFLICT` 必须分支（消 D-6/D-23 的 MC03 违规） | | |

---

## 七、F 章 符合性与改造项

### 7.1 改造清单（W41~W66 → ARCH_SPEC C30~C50）

| # | 问题（实测来源） | 违反条款 | 落点 | 阶段 | 验收标识 | 回填 |
|:--|:--|:--|:--|:--|:--|:--|
| W41 | datanet :18082 未运行，37 controller 全由 gateway 宿主（D-1） | ADR-15 | C.1 + S2/S3 门禁 | M1 | `DatanetEndpointParityTest` | C30 |
| W42 | datanet `excludeFilters` 掉 `DataEngineConfigController`，切流即端点缺失（D-2） | ADR-15 S3 / IR01 | C.1 前置② | M1 | `DatanetNoExclusionTest` | C31 |
| W43 | 5 套前缀同日同进程并活（D-3） | 铁律 §2.4 / route 单源 | C.2.1 | M1 | `RouteManifestParityTest` | C32 |
| W44 | 管道双模型双执行器都有数据（D-4） | 铁律 §4.8.2 / 单一事实源 | F02-03 | **M0** | `PipelineSingleModelArchTest` | C33 |
| W45 | DQ 双模型，治理侧 8 表 0 行（D-5） | REQ-DATA-02 | F02-08-4 + E.3 | **M0** | `DqLegacyMigrationParityTest` | C34 |
| W46 | 5xx 被掩蔽为 404（D-6，GlobalExceptionHandler:201） | REQ-API-03 / 00 册错误码表 | F02-08-1 | **M0** | `NoFourOhFourMaskingTest` | C35（P0） |
| W47 | `DqWorkOrderServiceImpl:242` 缺 FROM 子句（D-6b） | IR05/MC03 + 正确性 | F02-08-2 | **M0** | `WorkOrderListSqlContractTest` | C36 |
| W48 | `dq_score_asset` DDL 声明未落库（D-6c） | 铁律 §3.1 / IR02 手动 psql 流程 | E.5 V165 | **M0** | `SchemaDriftLintTest`（库表 vs 单源差集为空） | C37 |
| W49 | 同名表 public + 引擎 schema 双镜像，写全落 public（D-7） | ST07 / §四 附则1 / MC06 | E.1（**R-1**） | **M0** | `QualifiedTableNameArchTest` | C38 |
| W50 | `ecos_dq` 等 11 个 schema 越出 ST07「5+1」（D-8） | ST07 | E.1 裁决 + 规范回写 | M1 | `SchemaInventoryGateTest` | C39 |
| W51 | SEMANTIC/APPLICATION 载体 0（D-9） | 湖规范 v2.0 五层 | F02-05 + E.4 | **M0** | `LayerCarrierPresenceTest` | C40 |
| W52 | 数据源凭证明文入库，控制域不可豁免（D-11） | ST03 / ST03-A④ | F02-01 + V164 | **M0（安全）** | `DatasourceCredentialCipherTest`、`DatasourceLegacyPasswordMigrationTest` | C41（P0） |
| W53 | `DriverManager` 5 处未清（D-12） | IR06 / REQ-DATA-05 | F02-13 | M1 | `NoDriverManagerArchTest` | C42 |
| W54 | Service 层 51 文件直持 JdbcTemplate（D-13） | 规范 §五 三层 / IR01 | C.4 全量改 Mapper | M2 | `NoJdbcTemplateInServiceArchTest` | C43 |
| W55 | 跨引擎取数默认 :18082 且静默返回空列表 + 路径不存在（D-14） | ADR-15 / 分册 00 C.2.3 / fail-loud | F02-15 | **M0** | `CrossEngineFailLoudTest`、`DataNetResourcePathReachableTest` | C44（P0） |
| W56 | gateway 直查 `ecos_dq_issue`/`ecos_dq_rule`（D-15） | IR01 | 改经 datanet 契约 | M1 | `GatewayNoDomainTableArchTest` | C45 |
| W57 | `SOURCE_CDC` 目录有、执行器拒，用户可拖出必败节点（D-16） | 铁律 §4.8.2 | F02-04 | M1 | `NodeCatalogParityTest`、`UnavailableNodeRejectedAtSaveTest` | C46 |
| W58 | `ecos_pipeline_definition/execution` 建表只在 `ecos-sql/`，单源缺（D-17） | 铁律 §3.1 | E.5 V170 | M1 | `db-migration-lint` + 空库重建演练 | C47 |
| W59 | DDL 违规成片：`gen_random_uuid()`×4 / `JSONB`×49 文件 / `BIGSERIAL` / V6 同名双内容（D-18） | MC01/MC02/§3.1 | E.2/E.5 + lint 3 项 | M1 | `DdlComplianceLintTest` | C48（与 01 册 W38 同族） |
| W60 | 审计列漂移：dq_rule `created_at/version`、td_datasource 缺 3 列（D-19） | DR06/DR07/DR08 | E.3 末 + 新表模板 | M2 | `AuditColumnPresenceTest` | C49 |
| W61 | `buildContext` 空转（D-20） | REQ-DATA-04-1 | F02-09 | M2 | `ScoringContextHydrationTest` | — |
| W62 | DQ 告警升级无驱动定时器；且禁引擎自建 `@Scheduled`（D-21） | REQ-DATA-04-2 / 铁律 §2.5-4 | F02-10 | M2 | `DqAlertEscalationTimerTest`、`EscalationSchedulerRegisteredInRuntimeTaskTest` | — |
| W63 | REQ-DATA-06 已实现，仅存残余（D-22） | — | F02-12 加固 | M1 | `RepoRootSymlinkEscapeTest`、`AllGitCallersUseResolverArchTest` | — |
| W64 | 血缘扫 YAML + `LIMIT` 拼接 + `ILIKE`（D-23） | IR05 / MC03 | F02-11 | M1 | `LineageDerivationTest`、`NoYamlLineageScanArchTest` | — |
| W65 | kb 仍 INSERT `kb_doc_chunk`，A3 未退出（D-24） | REQ-DATA-08 | F02-14-2 | M2 | `KbChunkWriteGoneArchTest`、`DualRunDiffZeroTest` | — |
| W66 | 前端 `api.ts` 1656 行 / `ConnectionsTab` 1227 行（B.7） | 前端规范 组件 ≤800 行 | B.7 拆分 | M1 | `tsc --noEmit` + `file-size-lint` + `pipeline-editor.spec.ts` 回归 | C50 |

### 7.2 PRD 侧需回写清单（本册发现）

1. **PRD-02 §1.2 DDL 草案作废替换**：`gen_random_uuid()`、`JSONB`、`COALESCE` 表达式唯一索引、缺审计列 —— 以本册 E.2 为准（草案系照抄规范 v1.1 之前的模板，规范 v1.2 已自纠，PRD 未跟）。
2. **PRD-02 §1.3-4 "金额列不加密"表述**：改为"须先在 ST03-A 登记表逐列登记，未登记=违规"（规范 L182 已明文作废单方例外）。
3. **PRD-02 §六 REQ-DATA-06**：从"待实现"改"已实现，残余=存在性校验/symlink/覆盖三处调用方"（D-22）。
4. **PRD-02 §二 缺"DQ 双模型收敛"需求**：新增 REQ-DATA-09（治理模型收敛 + 404 掩蔽禁令 + legacy 只读期），否则 DATA-02 验收不可判定。
5. **PRD-02 §1.4 端点前缀**：`/api/v1/datanet/facts/**` 需登记进 route-manifest 并声明与既有 4 套前缀的关系（C.2.1）。
6. **PRD-02 §1.5-4 与 §三 验收**"端点 curl 验收表"需点名具体 spec/测试标识（00 册 §九 测试行已禁"人工确认"）。
7. **PRD-07 §3.2 authHeaders 替换清单作废**（D-24：已单源），REQ-SEC-03 验收改为"保持单源 + 三用例单测"。
8. **PRD-00/PRD-02 关于 datanet 承流的表述**：与 ADR-15 双口径一致化（01 册同样提过）。

### 7.3 裁决结果（R 项 —— 已于 2026-09-29 §十四.1 全量批准）

| # | 事项 | 选项 | 本册推荐 → **批准结果** |
|:--|:--|:--|:--|
| **R-1** | 数据域表 schema 归属终态：现状全在 `public`，`ecos_data`/`ecos_dq` 为 0 行镜像 | a) 新写落引擎 schema，`public` 只停写；b) 物理 `SET SCHEMA`；c) 承认 `public` 为主控制桶现状 | **a 已批准**（不可逆动作最小，符合"只停写不迁"；`public` 存量迁移属待授权执行） |
| **R-1b** | DQ 表归属：ST07 说"dq→主控制"，实现自建 `ecos_dq` | ① 迁主控制（严守规范）；② 修订 ST07，dq 归数据引擎 schema | **② 已批准**（dq 生命周期属数据域；规范已改：《数据库访问规范》v1.3） |
| **R-2** | 管道单模型选型：JSON definition vs YAML task | a) definition 为唯一（YAML 只读）；b) task 为唯一 | **a 已批准**（数据量 51:4 + 目录同源 + 前端绑定；YAML 模型转只读归档后迁移删除，删除动作待授权） |
| **R-3** | 治理栈三处 404 的暴露方式：修复期间是否允许临时保留"回落 legacy"的前端兜底 | ① 立即禁兜底（暴露失败）；② 保留到 V166 迁移完成 | **②+禁静默 已批准**（必须显式提示用户"当前为旧模型数据"；兜底保留至 `V166`，C35 的 404 掩蔽同步修） |

---

## 八、追溯矩阵（REQ → 本册落点 → 下游）

| REQ | 本册落点 | 被依赖 | 批次 |
|:--|:--|:--|:--|
| DATA-01 | F02-06 / E.2 / C.5 | FC-02 快照、KB-02 画像取数、09 册 M0 | 场景 A（P0） |
| DATA-02 | F02-07 / F02-08 / C.5 | FE-03 待办化、DATA-03 | 场景 A（P0） |
| DATA-03 | F02-06-2 / B.5 / D.2 | — | 场景 A |
| DATA-04 | F02-09 / F02-10 | 00 册 runtime-monitor | G5 |
| DATA-05 | F02-13 / W53 | ARCH-06 | **G1** |
| DATA-06 | F02-12（转加固）/ W63 | — | **G1** |
| DATA-07 | F02-14-1 / C.3.5 | — | G5 |
| DATA-08 | F02-14-2 / W65 | 04 册 kb 摄取链 | G5 |
| DATA-09（新增） | F02-08 | 09 册 DQ 门禁验收 | 场景 A（P0） |
| DB-05/06 | E.1 / E.5 / MC06 lint | 全部分册 DDL | M0 |
| NF-07 | C.4 traceId + PipelineEvent | 00 册事件总线 | M1 |

**本册与 01 册的接缝**：`GET /api/v1/datanet/facts/**`、`/datalake/objects`、行采样、导出下载四处**必须**调用 01 册 `SecurityDecisionService#decide`，禁止在本域拼 RLS 字符串（承接 W31 的旁路禁令）；`staff_ref_hash` 脱敏属存储侧，`hourly_rate`/`amount` 列裁剪与掩码属 01 册管道侧。

<!-- 详细设计-02-数据域 / 2026-09-28 / v1.1（2026-09-29 定版） / W41~W66 → C30~C50 / R-1 a、R-1b ②、R-2 a、R-3 ②+禁静默 已批准（报告 §十四.1） / Gate-1 已签字、Gate-2 已通过 / 【校订 2026-10-03】 本轮补齐 4 项离线可验验收测试（W46 NoFourOhFourMaskingTest P0 / F02-06-4 PublishedRowImmutableTest P0 / F02-06-5 ClosedPeriodWriteDeniedTest P0 / F02-01 §2 CredentialPresentReadPathTest P0）+ DataSourceEntity 新增派生 getter isCredentialPresent()（API 只增不改）；仍**未实跑库**（存量 SQL / 迁移脚本未做库侧断言） / 全 mvn -o test 4 模块 3 类共 8 例全通过 / 【校订 2026-10-03 二】 再补 1 项离线可验 P0 验收测试 F02-06 `FactBadDataTripleRejectTest`（DQ-F01/F02/F09 各 1 坏行 → 3 条 rejected 各带 rowNo/field/ruleId/suggestion + 一律不入库 batchUpdate 从不调用 + 合规行反向不误伤，data-engine-impl 单测 3 例全绿，Mockito 打桩 JdbcTemplate 不触库）；【校订 2026-10-03 三】再补 F02-06-6 `AmountExemptionRegisteredTest`（ST03-A 金额列逐列登记护栏：断言 `列级加密豁免登记表` 逐列含 `ecos_biz_stage_fact.amount/contract_base`、`ecos_biz_cost_fact.amount`、`ecos_biz_resource_fact.hourly_rate`，且 `staff_ref_hash` 不以豁免身份出现；仓根自模块 src/main/java 上溯 + 令牌边界列名匹配避免 `plan_amount/revenue_amount` 误命中，1 例全绿不触库）；本轮未再实跑库 / 【校订 2026-10-03 四】再补 4 项离线可验验收测试（9 例全绿不触库）：F02-02 `MetadataHistoryFromGitTest`（listHistoryVersions 只认 `yyyyMMdd_HHmmss.json`、降序 + 当前版 metadata.json、`../escape` 段拒绝回 not_available，3 例）＋ F02-02 `MetadataCollectIdempotencyTest`（同清单二次归档 diff=无变化/新增 0，加表→新增 1，2 例）＋ F02-01 `DatasourceSingleSurfaceArchTest`（两 Controller 均委托 DataSourceRegistryService 同一承流面、共享 CRUD 同 surface、无内联 DML/JdbcTemplate/DriverManager，1 例）＋ F02-13 `ConnectionTestViaRuntimeTest`（关系型走 runtime-access Connector.testConnection 委托与透传、失败透传 false、缺数据源不触 factory，3 例）；本轮未再实跑库 / 【校订 2026-10-03 五】F02-12 收口 B 类：`MetadataCollectGitArchive` 4 处 `Paths.get(repoRoot, ...)` 越界拼接全部改走 `GitRepoRootResolver.resolveUnderRoot(...)`（本类自拼 → resolver 单源带校验），新建 arch 护栏 `AllGitCallersUseResolverArchTest`（源码文本扫描：main 除 `GitRepoRootResolver.java` 外任何文件不得再出现 `Paths.get(repoRoot|root, ...)` 字面；改造前 4 处即红，改造后全 grep 0 命中绿）；同时把 `MetadataHistoryFromGitTest` / `MetadataCollectIdempotencyTest` 的 resolver 打桩从 mock 换**真实 resolver 实例 + mock `SysConfigService`**（让 `resolveUnderRoot` 走真实校验+路径拼接，让 @TempDir 真实落盘参与 diff 断言，测试语义更严实）；共 3 类 6 例全绿不触库 / 【校订 2026-10-03 六】W66/B.7 前端文件规模收口：`ecos_frontend/src/pages/data-workbench/api.ts` 由 1661 行单一文件拆为按域 thin barrel —— api.ts 退化为 82 行纯 re-export，新增 httpClient.ts(49) + apiCommon.ts(15) + apiDatasource.ts(764) + apiPipeline.ts(439) + apiMetadata.ts(421)，**全部 ≤800**；函数体逐字节迁移、仅重接线导入，既有 `import { … } from './api'` 消费方与 api.test.ts 零改动；验收 tsc --noEmit 全绿 + vitest api.test.ts 5/5（不碰库） / 【校订 2026-10-03 七】F02-03/W44(P0) 收口 `PipelineSingleModelArchTest`（离线 arch 护栏，3 例全绿不触库）：① `PipelineTaskController` 5 个写端点写调用清零 + 保留 5×`return readonlyReject();`（撤线不删端点，API 只增不改）② 全模块 controller/** 零引用 `PipelineExecutionEngine`（防 YAML 执行引擎重新挂路由）③ `PipelineExecutionEngine` 零 `INSERT INTO ecos_pipeline_task` / 零 `UPDATE … SET yaml_content`（YAML 内容退写入）+ 白线断言存在 `SET status`（legacy 运行兼容保留，防空文件静默绿）。范围口径：`PipelineTaskServiceImpl`（service 非 controller）的 engine 字段为过渡态留后续 W，`PipelineGitService` 的 yaml_content 写属 Git 版本管理链（R-2 a 未禁），本护栏不越权扫描 / 【校订 2026-10-03 八】F02-15/W55(P0) 补 `CrossEngineFailLoudTest` 尝试：`DataNetResourceClient` 依赖不可达 / 503 / 404 / 响应非法 3 个方法（listResourceFields/listResourcesByLayer/listMetadataFields）应一律抛 `DataAccessException`（ECOS-DATA-041），不返回空列表。**代码已全绿**（源码逐方法实证：catch 分支一律 throw），但**受并行窗口阻塞**：`DataNetResourceClient.java` 与 `MetricUnitAnalyzer/MetricUnitDerivationGuard/PublishGateService` 等 ontology 侧 (caliber/metric 特性) WIP 同时变更且 main-scope 编译未绿；按并行窗口规则（同文件不同窗口争用跳过 + 不改他人 WIP），本护栏落地挂起待 ontology 侧编译绿后随后续 PMO 批次落地（意图 + 断言已在会话内登记，防遗忘） / 【校订 2026-10-03 九】F02-W60/C49(M2) 补 `AuditColumnPresenceTest`（离线 DDL 单源审计，2 例全绿不触库）：按行定位 7 张本册新表 CREATE TABLE 块 —— V165.1 ecos_data.ecos_dq_score_asset / V166 ecos_data.ecos_dq_legacy_migration_report / V167 五张事实表（project_attribution/stage_fact/resource_fact/cost_fact/forecast_input_snapshot），逐表断言含规范七列（DR06 审计五列 + DR07 version_no + DR08 domain）；白名单反例 `td_datasource`（V19 knownLegacy 承数表）不在本护栏扫描范围，防误伤存量"只不改" / 【校订 2026-10-03 十】F02-W43/C32(M1) 补 `RouteManifestParityTest`（gateway 模块离线 arch 护栏，7 例全绿 / 非 Spring / 不触库）：① 台账结构完整性（entries≥5 / prefix 唯一 / 必须 /api 起头 / owner 非空 / 端口双态 / mode∈{monolith,service} / S3 前一律 monolith）② 参数化 4 条断言：数据域核心前缀 `/api/v1/datanet` `/api/v1/engine/data` `/api/v1/data` `/api/v1/dq` 必须登记且 owner=datanet + artifact 指向 data/datlanet 系模块（防空文件静默绿的白线）③ datanet owner 前缀总数≥4 ④ 跨 owner 端口撞用断言（防未来加前缀时抄错 owner 端口）。与既有 `GatewayRouteManifestTest`（详细设计-00 C.4 M0 结构+docs 同步）互不重叠：该测试守「单一 schema + docs 版本同步」，本测试守「数据域 C.2.1 收敛目标 + 4 核心前缀 owner 归属 + 端口冲突」，同一 route-manifest.json 的两个正交视角。规避 D-3 "5 套前缀同日同进程并活"退化的最小守卫：未来任何一次把核心前缀从单源删除或裸抽 owner 即红 / 【校订 2026-10-03 十一·终核对账】按"实现全部设计内容、逐项核对不漏项"目标做完最后一轮全项对账（不依赖前内存记录，逐条实测当前文件的 verify-before 复扫）：① **data-engine-impl 全量离线 269/269 绿（1 skip = Windows symlink 环境 assume 诚实跳过）+ gateway `RouteManifestParityTest` 7/7 绿** ② **D.2 REST 23 端点在 controller 实测齐备**（改用真实 `@*Mapping` 路径核验，非 OpenAPI operationId 名：`/{factType}/template`、`/batches/{batchId}`、`/api/v1/dq/scores`、`/definitions/{id}/execute`、`/topology/rebuild`、`/node-types`、`/api/v1/datanet/ingest`、`/datasource`(+/test) 等全部命中；此前按 operationId 字面 grep 的"0 命中"系命名面工件，非端点缺失）③ **F02-14-1 parquet 写器 = staged 降级态**（显式 `format=parquet` → 抛清晰业务异常"写器未就绪"，`DuckDBQueryService.registerParquetView` 现 no-op stub；实写需 runtime-access DuckDB+parquet 依赖落地，属未实跑基建）④ **F02-14-2 A3 退出 / W65** 现态未变（`KbDocChunkMapper` 仍 `INSERT INTO ecos_knowledge.kb_doc_chunk`，D-24），属分册 04 kb 摄入链 C.7 契约跨窗口，非数据域单窗口可控 ⑤ **B.1-B.6 六个 `.spec.ts`（connections/pipeline-editor/dq-rule-center/dq-alert-escalation/layer-carrier-browser/fact-import/lineage）** 作为 doc 验收名：现有 e2e 覆盖相应流但未按此六名独立拆文件，落地需 7 JAR+前端活环境联动（活验证）。**结论**：单窗口可控、离线可验的 C/H 验收面已穷尽（本轮新增 W43 `RouteManifestParityTest` 收口）。剩余全部触授权闸：实跑库（W45/48/50、ActiveFlag、SqlConsoleRegression、可达性、路由三态、V167/168 psql 实跑）、活 UI（6-9 B 章 spec）、跨窗口（W55 ontology 编译绿后）、跨分册（F02-14/A3-exit/W65）、跨模块基石（C.3.6 严格版 / E.5 lint ①③ 窄口径需专用授权），以及 `git push`（release/v2.1-alpha 本地领先 66，待显式指令）——均未在本轮越权自证 / 未实跑库 / 【校订 2026-10-03 十二】W58/D-17(E.5 检查③ 窄口径) 补 `PipelineDdlSingleSourcePairingTest`（data-engine-impl 离线护栏，3 例全绿不触库，本轮新增 +3 例）：把校订十一中「E.5 检查③ 需专用授权/扩成 noisy 99 文件全树扫描」的顾虑**收窄为只锁 D-17 两张管道表** `ecos_pipeline_definition`/`ecos_pipeline_execution` 的存在性×列名配对——两侧文件必需（单源 `V170__pipeline_definition_single_source.sql` + 多库镜像 `ecos-sql/postgresql/03_ecos_data.sql`），逐表断言 CREATE TABLE 块在<b>两侧都存在</b>（防任一侧被静默删）且<b>两份列名集合相等</b>（防单源补写时漏/多列与多库镜像漂移）。只比列名不比类型文案，以容纳 V170 头注合规化①声明的 `NOW()`~`CURRENT_TIMESTAMP` 已知等价写法（不误判）。纯文件读取+正则解析，**不触库/不 Spring/不联网**，不改动共享治理脚本 `db-migration-lint.ps1`（那份全量 lint 仍属人工审查面，本护栏只锁定 D-17 两表这一最小 E.5③ 语义）。与校订十一 « 终核对账 » 中登记为「窄口径需专用授权」项不同：本项**未越权扩基线**，仅为本模块单窗口可控范围内、离线可自我验证的守卫性收口 / 【校订 2026-10-03 十三】W59/D-18(E.5 单源纪律) 补 `MigrationBasenameSingleRootArchTest`（data-engine-impl 离线单测 M1，1 例全绿不触库不联网，本轮新增 +1 例）：把 D-18「单源目录唯一（铁律 §3.1 禁分域另立目录）」中**同名 V*.sql 跨根重复**的碎片面机械化——扫 6 个仓内 SQL 根（database/gateway/service 侧 engine/services/runtime/workspace，同 `db-migration-lint.ps1` $sqlRoots 口径），按 basename 归并 root，跨 ≥2 root 即碎片；断言碎片集合 ⊆ 2026-09-30 实查 14 项 knownLegacy（V1/V1.1/V2–V13，其中 V6__ecos_data_quality.sql = gateway 与 sysman-boot 同建 ecos_dq_rule 但 md5 不同，铁律 MC05 违规）。三档断言：a) 新增跨根碎片判红（只减不增）b) 已收敛项 INFO 不阻断 c) 白线 V6 当前跨根（防护栏空转静默绿；若 D-18 被单源化完成则白线首次判红作下线信号）。与既有 `LegacyDebtBaselineRatchetArchTest#W59_BASELINE_GEN_RANDOM_UUID_FILES=11` 分工：后者锁 gen_random_uuid(MC01) 违规文件**计数**只减不增（已含 D-18 点名 V155/V156/V46/V48 四文件），本护栏锁**同名文件跨根重复**的 DDL 单源碎片面（D-18 后半段），两档从不同维度同守铁律 §3.1，正交不重叠。全 data-engine-impl 模块离线 mvn -o test EXIT 0 / BUILD SUCCESS，surefire 报告合计 273 run / 0 failures / 0 errors / 1 skipped（skip 系 GitRepoRootHardeningTest 中 Windows symlink 场景 assumeTrue(false) 诚实跳过，与本轮无关）；本轮新增 MigrationBasenameSingleRootArchTest 1 例不触库/不 Spring/不联网，纯文件 walk 反则即红 / 【校订 2026-10-03 十四】W59/D-18/C48(E.5 检查② MC01+DR04 侧) 补 `DdlComplianceLintTest`（data-engine-impl 离线单测 M1，2 例全绿不触库不联网，本轮新增 +2 例）：把 D-18「DDL 违规成片」的三条未锁切片之一（JSONB×49 文件 + BIGSERIAL/SERIAL×20 文件）落成棘轮——扫单源目录 gateway/…/db/migration/ 顶层 *.sql 全部 197 个文件，从<b>列声明位置</b>（行首标识符 + 类型 token + -- 注释剔除）识别两个违规族：DR04 无 `_json` 后缀的 JSONB 列（基线 127 处）+ MC01 BIGSERIAL/SERIAL 主键列（基线 49 处）；只减不增（合规新增 `name_json JSONB` 天然不计入，防止合规改造反被判红）。另一半 D-18 四大违规已由本册其他三个 guard 分别锁定：`LegacyDebtBaselineRatchetArchTest` W59 锁 gen_random_uuid 计数棘轮（baseline 11）；`MigrationBasenameSingleRootArchTest` 锁 V6 等 14 项跨根同名碎片。与既有 W59 gen_random_uuid 棘轮正交（本类锁列类型面、后者锁函数调用面），四片齐锁 = D-18 完整机械化。全部离线，surefire 合计 275 run / 0 fail / 0 err / 1 skip / 【校订 2026-10-03 十五】F02-06-3 活跃唯一性 DDL 声明面补 `ActiveFlagUniquenessDdlTest`（data-engine-impl 离线单测 M2，5 例全绿不触库，本轮新增 +5 例）：doc 行 140 验收名 `ActiveFlagUniquenessTest（同键第二行存活→索引拒；旧行置 NULL 后新行可入）` 的**运行时行为需活库 INSERT（实跑库授权闸）**，但其<b>前置 DDL 契约</b>离线可验——单源 `V167__biz_fact_model.sql` 四张软替代事实表（project_attribution/stage_fact/resource_fact/cost_fact）逐表断言 ① `is_active SMALLINT NOT NULL DEFAULT 1` 列声明（DR05 布尔 SMALLINT，"1=存活"）② 含 is_active 的组合 <b>UNIQUE</b> 索引（该索引正是"唯一活跃"落地原语：NULL 老行不占唯一位、同键第二存活行被拒，MC03 非条件/MC04 非表达式）③ 具名索引 uniq_bpa/bsf/brf/bcf_active 实存白线（防空文件静默绿）。第 5 表 forecast_input_snapshot 例外：仅声明 is_active 列、**不得**含 is_active 的唯一索引（快照按 forecast_run_id 唯一，套用 active 唯一索引会改变语义），反向断言防误改。纯文件读取+正则解析，不触库/不 Spring/不联网。surefire 合计 280 run / 0 fail / 0 err / 1 skip / 【校订 2026-10-03 十六】E.5 lint 纪律 ①「裸表名（无 schema 限定）」落地 `BareTableNameRatchetArchTest`（data-engine-impl 离线护栏 M1，1 例全绿不触库不联网，本轮新增 +1 例）：本册 E.5 表登记 8 张新 V* 迁移脚本（V164.1/V165.1/V166/V167/V168/V169/V170/V171）逐行扫 DML/DDL 动词（CREATE [UNIQUE] INDEX ... ON / CREATE [MATERIALIZED] TABLE|VIEW [IF NOT EXISTS] / ALTER TABLE|INDEX|SEQUENCE / TRUNCATE [TABLE] / DROP [TABLE|INDEX|SEQUENCE|VIEW|SCHEMA|FUNCTION|MATERIALIZED VIEW] / INSERT INTO / UPDATE [ONLY] / DELETE FROM），抓动词后第一个表标识符，若**未含 `.`（即无 schema 限定）**且非 SQL 元名词（SCHEMA/INDEX/SEQUENCE/TABLE/VIEW/FUNCTION 等保留字）即违规命中；棘轮基线 = 0（2026-10-03 实测 8 脚本 DML/DDL 表标识符 100% 已带 schema 限定 ecos_data/ecos_dw/ecos_dq/public），未来本册新增 V* 脚本追加到 W60_SCOPE_SCRIPT_BASENAMES 即纳入扫描。**范围口径**：只锁本册 8 脚本（新落产物，应零已知债务），不外推 190+ 存量脚本 —— 存量裸表名属登记 grep 的另一档债务（不在本窗越权扩基线）。**白线**：V170 应含 `CREATE TABLE IF NOT EXISTS ecos_data.ecos_pipeline_definition`（防正则退化后空转判绿）。与既有 W59/MC01/MC02/DR04 四 guard 从不同维度同守 D-18：本护栏锁"schema 归属限定"（ST07 + 铁律 §3.1 schema 归口），是 E.5 lint ①的窄口径离线可验证实现，不改共享治理脚本 `db-migration-lint.ps1`（那份全量 lint 仍属人工审查面）。surefire 合计 281 run / 0 fail / 0 err / 1 skip（skip 仍系 GitRepoRootHardeningTest symlink 场景诚实跳过） -->
<!-- 【校订 2026-10-03 十七】E.3 末/W60/C49 M2 门面名不副实收口——V170 头注已点名"门禁标识 = W60/C49 AuditColumnPresenceTest"但主类只锁 7 张 V165.1/V166/V167 表；V168 `ecos_pipeline_yaml_migration_report`（形态 A 新表 7 列在 CREATE 块）+ V170 `ecos_pipeline_definition`/`ecos_pipeline_execution`（形态 B 现网补写——CREATE 镜像生产既有列 + 8 条 ALTER TABLE ADD COLUMN IF NOT EXISTS 补 7 规范列，D-19/IR03"只补建不删改"口径）均未被主类覆盖。本轮补 `AuditColumnPresenceExtendedTest`（data-engine-impl 离线单测 M2，2 例全绿不触库，本轮新增 +2 例）：① 3 表 × 7 列 = 21 token 用例——每表在【CREATE TABLE 块 + 该表后续所有 ALTER TABLE <schema>.<table> ADD COLUMN IF NOT EXISTS … 行】并集内必须齐含 DR06 审计五列 + DR07 version_no + DR08 domain ② 白线：V170 应实存 `ALTER TABLE ecos_data.ecos_pipeline_definition ADD COLUMN IF NOT EXISTS domain` 与 `ALTER TABLE ecos_data.ecos_pipeline_execution ADD COLUMN IF NOT EXISTS version_no` 两处字面（防未来 V170 若把 ALTER 打成别的补写形态导致"形态 B"静默退化）。提取器专用 `extractCombinedBlock`（CREATE 截止到 ');'+ 后续该表 ALTER 行并入）避开主类只读 CREATE 块的局限；一条 ALTER TABLE 只针对单表——天然收敛不可能串到别表。与主类同属 W60/C49 门禁标识、正交切片（主类锁"新表形态 A"、本类锁"现网补写形态 B"）；td_datasource 白名单反例不重复（域本职在主类）。实际意义：V170 现存 2 表已 7 列齐（V170 头注 2026-09-30 四补收口声明），本护栏把"门禁标识名叫 AuditColumnPresenceTest"与"该标识实覆盖到 V170"对齐，未再让门禁名不符实。surefire 合计 283 run / 0 fail / 0 err / 1 skip（skip 仍系 Windows symlink 场景诚实跳过） -->
<!-- 【校订 2026-10-03 十八】D.2 REST 清单「API 只增不改」离线 surface-presence 护栏——`DqRestApiOnlyAdditivePresenceTest`（data-engine-impl 离线单测 M1，2 例全绿不触库不联网，本轮新增 +2 例）：把校订十一「D.2 23 端点 controller 实测齐备」的<b>一次性人工核验固化为可复跑的持久护栏</b>（铁律 #9 API 只增不改：既有路径与签名不可删/改）。对 D.2 每条端点声明一组「controller 类基路径字面 + 方法后缀字面（含引号，防 /test 误命中 /testfoo）」，断言其在<b>同一</b> controller 源文件内共存（防跨文件巧合）。锁定面板 = D.2 的 23 条端点（datasource×3 / ingest run / facts 5 / write-channel / layers×2 / lineage×2 / pipeline×3 / dq rules×3 / work-orders×2 / scores system）。**实证发现一处 D.2 命名面真漂移（本轮唯一新增缺陷线，非护栏误判）**：D.2 行 `POST /api/v1/dq/evaluate`（operationId `evaluateDqRule`）——2026-10-03 逐控制器实测：controller 内<b>无</b> `/api/v1/dq/evaluate` 映射；evaluate 实际只 served 在 `QualityController`（base `/api/v1/engine/data/quality`）的 `@PostMapping("/evaluate")` 与 `/rules/{ruleId}/evaluate`（legacy quality 前缀）；`VersionPrefixRewriteFilter` 仅反向 `/api/dq/ → /api/v1/dq/`，<b>不桥接</b> `/api/v1/dq → /api/v1/engine/data/quality`；全仓（backend Java + frontend src）<b>无消费者</b>引用 `/api/v1/dq/evaluate`。结论：D.2 的目标前缀 `/api/v1/dq/evaluate` 现未收敛、仍走旧 quality 前缀。本护栏**显式不 lock-in 该漂移项**（D2_ENDPOINTS 仅锁其余 23 条；evaluate 第 24 条以「白线 status-quo」形式登记——断言 evaluate 仍在 legacy quality 前缀，若 F02 收口批次把它落到 `/api/v1/dq/` 则白线首红作"漂移已修、请同步补/改 D.2 + 撤本白线"信号）；是否补映射或改 D.2 行属 F02-09/D.2 命名面收口裁决，本窗不越权改既定 D.2 表（R9 只追加登记）。纯文件读取+字面匹配，不触库/不 Spring/不联网。surefire 合计 285 run / 0 fail / 0 err / 1 skip（skip 仍系 Windows symlink 场景诚实跳过） -->
<!-- 【校订 2026-10-03 十九】C.8 降级与失败语义矩阵落地——三条「管道依赖不可用即禁成功」错误码在管道执行面真实落位（数据域可离线自验切片，本轮 +3 落码 +4 例护栏，surefire 285→289）：C.8 矩阵定义 7 条 05x 码，2026-10-03 逐源码复核时实证 ECOS-DATA-051/052/053/054/055/056/057 在全 main source **0 命中**（码只在 doc 定义未落码）。本窗锁定其中三条具备清晰单窗口落点且属"禁成功"（fail-loud）语义的码，全部收口在数据域独有的 `PipelineExecutionService`（非并行窗争用文件）：① **ECOS-DATA-051**（runtime-access/JDBC Driver 不可用，禁）——`requireJdbcConnector` 非 JdbcConnector 分支由裸 `BusinessException` 升级为携带 `CODE_RUNTIME_ACCESS`；② **ECOS-DATA-052**（MinIO RAW 近源层不可用，禁、不留半对象）——`executeSinkMinio` 上传失败分支携带 `CODE_MINIO_UNAVAILABLE`；③ **ECOS-DATA-057**（DuckDB/parquet 不可用，可但须明确提示维持 csv）——`executeSinkMinio` 显式非 csv 格式拒绝分支由裸文案升级为携带 `CODE_DUCKDB_UNAVAILABLE`（既有 `写入器未就绪` 文案保留，既有 `PipelineLakeNodeTest` 断言不破）。三码以类常量 `CODE_RUNTIME_ACCESS/CODE_MINIO_UNAVAILABLE/CODE_DUCKDB_UNAVAILABLE` 定义，遵循既有"错误码落消息字面"惯例（同 ECOS-DATA-011/021/031/041），便于异常消息 grep 与文案校验。**护栏** `C8DegradationFailLoudTest`（4 例全绿不触库不 Spring 不联网，打桩 ConnectorFactory/MinIO/DataSourceService 复用 `PipelineLakeNodeTest` 单节点骨架）：051（SOURCE_JDBC 但工厂返回非 JdbcConnector → FAILED + 消息含 051）/ 052（SINK_MINIO 上传 status!=success → FAILED + 消息含 052 + verify 未调 `markNearSourceStructured` 即不留半登记）/ 057（SINK_MINIO format=parquet → FAILED + 消息含 057）/ 白线（三码常量在 service 内真实定义=doc 语义值，防空文件静默绿）。**范围口径**：另四条"可降级"码（053 OLAP 读降级—ultimate 档跨依赖 / 054 Kafka 审计重试—落 `ecos_runtime_audit_retry` 00 册 E / 055 monitor 不可用仅落库补投—runtime-monitor 侧 / 056 Git 仓库根不可用—`GitRepoRootResolver` 跨模块单源）无本数据域单窗口可控的清晰落点，属跨分册/跨模块/ultimate 档基建，未在本窗越权塞入，按并行窗与授权闸纪律留待对应窗口。surefire 合计 289 run / 0 fail / 0 err / 1 skip（skip 仍系 Windows symlink 场景诚实跳过） -->
<!-- 【校订 2026-10-03 二十】D.1 功能码落位（数据域可单窗口离线自验的两个非跨引擎码）——把 D.1 错误码表中数据域可单窗口落点且**不需活库/不需跨分册/不需跨模块**的两个码按"码落消息字面"惯例（同 C.8 校订十九的 ECOS-DATA-051/052/057）真正落到源码：① **ECOS-DATA-013（400 DAG 非法：环/孤立/C.4-2 步骤 2 静态校验）**——`PipelineExecutionService.topologicalSort`（Kahn 拓扑，单点抛出）的 `result.size() != nodes.size()` 分支（该分支同时覆盖环 / 孤立 / 引用未定义节点三态，Kahn 未消耗者即"未能拓扑定序"节点）由裸文案升级为携带常量 `CODE_DAG_INVALID="ECOS-DATA-013"`，且**保留既有 `循环依赖` 字面**（不因加码而破坏既有 `PipelineTopologyValidationTest` 的 `contains("循环依赖")` 断言——只增不改）；② **ECOS-DQ-101（409 DQ 规则状态机非法迁移）**——`DqRuleLifecycleServiceImpl.assertTransition` 由实例方法改**纯静态**（仅查包级静态 `TRANSITIONS` 表、无实例态 → 可离线脱离 Spring/DB 直测状态机契约），两个违例分支（from==null 未知态 / from→to 不在白名单）统一携带常量 `CODE_DQ_RULE_STATE_INVALID="ECOS-DQ-101"`。**护栏**：(a) 既有 `PipelineTopologyValidationTest#cycleDetectedBetweenTwoNodes` 增加一条 `contains("ECOS-DATA-013")` 断言（复用既有单节点/双节点 mock 骨架，A↔B depends_on 互指触发 013）；(b) 新建 `DqRuleStateTransitionInvalidTest`（5 例全绿不触库不 Spring 不联网，直接调静态 `assertTransition`）：① 非法迁移 DRAFT→ACTIVE → 抛 101 ② 终态无后继 SUPERSEDED→ACTIVE → 抛 101 ③ 未知初态 null→ACTIVE → 抛 101 ④ 合法迁移白名单（DRAFT→IN_REVIEW / IN_REVIEW→ACTIVE / IN_REVIEW→REJECTED / ACTIVE→DISABLED）`assertDoesNotThrow`（防护栏过度拦截真迁移）⑤ 白线常量值 `assertEquals("ECOS-DQ-101", CODE_DQ_RULE_STATE_INVALID)`（防空文件静默绿）。**范围口径**：D.1 其余代码——`ECOS-DATA-041`（503 跨引擎 fail-loud）落在**并行窗争用的** `DataNetResourceClient`（ontology caliber/metric WIP，main-scope 编译未绿），早已在校订八挂起；`ECOS-DQ-102`（400 规则表达式禁用构造：自由 SQL/裸表名——需一处规则表达式 lint 落点，当前无该校验入口，落地属规则表达式校验特性，不塞入本窗）、`ECOS-DQ-111`（404 治理记录不存在，须与 `ECOS-DATA-031` 严格区分——现有多处 `ApiResponse.notFound("…不存在")` 未挂功能码，且"治理记录"确切落点需 F02-09 治理面收口裁决，不在本窗锁码）。三码留待对应窗口；本窗只机械巩固 013 + 101 两项。全模块离线 `mvn -o test`：`PipelineTopologyValidationTest` 9/9（新增 013 断言）+ `DqRuleStateTransitionInvalidTest` 5/5 = 本轮 14/14 绿；surefire 报告独立求和 294 run / 0 fail / 0 err / 1 skip（skip 仍系 Windows symlink 场景诚实跳过），BUILD SUCCESS，全部不触库/不 Spring/不联网 -->
<!-- 【校订 2026-10-03 二十一】D.1 ECOS-DQ-102（400 规则表达式含禁用构造）落位——本条把校订二十中"102 需一处规则表达式 lint 落点、当时判断当前无校验入口"的结论**经复核推翻**：DQ 规则参数字段 `DqRuleDTO.parameters`（data-engine-api，doc 行 234"仅允许绑定变量，禁自由 SQL"、行 431 D.1 码表"自由 SQL/裸表名"）一处即清晰单窗口落点，且 `DqRuleLifecycleServiceImpl` 已在数据域独有（非并行窗争用），create 与 update 两条写路径共用。落码：新增纯静态 `findForbiddenSqlConstruct(DqRuleDTO)` + `assertNoForbiddenSqlConstruct(DqRuleDTO)`——对 `parameters`（JSON 字符串）用单词边界正则 `FORBIDDEN_SQL_STATEMENT`（`\b(select|insert|update|delete|drop|alter|truncate|merge)\b`，case-insensitive）探针，命中即抛 `BusinessException(CODE_DQ_RULE_EXPR_FORBIDDEN="ECOS-DQ-102": …命中: <verb>…)`，落在 `createDraft`（`validateRuleDTO` 之后）与 `updateDraft`（DRAFT 断言之后）两边界。两个精度取舍已实证（避免误伤既有合法参数）：**(1) 不剥除引号字面量**——真实注入 `{"query":"select * from x"}` 的动词恰在 JSON 值串内，剥离会连 SQL 一起剥掉令护栏空转，故直接扫原始 JSON 文本；**(2) 只匹配独立单词的 DML/DDL 语句动词**——避开 camelCase 字段名（`createdAt`/`selectedColumns`/`deletedFlag` 因后跟词字符而无边界、不命中）与日期区间 key `from`/`to`（非语句动词），使 `setParameters("{}")`（既有 `DqRuleLifecycleTest:256`）等合法参数零误伤。护栏 `DqRuleParamForbiddenSqlGuardTest`（5 例全绿不触库不 Spring 不联网，直接调静态探针，无需 mock）：① SELECT 注入→命中+抛 102 且消息含命中动词 ② DROP/UPDATE 注入（case-insensitive）→命中 ③ 纯绑定变量/`{}`/null/空白→null（不命中）+ null dto→null ④ camelCase + from/to 日期区间→不误伤 assertDoesNotThrow ⑤ 白线 `assertEquals("ECOS-DQ-102", CODE_DQ_RULE_EXPR_FORBIDDEN)`。全模块离线 `mvn -o -pl engine/data-engine/data-engine-impl test`：**299 run / 0 fail / 0 err / 1 skip（294→299，本轮 +5 例），BUILD SUCCESS**（surefire 报告独立求和复核一致；skip 仍系 Windows symlink 场景诚实跳过）。D.1 码表现余 `ECOS-DQ-111`（404 治理记录不存在，须与 031 严格区分，需 F02-09 治理面收口裁决）与 `ECOS-DATA-041`（041 仍在并行窗 ontology，校订八挂起）两项待对应窗口 -->
<!-- 【校订 2026-10-03 二十三】W50/D-8/C39(M1) E.1 ST07 5+1 授权 schema 清单护栏——`SchemaInventoryGateTest`（data-engine-impl 离线单测 M1，2 例全绿不触库不 Spring 不联网，本轮新增 +2 例）：把 W50 缺陷"D-8：`ecos_dq` 等 11 个 schema 越出 ST07 5+1 授权（AGENTS.md）"从一次性缺陷登记固化为可复跑棘轮。扫描单源目录 `gateway/…/db/migration/*.sql`（197 文件，与 W59/DdlComplianceLintTest 同根），以**动词 anchor + schema-限定 `<id>.<id>`** 提取所有 DDL/DML 引用（CREATE/ALTER/DROP TABLE|INDEX|SCHEMA|SEQUENCE|VIEW|MATERIALIZED VIEW / INSERT INTO / UPDATE / DELETE FROM / TRUNCATE；可选 IF NOT EXISTS/ONLY/CASCADE；仅小写字母开头 identifier 避免 Java 类名混入），逐条剔除 ST07 授权集合（`public` + 5 引擎 schema `ecos_data`/`ecos_ontology`/`ecos_knowledge`/`ecos_ai`/`ecos_cognitive` + 业务域 `ecos_dw`）后收集越出 distinct schema，**尺 = 越出 5+1 的 distinct schema 数**，基线 11（2026-10-03 上线日 C39 实测：`ecos_dq/ecos_agent/ecos_workflow/ecos_rule/ecos_pipeline/ecos_object/ecos_mission/ecos_identity/ecos_config/ecos_catalog/ecos_audit`，正好与 D-8 点名的"11 个越出 schema"一致）；只减不增——合规新文件若只引用已授权 schema 不推高基线，越出集合缩减也会绿灯放行。白线：断言 `ecos_dq` 必须在越出集合中（V166/V171 已授权单源目录当前仍在用 `ecos_dq.` 前缀建表），防止动词 anchor 演进/regex 退化到 0 命中时静默判绿。**与既有 W59 棘轮不同维度**：`LegacyDebtBaselineRatchetArchTest` W59 锁 `gen_random_uuid`(MC01) 违规文件计数、`DdlComplianceLintTest` 锁 JSONB/DL04+SERIAL/MC01 列声明位置、`MigrationBasenameSingleRootArchTest` 锁跨根同名 V*.sql 碎片、`BareTableNameRatchetArchTest` 锁本册 8 新脚本的 schema 限定；本类锁 D-8 面 = **越出 5+1 schema 归属**（ST07 + 铁律 §3.1 schema 归口），四者正交独立。范围口径：只锁越出集合**规模**（11 只减不增），不枚举具体名册成员（避免与新 V* 号硬绑导致基线频繁维护）；存量 11 个越出 schema 属仓根 ST07 knownLegacy，"只停写不迁移"（AGENTS.md），本护栏锁增殖不强制收敛，待 E.1 归属裁决收口后基线才允许下探。全 `mvn -o -pl engine/data-engine/data-engine-impl test` surefire 报告独立求和 **304 run / 0 fail / 0 err / 1 skip（302→304，本轮 +2 例），BUILD SUCCESS**，全部离线不触库/不 Spring/不联网。至此 task #31 "对账 doc 点名测试 vs 实存类" 完成：29 个 doc 命名的"未实存 `*Test`"token 中，本条补齐唯一的真缺口 W50；其余按 verify-before 复扫实证归为**假缺口**（覆盖已并入 `BusinessDomainWriteServiceTest` 的 `WriteChannelRejectsUnlistedColumnTest`/`NonOlapControlWriteGuardTest`/`IdempotentWriteReplayTest` + `DqRuleF01ToF11Test` 的 `PendingQueueActionableTest`/`NoSilentDefaultFillArchTest` + `LegacyDebtBaselineRatchetArchTest` 的 `W49 QualifiedTableName`/`W54 NoJdbcTemplateInService`/`W56 GatewayNoDomainTable` + `AuditColumnPresenceExtendedTest` 的 `ActiveFlagUniquenessTest` 前置 DDL 契约面 + `PipelineTaskReadonlyTest` 的 `YamlTaskReadOnlyTest` 等价覆盖）或**授权闸/跨窗口/基建闸**（校订八挂起的 `CrossEngineFailLoudTest`（ontology 并行窗编译未绿）/ 校订十一声明的实跑库族 `SqlConsoleRegressionPgTest`/`SqlConsoleRegressionDorisTest`/`DataEndpointTriFilterTest`(三滤波器三态)/`DqGovernanceEndpointReachabilityTest`/`DqLegacyMigrationParityTest`(17→17)/`MigrateFourYamlTasksTest`(V168 psql 实跑)/`SchemaDriftLintTest`(库表 vs 单源差集)/`DatanetEndpointParityTest`(datanet :18082 未独立承流)/`DatanetNoExclusionTest`(S3 §前置②)/`DualRunDiffZeroTest`(parquet 双跑避 diff)/`KbChunkWriteGoneArchTest`(分册 04 A3 退出跨窗)/`BusinessDomainWriteArchTest`(C.3.6 严格版跨模块 ArchUnit 需专用授权)/`RepoRootMissingDirectoryTest`+`RepoRootSymlinkEscapeTest`(已由 `GitRepoRootHardeningTest` 归并含 symlink `assumeTrue(false)` 诚实 skip Windows)。F02-01~15 十五项功能域逐条对账全部映射到已 commit 绿测试或已登记授权闸，doc 40-实现面/20-需求追溯映射无新增缺口。 -->
<!-- 【校订 2026-10-03 二十二】D.1 ECOS-DQ-111（404 治理记录不存在，与 031 严格区分）落位——本条把校订二十/二十一中"111 需 F02-09 治理面收口裁决、不在本窗锁码"的结论**经复核推翻**：核实发现 111 反而有清晰单窗口落点且**无需改共享 gateway/无需跨模块**。实证链：① `DqGovernanceServiceImpl.getRuleDetail(id)`（新端点 `GET /api/v1/dq/rules/{id}` 的读面，data-engine 独有）在记录缺失时已 `throw new NotFoundException("DQ 规则 <id> 不存在")`，但其消息被 gateway `GlobalExceptionHandler.handleNotFound(NotFoundException)` 的 `ApiResponse.notFound(ex.getMessage())` **逐字透传**到真实 404 响应体——故按仓内既成惯例（数据域 `AssetController:95 notFound("ASSET-010: …")` 已证"功能码落消息字面"）把该 404 消息**前缀 `ECOS-DQ-111:`** 即让 111 落进真实 404，零越权改共享层；② 与 `ECOS-DATA-031` 严格区分是**天然成立**的：031 由同一 handler 显式映射"数据层 SQL/映射异常 → 500, ECOS-DATA-031, 不再掩蔽成 404"（F02-08/W46 已落），而 111 只走 `NotFoundException → 404`，两者异常类型/HTTP 码/语义互斥，本护栏以"正确异常类型 + 真实 404 + 携 111 码"三证钉死该分工。落码：`DqGovernanceServiceImpl` 新增常量 `CODE_DQ_RULE_NOT_FOUND="ECOS-DQ-111"` + `getRuleDetail` 缺失分支消息前缀（**单行、不新端点不改签名不删改**，符合 API 只增不改）。**实证 pre-fix 缺口**（非臆测）：改造前该 `NotFoundException` 消息无码，`handleNotFound` 透传后 404 体不带任何 DQ 功能码——111 之前确实"未落码"（全仓 grep ECOS-DQ-111 = 0 命中），本条补齐。护栏 `DqRuleDetailNotFoundCodeTest`（3 例全绿不触库不 Spring：mock `DqRuleMapper.findById→null` + `DqSecurityService`）：① 记录不存在 → 抛 `NotFoundException` 且消息含 `ECOS-DQ-111` + 被查询 id ② 缺失时仍 `verify` 落 `auditRead("DQ_RULE_DETAIL", id)` 审计痕（不吞审计）③ 白线 `assertEquals("ECOS-DQ-111", CODE_DQ_RULE_NOT_FOUND)`。**范围口径**：本护栏只锁**规则详情读面**这一已证清晰的 404 落点，未把 111 强推到 issue/version 等其他 404 面（那些属 F02-09 治理面完整收口裁决，逐个挂码需治理面对齐，不在本窗越权撒网）。`ECOS-DATA-041`（跨引擎 fail-loud）仍在并行窗 ontology `DataNetResourceClient`（caliber/metric WIP、main 编译未绿），校订八挂起不越界。全 `mvn -o -pl engine/data-engine/data-engine-impl test`：surefire 报告独立求和 **302 run / 0 fail / 0 err / 1 skip（299→302，本轮 +3 例）**，BUILD SUCCESS，全部不触库/不 Spring/不联网。至此 D.1 表数据域单窗口可落码码（011/012/013/021/022/023/031/051/052/057/101/102/111）已**全落码 + 护栏**，仅 `ECOS-DATA-041` 因并行窗 ontology 编译未绿留挂（校订八） -->
