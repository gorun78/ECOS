# 详细设计 03 — 本体域（buszhi 服务 + ontology-engine 金·I）

> 来源: AI Agent（资深系统设计师视角，用户指令"分册按子系统顺序生成各子系统的设计文档…不符合要求的一定要改"）
> 日期: 2026-09-29 | 版本: v1.1（v1.1 2026-09-29：随**需求检视报告 §十四.1** 批量批准定版——**R-4 a+b 并行／R-5 ①+③／R-6 ①（口径主权归 ontology）／R-7 归属①+模型 b** 四项按本册推荐项批准；R-6 ① 的文本更正已同步 PRD-03 §1.4 与 PRD-09 FC-01。Gate-1 已签字、Gate-2 已通过。**批准 ≠ 已实现 ≠ 已验收**） | 责任人: AI Agent
> 上游依据: `docs/20-需求/需求检视报告-2026-09-28.md` §十二（用户批准 2026-09-28，Q1~Q14 + E2E 补记）**+ §十四.1（本册 R-4／R-5／R-6／R-7 已批准，2026-09-29）+ §十四.3（Gate-1 已签字／Gate-2 已通过）**—— 依 Q12，本节即需求基线批准凭证，不再补 APPROVAL_RECORD JSON
> 主责 REQ: **REQ-ONTO-01（领域语义发布）· REQ-ONTO-02（指标口径元数据）· REQ-ONTO-03（实体映射契约稳定性）· REQ-ONTO-04（Function 沙箱审计完备）**（`PRD-03-ontology-engine需求规格-2026-09-28.md`，4 项，全部 P0/P1）
> 关联 REQ（跨册接缝，本册为供方）: REQ-DATA-01（事实表列存在性 → V3 门禁）、REQ-KB-02/C1~C4（本体快照消费方）、REQ-FC-01（口径主权，PRD-09）、REQ-SEC-01（CLS 消费）、REQ-NF-04（traceId 审计）
> 前置依赖: 分册 00（承流口径 ADR-15、两态寻址、匿名清单单源、ApiResponse/traceId）、分册 01（`SecurityDecisionService#decide`、审计 `ecos.audit` 链）、分册 02（CURATED 事实表与写通道、schema 归属 R-1）
> 关联 ADR: ADR-15（制品≠承流）、ADR-8（本体=语义唯一源）、ADR-11（图谱双形态）、ADR-12（主控制 schema 三步走）、ADR-14（确定性计算产物经 data-engine 落业务域）
> 规范锚点: `.trae/rules/架构铁律.md` v2.0（§0.3.1 workflow 单点归 buszhi、§0.5-5 映射必校验、§2.4、§2.5-4 调度归 runtime-task、§3.1 DDL 单源、§十 配置单源、§十一 版本×Git 归档）；`.trae/rules/数据库访问规范.md` v1.2（ST07/MC01/MC02/MC03/IR01/IR04/IR05/DR05~DR08/§四 附则1 裸表名=FAIL/§五 三层）；`.trae/rules/后端开发规范.md` v1.1（§九 runtime 底座、§十一 版本归档）；`.trae/rules/前端开发规范.md` v1.1（组件 ≤800 行、每 Tab 独立文件）
> **编号接续**: 分册 00 = W01~W15 → C16~C21；分册 01 = W29~W40 → C22~C29；分册 02 = W41~W66 → C30~C50；**本册 = W67~W89 → C51~C71**

---

## 〇、本册定位与判定基线

### 0.1 本册覆盖的部署制品

| 制品 | 端口 | 实测承载 | 说明 |
|:--|:--:|:--|:--|
| `services/buszhi` | 18083 | **未运行**（2026-09-29 实测 `netstat` 仅 3000 在听） | 36 个 Java 类 = workflow 域 23 类 + 启动类 + DTO；本体 payload 由 gateway 宿主 |
| `engine/ontology-engine` | — | gateway fat-JAR `@ComponentScan` 宿主 | **34 个 Controller**（实测 `find`），25 个 `@RequestMapping` 值，跨 5 个前缀家族 |
| `services/buszhi/impl/buszhi-impl` | — | gateway + buszhi 双向可装 | workflow 单点归 buszhi（铁律 §0.3.1），但当前唯一承流方是 gateway |

### 0.2 本册要解决的核心命题

一句话：**本体域是"全平台语义供给方"，但它当前供给的是四套并行且互不一致的语义** —— 四重孪生 + 两个零存在的事实：

```
① 5 个 URL 前缀家族 × 25 个 @RequestMapping × 34 Controller（孪生接口面）
② 2 套本体读写模型（/api/v1/ecos/ontologies/{id}/entities 嵌套式 vs /api/v1/ontology/objects 平铺式）
③ 2 套 workflow 模型（public.ecos_workflow 8 行 trigger_event 式 vs public.ecos_workflow_v2 11 行 nodes/edges 式）
④ 2 张指标表（ecos_ontology.metric_definition 0 行 vs public.ecos_biz_metric 75 行）—— 且两张都是零引用孤表
⑤ 口径（caliber）：英文标识符全仓 0 命中 → REQ-ONTO-02 与 PRD-09 FC-01 的语义主体在代码与库里都不存在
⑥ 提案链：ecos_ontology_proposals 0 行 → 发布门禁 V1/V2/V3 无处生效，提案→审批→执行→版本发布整链未持久化运行
```

本册主任务**不是**"补 PRD-03 的功能清单"，而是：
1. **把口径（caliber）从零建成一等实体**（否则 REQ-ONTO-02、PRD-09 FC-01、REQ-FC-02/03 全部悬空）；
2. **收敛四重孪生**（接口面、本体模型、workflow 模型、指标表）；
3. **让发布门禁真正可执行**（提案落库 + V1/V2/V3 校验 + 拒绝路径）；
4. **修两条结构必败的链路**（Function 审计实参错位、workflow 审批表在代码查询不到的 schema）。

### 0.3 判定基线（写作纪律）

| 纪律 | 本册执行方式 |
|:--|:--|
| 只写实测事实 | §一 O-1~O-27 每条附 `file:line` / `pg_tables`/`count(*)` 实查 / `grep` 命中数；未实查的一律标"未验证"并写进改造项 |
| 验收可执行 | 「验收方式」只允许 `mvn -Dtest=X` 或 `tests/*.spec.ts` 标识；禁写"人工确认""Playwright 通过" |
| 存量只定性不迁 | schema/表归属改动全部挂裁决（本册 R-5/R-6/R-7，见 §七.3），不擅自物理搬迁 |
| 发现即回填 | W67~W89 必须回填 `架构设计说明书 §十一` 为 C51~C71，并同步 PRD 回写清单（§七.2） |

---

## 一、现状事实（实测 O-1 ~ O-27）

| # | 实测事实 | 证据 | 规范判定 / 去向 |
|:--|:--|:--|:--|
| O-1 | ontology-engine **34 个 Controller**，25 个类级 `@RequestMapping` 值，分属 **5 个前缀家族**：`/api/v1/ecos*`（含 `/ecos`、`/ecos/ontologies`、`/ecos/domains`、`/ecos/entities`、`/ecos/mappings`、`/ecos/versions`、`/ecos/ontology`）、`/api/v1/ontology*`（`/ontology`、`/ontology/{proposals,mappings,functions,data,actions,action-types,export,glossary}`）、`/api/v1/engine/ontology*`（``、`/git`、`/graph`、`/copilot`、`/settings`）、`/api/ontology`、`/api/ecos/ontology`、`/api/v1/lineage` | `grep -h @RequestMapping $(find engine/ontology-engine -name '*Controller.java')` 实测清单（25 值） | 与分册 02 D-3 同族（数据域 5 家族）；铁律 §2.4 / route 单源违例 → **W68** |
| O-2 | **同一基路径注册两个 Controller**：`OntologyController`（250 行）与 `OntologyVersionController`（160 行）都是 `@RequestMapping("/api/v1/ecos/ontologies")`；`OntologyDomainController`（`/api/v1/ecos/domains`，162 行）与 `OntologyDomainApiController`（`/api/v1/ontology`，333 行）同时提供 domains 读写 | `grep -n @RequestMapping` 两个文件（分别为 `OntologyController.java` / `OntologyVersionController.java` 类级注解） | Spring 不报冲突仅因方法级路径不同，但**契约不可发现**：调用方无法从基路径推断资源归属；文档与契约测试无法锚定 → **W79** |
| O-3 | **本体资源两套读写模型并存**：嵌套式 `/api/v1/ecos/ontologies/{ontologyId}/entities\|relationships\|properties`（`OntologyController`）与平铺式 `/api/v1/ontology/objects\|links`（`OntologyDomainApiController:127,179,202,224,263,276`）；前端两套都在调（`/api/v1/ecos/ontologies` 3 处、`/api/v1/ontology/objects` 1 处） | 后端 mapping 实查 + 前端 URL 频次统计（`grep -rho '/api/v1/(ecos\|ontology\|engine/ontology)…'`） | 双模型 = 语义漂移必然；收敛为平铺领域 API + 嵌套只读兼容别名 → **W69** |
| O-4 | **映射两套**：`OntologyMappingController`（354 行，`/api/v1/ontology/mappings`）与 `EcosMappingFullController`（166 行，`/api/v1/ecos/mappings`），另有 `OntologyEntityMappingController`（51 行，`/api/v1/ontology`，承载 REQ-ONTO-03 冻结契约 `entity-mappings`） | `wc -l` + mapping 实查 | 契约面（O-5）与 CRUD 面必须分离；→ **W70** |
| O-5 | **REQ-ONTO-03 冻结契约有实现、零测试**：`OntologyEntityMappingController` 提供 `GET /api/v1/ontology/entity-mappings`；消费方 `KbEntityInstanceExtractionService.java:595` 拼 `ontologyApiBase + "/ontology/entity-mappings?ontologyId="`；全仓无 `EntityMappingContractTest` / 快照比对 | `grep -rln "entity-mappings"` = 3 命中（1 controller + 1 kb 消费 + 1 AutoDiscover 注释）；ecos-tests 无 `*.spec.ts` | PRD-03 §3.3 三个用例（改映射重抽/映射指向不存在列/schema diff 只增）零落地 → **W71** |
| O-6 | kb 以 **HTTP 自环**访问本体：`KbEntityInstanceExtractionService.java:104` `@Value("${ecos.ontology-api-base:http://localhost:8080/api/v1}")`，默认值指向 **gateway 自身端口**（gateway 同时宿主 kb 与 ontology） | 该文件 :95,104,108,595,615,646 | ADR-15 S0 态下是"进程内 HTTP 绕圈"；切 S3 后指向必错。改两态寻址 + 失败不可静默 → **W84** |
| O-7 | **提案链整链零运行**：`ecos_ontology_proposals` = **0 行**；控制器 `OntologyProposalController`（`/api/v1/ontology/proposals`，submit/approve/reject/verify/execute/approve-and-publish 六端点齐备）；执行链 `executeAndPublish` 存在并写 `EXECUTED`+回填 `version_id` | `SELECT count(*) FROM public.ecos_ontology_proposals` = 0；`grep @PostMapping` 命中 :324,333,344,432,508,554 | PRD-03 §1.4 发布流程从未跑通（0 行=无一次成功提案落库）；门禁 V1~V3 无宿主 → **W74/W75** |
| O-8 | **提案 SQL 三违规**：`OntologyProposalController.java:500,511,535` 等使用 `SELECT * FROM ecos_ontology_proposals WHERE id=?::bigint` —— IR04（`SELECT *`）+ **MC03（`::bigint` 为 PG 私有转型，无 `databaseId` 分支）**+ §四 附则1（裸表名） | 源码实读 :500-536 | MySQL/Oracle 档必失败（`?::bigint` 语法非法）→ **W72** |
| O-9 | **`execute` 只做状态检查，无 V1/V2/V3 语义门禁**：`executeProposal` 仅断言 `status ∈ {verified, approved, pending}`，随后直接 `executeAndPublish(...)` | `OntologyProposalController.java:514-524` | PRD-03 §1.4-2"任一不过 → 提案拒绝执行"未实现 → **W75** |
| O-10 | **口径（caliber）在代码与库中零存在**：`grep -rli "caliber"` 覆盖 `engine/ services/ workspace/ runtime/ ecos_frontend/src ecos-sql` = **0 命中**；中文"口径"在后端 24 个 Java 文件中出现，语义均为"标准/口径一致"的口头用法（如 `ToolExecutorService.java:246`「自报值取主体（PMO-74 H9-T1 口径）」、`BuszhiServiceApplication.java:21`），非领域实体；库内无 caliber 表 | 全仓 grep（含 `.sql/.ts/.tsx/.yml/.java`） | **REQ-ONTO-02 全部 3 个字段、PRD-09 FC-01 口径服务、REQ-FC-02/03 的版本快照复现，全部依赖一个不存在的实体** → **W73（R-6）** |
| O-11 | `ecos_ontology.metric_definition` **缺 REQ-ONTO-02 三字段**：实测列 = `id VARCHAR(64)`、`code`、`name`、`expression`、`aggregation`、`entity_code`、`created_at`；**无 `caliber_id` / `formula_version` / `caliber_snapshot_json`**；且 `id VARCHAR(64)` 越出 MC01 的 `VARCHAR(36)`；`created_at` 违 DR06（应 `create_time` 等 5 列）；`time stamp` 无 `version_no`/`is_deleted`/`domain` | `\d ecos_ontology.metric_definition` 实查 | PRD-03 §2.1 声称"只加列"即可，实测基表连最小合规形态都不满足 → **W77** |
| O-12 | **两张指标表都是零代码引用孤表**：`ecos_ontology.metric_definition` = 0 行、`public.ecos_biz_metric` = 75 行（列 `dept_id/metric_type/metric_value NUMERIC(18,2)/target_value/metric_month/goal_id BIGINT`，外键指向 `public.ecos_wm_goal`）；`grep -rln` 两表名覆盖 `ecos_backend/**/*.{java,xml}` 与 `ecos_frontend/src` = **0 命中**；DDL 只在 `ecos-sql/{mysql,oracle,postgresql}/04_ecos_ontology.sql、07_ecos_cognitive.sql`、`migration/12_to_8_schema.sql` 与 **`V52__ecos_ontology_worldmodel.sql`**（即 PRD-03 §1.1 所引的 V52）；75 行数据由 **`V28__ecos_ceo_biz_scenario.sql:34` 的 `INSERT INTO ecos_biz_metric …` 演示种子**灌入 | psql count + 后端/前端 grep 各 0 + migration grep 命中 V28/V52 | "指标"这一核心语义只有表壳、无读写路径 → REQ-ONTO-01 的"10 项指标登记"实际无任何承载；且 `ecos_biz_metric` 的性质是 **CEO 场景演示种子数据**，非平台事实。语义二分（定义 vs 实例）+ 孤表处置 → **W77 / W78** |
| O-13 | **Function 审计实参错位（数据污染）**：`FunctionCacheManager.writeAudit(String functionName, String expression, String entityName, String resultValue, long executionTimeMs, String callerId, String status, String errorMessage)`；`FunctionController` 全部 3 个调用点传 `writeAudit(null, expression, entityName, …)`；实测 `public.ecos_function_audit_log` 5 行中 `function_name` **全为空**，`caller_id` 列却存着函数名（`fn_emp_tenure` / `fn_order_gross_margin` / `custom_function_6416`） | 方法签名 `FunctionCacheManager.java:118-121` + 调用点 `FunctionController.java:82-83,91-92,…` + `SELECT id, function_name, caller_id FROM ecos_function_audit_log` 实查 | **审计不可归因**：REQ-ONTO-04 的"哪个函数被谁调用"两列皆废 → **W80（P0）** |
| O-14 | Function 审计其余四缺陷：① `callerId` 缺省 `"anonymous"` 继续执行（`FunctionController.java:76`），PRD-03 §4.1-2 要求"审计上下文缺失 → 400 拒收"；② `writeAudit` 整体 `catch (Exception) log.error` 吞掉（`FunctionCacheManager.java:138-140`），无 `ecos.audit` Kafka 事件（ST06）；③ `queryAudit` 用 `SELECT * FROM ecos_function_audit_log WHERE 1=1` + 字符串拼接（`:148-150`，IR04/IR05）；④ 表 `id BIGSERIAL nextval(...)`（违 MC01），无 DR05~DR08 列 | 源码 + `\d` 实查 | → **W81 / W82 / W83** |
| O-15 | **表镜像漂移（本体侧同样存在）**：`public` 有 14 张本体表且有数据（`ecos_ontology` 1、`ecos_ontology_entity` 11、`ecos_ontology_property` 95、`ecos_ontology_relationship` 20、`ecos_object_data` 44、`ecos_object_version` 20、`ecos_glossary_term` 32、`ecos_business_glossary` 10）；`ecos_ontology` schema 同名镜像 8 张全 **0 行**（`ecos_object_data`/`ecos_object_version`/`ecos_workflow*` 等）；另有 5 张只在 `public` 存在的本体表（`ecos_ontology_data`/`_rule`/`_action` 皆 0 行） | `pg_tables` + 逐表 `count(*)`（见 §一 数据块） | 与分册 02 D-7 同族：引擎 schema 是空壳，写入全落 `public`（`search_path="$user",public` + 裸表名）→ **W87（挂裁决 R-1）** |
| O-16 | **workflow 双模型**：`public.ecos_workflow`（列 `id,name,code,description,status,trigger_event,workflow_type,version,created_at,updated_at`，**8 行**）与 `public.ecos_workflow_v2`（列 `id,name,description,status,mode,nodes,edges,created_at,updated_at,published_at`，**11 行**，`nodes`/`edges` 为 JSONB）并存；`ecos_workflow_node`(21)/`ecos_workflow_edge`(18) 又提供独立节点/边表；`ecos_ontology.ecos_workflow*` 镜像 0 行 | `information_schema.columns` 两表列集实查 + count | 铁律 §0.3.1 workflow 单点归 buszhi 只解决了"归属"，没解决"模型孪生"→ **W88（R-7）** |
| O-17 | **审批链结构必败**：`WorkflowApprovalRepository.java:44,50,56,62` 查询/写入裸表名 `ecos_workflow_approval`，而 `pg_tables` 显示该表**只存在于 `ecos_ontology`（0 行）**，`public` 中不存在；运行态 `search_path="$user",public` → 该表在承流路径上必然 `relation does not exist` | 代码 grep + `SELECT schemaname FROM pg_tables WHERE tablename='ecos_workflow_approval'` = `ecos_ontology` | PRD-08/REQ 的审批闭环不可用；被分册 02 C35（5xx 掩成 404）同类机制隐藏 → **W85** |
| O-18 | `WorkflowRepository.java:48,53,60,70,83,88` **`DELETE FROM ecos_workflow_v2 WHERE id = ?`**（物理删除）+ 全套 `SELECT *` + 裸表名；`WorkflowInstanceRepository.java:84` `current_node_ids = ?::jsonb`（MC03 PG 私有 + MC02 JSON 形态违规） | 源码实读 | DR05/DR06（应 `is_deleted` 软删）+ IR04 + MC03 → **W86** |
| O-19 | **sysman 跨域直查 buszhi 表**：`PortalSearchQueryService.java:52` 对 `ecos_workflow_v2` 做 `FROM`；buszhi 与 sysman 属不同服务域 | 源码实读 | IR01（域表只由本域引擎访问）→ **W82（P0）** |
| O-20 | **buszhi 服务越界装配数据引擎 bean**：`BuszhiServiceApplication.java:34-50` `@ComponentScan` 含 `com.chinacreator.gzcm.engine.data.service`（为 `OntologyCopilotController` 取 `CopilotServiceImpl`），`@MapperScan` 含 `engine.data.**.repository`、`engine.data.**.mapper`；`ICopilotService` 接口本身在 `runtime/common-api`（正确），但唯一实现落在 data-engine-impl | `grep -n ComponentScan/MapperScan` + `find -name CopilotServiceImpl.java` = `data-engine-impl/.../service/CopilotServiceImpl.java` | ArchUnit `shouldNotDependOnOtherEngines` 只看 import 图（实测 ontology 侧 0 条 `import …engine.data`），**对运行期 bean 装配越界零可见性** → **W84 / W81** |
| O-21 | **版本快照双写 DB + Git**：`OntologyVersionService.java:51-107` 归档经 `OntologyGitService`（`repositoryId="ontology"`、best-effort、失败仅 log）；同时 `snapshot` 列持久化在 `public.ecos_ontology_version`，实测类型 **`jsonb`**、`id VARCHAR(50)`、`created_at` | `\d public.ecos_ontology_version` + 源码 :88-107,147-174 | 后端规范 §十一「DB 只存**在用**版本、历史版本走 Git」；`jsonb` 违 MC02；`VARCHAR(50)` 违 MC01；best-effort 无补偿即"归档可能永久缺失"→ **W83（挂裁决 R-5）** |
| O-22 | **乐观锁与幂等**：`V4.3__ecos_ontology_optimistic_lock.sql` 存在，`Wave31OntologyConvergenceTest.java:182 c4_optimisticLockVersionMismatch` 有测试；`V4.4__ecos_ontology_rls.sql` 存在 | `ls migration` + `grep @Test` | 这是本册**唯一已验证为正向**的能力，保留并纳入契约测试基线（不改） |
| O-23 | **前端本体 UI 目录四份并存 + 两份 mock**：`pages/ontology`（33 文件 / 6999 行，最大 `DomainCanvas.tsx` 688 行、`ProposalPanel.tsx` 670、`VersionTimeline.tsx` 584）、`components/ontology`、`components/ontology-workbench`、`pages/OntologyWorkbenchLayout.tsx`、`pages/ObjectExplorer.tsx`(26) 与 `pages/object-explorer/`(81)、`pages/OntologyDesigner/`(85)；API 单源三文件 `services/ontologyApi.ts`、`services/ontologyWorkbenchApi.ts`、`services/glossary.ts`；`data/ontologyMockData.ts` **零 importer**，`data/enterpriseOntologyDemo.ts` 仅被 `OntologyWorkbenchLayout.tsx` 引用 | `wc -l` 目录级统计 + `grep -rln ontologyMockData` = 仅自身 | 组件行数合规（≤800，达标），但**入口与数据源分裂**：4 个 UI 簇 × 3 个 API 服务 × 2 个 mock → **W67 / W90→（并入 W67）** |
| O-25 | **本体侧审计走反射绕过事件底座**：`ontology/security/SecurityEngineClient.java:84-91` 以 `Object kafkaTemplateBean` + `@Qualifier("kafkaTemplate")` 注入，`:290-292` 用 `getClass().getMethod("send", …)` 反射调 `KafkaTemplate.send(KafkaTopics.AUDIT, event)`；bean 为 null 时 `:285-286` 仅 `log.warn` 后**继续返回**（审计事件丢失但不报错）。ArchUnit 规则 `businessMustNotDependOnSpringKafkaDirectly`（`gateway/src/test/.../ModuleDependencyArchTest.java:419`）白名单只放 `runtime.eventbus..`，其注释明确写"ontology 侧反射审计不含 spring-kafka 类型引用，**天然合规，无需豁免**" —— 即该规则**结构性看不见这条绕行**（`Optional<Object>` 反射） | 源码 :84-91,285-292 + ArchUnit 规则与注释实读 | 违反铁律 §2.5-4（事件唯一收敛 runtime-event）+ ST06（审计不可静默丢）；合规检查"过"不等于架构合规 → **W89** |
| O-26 | 承流事实：buszhi :18083 与全部 service 端口今日**未在监听**（仅前端 :3000），本体请求 100% 由 gateway :8080 承载（与分册 00 F-3、分册 02 D-1 一致） | `netstat -ano \| grep LISTEN` 实查 | ADR-15 S0；本册所有接口设计按 monolith/service 两态给出 |
| O-27 | 治理端点存活但未验签：本册 API 面 curl 未执行（gateway 未运行，返回 `000`）；分册 02 的 404 掩蔽结论仍适用于本册 | 今日 curl 实测 | 上线前须由 08 册 E2E spec 覆盖本体全族端点非 404 断言 → **W68** |

### 1.1 结论（一句话/条）

- **接口面**：5 个前缀家族 + 同基路径双注册 + 嵌套/平铺双模型（O-1~O-4）→ 契约不可发现、不可冻结、不可测（O-5）。
- **语义面**：口径零存在（O-10）、指标两张孤表（O-11/O-12）、提案链 0 行（O-7）→ **REQ-ONTO-01/02 事实上未开工**，PRD 若声称"既有提案系统与版本快照机制可复用"只成立一半（提案控制器在，提案数据与门禁不在）。
- **审计面**：Function 审计列错位 + 吞异常 + 匿名放行（O-13/O-14）→ REQ-ONTO-04 是"看似有表、实则不可归因"的假完备。
- **workflow 面**：双模型（O-16）+ 审批表跨 schema 缺失（O-17）+ 物理删除（O-18）+ 跨域直查（O-19）→ PRD-08 的流程闭环在数据层不成立。
- **越界面**：buszhi 扫 data-engine 包（O-20）与 kb 自环 HTTP（O-6）→ ArchUnit 的引擎边界规则对这两类越界**结构上看不见**（O-20 尾注）。
- **正向保留**：乐观锁 + RLS 迁移 + Wave31 收敛测试（O-22）是本册可依赖的既有地基，改造不得破坏。

### 1.2 REQ 追溯（本册主责）

| REQ | PRD 位置 | 实测达成度 | 本册落点 |
|:--|:--|:--|:--|
| REQ-ONTO-01 领域语义发布 | PRD-03 §一（7 对象 + 6 关系 + 10 指标 + V1~V3 门禁 + 领域包版本化） | **0/3 门禁实现**；对象/关系有 CRUD（`ecos_ontology_entity` 11 / `_property` 95 / `_relationship` 20）但 `biz-profit` 未发布；提案链 0 行 | A F03-03/F03-04 + C.2 + E.3/E.4 + W73/W74/W75 |
| REQ-ONTO-02 指标口径元数据 | PRD-03 §二（3 字段 + 3 规则 + 3 验收） | **未实现**（caliber 标识符 0 命中，字段全缺，基表违规） | A F03-01/F03-02 + C.1 + E.3 + W73/W77 |
| REQ-ONTO-03 实体映射契约稳定性 | PRD-03 §三（唯一入口 + 只增不改 + 3 契约用例） | 实现有、**测试零** | A F03-05 + D.2 + W71 |
| REQ-ONTO-04 Function 沙箱审计完备 | PRD-03 §四（Kafka FUNCTION_EXEC + 拒收路径 + 沙箱边界不变） | 部分（本地表有但列错位/吞异常/匿名放行） | A F03-06 + C.3 + W80~W83 |

---

## 二、A 章 功能设计（F03-01 ~ F03-16）

> 每项含：目标 / 输入输出 / 规则 / 异常 / 里程碑 / **验收方式（可执行标识）**。

### F03-01 口径（caliber）一等实体建立 — P0（本册第一优先）

**目标**：把 PRD-03 §二 与 PRD-09 FC-01 共同依赖、但当前不存在的"指标口径"建成控制域实体，使"指标绑定口径版本 → 发布冻结快照 → 预测按快照复现"这条主线可执行。

**实体与状态机**：

```
caliber（口径主表，控制域，归 ontology）
  ├─ caliber_id / code / name / unit / currency / dimension_json( TEXT ) / owner_role / status
  └─ caliber_version（版本表，一次升版一行，行不可变）
       ├─ version_no VARCHAR(20)（DR07）
       ├─ formula TEXT            -- 表达式明文，非 JSONB（MC02）
       ├─ additive SMALLINT       -- ADDITIVE=1 / SEMI=2 / NON=3（DR05 is_* 不适用，用数值码 + 字典）
       ├─ period_granularity      -- MONTH / QUARTER / YEAR
       ├─ status  : DRAFT → REVIEWING → APPROVED → RETIRED
       └─ git_ref  -- 历史内容以 Git 归档为准（R-5 裁决后生效）

metric_definition（改造 O-11 的孤表）
  ├─ caliber_id VARCHAR(36) NULL(Draft 可空) / formula_version VARCHAR(20) / caliber_snapshot TEXT
  └─ 状态: DRAFT → PUBLISHED → SUPERSEDED（PUBLISHED 前必须 caliber_version.status=APPROVED）
```

**规则（不可协商）**：
1. **只有 ontology 写口径**；cognitive / workspace / kb 一律经 `GET /api/v1/ontology/calibers/{code}/current` 只读（口径主权，Q3/铁律 §0.6）；
2. 口径升版**不自动**传播到指标（PRD-03 §2.2-2）：指标须走提案显式绑定新 `formula_version`；
3. `caliber_snapshot` 在指标发布时刻一次性写入，之后**只读**（更新语句命中该列 → 409 `ECOS-ONTO-041`）；
4. `DRAFT` 指标可无口径；`PUBLISHED` 指标无口径 = 发布失败（400 `ECOS-ONTO-040`）。

**异常与降级**：口径服务不可用（同 JVM 宿主下 = ontology bean 异常）→ 指标发布 **fail-closed（500 `ECOS-ONTO-042`）**，禁"跳过口径校验先发布"。

**验收方式**：`mvn -pl engine/ontology-engine/ontology-engine-impl -am test -Dtest='CaliberEntityTest,CaliberVersionImmutabilityTest,MetricCaliberBindingTest'`；E2E `ecos-tests/tests/ontology-caliber.spec.ts`（口径列表 + 指标绑定 + 发布拦截三断言）。

### F03-02 指标登记与可加性/单位校验（V2 门禁的实现体）

覆盖 PRD-03 §1.3 的 10 项指标（M_ACCEPTANCE_AMT / M_COLLECTION_AMT / M_SUBCONTRACT_AMT / M_FC_REVENUE / M_COST_SALARY / M_COST_MGMT / M_COST_MKT / M_FC_PROFIT / M_REALIZATION_RATE / M_COLLECTION_CYCLE），逐项登记编码、公式、可加性、期间、币种、血缘。

**单位推导规则**（V2 的可执行定义，替代 PRD 的"单位可推导且不冲突"）：
- 表达式抽象为四则树，叶子带单位（金额/月/比率/人天/人数），`金额±金额=金额`、`金额/金额=比率`、`金额/人数=金额`、`月±月=月`、其他组合 → 400 `ECOS-ONTO-021` 并返回推导链；
- `M_REALIZATION_RATE`（比率）与 `M_COLLECTION_CYCLE`（月）**禁参与可加聚合**，下游误用 = `ECOS-ONTO-022`。

**验收**：`mvn … -Dtest='MetricUnitDerivationTest,MetricAdditivityGuardTest'`（10 项指标逐条：`M_FC_PROFIT` 正例 + `比率−金额` 反例 + `SUM(M_REALIZATION_RATE)` 反例）。

### F03-03 发布门禁 V1/V2/V3（挂到提案执行前，修 O-9）

**插入点**：`OntologyProposalController#executeProposal` 与 `#approveAndPublish` 的**状态检查之后、`executeAndPublish` 之前**，调用新增 `PublishGateService.validate(proposal)`。

| 门禁 | 判据 | 拒绝码 | 依赖 |
|:--|:--|:--|:--|
| **V1 口径** | 提案 payload 中每个指标必带 `caliber_id`，且对应 `caliber_version.status = APPROVED` | `ECOS-ONTO-040` | F03-01 |
| **V2 单位** | 单位推导无冲突 + 可加性与聚合声明一致 | `ECOS-ONTO-021/022` | F03-02 |
| **V3 血缘列存在** | 指标/映射引用的事实表列**真实存在**，经 data-engine 元数据 REST 校验（禁跨 schema 直查 `ecos_dw`） | `ECOS-ONTO-030` | 分册 02 D 章 `GET /api/v1/datanet/metadata/columns` |

**三态语义**：通过（继续执行）/ 拒绝（提案停留 `PENDING`，返回逐条违反项）/ **依赖不可用**（V3 调 data-engine 失败）= 503 `ECOS-ONTO-050`，**不得默认通过**（铁律 §2.4-8）。

**验收**：`mvn … -Dtest='PublishGateV1Test,PublishGateV2Test,PublishGateV3Test,PublishGateDependencyDownTest'`；E2E `ontology-publish-gate.spec.ts`（正反各 1，含拒绝消息可见）。

### F03-04 `biz-profit` 领域包首发布与快照

- 领域包 = 7 类对象（Customer / Contract / Project / Department·ProjectTeam / AcceptanceStage·CollectionStage·SubcontractStage / ResourceInput / SalaryCost·ManagementCost·MarketingCost）+ 6 关系（OWNS 1:n / DERIVES 1:n / ATTRIBUTED_TO n:m / HAS_STAGE 1:n / CONSUMES 1:n / INCURS 1:n）+ 10 指标；
- 关系**基数登记**落入 `ecos_ontology_relationship` 新增列（`cardinality VARCHAR(16)`，MC02 白名单内），供 C2 关系合法性校验消费（PRD-03 §1.2 尾注）；
- 发布产物：一条 `ecos_ontology_version`（在用版本）+ Git 归档 ref（R-5）+ `kb_ontology_snapshot` 写事件（PRD-04 消费）；
- 幂等：同一 `(domain_code, version_no)` 重复发布 → 409 `ECOS-ONTO-011`，不产生第二版本。

**验收**：`mvn … -Dtest='BizProfitPackagePublishTest,KbSnapshotEmitTest'` + `ecos-tests/tests/scenario-profit-forecast.spec.ts` 第一步（领域包存在）。

### F03-05 实体映射契约冻结（REQ-ONTO-03 落地，修 O-5）

**契约基线文件**（新增，纳入版本控制）：`docs/30-设计/contracts/ontology-entity-mappings.schema.json` —— 冻结 PRD-03 §3.1 字段集（`entityCode/datasetId/resourceName/materialized/fieldMappings{entityField,physicalColumn,transform}`）。

**三条硬规则**：
1. **只增不改**：CI 比对基线，字段删除/改名/类型变更 → 构建失败（`EntityMappingContractFrozenTest`）；
2. `materialized` 裁度语义固化：`null`/`""`/`"true"`/`"1"` = true，其他 = skip（PRD §3.2-2，Q2_SKIP 口径），以表驱动单测锁定；
3. **映射不落 kb 表**（kb 仅内存装配）—— 加 ArchUnit/grep 断言：kb-engine 无 `INSERT INTO …mapping` 语句。

**PRD-03 §3.3 三用例** → `EntityMappingContractCasesTest`（换列生效 / 指向不存在列产生 `INVALID_MAPPING` issue 且不实例化 / schema diff 只增）。

**验收**：`mvn -pl engine/ontology-engine/… -Dtest='EntityMappingContract*'` + `mvn -pl engine/kb-engine/… -Dtest='KbMappingSkipTest'`。

### F03-06 Function 审计闭环（修 O-13/O-14）— P0

| 缺陷 | 目标态 | 迁移 |
|:--|:--|:--|
| `function_name` 恒空 + `caller_id` 存函数名 | 修正调用点参数顺序；函数名由 expression 归属反查（`FunctionRegistry`）；`caller_id` 只取 `UserContext` | `V171.1__function_audit_caller_backfill.sql`：按 `expression` 反查函数名回填 `function_name`，把误存的函数名从 `caller_id` 移到 `function_name`，`caller_id` 置 `unknown-legacy`（不伪造主体） |
| `callerId` 缺省 `"anonymous"` 放行 | 无 `operator` 或 `traceId` → **400 `ECOS-ONTO-060` 拒收**（PRD §4.1-2） | 保留 `FunctionController.java:76` 之前的入口，新增 `AuditContextGuard` |
| 吞异常 | Kafka `ecos.audit`（eventType=`FUNCTION_EXEC`，detail=`{functionId,expression(≤512),objectType,params,resultSummary,durationMs,cacheHit,traceId}`）；Kafka 不可用 → 本地表兜底且**允许执行**（与写审计同口径）；**两者都失败 → 拒绝执行**（无审计即无授权） | 依赖分册 01 C.5 审计底座 |
| `SELECT *` + 拼接 + `BIGSERIAL` + 无 DR 列 | 显式列清单 + 参数化 + 新表 `id VARCHAR(36)`（MC01）+ `create_time/update_time/create_by/is_deleted/version_no/domain` | `V172__function_audit_rebuild.sql`（新表 + 视图别名读旧列，停写不删，见 R-5） |

**沙箱边界不变**（PRD §4.1-3 明列）：受限 SQL 聚合子集、单表、5s 超时、Caffeine 300s、本体变更主动失效 —— 本册只加"审计上下文守卫"，**不动执行器**。

**同族第五缺陷（本册一并收口，O-25）**：本体侧另一条审计通道 `ontology/security/SecurityEngineClient.java:84-91,285-292` 用 `Object kafkaTemplateBean` + `getClass().getMethod("send", …)` **反射绕过 runtime-event 底座**，bean 缺失时仅 `log.warn` 后继续返回（审计静默丢失）。目标态：本体/服务侧任何跨进程事件**只能**经 `runtime-event` 的 `EventBusService`（铁律 §2.5-4、后端规范 §九），且 `EventBusService` 未装配 → 本地兜底必写、兜底也失败 → 拒绝操作（与本项 ②的"双失败拒收"同口径）。配套把 ArchUnit 的盲区补上：现有 `businessMustNotDependOnSpringKafkaDirectly`（`ModuleDependencyArchTest.java:419`）只看类型引用，注释还把这处反射写作"天然合规无需豁免"——新增断言禁业务包内对 `send(` 的反射调用。

**验收方式**：`mvn -pl engine/ontology-engine/ontology-engine-impl -am test -Dtest='FunctionAudit*Test,OntologyAuditViaEventBusOnlyTest,ReflectiveKafkaBypassArchTest'` + `ecos-tests/tests/ontology-function-audit.spec.ts`。

### F03-07 接口面收敛（本体域 URL 单源，修 O-1/O-2/O-3/O-4）

目标态 **1 个规范前缀 + 2 个职责子族**，其余转别名（保留 ≥2 迭代，带 `deprecated=true` 响应头）：

```
/api/v1/ontology/**           领域语义面（objects/links/properties/domains/proposals/calibers/metrics/functions/versions）
  ├─ /api/v1/ontology/git/**   Git 归档（runtime 通道）
  └─ /api/v1/ontology/copilot  AI 辅助（依赖 runtime common-api）
别名（deprecated）: /api/v1/ecos/**、/api/ontology/**、/api/ecos/ontology/**、/api/v1/engine/ontology/**、/api/v1/lineage/**
```

**三条配套**：
1. `OntologyController` 与 `OntologyVersionController` **拆基路径**（后者并入 `/api/v1/ontology/versions`，O-2）；
2. 平铺 `/objects|/links` 为唯一写入口，嵌套 `/ecos/ontologies/{id}/entities` 转只读别名（O-3）；
3. 全部本体路径登记 `route-manifest.json` 的 `domain: ontology`，CI 比对 `RouteManifestParityTest`（分册 02 C32 同族，扩展断言集合）。

**API 只增不改约束**：既有签名不改（AGENTS.md），收敛通过"新路径为主 + 老路径别名 + 弃用头"完成。

**验收**：`mvn -pl gateway -am test -Dtest='OntologyRouteManifestParityTest,OntologyNoDuplicateBaseMappingTest'`（后者扫描 `@RequestMapping` 值集合，断言无两个 Controller 共用同一基路径）。

### F03-08 提案链可用化（修 O-7/O-8）

- **落库**：`ecos_ontology_proposals` 迁移到规范形态（`id VARCHAR(36)`、DR06/DR07/DR08、schema 限定名）；补 seed 与 `proposal_type` 字典（≥3 行示例）；
- **SQL 合规**：`:500,511,535` 等全部 `?::bigint` → `CAST(? AS BIGINT)` + `databaseId` 分支（MC03）；`SELECT *` → 显式列（IR04）；裸表名 → `{schema}.ecos_ontology_proposals`（§四 附则1）；
- **状态机固化**：`PENDING →(submit)→ REVIEWING →(approve)→ APPROVED →(verify)→ VERIFIED →(execute)→ EXECUTED`，`reject → REJECTED`；`execute` 只接受 `VERIFIED/APPROVED`（**移除当前也允许的 `PENDING` 直执**，那正是绕过审批的口子），违反 → 409 `ECOS-ONTO-012`；
- 门禁（F03-03）插在 `execute` 之前，与状态校验并列。

**验收**：`mvn … -Dtest='ProposalLifecycleTest,ProposalPendingDirectExecuteRejectedTest,ProposalSqlPortabilityTest'`。

### F03-09 指标语义二分与孤表处置（修 O-12）

**定性**（不擅自迁，待 R-4/R-5 裁决）：
- `metric_definition` = **指标定义**（语义资产，控制域，归 ontology）→ 改造为 F03-01/E.3 形态，成为唯一可写入口；
- `public.ecos_biz_metric`（75 行，dept/month/value 形态，外键 `ecos_wm_goal`）= **指标实例/结果**（业务域数值），**不是**定义；它当前既无代码引用、又与 PRD-02 的 `ecos_dw` 五层没有登记关系 → 归 02 册 E 章载体表或 07/09 册场景表处置，本册只声明"本体域不读写该表"，并加断言（`OntologyNoForeignTableAccessArchTest`）；
- 两张表的**孤表事实**写入 §七.2 PRD 回写第 2 条（PRD-03 §1.3 "指标定义集"不能声称已有承载）。

**验收**：`mvn … -Dtest='MetricDefinitionWritePathTest,OntologyNoForeignTableAccessArchTest'`。

### F03-10 Function 与本体缓存一致性（保留 + 加固）

本体变更（对象/属性/关系发布）→ 主动失效 `FunctionCacheManager`（既有）+ 发 `ecos.ontology` Kafka 事件（`identity/catalog/ontology/object/workflow/agent/knowledge/audit` 8 topic 之一，AGENTS.md 基线）；缓存失效与事件发布**同事务后钩子**（`TransactionSynchronization#afterCommit`），避免"版本已发布但缓存仍旧"。

**验收**：`mvn … -Dtest='FunctionCacheInvalidateOnPublishTest,OntologyEventAfterCommitTest'`。

### F03-11 术语表（glossary）与本体关系归一

实测 `public.ecos_glossary_term` 32 行、`ecos_business_glossary` 10 行、`ecos_glossary_term_relation` 存在；`GlossaryController/GlossaryGraphController/GlossaryRelationController` 三控制器挂 `/api/v1/ontology/glossary`。
- 目标态：术语是**本体对象的别名层**，不另立语义源；`glossary_term.object_code` 必须能解析到已发布对象（悬空 → `ECOS-ONTO-070` 保存拒绝）；
- 双表合并（`ecos_business_glossary` vs `ecos_glossary_term`）挂 **R-4** 裁决，本册只加"新表可写、旧表只读别名"。

**验收**：`mvn … -Dtest='GlossaryDanglingTermRejectTest'`。

### F03-12 workflow 单模型收敛（修 O-16，挂 R-7）

- 目标态**一套** workflow 定义模型：`ecos_workflow`（`id VARCHAR(36)`, `nodes TEXT`, `edges TEXT`, DR 列齐备）+ 节点/边**投影表** `ecos_workflow_node`/`ecos_workflow_edge`（由保存时拆解写入，供血缘与校验检索；JSON 不参与 WHERE/JOIN —— MC02 附则 2）；
- `ecos_workflow_v2`（11 行）**停写 + 只读别名**，迁移脚本 `V173__workflow_model_converge.sql` 把 v2 的 nodes/edges 迁入投影表（幂等 `ON CONFLICT DO NOTHING`）；
- 物理删除改软删（修 O-18）：`WorkflowRepository.java:88` 的 `DELETE` → `UPDATE … SET is_deleted=1`（DR05）；
- `?::jsonb` 改 `CAST(? AS TEXT)` 存 JSON 文本（MC02/MC03）。

**验收**：`mvn … -Dtest='WorkflowSingleModelArchTest,WorkflowSoftDeleteTest,WorkflowRowParityAfterV173Test'`。

### F03-13 审批链可用性修复（修 O-17）— P0

- `WorkflowApprovalRepository` 的 `ecos_workflow_approval` 加 schema 限定（`{schema}.` 由 MC06 配置注入）+ 显式列清单；
- 若 R-7 判定 workflow 归业务域，则审批表随主表同域；两者不得跨 schema 分裂（这正是当前断链根因）；
- **结构断言**：新增 `SchemaDriftLintTest` 扩展（分册 02 C37 同族）—— 代码中出现的 `{schema}.table` 与 `pg_tables` 实存集合做差集，差集非空即 CI 红；把"跨 schema 缺表"从运行时 500 提前到构建期。

**验收**：`mvn … -Dtest='WorkflowApprovalTableReachableTest,SchemaDriftLintTest'`。

### F03-14 跨域访问收口（修 O-19/O-20）— P0

| 越界 | 目标态 |
|:--|:--|
| sysman `PortalSearchQueryService` 直查 `ecos_workflow_v2` | 改为 buszhi 工作流 REST（`GET /api/v1/ontology/workflows?keyword=`）或统一搜索索引；ArchUnit + 源码 regex 禁 sysman 出现 buszhi 域表名 |
| buszhi 启动类 `@ComponentScan` 扫 `engine.data.service`、`@MapperScan` 扫 `engine.data.**` | `CopilotServiceImpl` 上移 `runtime/common-api` 契约 + 独立 `runtime-copilot` bean 装配；或 ontology 侧实现自有 `ICopilotService`（禁"为取一个 bean 而扫别人整包"） |
| ArchUnit 只查 import 图 | 新增 `ComponentScanBoundaryTest`：断言各 service 启动类的 `basePackages` 只含自有引擎 + `runtime` + `common`，不含 `engine.<other>` |

**验收**：`mvn -pl services/sysman/… -Dtest='SysmanNoBuszhiTableArchTest'` + `mvn -pl services/buszhi/… -Dtest='BuszhiComponentScanBoundaryTest'` + 新 `mvn -pl gateway -Dtest='ServiceScanBoundaryGateTest'`。

### F03-15 版本 × Git 归档接线（修 O-21，挂 R-5）

- 依后端规范 §十一：**DB 只存在用版本，历史版本走 Git**。`ecos_ontology_version.snapshot` 从 `jsonb` 改 `TEXT`（MC02），并在发布成功后语义降级为"在用快照指针 + Git ref"（`git_ref VARCHAR(64)` 新列）；
- Git 归档从 best-effort 升级为**可补偿**：归档未 `COMMITTED` → 写 `git_archive_pending` 队列，由 **runtime-task** 调度重试（铁律 §2.5-4：禁引擎自建 `@Scheduled`）；连续失败 → 告警（分册 00 C20 底座），**但不回滚发布**（发布与归档分离，避免语义倒挂）；
- 同一机制适用于 `ecos_object_version`（`public` 20 行 / `ecos_ontology` 0 行）。

**验收**：`mvn … -Dtest='OntologyVersionGitArchiveCompensationTest,HistoryNotInDbTest'`。

### F03-16 本体域前端 UI 归一（修 O-23）

- 4 个 UI 簇 → 1 个入口 `pages/ontology/` + `components/ontology/`（其余 `pages/OntologyDesigner`、`pages/ObjectExplorer`、`pages/object-explorer`、`pages/OntologyWorkbenchLayout` 转路由级重定向）；
- 3 个 API 服务文件合并为 `services/ontologyClient.ts`（单源，内部按资源分文件导出：`caliber.ts`/`metric.ts`/`proposal.ts`/`mapping.ts`），URL 只从 `route-manifest` 消费（08 册口径）；
- 删除零引用 mock `data/ontologyMockData.ts`；`data/enterpriseOntologyDemo.ts` 若保留必须显式标注"演示数据，非平台事实源"，且不得作为验收证据。

**验收**：`cd ecos_frontend && npm run lint`（tsc）+ `ecos-tests/tests/ontology-workbench.spec.ts`（入口唯一 + 无 mock 数据渲染断言 + V4 三项）。

---

## 三、B 章 界面设计

> 依 `需求检视报告` §六 G5 新增条款：**8 要素文字/表格骨架**（R5 文档不入库图片）。本节引用既有页面文件名为改造锚点。

### B.1 口径与指标治理页（新增，`pages/ontology/caliber/`）

| 要素 | 规格 |
|:--|:--|
| 1 页面意图 | 让业务负责人看到"每个指标绑哪个口径版本、公式是什么、能否复现历史预测" |
| 2 入口与前置 | 本体工作台 → 侧栏「口径与指标」；需 `ontology:caliber:read`；写操作需 `ontology:caliber:publish` |
| 3 布局骨架 | 左：口径列表（`CaliberList`）→ 中：版本时间线（`CaliberVersionTimeline`）→ 右：公式与绑定指标（`FormulaView` + `BoundMetricTable`） |
| 4 数据字段 | 口径：`code/name/ownerRole/status/currentVersion/unit/currency`；版本：`versionNo/formula/additive/period/status/publishedAt/gitRef`；绑定指标：`metricCode/metricName/formulaVersion/snapshotFrozenAt` |
| 5 交互与状态 | 空态（无口径 → 引导"新建口径"）；加载（骨架行 8）；错误（`ECOS-ONTO-040/042` 直显后端 message）；不可变（`APPROVED` 版本公式只读，编辑入口禁用并说明"升版而非改历史"） |
| 6 权限与脱敏 | 金额类指标数值不在本页展示（本页只管定义）；`createBy/updateBy` 显示但 `caller_id` 类主体标识仅管理员可见 |
| 7 验收断言（spec） | `ecos-tests/tests/ontology-caliber.spec.ts`：列表非空 → 选中版本 → 公式可读 → 未绑定指标的发布按钮触发拦截文案 |
| 8 复用与不做 | 复用 `MobileDataTable`、`useTheme()` token、`lucide-react` 图标；**不**做公式在线试算（属 cognitive，05/09 册） |

### B.2 本体工作台主页（改造 `pages/ontology/` + `DomainCanvas.tsx` 688 行）

| 要素 | 规格（要点） |
|:--|:--|
| 意图 | 领域包的对象-关系图与发布状态 |
| 布局 | 顶：领域选择器 + 版本徽章；中：画布（`DomainCanvas`，关系基数以边标签 `1:n` 显示）；右：`ObjectTypeDetail`(514)/`LinkTypeDetail`(361) |
| 字段 | 对象：`code/name/属性数/映射数据集/发布态`；关系：`name/source/target/cardinality` |
| 交互 | 拖拽连线要求选择基数；**保存前**跑 V1~V3 门禁并把失败项在画布上高亮定位（新增 `GateErrorOverlay`） |
| 状态 | 乐观锁冲突 → 提示"他人已发布 v{n}，是否拉取新版"（复用既有 `c4_optimisticLockVersionMismatch` 语义） |
| 拆分要求 | `DomainCanvas.tsx` 688 行合规，但门禁高亮逻辑须独立文件（`components/ontology/GateErrorOverlay.tsx`），避免增长越 800 |
| 验收 | `ontology-workbench.spec.ts`：画布渲染 7 类对象 + 6 条边基数标签 + 一次门禁失败高亮 |
| 约束 | 主题 token / i18n key / lucide 图标；HashRouter 路由 `#/ontology/workbench` |

### B.3 提案面板（改造 `ProposalPanel.tsx` 670 行）

| 要素 | 规格 |
|:--|:--|
| 意图 | 提案全生命周期可视化：创建 → 审 → 验 → 执行 → 版本 |
| 布局 | 列表（状态 Tab：全部/PENDING/REVIEWING/VERIFIED/EXECUTED/REJECTED）+ 详情抽屉（payload diff + 门禁结果 + 执行日志） |
| 字段 | `code/proposalType/title/status/reviewer/reviewedAt/versionId/errorSummary` |
| 交互 | **移除 PENDING→执行直路按钮**（F03-08）；执行前必显示"门禁三检结果"，任一红则按钮禁用并展示原因 |
| 状态 | 空态引导"从对象/指标创建提案"；依赖不可用（503 `ECOS-ONTO-050`）显示"语义校验服务暂不可用，未执行"而非"失败" |
| 验收 | `ontology-proposal.spec.ts`：无 VERIFIED 时执行不可点；VERIFIED 后门禁拒绝时展示 `ECOS-ONTO-040`；通过后状态变 EXECUTED 且版本 id 可跳 |
| 不做 | 不做站内 IM 通知（走 runtime 告警）；不复制审批流（workflow 归 F03-12） |

### B.4 版本时间线（改造 `VersionTimeline.tsx` 584 行）

- 只展示**在用版本 + Git ref**（F03-15）；历史内容不在 DB，点开即经 `services/gitService.ts` 单通道取（前端规范 §十一，只传 `repositoryId`）；
- 三态徽章：`已发布 / 已退役 / 待归档`（`git_archive_pending` 非空 → 待归档 + 重试入口）。
- 验收：`ontology-version-history.spec.ts`（时间线渲染 + Git 详情加载 + 待归档态可见）。

### B.5 Function 审计页（新增 `pages/ontology/function-audit/`）

| 要素 | 规格 |
|:--|:--|
| 意图 | 回答"哪个函数被谁在何时执行、结果与耗时"（修 O-13 的 UI 侧闭环） |
| 字段 | `functionName / callerId / status(SUCCESS/FORBIDDEN/ERROR) / expression(截断 512，可展开) / durationMs / cacheHit / traceId / createdAt` |
| 交互 | 筛选：状态 / 主体 / 时间范围（参数化，禁前端拼 SQL）；traceId 可复制用于分册 00 链路追踪 |
| 空态 | 明确提示"审计上下文缺失的执行已被拒绝，不会写入本表"（把 F03-06 的拒收语义呈现给用户） |
| 权限 | 非管理员只见自身 `caller_id`；主体列表仅管理员（避免人员目录泄露） |
| 验收 | `ontology-function-audit.spec.ts`：列 `functionName` 与 `callerId` 非空且不同值（防 O-13 回归）；一条无上下文请求返回 400 |
| 不做 | 不展示完整 SQL 执行计划；不做函数在线编辑（属 `functions` CRUD） |

### B.6 术语表页（改造 `pages/glossary/`，4 文件 / 1086 行，最大 `TermFormPanel.tsx` 397）

- 表单新增"绑定对象"必选校验（F03-11：悬空即拒），错误文案带 `ECOS-ONTO-070`；
- `GlossaryGraphPanel.tsx`(281) 保留，图中术语-对象边为**只读**。
- 验收：`glossary-binding.spec.ts`。

### B.7 流程设计器（改造 `pages/WorkflowDesigner/`，4 文件 / 352 行 + `components/`）

| 要素 | 规格 |
|:--|:--|
| 意图 | 编辑单一 workflow 模型（F03-12），不再暴露 v1/v2 双概念 |
| 布局 | 画布（节点/边）+ 属性面板（节点类型、审批人、条件表达式）+ 校验抽屉 |
| 字段 | `name/description/mode/status/nodes[]/edges[]/publishedAt` |
| 交互 | 保存 = 拆解写投影表（node/edge）；删除 = 软删（UI 文案"停用"，非"删除"） |
| 状态 | 审批链断（O-17 类）→ 显示 `ECOS-WF-020 审批表不可达` 而非空列表 |
| 验收 | `workflow-designer.spec.ts`：新建 → 发布 → 软删 → 列表状态更新；审批 Tab 可加载（回归 O-17） |
| 约束 | 图标 lucide；`useMediaQuery`/`MobileDataTable` 复用；不新增画布库（沿用既有 `@xyflow/react`，见 bdf4c0d） |

### B.8 UI 侧统一约束（引用规范，不复制）

主题 token（禁硬编码 Tailwind 颜色）、`useLanguage()` i18n（禁中文字面量）、lucide-react 图标、组件 ≤800 行、每 Tab 独立文件、Git UI 走 `services/gitService.ts` 单一通道且只传 `repositoryId`、认证头走 `services/auth.ts`（分册 01 F01-10 已实测收敛，本册只做防回归）。

---

## 四、C 章 技术设计（控制流 + 数据流）

### C.1 控制流：提案 → 门禁 → 执行 → 版本 → 归档 → 事件

```
Client(BFF gateway-first)
  └─ POST /api/v1/ontology/proposals                ① 落库 PENDING（id 应用侧 UUID）
       └─ OntologyProposalController#create
            ├─ payload schema 校验（禁未知键）
            └─ {schema}.ecos_ontology_proposals INSERT（DR 列齐备）

  └─ POST /proposals/{id}/submit   → REVIEWING
  └─ POST /proposals/{id}/approve  → APPROVED（记录 reviewer）
  └─ POST /proposals/{id}/verify   → VERIFIED（结构一致性，不含语义门禁）

  └─ POST /proposals/{id}/execute  ② 唯一语义关口
       ├─ 状态校验：仅 {VERIFIED, APPROVED}（PENDING 直执 → 409 ECOS-ONTO-012）
       ├─ PublishGateService.validate(proposal)
       │    ├─ V1 口径：MetricCaliberGuard        → ECOS-ONTO-040
       │    ├─ V2 单位：MetricUnitDerivationGuard → ECOS-ONTO-021/022
       │    └─ V3 血缘：DatanetColumnGuard ── REST ─→ data-engine（分册 02）
       │                                   失败(不可用) → 503 ECOS-ONTO-050（fail-closed）
       ├─ executeAndPublish(...)：建版本 → 应用变更 → 发布 → 回填 EXECUTED + version_id
       ├─ afterCommit: FunctionCacheManager.invalidateAll() + 发 ecos.ontology 事件（Kafka）
       └─ 归档：OntologyGitService.archiveVersion(repositoryId=ontology)
              非 COMMITTED → git_archive_pending 入队 → runtime-task 重试 → 失败告警

  └─ GET /api/v1/ontology/versions/{id}      在用版本
  └─ GET /api/v1/ontology/git/history        历史（经 runtime Git，前端只传 repositoryId）
```

**关键点**：门禁只在 `execute` 一处，`approve-and-publish` 复用同一 `PublishGateService`（避免"两条发布路径两套校验"，这是分册 02 孪生教训的直接吸取）。

### C.2 控制流：Function 执行与审计（F03-06）

```
POST /api/v1/ontology/functions/test
  ├─ AuditContextGuard.require()      无 operator/traceId → 400 ECOS-ONTO-060（不进入执行）
  ├─ ExpressionValidator.quickScan    违禁 → writeAudit(...,FORBIDDEN) + 400
  ├─ ExpressionValidator.validate     白名单不过 → writeAudit(...,FORBIDDEN) + 400
  ├─ CacheManager.get                 命中 → writeAudit(...,cacheHit=true) → 返回
  ├─ Engine.test(expression, entityName, operator)   5s 超时 / 单表 / 聚合子集
  └─ AuditService.record(FUNCTION_EXEC)
       ├─ Kafka ecos.audit  ── 失败 ─→ 本地 {schema}.ecos_function_audit_log
       └─ 两者皆失败 → 500 ECOS-ONTO-061，且**不回滚结果**但记 ERROR（可观测）
```

### C.3 数据流：口径 → 指标 → 快照 → 预测（跨册主链）

```
caliber_version(APPROVED, formula TEXT, version_no)          [控制域 ecos_ontology]
        │ 绑定（提案显式）
        ▼
metric_definition(caliber_id, formula_version, caliber_snapshot TEXT)
        │ 发布冻结（一次性，之后只读）
        ▼
ecos_ontology_version(snapshot 指针 + git_ref)  ──归档──▶ Git repositoryId=ontology
        │ ontology 事件 (Kafka ecos.ontology)
        ├──────────▶ kb：kb_ontology_snapshot（PRD-04 抽取消费，entity_codes）
        └──────────▶ cognitive：REQ-FC-02/03 读 caliber_snapshot.formula 做确定性计算
                                 └─ 结果经 data-engine 写通道落业务域 APPLICATION/ecos_dw（ADR-14，分册 02 F02-05）
```

**读写边界**（铁律 §0.6 + ADR-14）：本体域**只产定义与版本，不产数值**；任何金额数值出现在本体表即视为越界（`OntologyNoFactValueArchTest` 断言本体 DDL 不含 `NUMERIC(18,2)` 度量列，除口径元数据的单位/精度描述列外）。

### C.4 数据流：workflow 单模型拆解与投影

```
PUT /api/v1/ontology/workflows/{id}
  ├─ 校验：节点类型 ∈ 字典；边引用存在；审批人可解析（服务端解析，非客户端 X-Org-Id，分册 01 W40）
  ├─ {schema}.ecos_workflow  UPSERT（nodes/edges TEXT 存原文）
  ├─ DELETE 投影（软删 is_deleted=1）+ INSERT ecos_workflow_node / _edge（供检索/血缘）
  └─ 事件 ecos.workflow（8 topic 基线之一）
审批：POST /workflows/instances/{id}/approvals → {schema}.ecos_workflow_approval（同 schema，修 O-17）
```

### C.5 两态寻址与承流（ADR-15 落到本册）

| 调用 | monolith 态（今日） | service 态（S3/S4） |
|:--|:--|:--|
| ontology → data-engine（V3 门禁） | 经 `ServiceEndpointResolver` 解析 `ecos.route.data-engine` → 默认 gateway :8080 | 解析 → datanet :18082，超时 3s，失败 503 fail-closed |
| kb → ontology（`entity-mappings`） | 同上，**禁硬编码 `http://localhost:8080`**（修 O-6） | buszhi :18083 |
| sysman → buszhi（门户搜索） | 经 REST，禁表级直查（修 O-19） | 同 |
| 前端 → 本体 | BFF 全转 `GATEWAY_URL`（PMO-B：`ECOS_LOCAL_DIRECT_SERVICE_PROXY=false`） | 不变（前端永不直连 service 端口） |

**切流前置（本册对 ADR-15 S2/S3 的补强）**：本体域切 service 态前必须完成 ①`BuszhiComponentScanBoundaryTest` 绿（不再扫 data 包）②`SchemaDriftLintTest` 绿（跨 schema 缺表清零）③提案链有 ≥1 条真实 EXECUTED 记录（否则承流切换无法回归验证发布链）。

### C.6 降级矩阵

| 依赖不可用 | 行为 | 依据 |
|:--|:--|:--|
| security-engine | **DENY**（默认拒绝，含只读） | 铁律 §2.4-8 |
| data-engine（V3 列校验） | 503 `ECOS-ONTO-050`，提案不执行 | 门禁 fail-closed |
| Kafka `ecos.ontology` | 发布不回滚；事件落本地 outbox + runtime-task 补偿 | `V48__ecos_outbox_and_saga.sql` 既有底座 |
| Kafka `ecos.audit`（Function 审计） | 本地表兜底，允许执行；两者皆失败 → 500 `ECOS-ONTO-061` | PRD-03 §4.1-2 |
| Git 归档通道 | 发布成功，版本标"待归档"，入重试队列 + 告警 | F03-15 |
| Neo4j（enterprise 图谱形态） | 降级 PG 关系表（ADR-11 双形态） | 分册 02 C.8 同口径 |

### C.7 可观测

- 每个门禁判定输出一条 INFO：`proposalId gate=V1|V2|V3 result=PASS|FAIL code=ECOS-ONTO-0xx traceId={}`；
- Function 审计耗时、缓存命中率、提案执行成功率 → runtime-monitor 指标（禁引擎自建定时器，铁律 §2.5-4）；
- traceId 贯通依赖分册 00 C21（logback MDC + `ApiResponse.traceId`）。

### C.8 三层与数据访问纪律（本册改造基线）

| 层 | 允许 | 禁止 |
|:--|:--|:--|
| Controller | 入参校验 + 调 Service | `SELECT * FROM … WHERE id=?::bigint` 这类 SQL 字面量（**现状 O-8 违例**） |
| Service | 业务规则 / 事务 / 校验 | `JdbcTemplate` 直接连（规范 §五；现状 buszhi 6 个 Repository + ontology 25 文件持有，分册 02 C43 同族） |
| Mapper/XML | CRUD，schema 限定名 | 裸表名、JSONB、`gen_random_uuid()`、PG 私有转型 |

本册**不新造**第 4 个持久化通道；buszhi 的 `*Repository`（JdbcTemplate 型）纳入与分册 02 W54 同一批次改 Mapper（M2）。

---

## 五、D 章 接口设计（OpenAPI 片段，operationId 唯一）

### D.1 端点族（目标态；`*` = 新增，`†` = 别名保留 ≥2 迭代）

| 方法与路径 | operationId | 说明 |
|:--|:--|:--|
| GET `/api/v1/ontology/objects` | `listOntologyObjects` | 平铺唯一入口（F03-03/07） |
| POST `/api/v1/ontology/objects` | `createOntologyObject` | 写入口 |
| GET `/api/v1/ontology/objects/{code}` | `getOntologyObject` | |
| GET `/api/v1/ontology/links` / POST | `listOntologyLinks` / `createOntologyLink` | 关系带 `cardinality` |
| GET `/api/v1/ontology/domains` | `listOntologyDomains` | † `/api/v1/ecos/domains` |
| **GET `/api/v1/ontology/calibers`** * | `listCalibers` | F03-01 |
| **GET `/api/v1/ontology/calibers/{code}/current`** * | `getCurrentCaliber` | 跨引擎只读（主权在本体） |
| **POST `/api/v1/ontology/calibers`** * | `createCaliber` | DRAFT |
| **POST `/api/v1/ontology/calibers/{code}/versions`** * | `publishCaliberVersion` | REVIEWING→APPROVED 走提案 |
| **GET `/api/v1/ontology/metrics`** * | `listMetricDefinitions` | 带 `caliberId/formulaVersion` |
| **POST `/api/v1/ontology/metrics/{code}/binding`** * | `bindMetricCaliber` | 显式绑版（§2.2-2） |
| GET `/api/v1/ontology/proposals` / `/{id}` | `listProposals` / `getProposal` | |
| POST `/api/v1/ontology/proposals` | `createProposal` | |
| POST `/api/v1/ontology/proposals/{id}/submit\|approve\|reject\|verify\|execute` | `submitProposal` … `executeProposal` | execute 唯一门禁口 |
| POST `/api/v1/ontology/proposals/{id}/approve-and-publish` | `approveAndPublishProposal` | 复用 `PublishGateService` |
| GET `/api/v1/ontology/entity-mappings?ontologyId=` | `listEntityMappings` | **冻结契约**（F03-05，基线 JSON Schema） |
| GET `/api/v1/ontology/mappings` / PUT | `listOntologyMappings` / `saveOntologyMappings` | † `/api/v1/ecos/mappings` |
| POST `/api/v1/ontology/functions/test` | `testOntologyFunction` | 需审计上下文（F03-06） |
| **GET `/api/v1/ontology/functions/audit`** * | `listFunctionAudit` | 参数化筛选 |
| GET `/api/v1/ontology/versions` | `listOntologyVersions` | † `/api/v1/ecos/versions`、`/api/v1/ecos/ontologies/{id}/versions` |
| GET `/api/v1/ontology/git/history` | `getOntologyGitHistory` | 只传 `repositoryId`+`assetType`+`versionNo` |
| GET/PUT `/api/v1/ontology/workflows` … | `listWorkflows` / `saveWorkflow` | 单模型（F03-12） |
| POST `/api/v1/ontology/workflow-instances/{id}/approvals` | `approveWorkflowTask` | 同 schema（修 O-17） |
| GET `/api/v1/ontology/glossary/terms` | `listGlossaryTerms` | 悬空校验（F03-11） |

### D.2 冻结契约响应（REQ-ONTO-03 §3.1，基线文件 `contracts/ontology-entity-mappings.schema.json`）

```json
{
  "code": 0,
  "data": [{
    "entityCode": "Project",
    "datasetId": "ds-uuid",
    "resourceName": "ecos_biz_stage_fact",
    "materialized": "true",
    "fieldMappings": [{"entityField": "project_id", "physicalColumn": "project_id", "transform": null}]
  }]
}
```
字段集**只增不改**；`materialized` 裁度 null/""/"true"/"1"=true。响应新增 `traceId`（分册 00 增量口径，属"增"）。

### D.3 `PublishGateService` 契约（内部 API，跨引擎可复用）

```yaml
GateViolation:
  required: [gate, code, message]
  gate:  { enum: [V1_CALIBER, V2_UNIT, V3_LINEAGE] }
  code:  { example: "ECOS-ONTO-040" }
  message: { example: "指标 M_FC_PROFIT 未绑定 APPROVED 口径版本" }
  refs: { type: array, items: string, maxItems: 20 }
GateResult:
  passed: boolean
  violations: [GateViolation]
  evaluatedAt: date-time
  dependencyErrors: [ { dependency: datanet, code: "ECOS-ONTO-050" } ]   # 非空即整体 503
```

### D.4 写通道与消费（与分册 02/04/05 的接缝）

| 方向 | 通道 | 约束 |
|:--|:--|:--|
| ontology → data-engine | `GET /api/v1/datanet/metadata/columns?tableRef=` （V3） | 两态寻址；不可用 = 503 fail-closed |
| ontology → kb | Kafka `ecos.ontology` → `kb_ontology_snapshot` | 事件带 `versionNo + traceId` |
| ontology → cognitive/workspace | `GET /calibers/{code}/current`（只读） | 口径主权：消费方不得复制公式，只可缓存 `caliber_snapshot` 于自身运行记录 |
| ontology → runtime | Git 归档、调度重试、告警 | 禁引擎自建 `@Scheduled`/Git 客户端 |

### D.5 三滤波器与匿名面

新增本体端点**默认不得**：① 进 `VersionPrefixRewriteFilter` 的映射白名单（除非需要 `/api/` 别名）② 进 `SecurityConfig` permitAll ③ 进 `ClearanceInterceptor` 豁免清单。
理由：本体是控制域语义源，`entity-mappings` 的调用方是同 JVM 引擎（kb），走服务身份（ADR-7 信任头 + 分册 00 W02 `HeaderAuthInterceptor`），**不是匿名**。
验收：`mvn -pl gateway -Dtest='OntologyAnonymousSurfaceTest'`（断言三处清单不含本体业务路径）。

### D.6 错误码表（`ECOS-ONTO-1xx` 本册扩展；0xx 段与分册 01/02 不冲突）

| code | HTTP | 语义 |
|:--|:--:|:--|
| `ECOS-ONTO-011` | 409 | 版本幂等冲突（同 `(domain,version_no)`） |
| `ECOS-ONTO-012` | 409 | 提案状态不允许执行（PENDING 直执） |
| `ECOS-ONTO-021` | 400 | 单位推导冲突（V2） |
| `ECOS-ONTO-022` | 400 | 不可加指标被声明为可加聚合 |
| `ECOS-ONTO-030` | 400 | V3 血缘列不存在 |
| `ECOS-ONTO-040` | 400 | 发布态指标缺 `caliber_id` 或口径版本非 APPROVED（V1） |
| `ECOS-ONTO-041` | 409 | 试图修改已冻结的 `caliber_snapshot` |
| `ECOS-ONTO-042` | 500 | 口径服务内部失败（fail-closed） |
| `ECOS-ONTO-050` | 503 | 门禁依赖（data-engine）不可用，未执行 |
| `ECOS-ONTO-060` | 400 | Function 审计上下文缺失（无 operator/traceId） |
| `ECOS-ONTO-061` | 500 | Function 审计双失败（Kafka + 本地表） |
| `ECOS-ONTO-070` | 400 | 术语绑定的对象不存在（悬空） |
| `ECOS-ONTO-080` | 404 | 对象/关系/口径编码不存在（**唯一允许 404 的语义**，禁 5xx 掩蔽，分册 02 C35） |
| `ECOS-WF-020` | 500 | 审批表不可达（schema 漂移，修 O-17） |

---

## 六、E 章 数据设计

### E.1 归属判定（挂裁决）

| 对象 | 域 | 归属 | 依据 / 裁决（已批准项标 R 号） |
|:--|:--|:--|:--|
| 本体元模型（对象/属性/关系/规则/动作） | 控制域 | `ecos_ontology` | ST07；现状数据全在 `public`（O-15）→ 挂 **R-1**（分册 02 已提） |
| 口径 / 口径版本 / 指标定义 | 控制域 | `ecos_ontology` | 语义主权在本体；同挂 **R-1** |
| 提案 / 版本 | 控制域 | `ecos_ontology` | 同上 |
| Function 审计 | 控制域 | 主控制（审计类与 `ecos_audit_log` 同侧） | **R-5**：随 01 册审计链，还是留 `ecos_ontology` |
| workflow 定义/实例/任务/审批 | **业务域还是控制域？** | 二选一 | **R-7**：PRD-08 场景工作台用它编排业务动作 → 倾向业务域 `ecos_dw`？但流程定义是平台能力 → 倾向控制域 `ecos_ontology`。需裁决 |
| 指标数值实例（`public.ecos_biz_metric` 75 行） | 业务域 | 五层载体 or 场景表 | **R-4**（与分册 02 E.4 事实五表同批判定） |

**写作纪律（R-1 a 已批准，执行待授权）**：所有新表 DDL 一律带 `{schema}.` 限定（MC06 配置注入），使 R-1/R-7 落地时无需改 SQL 文本，只改配置值。

### E.2 新表 DDL（模板合规：MC01 应用侧 UUID / MC02 白名单 / DR05~DR08 / 无 `gen_random_uuid()` / 无 JSONB）

```sql
-- 口径主表
CREATE TABLE IF NOT EXISTS {schema}.ecos_caliber (
    id              VARCHAR(36) PRIMARY KEY,          -- 应用侧 UUID
    code            VARCHAR(64) NOT NULL,
    name            VARCHAR(255) NOT NULL,
    unit            VARCHAR(32),
    currency        VARCHAR(8),
    owner_role      VARCHAR(64),
    dimension_json  TEXT,                             -- MC02：JSON 只准 TEXT，且不参与 WHERE
    create_time     TIMESTAMP NOT NULL DEFAULT NOW(),
    update_time     TIMESTAMP NOT NULL DEFAULT NOW(),
    create_by       VARCHAR(100),
    update_by       VARCHAR(100),
    version_no      VARCHAR(20) NOT NULL DEFAULT '1',
    is_deleted      SMALLINT NOT NULL DEFAULT 0,
    domain          VARCHAR(50) NOT NULL DEFAULT 'default'
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_ecos_caliber_code ON {schema}.ecos_caliber(code) WHERE is_deleted = 0;

-- 口径版本（一次升版一行，行不可变）
CREATE TABLE IF NOT EXISTS {schema}.ecos_caliber_version (
    id                  VARCHAR(36) PRIMARY KEY,
    caliber_id          VARCHAR(36) NOT NULL,
    version_no          VARCHAR(20) NOT NULL,
    formula             TEXT,
    additive            SMALLINT,                     -- 1 ADDITIVE / 2 SEMI / 3 NON
    period_granularity  VARCHAR(16),
    status              VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    approved_by         VARCHAR(100),
    approved_at         TIMESTAMP,
    git_ref             VARCHAR(64),
    create_time         TIMESTAMP NOT NULL DEFAULT NOW(),
    update_time         TIMESTAMP NOT NULL DEFAULT NOW(),
    create_by           VARCHAR(100),
    update_by           VARCHAR(100),
    is_deleted          SMALLINT NOT NULL DEFAULT 0,
    domain              VARCHAR(50) NOT NULL DEFAULT 'default'
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_caliber_ver ON {schema}.ecos_caliber_version(caliber_id, version_no);
```

### E.3 改造既有表（`V167~V174`，全部走 DDL 单源目录 `gateway/src/main/resources/db/migration/`）

> **【2026-09-30 收口对照】** `V168.1` 的指标定义合规新表按 **DR02/DR03** 定名 **`ecos_ontology.ecos_metric_definition`**（原字面 `metric_definition_v2` 违两条表名红线：缺 `ecos_` 前缀 + `_v2` 版本后缀；实测旧表 0 行、新表未建、代码零引用 ⇒ 改名零回归面）。旧表 `ecos_ontology.metric_definition` 只 ADD COLUMN + 停写登记，不改名不删列（IR03）。

| 脚本 | 动作 | 修 |
|:--|:--|:--|
| `V167.1__ecos_caliber_tables.sql` | 建 `ecos_caliber` + `ecos_caliber_version`（E.2） | O-10 |
| `V168.1__metric_definition_conform.sql` | `metric_definition` 补 `caliber_id VARCHAR(36)` / `formula_version VARCHAR(20)` / `caliber_snapshot TEXT` + DR06/07/08 列；`id VARCHAR(64)`→ 新表 `ecos_metric_definition VARCHAR(36)` 并迁移（0 行，成本≈0） | O-11 |
| `V169.1__ecos_ontology_audit_columns.sql` | 本体表族 `created_at`→`create_time/update_time` + `create_by/update_by/is_deleted/domain` | O-11/O-15 |
| `V170.1__ecos_ontology_version_snapshot_text.sql` | `snapshot jsonb`→`TEXT` + 新列 `git_ref VARCHAR(64)`；历史语义降级为"在用版本"**【2026-09-30 勘误】**合规列定名 = `snapshot_json`（DR04 `_json` 后缀，原稿 `snapshot_text` 违例）；文件名 slug 保留不改（IR02 按文件名手动执行，避免破坏运维/文档引用） | O-21 |
| `V171.1__function_audit_caller_backfill.sql` | `function_name`←从 `expression` 反查；误存于 `caller_id` 的函数名移回 `function_name`，`caller_id` 置 `unknown-legacy` | **O-13** |
| `V172__function_audit_rebuild.sql` | 新 `ecos_function_audit`（`VARCHAR(36)` PK + DR 列 + `trace_id`）；旧表只读别名视图 | O-14 |
| `V173__workflow_model_converge.sql` | `ecos_workflow_v2`(11) + `ecos_workflow`(8) → 单表 + 投影表；软删列 | O-16/O-18 |
| `V174__proposal_table_conform.sql` | `ecos_ontology_proposals` 规范化（UUID PK、DR 列、状态枚举字典） | O-7/O-8 |

**seed 同步**（数据库访问规范 §四）：`caliber_status`、`metric_additivity`、`proposal_status`、`workflow_node_type` 四个新字典各 ≥3 行示例，`ON CONFLICT DO NOTHING`，与 `V{n}` 同 commit。

**禁令**：本册不 DROP 任何表；`ecos_biz_metric`(75) / `ecos_workflow_v2`(11) 等存量只停写不迁（记忆纪律：存量只定性）。

### E.4 索引与检索规则

- 需要被检索的属性**必须**投影为独立列（§四 附则 2）：`caliber.status`、`metric.code`、`proposal.status/domain_code` 均为列；`dimension_json`/`nodes`/`edges`/`payload` 只做存取，**禁 WHERE/JOIN/索引**；
- workflow 节点/边的检索走投影表 `ecos_workflow_node`/`_edge`（这正是 F03-12 拆投影的理由）；
- 唯一性用 `active_flag SMALLINT` + 部分唯一索引模式（沿用分册 02 E.2 的 `COALESCE` 表达式索引替代方案，MC03 兼容）。

### E.5 与分册 02 的数据接缝（不可各写一套）

| 接缝 | 本册要求 | 对方落点 |
|:--|:--|:--|
| V3 列存在性校验 | 只经 data-engine REST，禁 ontology 直查 `ecos_dw`/`information_schema` 拼 SQL | 分册 02 D 章元数据端点（需补 `metadata/columns`） |
| 事实表引用 | 指标血缘引用 `ecos_biz_stage_fact.stage` 等枚举（PRD-02 表 2/3/4） | 分册 02 E.4 事实五表 DDL 定稿**在前**，本册 V3 才有基线 |
| 写通道 | 本体不写业务域 | 分册 02 F02-05 `WriteChannelRequest` |
| schema 限定 | 全部 `{schema}.` 配置注入 | 分册 02 C38/MC06 |

### E.6 多库兼容检查单（MC01~MC06，本册逐条自检）

| 红线 | 本册 DDL/SQL 落实 | 门禁 |
|:--|:--|:--|
| MC01 | 全部 PK `VARCHAR(36)`，无 `gen_random_uuid()`/`BIGSERIAL` | `OntologyDdlComplianceLintTest`（含 `ecos_function_audit_log.id BIGSERIAL` 存量断言） |
| MC02 | 类型白名单；JSON 一律 `TEXT`；无 `JSONB` | 同上（现状 `ecos_ontology_version.snapshot jsonb`、`ecos_workflow_v2.nodes jsonb` 为违例基线） |
| MC03 | 无 `::`、无 `ON CONFLICT`（seed 除外按规范写法）、无 `RETURNING`；`?::bigint`/`?::jsonb` 全清 | `OntologySqlPortabilityTest`（源码 regex） |
| MC04 | 列存由 `dw.olap.engine` 二选一，本册不涉及 | — |
| MC05 | 向量不入控制 schema（本册无向量） | — |
| MC06 | schema 名配置注入；DB 名不硬编码 | 分册 00 C17 同批 |
| §四 附则1 | 裸表名 = FAIL：本体 + buszhi 侧全部限定 | `QualifiedTableNameArchTest`（分册 02 C38 同族，扩展包根 `engine.ontology` / `buszhi`） |

---

## 七、F 章 符合性与改造项

### 7.1 改造清单（W67~W89 → ARCH_SPEC C51~C71）

| # | 偏差（§一 证据） | 违反 | 改造动作 | 里程碑 | 验收方式 | 回写 ARCH_SPEC |
|:--|:--|:--|:--|:--:|:--|:--|
| W67 | 前端本体 UI 四簇并存 + 3 个 API 服务 + 1 个零引用 mock（O-23） | 前端规范（入口/单源） | F03-16 归一 + 删 `ontologyMockData.ts` | M1 | `npm run lint` + `ontology-workbench.spec.ts` | C67 |
| W68 | 5 个 URL 前缀家族 / 25 个 `@RequestMapping`（O-1） | 铁律 §2.4、route 单源 | F03-07 收敛 + `route-manifest` 登记 | M1 | `OntologyRouteManifestParityTest` | C52 |
| W69 | 嵌套式与平铺式两套本体读写模型（O-3） | 单一事实源 | 平铺为唯一写入口，嵌套转只读别名 | M1 | `OntologySingleWriteSurfaceTest` | C53 |
| W70 | 映射双控制器 + 契约面与 CRUD 面混置（O-4） | REQ-ONTO-03 | 契约端点独立 + 基线 JSON Schema | M1 | `EntityMappingContractFrozenTest` | C54 |
| W71 | REQ-ONTO-03 三用例零实现；`entity-mappings` 无测试（O-5） | PRD-03 §3.3 | F03-05 三用例落地 | **M0** | `EntityMappingContractCasesTest` | C55 |
| W72 | 提案 SQL `?::bigint` + `SELECT *` + 裸表名（O-8） | MC03/IR04/§四附则1 | V174 + Mapper 化（IR01/§五） | **M0** | `ProposalSqlPortabilityTest` | C56 |
| W73 | **caliber 标识符全仓 0 命中 → REQ-ONTO-02 / PRD-09 FC-01 无承载**（O-10） | REQ-ONTO-02、§0.6 | E.2 新建 `ecos_caliber(_version)` | **M0** | `CaliberEntityTest` | C57（P0） |
| W74 | 提案链 0 行，发布流程从未跑通（O-7） | REQ-ONTO-01 §1.4 | F03-08 状态机 + 落库 + seed | **M0** | `ProposalLifecycleTest` | C58 |
| W75 | `execute` 无 V1/V2/V3 语义门禁（O-9） | PRD-03 §1.4-2 | F03-03 `PublishGateService` | **M0** | `PublishGateV1Test/V2Test/V3Test/DependencyDownTest` | C59（P0） |
| W76 | 发布态指标可无口径（无校验） | REQ-ONTO-02 §2.2-1 | F03-01 规则 4 + 冻结只读 | **M0** | `MetricCaliberBindingTest` | C60 |
| W77 | `metric_definition` 缺三字段 + `VARCHAR(64)` + 无 DR 列（O-11） | MC01/DR06~DR08 | V168 | **M0** | `MetricDefinitionConformTest` | C61 |
| W78 | 两张指标表皆零代码引用孤表，且 `ecos_biz_metric` 的 75 行来自 `V28:34` 演示种子（O-12） | 铁律 §0.5（语义资产须有读写路径）、虚假现状 | F03-09 语义二分 + 孤表定性 + 演示种子标注 | M1 | `MetricDefinitionWritePathTest`、`OntologyNoForeignTableAccessArchTest` | C62 |
| W79 | 同一基路径两个 Controller；两域控制器（O-2） | 契约可发现性 | F03-07 拆基路径 | M1 | `OntologyNoDuplicateBaseMappingTest` | C52（同项） |
| W80 | **Function 审计实参错位：`function_name` 全空、`caller_id` 存函数名**（O-13） | REQ-ONTO-04、ST06 | 修正调用点 + V171 回填 | **M0** | `FunctionAuditAttributionTest` | C63（P0） |
| W81 | `callerId` 缺省 `"anonymous"` 放行（O-14①） | PRD-03 §4.1-2 拒收 | `AuditContextGuard` | **M0** | `FunctionAuditRejectWithoutContextTest` | C64 |
| W82 | `writeAudit` 吞异常 + 无 Kafka + `SELECT *` 拼接 + `BIGSERIAL`（O-14②③④） | ST06、IR04/IR05、MC01 | F03-06 + V172 | M1 | `FunctionAuditKafkaFallbackTest`、`FunctionAuditBothFailDenyTest` | C65 |
| W83 | `ecos_ontology_version.snapshot jsonb` + 历史双写 DB/Git + 归档 best-effort 无补偿（O-21） | MC02、§十一 版本×Git | V170 + F03-15 补偿队列（runtime-task） | M1 | `OntologyVersionGitArchiveCompensationTest` | C66 |
| W84 | kb 以 HTTP 自环（默认 `localhost:8080`）访问本体（O-6） | ADR-15 两态寻址、W55 同族 | `ServiceEndpointResolver` + 失败不静默 | **M0** | `CrossEngineFailLoudTest`（分册 02 同名扩展至 ontology 面） | C68（P0） |
| W85 | **`ecos_workflow_approval` 只在 `ecos_ontology`，代码查裸名 → 结构必败**（O-17） | §四附则1、IR05、ST07 | V173 + schema 限定 + `SchemaDriftLintTest` 扩展 | **M0** | `WorkflowApprovalTableReachableTest`、`SchemaDriftLintTest` | C69（P0） |
| W86 | workflow 物理删除 + `?::jsonb` + `SELECT *`（O-18） | DR05、MC02/MC03、IR04 | F03-12 软删 + JSON→TEXT + 投影表 | M1 | `WorkflowSoftDeleteTest`、`WorkflowSingleModelArchTest` | C70 |
| W87 | 本体表 `public` 有数据、`ecos_ontology` 全 0 镜像（O-15） | ST07 / §四附则1 | 挂 **R-1**（分册 02）；本册只加限定名 | M1 | `QualifiedTableNameArchTest` | —（并入 C38） |
| W88 | workflow 双模型 + sysman 直查 buszhi 表 + buszhi 扫 data 包（O-16/O-19/O-20） | IR01、§0.3.1、ArchUnit 盲区 | F03-12/F03-14 + `ComponentScanBoundaryTest` | M1 | `SysmanNoBuszhiTableArchTest`、`BuszhiComponentScanBoundaryTest` | C69/C70（同族，**R-7 归属①+模型 b 已批准**） |
| W89 | **本体侧审计反射绕过事件底座**：`SecurityEngineClient.java:84-91,285-292` 以 `Object` + `getMethod("send")` 反射调 `KafkaTemplate`，bean 缺失时仅 `log.warn` 后继续（审计静默丢）；ArchUnit `businessMustNotDependOnSpringKafkaDirectly` 注释自称"天然合规无需豁免"，对该绕行**结构性看不见**（O-25） | 铁律 §2.5-4、ST06 | 改 `EventBusService` 单通道；bean 缺失 = 本地兜底必写，两者皆失败 → 拒绝操作；ArchUnit 增断言"业务包禁 `java.lang.reflect` 调 `send(`" | **M0** | `OntologyAuditViaEventBusOnlyTest`、`ReflectiveKafkaBypassArchTest` | C71（P0） |

### 7.2 PRD 侧需回写（本册发现，交任务 #26）

1. **PRD-03 §2.1 的 `caliber_snapshot_json JSONB`** → 改 `caliber_snapshot TEXT`（MC02：JSON 只准 TEXT，且不得参与 WHERE/索引）。PRD 原文的"只加列"还必须先补 DR06/DR07/DR08 与 MC01 主键形态（O-11）。
2. **PRD-03 §1.1"在既有 V52 worldmodel 通用元模型之上…复用提案系统与版本快照机制"** → 需加实测限定："提案控制器存在但 `ecos_ontology_proposals` 0 行（从未运行）；指标两张表零代码引用；worldmodel 表在 `public`（`ecos_wm_goal` 等）与 ontology 表不同族，不可称'复用'"。
3. **PRD-03 §1.3 表头写"9 项，逐项登记"，实际列 10 行** → 更正为 10，并逐项标注 `caliber_id + formula_version` 的**来源裁决**（当前口径不存在，见 R-6）。
4. **PRD-03 §1.4 V1 门禁"跨引擎读 workspace 口径服务"** → 与 Q3/铁律 §0.6 冲突：口径是控制域语义资产，主权应在 **ontology**（本册 F03-01），workspace 只消费。需把 PRD-09 FC-01 的"口径服务在场景层"表述改为"口径定义在本体、场景层消费与签核"。**（挂裁决 R-6）**
5. **PRD-03 §4.1-2"审计发送失败 → log 兜底且允许执行"** → 与"无审计上下文拒收"叠加后需补一句：**本地兜底也失败时的判定**（本册定为 500 拒收，`ECOS-ONTO-061`）。
6. **PRD-03 模块标注 `ontology-engine（buszhi:18083）`** → 按 ADR-15 双口径改写（制品 buszhi:18083，当前承流 gateway:8080），与 01/02 册同款。
7. **PRD-03 §3.1 契约示例含 `code`/`data` 无 `traceId`** → 统一为分册 00 D.5.1 的增量 `ApiResponse`（含 `errorCode/traceId`）。
8. **PRD-08 的 workflow 归属**：PRD-08 用 workflow 编排业务动作，但表既在 `public` 又有 `ecos_ontology` 镜像、双模型（O-16/O-17）→ 需与 R-7 裁决同步改写"流程定义=控制域平台能力，流程实例=业务域"的边界。

### 7.3 裁决结果（2026-09-29 §十四.1 批量批准；本册按推荐项写作，存量物理迁移属待授权执行项 §14.4）

| # | 议题 | 选项 | 推荐 |
|:--:|:--|:--|:--|
| **R-4** | `public.ecos_biz_metric`（75 行、零引用、外键指向 `public.ecos_wm_goal`）与 `ecos_ontology.metric_definition`（0 行、零引用）如何归位 | a) 前者定性为业务域指标实例并入分册 02 五层载体；b) 定性为 v1 legacy 只停写；c) 二者合一 | **a + b 并行**：定义表唯一可写（本体），实例表并入业务域载体但**不迁不改名**，两者语义分列 |
| **R-5** | Function 审计日志归属（主控制审计域 vs `ecos_ontology`）+ 历史 5 行是否保留 | ① 归主控制，与 `ecos_audit_log` 同侧（01 册审计链）；② 留本体；旧行 ③ 保留并回填 / ④ 归档不迁 | **① + ③**（审计是横切能力；5 行回填成本极低且是"曾经不可归因"的证据） |
| **R-6** | **口径主权归属**：本体（本册 F03-01）vs workspace（PRD-09 FC-01 现文） | ① 口径定义落 ontology，workspace/cognitive 只读消费并做业务签核；② 口径落 workspace 场景层，本体只引用 | **①**（与铁律 §0.6"场景层只编排不生产"、ADR-8"本体=语义唯一源"一致；②会让场景语义下沉引擎或反向让平台语义随场景漂移） |
| **R-7** | workflow 域（定义/实例/任务/审批）落控制域还是业务域；以及 `ecos_workflow` 与 `ecos_workflow_v2` 哪个为唯一模型 | 归属：① 控制域 `ecos_ontology`；② 业务域 `ecos_dw`。模型：a) `ecos_workflow`（trigger_event 式）；b) `ecos_workflow_v2`（nodes/edges 式，11 行） | 归属 **①**（流程定义是平台能力，审批链含权限语义）；模型 **b**（v2 行数多且已是前端编辑器的实际形态，v1 的 `trigger_event` 可投影为 v2 的一个节点属性） |

---

## 八、追溯矩阵与跨册接缝

### 8.1 REQ → 设计落点 → 验收 → 改造项

| REQ | A 章 | B 章 | C 章 | D 章 | E 章 | 改造项 | 验收标识 |
|:--|:--|:--|:--|:--|:--|:--|:--|
| REQ-ONTO-01 | F03-01~04, F03-08 | B.2/B.3/B.4 | C.1/C.3 | D.1/D.3 | E.2/E.3(V167/168/174) | W73,W74,W75,W76 | `CaliberEntityTest`、`PublishGate*Test`、`BizProfitPackagePublishTest`、`ontology-publish-gate.spec.ts` |
| REQ-ONTO-02 | F03-01/F03-02 | B.1 | C.3 | D.1 | E.2 + V168 | W73,W76,W77,W78 | `MetricUnitDerivationTest`、`MetricCaliberBindingTest`、`ontology-caliber.spec.ts` |
| REQ-ONTO-03 | F03-05 | —（无独立 UI） | C.5 | D.2 | — | W70,W71 | `EntityMappingContractFrozenTest`、`EntityMappingContractCasesTest`、`KbMappingSkipTest` |
| REQ-ONTO-04 | F03-06/F03-10 | B.5 | C.2/C.6 | D.1/D.6 | V171/V172 | W80,W81,W82 | `FunctionAudit*Test`、`ontology-function-audit.spec.ts` |
| （PRD-08 workflow） | F03-12/F03-13 | B.7 | C.4 | D.1 | V173 | W85,W86,W88 | `Workflow*Test`、`workflow-designer.spec.ts` |

### 8.2 依赖与被依赖

| 方向 | 对象 | 内容 |
|:--|:--|:--|
| 依赖 | 分册 00 | ADR-15 两态寻址、`route-manifest`、匿名清单单源、`ApiResponse.traceId`、runtime-task 补偿调度 |
| 依赖 | 分册 01 | 服务身份信任链（ADR-7 G7-1/2）、`ecos.audit` 审计链（Function 审计复用）、CLS 裁决（本体属性展示） |
| 依赖 | 分册 02 | `metadata/columns`（V3 血缘校验）、事实五表 DDL 定稿（血缘基线）、写通道（本体不写业务域）、schema 限定/R-1 裁决 |
| 被依赖 | 分册 04（kb） | `entity-mappings` 冻结契约、`ecos.ontology` 事件 → `kb_ontology_snapshot` |
| 被依赖 | 分册 05（cognitive） | `caliber_snapshot`（公式复现）、指标可加性（聚合合法性） |
| 被依赖 | 分册 07（workspace）/09（场景） | 口径只读消费 + `biz-profit` 领域包 + workflow 审批链 |
| 被依赖 | 分册 08（前端） | 本体路由单源、UI 归一（F03-16）、错误码呈现 |

### 8.3 与分册 02 的接缝收束（同一批改造，勿两次裁决）

| 同族问题 | 分册 02 | 本册 | 统一处置 |
|:--|:--|:--|:--|
| 5 前缀家族 | W43→C32 | W68→C52 | `RouteManifestParityTest` 扩展 domain 集合，一次断言全域 |
| 裸表名 / 引擎 schema 空镜像 | W49→C38 | W87 | 同一裁决 **R-1** |
| 5xx 掩蔽为 404 | W46→C35 | O-17（审批表缺 → 同类掩蔽） | `NoFourOhFourMaskingTest` 全域 + 本册 `ECOS-ONTO-080` 唯一 404 语义 |
| Service 直持 JdbcTemplate | W54→C43 | C.8 + buszhi 6 Repository | 同一批次（M2）改 Mapper，ArchUnit 规则一条覆盖 `engine..service..` + `buszhi..` |
| DDL 违规（JSONB/BIGSERIAL/`gen_random_uuid`） | W59→C48 | W77/W82/W83 | `db-migration-lint` 三项规则 + 各册违规基线数 |

---

**门禁状态**：本册为**待第二门批准**的技术设计交付物（ARCH_SPEC Gate-1 通过后产出）。批准前不分发 fullstack；改造项 W67~W89 的 M0 条目（W71~W76、W80、W81、W84、W85、W89）构成批次 A 的本体域必达子集。

> **【校订三十三】2026-10-04 · F03-01/02/03/05/08/09 离线验收落地 + 骨架首节**：A 章 F03-01/02/03/08/09 与 F03-05 的离线验收（W71/C54、W72/C55、W73/C57、W74/C58、W75/C59、W76/C60 主链条）已按 doc §七.1 命名单测逐项落地，全绿——**ontology-engine-impl 95 tests / 0 fail / 0 err / 0 skip** + **kb-engine-impl `KbMappingSkipTest` 3 例 green**：
>
> * **F03-01/02/03/08/09 UAT 13 类 71 例**（commit `cbc8cd5`）：`CaliberEntityTest` / `CaliberVersionImmutabilityTest` / `MetricCaliberBindingTest` / `MetricUnitDerivationTest` / `MetricAdditivityGuardTest` / `PublishGateV1/V2/V3Test` / `PublishGateDependencyDownTest` / `ProposalLifecycleTest` / `ProposalPendingDirectExecuteRejectedTest` / `ProposalSqlPortabilityTest` / `MetricDefinitionWritePathTest` + `OntologyNoForeignTableAccessArchTest`（F03-09 孤表护栏）。暴露真实 bug 并修 1 处：`CaliberServiceImpl.transition` 缺 APPROVED 迁移 `approvedBy/approvedAt` 落值→已补。
> * **F03-05 实体映射契约冻结（本批 · 新增 3 ontology 测 + 1 kb 测 = 6 例 target + 3 kb 例）**：
>   - 基线文件 `docs/30-设计/contracts/ontology-entity-mappings.schema.json`（**新增**，纳入版本控制；响应字段集即 doc §D.2 + PRD-03 §3.1 冻结语义）。
>   - `EntityMappingContractFrozenTest`（3 例）：① 冻结字段集 `{entityCode,datasetId,resourceName,materialized,fieldMappings}` 反射断言仍由 `OntologyMappingVO` 承载（删/改名即红）② 基线 JSON 顶层键完整（防 hollow 假绿）③ 契约端点基路径 `@RequestMapping("/api/v1/ontology")` + `@GetMapping("/entity-mappings")` 稳定 + 只读无 `INSERT INTO`。
>   - `EntityMappingContractCasesTest`（3 例）：① **硬规则③ 映射不落 kb 表**（kb-engine 源码全包走查无 `INSERT INTO …mapping`，防契约数据回写 kb；kb 内存装配承诺 CI 锁）② E.5 接缝：ontology 契约读侧 `OntologyEntityMappingController` 不越界直查 `information_schema / ecos_dw` ③ 契约基线只增：新增字段允许存在、冻结 7 键（`frozenFields/entityCode/datasetId/resourceName/materialized/fieldMappings/source/target`）必须仍在（防"改名换皮"绕过 frozen ratchet）。
>   - `KbMappingSkipTest`（kb-engine-impl，3 例，纯 Mockito 复用 `KbDryRunContractTest` 骨架）：`materialized` 裁度两侧全穷举——**null / 空串 / "true" / "1" → true**（实例化，不记 `Q2_SKIP`）；**"false" / "0" / 任意串 → skip**（记 `Q2_SKIP` entityCode=EMP，`nodeCreated/nodeUpdated` 双 0）；第 3 例白线锁契约字段集在位（`C4 INVALID_MAPPING` 反向验证）。
> * **命名 delta 已回填**：doc §D.2 契约示例写字段映射元素为 `entityField/physicalColumn/transform`；交付态 `OntologyMappingVO.fieldMappings` 沿用既有 `{source, target}`（**API 只增不改**，禁改名）——本 base contract 按交付态冻结 `{source, target}` 并在 schema `description` 标注映射到 PRD-03 §3.1 语义名。属只增不改约束下的文档欠账，在基线 JSON `fieldMappings.description` 备注。
> * **ArchUnit 回归修 1**：`CaliberService` 接口无 `I` 前缀→`ArchitectureTest.serviceInterfacesShouldStartWithI` 红（8h 前 `cbc8cd5` 已 commit 但因本批 6 例 target 运行时未复检 `ArchUnit` 面）。**改名 `ICaliberService`**（`git mv` 保留历史），11 处 owner 文件同步（`OntologyCaliberController` / `MetricCaliberGuard` / `PublishGateService` / `CaliberServiceImpl` / `MetricDefinitionService` + 5 测试类 `@Mock ICaliberService`）；`CaliberServiceImpl` 走 `implements ICaliberService` 不重命名（`I` 前缀约定仅约束 interface 层，Impl 名与 Bean 名不动，避免破坏 Spring 装配）。
> * **两模块 pod 不涉及 live 库/网络/Neo4j**；F03-05 硬规则③（kb 无 INSERT 到 mapping 表）+ F03-09 孤表护栏（ontology 不查 `ecos_biz_metric`）离线文件 walk 锁。
> * **仍属授权闸（本批不做，挂下派）**：F03-04 `BizProfitPackagePublishTest`/`KbSnapshotEmitTest` 需 V174 提案链跑通 + Git 归档 ref 实跑；F03-06 `FunctionAudit*Test` 需 V171/V172 迁移实跑 + Kafka probe；F03-07 接口收敛 + W68 `OntologyRouteManifestParityTest` 需 `route-manifest.json` 录入 + gateway 单测域；F03-10~15/Gateway 域需跨分册；B 章 B.1~B.7 UI spec 需 live 前端 + Playwright 面白名单授权。

> **【校订三十四】2026-10-04 · F03-06 W81/C64 "审计上下文守卫 400 拒收"落地 + 8h 前 `ICaliberService` 改名声明面欠账收口**：A 章 F03-06 O-14①（`FunctionController.callerId` 缺省回落 `"anonymous"` 继续执行 ⇒ 审计主体不可归因）按 PRD-03 §4.1-2 "审计上下文缺失 → 400 拒收"落地为入口守卫，全绿——**ontology-engine-impl 102 tests / 0 fail / 0 err / 0 skip**（含 F03-06 W81 新增 7 例 + ArchUnit 5 例复检全绿）：
>
> * **新增 `AuditContextGuard`**（`engine/ontology/gate/AuditContextGuard.java`，`@Component`）：`require(String callerId)` 在 `FunctionSandboxEngine`/`FunctionValidator`/`FunctionCacheManager` 之前先卡；任一缺失即 `ResponseStatusException(400, "ECOS-ONTO-060 审计上下文缺失（…）")`：
>   - **operator 缺失**：`callerId` null / blank / `"anonymous"`（equalsIgnoreCase，即 O-14① 的旧哨兵）→ 拒；
>   - **traceId 缺失**：`com.chinacreator.gzcm.common.context.TraceContext.current()`（MDC key=`traceId`）null 或 blank → 拒（entry filter 未跑 ⇒ 链路不可追踪，同无 operator 语义）。
>   - 命名走 `gate` 包（并列 `PublishGateService`/`MetricCaliberGuard`/`DatanetColumnGuard`，非 `service` 包，故 `I` 前缀 ArchUnit `serviceInterfacesShouldStartWithI` 规则不受影响）；错误码字面 `ECOS-ONTO-060` 落消息（沿用 ECOS 惯例，同分册 02 `ECOS-DATA-013`）。
> * **`FunctionController` 接线**（本批唯一主码改动，共 +2 处调用 + 1 ctor 参数）：ctor 追加 `AuditContextGuard` 第 4 参；`POST /test` 在 callerId 派生（`req.getCallerId() != null ? req.getCallerId() : "anonymous"`）后插 `auditContextGuard.require(callerId)`；`GET /{propertyId}/execute` 在 callerId 派生（`"api_execute_" + propertyId`，恒非空）后插 `auditContextGuard.require(callerId)`（此处 400 只在缺 traceId 时触发，白线由 gateway `RequestContextFilter` 写 MDC 保证）。**不修改**执行器/沙箱/缓存/审计写链的既有分支逻辑（F03-06 明列"只补审计上下文守卫这半，不动执行器/沙箱边界"）。
> * **`FunctionAuditRejectWithoutContextTest`（7 例，纯 Mockito + MDC 内设 traceId，零 live DB/network）**：
>   - 守卫 standalone 3 例：`null` / `"anonymous"` / blank（`""`、`"   "`）→ 400 + 消息含 `ECOS-ONTO-060`；MDC 空 traceId + operator 合规 → 仍 400（两条件 OR 语义，且消息点名 `traceId`）；operator + traceId 合规 → `assertDoesNotThrow`。
>   - Controller 集成 4 例：`/test` callerId 缺省（`"anonymous"`）→ `assertThrows` + `verifyNoInteractions(validator/engine/cacheManager)`（守卫先于一切副作用）；`/execute` MDC 无 traceId → `assertThrows` + 三件套零交互；`/test` 完整上下文白线 → `ApiResponse code=0` + `data.value=42`（走 `validator.quickScan=null` / `validator.validate={valid:true}` / `cacheManager.get=null` / `engine.test=FunctionResult(42)` 4 个 stub）。`@AfterEach MDC.clear()` 防跨用脏。
> * **顺带收口 `ICaliberService` 声明面欠账**：`8ae9d16` 的 `git mv CaliberService.java → ICaliberService.java` 只改文件名 + 11 处 owner 引用，**该接口自身的 `public interface` 声明直到本批才在 working tree 落地为 `public interface ICaliberService`**（未 commit 前 working tree 与 HEAD 有 1 行 diff）。本批将其拆出独立纳入本次 commit，避免下批夹带疑点。这行虽被 8ae9d16 依赖文件 altered 前 name 隐藏，但下批任何 `git checkout 8ae9d16^x` 全量重编会编译失败；提前收口。
> * **王炸护栏复盘**（8h 内第 2 次）：`targeted -Dtest=XxxTest` 全绿 ≠ 全量绿；`git mv` 更名文件还要同步改 `public` 声明面。过去 2 批均为 targeted-runtime 未复顾；本批自愈：全流程跑 `mvn -pl engine/ontology-engine/ontology-engine-impl test`（无 `-Dtest`），ArchUnit 5 例 + 102 例全绿前才 commit。
> * **离线性质不变**：本批零 live 库、零网络、零 Kafka、零 Neo4j；`TraceContext` 仅走 MDC 内存；`AuditContextGuard` 单类零 Spring DI 依赖（仅 stdlib + slf4j + `HttpStatus` + `ResponseStatusException`），故 standalone 可 `new` 直测不拉 Spring context。
> * **仍属授权闸（本批不做）**：F03-06 剩余——V171/V172 审计列迁移实跑（IR02 psql）+ Kafka `ecos.ontology` topic audit 落值 probe + W80 `FunctionAuditAttributionTest`（对应 doc §7.3 C63/C64 的 attribution 断言）；F03-04 / F03-07 W68 / F03-10~15 / B 章 UI 均按校订三十三挂下派不重复。

> **【校订三十五】2026-10-04 · F03-06 W80/C63 "函数名归属反查实参错位"落地**：A 章 F03-06 O-13（`FunctionController` 九处 `writeAudit` 调用点第一参 `function_name` 传 `null`（`/test` 五处）或 `propertyId`（`/{propertyId}/execute` 四处）—— DB 列 `function_name` 恒空 + 把"主体 ID" 误存为"函数名"，两条并侵归因信息）按 doc §F03-06 表行 1 "函数名由 expression 归属反查"落地，全绿——**ontology-engine-impl 111 tests / 0 fail / 0 err / 0 skip**（含新增 9 例 `FunctionAuditAttributionTest` + 前批 102 例回归全绿 + ArchUnit 5 例复检）：
>
> * **`FunctionValidator.extractFunctionName(String)`（新增 static）**：复用既有 `FUNCTION_NAME_PATTERN` / `KEYWORD_PATTERN` 两条 private regex，跳过 SQL 关键字后取首个函数调用 token（大写规范化），长度 ≤ 20；无函数调用 token 时返回 `null`（不再误写 OBJECT_ID；白线对齐：`/execute` 端 `col_a FROM emp` 这种纯引用表达式 audit 首参也是 `null`，而非 `"col_a"` 或 `propertyId`）。static 方法零 Spring 依赖，可直接测。
> * **`FunctionController` 9 处 `writeAudit` 调用点全部修订**：`/test` 在 `callerId` 派生后、守卫 require() 前派生 `String funcName = FunctionValidator.extractFunctionName(expression)`；`/{propertyId}/execute` 同样在其 `callerId = "api_execute_" + propertyId` 后紧接派生 `funcName`。9 × `writeAudit(funcName, …)`——不再传 null / propertyId；`caller_id` 参数位保持原语义（`/test` = 请求体 `callerId`，`/execute` = `"api_execute_" + propertyId`），与 W81 `AuditContextGuard` 的 400 拒收语义正交不稀释。
> * **`FunctionAuditAttributionTest`（9 例，纯 Mockito + MDC 内设 traceId）**：
>   - `extractFunctionName` 直接测 3 例：`SUM/AVG/CONCAT/COALESCE`（大小写不敏感）；`SELECT 1 FROM ...` 无函数 → `null` + `WHERE ... COALESCE(name, 'x')` 跳 SELECT 命中下一个真函数；`null/空/"col_a FROM emp"` → `null`。
>   - Controller 白线 4 例（每例 `verify(cacheManager).writeAudit(eq(...), ...)` 强绑全部 8 参）：`/test` 合规 → 首参 `SUM` + `caller_id=pmo-user-1`；`/test` 白名单失败 → 首参 `EVALUATE` + `FORBIDDEN`（`evaluate(` 不在白名单，Matcher 抓 "EVALUATE" token，虽非白名单但 `extractFunctionName` 不做白名单过滤——归属反查只关心"跑了什么 token"，白名单拦截由 `quickScan`+`validate` 独立分支）；`/test` quickScan 命中 `; DROP` → 首参 `SUM` + `FORBIDDEN`；`/execute` 合规 → 首参 `AVG` **（不再为 `prop-42`）** + `caller_id=api_execute_prop-42`；`/execute` exception → 首参 `MIN` + `ERROR`。
>   - **白线锁 O-13 反向证明**：“`/execute` 无函数调用 token → 首参 `null`（而不是 `propertyId`）”——直接证伪 O-13 根因：老代码把 `propertyId` 塞进函数名列一定必被此断言打红。
> * **API 只增不改**：本批未变路径/参数签名；`writeAudit` 签名/ `FunctionCacheManager` 落库 SQL 未动，只更正调用点产物；`AuditContextGuard` 400 拒收序仍第一层（在 `quickScan` 之前）。
> * **授权闸（挂下派）**：V171.1 历史行回填 `function_name` = 从 `expression` 反查（回填脚本属 IR02 psql 实跑面）+ Kafka `ecos.ontology` topic `FUNCTION_EXEC` 落值 probe（依赖分册 01 C.5 审计底座）。M0 批次 A 本体域是否需本节前先实跑回填 psql——挂裁决，不自主执行。
> * **离线性质不变**：本批零 live 库、零网络、零 Kafka；`FunctionValidator.extractFunctionName` 纯 regex / MDC 内 memory。

<!-- 详细设计-03-本体域 / 2026-09-29 / v1.1（2026-09-29 定版） / W67~W89 → C51~C71 / R-4 a+b 并行、R-5 ①+③、R-6 ①、R-7 归属①+模型 b 已批准（报告 §十四.1） / Gate-1 已签字、Gate-2 已通过 / 本轮未实跑库、未改业务代码 -->
