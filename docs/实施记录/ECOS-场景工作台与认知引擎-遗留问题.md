# ECOS 场景工作台与认知引擎 — 遗留问题

> 日期：2026-11-03
> 分级：P0 = 阻断验收红线；P1 = 功能缺口；P2 = 门禁/环境；P3 = 技术债。

## P0（阻断“完成”判定）

| # | 问题 | 影响 | 需要谁/什么 |
|---|---|---|---|
| P0-1 | ~~数据库迁移 V311/V312 未永久落库~~（2026-11-04 已授权落库） | 已执行并核验：框架表存在、种子 4 行、`run_mode/source_run_id/sandbox_lineage` 3 列、3 索引、`run_mode` 默认 `FORMAL`；`MigrationVersionUniquenessTest` 5/5 通过；live `GET /cognitive/frameworks` 返回 4 条、`POST .../publish` 200 且 PUBLISHED 列表可见 | 已关闭 |
| P0-3 | ~~live 业务端点 403~~（2026-11-04 复验不复现，已补测场景端点） | 登录 `code=0`；带 admin token：`GET /api/v1/cognitive/frameworks` 200、`GET /api/v1/system/users` 200。场景族经 workspace:18090 + Bearer + `X-ECOS-USER/ROLE/TENANT/DOMAIN` 实测：`GET /api/v1/workspace/scenarios` 200、详情 200、`/completeness` 200（无安全策略放宽）。gateway 本身不承载 `/api/v1/workspace/scenarios/**`（404 属路由面差异，非鉴权缺陷） | 已关闭 |
| P0-2 | **年度经营预测 E2E 未执行（2026-11-04 实证定位到五个阻断源）** | ① 目标场景 `sc_ent2_group_month` 完整度 `coverage=0.3333`、`verdict=PARTIAL`。**订正**：5 条边在 `/graph` 中均已存在（`bsl_ent2_extraction_*`、`bsl_ent2_cognition_*`），缺的不是连边；`EXTRACTION` 失败于契约探测 `isVerified()` 要求「syncedCount>0 且 lastSyncTime 非空」，实测 `syncedCount=28` 但 `lastSyncTime` 全 `null` ⇒ 需一次真实知识同步落 `kg_sync_log`；② **`COGNITION` 边无契约载体**：`HttpContractProbeFacade` 对该边直接抛异常（“智能体注册表未持有知识库引用，需求侧缺口”），且 `ecos_ai.ecos_agent_registry.metadata` 中 `knowledge` 引用行数=0，任何数据状态都无法 present；③ 正式口径读取被 fail-closed：`GET /api/v1/workspace/calibers` → 403 `scenario:read:caliber_definition / RLS_NO_POLICY_MATCHED`，库内 31 条 RLS 策略无一命中 `caliber`（安全策略数据缺口，不绕过）；④ `ecos_ai.ecos_scenario_run` 全表仅 1 行 `sc_entmgmt/FORMAL/SUCCEEDED_DEGRADED`，目标场景 **0 行**、`SANDBOX` **0 行**；⑤ 目标蓝图 `bp_ent2_sc_ent2_group_month` 停在 `BLUEPRINT_READY`、`approvalRef=null`，`artifactDigest/contextDigest/traceId/actor/releasedTime` 全空（未发布 + validate `passed=false`），结论追溯链尚未产生；⑥ `POST .../pre-validate` 实测 `mind: pass=false（no active mind）`；⑦ `sandbox_coverage: pass=false`，缺 `DATASOURCE/KNOWLEDGE_ARTICLE/INTERFACE_REF/AGENT_PROFILE/ONTOLOGY_ENTITY/SECURITY_POLICY` 六类节点（`bindings_reachability` 仅 1/3 PARTIAL） | 需 PM/安全侧决策：为 COGNITION 边定义契约载体（新增智能体→知识库引用契约，ADR）；补 `scenario:read:caliber_definition` 的 RLS 策略（安全数据变更须授权）；补齐 EXTRACTION 连边与知识库同步状态；建立目标场景运行记录并拉全栈。10 项业务验证在此之前不计通过 |
| P0-4 | **全栈 API/权限负向测试未跑**（PDP 下线、认知 503、E1-E4 live） | SW-04/07/08/09、CE-10 仅有单测/静态证据。早期 live 观测：gateway 启动、health 200；业务端点对 admin 返回 **Spring 默认 403 JSON**。排除项（只读实证）：JWT 公私钥匹配、admin `clearance_level=5`、日志无 JWT 校验失败、无 Clearance 超管旁路/等级不足告警；且 Clearance 拒绝会返回 `ApiResponse` JSON（非默认体）。2026-11-04 gateway 重建后 frameworks/users 已 200，403 未再复现，但负向矩阵仍未跑 | 需显式授权做 live 负向排查；本任务不改安全策略、不绕过 fail-closed |

## P1（设计缺口，本批未实现）

| # | 问题 | 证据 |
|---|---|---|
| P1-11 | **知识图谱同步接口不可用（直接阻断 EXTRACTION 边契约）** | 2026-11-04 live：`POST /api/v1/knowledge/sync/trigger` 与 `POST /api/v1/knowledge/sync/object/{objectType}` 均返回 200 started，但 `kg_sync_log` 立即落 `failed`：前者「读取本体快照失败: ontologyId=ALL」，后者对 `entmgmt` 同样「ontologyId=entmgmt」。改用真实本体 id `bc129d248741505d9687de171a3bfff7` 后返回 **500 `ECOS-KB-050 kb_nav_* 表缺失或 DDL 未执行，DB 层异常：value too long for type character varying(20)`**。⇒ 同步从未成功写入 `lastSyncTime`（status 全 `null`），故 `ContractSnapshot.isVerified()` 恒 false、EXTRACTION 恒 missing。定位：`ecos_knowledge.kb_nav_category/tag/article_rel` 的 `version_no varchar(20)` 是**数值递增语义**（`NavTaxonDao` 实为 `CAST(version_no AS INTEGER)+1`），32 字符值属**写入口径错配**，不得简单加宽列掩盖。**2026-11-04 二次定位并已修一半**：真正触发 500 的列是 `ecos_knowledge.ecos_kg_sync_log.object_type varchar(20)` —— 同步端点把 32 位本体 id 原样写入 object_type。已新增 **V313**（`graph_node/graph_edge/extract_candidate.ontology_version` + `ecos_kg_sync_log.object_type` 统一加宽到 128，仅加宽、语义不变）并对 dev 库实跑（4 条 ALTER 成功）；`MigrationVersionUniquenessTest` 基线同批 312/313 → 313/314，实跑 `Tests run: 5, Failures: 0` / `BUILD SUCCESS`。
修复后 live 复测：`POST /knowledge/sync/object/bc129d24…` → **不再 500**，日志 `failed: 读取本体快照失败: ontologyId=bc129d24…` ⇒ 剩余为**数据前置**：知识域无该本体的 `kb_ontology_snapshot` 行，需先由本体→知识快照任务生成快照，同步才能对齐版本并写 `lastSyncTime`。**2026-11-04 三次定位并已解阻（B28/B29）**：① 快照从未生成的真因是**本体版本发布 500**（`OntologyVersionDao` 读写 `created_at` / `snapshot::jsonb`，live 实列 `create_time` / `snapshot_json`），已修并加 `OntologyVersionContractDaoTest`（6/6, ontology 模块 288/0/0）；② 发布事件写快照失败的真因是 `ecos_knowledge.kb_ontology_snapshot` 在 live 不存在，已把快照腿（写+3 处读）切 V179 权威表 `ecos_knowledge.ecos_kb_ontology_snapshot`（kb 模块 343/0/2）；③ `graph_node/graph_edge` 缺 V134 溯源列（8 列全缺），新增 **V314**（幂等重放，已实跑）并同批升门禁基线 314/315。live 结果：`ont_entmgmt@1.0.1` 快照落盘（`schema_hash=c063657a…`），该次发布无 FAILED 行；`POST /knowledge/sync/object/ont_entmgmt` → 200 started（不再「读取本体快照失败」）|
| P1-12 | **kb 引擎内部 REST 调 DW 层被 403（EXTRACTION 例程的下一个阻断点）** | `tg-obj-1793776670109` → `FAILED / ECOS-DATA-041: DW 层资源端点不可用: http://localhost:8080/api/v1/engine/data/layers/CURATED (403 …)`；同端点带 admin Token 直连 **200**（资源正常）。即 kb 内部客户端未携带服务凭据，被既有 fail-closed 拦下。修法须选服务账号 / Token 透传 / mTLS 之一，属安全面决策，本任务不擅自放宽鉴权 |
| P1-13 | **成功同步不落 `ecos_kg_sync_log`，`lastSyncTime` 恒 null** | `GET /knowledge/sync/status` 中 entmgmt `syncedCount=28/totalCount=28` 但 `lastSyncTime=null`；只有失败行落台账（`insertSyncLogFailed`）。需补成功行或改为读作业表，避免 UI 长期显示"未同步" |

| # | 问题 | 现状 | 建议 |
|---|---|---|---|
| P1-1 | ~~Context Builder 仅接线 DATA_ASSET~~（已全接通） | 已新增 `KnowledgeOntologyContextSourceReader`（KNOWLEDGE/ONTOLOGY）、`CapabilityContextSourceReader`（CAPABILITY）、`CognitiveAssetContextSourceReader`（COGNITIVE）；版本均取源侧，缺失即失败 | 起全栈后做一次 live 验证（可选） |
| P1-2 | ~~Cognitive Method 无模型/接口~~（已补只读目录） | 已内置 5 方法字典 + 框架 `methods[]` 关联 + `GET /api/v1/cognitive/methods[/{id}]`；无方法独立写 API（按 §1.1 方法不是独立可部署资产） | 若需方法独立治理，再评审是否新增字典表 |
| P1-3 | 执行记录为进程内请求态 | 重启丢失；未接 runtime-task。已补 `contextSources`（来源类型/ID/版本）与 `contextDigest` 同记录，追溯链完整但存储仍在内存 | 若要求可恢复，应把执行事件映射到 runtime-task 既有记录，不新建执行表（需确认任务类型映射） |
| P1-4 | ~~Gap 严重级已有分类，无独立查询端点~~（已收口，含前端与发布门槛） | `GET {id}/gaps?severity=` 服务端分类 + 前端 `GapSeveritySummary` 六态展示（tsc + 4 文件/37 测试通过）；`validateForRelease` 分级输出 `openBlockerGaps`/`openWarnings`/`openSuggestions`（`ScenarioBlueprintValidatorTest` 3/3，workspace 全量 384 绿） | 无 |
| P1-5 | ~~前端完整度面板缺失~~（已补） | 已新增 `CompletenessPanel` 挂入蓝图 `completeness` Tab，只读展示 coverage/缺失边/孤岛/探测错误；e2e 用例仍为 skip（未起全栈） | 起全栈后启用 e2e |
| P1-6 | 假设状态词表冲突 | `HypothesisStatus` 四态 vs `CognitiveHypothesisService` 写 `VALID/INVALIDATED` | 需存量数据迁移方案后统一；本批未改 |
| P1-7 | ~~沙盘画布服务未接 `SandboxWriteGuard`~~（已收口） | `ScenarioSandboxLayoutService.saveLayout` 入口断言白名单；`SandboxSaveDTO.runMode` 可选（缺省 FORMAL），未知值拒绝；用例 `ScenarioSandboxLayoutGuardTest` 3/3 绿 | 无（后续新增沙盘写表须同步 `SANDBOX_WHITELIST`） |
| P1-8 | ~~框架执行器白名单未落~~（已收口） | `CognitiveFrameworkRegistryService.ALLOWED_EXECUTORS` 四值白名单；注册时 `steps[].executorType` 非白名单即 `E400_INVALID_PARAM`；用例 `unknownExecutorTypeIsRejected`（6/6 绿，全量 540 绿） | 无（后续若新增执行器类型需同步白名单） |
| P1-9 | **ContextSecurityTrimmer 无生产实现**（代码审计新发现） | 全仓无 `implements ContextSecurityTrimmer` 的非测试类；`ContextBuilderService.applySecurityTrim` 在裁剪器缺席时对必需源一律抛 `E403_SOURCE_DENIED`（fail-closed，0 泄露，但 Context Build 在真实宿主中对 required 源恒 403） | 需在 gateway/aiming 宿主装配 security REST 适配器（POST `/api/v1/security/decision`，`assetRef.table/columns + purpose`），并按 403/503 分态；属跨服务接线，需起全栈验证后再改 |
| P1-10 | **执行期动态切换/重规划事件未落 runtime-event**（B21 后发现） | 选择阶段已记录 `usedFrameworkVersions` + `selectionReason`，计划校验失败已记录 `planRevision`；但执行中途切换框架/重规划仍无事件落库（设计册 §五阶段五「所有动态变化记入运行事件」） | 需确认 runtime-event 的事件类型与字段映射后接入；本批未建第二套事件表 |

## P2（门禁/环境，非本任务引入）

| # | 问题 | 证据 |
|---|---|---|
| P2-1 | `runtime-core` 当前工作树编译失败（`RuntimeCacheManager` 引 `CacheInvalidationRelay` 缺 import，另有未跟踪 `CacheInvalidationNotifier`） | 属用户未提交改动；本任务未修改，避免干扰 |
| P2-2 | common-api 全量 ratchet 仍有存量红：`SecurityDenialHttpStatusRatchetTest`（UserController 2 腿）、`ArchitectureGuardTest`（ServerOrgAttribute）、`NoMachineCodeFactoryFaceRatchetTest` 计数 +5 | 均非本任务文件；本任务只按协议登记了外壳计数 34→35 与认知表白名单 |
| P2-3 | 前端全量 `npm test`（jsdom）在默认 Node 下 `ERR_REQUIRE_ESM` | 既有环境约束；受影响测试已用 `--environment=node` 实跑 |
| P2-4 | `cognitive_context` 禁止项抽查 | 已确认全仓无该表；V311 仅框架表 |
| P2-6 | **全栈启动不稳定（环境侧）** | 2026-11-04 实测：清空全部 gateway JVM 后单实例启动，JVM 存活 >170s 但 8080 无监听；日志含 `UsageCollector` 线程内 `redisConnectionFactory` 创建失败（`BeanPostProcessor before instantiation` + `No bean named 'ConfigurationClassPostProcessor.importRegistry'`），以及历史 `EventBusAuditPublisher` 无默认构造器记录（该类现已 `@Autowired`）。疑运行期在非刷新线程惰性创建 Bean 破坏上下文。需以 jstack/启动日志单独排障；不属本任务新增代码缺陷证据 |
| P2-5 | **既有迁移与代码 schema 漂移（阻断全栈启动）** | `ecos_ontology.ecos_ontology_git_archive_pending`（V170.2 建在 `public`）、`ecos_control.ecos_runtime_audit_retry`（V244 建在 `public`）均被代码按限定 schema 查询。2026-11-04 已授权：执行 V244（`CREATE TABLE/INDEX`），并按 `LIKE public... INCLUDING ALL` 在 `ecos_ontology`/`ecos_control` 幂等补表（均 0 行）；**未改仓库迁移与代码**。建议后续以迁移单源修正该漂移并加门禁 |
| P2-7 | **gateway fat-jar 增量构建不刷新内嵌依赖** | gateway BP 的 `BOOT-INF/lib/workspace-impl-*.jar` 内嵌 workspace 实现；仅 `mvn -pl gateway install` 时 Maven 判定模块自身无变更，fat-jar 时间戳与内容均不更新（实测仍含旧类 md5）。改动 workspace-impl 后必须 `mvn -pl gateway clean install -DskipTests` 再重启 gateway，否则 live 仍走旧逻辑。建议后续在启动前置检查中比对内嵌 jar 与 .m2 时间戳 |

## P3（技术债）

- `ContextBuildController.java:49`、`OutcomeEvaluateController.java:38-72` 仍用字面量路径（本次未改）。
- `SandboxWriteGuard` 原白名单命名与实际表不符，本批已补实测表名；建议加对账测试防再漂移。
- 蓝图端点前缀 `/api/v1/business/scenarios` 与场景其余 `/api/v1/workspace/scenarios` 仍双前缀（存量）。
- ~~前端 `FrameworkPanel` 采用轮询/手动刷新，未接统一缓存~~；`GapSeveritySummary` 每次进入详情各拉一次分类接口，未接缓存（低频只读，暂可接受）。

## 需人工决策的问题

1. **是否授权实跑 V311/V312**（本机 PG），以及是否同时执行回滚演练。
2. **是否授权启动全栈跑年度预测 E2E**；若数据不足，是否同意使用明确标记的合成数据集。
3. **Cognitive Method 的落点**：复用框架步骤 `methodRef`（轻量）还是新建方法字典表（需 ADR）。
4. **执行记录是否必须跨进程恢复**：若必须，需确认 runtime-task 的任务类型与字段映射。
5. **Context 权限裁剪器的资产映射口径**：security `/api/v1/security/decision` 需要 `assetRef.table/columns`，而 Context 源只带 `sourceType/sourceId`。需确认每种 sourceType（DATA_ASSET/KNOWLEDGE/ONTOLOGY/CAPABILITY/COGNITIVE）对应哪张安全资产表与 purpose，否则实现即为臆造策略映射（当前保持 fail-closed 403）。

## 部署、启动与验证（本次实现）

1. 数据库（授权后）：
   ```powershell
   docker exec -i ecos-postgres psql -U postgres -d sys_man -f /path/V311__cognitive_framework_registry.sql
   docker exec -i ecos-postgres psql -U postgres -d sys_man -f /path/V312__scenario_run_mode_lineage.sql
   ```
   幂等：`CREATE ... IF NOT EXISTS` / `ADD COLUMN IF NOT EXISTS` / `ON CONFLICT DO NOTHING`。
2. 构建与启动：
   ```powershell
   mvn -f D:\workspace\javaprojects\ECOS\ecos_backend\pom.xml clean install -DskipTests
   powershell -NoProfile -ExecutionPolicy Bypass -File D:\workspace\javaprojects\ECOS\_win_tasks\start-backend.ps1 -Modules gateway,sysman,datanet,buszhi,aiming,dccheng,workspace -Profile enterprise
   powershell -NoProfile -ExecutionPolicy Bypass -File D:\workspace\javaprojects\ECOS\_win_tasks\start-frontend.ps1
   ```
3. 冒烟（需登录 token）：
   - `GET /api/v1/cognitive/frameworks?status=PUBLISHED` → 四框架（默认 DRAFT，需先 publish 才出现在 PUBLISHED 列表）；
   - `POST /api/v1/cognitive/frameworks/{id}/1.0.0/publish` → 发布；
   - `POST /api/v1/cognitive/frameworks/select` → 硬过滤/排除原因/计划校验；
   - `POST /api/v1/workspace/scenarios/{id}/runs` body `runMode:SANDBOX` → 响应 `runMode=SANDBOX`、`metricsWritten=null`；再以 FORMAL + `baselineRunId=<沙盘run>` → 400。
4. 测试：
   - 后端：`mvn -o -f ecos_backend/pom.xml -pl engine/cognitive-engine/cognitive-engine-impl test`、`-pl workspace/workspace-impl test`、`-pl gateway test -Dtest=MigrationVersionUniquenessTest`
   - 前端：`npm run lint`；`npx vitest run --environment=node <受影响用例>`

## 回滚

- 代码：回退本任务 commit / 恢复上述文件即可；新增端点无调用方时不启动即无影响。
- DB：
  - V311：应用侧停用 `/frameworks*` 端点；表保留（IR03 禁脚本内 DROP）。
  - V312：读面回退旧 `SELECT` 即可；列保留（`ADD COLUMN` 无破坏）。
  - 如需彻底清理，仅允许人工裁决后执行，且需先备份。
- 配置：本任务未新增环境变量；无配置回滚项。
