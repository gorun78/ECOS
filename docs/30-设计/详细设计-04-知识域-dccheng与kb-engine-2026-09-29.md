# 详细设计 04 — 知识域（dccheng 服务 + kb-engine 水·K）

> 来源: AI Agent（资深系统设计师视角，用户指令"分册按子系统顺序生成各子系统的设计文档…不符合要求的一定要改"）
> 日期: 2026-09-29 | 版本: v1.1（v1.1 2026-09-29：随**需求检视报告 §十四.1** 批量批准定版——**R-8 ②（画像/假设数值落业务域 + 写通道唯一）／R-9 ①（派生统计量逐列登记 ST03-A）／R-10 ①（三载体收敛于知识层、禁向量入业务域 MC05）／R-11 ②（Neo4j 演示残留打 `demo` 标记不删）／R-12 ①（严格双条件）+②** 五项按本册推荐项批准。Gate-1 已签字、Gate-2 已通过。**批准 ≠ 已实现 ≠ 已验收**；R-11/R-12 的执行动作属待授权（§14.4）） | 责任人: AI Agent
> 上游依据: `docs/20-需求/需求检视报告-2026-09-28.md` §十二（用户批准 2026-09-28，Q1~Q14 + E2E 补记）+ §十三（R 系列登记表）**+ §十四.1（R-1~R-72 已于 2026-09-29 全量批准，本册立项为 R-8~R-12）+ §十四.3（Gate-1 已签字／Gate-2 已通过）**—— 依 Q12，本节即需求基线批准凭证，不再补 APPROVAL_RECORD JSON
> 主责 REQ: **REQ-KB-01（C3 属性完整性校验）· REQ-KB-02（历史画像知识）· REQ-KB-03（假设库版本化）· REQ-KB-04（场景认知契约守护）· REQ-KB-05（dry-run 真口径守护）**（`PRD-04-kb-engine需求规格-2026-09-28.md`，5 项：2 P0 新能力 + 1 P0 守护 + 1 P1 + 1 P1）
> 关联 REQ（跨册接缝）: REQ-DATA-01/02（PUBLISHED 事实只读供数）、REQ-DATA-08（A3 切分写入路径退出）、REQ-ONTO-01/03（本体快照与映射契约消费方）、REQ-COG-01/02（认知契约与证据分离）、REQ-AI-01（LLM 调用唯一入口）、REQ-FC-01/02（口径主权与预测快照，PRD-09）、REQ-SEC-01~04（RLS/CLS/脱敏/ABAC）、REQ-NF-04（traceId 审计链）、REQ-FE-01~03（前端规范）
> 前置依赖: 分册 00（承流口径 ADR-15、两态寻址、ApiResponse/traceId、DDL 单源）、分册 01（`SecurityDecisionService#decide`、审计 `ecos.audit` 链、默认 DENY）、分册 02（CURATED 事实表与 data-engine 写通道、schema 归属 R-1/R-1b）、分册 03（本体快照 `kb_ontology_snapshot` 生产方、口径主权 R-6、Function 审计归属 R-5）
> 关联 ADR: ADR-15（制品≠承流）、ADR-8（本体=语义唯一源，kb 只消费不另立语义）、ADR-11（图谱双形态与显式降级）、ADR-9（认知/知识边界）、ADR-12（主控制 schema 三步走）、ADR-13（向量存储形态）、ADR-14（确定性计算产物经 data-engine 落业务域 APPLICATION）
> 规范锚点: `.trae/rules/架构铁律.md` v2.0（§0.6 场景层只编排、§2.4 安全八条含 6 默认 DENY、§2.5 公共底座八条含 2 LLM/3 调度/4 事件/8 Git、§3.1 DDL 单源、§十 配置单源、§十一 版本×Git 归档）；`.trae/rules/数据库访问规范.md` v1.2（ST07/ST03-A/ST06/ST09、MC01/MC02/MC03/MC05/MC06、IR01/IR04/IR05、DR05~DR08、§四 附则1 裸表名=FAIL、§五 三层纪律）；`.trae/rules/数据湖存储分层规范.md` v2.0（数据域二分、SEMANTIC 双形态、降级须显式标记）；`.trae/rules/后端开发规范.md` v1.1（§九 runtime 底座、§十一 版本归档）；`.trae/rules/前端开发规范.md` v1.1（组件 ≤800 行、每 Tab 独立文件、i18n/主题/lucide）
> **编号接续**: 分册 00 = W01~W15 → C16~C21；分册 01 = W29~W40 → C22~C29；分册 02 = W41~W66 → C30~C50；分册 03 = W67~W89 → C51~C71；**本册 = W90~W114 → C72~C96**

---

## 〇、本册定位与判定基线

### 0.1 本册覆盖的部署制品与实测承载

| 制品/模块 | 端口 | 实测承载 | 证据 |
|:--|:--:|:--|:--|
| `services/dccheng` | 18086 | **制品空心**：1 个 Java 类（启动类），**0 个 Controller** | `services/dccheng/**` 仅 `DcchengServiceApplication.java` |
| `engine/kb-engine` | — | 由 gateway fat-JAR `@ComponentScan` 宿主 | `GatewayApplication.java:134-137`；kb 实测 21 Controller / 100 handler |
| `workspace` 知识场景 | 18090 | **共用** `/api/v1/knowledge` 前缀 | `KnowledgeWorkbenchController.java:40` |
| `ecos_frontend/pages/knowledge` | 3000 | 37 文件 / **14,281 行**；另有孤儿副本 8 文件 / 1,988 行 | `components/aiworkbench/knowledge/**` importer 零命中 |

### 0.2 本册要解决的核心命题

一句话：**知识域是"三主体共用一套前缀、三套文档载体、双形态只有一型有数"的结构，5 项主责 REQ 里 3 项零实现，且三处接口从路径拼装上就必败——而所有失败都被网关的 Exception→404 和前端的 catch→空数据掩成"正常无数据"。**

```
① 三主体一前缀：kb-engine（21 Controller）+ workspace（KnowledgeWorkbenchController）+ gateway 侧孪生类，
   全部落在 /api/v1/knowledge/**；dev 代理把该前缀指向 ai-engine:18084（kb 实为 18086）。
② 三套文档载体：public.extraction_drafts(4 行) / ecos_knowledge.kb_doc*/切片 / ecos_dw.doc+doc_chunk(V139 第三套)。
③ 双形态一型空：Neo4j 44 节点 0 关系（全是演示 label）；PG 218 节点，"同步"仅返回 status=ready_to_sync；
   pgvector 列已建（V137 vector(1536)+HNSW）但表 0 行，检索代码保留 JSONB 回退且降级只 log.warn。
④ 三处结构必败：kb_nav_* 三表 live 不存在却挂着 19 handler + 整块前端导航 UI；
   KnowledgeSettingsController 类级+方法级路径拼接出重复前缀的畸形 URL；/api/v1/knowledge-bases 无 bare 映射。
⑤ 5 REQ 实现度：KB-01/02/03 = 0%（标识符全仓零命中）；KB-04 守护的三表名与真身不符（G2-6）；KB-05 逻辑存在但零测试。
⑥ 底座绕行：LLM 自写 RestTemplate ×2 + new RestTemplate() ×2、上传落 java.io.tmpdir、
   Executors/CompletableFuture 自建并发、审计反射 send + 12 处 log 假审计。
```

本册主任务**不是**"补 PRD-04 的功能清单"，而是：
1. **把知识域从"三主体共用前缀"收敛为单一属主 + 明确接缝**（接口面归一，见 D 章）；
2. **把双形态做实**：图谱形态显式两态（PG 权威 / Neo4j 可选投影），向量形态显式降级标记，禁止 `ready_to_sync` 式伪成功（C.4/C.6）；
3. **从零建成 REQ-KB-02/03 两个 P0 新能力**（画像库 + 假设库），并以合规 DDL 重写 PRD-04 自带的违规模型（E.2）；
4. **把三处结构必败路径修成可寻址、可归因、可观测**（A 章 F04-01~03 + C.8 三层纪律）；
5. **登记 5 项新裁决 R-8~R-12**（写权限缺口、派生统计量 ST03 处置、文档三载体、Neo4j 演示残留、零行表可否 DROP 重建）。

### 0.3 判定基线（本册所有结论的对照标准）

| # | 基线 | 来源 |
|:--:|:--|:--|
| B-1 | kb-engine 读 CURATED/SEMANTIC（只读），写"图谱实例、向量、规则"；禁直读 RAW；A3 切分写入路径须退出 | ARCH_SPEC §4.3 + REQ-DATA-08 |
| B-2 | SEMANTIC 层双形态：图谱 Neo4j（enterprise+）/ PG 表（standard）；向量 pgvector；**向量禁入控制 schema**（MC05/ADR-11）；**降级须显式标记** | 湖规 v2.0 + MC05 |
| B-3 | 画像/假设属 SEMANTIC（知识层）制品，不是业务事实 → 不违反"cognitive 零新增业务事实表" | PRD-04 §2.2 归属说明 + ARCH_SPEC §4.2 |
| B-4 | 安全 8 条全走 security-engine REST，不可用**默认 DENY**；审计经 runtime-event 发 `ecos.audit` 且不阻塞主流程 | 铁律 §2.4-1~8、§2.5-7 |
| B-5 | 基础设施/LLM/调度/事件/监控一律收敛 runtime 底座；配置与字典只经 sysman 门面单源 | 铁律 §2.5-1/2/3、§十 |

---

## 一、实测事实（K-1 ~ K-64）

> 全部条目均有 `file:line` 或可复现命令；采集时间 2026-09-29，PG `sys_man`@5432（容器 `ecos-postgres`）、Neo4j 5.26.27@7687。复现口径沿用分册 02/03（`grep -rn` / `find` / `docker exec ecos-postgres psql -U postgres -d sys_man -c`）。

### 1.1 制品与装配（K-1 ~ K-7）

| # | 事实 | 证据 |
|:--:|:--|:--|
| K-1 | `services/dccheng` 制品空心：全目录仅 1 个 Java 类，Controller 数 **0** | `find services/dccheng -name '*Controller.java'` = 空 |
| K-2 | 启动类装配面宽于其承载：`@ComponentScan("services.dccheng","engine.kb","runtime","sysman")`、`@MapperScan` 6 项含 `engine.kb.**.dao`、`.repository`，实测 **`.dao` 包零命中** | `DcchengServiceApplication.java:32-58` |
| K-3 | 类注释与装配自相矛盾：javadoc 声称 kb 需与 cognitive 同 JVM 运行，但 `@ComponentScan` 不含 `engine.cognitive2` | 同文件 `:20-23` vs `:40-44` |
| K-4 | 死依赖：`kb-engine-impl` 依赖 `sysman-impl`，全模块 import 零命中 | `engine/kb/kb-engine-impl/pom.xml:83` |
| K-5 | 注释失真 + 依赖冗余：pom 注释称"有 `@KafkaListener`"，实测 kb 源码 `@KafkaListener` **零命中**，但仍引 `spring-kafka`（违铁律 §2.5-4 事件底座单源 + ArchUnit 只查类型引用的盲区） | `kb-engine-impl/pom.xml:86-92` |
| K-6 | 承流事实：kb 的 21 个 Controller 全部由 gateway fat-JAR 宿主；`:18086` 进程实测**零 controller 承流**（ADR-15 制品口径 ≠ 承流口径的极端样本） | `GatewayApplication.java:134-137` + `netstat` |
| K-7 | 前缀多主体：`/api/v1/knowledge**` 同时被 kb-engine（21 Controller）、workspace（`KnowledgeWorkbenchController.java:40`）声明，另有 gateway 侧孪生类（见 K-13） | `grep -rn '@RequestMapping("/api/v1/knowledge'` |

### 1.2 接口面与路由（K-8 ~ K-15）

| # | 事实 | 证据 |
|:--:|:--|:--|
| K-8 | kb 21 个 Controller 的类级路径**全部** `/api/v1/knowledge/**`，唯二例外：`/api/v1/knowledge-bases`、`/api/v1/engine/knowledge` | `KnowledgeListController.java:27`、`KbEngineHealthController.java:20` |
| K-9 | 无 bare/v1 双路径写法（对照既有约束"双路径各写一遍"），意味着 v1↔bare 全靠过滤器重写，一旦过滤器未覆盖即 404 | 全量 `@RequestMapping` 清点 |
| K-10 | 单类最大接口面：`NavTaxonController` 19 个 handler（本域最重，且依赖不存在的表，见 K-19） | `NavTaxonController.java:59` |
| K-11 | **结构必败①**：`KnowledgeSettingsController` 类级 `/api/v1/knowledge/settings` + 方法级再写全路径，Spring 拼接结果 `/api/v1/knowledge/settings/api/v1/knowledge/extract/upload-enabled`，永不可达 | `KnowledgeSettingsController.java:85` |
| K-12 | **结构必败②**：`EntityLinkController` 类级前缀 + 方法 `/entity/link` 组合出畸形路径（与 K-8 前缀家族不一致） | `EntityLinkController.java:21` |
| K-13 | 同名双控制器：`gateway/.../EcosKnowledgeGraphController.java:19` 声明 `/api/v1/ecos/knowledge-graph` 且已被 `GatewayApplication.java:87` 排除，仍留存源码；`kb/controller/EcosKnowledgeGraphController.java:26` 声明 `/api/v1/knowledge/ecos-graph` | 两处同名类 |
| K-14 | 重写单源缺口：`VersionPrefixRewriteFilter.java:45` 已把 knowledge 从重写清单移除，`:61` v1→bare、`:83` bare→v1 —— `/api/v1/knowledge-bases` 无 bare 侧映射（**结构必败③**） | `VersionPrefixRewriteFilter.java:45/61/83` |
| K-15 | dev 代理指错引擎：`vite.config.ts:25` 将 knowledge 前缀直连 **18084（ai-engine）**，而 kb-engine 归属 18086；`:38-39/41-42` 另有相关条目；生产 BFF `server.ts:41/107-112/146` 默认全走 gateway | 实测配置 |

### 1.3 数据层与 Schema（K-16 ~ K-27）

| # | 事实 | 证据 |
|:--:|:--|:--|
| K-16 | schema 全景：6 权威（`public` 199 / `ecos_data` 23 / `ecos_ontology` 19 / **`ecos_knowledge` 20** / `ecos_ai` 50 / `ecos_cognitive` 11）+ 越界 6（`ecos_demo` 208、`ecos_security` 53、`ecos_infra` 40、`ecos_sysman` 20、`ecos_dq` 20、`ecos_dw` 2）+ 空壳 4 | `information_schema.tables` 分组计数 |
| K-17 | 双镜像 6 对（`public` ↔ `ecos_knowledge`，实数据全在 `public`，引擎 schema 侧全 0 行），且**主键宽度不一致（VARCHAR(36) ↔ VARCHAR(64)）**：`ecos_glossary_term` 32/0、`ecos_knowledge_document` 8/0、`ecos_knowledge_graph_edge` 18/0、`ecos_knowledge_graph_node` 23/0、`ecos_marketplace_asset` 10/0、`access_request` 0/0 | 同表跨 schema 对比 |
| K-18 | `ecos_knowledge` 内仅 6 张有行：`kb_knowledge_graph_node` 218、`kb_ontology_snapshot` 5、`kb_knowledge_graph_edge` 4、`kb_extract_audit` 4、`kg_sync_log` 3、`kb_scheduled_extract` 2；**其余 14 张 0 行** | 逐表 `count(*)` |
| K-19 | **表缺失**：`kb_nav_category` / `kb_nav_tag` / `kb_nav_article_rel` 在 live 库**不存在**，但迁移脚本 `V155~V157` 存在（该迁移未在本环境执行），而 `NavTaxonController` 19 handler + 前端导航整块 UI 均按其存在来写 | `to_regclass('…kb_nav_category')` = NULL |
| K-20 | 文档/切片三载体并存：`public.extraction_drafts`(4) / `ecos_knowledge.kb_document*` / `V139` 在 **`ecos_dw.doc` + `doc_chunk`** 建第三套（业务域出现第三套文档表，与湖规分层归属冲突） | 迁移文件 + 表计数 |
| K-21 | 生产表被测试夹具污染：`sys_compliance_rule` 53 行，id 形如 `test-legacy-1`、`cr-N`，来源 `ComplianceRuleTest.java` | 抽样行 + 测试源码 |
| K-22 | **MC01 违规**：知识域仅 3 表合规；14 列使用 `nextval`（BIGSERIAL 系）；`V155:20 DEFAULT gen_random_uuid()`；`NavTaxonServiceImpl.java:152` 运行期 `gen_random_uuid()::text` | DDL + 源码 |
| K-23 | **MC02 违规**：知识域 19 个 `jsonb` 列（规范只允许 TEXT） | `information_schema.columns` |
| K-24 | **MC03 违规**：`::` 强转 16 处/7 文件、`ON CONFLICT` 14 处、`databaseId` 方言分支**零命中**、`ILIKE` 广泛 → 不可移植到 MySQL/Oracle | 全量 grep |
| K-25 | **DR06~DR08 无一表合规**；`domain` 列仅 9/30 表存在 | 逐表列清点 |
| K-26 | 迁移目录 125 文件、最高 `V163`，知识相关最高 `V157`；`V7/V100/V104/V116/V117/V118` 以**裸表名**建表（违 §四 附则1）；`V137` 是全仓唯一 `CREATE EXTENSION vector`；知识 schema **零 DROP** | 目录清点 |
| K-27 | 方言文件漂移：`ecos-sql/{postgresql,mysql,oracle}/05_ecos_knowledge.sql` 各 12 表且**落后 live**；同列 `embedding` 三库三形态（PG JSONB / MySQL JSON / Oracle CLOB）；PG 方言文件内**无 `vector(`** | 三方 diff |

### 1.4 知识层双形态（K-28 ~ K-33）

| # | 事实 | 证据 |
|:--:|:--|:--|
| K-28 | Neo4j 实测 **44 节点 / 0 关系**，labels 为 `S10_TEST`/`Invoice`/`Order` 等演示数据；凭据 `neo4j/neo4j123`（compose `NEO4J_AUTH`），版本 5.26.27 | cypher-shell `MATCH (n) RETURN count(n)` |
| K-29 | **伪成功**：`syncToNeo4j` 并不写 Neo4j，直接返回 `status="ready_to_sync"`（PG 218 节点 vs Neo4j 44 演示节点即为铁证） | `EcosKnowledgeGraphServiceImpl.java:136-178` |
| K-30 | 同一方法用 `log.info("AUDIT topic=…")` 冒充审计 | 同文件 `:171` |
| K-31 | **唯一合规图谱写入链**：`KGWriterService` 经 runtime-access 的 `Neo4jClient` 写 + 重试（本册以此为收敛基线） | `KGWriterService.java:17/63/95/115/121/177-191` |
| K-32 | **配置借键**：`RuleGraphService.java:46 @Value("${cognitive.neo4j.switch-on-write:}")` 借用 cognitive 前缀键，且 dccheng yml 未定义（违铁律 §十 配置单源）；`dccheng/application.yml:39-43/62-63` 自有 `spring.neo4j.uri` + `ecos.neo4j.enabled=${ECOS_NEO4J_ENABLED:false}` | 两处配置 |
| K-33 | **向量形态空转 + 降级不显式**：`V137:15/18-19/25-26` 建 `vector(1536)`+HNSW，但文件头自陈"需手工执行"、表 0 行；`KnowledgeEmbeddingMapper.java:32-35` 用 `<=>` + `CAST(… AS vector)`，`:60-73` 保留 JSONB 回退分支；`PgVectorSupport.java:45/49-53` 探测失败仅 `log.warn`（`:29/32`）→ 违反湖规 v2.0"降级须显式标记" | 迁移 + Mapper + 探测类 |

### 1.5 公共底座合规（K-34 ~ K-40）

| # | 事实 | 证据 |
|:--:|:--|:--|
| K-34 | LLM 唯一合规调用点：`NavLlmRecommendService` 经 llm-gateway | `:4/49-50/82` |
| K-35 | **绕底座 ×2**：`AnswerGenerationHelper.java:61/156-158`（`:89/48`）、`QueryEmbeddingHelper.java:68-70/122-139`（`:72-75/105-115/216-230`）自写 RestTemplate 直打 `/api/v1/llm/chat\|embedding`（违铁律 §2.5-2） | 源码 |
| K-36 | `new` 绕过装配：`KbEngineRestConfig.java:15-17 new RestTemplate()`、`KnowledgeExtractionService.java:101 new RestTemplate()` | 两处 |
| K-37 | **存储绕湖**：上传落 `java.io.tmpdir + "/ecos-extractions"`（`:64`）+ `transferTo`（`:116`），未走 MinIO RAW 层（对照唯一合规链 `KnowledgeDocIngestService.java:20/81/93-114/…`：MinIO 近源层 + datanet 登记） | 源码 |
| K-38 | **自建并发**：`KnowledgeExtractionService.java:124 Executors.newSingleThreadExecutor().submit`、`:154-184 MAX_RETRY=1`；`KnowledgeArticleController.java:72/102/104/114 CompletableFuture`（违铁律 §2.5-3 调度归 runtime-task） | 源码 |
| K-39 | 调度双轨：`KBExtractScheduleServiceImpl.java:72/75/110/123-124/143/149/163-166/203-206/232/258/273/460/476/511-514` 与 `KbImportTaskRegistrar.java:36-41`、`KbImportTaskExecutor.java:52/59/215-217/236/244/254` 并存，部分走 runtime-task、部分本地 `@Scheduled`；`KgSyncServiceImpl.java:58/156/214/223/232/263/484` 同型 | 源码 |
| K-40 | 进程内状态：`KnowledgeGraphServiceImpl.java:38-43` Caffeine 512/30s；`OntologyTreeController.java:55 static DOMAIN_BUCKET` 内存态（`:40-41/66/80/115/138/153-156/248-249/263/282-285`）→ 多副本不一致 | 源码 |

### 1.6 安全、隔离与异常（K-41 ~ K-46）

| # | 事实 | 证据 |
|:--:|:--|:--|
| K-41 | kb 主源码 `TenantContextHolder`、`@RequirePermission` **零命中** → ABAC 装饰器全未接入（铁律 §2.4-4） | 全量 grep |
| K-42 | 域隔离仅 5 处 `WHERE domain = #{domain}`（`ComplianceRuleMapper:41`、`ExpertRuleMapper:21`、`KnowledgeArticleMapper:14`、`KnowledgeNodeMapper:14/50`、`NavTaxonServiceImpl:188`）；写侧 `create_by` 落常量 `'current-user'`（`NavTaxonServiceImpl:152/196`）→ 审计不可归因 | 源码 |
| K-43 | `KnowledgeListController.java:8/18/37-59`：`HttpServletRequest` 形参声明未用，`tenant_id` 仅存在于注释 | 源码 |
| K-44 | **反射绕过事件底座**（与分册 03 C71 同型）：`KnowledgeNavSecurityEngineClient.java:48-126` ABAC DENY 侧合规（`:71-74/83-86`），但审计以反射 `send(...)` 发出（`:88/98-102`），`tenantId=default`/`role=user` 硬编码（`:29/34`），`timeoutMs` 声明未用（`:53/54`），bean 缺失仅 warn 后继续 = 静默丢审计 | 源码 |
| K-45 | **12 处 log 假审计**（字面量含 `topic=ecos.audit`）：`GraphSyncController.java:135`、`KnowledgeIngestController.java:315`、`KbEntityInstanceExtractionService.java:898`、`KgMapperService.java:268`、`KnowledgeDocIngestService.java:396`、`KnowledgeExtractionService.java:726` 等 | 全量 grep |
| K-46 | **掩蔽链**：`GlobalExceptionHandler.java:186-201` 将 `Exception` 统一映射为 **404**；kb 侧无局部 advice；dccheng `@ComponentScan` 不含 `gateway.handler`（K-2）；控制器 `catch (Exception)` **51 处/21 文件**。异常类型分布：Validation 50 / NotFound 14 / Runtime 11 / DataAccess 11 / TaskParse 7 / TaskExec 6 / IllegalState 3 / IllegalArg 3 / Business 2 / Forbidden 1；`DataAccessException` 同名双源（common 5 / spring 4） | 源码 + 统计 |

### 1.7 主责 REQ 实现度与门禁（K-47 ~ K-51）

| # | 事实 | 证据 |
|:--:|:--|:--|
| K-47 | **REQ-KB-01 = 0%**：`C3_UNKNOWN_ATTR`/`C3_MISSING_REQUIRED`/`C3_TYPE_MISMATCH` 与 `c3_enabled` 全仓零命中 | 全量 grep |
| K-48 | **REQ-KB-02/03 = 0%**：`profiles`/`assumptions`/`ecos_kb_profile`/`ecos_kb_assumption` 零命中；且 PRD-04 自带 DDL 违 MC01（`DEFAULT gen_random_uuid()`）与 MC02（`JSONB`） | `PRD-04:57/:119` |
| K-49 | **REQ-KB-04 守护对象不存在**：`kb_cognitive_hypothesis`/`kb_cognitive_belief`/`kb_mind_registry` 三表均无；真身为 `public.ecos_cognitive_hypothesis`(7)/`ecos_cognitive_belief`(9)/`ecos_cognitive_evidence`(5)（`V127~V129`）；`kb_mind_registry` 仅存在于 PRD-00/01/04/05 + ADR-8/9 + `BUSINESS_SCENARIO_*` 文档 → 对应需求检视报告 **G2-6** | `to_regclass` + PRD |
| K-50 | **REQ-KB-05 逻辑在、测试无**：`dryRun` 75 处、watermark 8 处，`KbEntityInstanceExtractionService.java:343` 确实仅非 dryRun 更新水位线（口径正确），但零契约测试，且 `kb_extract_watermark` 表 **0 行** | 源码 + 计数 |
| K-51 | 门禁盲区：kb `ArchitectureTest.java:37/60/76/85/95-101` 的 `shouldNotDependOnOtherEngines` 枚举 data/cognitive2/ai/security，**漏 `engine.ontology`**；实测 kb import `engine.ontology…` **10 处**，含对外契约 `kb-engine-api/…/model/ComplianceRule.java:3` 直接耦合 ontology 内部模型。另 cognitive 侧 `KbRestClientConfig.java:14/41-42/51-60`（`ecos.kb-base` 默认 localhost:8080、`catch → emptyMap`）其 javadoc `:51-52` 把降级策略标为"铁律 §2.4-6"，而 §2.4-6 实为**默认 DENY** → 条文挪用 | 两处测试/源码 |

### 1.8 前端（K-52 ~ K-64）

| # | 事实 | 证据 |
|:--:|:--|:--|
| K-52 | `pages/knowledge` 37 文件 / **14,281 行**；`components/aiworkbench/knowledge` 8 文件 / 1,988 行 **importer 零命中**（孤儿副本） | `find` + `wc -l` + importer grep |
| K-53 | 死页三件：`KnowledgeGraphHome.tsx`(554)、`KnowledgeGraphPage.tsx`(124)、`KnowledgeGraph.tsx`(521)，仅被 `main.tsx:142-145` 的**注释**引用 | 路由文件 |
| K-54 | 三入口竞争 + 三套路由命名：`Sidebar.tsx:94/112`、`CommandPalette.tsx:67`、`Topbar.tsx:36-38`；`main.tsx:108/150/163/188` 用 `knowledge_graph` / `knowledge_view` / `engine-knowledge` 三名并存 | 源码 |
| K-55 | `knowledgeApi.ts` **1,233 行 / ~85 个导出**：`:27-36` 三个 base 别名指向同一前缀；`:106-141` 手写 SSE；`:143-151 fetchGraph` 无 limit（全量拉图） | 源码 |
| K-56 | 9 组死调用指向后端不存在端点（含 `ExtractionReviewPanel.tsx:111/120/128`、`KnowledgeComplianceCheckTab.tsx:133`、`DocumentUploadTab.tsx:181`、`VectorIndexTab.tsx:130`），全部被 `catch → 空态` 吞掉（配合 K-46 = 用户看到"暂无数据"而非"接口坏了"） | 源码 |
| K-57 | 前端规范违例：**硬编码中文 256 处/18 文件**、**硬编码 Tailwind 色 155 处/16 文件**、`setTimeout` 3 处、TODO/占位 16 处、**localStorage 当数据库 4 组** | 全量 grep |
| K-58 | 组件超限（≤800）：`ClassificationTab.tsx` **915**、`GraphExplorerTab.tsx` **810** | `wc -l` |
| K-59 | 重复造轮：`GraphCanvas.tsx:49-53/131/137-142/193/386/408/455` 手写 SVG + **O(n²)** 力导向；`package.json:24/27/30-31/41` 已装 `@xyflow/react` 12.11、cytoscape 3.34、echarts 6.1、recharts 3.8 却未用于知识图谱 | 两处 |
| K-60 | 无障碍/移动端：`aria-*` 仅 9 处/3 文件；`useMediaQuery`/`useMobileSidebar`/`MobileDataTable` 在知识域 **0 命中** | 全量 grep |
| K-61 | 主题/i18n：8/40 文件未 import `useTheme`；i18n `knowledge` 命名空间已有 **1,533 键**，但代码仍 256 处硬编码中文（键存在而不用） | 源码 |
| K-62 | 测试真空：40 文件中仅 3 个有测试、API 层 0；`ecos-tests` 3 个 `.mjs` 对 knowledge **零命中**；无 `playwright.config.ts` | 目录清点 |
| K-63 | `KnowledgeView.tsx:7/56-75/101-120/127-135/176-183/196-207/212/224`：已裁决卸载的路由壳内仍有 8 处对 legacy 端点直调（卸载不彻底） | 源码 |
| K-64 | `typesAndConstants.ts:290-329/337-345/355/359-366/368-375/79`：前端状态/类型枚举三处与后端定义漂移（同一枚举两套字面量） | 源码 |

---

## 二、A 章 — 功能设计（F04-01 ~ F04-18）

> 每项含：功能陈述 / 规则锚点 / 现状差距（K 编号）/ 设计要点 / **可执行验收标识**（`mvn -Dtest=…` 或 `*.spec.ts` 用例名，依 Q13 裁定：写"人工确认"视为虚假验收）。

### F04-01 知识域接口面归一（属主收敛）

**陈述**：`/api/v1/knowledge/**` 建立单一属主 = kb-engine；workspace 场景侧改挂 `/api/v1/scenarios/knowledge/**`；gateway 侧孪生类删除；dev 代理指向修正。
**差距**：K-7、K-8、K-13、K-15、K-9。
**要点**：
1. `KnowledgeWorkbenchController` 类级路径迁移（**新增别名路径 + 旧路径标 `Deprecation`，不做破坏性改动**，依"API 只增不改"）；
2. 删除被 `GatewayApplication.java:87` 排除的死类，避免误读为可用实现；
3. `vite.config.ts` knowledge 前缀目标由 18084 → 18086，并加注释声明"仅 dev，生产走 BFF→gateway"；
4. `VersionPrefixRewriteFilter` 补 knowledge 家族映射（含 `knowledge-bases` 双路径）。

**验收**：`mvn -Dtest=KnowledgeRouteIntegrityTest#everyKnowledgeMappingHasV1AndBarePair`、`#knowledgePrefixHasSingleOwningModule`、`mvn -Dtest=KnowledgeRouteIntegrityTest#devProxyTargetsKbPort`（配置断言，读 vite.config.ts）。

### F04-02 三处结构必败路径修复

**陈述**：K-11/K-12/K-14 三条永不可达路径改为可寻址，并让失败可观测。
**要点**：方法级一律相对路径（禁再写全路径）；`EntityLinkController` 类级并入 `/api/v1/knowledge/graph` 家族并保留旧路径别名；`knowledge-bases` 补 bare 映射。
**验收**：`mvn -Dtest=KnowledgeRouteIntegrityTest#noNestedApiPrefixInHandlerMapping`（扫描 mapping 结果字符串，断言无二次 `/api/v1/`）+ `*.spec.ts › knowledge › 设置页开关可读写`。

### F04-03 异常语义与掩蔽解除（W 系列首项）

**陈述**：kb 侧建立局部 `@RestControllerAdvice`，使 `DataAccessException`/表缺失 → 500 + 错误码，不再落入网关 `Exception→404`；控制器 `catch (Exception)` 只允许边界层且必须重抛为领域异常。
**差距**：K-46、K-19、K-56。
**要点**：错误码见 D.5；`GlobalExceptionHandler.java:186-201` 的 Exception→404 映射改造属分册 01 C 系列（跨册接缝，本册引用不重复立项）；前端 `catch → 空态` 必须区分 `404`（无数据）与 `5xx/ECOS-KB-*`（故障），后者渲染显式错误态。
**验收**：`mvn -Dtest=KbNavMissingTableReturnsServerError#absentTableYields500Not404` + `*.spec.ts › knowledge › 接口故障不被渲染为空数据`。

### F04-04 知识导航（kb_nav_*）表存在性闭环

**陈述**：`V155~V157` 迁移在目标环境执行到位（或按 E.2 合规重写为 `V175`），并在开测前用 `to_regclass` 预检。
**差距**：K-19、K-22、K-23。
**验收**：`mvn -Dtest=SchemaInventoryGateTest#kbNavTablesExistBeforeHandlers`（沿用分册 02 的清单门禁形态）。

### F04-05 C3 属性完整性校验（REQ-KB-01，P1，从零实现）

**陈述**：在 `KbEntityInstanceExtractionService.extractForMapping` 的 C1 之后、节点 upsert 之前插入 C3 三规则（R1 未知属性 WARN / R2 必填缺失 WARN / R3 类型不符 WARN 且置 null 不写入），全 WARN 不阻断，issue 入 `report.issues[]`。
**差距**：K-47。
**要点**：`ecos.kb.extract.c3_enabled=true` 默认开；关闭时 report 标 `c3Skipped=true`（禁静默关闭）；issue 结构对齐既有 C1/C2/C4。
**验收**：`mvn -Dtest=C3AttributeValidationTest#missingRequiredProducesWarnButStillWrites`、`#unknownAttributeWarnsAndStillWrites`、`#typeMismatchWarnsAndNullifiesField`、`#c3DisabledMarksC3Skipped`。

### F04-06 历史画像知识生成（REQ-KB-02，P0，新能力）

**陈述**：只读 CURATED 层 `dq_status=PUBLISHED` 的 ACTUAL 事实，按 `项目类型×部门×环节` 生成分组分布画像（P10/P50/P90、样本量、缺失率、置信区间）+ 分层退化 + 审批发布。
**差距**：K-48。
**要点**：生成一律经 `ITaskManagementService.submitTask+executeTask`（铁律 §2.5-3，禁 `Executors`/`@Scheduled` 自建，纠正 K-38/K-39 双轨）；退化链 `项目×部门×环节 → 项目类型×部门×环节 → 项目类型×环节 → 部门×环节 → 全局×环节`；阈值 n≥30 HIGH / 10≤n<30 MEDIUM / n<10 触发退化；版本单调递增、已发布不可改；取数经 datanet REST 或 runtime-access 只读，**禁直读 RAW**（B-1）。落点与写通道 **R-8 ② 已批准**（数值事实落业务域 + 写通道唯一 = data-engine，版本/审批语义留 K 层）。
**验收**：`mvn -Dtest=KbProfileGenerateTest#groupedByTypeDeptStageWithHighConfidence`、`#insufficientSampleDegradesAndRecordsDegradeFrom`、`#republishCreatesNewVersionAndSupersedesOld`、`#publishedProfileUpdateIsRejected`、`mvn -Dtest=KbProfileResolveTest#resolveReturnsFullDegradeChain`。

### F04-07 假设库版本化（REQ-KB-03，P0，新能力）

**陈述**：情景假设（BASE/CONSERVATIVE/AGGRESSIVE/自定义）落库，状态机 `DRAFT → PENDING_APPROVAL → APPROVED(ACTIVE) → {EXPIRED | SUPERSEDED}`，仅 ACTIVE 可被预测引用；DELTA 语义表达相对增量；`reason` 必填；`evidence_ref` 缺失则 `unverified=true`。
**差距**：K-48。
**要点**：EXPIRED 判定经 runtime-task 定时（禁自建定时器）；APPROVED 后修改 = 新版本 + 旧 SUPERSEDED（409 拒改）；`unverified` 与 cognitive 证据分离一致（REQ-COG-02）。
**验收**：`mvn -Dtest=KbAssumptionLifecycleTest#threeScenariosResolveCorrectly`、`#approvedUpdateReturns409`、`#expiredAssumptionNotResolved`、`#missingEvidenceRefMarksUnverified`。

### F04-08 场景认知契约守护（REQ-KB-04，P0，守护型）

**陈述**：kb 对场景侧只读开放 hypothesis/belief/mind 三态；不提供写代理；status 枚举冻结 `PROPOSED/EVIDENCED/BELIEVED/REFUTED`；cognitive 域不反向写 business 表。
**差距**：K-49（守护对象表名不存在，G2-6）、K-51。
**要点**：**先做名称真身核对再落守护**——本册采用"文档契约名 ↔ 库内真身名"映射表（`kb_cognitive_hypothesis` ⇒ `public.ecos_cognitive_hypothesis` 等），把裁定权交 R-12/PRD 回写；枚举冻结以 OpenAPI `enum` + Java 枚举双锁；表存在性预检复用 PRD-01 DB-03 脚本。
**验收**：`mvn -Dtest=CognitionContractGuardTest#guardTablesResolveToRealRelation`、`#hypothesisStatusEnumIsFrozen`、`mvn -Dtest=ScenarioCognitionIntegrationTest#emptyScenario/multiMind/noGateway/serviceUnavailableReturns503`（依赖分册 05 E1~E4，收口后启用）。

### F04-09 dry-run 真口径守护（REQ-KB-05，P1，守护型）

**陈述**：同一输入 dry-run 与真执行的 `{created,updated,issues[]}` 三项 diff=0；dry-run 不改水位线、不改表行数。
**差距**：K-50。
**验收**：`mvn -Dtest=KbDryRunContractTest#dryRunMatchesRealExecutionOnCreatedUpdatedIssues`、`#dryRunLeavesWatermarkAndRowCountUnchanged`。

### F04-10 图谱双形态做实（伪成功清零）

**陈述**：`syncToNeo4j` 要么真写、要么明确返回 `notAttempted` 并置降级标记；standard 档以 PG 表为权威形态，enterprise+ 才有 Neo4j 投影，且投影是 PG 的**从属副本**（可重放、可校验一致性）。
**差距**：K-28、K-29、K-30、K-32。
**要点**：统一到 `KGWriterService`（K-31 唯一合规链）；配置键改 `ecos.kb.graph.neo4j.switch-on-write`（本域前缀，纠正借键）；同步后做 PG↔Neo4j 计数对账并落 `kg_sync_log` 真结果；`ready_to_sync` 状态字面量全仓清除。
**验收**：`mvn -Dtest=GraphDualFormTest#syncEitherWritesOrMarksDegraded`、`#pgNeo4jNodeCountReconcileAfterSync`、`mvn -Dtest=GraphDualFormTest#noReadyToSyncLiteralRemains`（源码 regex 断言）。

### F04-11 向量形态显式降级（MC05/ADR-11/湖规"降级须显式标记"）

**陈述**：pgvector 可用 → 向量检索；不可用 → JSONB 回退**必须在响应与报告中标记 `vectorDegraded=true` + 原因 + 影响**，且 UI 显示降级徽标；向量列禁入控制 schema。
**差距**：K-33、K-27。
**验收**：`mvn -Dtest=VectorFormDegradationTest#jsonbFallbackIsFlaggedNotSilent`、`#vectorColumnNeverInControlSchema`。

### F04-12 文档载体收敛（三 → 一）

**陈述**：文档/切片权威载体唯一化（依 R-10 裁决落点），`extraction_drafts` 定性为"抽取草稿"而非文档库，`ecos_dw.doc*` 第三套定性为演示遗留只停写。
**差距**：K-20、K-37。
**要点**：上传一律经 MinIO RAW 层（沿用 `KnowledgeDocIngestService` 合规链），`java.io.tmpdir` 仅作抽取临时缓冲且必须有 TTL 清理；A3 切分写入路径退出（REQ-DATA-08）。
**验收**：`mvn -Dtest=DocCarrierConvergenceTest#singleAuthoritativeDocTable`、`#uploadGoesThroughMinioNotTmpdir`。

### F04-13 LLM 调用收口 llm-gateway

**陈述**：删除 2 处自写 RestTemplate 直连与 2 处 `new RestTemplate()`，全部经 `llm-gateway`；超时/重试/审计/配额由底座承担。
**差距**：K-34（唯一合规点）、K-35、K-36。
**验收**：`mvn -Dtest=ModuleDependencyArchTest#kbMustNotCallLlmEndpointDirectly`（新增规则：kb 包禁出现 `/api/v1/llm/` 字面量与裸 `new RestTemplate`）。

### F04-14 调度与并发收口 runtime-task

**陈述**：`Executors.newSingleThreadExecutor`、控制器内 `CompletableFuture`、本地 `@Scheduled` 全部改经 `ITaskManagementService`；异步任务中心可反查 progress。
**差距**：K-38、K-39。
**验收**：`mvn -Dtest=KbTaskDelegationTest#profileGenerationVisibleInTaskCenter`、`mvn -Dtest=ModuleDependencyArchTest#kbMustNotCreateOwnExecutors`。

### F04-15 审计与事件收口 runtime-event

**陈述**：12 处 log 假审计与 `KnowledgeNavSecurityEngineClient` 反射 send 全部改经 `EventBusService`；bean 不可用时本地兜底必写，兜底亦失败 → 拒绝操作（默认 DENY，纠正"warn 后继续 = 静默丢审计"）。
**差距**：K-30、K-44、K-45。
**验收**：`mvn -Dtest=KbAuditChainTest#extractWriteEmitsAuditEvent`、`#eventBusUnavailableFailsClosed`、`mvn -Dtest=ModuleDependencyArchTest#kbMustNotReflectivelyInvokeSend`（补分册 03 C71 提出的反射规则到 kb 包）。

### F04-16 域隔离与可归因写入

**陈述**：kb 读路径接 security-engine RLS/CLS/脱敏裁决；写路径的 `domain`/`create_by` 取真实上下文，禁 `'current-user'` 常量与 `tenantId=default` 硬编码；`@RequirePermission` 覆盖全部写端点。
**差距**：K-41、K-42、K-43。
**验收**：`mvn -Dtest=KbIsolationTest#writeUsesRealActorAndDomain`、`#crossDomainReadIsDenied`、`#everyWriteEndpointHasPermissionAnnotation`。

### F04-17 前端知识域重构（详见 B 章）

**陈述**：3 入口 → 1 入口；孤儿副本与死页删除；`knowledgeApi.ts` 按 Tab 拆分为独立模块；图谱改用 `@xyflow/react`；硬编码中文/色板清零；补 i18n 与移动端复用。
**差距**：K-52 ~ K-64。
**验收**：`*.spec.ts › knowledge › 六 Tab 可达且空/故障态可区分`（4 主题 × 6 Tab 截图基线，沿用 ecos-fullstack-step 口径）+ `npm run lint` 零 error + `mvn -Dtest=FrontendBudgetGateTest#noKnowledgeComponentExceeds800Lines`。

### F04-18 契约与门禁补强

**陈述**：`ArchitectureTest` 的跨引擎规则补 `engine.ontology`（当前漏项，K-51）；`kb-engine-api` 对外模型不得耦合 ontology 内部类；PRD-04 的 DDL 违规由 PRD 回写修正（见 F 章）。
**验收**：`mvn -Dtest=ModuleDependencyArchTest#kbMustNotDependOnOntologyInternals`、`mvn -Dtest=KbApiContractIsolationTest#apiModuleHasNoEngineInternalImports`。

---

## 三、B 章 — 界面设计（信息架构 + 八要素）

### B.0 信息架构归一

**现状**：3 个入口（`Sidebar.tsx:94/112`、`CommandPalette.tsx:67`、`Topbar.tsx:36-38`）× 3 套路由命名（`knowledge_graph` / `knowledge_view` / `engine-knowledge`）× 3 个死页（K-53/K-54）。

**目标 IA**（唯一入口 = 侧栏"知识"；HashRouter；每 Tab 独立文件）：

```
#/knowledge
 ├─ overview      知识总览（资产健康度 + 双形态状态 + 降级徽标）
 ├─ assets        知识资产（文档/条目/规则，含审批态）
 ├─ extract       知识抽取（接入 → 预演 dry-run → 真执行 → 报告含 C1~C4 issues）
 ├─ graph         知识图谱（@xyflow/react；形态徽标 PG|Neo4j；一致性对账）
 ├─ wiki          知识导航（分类/标签树，依赖 F04-04）
 └─ govern        知识治理
      ├─ profiles      历史画像（生成任务、退化链、置信度、发布）
      ├─ assumptions   假设库（情景、状态机、审批、unverified 标记）
      └─ retrieval     RAG 调试（命中来源、CLS 掩蔽、vectorDegraded）
```

**删除**：`components/aiworkbench/knowledge/**`（8 文件孤儿副本）、`KnowledgeGraphHome/Page/Graph.tsx`（3 死页）、`KnowledgeView.tsx` 的 8 处 legacy 直调（K-52/K-53/K-63）。

### B.1 ~ B.8 八要素表

| Tab | B.1 用户与场景 | B.2 主流程 | B.3 数据与字段 | B.4 交互态 | B.5 空/错误态 | B.6 权限与脱敏 | B.7 i18n/主题 | B.8 验收（可执行） |
|:--|:--|:--|:--|:--|:--|:--|:--|:--|
| overview | 知识管理员看健康度 | 拉取资产计数 + 双形态状态 | 节点/边/向量/画像/假设计数、`vectorDegraded`、`graphForm` | 卡片点击跳对应 Tab | 加载中骨架；引擎不可用显示"来源不可达"而非 0 | 全部经 security 裁决；计数不含越权行 | `knowledge.overview.*`；`useTheme()` | `› knowledge › 总览显示真实降级标记` |
| assets | 编辑者管理文档/条目 | 列表 → 详情 → 提交审批 | 文档 id/来源/切片数/审批态/domain | 分页 + 批量选择 | 空态引导上传；5xx 显式错误码 | CLS 掩蔽列显示 `•••`；写需 `@RequirePermission` | 键复用现有 1,533 命名空间 | `› knowledge › 资产列表分页与掩蔽` |
| extract | 分析师做抽取 | 上传 → dry-run 预演 → 审阅 issues → 真执行 | `created/updated/issues[]`、`c3Skipped`、watermark | dry-run 与真执行同屏对比（diff 高亮） | 表缺失/LLM 不可达分别报错 | 上传落 MinIO；审计含真实 actor | `knowledge.extract.*` | `› knowledge › dryRun 与真执行三项一致` |
| graph | 架构师看关系 | 选本体快照 → 展开子图 → 折叠/钻取 | 节点/边（**必须带 limit**，纠正 K-55 无 limit） | `@xyflow/react` 拖拽、分组、小地图 | 0 边时显示"仅节点，无关系"并给对账入口 | 读经 RLS；节点标签脱敏 | 4 主题色由 token 提供 | `› knowledge › 图谱子图上限与形态徽标` |
| wiki | 全员浏览导航 | 分类树 → 文章 → 关联 | 分类/标签/文章关系（依赖 F04-04） | 树懒加载 | **表不存在时 500 显式报 ECOS-KB-050**（禁"暂无数据"） | 同 assets | `knowledge.wiki.*` | `› knowledge › 导航表缺失显示故障态` |
| govern/profiles | 知识管理员生成并发布画像 | 选指标+维度+窗口 → 提交任务 → 任务中心进度 → DRAFT 列表 → 发布 | `profile_key/metric/dims/window/p10/p50/p90/ci/sample/missing/confidence/degrade_from/version/status` | 退化链可视化（树形）；置信度色阶 | 任务失败显示 taskId + 错误；样本不足不报错只降置信 | 发布需知识管理员角色 | `knowledge.profiles.*` | `› knowledge › 画像生成到发布全流程` |
| govern/assumptions | 业务负责人维护情景假设 | 建 DRAFT → 提交 → 审批 → ACTIVE → 到期 EXPIRED | `scenario/metric/dimScope/value/semantic/base/reason/evidenceRef/expireAt/version/unverified/status` | 状态机徽标；DELTA 显示相对基准箭头 | 改已批准项 → 409 并提示"建新版本" | 审批需角色；`unverified` 必须在 UI 呈现 | `knowledge.assumptions.*` | `› knowledge › 假设审批与 409 提示` |
| govern/retrieval | 开发者调 RAG | 输入问题 → 看召回 → 看生成 | 命中片段、score、来源、`vectorDegraded`、掩蔽标记 | 流式输出（SSE 经统一封装，禁页面内手写，纠正 K-55） | 向量降级显式横幅 | 生成前经 CLS/脱敏（C.2） | `knowledge.retrieval.*` | `› knowledge › RAG 调试面板降级横幅` |

### B.9 前端工程约束落地项

| 约束 | 现状 | 目标动作 |
|:--|:--|:--|
| 组件 ≤800 行 | `ClassificationTab.tsx` 915、`GraphExplorerTab.tsx` 810（K-58） | 按"容器/展示/表格/表单"四段拆分，每 Tab 独立文件 |
| 禁硬编码中文 | 256 处/18 文件（K-57），而 i18n 已有 1,533 键（K-61） | 逐文件替换为 `t("knowledge.*")`；新增键走单源 json |
| 禁硬编码 Tailwind 色 | 155 处/16 文件 | 改 `useTheme()` token；4 主题截图验证 |
| 图标仅 lucide-react | 图谱手写 SVG（K-59 内嵌自绘） | 图形改 `@xyflow/react`，图标统一 lucide |
| 禁 localStorage 当库 | 4 组（K-57） | 改服务端持久化或 `sessionStorage` 仅限 UI 偏好 |
| 移动端复用 | 0 命中（K-60） | 复用 `useMediaQuery`/`useMobileSidebar`/`MobileDataTable` |
| 无障碍 | aria 9 处/3 文件 | 树/表格/对话框补 `aria-*`；键盘可达 |
| API 层唯一 | `knowledgeApi.ts` 1,233 行/85 导出、三 base 别名（K-55） | 按 Tab 拆 `api/{overview,assets,extract,graph,wiki,govern}.ts`，base 单源；auth 头只走 `services/auth.ts` |
| 枚举单源 | 前端三处漂移（K-64） | 由 OpenAPI 生成类型（分册 00 契约链），禁手写副本 |

---

## 四、C 章 — 技术设计（控制流 / 数据流 / 降级 / 可观测）

### C.1 主控制流：接入 → 抽取 → 校验 → 审批 → 投影

```
[上传/接入]
   └─ KnowledgeDocIngestService ──(MinIO RAW: raw/unstructured/{domain}/{yyyy}/{mm}/{hash})──▶ 对象登记(datanet)
        │  ※ 禁 java.io.tmpdir 终存（K-37）
        ▼
[提交任务] ITaskManagementService.submitTask(taskType=KB_EXTRACT)      ← 铁律 §2.5-3（禁 Executors/CompletableFuture，K-38）
        ▼
[异步执行] KbExtractTaskExecutor
   ① 取本体快照 kb_ontology_snapshot（分册 03 生产；缺快照 → ECOS-KB-011 拒绝，不降级为"无约束抽取"）
   ② 切片 + 字段映射 fieldMappings
   ③ C1 类型存在性 → C2 关系合法性 → 【C3 属性完整性（F04-05，新增）】 → C4 口径一致性
   ④ 存在性预查（同一集合算 created/updated，dry-run 与真执行共用 —— KB-05）
   ⑤ dry-run? ── 是 ─▶ 仅出报告（不写库、不动水位线）
              └ 否 ─▶ 节点/边 upsert（MyBatis，schema 限定 + 应用侧 UUID）
   ⑥ 图谱双形态投影：PG 权威写 → 若 ecos.kb.graph.neo4j.enabled=true 则 KGWriterService 投影 + 计数对账
   ⑦ 审计事件 EventBusService.publish(ecos.audit, {traceId, actor, domain, action, counts})  ← 非阻塞；底座不可用→本地兜底；兜底亦失败→回滚并拒绝（§2.4-6）
   ⑧ 水位线 kb_extract_watermark 仅在真执行成功后推进
   ⑨ 报告落库 + 任务进度回写（progress/created/updated/issues[]/c3Skipped/vectorDegraded）
```

**关键不变式**：
- I-1 抽取的一切写动作都在 SEMANTIC 定义态/实例态内，不写 CURATED/APPLICATION（B-1）；
- I-2 dry-run 与真执行的 `{created,updated,issues}` 差异只允许来自"实际写库"动作本身（REQ-KB-05）；
- I-3 任一 WARN 级 issue 不阻断写入（对齐既有裁定），但**不允许静默**：必须出现在报告且 UI 可见；
- I-4 审计事件缺失 ⇒ 该次写操作视为未完成（fail-closed）。

### C.2 RAG 读控制流（安全裁决前置）

```
Question
  ▼ ① SecurityDecisionService.decide(principal, resource, READ)     ← 默认 DENY（§2.4-6），不可用即 403
  ▼ ② QueryEmbeddingHelper（经 llm-gateway，禁自写 RestTemplate，K-35）→ queryVector
  ▼ ③ 召回：向量(<=>，需 pgvector) ∧ 关键词(ILIKE→改可移植全文，K-24) 混合
        └ pgvector 不可用 → JSONB 回退 + vectorDegraded=true + 原因/影响（显式，K-33）
  ▼ ④ CLS 列级过滤（security-engine 返回可见列集合）
  ▼ ⑤ 脱敏 mask（敏感列按策略；派生统计量按 R-9 ① 逐列登记 ST03-A 豁免）
  ▼ ⑥ AnswerGenerationHelper（经 llm-gateway）生成，附引用来源（source id + profile/assumption version）
  ▼ ⑦ 审计：检索行为发 ecos.audit（含 traceId、命中数、降级标记）
```

**要点**：③④⑤⑥ 任一步失败都必须返回带错误码的失败响应，禁止 `catch → emptyMap`（K-51 cognitive 侧同型问题，本册 kb 侧一并禁绝）。

### C.3 数据流：事实 → 画像 → 假设 → 预测快照（REQ-KB-02/03 供数链）

```
SOURCE(外部库) ──采集──▶ RAW(MinIO) ──管道/DQ──▶ CURATED(ecos_dw, dq_status=PUBLISHED)
                                                        │ 只读（禁直读 RAW，I-5）
                                                        ▼
                                          [画像生成任务 F04-06]
                                             分组统计 P10/P50/P90/CI + 分层退化
                                                        ▼
                                   SEMANTIC 知识资产：ecos_kb_profile（版本化，DRAFT→PUBLISHED）
                                                        │
                          [假设库 F04-07] ecos_kb_assumption（ACTIVE 才可引用）
                                                        ▼
                                    预测运行快照（PRD-09 FC-02）冻结 profile_version + assumption_version
                                                        ▼
                              APPLICATION(ecos_dw 预测结果/回测指标) —— 经 data-engine 写通道（ADR-14）
```

- I-5 kb 读事实只经 datanet REST 或 runtime-access 只读查询，**禁**直连外部源、禁读 RAW（B-1）；
- I-6 预测复现只靠"版本号引用"，故 PUBLISHED 画像/假设内容不可变（F04-06/07 的 409 拒改即为此）；
- I-7 画像数值是**知识制品**不是业务事实 → 不违"cognitive 零新增业务事实表"；但落点 schema 与写通道属 **R-8** 争议项。

### C.4 数据流：本体事件 → 快照 → 实例图谱

```
Kafka ecos.ontology（分册 03 发布事件）
   ▼ EcosOntologyEventConsumer（真实监听；纠正"注释谎称有 @KafkaListener 实则零命中"K-5）
   ├─ 版本快照落 kb_ontology_snapshot（含 content_hash —— 现有 placeholder sha256 必须换真摘要，:203-207）
   ├─ 映射变更触发受影响实例的重抽取建议（不自动写，出提案）
   └─ 事件幂等：按 (eventId, snapshot_version) 去重；重复事件不产生新快照
```

### C.5 两态寻址与承流（ADR-15，本册 S3 前置清单）

`ecos.route.kb = monolith | service`。**实测 `ServiceEndpointResolver` 零命中**（分册 00 W 系列已立项）；当前 7 处 `@Value` 默认 `localhost:xxxx` + 2 处硬编码 + 4 处自环（kb 调自身经 gateway 回环，`dccheng/application.yml:78-80` 注释自陈在规避）。

切流到 `service` 态（S3）之前必须完成：
1. `ServiceEndpointResolver` 落地并替换全部裸 `@Value` localhost（分册 00 供方）；
2. kb 的 21 Controller 在 `:18086` 真正承流（dccheng 制品空心，K-1/K-6）；
3. `@ComponentScan`/`@MapperScan` 与真实包对齐（K-2、`.dao` 零命中）；
4. 自环调用消除（上传/回调不再绕 gateway）；
5. 权限/审计链路在 service 态可复现（K-41/K-44 先修）。

在此之前，本册所有设计**按 monolith 态（gateway 宿主）写作**，service 态仅保留开关。

### C.6 降级矩阵（9 行，全部显式）

| 依赖不可用 | 检测点 | 降级行为 | 必须显式标记 | 禁止行为 |
|:--|:--|:--|:--|:--|
| security-engine | decide 调用异常 | **拒绝（DENY）** | `ECOS-KB-020` | 默认放行 / 返回空数据（K-44/K-51） |
| runtime-event | bean 缺失/publish 失败 | 本地兜底文件写；兜底失败 → 回滚并拒绝 | `auditDegraded=true` | `log.warn` 后继续（K-44） |
| llm-gateway | 底座健康/超时 | 抽取只走规则映射（无 LLM 增强）；RAG 返回 503 | `llmSkipped=true` | 自写直连 LLM（K-35） |
| Neo4j | `ecos.kb.graph.neo4j.enabled=false` 或连接失败 | PG 表形态为唯一图谱形态 | `graphForm="PG_ONLY"` | 返回 `ready_to_sync` 伪成功（K-29） |
| pgvector | `PgVectorSupport` 探测失败 | JSONB 回退检索 | `vectorDegraded=true`+原因 | 仅 `log.warn`（K-33） |
| MinIO | 上传失败 | 任务置 FAILED，不入草稿 | `ECOS-KB-030` | 落 `java.io.tmpdir` 继续（K-37） |
| CURATED 供数（datanet） | 画像取数失败 | 任务 FAILED + taskId 可查 | `ECOS-KB-040` | 用示例数值兜底 |
| 本体快照缺失 | `kb_ontology_snapshot` 无可用版本 | 拒绝抽取 | `ECOS-KB-011` | 无约束自由抽取 |
| runtime-task | submitTask 失败 | 同步返回 503，不本地起线程 | `ECOS-KB-021` | `Executors` 自建（K-38） |

### C.7 可观测性

- **traceId**：沿用分册 00 `ApiResponse.traceId`；本册在 `kb_extract_audit`、`kg_sync_log`、`ecos_kb_profile`、`ecos_kb_assumption` 增加 `trace_id VARCHAR(64)` 列（E.2）实现"UI 报错 → 日志/表行"双向定位。
- **指标**（runtime-monitor）：`kb_extract_created_total`、`kb_c3_issue_total{code}`、`kb_graph_sync_reconcile_diff`、`kb_vector_degraded_ratio`、`kb_audit_publish_fail_total`、`kb_profile_sample_insufficient_total`、`kb_route_4xx_masked_total`（404 掩蔽计数，用于证明 F04-03 生效）。
- **禁止**：以 `log.info("AUDIT topic=…")` 作为审计证据（K-30/K-45）；QA 验收必须查 Kafka 落 topic 或兜底文件。

### C.8 三层纪律（后端规范 §五）

| 层 | 允许 | 本册整改点 |
|:--|:--|:--|
| Controller | 参数校验 + 调 Service + 组装响应 | 删除 51 处 `catch (Exception)` 吞异常（K-46）；删除控制器内 `CompletableFuture`（K-38）；方法级路径禁全路径（K-11） |
| Service | 事务边界 + 领域规则 + 经底座调用 | **禁 JdbcTemplate 直连**；禁 `new RestTemplate()`（K-36）；禁反射 send（K-44）；画像统计计算为本册唯一允许的纯算法 Service |
| Mapper/DAO | SQL + schema 限定表名 | 清 16 处 `::`、14 处 `ON CONFLICT`、补 `databaseId` 方言分支（K-24）；裸表名清零（K-26） |

---

## 五、D 章 — 接口设计

### D.1 端点族表（`†`=结构缺陷必改，`*`=本册新增，`‡`=属主迁移）

| 族 | 现有前缀 | handler 数 | 承载 | 处置 |
|:--|:--|:--:|:--|:--|
| 导航分类/标签 | `/api/v1/knowledge/nav/**` | 19（`NavTaxonController.java:59`） | kb | † 依赖缺失表（F04-04）+ 错误语义（F04-03） |
| 文章 | `/api/v1/knowledge/articles/**` | 多 | kb | 分页强制 limit |
| 知识列表 | `/api/v1/knowledge-bases/**`（`KnowledgeListController.java:27`） | 少 | kb | † 补 bare 映射（K-14）；未用形参清理（K-43） |
| 引擎健康 | `/api/v1/engine/knowledge`（`KbEngineHealthController.java:20`） | 1 | kb | 保留，扩双形态/底座健康字段 |
| 抽取 | `/api/v1/knowledge/extract/**` | 多 | kb | 新增 `dry-run` 契约测试联动（F04-09） |
| 设置 | `/api/v1/knowledge/settings/**`（`KnowledgeSettingsController.java:85`） | 多 | kb | †† 路径拼接畸形，必改（F04-02） |
| 实体链接 | `/api/v1/knowledge` + `/entity/link`（`EntityLinkController.java:21`） | 少 | kb | †† 组合畸形，必改（F04-02）；`EntityLinkerService.java:86-98` `{:.2f}` 非法占位符 + `MAPS_TO` 注释无实现 |
| 生态图谱 | `/api/v1/knowledge/ecos-graph`（`EcosKnowledgeGraphController.java:26`） | 多 | kb | `‡` gateway 同名死类删除（K-13） |
| 图谱同步 | `/api/v1/knowledge/graph/sync/**`（`GraphSyncController.java:135`） | 多 | kb | 伪成功清零（F04-10）+ 真审计（F04-15） |
| 文档接入 | `/api/v1/knowledge/ingest/**`（`KnowledgeIngestController.java:315`） | 多 | kb | 假审计清零；上传走 MinIO |
| 合规/专家规则 | `/api/v1/knowledge/rules/**` | 多 | kb | 夹具数据处置（K-21 → R-12） |
| 场景知识工作台 | `/api/v1/knowledge/**`（`KnowledgeWorkbenchController.java:40`） | 多 | **workspace** | `‡` 迁 `/api/v1/scenarios/knowledge/**`，旧路径 `Deprecation`（F04-01） |
| 画像 | — | — | kb | `*` 见 D.3 |
| 假设 | — | — | kb | `*` 见 D.3 |
| 认知契约（只读） | `/api/v1/knowledge/cognition/**` | — | kb | `*` 只读开放，禁写代理（REQ-KB-04） |

> 其余族（向量化、Wiki、问答、血缘、专家、目录等）沿用现状，逐项清单以 `grep -rn '@RequestMapping("/api/v1/knowledge' ecos_backend | sort` 复现，改造范围以 F04-01/02/03 为准。

### D.2 契约冻结规则

1. 既有路径与参数签名**不可变更**（只增不改）；
2. 迁移路径（`‡`）保留旧路径 ≥2 个版本，响应加 `Deprecation: true` 头 + `Link: <新路径>; rel="successor-version"`；
3. 所有错误响应统一 `ApiResponse{code,message,traceId,details}`，错误码见 D.5；
4. 列表端点必须有 `limit/offset`（默认 limit=50，上限 500），禁止全量拉取（K-55）；
5. kb 不提供任何 cognitive 写代理端点（守护 REQ-KB-04）。

### D.3 关键新增契约

```
POST /api/v1/knowledge/profiles/generate
  body {metricCodes:[…], dims:{…}, windowFrom:"2025-01", windowTo:"2026-12", dryRun:false}
  202 → {taskId, status:"SUBMITTED", traceId}          ← 经 ITaskManagementService，非本地线程
  503 → ECOS-KB-021（runtime-task 不可用，禁降级为同步计算）

GET  /api/v1/knowledge/profiles?metric=&dims=&status=&page=&size=
POST /api/v1/knowledge/profiles/{id}/publish            → 403 ECOS-KB-062（非知识管理员）
GET  /api/v1/knowledge/profiles/resolve?metric=&dims=&atVersion=
  200 → {profileId, profileKey, profileVersion, confidence, degradeChain:[…], stats:{p10,p50,p90,mean,ciLow,ciHigh,sampleCount,missingRate,statsMethod}, traceId}

POST /api/v1/knowledge/assumptions           201 → {id, assumptionVersion, status:"DRAFT"}
GET  /api/v1/knowledge/assumptions?scenarioType=&metric=&status=
POST /api/v1/knowledge/assumptions/{id}/submit|approve
GET  /api/v1/knowledge/assumptions/resolve?scenarioType=&metric=&dims=&period=
  200 → {items:[{id, value, valueSemantic, baseAssumptionId, assumptionVersion, unverified, expireAt}]}
  409 → ECOS-KB-071（尝试修改 APPROVED 假设）
```

**画像发布响应（示例，字段与 E.2 列一一对应）**

```json
{"code":"0","message":"ok","traceId":"…","data":{
  "id":"0f4c…","profileKey":"M_REALIZATION_RATE|project_type=交付类|stage=ACCEPTANCE",
  "profileVersion":"1.2.0","status":"PUBLISHED","confidence":"HIGH",
  "degradeFrom":null,"degradeChain":["项目×部门×环节"],
  "stats":{"sampleCount":148,"missingRate":0.0132,"p10":0.51,"p50":0.74,"p90":0.93,
           "mean":0.7231,"ciLow":0.6902,"ciHigh":0.7560,"statsMethod":"T_APPROX"},
  "window":{"from":"2024-09","to":"2026-08"},"approvedBy":"u-1001"}}
```

### D.4 相邻册接缝（8 条）

| # | 接缝 | 供/需 | 契约 |
|:--|:--|:--|:--|
| S-1 | 分册 02 CURATED 事实 | 需方 | 只读 `dq_status=PUBLISHED`；经 datanet REST（C.3、I-5） |
| S-2 | 分册 02 data-engine 写通道 | 需方 | 画像/假设数值落 APPLICATION 时走此通道（ADR-14） |
| S-3 | 分册 03 本体快照 `kb_ontology_snapshot` | 需方 | 缺快照即拒绝（`ECOS-KB-011`）；C1~C4 属性定义来自本体 |
| S-4 | 分册 03 口径主权（R-6） | 需方 | kb 只引用口径编码，不定义口径 |
| S-5 | 分册 01 security-engine | 需方 | RLS/CLS/脱敏/ABAC/DENY；本册不自实现（B-4） |
| S-6 | 分册 01 审计链 | 需方 | `EventBusService` → `ecos.audit`（F04-15） |
| S-7 | 分册 05 cognitive | 供方 | 只读 hypothesis/belief/mind；枚举冻结；无写代理（REQ-KB-04） |
| S-8 | 分册 06 ai-engine / llm-gateway | 需方 | 唯一 LLM 入口（F04-13）；`/api/v1/llm/*` 不得被 kb 直连 |

### D.5 错误码（`ECOS-KB-*`）

| 码 | 语义 | HTTP | 引入原因 |
|:--|:--|:--:|:--|
| ECOS-KB-011 | 本体快照缺失/版本不可用 | 409 | C.1 ① |
| ECOS-KB-012 | 抽取字段映射不满足 C1/C2（硬失败子集） | 422 | 现有 C 系列语义化 |
| ECOS-KB-020 | 安全裁决不可用 → 拒绝 | 503 | K-44 纠正（原为静默继续） |
| ECOS-KB-021 | 任务底座不可用 | 503 | K-38 纠正 |
| ECOS-KB-022 | 事件底座不可用且兜底失败 | 500 | K-44 纠正 |
| ECOS-KB-023 | LLM 底座不可用（非降级路径） | 503 | K-35 收口后语义 |
| ECOS-KB-030 | 对象存储不可用（上传） | 500 | K-37 纠正 |
| ECOS-KB-031 | 上传文件类型/大小不合规 | 415/413 | 边界校验 |
| ECOS-KB-040 | CURATED 供数不可达（画像取数） | 502 | C.3 |
| ECOS-KB-041 | 画像样本不足且退化链穷尽 | 422 | F04-06 边界 |
| ECOS-KB-050 | 知识导航表缺失（kb_nav_*） | 500 | K-19，替代伪 404 |
| ECOS-KB-051 | 路由映射缺失（结构性） | 500 | K-11/K-12/K-14 |
| ECOS-KB-060 | 画像/假设状态机非法跃迁 | 409 | F04-06/07 |
| ECOS-KB-061 | 已发布资产不可变 | 409 | I-6 |
| ECOS-KB-062 | 权限不足（非知识管理员发布/审批） | 403 | K-41 |
| ECOS-KB-070 | 认知契约写请求被拒（无写代理） | 405 | REQ-KB-04 |
| ECOS-KB-071 | 尝试修改 APPROVED 假设 | 409 | F04-07 |
| ECOS-KB-072 | 假设已过期不可引用 | 410 | F04-07 |
| ECOS-KB-080 | 图谱形态不一致（PG↔Neo4j 对账差异） | 500 | F04-10 |

---

## 六、E 章 — 数据设计

### E.1 归属口径（先定性质，再定 schema）

| 数据物 | 湖层 | 目标 schema | 依据 / 争议 |
|:--|:--|:--|:--|
| 知识文章/条目/文档元数据 | SEMANTIC | `ecos_knowledge`（现 `public` 有实数据，K-17） | ST07 控制域五引擎 schema；随 R-1 口径"新写 schema 限定、存量只停写" |
| 图谱实例（节点/边） | SEMANTIC | PG 权威 `ecos_knowledge.kb_knowledge_graph_*` + Neo4j 投影（enterprise+） | 湖规双形态 + ADR-11 |
| 向量（embedding） | SEMANTIC | **只落业务域 `ecos_dw` 侧向量表**或 `ecos_knowledge` 内 pgvector 列 —— 但**禁入控制 schema 之外的越界 schema** | MC05"向量禁入控制 schema" 与"SEMANTIC 属控制域知识引擎"存在条文张力 → **R-8 附带议题，已按 R-10 ① 批准**（三载体收敛于知识层，禁把向量拉进业务域） |
| 历史画像 `ecos_kb_profile` | SEMANTIC（知识制品） | **R-8**：推荐定义态（key/dims/version/status）落 `ecos_knowledge`，数值统计事实落业务域 `ecos_dw` 经 data-engine 写通道 | B-3 + ADR-14 + ARCH_SPEC §4.3（kb 写权限清单未含 APPLICATION） |
| 情景假设 `ecos_kb_assumption` | SEMANTIC | `ecos_knowledge`（人工维护知识，多数值少） | B-3 |
| 抽取草稿 / 文档三载体 | RAW/SEMANTIC 边界 | **R-10** 收敛为单载体 | K-20 |
| 合规规则 `sys_compliance_rule` | 控制域 | `ecos_knowledge` 测试夹具处置见 **R-12** | K-21 |
| 认知三表（守护对象） | SEMANTIC | 真身 `public.ecos_cognitive_*`，文档名 `kb_cognitive_*` 不存在 | K-49（G2-6），需 PRD 回写 |

### E.2 合规 DDL（新增三脚本，单源目录 `ecos_backend/gateway/src/main/resources/db/migration/`）

**统一约束**（逐项对治实测违规）：主键 `VARCHAR(36)` 应用侧生成 UUID（对治 K-22 MC01）；JSON 一律 `TEXT`（对治 K-23 MC02）；不使用 `gen_random_uuid()`/`BIGSERIAL`/`JSONB`/`partial index`/`ON CONFLICT`（对治 MC01/MC02/MC03）；表名 **schema 限定**（对治 K-26 裸名）；基线列齐全 `create_time/update_time/create_by/update_by/version_no/is_deleted/domain` + `trace_id`（对治 K-25 DR06~DR08）。

```sql
-- V175__kb_nav_tables_conform.sql  （对治 K-19/K-22/K-23：kb_nav_* 缺失 + 违规形态）
CREATE TABLE IF NOT EXISTS ecos_knowledge.kb_nav_category (
    id            VARCHAR(36)  NOT NULL,
    parent_id     VARCHAR(36),
    name          VARCHAR(200) NOT NULL,
    description   TEXT,
    sort_order    INTEGER      NOT NULL DEFAULT 0,
    color_token   VARCHAR(64),
    domain        VARCHAR(64)  NOT NULL,
    trace_id      VARCHAR(64),
    version_no    INTEGER      NOT NULL DEFAULT 1,
    is_deleted    SMALLINT     NOT NULL DEFAULT 0,
    create_by     VARCHAR(64)  NOT NULL,
    update_by     VARCHAR(64)  NOT NULL,
    create_time   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_kb_nav_category PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_kb_nav_category_domain_parent ON ecos_knowledge.kb_nav_category(domain, parent_id, is_deleted);
CREATE UNIQUE INDEX IF NOT EXISTS uk_kb_nav_category ON ecos_knowledge.kb_nav_category(domain, parent_id, name, is_deleted);
-- kb_nav_tag / kb_nav_article_rel 同形态（tag: name/category_id/color_token；rel: article_id/tag_id/relation_type）
-- 可移植唯一性用"把 is_deleted 纳入唯一键"表达，不使用 PG 偏索引（WHERE is_deleted=0）
```

```sql
-- V176__kb_profile.sql  （REQ-KB-02 / F04-06；重写 PRD-04 §2.2 的违规形态）
CREATE TABLE IF NOT EXISTS ecos_knowledge.ecos_kb_profile (
    id                   VARCHAR(36)   NOT NULL,
    profile_key          VARCHAR(255)  NOT NULL,      -- 规范化：metric|k1=v1|k2=v2（键排序后拼接）
    metric_code          VARCHAR(64)   NOT NULL,      -- 对齐 PRD-03 指标编码（口径只引用不定义，S-4）
    group_dims_json           TEXT          NOT NULL,      -- JSON 文本（MC02：禁 JSONB）
    window_from          VARCHAR(7)    NOT NULL,      -- YYYY-MM
    window_to            VARCHAR(7)    NOT NULL,
    sample_count         INTEGER       NOT NULL,
    missing_rate         NUMERIC(5,4)  NOT NULL DEFAULT 0,
    p10                  NUMERIC(18,4),
    p50                  NUMERIC(18,4),
    p90                  NUMERIC(18,4),
    mean_value           NUMERIC(18,4),
    ci_low               NUMERIC(18,4),
    ci_high              NUMERIC(18,4),
    ci_level             VARCHAR(10)   NOT NULL DEFAULT '95',
    stats_method         VARCHAR(32)   NOT NULL DEFAULT 'T_APPROX',  -- T_APPROX/BOOTSTRAP
    confidence           VARCHAR(10)   NOT NULL,      -- HIGH/MEDIUM/LOW
    degrade_from         VARCHAR(36),                 -- 自引用：退化来源画像
    degrade_path_json         TEXT,                        -- 退化链（JSON 文本）
    applicable_condition VARCHAR(500),
    source_query_ref     VARCHAR(255),                -- 取数可复现（datanet 查询标识，S-1）
    profile_version      VARCHAR(20)   NOT NULL,
    status               VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',   -- DRAFT/PUBLISHED/SUPERSEDED
    approved_by          VARCHAR(100),
    approved_time        TIMESTAMP,
    task_id              VARCHAR(64),                 -- 生成任务反查（runtime-task）
    trace_id             VARCHAR(64),
    domain               VARCHAR(64)   NOT NULL,
    version_no           INTEGER       NOT NULL DEFAULT 1,
    is_deleted           SMALLINT      NOT NULL DEFAULT 0,
    create_by            VARCHAR(64)   NOT NULL,
    update_by            VARCHAR(64)   NOT NULL,
    create_time          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_ecos_kb_profile PRIMARY KEY (id),
    CONSTRAINT ck_kbp_status CHECK (status IN ('DRAFT','PUBLISHED','SUPERSEDED')),
    CONSTRAINT ck_kbp_conf   CHECK (confidence IN ('HIGH','MEDIUM','LOW'))
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_kbp_key_ver ON ecos_knowledge.ecos_kb_profile(profile_key, profile_version, is_deleted);
CREATE INDEX IF NOT EXISTS idx_kbp_metric_status ON ecos_knowledge.ecos_kb_profile(metric_code, status, is_deleted);
CREATE INDEX IF NOT EXISTS idx_kbp_domain_window ON ecos_knowledge.ecos_kb_profile(domain, window_from, window_to);
-- 金额/比率类统计量的 ST03 处置见 E.6（R-9）；若 R-8 选"数值事实落 ecos_dw"，
-- 则本表保留定义态 + stats_ref 外键，数值列迁 ecos_dw.ecos_kb_profile_stats（经 data-engine 写通道）
```

```sql
-- V177__kb_assumption.sql  （REQ-KB-03 / F04-07；同样重写 PRD-04 §3.1）
CREATE TABLE IF NOT EXISTS ecos_knowledge.ecos_kb_assumption (
    id                 VARCHAR(36)    NOT NULL,
    assumption_key     VARCHAR(255)   NOT NULL,       -- scenarioType|metric|dims 规范化键
    scenario_type      VARCHAR(32)    NOT NULL,       -- BASE/CONSERVATIVE/AGGRESSIVE/自定义
    metric_code        VARCHAR(64)    NOT NULL,
    dim_scope_json          TEXT           NOT NULL DEFAULT '{}',   -- JSON 文本（MC02）
    value_type         VARCHAR(10)    NOT NULL,       -- RATE/AMOUNT/DAYS
    value              NUMERIC(18,4)  NOT NULL,
    value_semantic     VARCHAR(20)    NOT NULL DEFAULT 'ABSOLUTE',  -- ABSOLUTE/DELTA
    base_assumption_id VARCHAR(36),
    valid_from         VARCHAR(7)     NOT NULL,
    valid_to           VARCHAR(7),
    reason             VARCHAR(500)   NOT NULL,       -- 必填（PRD-04 §3.2-3）
    evidence_ref       VARCHAR(255),
    is_unverified      SMALLINT       NOT NULL DEFAULT 0,   -- evidence_ref 缺失即 1
    expire_at          TIMESTAMP,
    assumption_version VARCHAR(20)    NOT NULL,
    status             VARCHAR(20)    NOT NULL DEFAULT 'DRAFT',  -- DRAFT/PENDING_APPROVAL/ACTIVE/EXPIRED/SUPERSEDED
    approved_by        VARCHAR(100),
    approved_time      TIMESTAMP,
    trace_id           VARCHAR(64),
    domain             VARCHAR(64)    NOT NULL,
    version_no         INTEGER        NOT NULL DEFAULT 1,
    is_deleted         SMALLINT       NOT NULL DEFAULT 0,
    create_by          VARCHAR(64)    NOT NULL,
    update_by          VARCHAR(64)    NOT NULL,
    create_time        TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time        TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_ecos_kb_assumption PRIMARY KEY (id),
    CONSTRAINT ck_kba_status CHECK (status IN ('DRAFT','PENDING_APPROVAL','ACTIVE','EXPIRED','SUPERSEDED')),
    CONSTRAINT ck_kba_sem    CHECK (value_semantic IN ('ABSOLUTE','DELTA')),
    CONSTRAINT ck_kba_vt     CHECK (value_type IN ('RATE','AMOUNT','DAYS'))
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_kba_key_ver ON ecos_knowledge.ecos_kb_assumption(assumption_key, assumption_version, is_deleted);
CREATE INDEX IF NOT EXISTS idx_kba_scenario_status ON ecos_knowledge.ecos_kb_assumption(scenario_type, status, is_deleted);
CREATE INDEX IF NOT EXISTS idx_kba_expire ON ecos_knowledge.ecos_kb_assumption(expire_at, status);
```

### E.3 存量收敛与迁移规划（`V175 ~ V186`，只加不删）

| 脚本 | 动作 | 对治 | 门禁 |
|:--|:--|:--|:--|
| V175 | 建 `kb_nav_*`（合规形态，schema 限定） | K-19/22/23 | `SchemaInventoryGateTest` |
| V176/V177 | 画像/假设新表 | K-48 | 状态机 CHECK + 唯一键 |
| V178 | 19 个 `jsonb` 列**增加同名 `_json` 列** + 双写迁移期（读写切 `_json` 后旧列停写）**【2026-09-30 勘误】**原字面 `_text` 违 DR04（JSON 语义列必加 `_json` 后缀）⇒ 以红线为准；脚本另纠正两处实测偏差：`kb_ontology_snapshot` 归属 schema = `ecos_knowledge`（非 public），`public.ecos_knowledge_graph_node.properties_json`（JSONB 但有 23 行数据 + `KnowledgeNodeRepository.java:15` 在读）从清单移除并登记为 MC02 存量偏差 | K-23 MC02 | `mvn -Dtest=Mc02JsonbRetirementTest#noJsonbReadRemaining` |
| V179 | `nextval`/BIGSERIAL 表**并建新表**（`id VARCHAR(36)`），旧表只停写 | K-22 MC01 | 新表合规 + 老表标记 knownLegacy |
| V180 | 补 `domain` 列（9/30 → 30/30）+ 补审计 5 列 + `version_no`/`is_deleted` | K-25 DR06~DR08 | `DrBaselineColumnsTest` |
| V181 | `kb_extract_audit`/`kg_sync_log` 增 `trace_id` | C.7 | 列存在性 |
| V182 | 孤儿列处置：`knowledge_embedding.article_id/chunk_text/model`、`knowledge_article.source_type/tags`（先补 Mapper 使用或标记停用） | 实测孤儿列 | 无 DROP |
| V183 | 双镜像 6 对：新写一律 `ecos_knowledge` 限定；`public` 侧只停写不迁（随 R-1 选项 a） | K-17 | `SchemaInventoryGateTest` |
| V184 | PK 宽度不一致（36↔64）并建新表统一 `VARCHAR(36)` | K-17 | MC01 |
| V185 | 向量：`ecos_dw` 侧 pgvector 表形态固化（`V137` 的"需手工执行"改为可自动执行的幂等脚本 + 探测降级列） | K-33/26 | `VectorFormDegradationTest` |
| V186 | 文档三载体：权威载体建/认（随 R-10），其余标 legacy 只停写 | K-20 | `DocCarrierConvergenceTest` |

**禁则**：知识 schema 现状零 DROP，本册亦**不新增 DROP**（除 R-12 明确批准的"零行/演示表重建"）；测试夹具 53 行（K-21）不 DELETE，改由 `domain`/`is_deleted` 逻辑隔离并标注来源。

### E.4 读写边界（本册执行表）

| 主体 | 读 | 写 | 禁止 |
|:--|:--|:--|:--|
| kb-engine | CURATED（只读，经 datanet/runtime-access）、SEMANTIC | `ecos_knowledge` 知识资产、图谱实例、向量、规则、画像/假设**定义态** | 直读 RAW；直写 APPLICATION（§4.3 未授予，**R-8**）；直改本体定义（分册 03 属主） |
| workspace 场景侧 | SEMANTIC（只读，REST） | 场景自有表（`/api/v1/scenarios/**`） | 写知识资产（须经 kb 端点）；持有计算主体 |
| cognitive | I/K/D 定位符（REST） | 模型资产 + 心智状态 | 反向写 business 表；写 kb 知识资产 |
| data-engine | — | 代 kb 写 APPLICATION 数值事实（若 R-8 选推荐项） | — |

### E.5 多库方言与单源

`ecos-sql/{postgresql,mysql,oracle}/05_ecos_knowledge.sql` 三套手写且落后 live（K-27）。**改为由单源迁移目录派生**（铁律 §3.1）：新增生成脚本口径 = "从 `db/migration/` 派生方言文件"，方言差异（`vector`/`JSON`/`CLOB`）集中在派生器；同时清除 Mapper 内 16 处 `::`、14 处 `ON CONFLICT` 并补 `databaseId` 分支（K-24），`ILIKE` 改可移植全文检索。

**验收**：`mvn -Dtest=SqlDialectDerivationTest#ecosSqlFilesMatchMigrationHead`、`mvn -Dtest=Mc03PortabilityTest#noBareCastOrOnConflictWithoutDatabaseId`。

### E.6 ST03 / ST03-A 登记

- 控制域列（`password`/`id_card`/`phone` 等）**永不可豁免**，画像/假设表不得复制此类列；
- 画像的 `p10/p50/p90/mean_value/ci_low/ci_high` 与假设的 `value`（`value_type=AMOUNT` 时）属**派生统计量**：不标识个人、但可能反推业务金额分布 → 是否逐列登记 ST03-A 属 **R-9**；
- 本册默认动作：在 ST03-A 登记表新增一行"知识域派生统计量列（`ecos_kb_profile.*_value/p*`、`ecos_kb_assumption.value`）"，登记为**说明行**（不加密、不豁免业务金额列规则）；**R-9 ① 已批准 ⇒ 本册逐列登记口径即为终态**；
- RAG 读路径的敏感列必须经 security-engine 脱敏后返回（C.2 ⑤），kb 侧不得自实现脱敏（B-4）。

---

## 七、F 章 — 符合性偏差登记（W90~W114 → 回填 ARCH_SPEC C72~C96）

> 依 ARCH_SPEC §十一 脚注纪律："后续各册实测发现的偏差继续追加 C72+，**不得只在分册内部登记而不回填本表**"。本表即回填源，回填动作随后执行。

| 本册 W | ARCH_SPEC C | 偏差（证据 K 编号） | 违反条款 | 收敛方向 |
|:--:|:--:|:--|:--|:--|
| W90 | **C72** | 三主体共用 `/api/v1/knowledge/**`（kb 21 Controller + workspace + gateway 死类），无前缀属主定义（K-7/K-8/K-13） | ARCH §4.3 读写边界、ADR-15 | 单一属主 + workspace 迁 `/api/v1/scenarios/knowledge/**`，旧路径 `Deprecation`（F04-01） |
| W91 | **C73** | `vite.config.ts:25` 把 knowledge 前缀 dev 直连 **18084（ai-engine）**，kb 实为 18086（K-15） | AGENTS.md 代理双轨、ADR-15 | 目标端口修正为 18086 + 配置注释声明仅 dev 生效 |
| W92 | **C74** | 类级前缀 + 方法级全路径拼出 `/api/v1/knowledge/settings/api/v1/knowledge/…`；`EntityLinkController` 组合畸形；`knowledge-bases` 无 bare 映射（K-11/K-12/K-14） | 后端规范 §五 Controller 纪律、API 只增不改 | 方法级相对路径 + 补重写清单；`KnowledgeRouteIntegrityTest` 源码级断言 |
| W93 | **C75** | `kb_nav_category/tag/article_rel` 迁移 V155~V157 存在但 **live 库无表**，19 handler + 整块前端导航 UI 打空（K-19） | PRD-01 DB-03 存在性预检 | 合规建表 `V175` + `SchemaInventoryGateTest` 前置门禁（F04-04） |
| W94 | **C76** | **三级掩蔽链**：`GlobalExceptionHandler:186-201` Exception→404 + 控制器 `catch(Exception)` 51 处/21 文件 + 前端 9 组死调用 `catch→空态`（K-46/K-56） | 铁律 §2.4-6 精神（不可静默）、文档/测试真实性裁定（Q13） | kb 局部 advice + 错误码表（D.5）+ 前端故障态与空态分离（F04-03） |
| W95 | **C77** | 同名双 Controller：gateway `EcosKnowledgeGraphController` 已被 `GatewayApplication:87` 排除仍留存源码（K-13） | 代码可维护性、ADR-15 | 删死类，保留 kb 侧唯一实现 |
| W96 | **C78** | 文档/切片三载体：`public.extraction_drafts` / `ecos_knowledge.kb_doc*` / `V139 ecos_dw.doc+doc_chunk`（K-20） | 湖规 v2.0 分层归属、ADR-13 | 权威载体唯一化（**R-10**），其余只停写（F04-12） |
| W97 | **C79** | `syncToNeo4j` 返回 `status="ready_to_sync"` 伪成功 + `log.info("AUDIT topic=…")`（PG 218 节点 vs Neo4j 44）（K-29/K-30） | 湖规双形态、铁律 §2.4-5 审计 | 真写或显式降级 + PG↔Neo4j 计数对账（F04-10） |
| W98 | **C80** | Neo4j 残留演示数据 44 节点 / **0 关系**（labels `S10_TEST`/`Invoice`/`Order`），无清理或标注口径（K-28） | 数据可信性、验收真实性 | 清理或显式标 demo 命名空间（**R-11**） |
| W99 | **C81** | pgvector 已建 `vector(1536)`+HNSW 但表 0 行、`V137` 自陈"需手工执行"；`PgVectorSupport` 探测失败仅 `log.warn`，JSONB 回退不标记（K-33） | 湖规"降级须显式标记"、ADR-11/MC05 | 幂等自动脚本 + `vectorDegraded` 入响应/报告/UI（F04-11） |
| W100 | **C82** | 配置借键 `RuleGraphService:46 ${cognitive.neo4j.switch-on-write:}`（dccheng yml 未定义）+ `application.yml:78-80` 自环规避注释（K-32） | 铁律 §十 配置单源 | 本域键 `ecos.kb.graph.neo4j.switch-on-write` + 消除自环（C.5） |
| W101 | **C83** | LLM 绕底座：`AnswerGenerationHelper`、`QueryEmbeddingHelper` 自写 RestTemplate 直打 `/api/v1/llm/*`；`new RestTemplate()` ×2（K-35/K-36） | 铁律 §2.5-2、§"禁 new 绕过装配" | 全量改经 llm-gateway + ArchUnit 新规则（F04-13） |
| W102 | **C84** | 上传落 `java.io.tmpdir + "/ecos-extractions"` 终存，绕 MinIO/RAW（K-37） | 湖规 RAW 层唯一入口、铁律 §2.5-1 | 走 `KnowledgeDocIngestService` 合规链；tmp 仅缓冲 + TTL（F04-12） |
| W103 | **C85** | 调度双轨：`Executors.newSingleThreadExecutor`、控制器 `CompletableFuture`、本地 `@Scheduled` 与 runtime-task 并存（K-38/K-39） | 铁律 §2.5-3 | 一律 `ITaskManagementService.submitTask/executeTask`（F04-14） |
| W104 | **C86** | 审计反射绕过 + 12 处 log 假审计（`KnowledgeNavSecurityEngineClient:88/98-102` bean 缺失仅 warn 后继续 = 静默丢审计）（K-44/K-45） | 铁律 §2.5-7、§2.4-6、ST06 | 经 `EventBusService`；兜底必写；兜底失败即拒绝（F04-15，与 C71 同规则扩 kb 包） |
| W105 | **C87** | 隔离缺位：kb 主源码 `TenantContextHolder`/`@RequirePermission` 零命中；`create_by='current-user'` 常量、`tenantId=default`/`role=user` 硬编码；`domain` 过滤仅 5 处（K-41/K-42/K-43） | 铁律 §2.4-1/4、DR06~DR08 | 安全裁决前置 + 真实 actor/domain 落库（F04-16） |
| W106 | **C88** | **MC01**：知识域仅 3 表合规；14 列 `nextval`；`V155:20 DEFAULT gen_random_uuid()`；`NavTaxonServiceImpl:152 gen_random_uuid()::text`；双镜像 PK 宽度 36↔64（K-22/K-17） | MC01 | 新表 `VARCHAR(36)` 应用侧 UUID；老表并建新表停写（V179/V184） |
| W107 | **C89** | **MC02**：知识域 19 个 `jsonb` 列（K-23） | MC02 | 并建 `_json` 列 + 双写切读（V178；2026-09-30 勘误：原字面 `_text` 违 DR04 后缀红线，已收口） |
| W108 | **C90** | **MC03**：`::` 16 处/7 文件、`ON CONFLICT` 14 处、`databaseId` 零命中、`ILIKE` 广泛（K-24） | MC03 多库兼容 | 方言分支 + 可移植写法（E.5） |
| W109 | **C91** | **DR06~DR08 无一表合规**；`domain` 仅 9/30 表（K-25） | DR06~DR08、§五 三层纪律 | V180 补齐基线列 + 门禁 |
| W110 | **C92** | 双镜像 6 对实数据全在 `public`；`V7/V100/V104/V116/V117/V118` 裸名建表；`ecos-sql` 三方言手写且落后 live；`sys_compliance_rule` 53 行为测试夹具（K-16/K-17/K-26/K-27/K-21） | §四 附则1 裸表名=FAIL、铁律 §3.1 单源、ST07 | schema 限定新写 + 派生器 + 夹具逻辑隔离（**R-12** 处置） |
| W111 | **C93** | 主责 REQ 零实现：KB-01（C3 三码 + `c3_enabled` 全仓 0 命中）、KB-02/03（画像/假设 0 命中）；且 **PRD-04 `:57/:119` 自带违 MC01/MC02 的 DDL**（K-47/K-48） | PRD-04 全部 P0/P1 主责项 | 本册 E.2 合规重写 + F04-05/06/07 从零建 + PRD 回写 |
| W112 | **C94** | REQ-KB-04 守护表名 `kb_cognitive_hypothesis/belief/kb_mind_registry` **不存在**，真身 `public.ecos_cognitive_*`（V127~V129）；`kb_mind_registry` 仅存于文档（K-49，= G2-6） | PRD-00/01/04/05 + ADR-8/9 一致性 | 文档名↔真身映射 + PRD 回写（不改库名，存量只停写） |
| W113 | **C95** | 门禁盲区与条文挪用：kb `ArchitectureTest:95-101` 漏 `engine.ontology`（实测 import 10 处，含 `kb-engine-api/model/ComplianceRule.java:3` 对外契约耦合）；cognitive `KbRestClientConfig` javadoc 把 `catch→emptyMap` 标为"§2.4-6"，该条实为默认 DENY（K-51） | 铁律 §2.4-6/7、ARCH 规则 | ArchUnit 补枚举 + api 模块解耦 + 条文引用订正（F04-18） |
| W114 | **C96** | 前端知识域规范违例集：孤儿副本 8 文件 1,988 行、3 死页、3 入口 3 路由名、`knowledgeApi.ts` 1,233 行/85 导出/无 limit、手写 SVG O(n²) 而 `@xyflow` 已装未用、硬编码中文 256 处 + 色板 155 处、组件超限 915/810、移动端与 aria 近零、40 文件仅 3 有测试（K-52~K-64） | 前端规范 §六/§七/§八/组件 ≤800、Q13 验收真实性 | B.0 IA 归一 + B.9 逐条清零 + Playwright 4 主题 × 6 Tab（分册 08 联动） |

### 7.1 PRD / 规则回写清单（本册发现，须由需求侧订正）

| # | 目标文件 | 回写内容 | 原因 |
|:--|:--|:--|:--|
| P-1 | `PRD-04-kb-engine需求规格-2026-09-28.md` §2.2 / §3.1 | DDL 删 `DEFAULT gen_random_uuid()`（改应用侧 UUID）与 `JSONB`（改 TEXT）；补基线列 + `trace_id`/`task_id`/`source_query_ref`；唯一索引纳入 `is_deleted` | 违 MC01/MC02/MC03/DR06~DR08（W106~W109） |
| P-2 | PRD-04 §四（REQ-KB-04）+ PRD-00/01/05 + ADR-8/9 | 认知三表统一改为真身名或登记"文档名↔物理名"映射 | K-49 / G2-6 |
| P-3 | PRD-04 §2.4 生成端点 | 明确"必走 `ITaskManagementService`，禁本地线程"，补 503/ECOS-KB-021 语义 | W103/W101 |
| P-4 | `.trae/rules/数据湖存储分层规范.md` 第 53 行 | 引用 `POST :18086/api/v1/kb/graph/sync` **实测零映射**（真身为 `/api/v1/knowledge/graph/sync`，且承流在 gateway） | K-6/K-8，规则侧引用失真 |
| P-5 | ARCH_SPEC §4.3 kb 行 | 写权限清单缺"画像/假设数值落 APPLICATION"通道 → **R-8 ② 已批准，本行待补入 ARCH_SPEC（任务 #26 未完项）** | B-1 vs C.3 缺口 |
| P-6 | `engine/kb/kb-engine-impl/pom.xml:86-92` | 删"有 `@KafkaListener`"失真注释；死依赖 `sysman-impl`（`:83`）与 `spring-kafka` 按事件底座收敛结论处置 | K-4/K-5 |
| P-7 | `services/dccheng/DcchengServiceApplication.java:20-23` | 类注释"需与 cognitive 同 JVM"与 `@ComponentScan` 不含 cognitive2 矛盾，须订正或补齐 | K-3 |
| P-8 | `dccheng/application.yml:48` | `mapper-locations: classpath*:mapper/*.xml` 但 XML **零命中**（纯 MyBatis 注解制），删无效配置 | 实测 |
| P-9 | `dccheng/application.yml:74` | `llm-gateway-base: ${ECOS_RAG_LLM_BASE:}` 空默认值 → 必填或显式降级，禁空串静默 | K-35 |
| P-10 | `ModuleDependencyArchTest.java:413-415` | 注释"天然合规，无需豁免"属结构性盲区（反射绕过不被类型引用检测覆盖） | K-51 / C71 同型 |
| P-11 | 前端 `typesAndConstants.ts` | 状态/类型枚举改由 OpenAPI 生成，删三处手写漂移副本 | K-64 / W114 |

### 7.2 本册裁决项 R-8 ~ R-12（登记于需求检视报告 §13.3；**已于 2026-09-29 §十四.1 全量批准**）

| # | 议题 | 事实 | 选项 | 本设计推荐 |
|:--|:--|:--|:--|:--|
| **R-8** | 画像/假设的落点与写通道 | ARCH_SPEC §4.3 授予 kb 的写清单为"图谱实例、向量、规则"，**不含** APPLICATION；而 REQ-KB-02 画像含数值统计、预测快照要引用；B-3 又定性画像为 SEMANTIC 知识制品 | ① 全表（含数值）落 `ecos_knowledge`，承认"知识制品可含数值"扩展 §4.3；② 定义态落 `ecos_knowledge`，数值统计落 `ecos_dw` 经 data-engine 写通道；③ 全部落 `ecos_dw` 按业务域处理 | **②**（守住"数值事实在业务域 + ADR-14 写通道"，同时保版本/审批等知识语义在 K 层；代价：resolve 需跨两 schema 组装） |
| **R-9** | 派生统计量列的 ST03 处置 | `p10/p50/p90/mean/ci_*` 与 `assumption.value(RATE/AMOUNT)` 可反推业务金额分布，但非个人敏感 | ① 登记为 ST03-A 说明行（不加密、不豁免业务金额列）；② 视同业务金额列逐列加密；③ 完全豁免不登记 | **①**（默认已按此写 E.6；选 ② 会使知识层读路径承担解密开销且破坏画像可分享性） |
| **R-10** | 文档/切片三载体收敛方向 | `public.extraction_drafts`(4) / `ecos_knowledge.kb_doc*` / `V139 ecos_dw.doc+doc_chunk`（K-20） | ① 权威 = `ecos_knowledge`（知识层），`ecos_dw.doc*` 定性演示只停写，`extraction_drafts` 降为草稿态；② 权威 = `ecos_dw`（业务域文档事实），知识侧只存引用 | **①**（文档是被加工为知识的原料与制品，属 K 层；②会把切片/向量拉进业务域，与 MC05 冲突） |
| **R-11** | Neo4j 演示残留处置 | 44 节点 / 0 关系、labels 全为 `S10_TEST`/`Invoice`/`Order`（K-28），与 PG 218 节点完全不成对 | ① `DETACH DELETE` 清空后由真实同步重建；② 保留但打 `demo=true` 属性并在对账中排除；③ 保持现状仅在对账报告标注 | **② 已批准**（存量动作纪律：不擅自删库；`demo` 标记使对账可自动化，且 enterprise 档切流时不会被演示数据污染验收。**执行属待授权项**，报告 §14.4） |
| **R-12** | 零行/夹具/违规形态表是否允许 DROP 重建 | `ecos_knowledge` 内 14 张 0 行；双镜像引擎侧全 0 行；`sys_compliance_rule` 53 行系测试夹具；14 列 `nextval` + 19 个 jsonb 列改造成本高（K-17/K-18/K-21/K-22/K-23） | ① 零行且零代码引用表允许 DROP 后按合规 DDL 重建（前置=备份 + 引用扫描）；② 一律不 DROP，只并建新表（V178/V179 路线）；③ 仅夹具行 DELETE | **① 限定于"零行 + 零引用"两条件同时成立**，其余走 ②；夹具行走逻辑隔离不 DELETE（尊重"存量只定性不擅删"） |

**裁决影响面**：R-8 → 本册 E.1/E.2/E.4 + ARCH_SPEC §4.3 + 分册 09 FC-02 快照引用；R-9 → ST03-A 登记表 + C.2 ⑤；R-10 → 本册 F04-12/V186 + 分册 02 E.4 载体表；R-11 → F04-10 对账口径 + 分册 08 图谱 UI；R-12 → V178/V179/V184 路线选择与工期。

---

## 八、追溯矩阵与门禁状态

### 8.1 REQ → 设计 → 验收 → 偏差 四向追溯

| 主责 REQ | 优先级 | 实现度现状 | 本册设计项 | 验收标识（可执行） | W/C |
|:--|:--:|:--|:--|:--|:--|
| REQ-KB-01 C3 校验 | P1 | **0%**（K-47） | F04-05、C.1 ③ | `C3AttributeValidationTest#missingRequiredProducesWarnButStillWrites` 等 4 法 | W111 / C93 |
| REQ-KB-02 历史画像 | **P0** | **0%**（K-48） | F04-06、C.3、E.2 `V176`、D.3 | `KbProfileGenerateTest#…` 4 法 + `KbProfileResolveTest#resolveReturnsFullDegradeChain` | W111 / C93（+R-8/R-9） |
| REQ-KB-03 假设库 | **P0** | **0%**（K-48） | F04-07、E.2 `V177`、D.3 | `KbAssumptionLifecycleTest` 4 法 | W111 / C93 |
| REQ-KB-04 认知契约守护 | **P0** | 守护对象名失真（K-49） | F04-08、D.4 S-7 | `CognitionContractGuardTest#guardTablesResolveToRealRelation`；`ScenarioCognitionIntegrationTest` 4 法（待分册 05 E1~E4） | W112 / C94 |
| REQ-KB-05 dry-run 守护 | P1 | 逻辑在、零测试（K-50） | F04-09、C.1 ④⑤⑧ | `KbDryRunContractTest#dryRunMatchesRealExecutionOnCreatedUpdatedIssues`、`#dryRunLeavesWatermarkAndRowCountUnchanged` | — / 关联 C93 |

### 8.2 跨册依赖与批次

| 项 | 依赖 | 被依赖 | 批次 |
|:--|:--|:--|:--|
| F04-01/02/03（路由与掩蔽） | 分册 00（重写清单单源）、分册 01（`GlobalExceptionHandler` 改造） | 分册 08 前端全部知识页可寻址 | **M0（P0，先行）** |
| F04-04 + V175 | 分册 02 `SchemaInventoryGateTest` 形态 | wiki Tab | M0 |
| F04-05 | 抽取主流程实体定义（分册 03） | 抽取报告 UI | 分册 03 G5 同期 |
| F04-06/07 + V176/V177 | 分册 02 DATA-01/02（PUBLISHED 事实）、分册 03 指标/口径编码、R-8/R-9 裁决 | 分册 09 FC-02 区间与缺省参数 | 场景批次 A |
| F04-08 | 分册 05 E1~E4 交付 | 场景工作台 | 分册 05 收口后 |
| F04-09 | — | 抽取契约测试（PMO-73 G3） | **G3 同期** |
| F04-10/11（双形态） | ADR-11、R-11 裁决、runtime-access Neo4jClient | 图谱 UI、向量检索 | M1 |
| F04-12（文档载体） | R-10 裁决、分册 02 MinIO/RAW | 抽取与知识资产 | M1 |
| F04-13/14/15/16（底座与安全） | 分册 01 审计链、llm-gateway、runtime-task | 全部写路径 | **M0（安全项）/ M1（其余）** |
| F04-17/18 | 分册 08 前端框架、OpenAPI 类型生成 | 用户可见交付 | M2 |

### 8.3 门禁状态

**门禁状态**：第一门（模块划分 + 技术选型）与**第二门均已通过**（凭证 = 需求检视报告 §十四.3，用户批准 2026-09-29，`deliverable_allowed=true`）。以下三项为第二门原审表态项，逐条结论：
1. **R-8 ~ R-12** 五项**已按本册推荐项全部批准**；本册 DDL 已按合规形态写作，现依报告 §14.4 授权**落迁移脚本文件**（含原例外项 `V175`），**不实跑库**；
2. W90~W114 → C72~C96 的回填是否接受（回填动作已随本册交付执行）；
3. 5 项主责 REQ 中 3 项零实现 + 2 项守护失真的**工期判定**：本册建议 F04-01~04/13~16 列 M0（结构与底座），F04-05~09 列 M1（需求本体），F04-10~12/17~18 列 M1~M2。

**验收真实性声明**：本册所有验收标识均为 `mvn -Dtest=类#方法` 或 `*.spec.ts` 用例名（依 Q13）；`ecos-tests` 现状 3 个 `.mjs` 对知识域**零命中**且无 `playwright.config.ts`（K-62），故**本册交付时不存在任何"已通过"的 E2E 结论**，所有 UI 验收待分册 08 Playwright 工程建成后执行。【2026-09-30 五补校订：分册 08 的 P-3 工程**已建成并首跑**（11 用例 = 10 passed / 1 failed，`需求检视报告 §14.6`）⇒ "工程未建成"前提失效；但**知识域自己的 `*.spec.ts` 用例仍未编写**（既有 4 文件是管道/血缘面），本册 UI 验收继续记"未执行（用例未落地）"，两段式口径见报告 §14.6.5】

<!-- 详细设计-04-知识域 / 2026-09-29 / v1.1（2026-09-29 定版） / W90~W114 → C72~C96 / R-8 ②、R-9 ①、R-10 ①、R-11 ②、R-12 ①+② 已批准（报告 §十四.1） / Gate-1 已签字、Gate-2 已通过 / 本轮未实跑库、未改业务代码 -->
