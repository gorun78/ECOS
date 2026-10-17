-- V266 (卷10 §5.1/§5.4 seed): W Agent 业务语义工具目录首批 12 行落 ecos_ai.ecos_wagent_tool_contract（V236 表定义）
-- 追溯: W245~W277 ↔ C227~C259（卷 10 §七）；本脚本承载 F10-17（注册与生命周期）/ F10-18（渐进披露与权限过滤）/
--       F10-19（业务语义工具目录首批 12 个），需求依据 REQ-WAG-17~19；§5.4 明载每工具 endpoint.kind=rest 且指向
--       既有 Engine 端点；**目录中不存在任何通用 CRUD 或 query_db 型工具**（F10-19 明文排除，与卷 06 C138 同罪）
-- 批准: 需求检视报告 §十四 14.1（2026-09-29）之 R-33=①、R-35=①、R-43=推荐项批准（端点前缀单源）；执行边界 §14.4 = 脚本文件落地
-- 状态: 仅落迁移脚本文件，**未对 ecos-postgres 实跑**（实跑属 §14.4 未授权项，需逐项再确认）
--       ⇒ 本批 12 行工具契约的"注册生效"没有任何库侧证据；应用侧 ToolContractRegistry 读不到行 = 目录空集，
--       PlanValidator 七项校验中"工具/契约"两关对任何引用一律拒绝（fail-closed，禁凭空放行未注册工具）
-- 号段: 接续 V265 取 V266（登记表 §二"新增按接续最大号取，不回填空洞 V241~V248"）；与 V265 同批同 commit——
--       V265 组 4 `wagent.toolset.core/analytics/action` 三行成员清单与本表 toolset+name 列**逐字一致**（两处同批改，禁单侧漂移）
-- endpoint 查证（2026-10-10 只读 grep @RequestMapping/@*Mapping 行号定位 Controller，**可达性未实测**、服务未实启）：
--   在位 8 工具 →
--     query_project_profit / get_project_acceptance_status / get_action_effect → data-engine FactController listFacts
--       GET /api/v1/datanet/facts/{factType}（:71 实测在位，page/pageSize/projectId/period/batchId/dqStatus 参数）
--     run_profit_forecast → cognitive-engine ForecastController POST /api/v1/cognitive/forecast（:48 实测在位）
--     find_historical_profile → kb-engine KbProfileController GET /api/v1/knowledge/profiles（:96 实测在位）
--     select_knowledge_profiles → kb-engine KbProfileController GET /api/v1/knowledge/profiles/resolve（:128 实测在位，命中画像+细→粗退化链）
--     check_metric_caliber → ontology-engine CaliberController GET /api/v1/ontology/calibers/{code}/current（:112 实测在位）
--     freeze_data_snapshot → data-engine FactController POST /api/v1/datanet/facts/{factType}/import（:53 实测在位；
--       factType=FORECAST_SNAPSHOT = ecos_dw.ecos_forecast_input_snapshot，V167/F02-06 业务事实五表第 5 表）
--     submit_readiness_task → gateway TaskController POST /api/v1/task/submit（:144 实测在位；runtime-task
--       ITaskManagementService facade 形态，调度收口单源 W255，禁建任务表/自建调度）
--   待复核 3 工具（填设计路径，本行即为复核登记位）→
--     run_cognitive_model：在位**无**通用 CM 执行端点——models 面在位的是注册/门禁/发布（ForecastController :57~:99
--       GET/POST /models、/models/registry、/models/{id}/gates、/models/{id}/publish），执行按意图拆分在
--       forecast(:48)/diagnose(:83)/simulate(:64)；设计路径 POST /api/v1/cognitive/models/{modelId}/execute 待卷 05 收口
--     create_decision_option：§5.2 candidate 面 #17~20 仅 列表/详情/review/publish，**无创建端点**；设计路径
--       POST /api/v1/wagent/candidates（R3-A/#131 待落地；表 ecos_wagent_candidate V238 已在，payload candidate_type=DECISION_OPTION）
--     create_action_draft：WAgentApiPaths.ACTIONS_COLLECTION 常量在位（=§5.2 #24a draftAction，路径正典 /api/v1/wagent/actions），
--       Controller 未落地（实测 wagent/web 目录仅 Goal/Question/Run 三组）；R3-A 落地后以真实映射复核
--   语义注记 2 处（端点在位、口径待复核）→
--     query_project_profit：利润**无独立聚合端点** = STAGE（收入/回款）+ COST（成本）事实行派生（volume 09 口径），派生聚合待 R2-B 工具实现侧定版
--     get_project_acceptance_status：验收状态 = STAGE 事实行 stage 字段取验收档（FactType.STAGE 白名单 :28 实测含 stage 列）；
--       listFacts **无 stage 过滤参数** ⇒ 行级过滤经 period/batchId 粗筛或属主端点补参，待复核
-- 列值词表单源（V236 R1-A 三段一致）: category ∈ read|compute|candidate|commit|notify（WAgentEnums.ToolCategory）、
--       side_effect ∈ none|draft|production（SideEffect）、disclosure ∈ always_loaded|searchable（Disclosure）、
--       status 6 态（ToolContractStatus，本表 CHECK 同步 V236:51）、min_level 数值 = AutomationLevel L0~L3 的 level（WAgentEnums:246~250）、
--       engine 代号 = WAgentEngineCatalog.contractEngineCodesForDdlCheck() = data/ontology/kb/cognitive/ai/security（workspace 场景层不入工具面）
-- 生命周期口径: 12 行一律 status='registered'——F10-17 契约测试五关（Schema 合法/示例调用/超时错误码/幂等/权限拒绝路径）
--       未跑，且 F10-18 只 active 可被召回（ToolContractRegistryTest#onlyActiveVersionSelectable）⇒ 本 seed **只注册不放开可选**；
--       registered→validated→active 的翻转会随 R2-B/#130 五关通过由应用侧单入口执行（本脚本不代写状态跃迁）
-- 披露口径: 12 行 disclosure 全部 searchable；always_loaded 名额留给 tool_search/tool_describe/tool_validate 三元元工具
--       （非本批 12 业务工具成员，随 R2-B 另行登记；F10-18 硬约束 always_loaded 计数 <10 天然满足，V265 组 4 同判）
-- 写通道对齐: create_decision_option / create_action_draft = category='candidate' + side_effect='draft'（F10-21：L1 写一律走
--       Candidate；发布唯一链路 = Candidate 评审 + approval_token + CommitService 调属主端点，F10-23/W259）；
--       本批**无 category='commit' 行** ⇒ ck_wagent_tool_rollback（V236:49）不触发，但 side_effect='production' 三行
--       （run_profit_forecast/run_cognitive_model/freeze_data_snapshot/submit_readiness_task 共 4 行）均预防性填 rollback_plan_text
-- 通用列口径: tenant_id='default'——uniq_wagent_tool_name_ver = UNIQUE(name,version,is_deleted) **不含 tenant 列**
--       ⇒ 首批目录为全局共享登记行，租户级定制须在实例化时复制并递增 version（R-36 挂账，待复核）；
--       org_id NULL；domain='default'（DR08 默认）；version='1.0.0' + version_no='1'（DR07）；
--       id 取可读固定键 wag-tool-*（沿用 V246 seed 先例"可读固定键保证跨 seed/测试可预期"；≤36 字符满足 V236 id VARCHAR(36) 列宽）；
--       create_time/update_time/is_deleted 走 V236 列 DEFAULT，不在本 INSERT 列出（多库兼容，无 NOW() 函数依赖）
-- 幂等: 全部 INSERT ... ON CONFLICT (name, version, is_deleted) DO NOTHING——去重键与 uniq_wagent_tool_name_ver（V236:66）
--       逐字一致 ⇒ 重复执行零报错、零覆盖（不冲掉运维已改值）

INSERT INTO ecos_ai.ecos_wagent_tool_contract
    (id, tenant_id, org_id, name, version, engine, category, side_effect, min_level, required_permission,
     input_schema_json, output_schema_json, timeout_ms, retry_text, is_idempotent, cost_class, is_evidence_output,
     data_classification_text, rollback_plan_text, endpoint_kind, endpoint_url, endpoint_method, owner, sla_text,
     status, deprecated_by, toolset, summary, disclosure, capability_deps_text, domain, version_no, create_by) VALUES

-- ── toolset=core（6，V265 组 4 wagent.toolset.core 逐字一致）──────────────────────────────
  ('wag-tool-query-profit', 'default', NULL, 'query_project_profit', '1.0.0', 'data', 'read', 'none', 1, 'data:fact:read',
   '{"type":"object","required":["projectId"],"properties":{"projectId":{"type":"string","description":"项目ID"},"periodFrom":{"type":"string"},"periodTo":{"type":"string"},"granularity":{"type":"string","enum":["MONTH","QUARTER","YEAR"],"default":"MONTH"},"factType":{"type":"string","enum":["STAGE","COST"],"description":"利润=STAGE收入回款行与COST成本行派生 无独立聚合端点(头注待复核)"}}}',
   '{"type":"object","properties":{"rows":{"type":"array","description":"事实行(经 security RLS/CLS/mask 后)"},"totalProfit":{"type":"number","description":"派生值 amount(STAGE)-amount(COST)"},"coverage":{"type":"number"},"asOf":{"type":"string"},"batchId":{"type":"string"}}}',
   3000, '读幂等，退避重试 ≤2 次；security 裁决不可用 = DENY（E-WA-DEP 由属主侧透传，禁包装 200）', 1, 'low', 1,
   'S2 财务事实（金额列的 RLS/CLS/脱敏裁决归 security-engine 查询链注入，本列仅声明）',
   '无（纯读无副作用）',
   'rest', '/api/v1/datanet/facts/STAGE', 'GET',
   'data-engine FactController listFacts（:71 实测在位；profit 派生聚合口径待复核）', '设计目标 P95≤1000ms、可用率 99.5%（未实测）',
   'registered', NULL, 'core', '查询项目经营利润事实：STAGE(收入/回款)+COST(成本) 事实行，利润按事实侧派生（卷09 事实链）', 'searchable',
   'security-engine(policy-evaluate);ontology:caliber_published', 'default', '1', 'seed-v266'),
  ('wag-tool-accept-status', 'default', NULL, 'get_project_acceptance_status', '1.0.0', 'data', 'read', 'none', 1, 'data:fact:read',
   '{"type":"object","required":["projectId"],"properties":{"projectId":{"type":"string"},"period":{"type":"string","description":"期间(可选)"}}}',
   '{"type":"object","properties":{"stages":{"type":"array","description":"STAGE 事实行按 stage 档投影"},"acceptanceStatus":{"type":"string"},"asOf":{"type":"string"}}}',
   3000, '读幂等，退避重试 ≤2 次', 1, 'low', 1,
   'S2 业务事实',
   '无（纯读无副作用）',
   'rest', '/api/v1/datanet/facts/STAGE', 'GET',
   'data-engine FactController listFacts（:71 在位；验收档=STAGE.stage 行级过滤，listFacts 无 stage 参数 → 待复核）', '设计目标 P95≤1000ms（未实测）',
   'registered', NULL, 'core', '查询项目验收状态：读 STAGE 事实行 stage 字段取验收档（待复核：stage 过滤参数或属主端点补参）', 'searchable',
   'security-engine(policy-evaluate)', 'default', '1', 'seed-v266'),
  ('wag-tool-cal-check', 'default', NULL, 'check_metric_caliber', '1.0.0', 'ontology', 'read', 'none', 1, 'ontology:caliber:read',
   '{"type":"object","required":["metricCode"],"properties":{"metricCode":{"type":"string"},"atVersion":{"type":"string","description":"空=当前发布版"}}}',
   '{"type":"object","properties":{"caliberId":{"type":"string"},"versionNo":{"type":"string"},"status":{"type":"string"},"definition":{"type":"object"},"conflictCount":{"type":"integer"}}}',
   3000, '读幂等，退避重试 ≤2 次', 1, 'low', 1,
   'S1 口径定义（口径主权 ecos_ontology，只读引用 F10-07）',
   '无（纯读无副作用）',
   'rest', '/api/v1/ontology/calibers/{code}/current', 'GET',
   'ontology-engine CaliberController getCurrent（:112 实测在位；绑定链 metric_definition V168.1）', '设计目标 P95≤500ms（未实测）',
   'registered', NULL, 'core', '核验指标口径当前版本与状态（I 层 onto.check_metric/detect_conflict 探测项 F10-08）', 'searchable',
   'metric_definition(V168.1);ecos_caliber(V167.1)', 'default', '1', 'seed-v266'),
  ('wag-tool-hist-profile', 'default', NULL, 'find_historical_profile', '1.0.0', 'kb', 'read', 'none', 1, 'knowledge:profile:read',
   '{"type":"object","required":["metric"],"properties":{"metric":{"type":"string"},"domain":{"type":"string"},"status":{"type":"string","default":"PUBLISHED"},"limit":{"type":"integer","default":50}}}',
   '{"type":"object","properties":{"profiles":{"type":"array","description":"KbProfileDO 投影(样本量/applicability 随行)"}}}',
   3000, '读幂等，退避重试 ≤2 次', 1, 'low', 1,
   'S1 知识画像',
   '无（纯读无副作用）',
   'rest', '/api/v1/knowledge/profiles', 'GET',
   'kb-engine KbProfileController list（:96 实测在位；ecos_knowledge.ecos_kb_profile V176）', '设计目标 P95≤500ms（未实测）',
   'registered', NULL, 'core', '检索历史画像池（K 层历史样本来源；n<30/n<10 两档降级联动 V265 组 1 阈值）', 'searchable',
   'kb-profile(V176)', 'default', '1', 'seed-v266'),
  ('wag-tool-snap-freeze', 'default', NULL, 'freeze_data_snapshot', '1.0.0', 'data', 'compute', 'production', 1, 'data:fact:snapshot:write',
   '{"type":"object","required":["forecastRunId","asOfTime","scopeHash"],"properties":{"forecastRunId":{"type":"string"},"asOfTime":{"type":"string"},"scopeHash":{"type":"string"},"factRefsJson":{"type":"string"},"caliberId":{"type":"string"},"caliberVersion":{"type":"string"},"checksum":{"type":"string"}}}',
   '{"type":"object","properties":{"snapshotId":{"type":"string"},"batchId":{"type":"string"},"checksum":{"type":"string"},"snapshotRef":{"type":"object","description":"{id,hash} 引用 §4.3 D 层"}}}',
   10000, 'scopeHash+checksum 相同 ⇒ 幂等跳过（不重冻）；失败记 E-WA-DEP 转缺口补齐（F10-09 type=fetch）', 1, 'low', 1,
   'S2 事实快照（写通道唯一 = data-engine 事实导入，Agent 不直连库表 F10-03 红线 1）',
   '快照行 append-only（batch_id+scope_hash）；作废=superseded 逻辑删（is_deleted=1），禁物理删（IR03）',
   'rest', '/api/v1/datanet/facts/FORECAST_SNAPSHOT/import', 'POST',
   'data-engine FactController importFacts（:53 在位；FORECAST_SNAPSHOT=五表第5表 V167）', '设计目标 P95≤3000ms、批量 ≤100 行/次（EN03，未实测）',
   'registered', NULL, 'core', '冻结预测输入快照（D 层 snapshot_ref 唯一落点；经 data-engine 唯一写通道 F02-05）', 'searchable',
   'security-engine(policy-evaluate);data-engine(unique write channel F02-05)', 'default', '1', 'seed-v266'),
  ('wag-tool-task-submit', 'default', NULL, 'submit_readiness_task', '1.0.0', 'ai', 'notify', 'production', 2, 'task:submit',
   '{"type":"object","required":["taskName","taskType"],"properties":{"taskName":{"type":"string"},"taskType":{"type":"string","description":"对齐 runtime-task 类型池(GET /api/v1/task/types :295)"},"parameters":{"type":"object"},"gapId":{"type":"string","description":"缺口引用(F10-09 type=task)"}}}',
   '{"type":"object","properties":{"taskId":{"type":"string"},"status":{"type":"string","description":"SUBMITTED(状态流转经 runtime-task)"}}}',
   5000, '非幂等（重复提交=新任务）；上层按缺口 id 去重后提交；失败禁静默重试成多任务', 0, 'low', 0,
   'S1 任务元数据',
   'POST /api/v1/task/{taskId}/cancel（gateway TaskController :196 实测在位）；对账唤醒属 runtime-task 周期任务（W255）',
   'rest', '/api/v1/task/submit', 'POST',
   'gateway TaskController submit（:144 在位；载体归 runtime-task 底座，engine=ai 属主）', '设计目标 P95≤800ms（未实测）',
   'registered', NULL, 'core', '提交缺口补齐待办任务（F10-09 gap type=task 唯一落点；L2+ 且需人工确认，禁建任务表/自建调度）', 'searchable',
   'runtime-task(ITaskManagementService facade);wagent-restore-listener(ecos.agent topic)', 'default', '1', 'seed-v266'),

-- ── toolset=analytics（3，V265 组 4 wagent.toolset.analytics 逐字一致）─────────────────────
  ('wag-tool-profit-fcst', 'default', NULL, 'run_profit_forecast', '1.0.0', 'cognitive', 'compute', 'production', 1, 'cognitive:forecast:run',
   '{"type":"object","required":["metricId","asOf"],"properties":{"metricId":{"type":"string"},"caliberVersion":{"type":"string"},"asOf":{"type":"string"},"horizon":{"type":"integer","description":"预测期数"},"scope":{"type":"object"},"cmType":{"type":"string","description":"CM-01~05 选择(卷05 注册中心)"}}}',
   '{"type":"object","properties":{"runId":{"type":"string","description":"ecos_dw.ecos_fc_run V215"},"forecastSeries":{"type":"array"},"confidenceInterval":{"type":"object"},"grade":{"type":"string"},"evidenceRefs":{"type":"array"}}}',
   30000, '超时 30s 一律失败不重试（cognitive 引擎红线：推理不 >30s）；引擎不可用 E-WA-DEP 允许调用方按预算重试', 0, 'medium', 1,
   'S2 财务预测（产物数字唯一来源 = CM/事实列，§0.2 行 2 红线，禁 LLM 出数）',
   '运行产物落 ecos_dw（ADR-14 确定性计算产物经 data-engine 写通道）；不可变追加，回放一致性由 input_hash→output_hash（F10-14），禁就地改写',
   'rest', '/api/v1/cognitive/forecast', 'POST',
   'cognitive-engine ForecastController forecast（:48 实测在位；移动均值+线性外推+KG 因子修正，PMO-51）', '设计目标 P95≤15s（未实测；上限 30s 为引擎红线）',
   'registered', NULL, 'analytics', '运行利润预测（CM-01 时序基线 + KG 因子修正的确定性计算；C 层 cog.model_availability 联动）', 'searchable',
   'cognitive-model-registry(active version);ontology:caliber_published', 'default', '1', 'seed-v266'),
  ('wag-tool-run-cm', 'default', NULL, 'run_cognitive_model', '1.0.0', 'cognitive', 'compute', 'production', 1, 'cognitive:model:run',
   '{"type":"object","required":["modelId","modelVersion","input"],"properties":{"modelId":{"type":"string"},"modelVersion":{"type":"string"},"input":{"type":"object"},"seed":{"type":"integer","description":"蒙特卡洛复现 seed(卷05 反事实同族)"}}}',
   '{"type":"object","properties":{"output":{"type":"object"},"artifactRef":{"type":"string","description":"ecos_dw 产物引用"},"gates":{"type":"array"},"evidenceGrade":{"type":"string"}}}',
   30000, '确定性模型超时不自动重试；E-WA-DEP 可重试由上层预算控制', 0, 'medium', 1,
   'S2 认知计算产物',
   '产物按版本不可变（V261 cm_type_gates_immutable）；模型版本不可就地改写，回退=引用旧版本重跑',
   'rest', '/api/v1/cognitive/models/{modelId}/execute', 'POST',
   'cognitive-engine（设计路径待复核：通用执行端点不在位，归并待卷05 收口）', '未实测（端点未定版，SLA 待复核后回填）',
   'registered', NULL, 'analytics', '按模型 ID 与版本运行认知模型 CM-01~05（待复核：通用执行端点不在位，设计路径登记）', 'searchable',
   'cognitive-model-registry(V198/V261);data-engine(artifact write channel ADR-14)', 'default', '1', 'seed-v266'),
  ('wag-tool-know-select', 'default', NULL, 'select_knowledge_profiles', '1.0.0', 'kb', 'read', 'none', 1, 'knowledge:profile:resolve',
   '{"type":"object","required":["metric"],"properties":{"metric":{"type":"string"},"status":{"type":"string","default":"PUBLISHED"},"atVersion":{"type":"string"}}}',
   '{"type":"object","properties":{"profile":{"type":"object"},"degradeChain":{"type":"array","description":"细→粗退化链(KbProfileController resolve 既有语义)"}}}',
   3000, '读幂等，退避重试 ≤2 次；无命中画像 = 404 语义转 DG-K1 降级判定（V265 组 2），禁算错误', 1, 'low', 1,
   'S1 知识画像',
   '无（纯读无副作用）',
   'rest', '/api/v1/knowledge/profiles/resolve', 'GET',
   'kb-engine KbProfileController resolve（:128 实测在位）', '设计目标 P95≤500ms（未实测）',
   'registered', NULL, 'analytics', '为当前指标与 scope 解析适用画像（K 层 know.select_profiles 探测项；applicability 阈值联动 V265 组 1）', 'searchable',
   'kb-profile(V176);wagent.readiness.threshold(V265 组 1)', 'default', '1', 'seed-v266'),

-- ── toolset=action（3，V265 组 4 wagent.toolset.action 逐字一致；发布不在本集 W259）───────────
  ('wag-tool-dec-option', 'default', NULL, 'create_decision_option', '1.0.0', 'ai', 'candidate', 'draft', 1, 'wagent:candidate:create',
   '{"type":"object","required":["goalId","label"],"properties":{"goalId":{"type":"string"},"decisionId":{"type":"string"},"label":{"type":"string"},"leverType":{"type":"string"},"leverTargetId":{"type":"string"},"profitDelta":{"type":"number"},"revenueDelta":{"type":"number"},"costDelta":{"type":"number"},"riskDelta":{"type":"number"},"confidence":{"type":"number"},"isBaseline":{"type":"boolean","description":"baseline_slot 唯一性由应用层保证(§6.1 禁 partial index)"}}}',
   '{"type":"object","properties":{"candidateId":{"type":"string"},"payloadHash":{"type":"string"},"status":{"type":"string","default":"pending_review"}}}',
   10000, 'payload_hash 相同 ⇒ 幂等去重不重复建候选（F10-20）', 1, 'low', 0,
   'S2 决策选项（数字来自 CM/事实，引用可追溯 F10-24）',
   '候选作废：status→expired + is_deleted 软删，不动属主域表；发布另走 Candidate 评审 + approval_token 闸门（F10-23）',
   'rest', '/api/v1/wagent/candidates', 'POST',
   'ai-engine wagent（设计路径待复核：§5.2 candidate #17~20 无创建端点，R3-A/#131 落地定版）', '未实测（端点未定版）',
   'registered', NULL, 'action', '创建决策选项候选（写通道唯一=Candidate；L1 只读+建议语义，发布另闸门，§5.2 #23 同族待 R3-A）', 'searchable',
   'wagent-candidate(V238);wagent-readiness(pinned ref)', 'default', '1', 'seed-v266'),
  ('wag-tool-act-draft', 'default', NULL, 'create_action_draft', '1.0.0', 'ai', 'candidate', 'draft', 1, 'wagent:action:draft',
   '{"type":"object","required":["goalId","title","ownerId"],"properties":{"optionId":{"type":"string"},"goalId":{"type":"string"},"title":{"type":"string"},"ownerId":{"type":"string"},"role":{"type":"string"},"dueDate":{"type":"string"},"kpiText":{"type":"string"},"publishTarget":{"type":"string","description":"task|scenario_action|ontology_action|effect_writeback（V265 组 6 适配器白名单）"},"publishMode":{"type":"string"}}}',
   '{"type":"object","properties":{"actionId":{"type":"string"},"status":{"type":"string","default":"draft"},"approvalTokenRequired":{"type":"boolean","default":true}}}',
   10000, '同 optionId+title 哈希去重（应用层）', 1, 'low', 0,
   'S2 行动草稿',
   '草稿软删（is_deleted=1，V236 通用列形态）；提交发布必经 approval_token + 属主端点（CommitService/CandidateCommitPort，Agent 不写对方表 F10-03 红线 1）',
   'rest', '/api/v1/wagent/actions', 'POST',
   'ai-engine wagent（设计路径待复核：ACTIONS_COLLECTION 常量在位、Controller 未落地，R3-A 复核）', '未实测（端点未落地）',
   'registered', NULL, 'action', '创建行动草稿（Decision/Option/Action 属主 = ai-engine，F10-02/R-32①；写通道仅 Candidate，目录无 publish/commit 型工具）', 'searchable',
   'wagent-action(V241 待定表);wagent.action_publish_target.adapter(V265 组 6)', 'default', '1', 'seed-v266'),
  ('wag-tool-act-effect', 'default', NULL, 'get_action_effect', '1.0.0', 'data', 'read', 'none', 1, 'data:fact:read',
   '{"type":"object","required":["actionId"],"properties":{"actionId":{"type":"string"},"batchId":{"type":"string","description":"效果回灌批次引用"},"period":{"type":"string"},"projectId":{"type":"string"}}}',
   '{"type":"object","properties":{"planned":{"type":"object"},"actual":{"type":"object"},"deviationReason":{"type":"string"},"backtestRef":{"type":"string","description":"ecos_dw.ecos_fc_backtest V217"}}}',
   3000, '读幂等，退避重试 ≤2 次', 1, 'low', 1,
   'S2 效果事实（回灌行经 DQ 门禁，重复回灌 PUBLISHED 行 409 ECOS-DATA-021）',
   '无（纯读无副作用）',
   'rest', '/api/v1/datanet/facts/STAGE', 'GET',
   'data-engine FactController listFacts（:71 在位；效果回灌通道 POST facts/action-outcomes :92）', '设计目标 P95≤1000ms（未实测）',
   'registered', NULL, 'action', '读回行动效果（T10 结果回灌唯一管道 = data-engine 事实面，§9.5 T10 行；禁独立回灌路径）', 'searchable',
   'security-engine(policy-evaluate);data-engine(unique write channel F02-05)', 'default', '1', 'seed-v266')
ON CONFLICT (name, version, is_deleted) DO NOTHING;

-- ── 注释登记 ──────────────────────────────────────────────
COMMENT ON TABLE ecos_ai.ecos_wagent_tool_contract IS 'W Agent 工具契约（附件 §5.1/§5.4 重写；UNIQUE(name,version)；commit 类必带回滚预案）；首批 12 业务语义工具 seed 见 V266（status=registered，toolset core/analytics/action 与 V265 组 4 逐字一致；run_cognitive_model/create_decision_option/create_action_draft 三行 endpoint 为设计路径待复核）';

-- ── 回滚说明 ──────────────────────────────────────────────
-- 数据回滚：应用侧 ToolContractRegistry 读不到行 = 工具目录空集 ⇒ PlanValidator"工具/契约"两关对任何工具引用一律拒绝
--           （fail-closed，无隐性放行）；回滚动作 = 运维把本批 12 行 status 置 'retired' 或 is_deleted 置 1（本脚本不 DROP、不 DELETE，IR03）。
-- 幂等性：INSERT ... ON CONFLICT (name, version, is_deleted) DO NOTHING ⇒ 重复执行零报错、零覆盖（uniq_wagent_tool_name_ver 为去重目标）。
-- 工具名唯一性：12 个 name 字面取自卷 10 F10-19 冻结清单与 V265 组 4 成员清单（三处同字面，禁"顺手改名统一"——工具名与 API 同规 = 只增不改）。
-- 上调禁区：min_level 是工具**下限声明**（生效级 = min(tool,run ceiling,tenant cap)，附件 AC-03）；运维侧只可把行置 retired/deprecated
--           （deprecated_by 指向后继工具），不可 UPDATE 本批行放宽 category/side_effect/min_level（放宽 = 新版本重注册 + 五关重测，F10-17）。
-- 版本策略：下架后重注册须递增 version（唯一索引含 is_deleted 的既定口径，V236:65 注释）；本批 version='1.0.0' 为首版。

-- ── DDL Lint Self-audit ──────────────────────────────────────
-- [DR01] 目标表 ecos_ai.ecos_wagent_tool_contract 限定形态与 V236 建表逐字一致（ST07 五引擎 schema）✓
-- [DR02/DR03] 无新建表 ⇒ 前缀/版本后缀不适用 ✓
-- [DR04/DR05] 无新增列；input_schema_json/output_schema_json 为 V236 既有 *_json TEXT 列（MC02 合规，禁 jsonb）✓
-- [DR06~DR08] 通用列走 V236 列默认（create_time/update_time/is_deleted 未列出即 DEFAULT）；domain='default'（DR08）、version_no='1'（DR07）✓
-- [MC01] 无新主键；id 'wag-tool-*' 可读固定键 ≤36 列宽实测最长 'wag-tool-accept-status'(20) ✓（运行时行仍应用侧 UUID，seed 例外见头注 V246 先例）
-- [MC02] JSON 语义 = TEXT 列既有形态，无 cast、无 jsonb ✓  [MC03] 无索引/分区/PG 专有语法（ON CONFLICT 目标为 V236 已建唯一索引列，多库可执行性随登记表 §七 前置件复核）✓
-- [ST03-A] 无敏感列新增；data_classification_text 仅声明值（S1/S2），裁决与脱敏归 security-engine 查询链 ✓
-- [ST07/ST08] 落 ecos_ai（引擎 schema），不入 ecos_dw；不落 public ✓
-- [ST09] 无 FOREIGN KEY；本脚本只写本域表（工具契约属主 = ai-engine wagent，V236 同表）✓
-- [CHECK 词表] 12 行字面值全部落 V236 五 CHECK（status/category/side_effect/disclosure + rollback 联动）：本批 category 无 commit ⇒ ck_wagent_tool_rollback 不触发；
--       engine 值 {data,ontology,kb,cognitive,ai} ⊂ contractEngineCodesForDdlCheck() 六值词表（security 本批未用作 engine 值）✓
-- [金额] 无金额列新增；create_decision_option input_schema 的 profitDelta 等为 JSON Schema 声明（TEXT 载荷），落库金额列语义属 V241 待定项（NUMERIC 白名单届时逐列进 ST03-A 登记）✓
-- [IR02] 手动 psql 执行面（Flyway 禁用），本批只落文件、未实跑 ✓
-- [IR03] 不 DROP、不改名、不 DELETE、不 UPDATE 既有行 ✓
-- [C14.4] 与 V265 同批（toolset 成员清单两处逐字一致）；未新建 Kafka topic、未改业务代码、未新建表 ✓
