# 重名表族逐族裁定册（36 族全集）

> 建立：2026-11-18 | 依据：用户裁决「改走逐族按 DDL 意图」+「先做无争议面」
> 定位：task #29「同类误会一并解决」/ #31「逐族裁定并修写方」的**证据单源**。
> 本册只记录**实测到的事实**与**待裁项**，不代替用户裁决；未获授权不改结构、不迁数据。
> 上游：`../00_init/README.md` §四-6（计数口径）、`../04-demo-data/README.md` §五-2/3。

---

## 一、计数口径：同一库三个数，必须声明用哪个

`pg_class ⋈ pg_namespace`，`relkind='r' AND relispartition=false`，9 schema 白名单
（`ecos_control`/`ecos_data`/`ecos_ontology`/`ecos_ai`/`ecos_cognitive`/`ecos_knowledge`/`ecos_infra`/`ecos_dw`/`public`）：

| 口径 | 定义 | 实查族数 |
|---|---|--:|
| A | `relname` 完全相同、跨 schema（**不归一前缀**） | 26 |
| B | 去 `ecos_` 前缀后同名、跨 schema | 29 |
| C | 去 `ecos_` 前缀后同名、**含同 schema 内前缀变体** | **36** |

此前台账只写「26」而未声明口径 = 口径 A，**会漏掉 10 个前缀变体族**（`agent_registry`/`dq_rule`/
`dq_score_asset`/`kb_extract_audit`/`kb_extract_candidate`/`kb_extract_watermark`/`kb_ontology_snapshot`/
`kg_sync_log`/`metric_definition` 等）。这些恰是**写方最容易读错**的一类，故本册取 **C=36** 为全集。

复现：`GROUP BY regexp_replace(relname,'^ecos_','') HAVING count(*)>1`。
行数一律 `count(*)` 精确取，**不用 `reltuples`**（本库多数表从未 ANALYZE，`reltuples=-1`）。

---

## 二、判定方法：三条证据腿，缺一不可

单看「哪份有数据」会判错（数据可能是种子灌的孤儿）；单看「DDL 写了哪个 schema」也会判错（代码可能没跟着改）。
本册每族同时取三腿：

1. **DDL 意图** —— 建表 V 脚本声明的 schema + 脚本/代码注释里的归位裁决号（V191/R-13①、V322/DD-4、V173、V179/B28、V168.1、D108…）。
2. **写方实查** —— `src/main` 的 Java + MyBatis XML 里真实 SQL 的**限定形态**，区分 READ/WRITE。
3. **运行时解析** —— 裸名按 `currentSchema` 首位命中；`%s`/`+ schema +` 动态注入按 `@Value` 默认值命中。

### 2.1 裸名解析规则（关键机制）

全部 8 个部署模块 JDBC URL 一致：

```
currentSchema=ecos_control,ecos_data,ecos_ontology,ecos_ai,ecos_cognitive,ecos_knowledge,ecos_infra,ecos_dw,public
```

⇒ **裸名 SQL 一律先命中 `ecos_control`**（若该表在 control 存在）。
所以「裸名引用」不是引擎侧副本的证据，恰恰是 **control 侧副本**的证据。
只有 control 无同名表时，才沿 search_path 落到后面的 schema（`ecos_agent_registry` 即此例 → 落 `ecos_ai`）。

### 2.2 动态 schema 注入的实际取值（实查：**全仓无任何覆盖**）

`grep control-schema|controlSchema` 全仓（含 `*.yml`/`*.yaml`/`*.properties`）⇒ **配置文件零命中**，
只有 Java `@Value` 默认值与测试断言。故以下默认值就是运行时生效值：

| 属性 | 默认值（= 生效值） | 消费方 |
|---|---|---|
| `ecos.ontology.control-schema` | **`ecos_ontology`** | buszhi `Workflow*Dao` 5 个、ontology `CaliberDao`/`OntologyCandidateDao`/`OntologyProposalDao` |
| `kb.control-schema` | **`ecos_knowledge`** | kb-engine 全部 DAO（`KbOntologySnapshotDao`/`KgSyncLogDao`/`KbExtractAuditDao`/`ProfileDao`…） |
| `ecos.db.control-schema` | **`ecos_control`** | workspace `JdbcQueryHistoryRepository`/`ScenarioMapper` |

⚠ 属性名叫 `control-schema` 但 ontology/kb 两侧默认值指向**引擎 schema**，名实不符，极易误读为 `ecos_control`。
`WorkflowDao.java:27` 即此例 —— 这是 workflow 族判定的决定性一腿。

---

## 三、活体实证（真实库 `sys_man` + gateway:8080，全程只读）

登录走真实端点 `POST /api/v1/auth/login`（admin），返回
`roles=["ROLE_SUPER_ADMIN","ADMIN","SECURITY_AUDITOR"]`、`userId=u-admin` ⇒ D157 修法再次活体确认。

### 3.1 已证实的缺陷 ①：workflow 族 —— 5 行真实数据在界面上恒不可见

| 腿 | 实测 |
|---|---|
| `GET /api/v1/ontology/workflows` | **HTTP 200**，`data:{"data":[],"total":0}` |
| `GET /api/v1/ecos/workflows` | **HTTP 200**，`data:{"data":[],"total":0}` |
| `ecos_control.ecos_workflow` | **5 行**：`wf001 供应商准入审批` / `wf002 数据质量巡检` / `wf003 智能客服问答` / `wf004 供应商风险审查` / `wf005 设备故障应急响应` |
| `ecos_ontology.ecos_workflow` | **0 行** |
| 写方 | `WorkflowDao:27` 注入 `ecos_ontology`，`:55/63/71/82/96/108/114` 全部 `%s.ecos_workflow` |

⇒ 代码读写 `ecos_ontology`（0 行），演示数据在 `ecos_control`（5 行），**control 那 5 行无任何生产读方**。
两端点返回空**不是数据没灌**，是族分叉。DDL 意图（V173 `ecos_ontology`）与代码默认值**一致** ⇒ 权威侧 = ontology，
control 的 5 行属孤儿数据。**这条直接影响演示数据集的可用性**，须与 `04-demo-data` 基线一并裁定。

### 3.2 已证实的缺陷 ②：kb_ontology_snapshot 族 —— 4 个 KPI 恒 0

| 腿 | 实测 |
|---|---|
| `GET /api/v1/knowledge/stats` | **HTTP 200** |
| `ontologySnapshotCount` | **0** |
| `ontologyVersionCount` | **0** |
| `publishedOntologyVersionCount` | **0** |
| `lastOntologySnapshotAt` | **null** |
| `ecos_knowledge.ecos_kb_ontology_snapshot` | **1 行**（`is_deleted=0` 亦 1 行） |
| `ecos_control.kb_ontology_snapshot` | **0 行** |

写方 `KbOntologySnapshotDao:36` 注入 `ecos_knowledge` + `:26` 注释明指 V179 权威表（B28，2026-11-04）；
读方 `KnowledgeStatsAggregator:38/67/69/76/80` 用**裸名** `kb_ontology_snapshot` ⇒ 命中 control 的 0 行空壳。

**同一方法内的对照腿（证明机制而非猜测）**：`kgSyncLogCount` = **10**，与
`ecos_knowledge.ecos_kg_sync_log` 实库 10 行**逐数相符** —— 因为 `:72/:79` 那两腿是**显式限定**的。
同一个聚合器里，显式限定的腿读对、裸名的腿读错 ⇒ 根因就是 search_path 解析，不是数据缺失、不是表未建。

#### 为什么门禁没拦住：一处**刻意收窄**的前提已被实测推翻

`KbExtractBareTableGateTest`（kb-engine-impl，#146h D-7 棘轮门）**扫描根已含 `workspace-impl/src/main`**
（`:57-59`），本应覆盖这个聚合器。但其 Javadoc `:36-38` 明写：

> **扫描口径刻意收窄**：`graph_node/graph_edge/kb_ontology_snapshot` 虽也是裸名但
> dev 库面**在位承接 live 数据**（不在 D-7 断点面），不入本门词表，防止口径扩张误杀在位腿。

即 `kb_ontology_snapshot` 被**有意排除**在裸名禁令之外，理由是「裸名那份在位且承接 live 数据」。
**本册 §3.2 实测推翻该前提**：裸名命中的 `ecos_control.kb_ontology_snapshot` 是 **0 行空壳**，
live 数据（1 行）在 `ecos_knowledge.ecos_kb_ontology_snapshot`。排除理由所依据的库面事实已不成立。

⇒ 这不是门禁漏扫，是**门禁的豁免前提过期**。修法有两层：
1. 读腿补限定（下方 R1，纯读侧、不动数据）；
2. 把 `kb_ontology_snapshot` 纳入该门词表（**会反转 #146h 的刻意收窄裁决，须用户批准**；
   纳入后 `KnowledgeStatsAggregator:38/67/69/76/80` 立即变红，正好逼出第 1 层修法 —— 这是棘轮应有的行为）。

> 同族 `graph_node`/`graph_edge` 的排除理由**已另行实查，当前成立**：全库 `pg_class` 精确查
> 这两个名字（含 `ecos_` 前缀变体）**各只有一份**，均在 `ecos_knowledge`，control 侧无同名表
> ⇒ 裸名沿 search_path 正确落到 `ecos_knowledge`，**不构成错读**（两表实库均 0 行，故 UI 显示 0 是正确的）。
> 即该门三条例外里，**只有 `kb_ontology_snapshot` 一条的前提被推翻**，另两条不必跟着改。

### 3.3 反证记录：belief 族差点被我误判为「读空」

静态读到 `BeliefReader:40` / `BeliefScorer:39` 都是显式 `ecos_cognitive.ecos_cognitive_belief`（0 行），
据此**推断** beliefs 端点应返回空。活体实测**推翻**：

```
GET /api/v1/cognitive/beliefs?domain=pricing  → HTTP 200
data: [{"id":"cog_blf_seed_002","variableName":"competitor_price_cut_prob","domain":"pricing",...}, …]
```

返回的正是 `ecos_control.ecos_cognitive_belief` 的 **2 行**。补查才找到真写方：
**`BeliefDao:32/43/53/66/78/87/101/114/123` 全部裸名** ⇒ control。
即同一模块内 **CRUD 走裸名（control）、评分/读取走显式限定（cognitive）**，是真 SPLIT，不是「读空」。

> 教训：第一轮 grep 的裸名模式**漏列了 `cognitive_belief/evidence/hypothesis/model` 四个名字**，
> 只查了它们的限定形态，于是把「限定形态指向空表」误当成「该族读空」。
> **grep 名单不全 = 证据不全**，补全名单后结论反转。归因必须有反证，不能停在「看起来像」。

---

## 四、逐族裁定表（36 族，按族名字母序）

判定词：`CONTROL` = 写方实落 control；`ENGINE` = 写方实落引擎 schema；
`SPLIT` = 不同代码路径落不同副本（**活缺陷**）；`PREFIX` = 同 schema 内裸名 vs `ecos_` 前缀；
`ZERO-REF` = 两侧均无生产 SQL 引用。
「活体」列：✅ = 本册 §三 已实测；— = 仅静态证据（**未实测，不得当已验**）。

| # | 族 | 成员 = 行数 | 写方实证（file:line） | 判定 | 活体 | 处置建议（待裁） |
|--:|---|---|---|---|:--:|---|
| 1 | `agent` | ai.ecos_agent=0<br>control.ecos_agent=5 | `AgentDao:53-126` 显式 control 全 CRUD；`DiagnosticAgentDao:40` 裸名→control；`AgentDao:22-24` 注释明标 ai 副本为「旧版」0 行且多 `tenant_id` 列 | CONTROL | — | ai 副本可清退（代码已自证旧版） |
| 2 | `agent_registry` | ai.agent_registry=4<br>ai.ecos_agent_registry=13 | ~~`AgentRegistryRepository:16-38` 裸名 CRUD~~ **订正见 7.4①：六处实为显式 `ecos_agent_registry`（前缀名），无一处裸名**；+ `AiEngineHealthDao:19` 同前缀名；无 schema 限定，control/data/ontology 无同名 ⇒ 沿 search_path 落 `ecos_ai` | PREFIX（`ecos_` 胜） | ✅7.1 | 🔴 **不可直接清退**：活表 `ecos_ai.ecos_agent_registry` **单源无 CREATE**（7.5），裸名侧反而是 V 链唯一建出的表 ⇒ 须先补 V350+ 建活表，见 A4 |
| 3 | `business_scenario` | ai=0 / cognitive=0<br>control=245 | `WorkspaceDataDao:191-484` 裸名 CRUD + `ScenarioService:141`；cognitive `ScenarioDao:23` 显式 control（D108，2026-11-15） | CONTROL | — | 两引擎副本 0 行，可清退 |
| 4 | `cognitive_belief` | cognitive=0<br>control=2 | **SPLIT**：`BeliefDao:32-123` 裸名 CRUD→control；`BeliefReader:40`/`BeliefScorer:39` 显式 cognitive→0 行 | **SPLIT** | ✅ | DDL 意图 V191/R-13①=cognitive；须先裁权威侧再统一写方 |
| 5 | `cognitive_evidence` | cognitive=0<br>control=2 | **SPLIT**：`EvidenceDao:35-95` 裸名 CRUD + `MentalReviewDao:57` 裸名→control；`CognitiveObjectClass:27` 显式 cognitive | **SPLIT** | — | 同 #4 |
| 6 | `cognitive_hypothesis` | cognitive=0<br>control=1 | **SPLIT**：`HypothesisDao:52-154` 裸名 CRUD + `MentalReviewDao:42`→control；`HypothesisReader:34`/`CognitionContractGuard:25` 显式 cognitive | **SPLIT** | — | 同 #4 |
| 7 | `cognitive_model` | cognitive=0<br>control=1 | `ModelRegistryDao:75-204` **全显式 cognitive**（含 INSERT/UPDATE）；无裸名写方 | ENGINE | — | control 那 1 行是孤儿，需迁往 cognitive |
| 8 | `cognitive_run_invalidation` | cognitive=0 / control=0 | `InvalidationDao:55/58` 裸名 INSERT + `MentalReviewDao:71` 裸名→control | CONTROL（事实） | — | 与 DDL 意图 cognitive 冲突；0 行，先定意图再改限定 |
| 9 | `dq_rule` | control.ecos_dq_rule=5<br>data.dq_rule=37 | **SPLIT 跨模块**：`DqRuleLifecycleDao:32-145`+`DqRuleMapper:58`+`DqRuleSqlProvider:23/34` 显式 `ecos_data.dq_rule`（写）；`DqDashboardController:54/124/126/187` 裸名 `ecos_dq_rule`→control（读） | **SPLIT** | — | control 副本是 `ecos_dq_issue` 的**真实 CASCADE FK 父**，**不得先删**；属已登记 11 个 `dq_*` 族 |
| 10 | `dq_score_asset` | data.dq_score_asset=0<br>data.ecos_dq_score_asset=0 | `DqScoreDao:26/39/80/85`+`DqReportDao:80` 显式 `ecos_data.dq_score_asset` | PREFIX（裸名胜） | — | `ecos_` 前缀副本零引用 |
| 11 | `goal_tracking` | cognitive=0 / control=0 | `DqDashboardController:284/291` 裸名→control | CONTROL（事实） | — | 0 行；FK 从属见 demo README §三 |
| 12 | `kb_extract_audit` | knowledge.ecos_=45<br>knowledge.bare=0 | `KbExtractAuditDao:26` 注入 `ecos_knowledge` + `:15` 注释指 `ecos_kb_extract_audit` | PREFIX（`ecos_` 胜） | — | 裸名副本 0 行零引用 |
| 13 | `kb_extract_candidate` | knowledge.ecos_=0<br>knowledge.bare=0 | 仅 `WAgentEngineCatalog:116` **注释**提及裸名；无生产 SQL | ZERO-REF | — | 待裁 |
| 14 | `kb_extract_watermark` | knowledge.ecos_=0<br>knowledge.bare=0 | `KbEntityInstanceExtractionServiceDao:33` 注入 + `:18` 注释指 `ecos_` 前缀 | PREFIX（`ecos_` 胜） | — | 裸名副本零引用 |
| 15 | `kb_lineage_event` | control.kb_lineage_event=0<br>knowledge.ecos_kb_lineage_event=0 | `LineageEventDao:45-85`（**在 ontology-engine**）裸名 CRUD→control；`KnowledgeStatsAggregator:40/74` 裸名→control | CONTROL（事实） | — | 读写同侧**自洽**，非活缺陷；但 DDL 意图=knowledge，归属待裁 |
| 16 | `kb_ontology_snapshot` | control.kb_ontology_snapshot=0<br>knowledge.ecos_kb_ontology_snapshot=1 | **SPLIT**：`KbOntologySnapshotDao:36` 注入 `ecos_knowledge`（写 1 行）；`KnowledgeStatsAggregator:38/67/69/76/80` **裸名**→control(0 行) | **SPLIT** | ✅ | 4 个 KPI 恒 0/null；修法=读腿补限定（**不动数据**） |
| 17 | `kg_sync_log` | knowledge.kg_sync_log=0<br>knowledge.ecos_kg_sync_log=10 | `KgSyncLogDao:31` 注入 + `KnowledgeStatsAggregator:72/79` **显式限定** | PREFIX（`ecos_` 胜） | ✅ | 活体=10 正确；是 #16 的**内部对照腿** |
| 18 | `metric_definition` | ontology.metric_definition=0<br>ontology.ecos_metric_definition=0 | `CaliberDao:159-255` 注入 `ecos_ontology` + `ecos_metric_definition`；`CaliberService:25` 注释 V168.1 | PREFIX（`ecos_` 胜） | — | 裸名副本零引用 |
| 19 | `object_attachment` | control=0 / ontology=0 | `ObjectRuntimeDao:174` 裸名→control | CONTROL | — | ontology 副本零引用 |
| 20 | `object_links` | control=7<br>ontology=0 | `PgGraphDao:42` INSERT 裸名 + `:56/69` + `PgGraphService:144` + `ObjectQLParser:201`→control | CONTROL | — | ontology 副本 0 行，可清退 |
| 21 | `object_relationship` | control=0 / ontology=0 | `ObjectRuntimeDao:97` INSERT / `:166` DELETE 裸名→control | CONTROL | — | ontology 副本零引用 |
| 22 | `object_state_machine` | control=13<br>ontology=0 | `StateMachineDao:35-77` 裸名 CRUD→control | CONTROL | — | ontology 副本 0 行，可清退 |
| 23 | `object_timeline` | control=18<br>ontology=0 | `ObjectRuntimeDao:36` INSERT + buszhi `TimelineDao:43` INSERT 裸名 + `ObjectController:302` + `IntegrationMetadataDao:41`；`TimelineDao:16` 注释明指 control（V200 后） | CONTROL | — | ontology 副本 0 行，可清退 |
| 24 | `ontology_entity` | control=18（**仅 4 行 `is_deleted=0`**）<br>ontology=0 | **SPLIT**：写方全裸名→control（`OntologyDao:122` INSERT / `:131` UPDATE / `:143` 软删 / `:386` 归域；`AutoDiscoverDao:54` INSERT）；读方裸名 8+ 处（`OntologyDomainDao:172/177`、`WorkspaceDataDao:92/114`、`ObjectController:90`、`ObjectQLController:61`、`OntologyKgSyncDao:18`、`OntologyEngineHealthDao:23`、`FunctionEvaluatorDao:45`、`OntologySourceDao:31`）；**但** `OntologyMappingDao:117/119` 显式 `ecos_ontology`（读 0 行）+ `SecurityEngineContextTrimmer:62` 映射 `ecos_ontology` | **SPLIT** | — | V322/DD-4 意图=ontology；迁移须**同批改 10+ 处裸名**，否则写读再次分叉。14 行软删数据是否随迁须裁 |
| 25 | `scenario_binding` | cognitive=0<br>control=8 | `WorkspaceDataDao:310/318/348/446/490/497` 裸名 CRUD→control | CONTROL | — | cognitive 副本 0 行，可清退 |
| 26 | `scenario_binding_link` | cognitive=0<br>control=7 | `WorkspaceDataDao:429-503` 裸名 CRUD→control | CONTROL | — | 同上 |
| 27 | `wm_causal_link` | cognitive=0<br>control=15 | `WorldModelGraphWriteDao:119` INSERT / `:131` DELETE 裸名 + `QueryDao:79` + `DqDashboardController:225` + `DiagnosticAgentDao:78`→control；`WorldModelSegment:22` **声明** cognitive | **SPLIT**（声明 vs 实写） | — | 数据在 control，认知目录声明 cognitive |
| 28 | `wm_goal` | cognitive=0<br>control=21 | `WorldModelGraphWriteDao:31` INSERT / `:51` UPDATE / `:67` DELETE 裸名 + `QueryDao:29/65` + `DqDashboardController:215/226/227/285/292` + `DiagnosticAgentDao:61/79/80`→control；`CognitiveObjectClass`/`WorldModelSegment:22` **声明** cognitive | **SPLIT** | — | 同 #27；`ecos_wm_goal.parent_id` 自引用，迁移须深度序 |
| 29 | `wm_goal_log` | **`ecos_cognitive.ecos_wm_goal_log`=0<br>`ecos_control.ecos_wm_goal_log`=0**（订正见 7.4②：**两侧都带 `ecos_` 前缀**，属跨 schema 同名而非前缀变体） | 无生产 SQL 命中（仅作 FK 从属存在；两侧各有一条 CASCADE FK 指向各自 schema 的 `ecos_wm_goal`，见 7.2） | ZERO-REF | ✅7.1 | 待裁权威 schema；演示面只认 control 侧（7.6），见 A3 |
| 30 | `wm_scenario` | cognitive=0<br>control=5 | `WorldModelScenarioDao:37-88` 裸名 CRUD→control + `DiagnosticAgentDao:95`/`Controller:388` 裸名读；`CognitiveObjectClass:67` **声明** cognitive | **SPLIT** | — | 同 #27 |
| 31 | `workflow` | control=5<br>ontology=0 | `WorkflowDao:27` 注入 **`ecos_ontology`** + `:55/63/71/82/96/108/114` `%s.ecos_workflow` 全 CRUD | ENGINE | ✅ | 两端点活体 `total=0`；control 5 行孤儿。DDL V173 与代码**同意图** |
| 32 | `workflow_approval` | control=0 / ontology=0 | `WorkflowApprovalDao:63-80` 同注入 `ecos_ontology` | ENGINE | — | 两侧 0 行 |
| 33 | `workflow_instance` | control=0 / ontology=0 | `WorkflowInstanceDao:69-114` 同注入；`TenantAwareJdbcTemplate:45` 列名册 | ENGINE | — | 同上 |
| 34 | `workflow_log` | control=0 / ontology=0 | `WorkflowLogDao:57-69` 同注入 | ENGINE | — | 同上 |
| 35 | `workflow_task` | control=0 / ontology=0 | `WorkflowTaskDao:69-134` 同注入 | ENGINE | — | 同上 |
| 36 | `world_scenarios` | cognitive=0 / control=0 | `PortalSearchQueryService:151` 裸名→control；`CognitiveObjectClass:66` **声明** cognitive | **SPLIT**（两侧 0 行） | — | 待裁 |

### 汇总

| 判定 | 族数 | 族（对应上表编号） |
|---|--:|---|
| **SPLIT（活缺陷）** | **10** | #4 belief / #5 evidence / #6 hypothesis / #9 dq_rule / #16 kb_ontology_snapshot / #24 ontology_entity / #27 wm_causal_link / #28 wm_goal / #30 wm_scenario / #36 world_scenarios |
| CONTROL（写方实落 control） | 12 | #1 agent / #3 business_scenario / #8 cognitive_run_invalidation / #11 goal_tracking / #15 kb_lineage_event / #19 object_attachment / #20 object_links / #21 object_relationship / #22 object_state_machine / #23 object_timeline / #25 scenario_binding / #26 scenario_binding_link |
| ENGINE（写方实落引擎） | 6 | #7 cognitive_model / #31 workflow / #32 workflow_approval / #33 workflow_instance / #34 workflow_log / #35 workflow_task |
| PREFIX（同 schema 前缀变体） | 6 | #2 agent_registry / #10 dq_score_asset / #12 kb_extract_audit / #14 kb_extract_watermark / #17 kg_sync_log / #18 metric_definition |
| ZERO-REF | 2 | #13 kb_extract_candidate / #29 wm_goal_log |
| **合计** | **36** | 与 §一 口径 C 一致 ✓ |

**其中「端点级活体」已证 3 族**：`workflow`（§3.1）、`kb_ontology_snapshot`（§3.2）、`cognitive_belief`（§3.3）。
其余 7 个 SPLIT 族**只有静态证据**，未逐一活体验证 —— 不得据本册宣称「10 个缺陷已全部实测」。

**2026-11-18 §七 补：另 8 族已做「库级活体 + 引用面全扫」**（#2/#10/#12/#13/#14/#17/#18/#29，
即 ZERO-REF 2 族 + PREFIX 6 族）。口径与上面 3 族**不同、不可混称**：
§七 证的是 `count(*)` 精确行数、`pg_constraint` 双向 FK、三形态生产 SQL 引用面、DDL 建表出处；
**未做 HTTP 端点调用**（这 8 族无 UI 读面缺陷待证）。表内 `✅7.1` 标记即此口径。

---

## 五、请裁决（按风险从低到高）

| 项 | 内容 | 风险 | 建议 |
|---|---|---|---|
| **R1** | `kb_ontology_snapshot` 读腿补限定：`KnowledgeStatsAggregator:38/67/69/76/80` 裸名 → `ecos_knowledge.ecos_kb_ontology_snapshot` | **数据风险最低**（纯读侧、不动数据、不改 API 签名，已有同方法内 `kgSyncLogCount` 正确腿作对照）；**但有架构代价，见下方 R1 附注** | 需要用户在「补限定」与「走 kb-api 门面」之间选一条 |
| **R2** | 6 个 PREFIX 族的零引用副本（`agent_registry` 4 行 / `dq_score_asset` / `kb_extract_audit` 裸名 / `kb_extract_watermark` 裸名 / `kg_sync_log` 裸名 / `metric_definition` 裸名）清退 | 低：除 `agent_registry`(4 行) 外均 0 行；但 IR03 DROP 需逐项授权 + 备份 | 与 V349 同口径，需授权 |
| **R3** | 12 个 CONTROL 族的引擎侧 0 行副本清退 | 低（0 行），但**须先确认不是迁移目标**（用户此前已明令「引擎侧 0 行副本刻意不清退」） | 需逐族确认 DDL 意图后再动 |
| **R4** | `workflow` 族：control 5 行孤儿数据迁往 `ecos_ontology`（代码与 DDL 同意图） | 中：动数据；迁完两端点应显示 5 行 | 需授权 + 备份 + dry-run |
| **R5** | 10 个 SPLIT 族统一权威侧 + 改全部写方限定 | **高**：`ontology_entity` 涉及 10+ 处裸名、`wm_goal` 自引用深度序、`dq_rule` 有真实 CASCADE FK 父 | 必须逐族单独裁定，禁止批量正则改写 |

### R1 附注：补限定会**加深一条已冻结的架构违约**，不是纯赚

`WorkspaceLayeringArchTest.noEngineSchemaTablesTouchedDirectly()`（workspace-impl，`:83-109`）
钉的是**铁律 §3.1「workspace 禁止把 SQL 打到对方引擎的 schema/表」**，现状是 **ratchet 冻结而非清零**：

- `knowledge/service/KnowledgeStatsAggregator.java` **已在冻结基线里**（`:94`），`KnowledgeStatsVO.java` 同（`:95`）；
- 基线口径「**只允许减少，不允许新增**」，判据是**文件级**（`offenders` 是文件路径集合，不是表名集合）；
- 该测试 Javadoc `:79-80` 明写这些文件「**不在卷07 F07-16 的可改面内（属 ontology/knowledge 侧 Agent）**」。

⇒ 给该文件**再加** `ecos_knowledge.` 限定：
- **门禁不会变红**（文件已在基线，文件级判据不数表名个数）；
- 但**实质上加深了跨引擎直读违约**，与「基线只许缩小」的治理方向相反。

三条路，须用户选：

| 选项 | 做法 | 代价 |
|---|---|---|
| **R1-a** | 就地补限定（5 处裸名 → `ecos_knowledge.ecos_kb_ontology_snapshot`） | 立刻修好 4 个 KPI；加深冻结违约；门禁仍绿 |
| **R1-b** | 改走 kb-api 门面（workspace 不再直读 kb 表，由 kb 侧提供快照计数端点） | 架构正确、可把该文件**移出**冻结基线（棘轮收紧）；需新增 API（只增不改允许）+ 跨模块改动 + 两侧联调 |
| **R1-c** | 只登记不修 | 4 个 KPI 继续恒 0；无风险 |

**另需注意（三选项通用）**：`KnowledgeStatsAggregator` 在 `workspace-impl`，而该模块被 **gateway fat JAR 内嵌**。
故任何修法要活体复验，都必须**重打 gateway fat jar + 重启 gateway** —— 这会中断用户当前正在跑的环境，
属需确认的动作，本册不擅自执行。（重打 fat jar 的已知坑见项目记忆：`-pl gateway install` 会静默不重打包。）

> 本册原稿把 R1 写成「风险最低、可先做」，**漏了这条架构代价与重启代价**，现订正。
> 教训：判「低风险」不能只看数据面，还要看**该文件是否处在某个冻结基线/棘轮里**。

**红线提醒**：R2/R3/R4 均含 DROP 或数据迁移，属不可逆 + 撞 IR03 成文红线，
按项目规矩**即使有「全授权」也须停下逐项请示**；本册不预置任何执行脚本。

---

## 六、方法论备忘（都已踩过）

1. **grep 名单不全 = 证据不全**：第一轮裸名模式漏列 4 个 `cognitive_*` 名字，导致 belief 族误判（§3.3）。
   逐族扫描必须**用族全集驱动**，不能手挑名字。
2. **`%s` / `+ schema +` 动态注入必须追到 `@Value` 默认值**，且要**实查配置文件有无覆盖**。
   只看 SQL 字面会把 `WorkflowDao` 误判成裸名或硬编码。
3. **属性名会骗人**：`ecos.ontology.control-schema` 默认值是 `ecos_ontology` 而非 `ecos_control`。
4. **同方法内的对照腿是最强证据**：`kgSyncLogCount`(显式限定)=10 正确 vs `ontologySnapshotCount`(裸名)=0 错误，
   一次调用就排除了「数据缺失」「表未建」「权限」三种替代解释。
5. **有数据 ≠ 权威**：control 的 `ecos_workflow` 5 行是种子灌的孤儿，生产代码根本不读它。
6. **0 行 ≠ 垃圾**：引擎侧空壳可能是裁定的迁移目标（用户已明令不得先删）。

---

## 七、逐族推进第一批：ZERO-REF 2 族 + PREFIX 6 族活体反证（2026-11-18，全程只读）

授权依据 = 用户「按册子顺序逐族推进」，其选项原文为「从 ZERO-REF/PREFIX 这类低风险族开始，
**每族先出反证与备份方案再动手**」。本节只做「反证与备份方案」这一半。
**未执行任何写操作**：无 DROP、无 INSERT、无 UPDATE、无迁移脚本落盘。
R2 的 DROP 属不可逆 + 撞 IR03 成文红线，按 §五 红线提醒须逐项请示，本节末列出请示项。

### 7.1 活体行数（`count(*)` 精确取，非 `reltuples`）

`docker exec ecos-postgres psql -U postgres -d sys_man`，DO 块遍历 `pg_class ⋈ pg_namespace`
（`relkind='r' AND NOT relispartition`，9 schema 白名单，族名按 `regexp_replace(relname,'^ecos_','')` 匹配）：

| # | 族 | 成员 = 实测行数 | 与 §四 册载 |
|--:|---|---|---|
| 2 | `agent_registry` | `ecos_ai.agent_registry`=**4** / `ecos_ai.ecos_agent_registry`=**13** | 相符 |
| 10 | `dq_score_asset` | `ecos_data.dq_score_asset`=**0** / `ecos_data.ecos_dq_score_asset`=**0** | 相符 |
| 12 | `kb_extract_audit` | `ecos_knowledge.kb_extract_audit`=**0** / `ecos_knowledge.ecos_kb_extract_audit`=**45** | 相符 |
| 13 | `kb_extract_candidate` | `ecos_knowledge.kb_extract_candidate`=**0** / `ecos_knowledge.ecos_kb_extract_candidate`=**0** | 相符 |
| 14 | `kb_extract_watermark` | `ecos_knowledge.kb_extract_watermark`=**0** / `ecos_knowledge.ecos_kb_extract_watermark`=**0** | 相符 |
| 17 | `kg_sync_log` | `ecos_knowledge.kg_sync_log`=**0** / `ecos_knowledge.ecos_kg_sync_log`=**10** | 相符 |
| 18 | `metric_definition` | `ecos_ontology.metric_definition`=**0** / `ecos_ontology.ecos_metric_definition`=**0** | 相符 |
| 29 | `wm_goal_log` | `ecos_cognitive.ecos_wm_goal_log`=**0** / `ecos_control.ecos_wm_goal_log`=**0** | 行数相符，**成员名册载缺失，见 7.4 订正②** |

合计 16 张表，8 族。

### 7.2 FK 依赖：**零 INBOUND**（DROP 安全性的决定性一腿）

`pg_constraint` 双向查（`confrelid` = 入向 / `conrelid` = 出向），16 张表全量：

| 方向 | 命中 | 明细 |
|---|--:|---|
| **INBOUND**（谁引用这些表） | **0** | 16 张表**没有一张是外键父表** |
| OUTBOUND（这些表引用谁） | 2 | `ecos_cognitive.ecos_wm_goal_log` → `ecos_cognitive.ecos_wm_goal`（`fk_cog_goallog_goal`，`confdeltype='c'` CASCADE）<br>`ecos_control.ecos_wm_goal_log` → `ecos_control.ecos_wm_goal`（`ecos_wm_goal_log_goal_id_fkey`，CASCADE） |

⇒ **§四 #9 `dq_rule` 那条「control 副本是 `ecos_dq_issue` 的真实 CASCADE FK 父，不得先删」的警告，
不适用于本批 8 族**。本批唯一的 FK 牵连在 `wm_goal_log` 自己身上，且它是**子表侧**——
DROP 子表不需要先动父表，也不会触发 CASCADE 删父表数据。

### 7.3 生产 SQL 引用面：三种形态全扫（只扫 `src/main`）

册子 §六 教训①「grep 名单不全 = 证据不全」在此再次应验——**单一形态扫描会漏判**。
本轮扫了三种形态，缺一不可：

| 形态 | 扫描式 | 命中 |
|---|---|---|
| ① 字面限定 | `(FROM\|INTO\|UPDATE\|JOIN\|TABLE\|EXISTS)\s+(%s\.\|ecos_\w+\.)?<族名>` | 仅 `ecos_data.dq_score_asset`（`DqScoreDao:26/39/80/85` + `DqReportDao:80`）与 `ecos_agent_registry` / `ecos_knowledge.ecos_kg_sync_log` |
| ② **常量拼接** | `\+\s*"\.<族名>` | **30 处，全是 `ecos_` 前缀名**（`CaliberDao:159/168/179/211/255`、`KgSyncLogDao` 12 处、`StructuredExtractQueryDao:35/44/53/69/77/93`、`KbEntityInstanceExtractionServiceDao:116/130`、`KbExtractAuditDao:37`、`KgMapperDao:85`、`EcosKnowledgeGraphDao:88`、`KbOntologySnapshotDao:50`、`KnowledgeLifecycleDao:42`） |
| ③ 裸名（①②合计） | — | **零命中**（`dq_score_asset` 除外，它带 `ecos_data.` 限定，属①） |

**为什么必须扫形态②**：`KbExtractAuditDao:37` 的真实写法是
`"INSERT INTO " + schema + ".ecos_kb_extract_audit "` —— 表名与闭合引号之间还有空格，
且 `INTO` 后面跟的是 `" + schema + "` 而非表名。**形态①的正则对它完全失明**。
若只跑形态①就宣布「kb 侧零引用」，会把**活表**误判成孤儿。

⇒ 8 族的活侧/孤儿侧判定（**生产 Java SQL 口径**）：

| # | 活侧（有生产 SQL） | 孤儿侧（零生产 SQL） | 孤儿行数 |
|--:|---|---|--:|
| 2 | `ecos_ai.ecos_agent_registry`（7 腿） | `ecos_ai.agent_registry` | **4** |
| 10 | `ecos_data.dq_score_asset`（5 腿，**裸名胜**） | `ecos_data.ecos_dq_score_asset` | 0 |
| 12 | `ecos_knowledge.ecos_kb_extract_audit` | `ecos_knowledge.kb_extract_audit` | 0 |
| 13 | **两侧皆无** | 两侧皆孤儿 | 0 / 0 |
| 14 | `ecos_knowledge.ecos_kb_extract_watermark` | `ecos_knowledge.kb_extract_watermark` | 0 |
| 17 | `ecos_knowledge.ecos_kg_sync_log` | `ecos_knowledge.kg_sync_log` | 0 |
| 18 | `ecos_ontology.ecos_metric_definition` | `ecos_ontology.metric_definition` | 0 |
| 29 | **两侧皆无** | 两侧皆孤儿 | 0 / 0 |

### 7.4 两处册载订正（实测推翻，不是推断）

**订正① — §四 #2 的「写方实证」写错了。**
册载 `AgentRegistryRepository:16-38` **裸名** CRUD + `AiEngineHealthDao:19`。
逐行读原文：`AgentRegistryRepository:16/21/26/29/33/38` **六处全是 `ecos_agent_registry`（前缀名）**，
`AiEngineHealthDao:19` 亦为 `SELECT COUNT(*) FROM ecos_agent_registry`。**无一处裸名。**

- 判定词 `PREFIX（ecos_ 胜）**不变**，但机制须改：不是「裸名沿 search_path 落 ecos_ai」，
  而是「**显式前缀名、无 schema 限定**，沿 search_path 落 `ecos_ai`（control/data/ontology 均无同名）」。
- 该订正**加强**而非削弱「裸名 4 行零引用」的结论：写方明确指向前缀名，裸名副本彻底无生产读方。

**订正② — §四 #29 缺成员名，且它不是前缀变体族。**
册载只写「cognitive=0 / control=0」。实测两侧**都带 `ecos_` 前缀**：
`ecos_cognitive.ecos_wm_goal_log` 与 `ecos_control.ecos_wm_goal_log`。
⇒ #29 属**跨 schema 同名**（口径 A），不属「同 schema 内裸名 vs 前缀」（口径 C 的前缀变体子类）。
§一 口径 C 把它计入 36 全集没错（去前缀后同名），但**处置手法与前缀族不同**：
前缀族是「留一侧删一侧」，#29 是「两个 schema 各一份、须先裁权威 schema」。

### 7.5 🔴 新发现的阻断项：#2 的**活表在 DDL 单源里根本没有 CREATE**

归档单源全量扫 `ecos_agent_registry`（**298 枚**，含宽松匹配）只有 4 处提及，**无一处 CREATE**：

| 脚本 | 语句 | 性质 |
|---|---|---|
| `V30__ecos_diagnostic_agent.sql:50` | `INSERT INTO ecos_agent_registry …` | 无 schema 限定，**假定表已存在** |
| `V316__agent_registry_knowledge_carrier.sql:25` | `UPDATE ecos_ai.ecos_agent_registry …` | 同上 |
| `V200__ai_tool_registry.sql:9` | 注释称其为「public 的 5 张在用真身表」之一 | 散文 |
| `V47__ecos_schema_isolation.sql:117` | `-- ALTER TABLE IF EXISTS ecos_agent_registry SET SCHEMA ecos_agent;` | **已注释掉** |

而裸名孤儿侧**建表与种子俱全**：`V50:100 CREATE` + `V50:134 INSERT` + `V56:4 INSERT`
（`V56` 头注还明写「`ecos_ai.agent_registry` 实存，X-5 实测 0 行」后 INSERT）。

**活体数据坐实两侧是不同代、不是同份副本**：

| 副本 | id 命名空间 | 名称形态 |
|---|---|---|
| 裸名 4 行 | `ag-compliance` / `ag-coordinator` / `ag-data` / `ag-knowledge` | 英文（`Compliance Agent` …） |
| 前缀 13 行 | `agent-ai` / `agent-data` / `agent_diagnostic` / `agent-knowledge` / `agent-ontology` / `agent-scenario` / `agent-security` + `builtin-{ai,cognitive,data,kb,ontology,security}-agent` | 中文（`AI 智能体` …） |

⇒ **这是 D123-2 同类缺陷**（单源无建表 ⇒ 从零按 V 链重放必断）：
按 V 脚本顺序重放，只会建出**裸名**表并灌 4 行英文种子，**永不建出生产真正读写的前缀表**
⇒ `AgentRegistryRepository` 六腿 + `AiEngineHealthDao:19` 全部 `relation does not exist`。
当前之所以没断，是因为走的是 `00_init/`（反向导出，`05_ecos_ai.sql:347` 含该表），**不是 V 链**。

**因此 R2 对 #2 的建议「裸名 4 行零引用，待裁清退」不可直接执行**：
裸名表是 V 链里**唯一**被建出的 agent_registry。先删它、又不补前缀表 CREATE，
等于把 V 链的 agent 注册表能力整个删掉。**正确顺序 = 先补 V350+ 建前缀表（并迁 13 行），再裁裸名侧。**

### 7.6 跨阶段共改面（册子 §五 R2 未提，实测补登）

清退任何一张表，**不止改 dev 库**，以下两处必须同批，否则缺陷复发：

| 共改面 | 实测 | 不改的后果 |
|---|---|---|
| **`00_init/`** | **16/16 张表全在**（`01_ecos_control:2707`、`02_ecos_data:407/950`、`03_ecos_ontology:295/986`、`04_ecos_knowledge:100/127/156/403/805/858/973/1234`、`05_ecos_ai:238/347`、`06_ecos_cognitive:1023`） | `00_init` 是从活库反向导出的当前态快照 ⇒ **从零建库会把孤儿副本原样再造回来**，清退归零 |
| **`04-demo-data/`**（三件套实名 = `demo_seed.sql` / `demo_cleanup.sql` / `demo_verify.sql`） | **#2 两侧全在写入+清空+钉值三面**：`demo_seed:41-44` 灌裸名 4 行 `ag-*`、`:49-61` 灌前缀 13 行；`demo_cleanup:31/32/216/217` 的 `v_demo` 数组列两侧、`:208/209` 各一条 `DELETE`；`demo_verify:19-21` 钉裸名 `baseline=4`、`:23-25` 钉前缀 `baseline=13`。<br>**#29 只涉 control 侧**：`demo_cleanup:141` `DELETE FROM ecos_control.ecos_wm_goal_log WHERE goal_id IN (SELECT id FROM ecos_control.ecos_wm_goal)`；`README:93` 登记该侧 FK `ecos_wm_goal_log_goal_id_fkey`=0 行 | 演示初始化、清空、对账**三面齐断**（`relation does not exist` + 钉值失配） |

> **一处误命中已排除（勿据此扩大共改面）**：`demo_seed.sql:465` 出现的 `metric_definition`
> 位于 `ecos_ai.ecos_wagent_tool_contract` 的 **JSON 载荷字符串内**（工具契约 schema 描述），
> **不是表引用**。⇒ #18 `ecos_ontology.metric_definition` **不在演示数据写入面内**，
> 清退它只需改 `00_init/03_ecos_ontology.sql:986`，不动 demo 三件套。
> 教训接 §六①：**grep 命中 ≠ SQL 引用**，必须回读该行上下文判形态。

另：`KbExtractBareTableGateTest`（kb-engine-impl）词表涉及本批裸名，
清退后须复核该门是否仍绿（其豁免前提已在 §3.2 被推翻一条，属独立待裁项，本批不动）。

### 7.7 逐族备份方案与可执行性分级

备份统一口径（与阶段0/阶段3 同法）：`pg_dump --table=<schema>.<table> --data-only --column-inserts`
落 `03-baseline/`，**DROP 前先事务空跑**（`BEGIN; DROP …; ROLLBACK;`）验证无依赖报错。

| # | 孤儿侧 | 行数 | 备份必要性 | 可执行性 |
|--:|---|--:|---|---|
| 12 | `ecos_knowledge.kb_extract_audit` | 0 | 结构备份即可（无数据） | 🔴 **不可 DROP**：`00_init/04_ecos_knowledge.sql:833` 注释原文「knownLegacy(MC01/V179)…**本表只停写不迁移不 DROP（R-12 ①）**」 |
| 14 | `ecos_knowledge.kb_extract_watermark` | 0 | 同上 | 🔴 **不可 DROP**：`:993` 同一条 R-12 ① 裁决 |
| 17 | `ecos_knowledge.kg_sync_log` | 0 | 同上 | 🔴 **不可 DROP**：`:1261` 同一条 R-12 ① 裁决（活侧 10 行在 `ecos_kg_sync_log`，读对） |
| 18 | `ecos_ontology.metric_definition` | 0 | 同上 | 🔴 **不可 DROP**：`00_init/03_ecos_ontology.sql:1009` 注释原文「V168.1 起停写，新写一律 `ecos_metric_definition`；**只加不删（IR03），清理走 PMO 专项**」 |
| 10 | `ecos_data.ecos_dq_score_asset` | 0 | 同上 | 🔴 **亦不可 DROP**：`00_init/02_ecos_data.sql:950` 虽无保护性注解，但 `V165.1:9` 明写「如需回滚**禁 DROP**（IR03）」；且该表名是 V165.1 为合规 DR02 **刻意加的 `ecos_` 前缀**，V342（pg_dump 导出）却建了裸名 `dq_score_asset` ⇒ **两套 DDL 意图互斥，删任一侧都是单方面裁决**，须先定权威命名 |
| 13 | `kb_extract_candidate` 两侧 | 0 / 0 | 同上 | **须先裁权威侧**；`V140`（裸名）与 `V179:106`（前缀）都有 CREATE；每侧是否也在 R-12 ① 名下须逐张查注释 |
| 29 | `ecos_wm_goal_log` 两侧 | 0 / 0 | 同上 | **须先裁权威 schema**（见订正②）；两侧各一条 CASCADE FK 指向各自 schema 的 `ecos_wm_goal` |
| 2 | `ecos_ai.agent_registry` | **4** | **必须含数据备份** | 🔴 **被 7.5 阻断**：先补前缀表 CREATE + 迁 13 行，才轮到裁裸名侧 |

> 🔴 **本表推翻 §五 R2 的核心前提**：R2 把 6 个 PREFIX 族的孤儿侧统称「清退，与 V349 同口径」。
> 实测 **5 族里有 4 族（#12/#14/#17/#18）的 `00_init` 表注释里写着明文裁决「不 DROP」**
> （R-12 ① / PMO 专项）。V349 清退的是**无任何注释保护的 superseded shells**，
> 与这 4 族**不同口径**。把它们并在 R2 一起清退属**误归并**，本批已拆开。

### 7.8 请裁决（逐项，IR03 不可逆红线）

以下每项都需用户**逐项明确授权**，本册不预置执行脚本、不擅自执行：

| 残项 | 请求 | 前置 / 实测约束 |
|---|---|---|
| **A1-残** | `ecos_data.ecos_dq_score_asset`（0 行）是否 DROP —— 本批**唯一**未被既有裁决封死的候选 | 🔴 **但实测又出两处矛盾**：① `V165.1:9` 明写「如需回滚**禁 DROP**（IR03）」；② 该表是 V165.1 为符合 DR02 前缀规范**刻意命名**的，而 V342（pg_dump 反向导出）建了**裸名** `dq_score_asset` 且生产代码读的正是裸名 ⇒ 两套 DDL 意图互相打架。**建议不删**，改裁「哪一侧是权威命名」 |
| **A2** | #13 `kb_extract_candidate` 权威侧裁定（倾向留 `ecos_` 前缀，V179 合规形态） | 两侧皆 0 行 0 引用；**每侧是否也在 R-12 ① 名下须逐张查注释**（本批未查，不作为「可直接删」依据） |
| **A3** | #29 `ecos_wm_goal_log` 权威 schema 裁定（`ecos_cognitive` vs `ecos_control`）+ 连带 FK 归属 | 见订正②；演示面只认 control 侧 |
| **A4** | 🔴 **#2 先补 DDL**：新增 V350+ 建 `ecos_ai.ecos_agent_registry`，并裁 13 行是否随迁 | 补建后才可议裸名侧清退；**本项是补建不是 DROP，风险方向相反** |
| **A5** | R1（`kb_ontology_snapshot` 读腿）三选一：R1-a 补限定 / R1-b 走 kb-api 门面 / R1-c 只登记不修 | 见 §五 R1 附注；任何修法都要重打 gateway fat jar + 重启，会中断当前环境 |
| **A6** | 门禁红 B-1/B-2/B-3：`OntologyNoFactValueArchTest` 的 V344 误报（§8.7） | B-2「补登换绿」= 固化误报，与「不为换绿扩豁免」纪律冲突 |
| **A7** | `ecos_dq` schema 空壳（0 对象，ST07 枚举外第 10 个）如何收口（§8.4 路一 / 路二） | 路一 = 扩 `ALLOWED_DUPLICATES` 登记 11 个 `dq_*` 族；路二 = V350+ `DROP SCHEMA ecos_dq CASCADE`（撞 IR03）。**两路都与既有纪律有张力，必须你裁** |

> 本节方法论补一条（接 §六）：
> **7. 「零引用」必须声明是哪种口径**——生产 Java SQL 零引用 ≠ DDL 零引用 ≠ 种子零引用。
> #2 裸名侧是「生产零引用、但 DDL 有 CREATE + 两处种子 + 演示种子」，
> 只报「零引用」会让人以为可直接删，实际删了会打断 V 链与演示初始化。
> **8. 反向导出物是清退的共改面**：`00_init/` 与 `04-demo-data/` 都从活库导出，
> 活库删表而导出物不改 ⇒ 从零建库把缺陷原样复现。清退必须三面同批（活库 / 00_init / demo）。

---

## 八、逐族推进第二批：其余 28 族库级活体核查（2026-11-18，全程只读）

第一批（8 族）见 §七。本批 = §四 剩余 28 族（#1/#3~#9/#11/#15/#16/#19~#28/#30~#36）。
**同样只做反证，未执行任何写操作**；`count(*)` 精确取、`pg_constraint` 双向查、生产 SQL 三形态扫。

### 8.1 活体行数：28 族逐数核对，**与 §四 册载全部相符**

| # | 族 | 两侧实测行数（schema.表名 = 行数） | 册载 |
|--:|---|---|---|
| 1 | `agent` | `ecos_ai.ecos_agent`=0 / `ecos_control.ecos_agent`=5 | 相符 |
| 3 | `business_scenario` | `ecos_ai`=0 / `ecos_cognitive`=0 / `ecos_control`=**245** | 相符 |
| 4 | `cognitive_belief` | `ecos_cognitive`=0 / `ecos_control`=**2** | 相符（§3.3 已端点级实证） |
| 5 | `cognitive_evidence` | `ecos_cognitive`=0 / `ecos_control`=**2** | 相符 |
| 6 | `cognitive_hypothesis` | `ecos_cognitive`=0 / `ecos_control`=**1** | 相符 |
| 7 | `cognitive_model` | `ecos_cognitive`=0 / `ecos_control`=**1** | 相符 |
| 8 | `cognitive_run_invalidation` | `ecos_cognitive`=0 / `ecos_control`=0 | 相符 |
| 9 | `dq_rule` | `ecos_control.ecos_dq_rule`=**5** / `ecos_data.dq_rule`=**37** | 相符 |
| 11 | `goal_tracking` | `ecos_cognitive`=0 / `ecos_control`=0 | 相符 |
| 15 | `kb_lineage_event` | `ecos_control.kb_lineage_event`=0 / `ecos_knowledge.ecos_kb_lineage_event`=0 | 相符 |
| 16 | `kb_ontology_snapshot` | `ecos_control.kb_ontology_snapshot`=0 / `ecos_knowledge.ecos_kb_ontology_snapshot`=**1** | 相符（§3.2 已端点级实证） |
| 19 | `object_attachment` | `ecos_control`=0 / `ecos_ontology`=0 | 相符 |
| 20 | `object_links` | `ecos_control.ecos_object_links`=**7** / `ecos_ontology.ecos_object_links`=0 | 相符 |
| 21 | `object_relationship` | `ecos_control`=0 / `ecos_ontology`=0 | 相符 |
| 22 | `object_state_machine` | `ecos_control`=**13** / `ecos_ontology`=0 | 相符 |
| 23 | `object_timeline` | `ecos_control`=**18** / `ecos_ontology`=0 | 相符 |
| 24 | `ontology_entity` | `ecos_control.ecos_ontology_entity`=**18**（`is_deleted=0` 仅 **4**）/ `ecos_ontology`=0 | 相符，且实测坐实「18 行中 14 行是软删」 |
| 25 | `scenario_binding` | `ecos_cognitive`=0 / `ecos_control`=**8** | 相符 |
| 26 | `scenario_binding_link` | `ecos_cognitive`=0 / `ecos_control`=**7** | 相符 |
| 27 | `wm_causal_link` | `ecos_cognitive`=0 / `ecos_control`=**15** | 相符 |
| 28 | `wm_goal` | `ecos_cognitive`=0 / `ecos_control`=**21** | 相符 |
| 30 | `wm_scenario` | `ecos_cognitive`=0 / `ecos_control`=**5** | 相符 |
| 31 | `workflow` | `ecos_control.ecos_workflow`=**5** / `ecos_ontology`=0 | 相符（§3.1 已端点级实证） |
| 32 | `workflow_approval` | `ecos_control`=0 / `ecos_ontology`=0 | 相符 |
| 33 | `workflow_instance` | `ecos_control`=0 / `ecos_ontology`=0 | 相符 |
| 34 | `workflow_log` | `ecos_control`=0 / `ecos_ontology`=0 | 相符 |
| 35 | `workflow_task` | `ecos_control`=0 / `ecos_ontology`=0 | 相符 |
| 36 | `world_scenarios` | `ecos_cognitive.ecos_world_scenarios`=0 / `ecos_control.ecos_world_scenarios`=0 | 相符 |

⇒ **§四 册载的 36 族行数**全部经 `count(*)` 复核无误（第一批 8 族见 §7.1，本批 28 族见上表）。

### 8.2 FK 双向：全库只有 **14 条 INBOUND**，全部集中在 `wm_goal` 与 `dq_rule` 两个族

| 目标表 | 入向 FK 条数 | 明细（引用方 → 删除行为） |
|---|--:|---|
| `ecos_cognitive.ecos_wm_goal` | **8** | `ecos_biz_metric` NO ACTION / `ecos_biz_project` NO ACTION / `ecos_biz_target` NO ACTION / `ecos_goal_tracking` CASCADE / `ecos_wm_causal_link` ×2（NO ACTION + CASCADE）/ `ecos_wm_goal` 自引用 NO ACTION / `ecos_wm_goal_log` CASCADE |
| `ecos_control.ecos_wm_goal` | **5** | 同上 5 条（`ecos_goal_tracking` / `ecos_wm_causal_link` ×2 / 自引用 / `ecos_wm_goal_log`），全 CASCADE 除自引用为 NO ACTION |
| `ecos_control.ecos_dq_rule` | **1** | `ecos_control.ecos_dq_issue` → **CASCADE** |
| 其余 25 族 | **0** | 无任何表引用它们 |

**三条结论**：

1. **§四 #9 的警告经实测坐实**：`ecos_control.ecos_dq_rule`（5 行）确实是 `ecos_control.ecos_dq_issue` 的 CASCADE FK 父。
   删 control 侧会连带删 `ecos_dq_issue` 的行 —— 而 `ecos_dq_issue` **不属于本 36 族**，删它属额外伤害。
   ⇒ #9 **不可先删 control 侧**，与 §四 判断一致。
2. **`wm_goal` 族（#28）是全库 FK 枢纽**：两侧各有 8 / 5 条入向引用，且含**自引用**（`fkN_goal_parent` / `fk_cog_goal_parent`）。
   §四 说的「迁移须深度序」经实测被证实且**比册载更重**：不只深度序，还要按 FK 依赖拓扑逐条处理 8+5 条边。
   `ecos_cognitive.ecos_wm_goal` 虽然 0 行，但**它有 8 条入向 FK**（引用方也多为 0 行）——
   **0 行 ≠ 可随意 DROP**，`DROP TABLE` 会因依赖对象存在而需要 `CASCADE`（连带删约束）。
3. **其余 25 族零 FK 牵连**：DROP 不会触发级联，也不被其它表阻塞。

### 8.3 生产 SQL 引用侧抽查（两族，其中一族的册载归因需加强）

**#24 `ontology_entity` —— 比册载更严重，且已活体坐实**

- 显式 `ecos_ontology.` 限定只有 **4 处**：`OntologyMappingDao:117/119`（SELECT，读 **0 行**）+ `SecurityEngineContextTrimmer:46/62`（认知侧映射声明）。
- 其余 **20+ 处全部裸名**，含**本体引擎自己的核心 `OntologyDao`**：
  `:110/116` SELECT、`:122` INSERT、`:131/143/386` UPDATE、`:373/381/438` SELECT/JOIN。
  另有 `AutoDiscoverDao:45/49/54/87/96`（含**写** INSERT）、`OntologyDomainDao:172/177`、
  `OntologyCandidateDao:191/218/219/233`、`OntologySourceDao:31`、`OntologyKgSyncDao:18`、
  `OntologyEngineHealthDao:23`、`FunctionEvaluatorDao:45`、`AutoDiscoverPreviewQueryDao:32`、
  workspace 侧 `WorkspaceDataDao:92/114`、`ObjectController:90`、`ObjectActionController:293`、`ObjectQLController:61`。
- ⇒ **本体引擎自写自读都落在 `ecos_control`（18 行），而 DDL 意图（V322/DD-4）是 `ecos_ontology`（0 行）**。
  这不是「workspace 越界」，是**本体引擎自身违约**。§四 #24 的处置「同批改 10+ 处裸名」实测应为 **20+ 处**，
  且必须含本体引擎 core DAO —— 迁移工作量比册载高一个量级。

**#9 `dq_rule` —— 册载「SPLIT 跨模块」经实测成立，且两侧都用真名**

- 写侧全 `ecos_data.dq_rule`：`DqRuleLifecycleDao:32/51/64/72/89/98/106/114/122/137/145`、
  `DqRuleMapper:58`、`DqRuleSqlProvider:23/34`、`DqScoreDao:108`（共 15 腿，含 INSERT/UPDATE/SELECT）。
- 读侧裸名 `ecos_dq_rule`：`DqDashboardController:54/124/126/187`（4 腿，全 SELECT/JOIN）。
- `ecos_dq_rule` 无 schema 限定 ⇒ 沿 search_path 落 `ecos_control`（5 行）⇒ **仪表盘读 5 行、规则生命周期读写 37 行**。
- ⇒ 真 SPLIT，且与 #24 同构（**同一个模块内**写一处、读另一处）。

### 8.4 `ecos_dq` schema：活库空壳，ST07 枚举外第 10 个 schema

`pg_namespace` 实测有 **10 个** ecos 域 schema：ST07 八 schema（`ecos_control`/`ecos_data`/`ecos_ontology`/`ecos_knowledge`/`ecos_ai`/`ecos_cognitive`/`ecos_infra`/`ecos_dw`）+ `public` + **`ecos_dq`**。

| 检查 | 实测 |
|---|---|
| `ecos_dq` 下对象总数 | **0**（`pg_class` 全 relkind 计数为 0，真空壳） |
| 建它的是谁 | 归档 `V111__ecos_dq_governance_v1.sql:26` `CREATE SCHEMA IF NOT EXISTS ecos_dq` + V112/V113/V114 建表 |
| 表去哪了 | 阶段3 清理已迁/清，schema 空壳残留（V349 只 DROP `ecos_security`/`ecos_sysman`，未动 `ecos_dq`） |
| `00_init` 是否建它 | **不建**。`00_create_schemas.sql:32` 明确列为「knownLegacy 空壳」注释，只建 8 + public |
| `00_init` 里 46 处 `ecos_dq` 命中 | **全是误命中**：表名 `ecos_control.ecos_dq_execution_result` / `ecos_dq_issue` / `ecos_dq_rule`，属 control 域，与 `ecos_dq` schema 无关 |

⇒ **对门禁红的直接意义**：`DdlCodeSchemaAlignmentGateTest.duplicatedTableNamesAreLimitedToRegisteredPatterns`
报的 11 个 `dq_*` 重名（`dq_alert_record`/`dq_knowledge_entry`/`dq_report`/`dq_rule`/`dq_rule_check`/`dq_rule_version`/`dq_schedule`/`dq_score_asset`/`dq_score_snapshot`/`dq_throttle`/`dq_work_order`）**纯粹来自归档语料里
V111~V114（建 `ecos_dq.dq_*`）与 V342（建 `ecos_data.dq_*`）两套脚本并存**，
**活库一侧只有 `ecos_data.dq_*`，`ecos_dq` 侧 0 表**。
⇒ 该门红是「**历史脚本语料的重复**」，不是「活库存在两套表」。
处置有两条路，**须用户裁**：
- **路一（推荐）**：认定 V111~V114 的 `ecos_dq.*` 已被 V342 取代，按「设计资产历史版本」之外的一般情形**不能 DROP**（IR03 只加不删）⇒
  只能**扩 `ALLOWED_DUPLICATES` 并登记理由**（11 个 `dq_*` 族逐族写明「V111 历史落点已被 V342 取代、活库 ecos_dq 0 对象」）。
  但 §七 的纪律是「不为换绿扩豁免」。**两者冲突，须用户明确裁决**。
- **路二**：新增 V350+ 脚本 `DROP SCHEMA ecos_dq CASCADE`（活库已是空壳，无数据损失）+ 从归档语料里标注 V111~V114 作废。
  撞 IR03，需逐项授权 + 备份。

### 8.5 本批逐族可执行性总览（28 族，按 §四 判定分组，**无一可在本节直接执行**）

| 判定组 | 族数 | 族 | 本批实测的阻塞原因 |
|---|--:|---|---|
| **SPLIT** | **10** | #4 belief / #5 evidence / #6 hypothesis / #9 dq_rule / #16 kb_ontology_snapshot / #24 ontology_entity / #27 wm_causal_link / #28 wm_goal / #30 wm_scenario / #36 world_scenarios | #24 须先裁权威侧再改 **20+ 处**裸名（册载「10+」偏低）；#28/#27/#30 是 FK 枢纽（8+5 条入向含自引用），迁移须拓扑序；#9 control 侧是 `ecos_dq_issue` 的 CASCADE 父，**不可先删**；#16 走 R1（A5 三选一）；#4/#5/#6/#36 须先裁权威侧 |
| **CONTROL** | **12** | #1 agent / #3 business_scenario / #8 cognitive_run_invalidation / #11 goal_tracking / #15 kb_lineage_event / #19 object_attachment / #20 object_links / #21 object_relationship / #22 object_state_machine / #23 object_timeline / #25 scenario_binding / #26 scenario_binding_link | 全部撞**用户既有明令「引擎侧 0 行副本刻意不清退」**（§五 R3）。本批实测确认这 12 族的引擎侧确实多为 0 行（#3 的 ai/cognitive 皆 0、#20/#22/#23 的 ontology 侧 0），但**清退方向被既有指令封住**，须用户先解封 |
| **ENGINE** | **6** | #7 cognitive_model / #31 workflow / #32 workflow_approval / #33 workflow_instance / #34 workflow_log / #35 workflow_task | 写方实落引擎 schema（#31~#35 `ecos_ontology`、#7 `ecos_cognitive`）。**control 侧是孤儿**：#31 有 **5 行**实体数据（§3.1 端点级已证不可见）、#7 有 1 行，其余 4 族两侧 0 行。清孤儿 = DROP 有数据的表 ⇒ IR03 + §五 R4 授权要求 |
| **合计** | **28** | 与 §四 §汇总 的 10/12/6 分组逐族对齐 ✓ | — |

**本批结论**：28 族**无一可在本节直接执行**。每族要么撞 IR03 DROP 红线、要么撞用户
「引擎侧 0 行副本刻意不清退」的既有指令、要么需要先裁权威侧再动 20+ 处代码。
**这不是没推进，而是把「看起来能删」的假象逐族证伪了**——
#2 在 §七 就是被这样证伪的：册子说「4 行零引用待裁清退」，实测发现删了会打断 V 链与演示三件套。
同法复核后，本批 28 族**没有一族是「零共改面 + 零 FK + 零生产引用 + 可直接 DROP」的干净目标**。

### 8.6 跨批总结：8 个 PREFIX/ZERO-REF 族的「零行 ⇒ 可清退」直觉**全部失效**

库面看这 8 族确实几乎全是 0 行（#2 除外，4 行），但 DDL 面写着四类不同禁令：

| 族 | 保护性依据（`00_init` 表注释原文 / 脚本头注） | 结论 |
|---|---|---|
| #12 `kb_extract_audit` | `04_ecos_knowledge.sql:833`「knownLegacy(MC01/V179)…**本表只停写不迁移不 DROP（R-12 ①）**」 | **禁 DROP** |
| #14 `kb_extract_watermark` | `:993` 同上 R-12 ① | **禁 DROP** |
| #17 `kg_sync_log` | `:1261` 同上 R-12 ① | **禁 DROP** |
| #18 `metric_definition` | `03_ecos_ontology.sql:1009`「V168.1 起停写…**只加不删（IR03），清理走 PMO 专项**」 | **禁 DROP** |
| #10 `ecos_dq_score_asset` | `V165.1:9`「如需回滚**禁 DROP**（IR03）」+ V165.1（加 `ecos_` 前缀合规 DR02）与 V342（pg_dump 导出裸名）**两套命名意图互斥** | 须先定权威命名 |
| #13 `kb_extract_candidate` | 未逐张查注释（本批不作为「可删」依据） | 待查 + 裁权威侧 |
| #29 `ecos_wm_goal_log` | 未查；两侧各一条 CASCADE FK 指向各自 `ecos_wm_goal` | 须裁权威 schema |
| #2 `agent_registry` | 活表**单源无 CREATE**（7.5）；裸名侧反而有 CREATE + 两处种子 + 演示三件套 | 先补建，再议 |

⇒ **§五 R2「6 个 PREFIX 族与 V349 同口径清退」是误归并**：V349 清的是
**无任何注释保护的 superseded shells**，而这 6 族里有 4 族带**明文禁令**、2 族需先裁权威。
**本批不产生任何 DROP 授权请求**——除 A1-残（#10，建议不删、改裁命名权威）与
A2/A3（裁权威而非删表）外，全部收敛为「维持现状 + 已登记」。

> 方法论补一条（接 §七 的 7、8、9）：
> **10. 反向导出物里的表注释就是隐性裁决台账。** `00_init/` 由 `pg_dump` 导出，
> `COMMENT ON TABLE` 从活库带出 —— 而注释里写着「不 DROP」「只加不删」「清理走 PMO 专项」等
> **先行裁决的原文**。判「能不能删」时若不读注释，就会把已被禁止的动作重新提上桌。
> 本批若不查这 4 条注释，A1 会以「5 张零行零引用孤儿」的名义提出 DROP 请示 —— 那是**对既有裁决的重复翻案**。

### 8.7 顺带反证：gateway 第二枚门禁红（V344 金额钉值缺登）是**门禁误报**，非真实违例

阶段5 复跑留下的第二枚红 = `OntologyNoFactValueArchTest.moneyScaleNumericSurfaceIsPinnedAndNeverOntological`
期望钉值集合缺 `V344__ecos_business_scenario_budget_backfill.sql`。原登记说「补登前须先核 V344 的
`NUMERIC(p,s)` 落点确在 `ecos_dw` 而非本体面」。**本批已核完，结论与预期相反：V344 根本没有金额列定义。**

| 检查 | 实测 |
|---|---|
| V344 的 `CREATE TABLE` / `ALTER TABLE` / `ADD COLUMN` | **0 处**（`grep -ciE` 全文件计数为 0） |
| V344 大小写不敏感 `numeric|decimal` 全部命中 | **仅 3 行**：`:9` 与 `:27` 是 `--` 注释；`:67` 是 `COMMENT ON COLUMN … IS '金额权威列 = budget_amount（numeric(18,2)，MC01）…'` 的**字符串字面量** |
| V344 真实动作 | 只做 `UPDATE ecos_control.ecos_business_scenario` 的 `budget`→`budget_amount` 回填 + 一条列注释；唯一真实 `numeric` 用法是 `:34/:59` 的 `CAST(… AS numeric)`（**无精度**，不匹配 `18,2`） |

**误报根因**：该门的 `stripComments`（`:485-514`）只剥 `--` 与 `/* */`，**不剥字符串字面量**。
于是 `COMMENT ON COLUMN … IS '…（numeric(18,2)…'` 里的注解文本被当成列定义命中。
⇒ 这是**门禁自身缺陷**（同一族的 D-15/D-16 类），与 lint 的 D-15/D-16 是同一病灶家族：
**扫描前未做词法层面归一（注释剥了、字符串没剥）**。

**处置建议（须用户裁，本批不动）**：

| 选项 | 做法 | 代价 |
|---|---|---|
| **B-1** | 修该门 `stripComments`：剥注释后再剥 `'…'` 字符串（或至少对 `COMMENT ON` 行整行跳过） | 门禁变严/变准；须同批复跑确认钉值集合是否收缩（可能连带别的脚本出集） |
| **B-2** | 把 V344 补登进 `PINNED_SCRIPTS_CONTAINING_MONEY_NUMERIC` 换绿 | **等于把误报固化成基线**，掩盖门禁缺陷；与 §七「不为换绿扩豁免」纪律冲突 |
| **B-3** | 只登记不修 | 该红持续；但已在本册留反证，不会被误读成真实违例 |

> 方法论补一条（接 §七 的 7、8）：
> **9. 门禁红也须反证归因**——「期望集合缺 X」不等于「X 真违例」。
> 本例 X 是注释字符串里的文本，门禁词法处理不完整才报红。
> 与项目既有纪律一致：根因要用反证确认，不能拿台账/断言消息当事实。