# 详细设计 05 — 认知域（aiming 服务 + cognitive-engine 木·C）

> 来源: AI Agent（资深系统设计师视角，用户指令"分册按子系统顺序生成各子系统的设计文档…不符合要求的一定要改"）
> 日期: 2026-09-29 | 版本: v1.2（v1.2 2026-09-29：随**需求检视报告 §十四.1** 批量批准定版——本册 **R-13 ①／R-14 ①／R-15 ①／R-16 ①+③／R-17 ①／R-42 ①／R-67 ②** 七项全部按推荐项批准；R-42 ① 的规则文字更正已落《架构铁律》v2.1 §0.6 第 3 条。v1.1 新增 §八「与 W-Agent 接缝」，原 §八 追溯矩阵顺延为 §九；接缝对象 = PRD-05 v1.2 §六 REQ-COG-06~09） | 责任人: AI Agent
> 上游依据: `docs/20-需求/需求检视报告-2026-09-28.md` §十二（Q1~Q14 + E2E 补记）+ §十三（本册立 **R-42**（§8.1 CM 接缝）与 **R-67**（§13.11 CM-06 相似度形态 / CM-04 首期边界））**+ §十四.1（R-1~R-72 已于 2026-09-29 全量批准、待裁决归零；本册 R-13~R-17／R-42／R-67 逐条口径见该表）+ §十四.3（Gate-1 已签字／Gate-2 已通过，deliverable_allowed=true）**—— 依 Q12，本节即需求基线批准凭证
> 主责 REQ: **REQ-COG-01（E1–E4 场景认知端点）· REQ-COG-02（领域化因果诊断）· REQ-COG-03（反事实由预测运行驱动）· REQ-COG-04（capabilityMask 枚举与映射）· REQ-COG-05（铁律守护）**（`PRD-05-cognitive-engine需求规格-2026-09-28.md`，2 P0 + 1 P0 守护 + 2 P1）
> 关联 REQ: REQ-KB-04（kb 侧认知契约守护，接缝 S-7）、REQ-FC-02/03/04（预测运行快照与情景，PRD-09）、REQ-WS-03（场景工作台右栏）、REQ-SEC-01~04（默认 DENY 与租户过滤）、REQ-NF-04（traceId）、REQ-DB-03（表存在性预检）
> 契约上游: `docs/40-实现/features/20260917-scenario-workbench/BUSINESS_SCENARIO_SERVICE_DOC.md`（206 行 v1.0）+ `BUSINESS_SCENARIO_COGNITION_DOC.md`（147 行 v1.1）—— 本册实测发现两份契约**同文档内自相矛盾且与库内实现三套枚举脱节**，订正清单见 §7.1
> 前置依赖: 分册 00（承流口径 ADR-15、两态寻址、ApiResponse/traceId、DDL 单源）、分册 01（默认 DENY、审计 `ecos.audit` 链）、分册 03（本体快照/口径编码，诊断取参）、分册 04（画像/假设供数、认知契约守护 R-8 同源议题）
> 关联 ADR: ADR-8（认知模型资产存储，**已明令禁用 `kb_*` 误名**）、ADR-9（认知持久化四层）、ADR-14（确定性计算产物经 data-engine 落业务域 APPLICATION）、ADR-15（制品≠承流）、ADR-11（图谱双形态，本域不适用但借键问题同源）
> 规范锚点: `.trae/rules/架构铁律.md` v2.0（§0.2.1 禁 LLM 生成数值、§0.6 场景层只编排、§2.4-6 默认 DENY、§2.5-2 LLM/3 调度/7 事件、§十 配置单源）；`.trae/rules/数据库访问规范.md` v1.2（ST07/MC01/MC02/MC03/MC05/DR06~DR08/§四附则1 裸表名=FAIL/§五 三层纪律"Service 禁 JdbcTemplate 直连"）；`.trae/rules/后端开发规范.md` v1.1（§九 runtime 底座）；`.trae/rules/前端开发规范.md` v1.1
> **编号接续**: 分册 00 = W01~W15→C16~C21；01 = W29~W40→C22~C29；02 = W41~W66→C30~C50；03 = W67~W89→C51~C71；04 = W90~W114→C72~C96；**本册 = W115~W139 → C97~C121**

---

## 〇、本册定位与判定基线

### 0.1 本册覆盖的部署制品与实测承载

| 制品/模块 | 端口 | 实测承载 | 证据 |
|:--|:--:|:--|:--|
| `services/aiming` | 18084 | **制品空心**：全模块 3 个文件（启动类 + `AimingInfrastructureConfig` + `application.yml`），**0 个自有 Controller** | `find ecos_backend/services/aiming -type f` |
| `engine/cognitive-engine` | —（boot 调试引擎 18089） | 由 **aiming** `@ComponentScan("engine.cognitive2")` 与 **gateway fat-JAR** 双宿主 | `AimingServiceApplication.java:56`；`GatewayApplication.java:44` |
| cognitive 接口面 | — | **17 个 Controller / 50 个 handler**，全部在 `cognitive2/controller` | 逐类清点（本册 X-2） |
| 认知持久化 | — | **0 个 MyBatis Mapper**，全部 `JdbcTemplate` 字符串 SQL 落在 Service/Store 层 | `EvidenceStore.java:23-24` 自陈 |
| 前端 | 3000 | 认知/场景相关 **31 个组件文件**，三份"认知四件套"实现并存 | B 章 |

### 0.2 本册要解决的核心命题

一句话：**REQ-COG-01/02/03/04 四项主责能力中，契约端点、枚举、上下文块在代码里全部零命中；库内既没有任何 `mind` 命名的表，也没有 `mind_id` 列——E1 的数据源物理不存在。当前能跑的是一套"路径不同、方法不匹配、状态枚举与文档三套并存"的近似实现，且其宿主 aiming 进程在 18084 上不挂任何鉴权。**

```
① 契约 vs 实现：E1 `cognition/detect`、E2 `cognition/operation-eval` 前后端**双零命中**；
   E3/E4 只有"路径不同"的近似端点（/api/v1/cognitive/hypotheses|beliefs），且无 `?mind=` 过滤。
② 数据源缺失：全库无名字含 mind 的表、`mind_id` 列 0 个；kb_mind_registry 仅存在于文档；
   `ecos_scenario_mind`(V146) 迁移在、库里不存在，而代码仍在 INSERT。
③ 枚举三套：文档冻结 PROPOSED/EVIDENCED/BELIEVED/REFUTED；契约示例写 VALID；
   库内实存 VALID/INVALIDATED/ARCHIVED（7 行）→ 状态机守护项（REQ-KB-04/C1）从未生效。
④ schema 反义：`ecos_cognitive` 11 张表全 0 行，且装的是 ecos_biz_*/ecos_wm_*（业务域/世界模型表）；
   认知真身 14 张表全在 public，其中 6 张 PK 为 bigint nextval。
⑤ 三层纪律失守：0 Mapper、JdbcTemplate 裸 SQL、认知代码写 sysman.sys_config 与 kb.sys_compliance_rule、
   写 legacy kb_cognitive_pipeline（V163 声明停写但从未切换）。
⑥ 底座绕行：LLM 零经 llm-gateway（硬编码 8080 agent-loop + new RestTemplate ×4）、
   Executors.newFixedThreadPool 绕过 ArchUnit 的自建调度、审计 ObjectProvider 缺失即 warn 继续（fail-open）。
⑦ 伪实现：健康端点每个 "UP" 是字面量；world-model 全为固定值与一条硬编码因果边；
   diagnose/history 存进程内 Map；diagnose/preflight 的 neo4jOk/llmOk 硬编码 true。
⑧ 鉴权两态不一致：gateway 侧认知端点需 JWT（注释谎称 permitAll）；aiming:18084 侧排除 Security 自动装配
   + header-auth=false ⇒ 同一 50 个端点在 service 态**完全无鉴权可达**。
```

本册主任务：
1. **把 E1–E4 从"文档端点"建成真实能力**，并先解决"数据源不存在"（Mind 注册/绑定的落点属 **R-13/R-14** 裁决）；
2. **把契约收敛为单源**（8 处复写、3 种前缀、2 种 capabilityMask 类型、3 套状态枚举 → 一次定版）；
3. **把认知域从 JdbcTemplate 裸 SQL 迁到三层纪律**（Mapper 层 + schema 限定 + 方言分支）；
4. **把鉴权与租户过滤在两态下拉到同一水平**（默认 DENY，纠正 fail-open）；
5. **把 stub/伪实现降级为显式 stub**（返回 501/503 + `stub=true`，禁 200 伪数据）；
6. 登记 **R-13 ~ R-17** 五项新裁决。

### 0.3 判定基线

| # | 基线 | 来源 |
|:--:|:--|:--|
| B-1 | cognitive 只读经 REST 的 I/K/D 定位符；产物（模型资产 + 心智状态）落控制域两档；**零新增业务事实表**，确定性计算产物经 data-engine 写通道落 APPLICATION | ARCH_SPEC §4.3 + ADR-14 + 铁律 §0.6 |
| B-2 | 铁律守护五项：推理结果不落盘 / 不新增业务事实表 / 不引入规则引擎 / **禁 LLM 直接承担推理职责（进入 chains/contribution/score 的数值必须确定性可复算）** / Context 不建表 | REQ-COG-05 |
| B-3 | 反事实的金额来源必须是预测运行服务的确定性重算；`ScenarioSimulatorServiceImpl`（RAG+Agent）**禁**作金额来源，其输出必须带 `exploratory=true` 且 UI 标"非核算口径" | REQ-COG-03 §3.3-2 |
| B-4 | 503 原样穿透（禁 200 空 body，用户裁定 A3）；场景无 Mind → 200 + `data:[]`（禁 404）；`mind` 不属场景 → 400（禁静默返回全部） | REQ-COG-01 §1.2 |
| B-5 | 安全全走 security-engine，不可用**默认 DENY**；LLM/调度/事件/存储一律经 runtime 底座 | 铁律 §2.4-6、§2.5 |

---

## 一、实测事实（X-1 ~ X-52）

> 全部条目带 `file:line` 或复现探针；采集 2026-09-29。探针示例：`grep -rn --include="*.java" -e "cognition/detect" ecos_backend ecos_frontend/src`（全仓零命中）、`docker exec ecos-postgres psql -U postgres -d sys_man -c "SELECT count(*) FROM ecos_cognitive.ecos_biz_metric"`。

### 1.1 制品与装配（X-1 ~ X-8）

| # | 事实 | 证据 |
|:--:|:--|:--|
| X-1 | `services/aiming` 制品空心：全模块仅 3 个文件（启动类、`AimingInfrastructureConfig`、`application.yml`），自有 Controller **0** | `find ecos_backend/services/aiming -type f -not -path '*/target/*'` |
| X-2 | 认知接口面 17 Controller / 50 handler 全在 `engine/cognitive-engine/cognitive-engine-impl/.../cognitive2/controller/`，经 aiming `@ComponentScan("engine.cognitive2")` 与 gateway 双宿主 | `AimingServiceApplication.java:56`、`GatewayApplication.java:44` |
| X-3 | `@MapperScan` **6 个模式零命中**：`engine.ai.**.dao`/`.mapper`、`services.agent.**.repository/dao/mapper`、`engine.cognitive2.**.dao`/`.mapper`、`runtime.**.mapper`；根因是**认知模块根本没有 dao/mapper/repository 包**（代码注释自陈"cognitive 模块无 mapper 包既有基建，gateway @MapperScan 亦未登记 cognitive 包"） | `AimingServiceApplication.java:101-118`、`EvidenceStore.java:23-24` |
| X-4 | `spring-kafka` 死依赖 ×2（`cognitive-engine-impl/pom.xml:36-38`、`services/aiming/pom.xml:96-99`），main 源码 `org.springframework.kafka` 零 import。**合规项**：认知 pom 无 drools/easy-rules/neo4j driver（REQ-COG-05"不引入规则引擎"通过） | 依赖清点 + grep |
| X-5 | 同名双控制器同路径：`cognitive2/CognitiveConfigController.java:45` 与 `ai-engine/.../CognitiveConfigController`**均映射 `/api/v1/cognitive/config`**，靠两边互斥排除 + `allow-bean-definition-overriding: true` 才不冲突 | `GatewayApplication.java:82`、`AimingServiceApplication.java:88-89`、`aiming/application.yml:9` |
| X-6 | gateway **排除**认知健康端点：`CognitiveEngineHealthController.class`（`GatewayApplication.java:85`）、`CognitiveEngineOpenHealthController.class`（`:119`，注释自陈仅 standalone :18089 有效）；aiming 反向排除 ai 侧同类 → **同一能力在两态下可达性相反** | 两处 excludeFilter |
| X-7 | 6 个 Controller 共用裸前缀 `/api/v1/cognitive`（仅靠子路径区分：#3 健康、#8 planner、#9 counterfactual、#11 diagnosis、#12 forecast、#13 mental-reviews）；**仅 `CognitivePlannerController.java:30` 声明 bare 孪生 `{"/api/v1/cognitive","/api/cognitive"}`**，其余 16 类只有 v1 | 映射清点 |
| X-8 | 陈旧安全注释失真：`CognitiveEvidenceController.java:27`、`CounterfactualController.java:24` 声称"`/api/v1/cognitive/**` 已被 SecurityConfig permitAll + ClearanceInterceptor 豁免"；实测 `SecurityConfig.java:36-45` 仅 permit 8 条（无 cognitive）、`ClearanceInterceptor.java:34-38` 仅 3 条前缀规则（无 cognitive）→ 注释描述的豁免**不存在**（结论碰巧安全，但文档失真且掩盖了 X-9） | 三处源码 |

### 1.2 鉴权两态与租户（X-9 ~ X-13）

| # | 事实 | 证据 |
|:--:|:--|:--|
| X-9 | **aiming:18084 无鉴权层**：yml 排除 `SecurityAutoConfiguration` + `UserDetailsServiceAutoConfiguration`（`aiming/application.yml:11-15`）、`ecos.header-auth.enabled: false`（`:60-61`）、`@ComponentScan` 不含 `sysman.security`、3 个源文件内无任何拦截器 ⇒ 该进程承流时 **50 个认知端点 + ai 端点可无凭据访问**（fail-open） | 配置 + 文件清单 |
| X-10 | 认知源码 `TenantContextHolder`、`@RequirePermission`、security-engine 客户端 **零命中**；认知 SQL **无任何租户/域过滤**（`WHERE domain = ?` 形式零命中） | grep |
| X-11 | 两态鉴权语义相反：gateway 态唯一匿名可达 = `/api/v1/engine/cognitive/health`（其余 `anyRequest().authenticated()`）；service 态（18084）全部匿名 | X-8/X-9 合取 |
| X-12 | 跨引擎调用一律绕 gateway 自环：workspace `DcchengClient.java:34 ${ecos.cognitive-base:http://localhost:8080/api/v1}`（认知宿主实为 18084）、`ScenarioPreValidateController.java:40`/`KnowledgeHealthAggregator.java:80` `ecos.gateway-base`，后者注释 `:54` 自认"单体时命中本 JVM 路由"= workaround | 三处源码 |
| X-13 | 门禁缺口：9 个 `*Arch*Test.java` 中**无任何**针对 RestTemplate/llm-gateway 收敛或租户过滤的谓词 → X-9/X-10 类问题不可能被门禁发现 | ArchUnit 文件集清点 |

### 1.3 契约与实现脱节（X-14 ~ X-22）

| # | 事实 | 证据 |
|:--:|:--|:--|
| X-14 | **REQ-COG-01 E1/E2 = 0%**：`cognition/detect`、`cognition/operation-eval`（含 `operation-eval` 裸串）在 java/ts/tsx **双零命中** | 全仓 grep |
| X-15 | E3/E4 仅"功能近似"：`GET /api/v1/cognitive/hypotheses`（`CognitiveHypothesisController.java:32,42`）、`GET /api/v1/cognitive/beliefs`（`CognitiveBeliefController.java:39,53`）读真实库（`HypothesisStore.java:36-38`、`BeliefStore.java:37-39`），但**契约前缀 `/api/v1/business/scenarios/{id}/…` 不同**，且认知 controller 内 `mind` 字样零命中（无 `?mind=` 过滤，违反 E3"带 mind 严格过滤"） | 源码 |
| X-16 | `/api/v1/business/scenarios` 前后端零命中；表 `public.ecos_business_scenario`（3 行）**无任何 java 读取方** | grep + 计数 |
| X-17 | **REQ-COG-04 = 0%**：`MindCapabilityMask`、`capabilityMask`、`capability_mask`、`trend_weight`、`ecos.cognition.eval`、`ecos.cognition.domain_alias` **全零命中**；`ecos.cognition.*` 配置键全仓零命中（存在的是相邻键 `ecos.cognitive.scan-interval`，`gateway/application.yml:54-55`） | grep |
| X-18 | **REQ-COG-02/03 契约块 = 0%**：`forecastContext`、`forecastRunId`、`baselineRunId`、`unverifiedHypotheses`、`exploratory` 全零命中 | grep |
| X-19 | **E1 数据源物理不存在**：`kb_mind_registry`/`mind_registry` 零命中；库内**无任何名字含 `mind` 的表**、`mind_id` 列 **0 个**；`ecos_scenario_mind`（`V146:8-19`）迁移存在但 `to_regclass` 两侧均 NULL，而 `ScenarioMindService.java:84-115`（含 `:119 INSERT INTO ecos_scenario_mind`）仍在写它 → 认知-场景绑定链结构性必败 | `table_name ILIKE '%mind%'` = 0 行 |
| X-20 | **假设状态三套并存**：文档冻结 `PROPOSED/EVIDENCED/BELIEVED/REFUTED`（`BUSINESS_SCENARIO_COGNITION_DOC.md:67-72`，"新枚举值必须先入此文档"）；契约示例写 `"status":"VALID"`（`BUSINESS_SCENARIO_SERVICE_DOC.md:104`）；库/代码实为 `VALID/INVALIDATED/ARCHIVED`（`HypothesisStore.java:34`、`V128__ecos_cognitive_hypothesis.sql:21`），实存 7 行全是 `INVALIDATED` → 冻结状态机从未落地，守护项形同虚设 | 三处 |
| X-21 | **同一契约 8 处复写**（ARCH_SPEC:265、PRD-00:41/129/133/271、PRD-01:199、PRD-04:172、PRD-05、需求检视报告、ADR-8/9、分册 04）；**路径前缀 3 种**（`business.scenarios/…` SERVICE_DOC:74 vs `/scenario/…` SERVICE_DOC:174 vs `/api/v1/business/scenarios/…` PRD-05:18）；**capabilityMask 类型 2 种**（逗号串 `"PLANNING,REVIEW,EXECUTION"` SERVICE_DOC:88 — 其中 PLANNING/EXECUTION **不在**其自身 §2.2 枚举内 vs 数组 `["DETECT","FORECAST"]` PRD-05:23）；**端点数自相矛盾**（SERVICE_DOC:28 称"新增 2 端点"，§3.1 列 4） | 逐处引用 |
| X-22 | 契约文档**同文档内自相矛盾**：`BUSINESS_SCENARIO_COGNITION_DOC.md:4`（上游行）已改口真身名 `ecos_cognitive_hypothesis/belief/evidence(V127–129)`，但正文 `:13/:14/:15/:51/:86/:145` 仍用 `kb_*` 误名；而 ADR-8:21、ADR-9:38 已**明令禁止在新文档使用 `kb_*`**（Q11 裁决）；E1 绑定表在 COGNITION_DOC:25-37 写成占位符 `{mind_registry_table}`/`{scenario_mind_binding}` 并列三候选、"不进本批 DDL" | 文档行号 |

### 1.4 数据层（X-23 ~ X-33）

| # | 事实 | 证据 |
|:--:|:--|:--|
| X-23 | **schema 语义反义**：`ecos_cognitive` 11 张表**全 0 行**，且表名是 `ecos_biz_contract/department/metric/project/target`、`ecos_goal_tracking`、`ecos_wm_causal_link/goal/goal_log/scenario`、`ecos_world_scenarios`（业务域/世界模型资产）；认知真身（hypothesis 7/belief 9/evidence 5/model 1/run_invalidation 1/scenario_run 12/scenario_binding 8/_link 2/business_scenario 3/wm_scenario 8/world_scenario 7/_impact 10/kb_cognitive_pipeline 0）**全在 `public`** | 逐表 `count(*)` |
| X-24 | 认知↔public 镜像 **11 对**，引擎侧全 0 行；PK 宽度不一致：`varchar(64)`↔`varchar(20)`（biz_contract/department/project）、`varchar(64)`↔`varchar(64)`（world_scenarios）、唯一一致为 36 的是 `ecos_goal_tracking`；`ecos_wm_causal_link`、`ecos_wm_goal` **两侧均为 bigint nextval**（双方同时违 MC01） | pg_catalog PK 比对 |
| X-25 | **MC01**：目标集 **9 张表 9 列 `id bigint default nextval`**（`ecos_cognitive` 6 + `public.ecos_wm_scenario` + `public.kb_cognitive_pipeline` + `ecos_data.ecos_cognitive_rule`）；根因在迁移：`V22:7 BIGSERIAL`、`V117:22 BIGSERIAL`、`V146:9 BIGINT GENERATED ALWAYS AS IDENTITY` | DDL 审计 |
| X-26 | **MC02**：**13 张表 16 个 jsonb 列** — `ecos_ai.forecast.values`/`scenario.assumptions`/`simulation.config`/`simulation_result.output_state+predictions`、`ecos_data.ecos_cognitive_rule.action_config`、`public.ecos_business_scenario.metrics`、`ecos_cognitive_belief.distribution`、`ecos_cognitive_evidence.blob+refuting_evidence_ids`、`ecos_cognitive_hypothesis.evidence_ids`、`ecos_cognitive_model.features`、`ecos_scenario_run.diagnosis_result+forecast_result+simulation_result+strategy_result`、`kb_cognitive_pipeline.config+result` | information_schema |
| X-27 | **DR06~DR08 无一表全合规**：`ecos_cognitive` 全 11 表缺 7 基线列、`ecos_ai` 4 表缺 7、`ecos_data.ecos_cognitive_rule` 缺 7、`public.ecos_wm_scenario` 缺 7；`ecos_cognitive_evidence` **缺 domain**（与 `V159:4` 注释声称"V127 evidence 已有 domain"直接矛盾——V127 CREATE 无该列、实库亦无）；`kb_cognitive_pipeline` 用非标准名 `created_at/updated_at` | 列清点 + 迁移对读 |
| X-28 | 迁移单源目录 125 文件、最高 `V163`，认知关键词命中 18 文件，其中 **17/18 裸表名**（违 §四附则1），唯一 schema 限定是 `V163:16 ecos_cognitive.ecos_cognitive_pipeline`；**目录内版本号重复**（`V150__…doc_anchor_json` 与 `V150__ecos_scenario_sandbox_layout`；`V155__kb_nav_category` 与 `V155__lineage_node_pipeline_task_id`）——仅因 `spring.flyway.enabled:false`（`gateway/application.yml:29-30`、`aiming/application.yml:31-32`）才未暴露 | `ls` + grep |
| X-29 | 迁移↔实库漂移 3 处（表不存在）：`ecos_scenario_mind`(V146)、`ecos_scenario_sandbox_layout`(V150，`ScenarioSandboxLayoutService.java:122` 仍 INSERT)、`ecos_cognitive.ecos_cognitive_pipeline`(V163)；另 `ecos_cognitive_rule` 实库落 `ecos_data` 而 V22 裸名（落点与脚本不符） | `to_regclass` |
| X-30 | **列级漂移必败**：`public.ecos_scenario_binding` 实存 10 列（止于 `is_deleted`），无 V147 的 `target_id/target_type`；而 `ScenarioService.java:349` 的 INSERT 含这两列 → 运行期 SQL 异常，被 `catch → ApiResponse.internalError` 吞成 500 | 列比对 + 源码 |
| X-31 | **声明停写 ≠ 实际停写**：`V163:1-7` 头注声明 `public.kb_cognitive_pipeline` 为 knownLegacy 停写、权威迁 `ecos_cognitive.ecos_cognitive_pipeline`；实测代码仍全部读写旧表（`CognitivePipelineRepository.java:62/68/82/91/98`），新表**不存在且 Java 侧零引用** | 迁移头注 + grep |
| X-32 | **跨域写表**：认知代码 INSERT/UPDATE `sys_config`（属 sysman，`CognitiveConfigQueryService.java:15/66/71-74`，其中 `:66` 用 `gen_random_uuid()`）与 `sys_compliance_rule`（属 kb，4 处）；裸名触及表集 19 张（`ecos_cognitive_belief` 5、`ecos_provenance_entry` 3、`ecos_cognitive_evidence` 3、`kb_cognitive_pipeline` 5、`ecos_decision` 2、`ecos_cognitive_hypothesis` 3、`ecos_scenario_run` 2、`ecos_decision_policy/_exception/_causal_link` 各 1、`ecos_cognitive_run_invalidation` 1、`ecos_cognitive_model` 1、`ecos_diagnostic_agent` 1、`ecos_ceo_causal_chain` 1） | SQL 抽取 |
| X-33 | **三层纪律失守**：0 个 MyBatis Mapper（`mybatis-spring-boot-starter` 在 `cognitive-engine-impl/pom.xml:27-30`、`mybatis.mapper-locations` 在 `aiming/application.yml:48-49` 与 boot yml:15-16 皆空转），全部 `JdbcTemplate` 字符串 SQL 位于 Store/Service 层 → 违 §五"Service 禁 JdbcTemplate 直连"。SQL 违规：`::jsonb` **7 处**（`BeliefStore:64`、`CognitivePipelineRepository:63,70`、`EvidenceStore:81,141`、`HypothesisStore:58`、`CognitiveInvalidationConsumer:143`）、`ILIKE` 2 处（`DecisionServiceImpl:80,81`）、`gen_random_uuid()` 1 处；**合规项**：无 `ON CONFLICT`/`RETURNING`/物理 `DELETE FROM`（软删 `CognitivePipelineRepository:98 SET is_deleted=1`） | grep 分类计数 |

### 1.5 底座、事件与异常（X-34 ~ X-41）

| # | 事实 | 证据 |
|:--:|:--|:--|
| X-34 | **LLM 零经 llm-gateway**：认知 main 源码无一处 llm-gateway 客户端；因果链补全经 `SuggestionBuilder.java:34` 硬编码常量 `AGENT_LOOP_URL="http://localhost:8080/api/v1/agent-loop/chat"` + `:40 new RestTemplate()`（匿名 `SimpleClientHttpRequestFactory`）；`CausalReasonerServiceImpl.java:132-138` 调用它，`catch → log.warn` 后回落规则。aiming 虽依赖 llm-gateway（`aiming/pom.xml:49-53`）却只贡献一个未池化共享实例（`AimingInfrastructureConfig.java:31-33`，注释 `:25-26` 自陈） | 源码 |
| X-35 | `new RestTemplate()` **×4**：`EngineCapabilityRegistryImpl.java:55`、`EntityLinker.java:48`、`ScenarioSimulatorServiceImpl.java:46`、`SuggestionBuilder.java:40`（违"禁 new 绕过装配"） | grep |
| X-36 | 硬编码 localhost ×4 + `@Value` 默认 localhost ×2：`EngineCapabilityRegistryImpl.java:38 INTERNAL_BASE="http://localhost:8080"`（且 `:112` 用认知自身前缀 `/api/v1/knowledge/reason` 经 8080 回环）、`EntityLinker.java:42-43`、`ScenarioSimulatorServiceImpl.java:41 ${ecos.agent.completion.url:…8080…}`（**借 ai 域前缀键 `ecos.agent.*`**，违铁律 §十 配置单源）、`KbRestClientConfig.java:73 ${ecos.kb-base:…8080…}`（该键**全部 yml 未定义**）；另 `ecos.cognitive.neo4j-enabled`（`DiagnosisController.java:29`）**全仓未定义**，与 aiming yml 的 `ecos.neo4j.enabled`（`:62-63`）不同名 | `@Value` 清点 |
| X-37 | **绕过 ArchUnit 的自建并发**：`CognitivePipelineExecutor.java:27 Executors.newFixedThreadPool(4)` + `:68-99 CompletableFuture.runAsync/allOf`；`KnowledgeReasonerService.java:330-356` 三路并行 + 10s 超时。门禁 `businessMustNotSelfSchedule`（`ModuleDependencyArchTest.java:366-403`）**只禁** `@Scheduled` 与 `ScheduledExecutorService` → 固定线程池不触发（同 C71/C86 的门禁结构性盲区家族） | 源码 + 规则文本 |
| X-38 | **本域唯一合规基线**：`MentalScanTask.java:13/52/61` 经 `ITaskManagementService`，`:71-86 @PostConstruct` 用 runtime-task `TaskSchedulerService.schedulePeriodicTask` 注册周期任务 → F05-08 以它为收敛样板 | 源码 |
| X-39 | 事件/审计：5 处合规 `EventBusService.publish(KafkaTopics.AUDIT, …)`（`CognitiveBeliefService.java:287`、`CognitiveEvidenceService.java:220`、`CognitiveHypothesisService.java:216`、`mental/CognitiveInvalidationConsumer.java:207`、`mental/CounterfactualSimulator.java:435`），但**底座缺失即 fail-open**（ObjectProvider + `log.warn("…EventBusService 未装配…")` 继续，`CognitiveBeliefService.java:276`）；`CognitiveConfigController.java:120-127` 以 `log.info("AUDIT topic=ecos.audit action={} detail={}")` 冒充审计（PUT `:117` 调用），javadoc `:119` 自认"log 兜底；正式 Kafka 后切换"。反射 `getMethod("send"` **零命中**（优于本体/知识域同型问题） | 源码 |
| X-40 | **合规项**：认知/aiming 无 MinIO/runtime-access/`java.io.tmpdir` 使用（零命中）；`NewsFeedReader` 只解析请求体 markdown，无文件/URL IO；无 `@Scheduled`/`ThreadPoolTaskExecutor`/`new Thread(` | grep |
| X-41 | 异常：18 处 `catch (Exception)`（Decision 6、CognitivePlanner 4、CognitivePipeline 3、Config/Provenance/Wave3Demo/Diagnosis 各 1）；无本地 `@RestControllerAdvice`；认知/aiming **自定义异常类 0**（`model/DecisionException.java:9` 是 POJO 非 throwable）；失败响应泄漏内部信息——`ApiResponse.internalError("…: " + e.getMessage())`（`CognitivePipelineController.java:95/136/159`、`DecisionController.java:51/76/94/110/122`）；**fail-open 空值降级 40 处** `Collections.empty*`，并被写进制度：`KbRestClientConfig.java:57-58` 类注释规定"catch Exception → log.warn + 返回空值(emptyList/emptyMap/null)"（示例 `:110-113`），`EvidenceStore.java:204-217`"解析失败返空不抛"、`BeliefStore.java:156` | grep + 源码 |

### 1.6 伪实现与 stub（X-42 ~ X-45）

| # | 事实 | 证据 |
|:--:|:--|:--|
| X-42 | 健康端点无探测：`CognitiveEngineHealthController.java:49-58` 每个 `"UP"` 都是 map 字面量 | 源码 |
| X-43 | world-model 全为固定值：`WorldModelServiceImpl.java:16-58` — `getCurrentState()` 返回空 map、`simulate()` 固定 `confidence 0.75`、`recommendStrategy()` 固定 0.15/0.3、`getCausalGraph()` 返回一条硬编码边 `CustomerSatisfaction→RenewalRate w=0.8`；而 `WorldModelController.java:11-27` 有 4 个 handler 对外，前端 `cognitiveEngineApi.ts:321-343` 正在调用 | 源码 |
| X-44 | 伪状态：`DiagnosisController.java:32-34` `/diagnose/history` 读进程内 `ConcurrentHashMap`（最近 10 条，重启即失，非库）；`:177-178 neo4jOk=true`、`:193 llmOk=true` 硬编码；`ScenarioController.java:152-167` `/list` 返回 5 条硬编码模板；`Wave3DemoController.java:83-85` 空输入回落内置 demo markdown | 源码 |
| X-45 | ai 侧桩版本：`ai-engine/.../CognitiveController.java:13-23` 类注释自陈"仅声明 class 不暴露 `/api/cognitive/**` endpoint"，而前端 `api.ts:2483` 正在 `POST /api/v1/cognitive/reason`；`:2501` 又调 `/api/v1/cognitive/blueprint`（全仓无映射）→ 两个认知前端调用打到桩/无映射路径 | 两处 |

### 1.7 前端与测试（X-46 ~ X-52）

| # | 事实 | 证据 |
|:--:|:--|:--|
| X-46 | **"认知四件套"三份实现并存**：`pages/scenario/CognitionPanel.tsx`(737 行，经 `/runs`) ∥ `pages/aiworkbench/CognitionView.tsx`(489 行，头注释 `:5-11` 自陈"与 CognitionPanel 共享 workspace REST 但独立渲染") ∥ `pages/aiworkbench/cognition/CognitionWorkbench.tsx`(102 行 + 7 个 tab 组件，走 `cognitiveEngineApi`)；因果图另有三处（`CognitionPanel.tsx` 自绘、`cognition/causalAnalysis.tsx`、`components/CausalGraphView.tsx:3`） | `wc -l` + import 链 |
| X-47 | 死代码三件**零 importer**：`CognitionView.tsx`（唯一引用是自身测试 `CognitionViewE2E.test.tsx:23`）、`aiworkbench/cognition/barrel.ts`（`CognitionWorkbench.tsx:16-22` 绕过 barrel 逐个 import）、`aiworkbench/CognitiveOperatingSystem.tsx`（`main.tsx:16` lazy 指向 `./pages/CognitiveOperatingSystem.tsx` 另一个文件） | importer grep |
| X-48 | **方法不匹配 → 405**：`CognitionView.tsx:206-208` 以 GET 调 `/scenarios/{id}/cognitive/{ep}`，后端 `ScenarioCognitiveController.java:37/51/65/96` **仅 `@PostMapping`**，且其方法签名无 `mind` 参（契约要求的 `?mind=` 无载体） | 两侧源码 |
| X-49 | 死调用 5 组（后端零映射）：`/api/v1/cognitive/reason`（X-45 桩）、`/api/v1/cognitive/blueprint`（`api.ts:2501`）、`/scenarios/{id}/minds/base`（`sandbox/api.ts:167/177/187`）、`GET /decision/{id}/rules`（`cognitiveEngineApi.ts:306`，后端实为 `POST /{id}/check-rules` `DecisionController.java:127`）、`/api/v1/evolution/trigger` + `/evolution/log/{mid}`（`CognitiveOperatingSystem.tsx:32/50`）；同文件另有两套互斥 replay 签名（`cognitiveEngineApi.ts:171-176` POST 无 version 段 vs `ReplayTab.tsx:129` GET 带 version） | 逐项对照 |
| X-50 | API 层：`src/api.ts` **2,625 行 / 184 个 export**；`/api/v1/cognitive` 常量在 3 文件重复定义（`api.ts:2475`、`cognitiveEngineApi.ts:4`、`knowledgeApi.ts:31`）；`WM_BASE`(`api.ts:1067`) 与 `CAUSAL_BASE`(`:1188`) 同值双别名；`/api/v1/engine/cognitive` 被 ai（`AiEngineStatusController.java:11/:20`）与 cognitive2（`CognitiveEngineOpenHealthController.java:54/:106`）同时占用；除 `CognitionPanel.tsx:150`（`/runs?limit=20`）外**所有列表调用无 limit**（含 `fetchModels:188`、`fetchBeliefs:132`、`fetchHypotheses:67`、`fetchPipelines:229`），E2 的 Top-N/`?limit=` 前端零命中 | 源码 |
| X-51 | **auth 旁路**：`services/auth.ts:2` 自称"全仓唯一 authHeaders 定义处"（`:3-17` 三 key union `getAuthToken()`），但主链路 `services/httpClient.ts:243/270/301` 在 `apiFetch/apiFetchData/doFetch` 内各自 ad-hoc 拼 `Bearer` 且**只读单 key `localStorage['token']`** → 所有 scenario/cognition 调用实际不走唯一通道 | 两处源码 |
| X-52 | 前端规范与测试：硬编码中文 13 文件（`CognitiveEngineView.tsx` 29、`scenario/ScenarioEditor.tsx` 27、`cognition/overview.tsx` 19…）；硬编码 Tailwind 色 **1,058 处/19 文件**（`hypothesis.tsx` 146、`situationDiagnosis.tsx` 128、`overview.tsx` 116…）；`ScenarioEditor/ScenarioList/SimulationResultPanel` 从不 import `useTheme/useLanguage`；使用键 395 中 **141 个在任何 locale 文件不存在**（`causal.*`/`hypo.*`/`state.*`/`model.*`/`diagnosis.*` **五个命名空间整体缺失**，全靠 `t(key,'中文fallback')` 运行；locale 实测 scenario 293、cognition 175、aiworkbench 495 键，zh/en 对齐）；`aria-*` 仅 3 处；移动端复用件 0；手写 SVG sparkline `CognitionPanel.tsx:697-709`（`SandboxCanvas.tsx:39-40` 用 @xyflow 合规）；轮询 `setInterval` 10s/3s（`ScenarioManagementView.tsx:202`、`CognitiveOperatingSystem.tsx:60`）**而契约要求的 30s 轮询 + debounce 100–300ms 零命中**；`localStorage` 当缓存 `ScenarioManagementView.tsx:139/142/182/185`（`ecos_cached_git_commits/branches`）+ `:473-474` 假 git push 动画；TODO/mock/stub 55 处/20 文件（`sandbox/api.ts:9` 注释"本期 stub"，`:218` 实改走 `/runs`）。**测试**：`ScenarioCognitionIntegrationTest` **不存在**（三处文档验收产物缺失）；认知 16 个测试类 89 个 `@Test` 全为纯 JUnit/Mockito，**`@SpringBootTest` 零命中、无一连真实 DB**（`PrecedentRecallerTest.java:16/24-25` 反而用"无 DataSource 的 JdbcTemplate"模拟 DB down）；**workspace 服务 src/test 文件数 = 0**；`ecos-tests` 3 个 .mjs 对 scenario/cognition **零命中**；`playwright.config.ts` **全仓零命中**；前端仅 3 个相关测试（`CognitionViewE2E.test.tsx` 测的是 X-47 死代码） | 逐项实测 |

## 二、A 章 — 功能设计（F05-01 ~ F05-17）

> 每项含：功能陈述 / 规则锚点 / 现状差距（X 编号）/ 设计要点 / **可执行验收标识**（`mvn -Dtest=类#方法` 或 `*.spec.ts` 用例名；依 Q13，写"人工确认""Playwright 通过"视为虚假验收）。
> 用例名冻结约定：E2E 一律 `tests/scenario-cognition.spec.ts` 内 `test("COG-xx …")`；集成测试类名一律以 `Cognitive` 起头并落在 `ecos_backend/tests` 对应模块 `src/test/java`。

### F05-01 契约单源与路由唯一性定版

**陈述**：认知域接口契约以 `docs/40-实现/features/20260917-scenario-workbench/BUSINESS_SCENARIO_COGNITION_DOC.md` 修订版（v2.0，本册 §7.1 订正清单落笔后）为**唯一真源**；Java 侧以 `cognitive-engine-api` 包内的路径常量类 + 枚举类为代码侧唯一真源；前端以 `services/cognitiveClient.ts`（新建，归一 `api.ts`/`cognitiveEngineApi.ts`/`knowledgeApi.ts` 三处重复常量）为唯一通道。
**差距**：X-9、X-14、X-15、X-16、X-17、X-49、X-50（8 处契约复写、3 种前缀、2 套 capabilityMask 类型、`/api/v1/engine/cognitive` 双占）。
**要点**：
1. 新增 `com.chinacreator.gzcm.engine.cognitive.api.CognitiveApiPaths`（`public static final String` 常量 + `SCENARIO_COGNITION_TEMPLATE = "/api/v1/business/scenarios/%s/cognition"`），Java 侧禁止再出现字面量路径（ArchUnit 谓词见 F05-17）；
2. `/api/v1/engine/cognitive/**` 健康族属主判给 cognitive（ai 侧 `AiEngineStatusController.java:11/:20` 迁往 `/api/v1/ai/engine-status`，**新增别名 + 旧路径 Deprecation 头，不做破坏性改动**）；
3. `RouteUniquenessTest` 扩展：断言同一 `类级+方法级` 全路径在**全部宿主进程集合**（gateway fat-JAR / aiming / workspace）内唯一，双占即 FAIL；
4. 前端三处 `/api/v1/cognitive` 常量合并入 `services/cognitiveClient.ts`，`/api/v1/engine/cognitive` 单别名。

**验收**：`mvn -Dtest=CognitiveContractSingleSourceTest#noInlineCognitivePathLiterals`、`mvn -Dtest=RouteUniquenessTest#engineCognitivePrefixHasSingleOwner`、`mvn -Dtest=RouteUniquenessTest#scenarioCognitionFourEndpointsAreAddressable`（对 `RequestMappingHandlerMapping` 实际 bean 断言，非源码 regex）。

### F05-02 Mind 资产落点与注册/绑定（E1 数据源从"不存在"建成）

**陈述**：E1 的 `kb_mind_registry` 与场景 Mind 绑定当前**物理不存在**（X-19：全库无 `%mind%` 表、`mind_id` 列 0 个、`ecos_scenario_mind`(V146) 库内缺失而 `ScenarioMindService.java:84-115/119` 仍在 INSERT）。本项把 Mind 定义为**认知模型资产**（ADR-8 正名，明令禁用 `kb_*` 前缀误名），落控制域 `ecos_cognitive`，建两张表 + 迁移到位 + 存在性预检。
**差距**：X-18、X-19、X-24、X-30。
**要点**：
1. 表名 `ecos_cognitive.ecos_cognitive_mind`（注册表）与 `ecos_cognitive.ecos_cognitive_scenario_mind`（场景绑定），DDL 见 E.2 `V187`/`V188`，严格 MC01（`VARCHAR(36)` + 应用侧 UUID）/MC02（`capability_mask` 用 TEXT JSON 数组，禁 jsonb）/DR06~DR08 七基线列；
2. **`kb_mind_registry` 一律改名为 `ecos_cognitive_mind`**，文档、代码、D 章契约同步（订正清单 §7.1 Cg-2）；
3. `V146` 缺失的 `ecos_scenario_mind` 以新表替代（**不重建 legacy 名**，避免第 4 套孪生），`ScenarioMindService.java:84-115/119` 的 INSERT 改指新表；旧写点在切换前必须**先停写**（禁双写造成不可归因）；
4. 落点归属为**R-13 ① 已批准**（认知域表落点：`ecos_cognitive` 现被 11 张 0 行业务/世界模型表占用，认知真身 14 张在 `public`）；本项按已批准口径（新建 `ecos_cognitive_mind`/`ecos_cognitive_scenario_mind` 入 `ecos_cognitive`）写作，并依报告 §14.4 授权**落迁移脚本文件**（`V187/V188` 归入本轮单源目录接续号段），**不实跑库**；
5. 预检：`to_regclass('ecos_cognitive.ecos_cognitive_mind')`，缺失 → 引擎抛 `ECOS-COG-503 schema not ready`（区别于 `not enabled`，REQ-COG-01 §1.2 末行）。

**验收**：`mvn -Dtest=CognitiveSchemaPreflightTest#mindTablesResolveBeforeE1`、`mvn -Dtest=ScenarioCognitionIntegrationTest#e1ReturnsEmptyArrayWhenScenarioHasNoMind`（**200 + `data:[]`，禁 404**）、`mvn -Dtest=ScenarioMindWritePathTest#noInsertIntoDroppedLegacyMindTable`（源码断言）。

### F05-03 E1 `GET .../cognition/detect` 建成

**陈述**：按 PRD-05 §1.1 E1 契约实现场景可用 Mind 探测：JOIN `ecos_cognitive_mind` × `ecos_cognitive_scenario_mind`，仅返 `is_active=1` 且 `is_deleted=0` 行，字段 `mindId/name/capabilityMask[]/closedLoopBounds[]/isActive`。
**差距**：X-14（前后端双零命中）、X-48（现有近似端点仅 POST 且无 `mind` 参）、X-22（无 Mapper）。
**要点**：只读（仅 SELECT，B-2 铁律）；宿主属主按 **R-14 ① 已批准**定版（契约前缀 `/api/v1/business/scenarios/{id}/cognition/*` 属 cognitive，而实现近似体在 workspace 侧 `/api/v1/workspace/scenarios/{id}/cognitive/*`；本册推荐 **cognitive 属主 + workspace 编排转发**，依铁律 §0.6"场景层只编排不生产"）；`closedLoopBounds` 枚举 `PRE/POST`，非法值读侧容错为 `[]` + issue 记录（不阻断）。
**验收**：`mvn -Dtest=ScenarioCognitionIntegrationTest#e1DetectHappyPathFieldsAndActiveOnly`、`#e1Detect404WhenScenarioMissing`、`#e1Detect404WhenScenarioSoftDeleted`、`tests/scenario-cognition.spec.ts › COG-E1 右栏探测列表渲染`。

### F05-04 E2 `GET .../cognition/operation-eval?mind=` 建成（确定性评分）

**陈述**：实现 `score(belief_i) = belief.probability × trend_weight(belief.domain, scenario)`，本期 `trend_weight` 恒为 1（配置项 `ecos.cognition.eval.trend_weight` 仅 Properties 入口，不实现动态），score desc 取 Top-N（默认 10，`?limit=` 可调，**上限 50 硬截断**），`trend` 本期恒 `FLAT` 且必须真实标注。
**差距**：X-14（零命中）、X-50（前端列表调用无 `limit`，Top-N 零命中）、X-41（缺省即空集 fail-open）。
**要点**：
1. **数值确定性**：`trend_weight` 与 score 计算全部由 Java 算术完成，禁 LLM 参与（B-2 / 铁律 §0.2.1）；计算入口单一 `CognitiveEvalCalculator`（可单测、可复算）；
2. `limit` 校验：`limit<=0` → 默认 10；`limit>50` → 截断 50 并在响应 `data.truncated=true`（禁静默丢数据）；
3. `mind` 不属于该场景 → 400 `mind not found on this scenario`，**禁静默兜底返回全部**；
4. `trend` 恒 `FLAT` 由常量枚举给出，响应体附 `trendSource="NOT_IMPLEMENTED"`（禁伪造趋势，REQ-COG-01 §1.1 E2 明示）。

**验收**：`mvn -Dtest=CognitiveEvalCalculatorTest#scoreEqualsProbabilityWhenWeightIsOne`、`#topNTruncatesAtFiftyAndFlagsTruncated`、`mvn -Dtest=ScenarioCognitionIntegrationTest#e2RejectsMindNotOnScenario`、`tests/scenario-cognition.spec.ts › COG-E2 评分排序与 FLAT 标注`。

### F05-05 E3 `GET .../cognition/hypotheses?mind=` 建成（含状态机收敛）

**陈述**：假设查询支持无 `mind` → 场景全部；带 `mind` → `AND mind_id = ?` **严格过滤**；恒含 `is_deleted = 0`；排序 `updatedAt desc`；`confidence ∈ [0,1]`，前端三色分段渲染。
**差距**：X-15、X-20（三套状态枚举）、X-21（7 行存量 `INVALIDATED`）、X-48。
**要点**：状态枚举终态为 `PROPOSED/EVIDENCED/BELIEVED/REFUTED`（**R-15 ① 已批准**——终态仅对新数据生效 + 存量 7 行映射留痕，`CHECK` 待全量切换后补：库内实存 `VALID/INVALIDATED/ARCHIVED` 7 行的映射与 `CHECK` 约束改造；本册推荐 `VALID→BELIEVED`、`INVALIDATED→REFUTED`、`ARCHIVED→REFUTED` + 保留 `old_status` 审计列，不伪造历史），读侧在收敛期用 `HypothesisStatusLegacyAlias` 兼容映射（只读映射，禁写入旧值）。
**验收**：`mvn -Dtest=ScenarioCognitionIntegrationTest#e3StrictMindFilterNoFallback`、`#e3NeverReturnsSoftDeletedRows`、`mvn -Dtest=HypothesisStatusMachineTest#onlyFourCanonicalStatusesAreWritable`、`tests/scenario-cognition.spec.ts › COG-E3 置信度三色分段`。

### F05-06 E4 `GET .../cognition/beliefs?mind=` 建成（capability 投影）

**陈述**：belief 列表按 `updatedAt desc`；每行 `capability = mind.capability_mask ∩ belief.domain 映射`，本期字符串直等（F05-11），**交集为空 → `capability=null` 且不丢弃该行**（前端显示"未映射"）。
**差距**：X-16（前端 `/beliefs` 无 mind 参、无 limit）、X-25（`public` 内 belief 表 6 张 PK 为 bigint nextval）。
**要点**：未映射行必须写入 `data.issues[]`（可观测，禁静默）；`probability`/`strength` 为 `NUMERIC(18,4)` 列（MC01 合规）。
**验收**：`mvn -Dtest=ScenarioCognitionIntegrationTest#e4UnmatchedDomainKeepsRowWithNullCapability`、`#e4OrdersByUpdatedAtDesc`、`tests/scenario-cognition.spec.ts › COG-E4 未映射显示为占位而非丢弃`。

### F05-07 错误矩阵与 503 穿透（禁 200 空 body）

**陈述**：四端点统一实现 PRD-05 §1.2 六行错误矩阵（404 场景不存在 / 404 已软删除 / 400 mind 不属场景 / 400 mind 不存在 / 503 引擎未启用 / 503 schema 未就绪），`ApiResponse.code` 与 HTTP 状态一致。
**差距**：X-39、X-40（18 处 `catch (Exception)` 泄漏 `e.getMessage()`；40 处 `Collections.empty*` 空集回落，`KbRestClientConfig.java:57-58` 把 fail-open 写成制度）、X-42~X-44（伪实现）。
**要点**：
1. cognitive 局部 `@RestControllerAdvice`：`DataAccessException`/表缺失 → 503 `ECOS-COG-503`（`cognitive schema not ready`），**不得落入网关 Exception→404**（分册 04 C76 同族三级掩蔽）；
2. 引擎未启用（`ecos.cognitive.enabled=false` 或 LLM 网关不可用致能力关闭）→ 503 `cognitive engine not enabled`，**原样穿透**（用户裁定 A3）；
3. 禁 `catch (Exception) → Collections.emptyList()`：认知域内所有空集返回必须来自"查询确实无行"，由 `CognitiveNoEmptyFallbackTest` 以源码 regex + 断言双重拦下；
4. `e.getMessage()` 不得入响应体（只入日志 + traceId）。

**验收**：`mvn -Dtest=CognitiveErrorMatrixIntegrationTest#{scenarioNotFound,scenarioSoftDeleted,mindNotOnScenario,mindNotFound,engineDisabled,schemaNotReady}`（6 个方法名与矩阵 6 行一一对应）、`mvn -Dtest=CognitiveNoEmptyFallbackTest#noCatchAllReturningEmptyCollection`、`tests/scenario-cognition.spec.ts › COG-503 引擎停止时右栏置灰 banner`（联动 PRD-08 WS-03）。

### F05-08 近似实现与死调用归一

**陈述**：现有 `/api/v1/cognitive/hypotheses|beliefs`（无 mind 参、GET/POST 混乱）与 5 组后端零映射死调用，统一改为"新增契约路径为准 + 旧路径别名（`Deprecation: true` + `Sunset` 日期）"；两套互斥 replay 签名定版为一种。
**差距**：X-15、X-16、X-48、X-49。
**要点**：`/scenarios/{id}/minds/base`、`/api/v1/cognitive/blueprint`、`/api/v1/cognitive/reason`、`GET /decision/{id}/rules`、`/api/v1/evolution/*` 五组：或补后端映射或前端删除调用，**禁保留"前端在调、后端必 404"的静默死链**；`CognitionView.tsx:206-208` 的 GET 与 `ScenarioCognitiveController` 的 POST 必须定版（契约要求 GET，只读）。
**验收**：`mvn -Dtest=CognitiveDeadCallAuditTest#everyFrontendCognitivePathHasBackendMapping`（读前端 `cognitiveClient.ts` 路径集合 × 后端 mapping 集合做差集，差集必须为空）、`mvn -Dtest=RouteUniquenessTest#cognitiveReplaySingleSignature`。

### F05-09 REQ-COG-02 领域化因果诊断（P1）

**陈述**：`POST /api/v1/cognitive/diagnose` 增可选 `forecastContext` 块（`forecastRunId/caliberId/caliberVersion/asOfTime/projectId/departmentId`），响应新增 `chains[]`（`path/contribution/evidenceRefs/statSupport/unverifiedHypotheses`）。
**差距**：X-17（`forecastContext`/`unverifiedHypotheses`/`chains` 代码零命中）、X-43、X-44。
**要点**：
1. **只增不改**：无 `forecastContext` 时维持既有通用诊断行为与响应 schema（向后兼容，REQ-COG-02 §2.4-3）；
2. `contribution` 必须由 `ForecastRunSnapshotReader`（调预测运行服务只读端点，PRD-09 FC-02）取快照后**确定性复算**，禁 LLM 生成数值（B-2）；
3. 证据/假设分离：仅当边存在 `evidenceRefs` 或 `statSupport` 才入 `chains.path`，否则归 `unverifiedHypotheses`；
4. `forecastContext` 存在时**锁定快照范围**，禁全库扫描；
5. `chains` 结构以 TEXT JSON 列存储属**禁止**（MC02 只禁 jsonb 不禁 TEXT，但 B-2"推理结果不落盘"优先：诊断结果**一律不落库**，只回响应 + 审计事件）。

**验收**：`mvn -Dtest=CausalChainDeterminismTest#contributionIsRecomputableFromSnapshot`、`mvn -Dtest=CognitiveIronLawsTest#diagnoseLeavesRowCountUnchanged`（调用前后 DB 行数不变，REQ-COG-05）、`mvn -Dtest=CausalChainEvidenceTest#unevidencedEdgeGoesToUnverifiedHypotheses`、`mvn -Dtest=DiagnoseContractRegressionTest#legacyRequestSchemaUnchanged`。

### F05-10 REQ-COG-03 反事实金额来源与 exploratory 隔离（P0）

**陈述**：经营预测语境下的情景模拟 = **确定性重算**：workspace 情景复制 → 预测运行服务以 `baselineRunId + overrides` 重算得新 `forecastRunId`（scenario 类型）→ cognitive 只对 diff 做归因与解释。`ScenarioSimulatorServiceImpl`（RAG+Agent 推演）禁止作金额来源。
**差距**：X-17（`overrides/baselineRunId/exploratory` 零命中）、X-31（跨域写）、X-35。
**要点**：
1. `overrides` 结构冻结（`projectId/departmentId/periods[]/target/stage/delta`），**只覆盖命中范围**，其余继承基准快照；
2. `ScenarioSimulatorServiceImpl` 所有出口强制置 `exploratory=true`，响应内**不得出现金额字段**（贡献额、利润差一律由运行服务给），UI 显示"非核算口径"；
3. 归因平衡校验：`sum(contribution) == scenarioProfit − baselineProfit`（容差 ±0.01），不平即 FAIL，由 `ScenarioBalanceChecker` 在响应前自检，失败 → 503 而非返回不平数据；
4. 情景运行满足六要素（独立 forecastRunId + 快照 + 口径版本），其产物落点 = **R-17 ① 已批准**（经 data-engine 写 `APPLICATION`/`ecos_dw`，ADR-14 保持；实测 `ecos_dw` 仅 doc/doc_chunk 两张 0 行，forecast 结果实际存 `public.ecos_scenario_run` 的 4 个 jsonb 列 → ADR-14 落地路径需裁定）；
5. 认知域**不得**直接写 `ecos_dw`/业务域表（现状 X-31 写 `sysman.sys_config`、`kb.sys_compliance_rule` 必须消除，见 F05-13）。

**验收**：`mvn -Dtest=ScenarioCounterfactualIntegrationTest#onlyOverriddenScopeDiffs`（其他项目/月份 diff=0）、`#contributionSumBalancesWithinCent`、`mvn -Dtest=ExploratoryGuardTest#ragSimulatorOutputHasNoMonetaryFields`、`tests/scenario-cognition.spec.ts › COG-3 探索式通道显示"非核算口径"`。

### F05-11 REQ-COG-04 capabilityMask 枚举与映射（P1）

**陈述**：`com.chinacreator.gzcm.engine.cognitive.api.mind.MindCapabilityMask`（cognitive-engine-api 包内，唯一权威）= `DETECT/FORECAST/SIMULATE/PLAN/REVIEW`；`belief.domain ↔ capabilityMask` 本期字符串直等 + 中文别名表（配置 `ecos.cognition.domain_alias`），匹配不到 → `capability=null` + issue 记录。
**差距**：X-13（两套 capabilityMask 类型：前端 `string[]` vs 后端 `String` 逗号分隔 vs 另一处枚举）、X-47。
**要点**：
1. 代码内两套并行定义（engine 内私有 enum + 字符串常量）删除，统一引用 api 包枚举；DB 存 TEXT JSON 数组，序列化只经 `MindCapabilityMaskCodec`；
2. 别名表落 `sysman` 配置门面（铁律 §十 配置单源），新增别名不改代码；
3. 正式版预留 KB 维度 recode（**本批不实现**，REQ-COG-04 §4.2-2），文档 §4.1 疑义已登记；
4. 枚举报表：`MindCapabilityMaskTest#enumMatchesContractDocTable` 以契约文档 §2.2 表为断言基准（读文档资源文件，防漂移）。

**验收**：`mvn -Dtest=MindCapabilityMaskTest#enumMembersAreExactlyFive`、`#enumMatchesContractDocTable`、`mvn -Dtest=MindCapabilityMaskCodecTest#roundTripThroughTextJsonArray`、`mvn -Dtest=DomainAliasConfigTest#unknownDomainYieldsNullCapability`。

### F05-12 REQ-COG-05 铁律守护接线（P0，守护型）

**陈述**：五项守护从"文档条文"变为"构建期/测试期可执行断言"。
**差距**：X-37（ArchUnit 谓词对反射与 bean 装配越界零可见，分册 03 C71 同族）、X-12、X-42~X-45。
**要点（五项逐条接线）**：

| 守护项 | 落地机制（可执行标识） |
|:--|:--|
| 推理结果不落盘 | `mvn -Dtest=CognitiveIronLawsTest#diagnoseForecastSimulateLeaveRowCountUnchanged`（三端点调用前后对 kb 三表 + 认知四表 count 比对） |
| 不新增业务事实表 | ArchUnit `cognitiveDomainMayNotCreateTablesOutsideFour`（扫 DDL 单源目录，认知域新表须登记白名单）+ DDL 评审 checklist；配合分册 02 `SchemaInventoryGateTest` |
| 不引入规则引擎 | `mvn -Dtest=CognitiveDependencyAuditTest#noRuleEngineOnClasspath`（pom 扫描 drools/easy-rules/mvel 等，SpEL 允许） |
| 禁 LLM 直接承担推理职责 | `mvn -Dtest=CognitiveNoLlmNumbersTest#chainsContributionScoreNeverFromLlmResponse`（LLM 出口响应仅允许落 `explanation`/`candidate` 字段）+ 评审 checklist |
| Context 不建表 | `mvn -Dtest=CognitiveIronLawsTest#noCognitiveContextTable`（断言 `to_regclass('…cognitive_context*')` 恒为 null） |

**验收**：上述 5 个 `mvn -Dtest=` 全绿，并纳入 CI `pr-gate`（分册 01 C 系列门禁矩阵同批）。

### F05-13 三层纪律迁移与 schema 限定（0 Mapper → Mapper 层）

**陈述**：认知域从"Service/Store 直连 `JdbcTemplate` 拼字符串 SQL"迁移到 Controller/Service/Mapper 三层（后端规范 §五、数据库访问规范 §五），并消除裸表名、跨域写、方言违例。
**差距**：X-22~X-33（0 Mapper、17/18 迁移脚本裸表名、`::jsonb` 7 处、`ILIKE` 2 处、9 列 nextval、16 个 jsonb 列、DR 列无一全合规、V150/V155 版本号重复、`ScenarioService.java:349` INSERT 引用不存在列 `target_id/target_type`、V163 声明停写未落地、跨域写 `sysman.sys_config` 与 `kb.sys_compliance_rule`）。
**要点**：
1. 新建 `cognitive-engine-impl/src/main/resources/mapper/*.xml`（namespace 与 Mapper 接口一一对应），Service 层删除 `JdbcTemplate` 字段；
2. 所有表名 `{schema}.` 限定（§四附则1 裸表名=FAIL），扩展 `SchemaDriftLintTest` 到认知域（分册 03 C69 同族机制复用）；
3. `::jsonb` → 列改 TEXT + Java 侧序列化（MC02）；`ILIKE` → `lower() LIKE`（MC03 多库兼容）；无 `databaseId` 分支的 `RETURNING`/`ON CONFLICT` 全部改应用侧 UUID + 显式 select；
4. 跨域读写改走对方引擎 REST（写经 data-engine 通道），认知 pom 移除对他域 Mapper 的依赖；
5. `V150/V155` 版本号重复与 `ScenarioService.java:349` 不存在列引用属 **M0**（结构必败），列缺失按"只加不删"补列或改语句，二选一在 E.3 定版。

**验收**：`mvn -Dtest=CognitiveLayerDisciplineTest#noJdbcTemplateInCognitiveServiceLayer`、`mvn -Dtest=CognitiveSqlDialectTest#noJsonbCastNoIlNoBareReturning`、`mvn -Dtest=SchemaDriftLintTest#cognitiveTablesAreSchemaQualified`、`mvn -Dtest=CognitiveCrossDomainTest#cognitiveWritesNoOtherDomainTables`。

### F05-14 鉴权两态拉平与租户过滤（默认 DENY）

**陈述**：同一批 50 个认知端点在 gateway 态需 JWT、在 aiming:18084 态**完全无鉴权**（X-10：aiming 排除 Security 自动装配 + header-auth=false）。本项把两态拉到同一水平：业务数据端点默认 DENY，过三滤波器。
**差距**：X-10、X-11、X-12（E1~E4 属业务数据端点，**不写 permitAll**）。
**要点**：
1. aiming 服务纳入 security 链路（要么内置 `SecurityConfig` 复用 security-engine 校验，要么**只允许经 gateway 承流**并在 service 态监听 `127.0.0.1` + 端口门禁，**R-16 ①+③ 已批准**（取前者 ⇒ aiming service 态必须能独立裁决；③ S3 门禁新增"两态鉴权等价测试"）；
2. 新增 Controller 三滤波器（`VersionPrefixRewriteFilter` 映射 + `SecurityConfig` 授权 + `ClearanceInterceptor` 豁免，双路径 `/api/v1/` 与 `/api/` 各写一遍）；
3. 租户/域过滤：`domain` 列进 WHERE（DR08），四端点查询恒含 `is_deleted=0`；
4. 越权两通道（直接 HTTP + 服务间 REST）均需拒绝，与分册 01 门禁一致。

**验收**：`mvn -Dtest=CognitiveAuthParityTest#sameEndpointsDenyInBothModes`（参数化 gateway/service 两态）、`mvn -Dtest=ScenarioCognitionIntegrationTest#e1ToE4RequireJwtAndClearance`、`tests/scenario-cognition.spec.ts › COG-越权 未登录访问右栏端点被拒`。

### F05-15 公共底座收口（LLM / 并发 / 审计 / 事件）

**陈述**：LLM 调用一律经 `runtime/llm-gateway`；并发与定时一律经 `runtime-task`；审计与事件一律经 `runtime-event EventBusService` 且 **fail-closed**。
**差距**：X-34（`SuggestionBuilder.java:34/:40` 自建 LLM 客户端）、X-35（4× `new RestTemplate()` 硬编码 `http://localhost:8080` agent-loop）、X-36（`Executors.newFixedThreadPool(4)` 绕过 `businessMustNotSelfSchedule`，`ModuleDependencyArchTest.java:366-403` 只看 import 图）、X-37、X-38（`MentalScanTask` 是**本域唯一合规调度样板**，以其为迁移参照）、X-39（`:276` 审计 fail-open）、X-40（`CognitiveConfigController.java:120-127` 手工 log 冒充审计）。
**要点**：
1. `SuggestionBuilder`/agent-loop 改注入 `LlmGatewayClient`；`new RestTemplate()` 全部删除，跨服务调用走 `ServiceEndpointResolver` + 共享客户端 bean（分册 00 两态寻址）；
2. `Executors.newFixedThreadPool` → `runtime-task` 提交；ArchUnit 增谓词"业务包禁 `Executors.new*` / `new Thread(` / `@Scheduled`"（补 C71 盲区）；
3. 审计：`ObjectProvider` 缺失即 `log.warn` 继续 → 改为"不可用则写本地兜底，兜底亦失败则**拒绝业务动作**"（fail-closed）；假审计（`:120-127`）删除，改发 `ecos.audit` 事件；
4. 事件：5 处 `EventBusService` 合规保留，`:276` 的 catch-continue 修好。

**验收**：`mvn -Dtest=ModuleDependencyArchTest#businessMustNotConstructRestTemplate`、`#businessMustNotSelfSchedule`（扩展谓词后）、`mvn -Dtest=CognitiveAuditFailClosedTest#auditSinkFailureRejectsAction`、`mvn -Dtest=CognitiveLlmGatewayTest#noLlmCallOutsideGateway`。

### F05-16 伪实现与 stub 显式化（禁 200 伪数据）

**陈述**：所有非真实实现改为**显式 stub**：返回 `501 Not Implemented` + `stub=true`，或真实降级 `503`，禁止 200 + 固定/内置数据。
**差距**：X-42（健康端点每个 `"UP"` 是字面量）、X-43（`WorldModelServiceImpl.java:16-58` 固定值 + 硬编码因果边 `CustomerSatisfaction→RenewalRate w=0.8`，而 `WorldModelController.java:11-27` 4 handler 对外、前端 `cognitiveEngineApi.ts:321-343` 在调）、X-44（`DiagnosisController.java:32-34` 进程内 Map 冒充历史、`:177-178/:193` `neo4jOk/llmOk` 硬编码 true、`ScenarioController.java:152-167` 5 条硬编码模板、`Wave3DemoController.java:83-85` 内置 demo markdown）、X-45（ai 侧 `CognitiveController.java:13-23` 桩而前端在调）。
**要点**：
1. 健康端点改为真实探测（DB `SELECT 1`、security-engine、llm-gateway、可选 Neo4j），每分量 `{status,checked:true}`；探测失败 → `DOWN`，禁字面量；
2. world-model 四端点：本期无实现 → `501 + stub=true`，前端渲染"未实现"占位（禁渲染固定 0.75/0.15/0.3）；
3. `/diagnose/history` 进程内 Map：改为读预测运行/审计事件侧真实存储，或 `501`；
4. `diagnose/preflight` 的 `neo4jOk/llmOk` 改为真实探测值；
5. `Wave3DemoController`/硬编码模板：加 `demo=true` 且只在 `ecos.cognitive.demo=true`（dev profile）下可达，生产 404。

**验收**：`mvn -Dtest=CognitiveStubHonestyTest#noLiteralUpInHealthPayload`、`#worldModelReturnsNotImplemented`、`mvn -Dtest=CognitiveHealthProbeTest#eachComponentIsActuallyChecked`、`tests/scenario-cognition.spec.ts › COG-stub 未实现能力显示占位而非假数据`。

### F05-17 前端认知域重构（详见 B 章）

**陈述**：三份"认知四件套"归一为一件、死代码三件删除、`api.ts` 拆分、auth 头单源、i18n 141 缺键补齐、契约要求的 30s 轮询 + debounce 落地、轮询与缓存合规。
**差距**：X-46~X-52。
**要点**：见 B.0/B.9；ArchUnit 不适用于前端，改由 `npm run lint`（`tsc --noEmit`）+ ESLint 自定义规则（禁 `localStorage['token']` 直读、禁裸 Tailwind 色）+ `cognitiveClient.ts` 单源断言测试。
**验收**：`npx vitest run src/services/__tests__/cognitiveClient.spec.ts -t "authHeaders come only from services/auth"`、`npx vitest run src/api/__tests__/cognitivePaths.singleSource.spec.ts`、`tests/scenario-cognition.spec.ts › COG-IA 认知四件套唯一入口`。

---

## 三、B 章 — 界面设计（信息架构归一 + 八要素）

### B.0 信息架构归一（三 → 一）

**现状三簇并存**（X-46）：`pages/scenario/CognitionPanel.tsx`(737 行，经 `/runs`) ∥ `pages/aiworkbench/CognitionView.tsx`(489 行，头注释自陈与 CognitionPanel 共享 REST 但独立渲染) ∥ `pages/aiworkbench/cognition/CognitionWorkbench.tsx`(102 行 + 7 个 tab，走 `cognitiveEngineApi`)；因果图另有三处自绘。**死代码**（X-47）：`CognitionView.tsx`、`cognition/barrel.ts`、`aiworkbench/CognitiveOperatingSystem.tsx` 三件零 importer。

**目标 IA**（唯一入口 = 场景工作台右栏 + 独立认知工作台 Tab）：

```
场景工作台 (scenario)
└─ 右栏 CognitiveRightPanel (新建，每 Tab 独立文件，≤800 行)
   ├─ DetectTab        ← E1  /cognition/detect
   ├─ OperationEvalTab ← E2  /cognition/operation-eval?mind=&limit=
   ├─ HypothesesTab    ← E3  /cognition/hypotheses?mind=
   └─ BeliefsTab       ← E4  /cognition/beliefs?mind=
认知工作台 (aiworkbench/cognition，保留 CognitionWorkbench 为唯一实现)
├─ overview / situationDiagnosis / causal / hypothesis / state / model / replay  (7 Tab，现有文件保留)
└─ 因果图组件单源 CausalGraphView (@xyflow/react)  ← 删除 CognitionPanel 自绘与 cognition/causalAnalysis.tsx
```

归一动作：`CognitionPanel.tsx` 的能力并入 `CognitiveRightPanel`；`CognitionView.tsx`（死代码 + 方法不匹配 405 的源头）**删除**并移除其测试 `CognitionViewE2E.test.tsx`；`barrel.ts` 要么被 `CognitionWorkbench.tsx:16-22` 实际使用、要么删除；`main.tsx:16` lazy 指向核实（`pages/CognitiveOperatingSystem.tsx` 与 `aiworkbench/CognitiveOperatingSystem.tsx` 二选一）。

### B.1 ~ B.8 八要素表

| # | 要素 | 设计 |
|:--:|:--|:--|
| B.1 | 目标用户 | 经营分析/预测口径的业务用户（读 E1~E4 认知证据）；平台治理者（口径与 Mind 配置，只经 sysman 配置门面） |
| B.2 | 核心任务 | 在场景上下文中"看见平台的认知"：可用 Mind → 运行评价 → 假设 → 信念，并在因果诊断中区分"证据支撑"与"待验证假设" |
| B.3 | 关键流程 | 选场景 → E1 探测 Mind（无 Mind → 空态文案"该场景未绑定 Mind"，**不是报错**）→ 选 Mind → E2/E3/E4 三 Tab 并行加载 → 点假设跳因果诊断（REQ-COG-02）→ 反事实入口（REQ-COG-03，标注核算口径 vs 探索口径） |
| B.4 | 信息结构 | 每 Tab 一个卡片列表：Mind（`name` + `capabilityMask` chips + `closedLoopBounds`）、评分行（`name/capability/score 0-1 进度条/trend 箭头`）、假设行（`reason/confidence 三色分段/status 四态徽标`）、信念行（`domain/probability/strength/capability 或"未映射"`） |
| B.5 | 状态与反馈 | 7 态矩阵：加载（骨架屏）/ 正常 / **空（200 `data:[]`）** / **故障（5xx 或 `ECOS-COG-*` → 显式错误态 + traceId 复制）** / **未启用（503 not enabled → 右栏整体置灰 banner）** / **schema 未就绪（503 → 运维文案）** / 未实现（`stub=true` → 占位）。**故障与空必须视觉可分**（解除 X-40/分册 04 C76 掩蔽链） |
| B.6 | 数据密度与分页 | E2 Top-N（默认 10，可调上限 50，`truncated=true` 显示"已截断"提示）；E3/E4 游标分页（`?limit=` 必传，禁无 limit 全量拉取，X-50）；30s 轮询 + debounce 100–300ms（契约要求，现 10s/3s 硬编码且无 debounce，X-52） |
| B.7 | 可访问性 | 全交互元素 `aria-label`（现仅 3 处 `aria-*`）；三色置信度不依赖颜色单一通道（配文字档位）；`role="status"` 播报加载/错误；键盘可达 Tab 切换；对比度过 `useTheme()` 4 主题（`slate-light/deep-space/cyber-terminal/royal-purple`）全部 AA |
| B.8 | 移动端 | 只复用既有三件（`useMediaQuery`/`useMobileSidebar`/`MobileDataTable`，现移动端复用件 0）；右栏在 `<768px` 折叠为抽屉；表格走 `MobileDataTable`，禁自绘 sparkline（`CognitionPanel.tsx:697-709` 手写 SVG 改为合规图表组件或删除） |

### B.9 前端工程约束落地项（对应 X-46~X-52）

1. **`src/api.ts` 2,625 行 / 184 export 拆分**：按域拆为 `services/{scenario,cognitive,data,knowledge,ontology}Client.ts`，`api.ts` 仅保留 re-export 兼容层（一周期后删除）；每文件 ≤800 行（前端规范）。
2. **auth 头单源**：`services/httpClient.ts:243/270/301` 三处 ad-hoc `Bearer` 拼接（只读单 key `localStorage['token']`）删除，统一 `getAuthToken()`（`services/auth.ts:3-17` 三 key union）；ESLint 规则 `no-restricted-syntax` 禁直读 `localStorage['token']`。
3. **i18n**：补齐 141 个缺失键（`causal.*`/`hypo.*`/`state.*`/`model.*`/`diagnosis.*` 五命名空间整体新建，zh/en 对齐），删除 `t(key,'中文fallback')` 的 fallback 字面量（13 文件硬编码中文改 `t()`）。
4. **主题**：1,058 处硬编码 Tailwind 色（19 文件，`hypothesis.tsx` 146、`situationDiagnosis.tsx` 128、`overview.tsx` 116 优先）改为 `useTheme()` token；`ScenarioEditor/ScenarioList/SimulationResultPanel` 补 `useTheme/useLanguage`。
5. **缓存与 Git**：`ScenarioManagementView.tsx:139/142/182/185` 的 `localStorage` 缓存（`ecos_cached_git_commits/branches`）与 `:473-474` 假 git push 动画删除，Git UI 一律走 `services/gitService.ts` 且只传 `repositoryId`（前端规范 §十/§十一）。
6. **TODO/mock/stub 55 处/20 文件**：与 F05-16 的后端显式 stub 对齐，前端不得再静默回落假数据（`sandbox/api.ts:9` "本期 stub"注释 + `:218` 实改 `/runs` 的口径不一致要定版）。
7. **测试**：`ecos-tests` 新建 `playwright.config.ts` + `tests/scenario-cognition.spec.ts`（本册验收用例名全落此文件），3 个 `.mjs` 迁移后删除（E2E 裁决，Q13/§十二 补记）。【2026-09-30 五补校订：`playwright.config.ts` 已建成、3 个 `.mjs` 已删除（报告 §14.6）；`tests/scenario-cognition.spec.ts` **本册文件仍未创建** ⇒ 本册验收仍记"未执行"，口径见报告 §14.6.5】

## 四、C 章 — 技术设计（控制流 / 数据流 / 两态 / 降级 / 可观测）

### C.1 主控制流：场景认知读四端点（E1~E4）

```
Client(BFF :3000 → gateway :8080 | service :18084)
  │ X-Request-Id: <tid>
  ▼
VersionPrefixRewriteFilter ── 双路径 /api/v1/business/scenarios/… ↔ /api/business/scenarios/…
  ▼
SecurityConfig 授权（默认 DENY，非 permitAll）── 无 token → 401
  ▼
ClearanceInterceptor（豁免清单外一律裁决）→ security-engine REST decide()
  │ security 不可用 → DENY（铁律 §2.4-6，禁 fail-open）
  ▼
CognitiveAvailabilityGuard  ← 前置门
  │ ecos.cognitive.enabled=false ─────────────► 503 ECOS-COG-500 "engine not enabled"
  │ to_regclass('ecos_cognitive.ecos_cognitive_mind') IS NULL ─► 503 ECOS-COG-501 "schema not ready"
  ▼
ScenarioGuard（场景只读存在性校验）
  │ 不存在 / is_deleted=1 ──────────────────► 404（两种 message 区分）
  ▼
MindScopeGuard  (?mind= 属主校验)
  │ mind ∉ scenario ────────────────────────► 400 ECOS-COG-400（禁静默返回全部）
  ▼
CognitiveQueryService（只读，仅 SELECT，无任何写）
  ├─ E1: ecos_cognitive_mind ⋈ ecos_cognitive_scenario_mind  WHERE is_active=1 AND is_deleted=0
  ├─ E2: beliefs → CognitiveEvalCalculator.score = probability × trend_weight(=1)
  │        sort desc → limit(N, 默认10 上限50) → trend=FLAT + trendSource=NOT_IMPLEMENTED
  ├─ E3: hypothesis WHERE scenario_id=? [AND mind_id=?] AND is_deleted=0 ORDER BY updated_at DESC
  └─ E4: belief   WHERE … → capability = mask ∩ domainAlias(domain)（空→null + issues[]）
  ▼
MDC 日志  EVT=CognitiveAx op={detect|eval|hyp|belief} agent={} scenario={} mind={} cost={}ms
  ▼
ApiResponse{code,message,timestamp,data}   ← 异常经 cognitive @RestControllerAdvice，不落网关 Exception→404
```

**不变式**：①四端点零写操作（B-2）；②任何空结果 = 200 + `data:[]`，与 5xx 严格可分（B-4）；③数值一律 Java 算术，LLM 不参与（铁律 §0.2.1）。

### C.2 控制流：领域化因果诊断（REQ-COG-02）

```
POST /api/v1/cognitive/diagnose {domain, deviation, forecastContext?}
  ▼ 鉴权链同 C.1
  ├─ forecastContext 缺失 ──► 既有通用诊断分支（响应 schema 不变，向后兼容）
  └─ forecastContext 存在：
       1 ForecastRunSnapshotReader.getRun(forecastRunId, caliberVersion)   ← 预测运行服务只读端点（PRD-09 FC-02）
          │ 取数范围锁定该快照，禁全库扫描（§2.3-2）
       2 MetricDeviationResolver：按 projectId/departmentId/period 解析 deviation 涉及的指标实例
       3 CausalPathBuilder：从本体因果边（03 册口径 + 04 册图谱投影）**只读**装配候选路径
       4 ContributionCalculator（确定性）：对每条边计算 contribution，逐元可复算
       5 EvidenceJoiner：evidenceRefs（04 册证据库）/ statSupport（effect-estimate, pValue）
            ├─ 有支撑 → 入 chains[].path
            └─ 无支撑 → 入 unverifiedHypotheses[]（UI 标"待验证假设"）
       6 ExplanationWriter：LLM 仅生成 explanation 文本字段（经 llm-gateway），**不得写入任何数值**
       7 不落盘：诊断结果只回响应体 + 一条 ecos.audit 事件（REQ-COG-05 第 1 项）
```

### C.3 控制流：反事实确定性重算（REQ-COG-03）

```
workspace 情景复制（编排，不生产 — 铁律 §0.6）
  ▼
POST 预测运行服务：{baselineRunId, overrides[]}      ← 金额唯一来源
   ├─ 复制基准快照（逐项继承）
   ├─ 仅覆盖 overrides 命中的 project/department/periods/target/stage 输入
   └─ 重跑确定性计算 → 新 forecastRunId（type=SCENARIO）+ 快照 + 口径版本（六要素，PRD-09 FC-03）
  ▼
cognitive：POST /api/v1/cognitive/simulate {mode:DETERMINISTIC_DIFF, baselineRunId, scenarioRunId}
   ├─ DiffScope：逐项比较，范围仅 overrides 命中集
   ├─ ScenarioBalanceChecker：|Σcontribution − Δprofit| ≤ 0.01 ？
   │     ├─ 平 → 出 chains + contribution（复用 C.2 归因）
   │     └─ 不平 → 503 ECOS-COG-502（禁返回不平数据）
   └─ 并行度经 runtime-task（禁 Executors.new*，X-36）
  ✗ 禁止路径：ScenarioSimulatorServiceImpl（RAG+Agent）→ 强制 exploratory=true、无金额字段、UI"非核算口径"
```

### C.4 数据流：认知域供数与产物（含 R-13/R-17 争议标注）

```
[控制域 · 只读消费]
  ecos_ontology（口径/实体/因果边，03 册）  ─┐
  ecos_knowledge（画像/假设/证据/快照，04 册）─┼─► cognitive 读侧装配
  ecos_data（CURATED 事实，02 册）           ─┘
        │
        ├─ 认知模型资产（Mind 定义与场景绑定）＝ ecos_cognitive_mind / ecos_cognitive_scenario_mind
        │    落点：ecos_cognitive（**R-13 ① 已批准**；ADR-8 正名、禁 kb_* 误名）
        ├─ 心智状态（belief / hypothesis）＝ 现状在 public（X-23~X-26），目标收敛至 ecos_cognitive（R-13）
        └─ 确定性计算产物（forecast/scenario 运行结果与快照）
             现状：public.ecos_scenario_run 的 4 个 jsonb 列（违 MC02）
             目标：业务域 APPLICATION / ecos_dw，经 data-engine 写通道（ADR-14）
                   **落地路径按 R-17 ① 批准定版**（经 data-engine 写 APPLICATION/ecos_dw；实测 ecos_dw 仅 doc/doc_chunk 两张 0 行表）
[禁止]
  ✗ cognitive_context 一类"运行时上下文"表（REQ-COG-05 第 5 项：Context 只在内存/请求态）
  ✗ 诊断/推理结果落盘（第 1 项）
  ✗ cognitive 直写他域表（现状 sysman.sys_config、kb.sys_compliance_rule — X-31 必消除）
```

### C.5 两态寻址与承流（ADR-15，本册 S3 前置清单）

| 项 | monolith 态（现状唯一承流） | service 态（S3 后） | 本册动作 |
|:--|:--|:--|:--|
| 认知端点宿主 | gateway fat-JAR `@ComponentScan`（X-1/X-2，17 Controller/50 handler） | aiming:18084（`AimingServiceApplication.java:56` 已扫 `engine.cognitive2`） | 两态路由必须等价：`CognitiveRouteEquivalenceTest` 参数化比对 mapping 集合 |
| 寻址 | 前端 → BFF → gateway | 前端 → BFF → `ServiceEndpointResolver` 按 `ecos.route.aiming=service` | **实测该类零命中**（00 册同项）；认知侧 4 处 `new RestTemplate()` 硬编码 8080 必须改走解析器（X-35） |
| 鉴权 | JWT + ClearanceInterceptor | **无**（X-10：排除 Security 自动装配 + header-auth=false） | **R-16 ①+③ 已批准**：service 态内置同等链路 + 两态鉴权等价测试 |
| aiming 制品 | 3 文件 / 0 自有 Controller（X-1） | 同 | 允许制品保持空心（引擎由 service 装配是既有口径），但**不得**据此声称"aiming 独立承流已就绪"（虚假现状声明） |

**S3 切流前置（本册 5 项）**：①`ServiceEndpointResolver` 建成并接线；②aiming service 态鉴权拉平（R-16）；③认知四端点两态路由等价测试绿；④schema 预检门禁上线（F05-02）；⑤审计 fail-closed 落地（F05-15）。任一未满足 → **不得切流**（00 册 S0→S4 门禁矩阵）。

### C.6 降级矩阵（10 行，全部显式；禁静默空集）

| # | 触发条件 | HTTP | 响应码 | 前端表现 | 禁则 |
|:--:|:--|:--:|:--|:--|:--|
| DG-1 | `ecos.cognitive.enabled=false` | 503 | `ECOS-COG-500` | 右栏整体置灰 banner"认知引擎未启用" | 禁 200 空 body（用户裁定 A3） |
| DG-2 | kb/cognitive 表缺失（迁移未跑） | 503 | `ECOS-COG-501` | 运维文案 + traceId | 禁与 DG-1 同 message（要可定位） |
| DG-3 | 场景不存在 | 404 | `ECOS-COG-404` | "场景不存在" | — |
| DG-4 | 场景已软删除 | 404 | `ECOS-COG-404` | "场景已删除"（message 区分） | — |
| DG-5 | `mind` 不属场景 | 400 | `ECOS-COG-400` | 选择器红字提示 | **禁静默兜底返回全部** |
| DG-6 | 场景无 Mind（E1 正常查空） | 200 | code=0 `data:[]` | 空态"该场景未绑定 Mind" | **禁 404** |
| DG-7 | belief.domain 无 capability 匹配 | 200 | code=0 + `issues[]` | 该行 capability 显示"未映射" | 禁丢弃行 |
| DG-8 | llm-gateway 不可用（解释缺失） | 200 | `explanationUnavailable=true` | 数值/链路正常显示，解释区占位 | **数值不得因 LLM 不可用而缺失或伪造** |
| DG-9 | 能力本身未实现（world-model/replay 等） | 501 | `ECOS-COG-503` + `stub=true` | "未实现"占位 | 禁 200 + 固定值（X-43） |
| DG-10 | security-engine 不可用 | 403/503 | `ECOS-SEC-*` | 拒绝并提示重新鉴权 | **默认 DENY，禁放行**（铁律 §2.4-6） |

### C.7 可观测性

1. **traceId**：`X-Request-Id` → MDC → 响应 `data.traceId` 回显（错误态前端可复制）；
2. **事件日志**：`EVT=CognitiveAx op={detect|eval|hyp|belief} agent={} scenario={} mind={} cost={}ms result={ok|empty|deny|stub}`（`result` 四分可算空集率/降级率）；
3. **审计**（fail-closed）：诊断/模拟/配置写各发 `ecos.audit` 事件，经 runtime-event `EventBusService`；
4. **指标**：`cognitive_eval_truncated_total`（E2 截断）、`cognitive_capability_unmapped_total`（DG-7）、`cognitive_balance_fail_total`（C.3 不平）—— 经 runtime-monitor 暴露，禁自建（铁律 §2.5-5）。

### C.8 三层纪律（后端规范 §五）

| 层 | 认知域职责 | 禁则（现状差距） |
|:--|:--|:--|
| Controller（17 类 / 50 handler） | 参数校验 + 错误矩阵映射 + 调 Service，**不含 SQL、不含业务计算** | X-39/X-40 catch-all 与假审计删除 |
| Service | 编排（快照读取、计算器调用、状态机校验）、事务边界 | X-22 `JdbcTemplate` 字段全部移除 |
| Mapper（新建） | 唯一 SQL 出口，`{schema}.` 限定，方言分支 `databaseId` | X-23~X-33 裸表名 / `::jsonb` / `ILIKE` / nextval 清零 |
| Calculator/Codec（新增纯函数） | `CognitiveEvalCalculator`、`ContributionCalculator`、`ScenarioBalanceChecker`、`MindCapabilityMaskCodec` | 禁依赖 Spring/DB（便于确定性复算单测） |

---

## 五、D 章 — 接口设计

### D.1 端点族表（`†`=结构缺陷必改 · `*`=本册新增 · `‡`=属主按 R-14 ① 定版为 cognitive（对外路径冻结不变，迁移执行待授权 §14.4））

| 族 | 方法/路径 | 现状 | 出处 |
|:--|:--|:--|:--|
| **场景认知 E1 †‡\*** | `GET /api/v1/business/scenarios/{id}/cognition/detect` | 前后端双零命中（X-14） | F05-03 |
| **场景认知 E2 †‡\*** | `GET /api/v1/business/scenarios/{id}/cognition/operation-eval?mind=&limit=` | 零命中 | F05-04 |
| **场景认知 E3 †‡\*** | `GET /api/v1/business/scenarios/{id}/cognition/hypotheses?mind=` | 零命中（近似体无 mind） | F05-05 |
| **场景认知 E4 †‡\*** | `GET /api/v1/business/scenarios/{id}/cognition/beliefs?mind=` | 零命中 | F05-06 |
| 近似体（保留别名） | `GET/POST /api/v1/cognitive/hypotheses\|beliefs` | 存在但签名脱节（X-15/X-48） | F05-08 |
| workspace 侧 †‡ | `POST /api/v1/workspace/scenarios/{id}/cognitive/*`（4 个仅 POST） | 前端 GET → 405（X-48） | F05-08 |
| 诊断 | `POST /api/v1/cognitive/diagnose` | 存在；`forecastContext/chains` 零命中（X-17） | F05-09 |
| 诊断历史 † | `GET /api/v1/cognitive/diagnose/history` | 进程内 Map 伪状态（X-44） | F05-16 |
| 模拟 | `POST /api/v1/cognitive/simulate` | 存在；金额来源违规风险 | F05-10 |
| 预测（相邻） | `POST /api/v1/cognitive/forecast` | 存在 | C.4 不落盘断言 |
| 决策 | `POST /api/v1/cognitive/decision/{id}/check-rules` | 前端 `GET /decision/{id}/rules` 死调用（X-49） | F05-08 |
| 世界模型 † | `GET /api/v1/cognitive/world-model/*`（4 handler） | 全固定值 + 硬编码因果边（X-43） | F05-16 |
| 健康 | `GET /api/v1/engine/cognitive/health`（ai 与 cognitive2 双占） | `"UP"` 字面量（X-42） | F05-01/16 |
| 死链（必清 †） | `/cognitive/reason`、`/cognitive/blueprint`、`/scenarios/{id}/minds/base`、`/evolution/trigger`、`/evolution/log/{mid}` | 前端在调、后端无映射（X-45/X-49） | F05-08 |
| Replay † | `POST /replay` vs `GET /replay/{version}` | 两套互斥签名（X-49） | F05-08 |

### D.2 契约冻结规则

1. **只增不改**：既有路径与参数签名不可变更（AGENTS.md 约束）；本册所有变更 = 新增路径 / 新增可选字段 / 旧路径加 `Deprecation`+`Sunset`；
2. 四端点 Schema 冻结于契约文档 v2.0 修订版 + `CognitiveApiPaths`，**二者由 `CognitiveContractSingleSourceTest` 做构建期一致性断言**（文档表格 ↔ 代码常量 ↔ OpenAPI 片段）；
3. 枚举单源（全部落 `cognitive-engine-api`，禁在 impl/前端重复定义）：`MindCapabilityMask`（5 值）、`HypothesisStatus`（4 值，R-15）、`Trend`（`UP/FLAT/DOWN`，本期恒 FLAT）、`ClosedLoopBound`（`PRE/POST`）；
4. `confidence/probability/score ∈ [0,1]` 越界即 500（数据缺陷，禁 clamp 静默）；
5. 列表端点必带 `limit`（默认值见各端点），无 `limit` 的全量拉取（X-50 前端 4 处）视为契约违例；
6. **场景语义不下沉**（ARCH_SPEC §七-4，Q4 裁决）：契约路径中的 `{id}` 由**属主层**接收后转为 `subjectType="SCENARIO" + subjectId` 传入 cognitive 内部 Service；Service/Mapper 签名**禁止**出现 `scenarioId` 之外的场景专有耦合形态，更**禁止**为此另建第二套 workspace 侧实现（随 **R-14** 定版属主）。

### D.3 关键新增契约（Schema）

**E2 响应（含截断与趋势来源声明）**

```json
{"code":0,"message":"ok","timestamp":1790495600000,"data":{
  "mindId":"M001","limit":10,"truncated":false,
  "trendWeight":1,"trendWeightSource":"ecos.cognitive.eval.trend_weight",
  "trendSource":"NOT_IMPLEMENTED",
  "metrics":[{"metricId":"op-1","name":"验收治理","capability":"DETECT","score":0.83,"trend":"FLAT"}]
}}
```

**diagnose 请求增块（可选；缺省即向后兼容）**

```json
{"forecastContext":{"forecastRunId":"fr-001","caliberId":"cal-01","caliberVersion":"3",
  "asOfTime":"2027-08-31T00:00:00Z","projectId":"p1","departmentId":"d01"}}
```

**diagnose 响应增块**

```json
{"chains":[{"chainId":"c1",
  "path":[{"node":"进度延期","type":"EVENT"},
          {"node":"验收率下降","type":"METRIC:M_REALIZATION_RATE"},
          {"node":"利润下降","type":"METRIC:M_FC_PROFIT"}],
  "contribution":-180000,
  "evidenceRefs":["ev-101","kg-edge-233"],
  "statSupport":{"method":"effect-estimate","pValue":0.03},
  "recomputable":true,
  "unverifiedHypotheses":[{"statement":"外包比例上升放大延期影响","reason":"无反向证据累计"}]}],
 "explanation":"…（LLM 文本，无数值）","explanationUnavailable":false}
```

**simulate 请求（确定性重算模式）**

```json
{"mode":"DETERMINISTIC_DIFF","baselineRunId":"fr-001","scenarioRunId":null,
 "overrides":[{"projectId":"p1","departmentId":null,
   "periods":["2027-07","2027-08","2027-09"],
   "target":"stage.realization_rate","stage":"ACCEPTANCE","delta":-0.10}]}
```

响应：`{"mode":"DETERMINISTIC_DIFF","scenarioRunId":"fr-077","balanceOk":true,"deltaProfit":-214000.00,"contributionsSum":-214000.00,"tolerance":0.01,"chains":[…]}`；探索通道：`{"exploratory":true,"caliber":"NON_ACCOUNTING"}`（**无金额字段**）。

**统一错误响应体**（cognitive `@RestControllerAdvice`）

```json
{"code":503,"message":"cognitive schema not ready","timestamp":1790495600000,
 "data":{"traceId":"…","guard":"CognitiveAvailabilityGuard","missing":"ecos_cognitive.ecos_cognitive_mind"}}
```

### D.4 相邻册接缝（9 条）

| # | 接缝 | 本册角色 | 对方册条目 |
|:--:|:--|:--|:--|
| S-1 | 事实/指标数值（E2 belief 供数、diagnose 指标实例） | 消费方 | 02 册 CURATED 写通道 |
| S-2 | 口径（caliber）定义与版本 | 只读消费（`caliberId/caliberVersion` 透传） | 03 册 **R-6**（口径主权推荐落 ontology） |
| S-3 | 因果边与图谱投影（`CausalPathBuilder` 候选路径） | 只读消费 | 03 册 F03-*、04 册图谱双形态 |
| S-4 | 画像/假设/证据（E3/E4 与 `evidenceRefs`） | 消费方 | 04 册 F04-06/07 + **R-8** |
| S-5 | 认知契约守护测试（表名/枚举/路径） | 供方（枚举终态在本册） | 04 册 F04-08 + **C94/G2-6** |
| S-6 | 预测运行快照只读端点（FC-02）与情景运行（FC-03） | **强依赖**（金额唯一来源） | 09 册年度经营预测竖切片 |
| S-7 | 场景工作台右栏渲染（E1~E4 消费） | 供方 | 08 册前端与 BFF + PRD-08 WS-03 |
| S-8 | 鉴权/审计/事件底座（默认 DENY、`ecos.audit`） | 消费方 | 01 册安全域 |
| S-9 | gateway `Exception→404` 掩蔽链改造 | 引用不重复立项 | 04 册 **C76**、01 册 C 系列 |

### D.5 错误码（`ECOS-COG-*`，认知域新增；`code` 与 HTTP 状态一致）

| 码 | HTTP | message（冻结字面量） | 触发 |
|:--|:--:|:--|:--|
| `ECOS-COG-400` | 400 | `mind not found on this scenario` | `?mind=` 不属场景（禁兜底） |
| `ECOS-COG-400M` | 400 | `mind not found` | mind 指向不存在注册行 |
| `ECOS-COG-400P` | 400 | `invalid parameter` | `limit`/枚举/区间越界 |
| `ECOS-COG-404` | 404 | `scenario not found` / `scenario deleted` | 场景缺失 / 软删除 |
| `ECOS-COG-403` | 403 | `clearance denied` | security-engine 裁决拒绝 |
| `ECOS-COG-500` | 503 | `cognitive engine not enabled` | `ecos.cognitive.enabled=false` |
| `ECOS-COG-501` | 503 | `cognitive schema not ready` | 表缺失（区别于 500，便于运维定位） |
| `ECOS-COG-502` | 503 | `scenario attribution not balanced` | C.3 平衡校验不平（禁返回不平数据） |
| `ECOS-COG-503` | 501 | `capability not implemented` | DG-9 显式 stub（附 `stub=true`） |
| `ECOS-COG-504` | 500 | `cognitive internal error` | 兜底（含 traceId，禁泄漏 `e.getMessage()`） |

---

## 六、E 章 — 数据设计

### E.1 归属口径（先定性质，再定 schema）

| 数据种类 | 性质 | 目标 schema | 依据 | 争议 |
|:--|:--|:--|:--|:--|
| Mind 注册（认知模型资产） | 定义态资产 | `ecos_cognitive.ecos_cognitive_mind` | ADR-8（认知模型资产存储，**禁 `kb_*` 误名**） | **R-13** |
| 场景↔Mind 绑定 | 定义态资产 | `ecos_cognitive.ecos_cognitive_scenario_mind` | ADR-8 + 场景层只编排（§0.6） | **R-13/R-14** |
| 信念 belief | 心智状态 | `ecos_cognitive.*`（现状在 `public`） | ADR-9 认知持久化四层 | **R-13** |
| 假设 hypothesis | 心智状态 | `ecos_cognitive.*`（现状在 `public`） | ADR-9 | **R-13/R-15** |
| 运行时上下文（forecastContext 等） | 请求态 | **不建表** | REQ-COG-05 第 5 项 | — |
| 诊断/推理结果 | 派生结果 | **不落盘**（响应 + 审计事件） | REQ-COG-05 第 1 项 | — |
| 确定性计算产物（forecast/scenario 运行与快照） | 业务域 APPLICATION | `ecos_dw`，经 data-engine 写通道 | ADR-14 + 铁律 §0.6 | **R-17** |
| 认知配置（`trend_weight`、`domain_alias`） | 配置 | sysman 配置门面（单表分组 `config_group`/`subsystem`） | 铁律 §十 | 现状直写 `sysman.sys_config`（X-31）必改 |

**实测反差（R-13 的证成）**：`ecos_cognitive` schema 现有 11 张表**全 0 行**，且装的是 `ecos_biz_*` / `ecos_wm_*`（业务域与世界模型表）；认知真身 14 张表全在 `public`，其中 6 张 PK 为 `bigint nextval`（违 MC01）、16 个 jsonb 列（违 MC02）、DR06~DR08 七基线列**无一表全合规**。ADR-8/9 声称"认知仅 4 表"与实测 14 表冲突 → 需裁决"权威清单 + 落点 + 收敛顺序"。

### E.2 合规 DDL 草案（`V187` 起；**R-13/R-14/R-17 已批准 ⇒ 本轮落迁移脚本文件、不实跑库**，报告 §14.4）

> **【2026-09-30 收口对照】** 本批三处按 **DR02**（新表必带 `ecos_` 前缀）改名并已在单源脚本落地：`ecos_cognitive.cognitive_mind`→**`ecos_cognitive.ecos_cognitive_mind`**（V187）、`ecos_cognitive.cognitive_scenario_mind`→**`ecos_cognitive.ecos_cognitive_scenario_mind`**（V188，保留 ADR-8 正名词干 `cognitive_*`）、`ecos_dw.application_forecast_run`→**`ecos_dw.ecos_application_forecast_run`**（V197）。实测三表库内不存在、全仓 Java/XML/TS 零引用 ⇒ 零回归面；R-17 ① 批准的是**落点**（`ecos_dw` + data-engine 写通道），不含不合规表名，故不构成本改名的冲突项。

> 单源目录：`ecos_backend/gateway/src/main/resources/db/migration/`（铁律 §3.1）。模板沿用 04 册 E.2 的 MC01/MC02/MC03 + DR06~DR08 合规形态。

```sql
-- V187__cognitive_mind_registry.sql   （R-13 裁决后执行；认知模型资产注册表）
CREATE TABLE IF NOT EXISTS ecos_cognitive.ecos_cognitive_mind (
  id                 VARCHAR(36)  NOT NULL,
  mind_key           VARCHAR(120) NOT NULL,
  name               VARCHAR(200) NOT NULL,
  description        TEXT,
  capability_mask    TEXT         NOT NULL DEFAULT '[]',   -- JSON 数组字符串，MC02 禁 jsonb
  closed_loop_bounds TEXT         NOT NULL DEFAULT '[]',   -- ["PRE","POST"]
  status             VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
  is_active          SMALLINT     NOT NULL DEFAULT 1,
  mind_version       VARCHAR(20)  NOT NULL,
  trace_id           VARCHAR(64),
  -- DR06~DR08 七基线列
  domain             VARCHAR(64)  NOT NULL DEFAULT 'DEFAULT',
  version_no         INTEGER      NOT NULL DEFAULT 1,
  is_deleted         SMALLINT     NOT NULL DEFAULT 0,
  create_by          VARCHAR(64)  NOT NULL,
  update_by          VARCHAR(64),
  create_time        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time        TIMESTAMP,
  CONSTRAINT pk_ecos_cognitive_mind PRIMARY KEY (id),
  CONSTRAINT ck_cm_capability_mask CHECK (capability_mask IN ('[]') OR capability_mask LIKE '[%'),
  CONSTRAINT ck_cm_status   CHECK (status IN ('DRAFT','PUBLISHED','SUPERSEDED')),
  CONSTRAINT ck_cm_is_active CHECK (is_active IN (0,1))
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_cm_key_ver
  ON ecos_cognitive.ecos_cognitive_mind (mind_key, mind_version, is_deleted);   -- 不用 PG partial index（多库兼容）
CREATE INDEX IF NOT EXISTS idx_cm_active ON ecos_cognitive.ecos_cognitive_mind (is_active, is_deleted);

-- V188__cognitive_scenario_mind.sql   （场景↔Mind 绑定；替代库内缺失的 ecos_scenario_mind/V146）
CREATE TABLE IF NOT EXISTS ecos_cognitive.ecos_cognitive_scenario_mind (
  id            VARCHAR(36) NOT NULL,
  scenario_id   VARCHAR(36) NOT NULL,
  mind_id       VARCHAR(36) NOT NULL,          -- 首个 mind_id 列（实测全库 0 个，X-19）
  binding_role  VARCHAR(20) NOT NULL DEFAULT 'PRIMARY',
  priority      INTEGER     NOT NULL DEFAULT 100,
  trace_id      VARCHAR(64),
  domain        VARCHAR(64) NOT NULL DEFAULT 'DEFAULT',
  version_no    INTEGER     NOT NULL DEFAULT 1,
  is_deleted    SMALLINT    NOT NULL DEFAULT 0,
  create_by     VARCHAR(64) NOT NULL,
  update_by     VARCHAR(64),
  create_time   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time   TIMESTAMP,
  CONSTRAINT pk_ecos_cognitive_scenario_mind PRIMARY KEY (id),
  CONSTRAINT ck_csm_role CHECK (binding_role IN ('PRIMARY','SUPPORTING'))
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_csm_scn_mind
  ON ecos_cognitive.ecos_cognitive_scenario_mind (scenario_id, mind_id, is_deleted);

-- V189__cognitive_hypothesis_status_converge.sql   （R-15 裁决后执行；只加列 +  CHECK 延后）
ALTER TABLE ecos_cognitive.cognitive_hypothesis ADD COLUMN IF NOT EXISTS old_status VARCHAR(20);
-- 存量 7 行 VALID/INVALIDATED/ARCHIVED 的映射写入 old_status 留痕（不伪造历史主体），
-- 新枚举写入由应用层 HypothesisStatus 枚举 + Mapper 白名单把关；
-- CHECK 约束在应用侧全量切换且无旧值后再补（禁"先加约束后改代码"造成线上拒写）。
```

**禁则复述**：本册 DDL 一律 `VARCHAR(36)` 主键 + 应用侧 UUID（禁 `gen_random_uuid()`/BIGSERIAL/nextval，MC01）；JSON 只 TEXT（MC02）；禁 `::jsonb`/`ON CONFLICT`/无 `databaseId` 分支的 `RETURNING`/`ILIKE`（MC03）；表名必带 `{schema}.`（§四附则1）；金额/比率高精度列用 `NUMERIC(p,s)`（MC01）。

### E.3 存量收敛与迁移规划（只加不删；DROP 仅限裁决项）

| 现状 | 收敛动作 | 迁移号 | 前置 |
|:--|:--|:--|:--|
| `public` 内认知真身 14 张表 | 逐表补 `{schema}.` 限定读 + 双读写切换，最终归位 `ecos_cognitive` | V190~V195（分批） | **R-13** |
| `ecos_cognitive` 内 11 张 0 行业务/世界模型表 | 定性为"错放"，**只停写不迁**（存量动作属待授权执行项，报告 §14.4）；是否 DROP 重建 = **R-12 ① 严格双条件已批准** | — | R-12/R-13 |
| 6 张 `bigint nextval` PK 表 | 新增 `guid VARCHAR(36)` 列双写 → 读切换 → 新表以 GUID 为主 | V196 | 应用侧先行 |
| 16 个 jsonb 列（含 `ecos_scenario_run` 4 列产物） | 新增平行 TEXT 列双写；产物落点随 **R-17** | V197 | R-17 |
| `V146 ecos_scenario_mind`（迁移在、库内无表，代码仍 INSERT） | 以 `ecos_cognitive_scenario_mind` 替代，**不重建 legacy 名**；旧 INSERT 先停写 | V188 | R-13 |
| `V150/V155` 版本号重复 | 重编号 + `MigrationVersionUniquenessTest` 构建期拦 | V198 | — |
| `ScenarioService.java:349` INSERT 引用不存在列 `target_id/target_type` | 结构必败（M0）：补列或改语句，二选一以契约为准 | V199 | — |
| `V163` 声明 `kb_cognitive_pipeline` 停写但未落地 | 写点真实切换后补"停写声明"校验 | — | 代码先行 |
| 跨域写 `sysman.sys_config` / `kb.sys_compliance_rule` | 改走 sysman 配置门面 / kb REST；认知 Mapper 删除他域语句 | — | F05-13 |

### E.4 读写边界（本域执行表）

| 对象 | cognitive 权限 | 通道 | 违例现状 |
|:--|:--|:--|:--|
| `ecos_cognitive_mind` / `ecos_cognitive_scenario_mind` | 读写（本域资产） | 本域 Mapper | — |
| belief / hypothesis（心智状态） | 读为主；写仅经认知状态机 | 本域 Mapper | 状态三套枚举（X-20） |
| kb 三表（画像/假设/证据，04 册） | **只读** | kb-engine REST | 存在直连（X-31） |
| 本体口径/因果边（03 册） | **只读** | ontology REST | — |
| CURATED 事实（02 册） | **只读** | data-engine REST | — |
| 确定性计算产物 | **不直写** | 预测运行服务 → data-engine 写通道 → `ecos_dw` | 实存 `public.ecos_scenario_run` jsonb（R-17） |
| 运行时上下文 | 内存/请求态 | 不建表 | — |
| 诊断/推理结果 | 不落盘 | 响应 + `ecos.audit` | 进程内 Map 伪历史（X-44） |

### E.5 多库方言与单源

- 三档（standard PG / enterprise +Neo4j / ultimate +Doris∨ClickHouse）下认知域 SQL 必须只依赖 PG 标准语法 + MyBatis `databaseId` 分支；本期认知域**不引入 Neo4j 依赖**（现状 `neo4jOk=true` 硬编码，X-44，改为真实探测或 `stub=true`）；
- DDL 单源目录（铁律 §3.1），禁分域另立目录；`ecos-sql/mysql/` 侧同步补认知两表（如列入多库基线）；
- 配置单源：`ecos.cognitive.enabled`、`ecos.cognitive.eval.trend_weight`、`ecos.cognition.domain_alias`、`ecos.cognitive.demo` 一律经 sysman 配置门面消费，禁 `@Value` 散落默认值造成两态漂移。

### E.6 ST03 / ST03-A 登记

- 认知域**无敏感个人数据列**（不存 password/id_card/phone），控制域列永不可豁免的红线不受本册影响；
- 派生统计量列（`probability`、`strength`、`score`、`confidence`）与 04 册 **R-9** 同族：本册推荐登记为"派生统计量，非个人数据"，纳入 ST03-A 豁免登记表逐列列举（**不新增表**，沿用 02/04 册登记表）；
- 金额相关列不在认知域（`contribution` 只出现在响应与审计事件，不落库）→ 无 MC01 金额列新增形态。

## 七、F 章 — 符合性偏差登记（W115~W139 → 回填 ARCH_SPEC C97~C121）

> 编号纪律：**本表 25 项必须逐条回填 `docs/10-架构/架构设计说明书-2026-09-28.md` §十一**（"不得只在分册内部登记而不回填本表"）。`M0`=阻断（结构必败 / P0 安全 / 主责 REQ 零实现），`M1`=批次内收敛，`M2`=B 批次。

| W | C | 偏差（证据 X 号） | 违反条款 | 收敛方向 | F 项 | 优先级 |
|:--:|:--:|:--|:--|:--|:--:|:--:|
| W115 | **C97** | 主责端点 E1 `cognition/detect`、E2 `cognition/operation-eval` **前后端双零命中**；REQ-COG-01 的 P0 能力不存在（X-14） | PRD-05 §1.1、REQ-COG-01(P0) | 四端点从零建成 + 属主定版（R-14） | F05-03/04 | **M0** |
| W116 | **C98** | **E1 数据源物理缺失**：全库无名字含 `mind` 的表、`mind_id` 列 **0 个**；`kb_mind_registry` 仅存于文档；`ecos_scenario_mind`(V146) 库内不存在而 `ScenarioMindService.java:84-115/119` 仍 INSERT（X-19） | PRD-05 §1.1 E1 数据源、§四附则1 | `ecos_cognitive_mind`/`ecos_cognitive_scenario_mind` 新建（V187/188）+ 停写旧 INSERT + `to_regclass` 预检 | F05-02 | **M0** |
| W117 | C99 | 认知模型资产以 `kb_*` 前缀命名（文档与 legacy 表），ADR-8 **明令禁用该误名**（X-18） | ADR-8 | 一律正名 `cognitive_*`，文档/代码/契约同步 | F05-02 | M1 |
| W118 | C100 | 假设状态**三套枚举并存**：文档 4 值 vs `SERVICE_DOC:104` `VALID` vs `HypothesisStore.java:34`/`V128:21` `VALID/INVALIDATED/ARCHIVED`（库内实存 7 行）→ 状态机守护从未生效（X-20/X-21） | PRD-05 §1.1 E3 冻结枚举、REQ-KB-04 | 终态 4 值 + 只读别名映射 + 存量留痕（R-15） | F05-05 | **M0** |
| W119 | C101 | 近似端点 `/api/v1/cognitive/hypotheses\|beliefs` **无 `?mind=` 严格过滤**，无法区分"场景全部"与"指定 Mind"（X-15/X-16） | REQ-COG-01 §1.2（禁静默兜底返回全部） | 契约端点补 mind 参 + `MindScopeGuard` 400 | F05-05/06 | **M0** |
| W120 | C102 | E2 计算要素全缺：`trend_weight` 配置项、Top-N、`?limit=`、`truncated` 声明零命中；前端列表调用**全量无 limit**（仅 1 处 `?limit=20`）（X-50） | PRD-05 §1.1 E2 | `CognitiveEvalCalculator` 纯函数 + 上限 50 硬截断 + `limit` 必传 | F05-04 | M1 |
| W121 | C103 | 契约前缀三套 + workspace 侧 4 handler **仅 `@PostMapping`**，前端 `CognitionView.tsx:206-208` 以 GET 调用 → **405 结构必败**（X-48） | API 只增不改、ADR-15 两态等价 | 契约路径定版 + GET 只读 + 旧路径 Deprecation 别名 | F05-01/08 | **M0** |
| W122 | C104 | `/api/v1/engine/cognitive` 被 ai（`AiEngineStatusController.java:11/:20`）与 cognitive2（`CognitiveEngineOpenHealthController.java:54/:106`）**同时占用**（X-9） | ARCH_SPEC 属主单源、路由唯一性 | 属主判给 cognitive + `RouteUniquenessTest` 双占即 FAIL | F05-01 | **M0** |
| W123 | C105 | 5 组死调用（前端在调、后端零映射）+ 同文件两套互斥 replay 签名（POST 无 version vs GET 带 version）（X-49） | 虚假现状声明、前端规范（数据源单源） | 逐组补映射或删除调用；replay 定版一种 | F05-08 | M1 |
| W124 | C106 | **schema 反义**：`ecos_cognitive` 11 张表**全 0 行**且装的是 `ecos_biz_*`/`ecos_wm_*`（业务域/世界模型表），认知真身 **14 张全在 `public`**；ADR-8/9"认知仅 4 表"与实测冲突（X-23~X-26） | ST07、湖规 v2.0 数据域二分 | 权威清单 + 落点 + 收敛顺序（**R-13**）；本册先补 `{schema}.` 限定 | E.1/E.3 | **M0** |
| W125 | C107 | 认知 14 表中 **6 张 PK 为 `bigint nextval`**（另 9 列 nextval）、**16 个 jsonb 列**、DR06~DR08 七基线列**无一表全合规**（X-27/X-28） | MC01、MC02、DR06~DR08 | 平行 GUID 列双写 → 切换；jsonb → TEXT 平行列（产物落点随 R-17） | E.3 | **M0** |
| W126 | C108 | 17/18 张认知迁移脚本**裸表名**；`V150/V155` **版本号重复**（X-29/X-30） | §四附则1（裸表名=FAIL）、迁移单源 | `SchemaDriftLintTest` 扩认知域 + `MigrationVersionUniquenessTest` | F05-13 | **M0** |
| W127 | C109 | `ScenarioService.java:349` INSERT 引用**不存在的列** `target_id/target_type` → 结构必败（X-32） | IR05、§四附则1 | 补列或改语句（V199），以契约为准 | E.3 | **M0** |
| W128 | C110 | `V163` 声明 `kb_cognitive_pipeline` 停写但**从未落地**，认知仍在读写 legacy 表（X-33） | 铁律 §0.5-5、迁移声明真实性 | 写点真实切换后补停写校验测试 | F05-13 | M1 |
| W129 | C111 | 认知代码**跨域写** `sysman.sys_config` 与 `kb.sys_compliance_rule`（X-31） | 铁律 §十 配置单源、跨引擎不操作对方表 | 配置经 sysman 门面、kb 走 REST；认知 Mapper 删他域语句 | F05-13 | **M0** |
| W130 | C112 | **0 个 MyBatis Mapper**，认知 SQL 全部 `JdbcTemplate` 字符串拼接落在 Service/Store（`EvidenceStore.java:23-24` 自陈），`::jsonb` 7 处、`ILIKE` 2 处、无 `databaseId` 分支 `RETURNING`（X-22/X-33） | 后端规范 §五、数据库访问规范 §五、MC03 | 三层纪律迁移（Controller/Service/Mapper + 纯函数 Calculator） | F05-13 | **M0** |
| W131 | **C113** | **aiming:18084 service 态完全无鉴权**：排除 Security 自动装配 + `header-auth=false`，同一 50 端点在 gateway 态需 JWT、service 态裸奔；且注释**谎称 permitAll**（X-10/X-11/X-12） | 铁律 §2.4-6 默认 DENY、REQ-SEC-01 | 两态鉴权拉平（**R-16**）+ 三滤波器 + 越权两通道均拒 | F05-14 | **M0（P0 安全）** |
| W132 | **C114** | **fail-open 与错误掩蔽成链**：18 处 `catch (Exception)` 泄漏 `e.getMessage()`、40 处 `Collections.empty*` 空集回落、`KbRestClientConfig.java:57-58` **把 fail-open 写成制度** → 503 穿透（用户裁定 A3）不可能成立（X-39/X-40） | REQ-COG-01 §1.2、铁律 §2.4-6 | 认知局部 Advice + `CognitiveNoEmptyFallbackTest` + 前端区分故障/空态 | F05-07 | **M0** |
| W133 | C115 | **LLM 绕开 llm-gateway**：`SuggestionBuilder.java:34/:40` 自建客户端 + 4× `new RestTemplate()` 硬编码 `http://localhost:8080` agent-loop（X-34/X-35） | 铁律 §2.5-2、ADR-15 两态寻址 | 一律 `LlmGatewayClient`；跨服务经 `ServiceEndpointResolver` | F05-15 | **M0** |
| W134 | C116 | `Executors.newFixedThreadPool(4)` 绕过"调度归 runtime-task"，**ArchUnit `businessMustNotSelfSchedule`（`ModuleDependencyArchTest.java:366-403`）只看 import 图，对 `Executors`/反射零可见**（与 03 册 C71 同族盲区）（X-36/X-37） | 铁律 §2.5-3 | 改 runtime-task；ArchUnit 增"业务包禁 `Executors.new*`/`new Thread(`/`@Scheduled`"谓词 | F05-15 | **M0** |
| W135 | C117 | 审计 **fail-open + 假审计**：`EventBusService` 缺失即 `log.warn` 继续（`:276`）、`CognitiveConfigController.java:120-127` 手工 log 冒充审计（X-38/X-39/X-40） | 后端规范 §九、ST06、REQ-NF-04 | fail-closed（兜底必写，兜底失败拒动作）；假审计删除改发 `ecos.audit` | F05-15 | **M0** |
| W136 | C118 | **伪实现族（7 处）**：健康端点每个 `"UP"` 是字面量（X-42）；world-model 全固定值 + 硬编码因果边 `CustomerSatisfaction→RenewalRate w=0.8` 而 4 handler 对外、前端在调（X-43）；`/diagnose/history` 进程内 Map 冒充历史 + `neo4jOk/llmOk` 硬编码 true（X-44）；`/list` 5 条硬编码模板、demo 空输入回落内置 markdown（X-44）；ai `CognitiveController` 桩而前端 `POST /cognitive/reason` 在调（X-45） | 虚假现状声明、铁律 §0.2.1 | 显式 stub（501 + `stub=true`）/ 真实探测 / demo 仅 dev profile | F05-16 | **M0** |
| W137 | C119 | 前端**三份"认知四件套"并存** + 因果图三处自绘 + **3 件零 importer 死代码**（`CognitionView.tsx`、`cognition/barrel.ts`、`aiworkbench/CognitiveOperatingSystem.tsx`）+ `src/api.ts` **2,625 行/184 export** + `/api/v1/cognitive` 常量 3 处重复 + `WM_BASE/CAUSAL_BASE` 同值双别名（X-46/X-47/X-50） | 前端规范（组件 ≤800 行、每 Tab 独立文件、入口/数据源单源） | IA 归一（B.0）+ `cognitiveClient.ts` 单源 + 死代码删除 + api.ts 拆分 | F05-17/B 章 | M1 |
| W138 | C120 | 前端横切规范违例集：**auth 旁路**（`services/httpClient.ts:243/270/301` 各自 ad-hoc 拼 `Bearer` 且只读单 key `localStorage['token']`，违背 `services/auth.ts:2` 自称的唯一通道）；硬编码中文 13 文件；硬编码 Tailwind 色 **1,058 处/19 文件**；i18n 使用键 395 中 **141 个不存在**（`causal/hypo/state/model/diagnosis` **五命名空间整体缺失**）；`aria-*` 仅 3 处、移动端复用件 0、手写 SVG sparkline；轮询 10s/3s **而契约要求的 30s + debounce 100–300ms 零命中**；`localStorage` 当缓存 + 假 git push 动画；TODO/mock/stub 55 处/20 文件（X-51/X-52） | 前端规范 §六 i18n、§七 主题、§八 移动端、§十 收口 | auth 单源 + i18n 补齐 + token 化配色 + a11y/移动端 + 轮询合规 + Git 单通道 | B.9 | **M0（auth）/M1（其余）** |
| W139 | C121 | **测试与验收零基础**：`ScenarioCognitionIntegrationTest`（三处文档的验收产物）**不存在**；认知 16 测试类 89 `@Test` 全为纯 JUnit/Mockito，**`@SpringBootTest` 零命中、无一连真实 DB**（`PrecedentRecallerTest.java:16/24-25` 反以"无 DataSource 的 JdbcTemplate"模拟 DB down）；**workspace 服务 src/test 文件数 = 0**；`ecos-tests` 3 个 `.mjs` 对 scenario/cognition 零命中；`playwright.config.ts` **全仓零命中**；REQ-COG-05 五守护**零接线**（X-52） | Q13 裁定（写"Playwright 通过"=虚假验收）、E2E 裁决、REQ-COG-05 | `ecos-tests` Playwright 工程化（本册用例名落 `scenario-cognition.spec.ts`）+ 集成测试连真实 PG + 五守护 5 个 `mvn -Dtest=` | F05-12/B.9-7 | **M0** |

> **回填口径**：W115~W139 与 C97~C121 **一一映射、无归并**。其中 C106（schema 反义）与 03 册 C62、02 册 R-1 属同一批 schema 归属判定，建议与 R-1/R-13 一次性裁决以免反复改 DDL；C113（service 态无鉴权）与 01 册 C22~C29 安全域、C114（fail-open 掩蔽链）与 04 册 C76 同族，改造机制复用、不重复立项。

### 7.1 契约 / PRD 回写清单（本册发现，须由需求侧订正）

| # | 文件与位置 | 现文 | 实测事实 | 订正动作 |
|:--:|:--|:--|:--|:--|
| Cg-1 | `BUSINESS_SCENARIO_COGNITION_DOC.md` §状态枚举 | `PROPOSED/EVIDENCED/BELIEVED/REFUTED` | 库内实存 `VALID/INVALIDATED/ARCHIVED`（7 行），契约示例另写 `VALID` | 终态定版 4 值 + 存量映射（R-15）；文档标"v2.0 定版" |
| Cg-2 | 同上 §数据源 | `kb_mind_registry` | 全库无该表；`kb_*` 前缀为 ADR-8 明令禁用误名 | 改 `ecos_cognitive_mind`（含 E1 响应字段来源说明） |
| Cg-3 | 同上 §端点方法 | E1~E4 为 `GET` | 近似实现 4 handler **仅 POST** | 文档补"实现现为 POST，本册定版 GET 只读 + 别名"，并声明只增不改 |
| Cg-4 | `BUSINESS_SCENARIO_SERVICE_DOC.md:104` | 出现 `VALID` 状态 | 与 Cg-1 冲突（同文档内自相矛盾） | 统一为 4 值枚举 |
| Cg-5 | `PRD-05` §1.2 错误矩阵末行 | "kb 三表缺失 → 503" | 认知域另需 `ecos_cognitive_mind` 两表；缺失语义要合并 | 改为"kb 三表 **或认知资产表**缺失 → `ECOS-COG-501`" |
| Cg-6 | `PRD-05` §1.1 E2 | `trend_weight` 固定 1、trend 全 FLAT | 无实现；前端无 limit | 保留（本册按此实现），补 `truncated`/`trendSource` 响应字段（D.3） |
| Cg-7 | `PRD-05` §3.2 分工 | "反事实计算主体落预测运行服务" | 认知侧仍在算金额（RAG 通道可达） | 补禁令落点与 `exploratory=true` 出口约束（F05-10） |
| Cg-8 | `PRD-05` §4.1 | `MindCapabilityMask` 5 值 | 代码内两套并行定义（enum + 逗号串 + 前端 string[]） | 声明 api 包为唯一权威 + 序列化单 Codec |
| Cg-9 | `PRD-05` §1.3-3 鉴权 | "业务数据端点不写 permitAll" | service 态**完全无鉴权**（C113） | 补"两态均须默认 DENY"，纳入 S3 前置 |
| Cg-10 | `PRD-05` §五 守护表 | 五项守护 | 零接线（C121） | 每项绑定一个 `mvn -Dtest=` 标识（F05-12 表） |
| Cg-11 | `AGENTS.md` 引擎端口表 | cognitive-boot 18089 | 实测 17 Controller 由 gateway + aiming 双宿主，boot 端口从未独立承流 | 补 ADR-15 制品≠承流注记（00 册同项，避免重复声明） |
| Cg-12 | `PRD-08` WS-03 | 右栏置灰 banner | 前端三簇并存、无 503 分支渲染 | 接缝 S-7 对齐：本册 D.5 码表 + B.5 七态 |

### 7.2 裁决项 R-13 ~ R-17（登记于需求检视报告 §13.4；**已于 2026-09-29 §十四.1 全量批准**）

| # | 议题 | 实测事实 | 选项 | 本设计推荐 |
|:--:|:--|:--|:--|:--|
| **R-13** | **认知域表落点与权威清单** | `ecos_cognitive` 11 张全 0 行且装 `ecos_biz_*`/`ecos_wm_*`；认知真身 14 张全在 `public`（6 张 nextval PK、16 jsonb）；ADR-8/9 声称"认知仅 4 表"与实测冲突（X-23~X-28） | ① 认知资产+心智状态全归 `ecos_cognitive`，`ecos_cognitive` 内错放的 11 张业务/WM 表定性停写；② 认知维持 `public`，`ecos_cognitive` 更名他用于业务域；③ 折中：仅新建 mind 两表入 `ecos_cognitive`，belief/hypothesis 原地合规化 | **①**（与 ST07 控制域 5+1 单源权威一致；②会让 ST07 再次反义）。执行顺序：先 `{schema}.` 限定 + 新表，再双写迁移，禁一次性 DROP |
| **R-14** | **E1~E4 契约前缀与属主** | 契约 `/api/v1/business/scenarios/{id}/cognition/*` vs 实现 `/api/v1/workspace/scenarios/{id}/cognitive/*`（仅 POST）；认知端点究竟属 cognitive 还是 workspace（X-15/X-48）。**另一重冲突**：ARCH_SPEC §七-4（Q4 裁决）"场景语义不得下沉到引擎参数，只准 `subjectType+subjectId`"，而契约路径把 `scenarios/{id}` 写进引擎侧路径 | ① cognitive 属主 + workspace 只编排转发；② workspace 属主（场景侧聚合），cognitive 只提供 `/api/v1/cognitive/*` 原子能力；③ 双前缀长期并存 | **①**（铁律 §0.6"场景层只编排不生产"；选 ② 则认知能力被场景层私有化，其他消费方无法复用）。**实施口径须同时守 §七-4**：对外契约路径冻结不变，cognitive 内部 Service 一律以 `subjectType+subjectId` 承参，场景前缀由属主层映射，**不新增第二套实现**。②/③ 需同时改 PRD-05 §1.1 与 PRD-07 场景层职责文本 |
| **R-15** | **假设状态机终态与存量映射** | 三套枚举并存（文档 4 值 / 契约 `VALID` / 库内 `VALID,INVALIDATED,ARCHIVED` 7 行）（X-20/X-21） | ① 终态 4 值 + 存量只读别名映射（`VALID→BELIEVED`、`INVALIDATED→REFUTED`、`ARCHIVED→REFUTED`）+ `old_status` 留痕；② 终态 4 值 + 存量按①映射后**直接改写**（不留痕）；③ 扩枚举至 6 值容纳旧词 | **①**（历史可追溯优先；③会让"冻结枚举"失去契约意义，前端三色/状态徽标要重设计）。CHECK 约束延后至应用全量切换（E.2 V189） |
| **R-16** | **aiming:18084 无鉴权形态 + 路由唯一性门禁** | service 态排除 Security 自动装配 + `header-auth=false` ⇒ 50 端点裸奔；`/api/v1/engine/cognitive` 双占（X-9/X-10） | ① service 态内置同等鉴权（复用 security-engine 校验）；② service 态只允许内网回环监听 + 只在 monolith 态承流（S3 前不切）；③ 引入服务间 token（mTLS/内部 JWT） | **① + ③ 组合**：service 态必须能独立裁决（否则 S3 切流即安全倒退）；同时 S3 门禁加"两态鉴权等价测试"。若选 ②，须在 ARCH_SPEC §S3 明示"cognitive 永久 monolith"，与 ADR-15 冲突需另开 ADR |
| **R-17** | **确定性计算产物落点（ADR-14 落地路径）** | `ecos_dw` 实测仅 `doc`/`doc_chunk` 两张 **0 行**表；forecast/scenario 运行结果实际存 `public.ecos_scenario_run` 的 **4 个 jsonb 列**（违 MC02 + ADR-14）（X-28/X-30） | ① 建 `ecos_dw.ecos_application_forecast_run` 等 APPLICATION 表，经 data-engine 写通道，jsonb→TEXT，旧列只停写；② 维持 `public.ecos_scenario_run`，仅把 jsonb 改 TEXT 平行列（ADR-14 记为例外）；③ 产物完全不落库（每次重算，只存快照指针） | **①**（ADR-14 是已批准决策，②/③ 需先修订 ADR-14；③会使"重跑一致"门禁（M1）无法验证）。选 ① 时 09 册 FC-02/FC-03 的运行快照读端点契约同步改 |

> **批准后处置**：R-13~R-17 已按推荐项批准 ⇒ 相关 DDL（`V187`~`V199`）本轮**落迁移脚本文件**（不实跑库，§14.4）；代码与文档改造项照常推进（尤其 C113/C114 两项 P0 安全与掩蔽，属 M0 直改，不以批准为前提）。

---

## 八、与 W-Agent 接缝（认知域侧义务清单，v1.1 新增）

> **编号纪律**：本节**不新立 W/C 编号**（避免同族双口径），只登记"本册与 W Agent 编排域（分册 10 / PRD-10 / PRD-0X 承接需求族）之间的接缝义务"。
> 接缝中若发现新偏差，必须**同窗**回填 ARCH_SPEC §十一 C 系列并在分册侧登记对应 W 号后方可引用，否则只写"待登记"。
> 本节承接需求（REQ-COG-06~09 族）**已随 2026-09-29 §十四.1 批准并计入已批准基线 138**；**批准 ≠ 已实现 ≠ 已验收**——`pw *` 用例在前置件 P-3（Playwright 工程）建成前一律记"未执行"，未创建的测试类同样记"未执行"。

### 8.1 接缝总表

| 接缝 | W Agent 侧对象 | 本册义务 | 边界（不得越界项） | 依据 |
|:--|:--|:--|:--|:--|
| C 层 11 类对象目录 | Run 的 `context/situation`、`evidence`、`belief`、`decision_basis` 引用 | 提供**对象目录**（枚举 + 物理载体或"引用态"二选一）与只读解析端点 | W 侧不得在 `ecos_ai.ecos_wagent_*` 复制 C 对象定义；不得把 `cognitive_model_instance` 与模型资产 `ecos_cognitive_model` 混用一表 | PRD-05 REQ-COG-06；ARCH_SPEC **C94 / C98**；本册 **X-19 / W116 / §Cg-2** |
| Mind 注册与场景绑定 | E1 `detect` 的 `capabilityMask`/`closedLoopBounds` | `ecos_cognitive_mind` + `ecos_cognitive_scenario_mind` 建表与存在性预检（当前**物理不存在**） | 表未建前 E1 不得以 200 空数组蒙过；`kb_mind_registry` 名字禁用 | REQ-COG-06；Q11 / ADR-8 §5；PRD-01 **DB-03**（v1.2 已更正查表名） |
| CM-01~07 注册与发布门禁 | Planner 选模型（REQ-WAG-05/13） | 模型类型枚举 + 版本不可变 + 四类门禁（contract / regression / backtest / calibration） | **CM-07 只能产 hypothesis**，不得写 `belief.probability` 或任何金额/比率列；grade 禁 LLM 回填；**CM-06 相似度检索形态与附件不符**（实测：载体存在但不是向量——`PrecedentRecaller` 调 `DecisionServiceImpl.findSimilarDecisions`，SQL 为 `category` 精确匹配 + `scenario/reasoning` ILIKE，源码注释自认「降级策略」；`PrecedentRef.similarityEvidence.vector_score` 恒填 **-1**，`MIN_SIMILARITY=0.5` 在无数值时被 `domain_match` 分支旁路放行；`ecos_decision*` 全部 DDL（V103/V125/V151）无向量列，pgvector+HNSW 只在 KB 侧 V137 `vector(1536)` 且**表 0 行**）；**CM-04 属部分实装且与附件「逐步支持」口径相容**（`CausalReasonerServiceImpl` 283 行 KG `CAUSES/AFFECTS/CORRELATES` 逐层遍历 + `CausalDetector` 128 行 + `RootCauseAnalyzer`；贝叶斯侧 `POST /api/v1/cognitive/beliefs/{variable}/update-by-evidence` 已落（P2b，`mental/BayesianUpdater` 纯 Java 加权更新 + version+1 溯源）；反事实侧 `CounterfactualSimulator` + `POST /api/v1/cognitive/counterfactual` 已落（P3a，干预 SET/DELTA + 蒙特卡洛 N≤5000）；**无结构方程、无 DAG 拓扑辨识 / do-calculus**）⇒ CM-06 是否接入向量检索（复用 KB pgvector 还是给 `ecos_decision` 新列，受 MC05 向量禁入控制 schema 约束）与 CM-04 首期边界**已按 R-67 ② 批准**（登记于 §13.11、批准于 §十四.1：CM-06 向量形态升级前置未解 ⇒ 暂不接入；CM-04 保持附件"逐步支持"口径；本册前稿曾误记「CM-04/CM-06 首期无完整载体」，已按上述实测更正——只按关键字 `case_based`/`analogical` 检索会漏掉 `PrecedentRecaller` 这一实际命名） | PRD-05 REQ-COG-07；附件二册 :1056-1062/:1116-1117 |
| Decision Basis 只读 | 叙述与决策卡引用 `decisionId + version + hash` | 提供只读解析；`ecos_decision_case` 在 `public` 与 `ecos_ai` **双侧并存**必须先定写权威 | 权威未定前禁两处同时开写口；Agent 侧不得回写；快照 JSON 列 `TEXT` 不建索引（MC02） | PRD-05 REQ-COG-08；只读复核原则（禁据推断改代码） |
| 确定性计算产物 | Agent 只引用 `forecastRunId`，不参与金额计算 | 计算组件（`DeterministicForecastCalculator` 暂名）产物经 **data-engine 写通道**落 `ecos_dw` | cognitive 不直连 `ecos_dw`、不在引擎控制 schema 建金额表（ST08）；与 `ScenarioSimulatorServiceImpl` / `CausalReasonerService` 职责互不回退 | **ADR-14**；PRD-05 REQ-COG-09 |

### 8.2 接缝验收（可执行标识）

1. `mvn -Dtest=CognitiveObjectCatalogTest#everyCClassHasExactlyOneCarrierOrNone`；
2. `mvn -Dtest=Cm07NumericGuardTest#llmTypeCannotWriteProbabilityOrAmount`；
3. `mvn -Dtest=DecisionBasisOwnershipTest#decisionCaseHasSingleWritableCarrier`（权威实测前记"未执行"）；
4. `mvn -Dtest=DeterministicForecastArtifactTest#resultsLandInEcosDwNotEngineSchema`；
5. 界面（P13 认知模型注册中心，载体归卷 08）：`pw p13-cognitive-model-registry.spec.ts` —— P-3 未建成记"未执行"。

---

## 九、追溯矩阵、依赖批次与门禁状态

### 8.1 REQ ↔ F ↔ W/C ↔ 验收标识（主责 5 项全覆盖）

| REQ | 优先级 | 设计项 | 偏差登记 | 可执行验收标识（Q13） |
|:--|:--|:--|:--|:--|
| REQ-COG-01 E1–E4 | P0 | F05-02/03/04/05/06/07/14 | C97,C98,C99,C100,C101,C102,C113,C114 | `mvn -Dtest=ScenarioCognitionIntegrationTest`（6 法）+ `CognitiveErrorMatrixIntegrationTest`（6 法）+ `tests/scenario-cognition.spec.ts › COG-E1/E2/E3/E4/503` |
| REQ-COG-02 领域化因果诊断 | P1 | F05-09 | C118（伪实现）、C114（掩蔽链）、C115（LLM 绕底座） | `mvn -Dtest=CausalChainDeterminismTest, CausalChainEvidenceTest, DiagnoseContractRegressionTest` |
| REQ-COG-03 反事实由预测运行驱动 | P0 | F05-10 | C111（跨域写）、C115（LLM 推演通道）、**R-17** | `mvn -Dtest=ScenarioCounterfactualIntegrationTest#onlyOverriddenScopeDiffs,#contributionSumBalancesWithinCent` + `ExploratoryGuardTest` |
| REQ-COG-04 capabilityMask 枚举与映射 | P1 | F05-11 | C100,C119 | `mvn -Dtest=MindCapabilityMaskTest#enumMembersAreExactlyFive,#enumMatchesContractDocTable` + `DomainAliasConfigTest` |
| REQ-COG-05 铁律守护 | P0（守护型） | F05-12 | C121（零接线） | 5 个 `mvn -Dtest=CognitiveIronLawsTest#…` / `CognitiveDependencyAuditTest` / `CognitiveNoLlmNumbersTest` 全绿并入 CI `pr-gate` |

### 8.2 依赖与批次

| 设计项 | 依赖（前置） | 被依赖 | 批次 |
|:--|:--|:--|:--|
| F05-01/02（契约单源 + Mind 资产） | R-13/R-14 裁决、00 册 `CognitiveApiPaths` 基线 | F05-03~07 全部 | **A（P0 阻断）** |
| F05-03~07（E1~E4 + 错误矩阵） | F05-02、04 册 belief/hypothesis 供数、DB-03 预检 | 08 册右栏、04 册契约守护 | A |
| F05-13（三层纪律） | 02 册写通道、`SchemaDriftLintTest` 扩展 | 所有 DDL 与查询 | A |
| F05-14（鉴权两态） | R-16、01 册 `SecurityDecisionService#decide` | S3 切流门禁 | A |
| F05-15/16（底座 + stub 显式化） | 00 册 `ServiceEndpointResolver`/llm-gateway/runtime-task | 06 册智能域 | A |
| F05-09/10（诊断 + 反事实） | 09 册 FC-02 运行快照只读端点、03 册口径 | 09 册演练 4、PRD-08 | **B（M2）** |
| F05-11（枚举映射） | F05-03 | — | A（与 COG-01 同批） |
| F05-12（五守护接线） | ARCH-07、ArchUnit 谓词扩展 | 全部认知变更 | A + 持续 |
| F05-17 / B 章（前端） | F05-01~07 契约、08 册工程化 | 08 册 WS-03 | A（auth 旁路）+ B |

### 8.3 门禁状态（本册自检）

| 门禁 | 判定 | 依据 |
|:--|:--:|:--|
| Gate-1（模块划分 + 选型） | 本册不重开；**5 项 R-13~R-17 已回填需求检视报告 §13.4 并于 §十四.1 全量批准** | ARCH_SPEC Gate-1 第 9 行 |
| 需求覆盖 | 主责 5 项 REQ 全部有 F 项 + 验收标识，无遗漏 | 8.1 |
| PRD 追溯 | 每个 F 项锚定 REQ 或铁律条款；偏差全部 W→C 一一映射（25 项） | F 章 |
| DDL 合规自检 | `V187~V189` 草案逐列核 MC01/MC02/MC03/DR06~DR08/§四附则1；无 jsonb、无 nextval、无裸表名、无 partial index | E.2 |
| 编号回填 | C97~C121 **待回填 ARCH_SPEC §十一**（本册完成即刻执行，不延后） | F 章表头 |
| 虚假验收防线 | 全部验收标识为 `mvn -Dtest=类#方法` 或 `*.spec.ts › 用例名`；本册内**无**"人工确认""E2E 通过"表述 | Q13 |
| 裁决依赖声明 | R-13~R-17 未裁决 → DDL 不落脚本；但 C113（无鉴权）/C114（fail-open）两项 P0 **不等裁决**，可先行改造 | 7.2 |
| **Gate-2（技术负责人批准）** | **已通过**（凭证 = 需求检视报告 §十四.3，用户批准 2026-09-29；`deliverable_allowed=true`） | — |

---

**本册一句话结论**：认知域当前不是"实现有偏差"，而是**主责 P0 能力的契约端点与数据源双双不存在**（E1/E2 零命中 + 全库无 mind 表/列），其上运行着一套路径不同、方法不匹配、三套状态枚举并存、0 Mapper 裸 SQL、service 态无鉴权、伪实现以 200 返回固定值的近似系统。本册把它重建为：契约单源 + Mind 资产落点定版 + 三层纪律 + 两态鉴权等价 + 显式降级 + 五守护接线，并把五项存量归属议题（R-13~R-17）交由裁决。

<!-- 详细设计-05-认知域 / 2026-09-29 / v1.2（2026-09-29 定版） / W115~W139 → C97~C121 / R-13 ①、R-14 ①、R-15 ①、R-16 ①+③、R-17 ①、R-42 ①、R-67 ② 已批准（报告 §十四.1） / Gate-1 已签字、Gate-2 已通过 / 本轮未实跑库、未改业务代码 -->
