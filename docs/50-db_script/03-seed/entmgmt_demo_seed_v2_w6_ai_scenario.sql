-- =============================================================================
-- entmgmt_demo_seed_v2_w6_ai_scenario.sql
-- 二期 W6 · 域4 AI 工作台（智能体 + 工具 + 编排）+ 域5 场景工作台（六类绑定 + 必需三边 + 蓝图）
--
-- 契约依据（2026-10-17 information_schema 实查 + DAO 源码实读，非推定）：
--   * 智能体活表 = ecos_ai.ecos_agent_registry（AgentRegistryRepository.java:14-39 唯一读写者）。
--     幽灵表 ecos_ai.agent_registry / agent_definition 全仓零引用，且缺 created_at/updated_at 列
--     ⇒ 活读 SQL 不可用 ⇒ 本脚本不写它们。
--   * status 大小写是硬契约：findActive() 谓词字面量 status = 'ACTIVE'（:26，PG varchar 区分大小写）。
--     存量 builtin-*-agent 六行 status='active'（小写，由 ai-engine config/DataInitializer.java 写入）
--     ⇒ 对 ACTIVE 读面恒不可见，登记为缺陷 D-W6-1，本脚本不改他人工作面数据。
--   * 工具注册 = ecos_ai.ecos_ai_tool_registry（AiToolRegistryDao.java:25,35 只读）；
--     backend_kind 唯一允许值 'REST'（ScenarioToolDefinition.java:35 / F06-11 要点2：禁 SQL 直查他引擎表，
--     表里 backend_kind='SQL' 的行会被 ScenarioToolRegistry:21 拒绝并告警）；
--     CHECK ck_atr_operation ∈ {read,write}、ck_atr_status ∈ {ACTIVE,DISABLED,DRAFT}。
--   * 技能活表 = ecos_ai.ecos_skill（SkillDao.java:43-107 非限定名，search_path 第 4 位命中 ecos_ai）；
--     W-Agent 技能面 = ecos_ai.ecos_wagent_skill（WAgentSkillDao.java:23-59，findByStatus 走 idx_wagent_skill_status）。
--   * W-Agent 工具契约 = ecos_wagent_tool_contract，在档 V266 已带 core 契约（ON CONFLICT (name,version,is_deleted)
--     DO NOTHING ⇒ 本批只补经营管控场景专属 4 条，tenant_id='default' 沿用 V266 头注口径）；
--     词表 WAgentEnums：category ∈ read|compute|candidate|commit|notify，side_effect ∈ none|draft|production，
--     disclosure ∈ always_loaded|searchable，status 生命周期 draft|registered|validated|active|deprecated|retired。
--   * 场景活表三件套 = ecos_ai.ecos_business_scenario（父）+ ecos_ai.ecos_scenario_binding（六类绑定）
--     + ecos_control.ecos_scenario_binding_link（连边），全部由 WorkspaceDataDao 承流
--     （:147/:238/:271/:352-399/:413-427）。
--     幽灵面 = ecos_control.ecos_scenario_definition + ecos_scenario_asset_binding：ScenarioMapper.java 全仓
--     零注入点，且 XML 用 #{schema}. 作标识符（JDBC 不可绑定标识符 ⇒ 该 SQL 本身不可执行）、
--     又拿 is_island = TRUE 比 smallint ⇒ 登记为缺陷 D-W6-2，本脚本不写。
--     同理 ecos_scenario_binding_edge（14 列，_link 的未使用孪生）与 ScenarioBindingMapper
--     （接口零注入）不写。
--   * 绑定词表 6 类 = BindingType.java:14-19；连边词表 5 类 = ScenarioBindingLinkService.java:34,116
--     （MAPPING/EXTRACTION/COGNITION/GOVERN/EXPOSE，注：从零实施版 §:7185 写的 EXPOSURE/REFERENCE 与
--     库内 CHECK 不一致 ⇒ 按代码/库为准，订正见规划册）。
--   * 必需三边方向 = FixedDenominatorEdgePolicy.java:23-26：
--     MAPPING DATASET→OBJECT_TYPE、EXTRACTION OBJECT_TYPE→KNOWLEDGE_BASE、COGNITION KNOWLEDGE_BASE→AI_AGENT；
--     可选边按 :42 注释：GOVERN SECURITY_POLICY→AI_AGENT（治理引用）、EXPOSE AI_AGENT→INTERFACE（链尾出口）。
--     MAPPING 必须带非空 source_contract（ScenarioBindingLinkService:118-121，§0.6.2.2）⇒ 本脚本落
--     'ent2://field-mappings/<对象码>'，指向域2 ecos_entity_table_mapping 真实映射行。
--   * 蓝图 = ecos_control.ecos_scenario_blueprint（ScenarioBlueprintDao.java:33-164，V271 已在档并已实跑，
--     0 行）；状态词表单源 BlueprintStatus.java:11-44（11 主链态 + 5 异常态）。本批只落 BLUEPRINT_READY
--     （草案可评审、尚未执行）⇒ actor/trace_id/release_* 一律 NULL，不伪造执行痕迹。
--
-- 跨域引用全部子查询派生（不写死 id）：target_ref 列 NOT NULL ⇒ 任一解析失败即整体回滚 = 内置悬挂闸。
--   DATASET→域1 td_data_resource(layer=CURATED) / OBJECT_TYPE→域2 ecos_ontology_entity.code /
--   KNOWLEDGE_BASE→域3 ecos_kb_document.doc_code / AI_AGENT→本脚本 §1 / SECURITY_POLICY→域0 td_abac_policy /
--   INTERFACE→域2 段 ecos_interface_ref.id；department→域0 td_organization.org_id。
--
-- 禁 seed（红线）：ecos_ai.ecos_mission / ecos_mission_task / ecos_wagent_run* / _goal / _mission /
--   _decision* / _candidate* / _evidence / agent_execution* / sys_agent_session / ecos_agent_alert =
--   执行结果面；ecos_cognitive.ecos_business_scenario|ecos_scenario_binding|ecos_scenario_binding_link =
--   错放镜像，只停写（CognitiveIronLawsTest IR03）。
--
-- 幂等：按本批命名空间先删后插 —— ag_ent2_% / seed-entmgmt（工具·技能·契约·蓝图）/ sc_ent2_% /
--   sb_ent2_% / bsl_ent2_%；一期 sc_entmgmt 及其 sb_*/bsl_* 存量行保留不动（仅为其补 2 类绑定 + 2 条边）。
-- 前置：V265（wagent sys_config 字典）+ V266（wagent 工具契约 core）已实跑；缺则 §5 语义不完整但不报错。
-- 跑法：docker exec -i ecos-postgres psql -U postgres -d sys_man -v ON_ERROR_STOP=1 -f - < 本文件
-- =============================================================================

BEGIN;

-- ---------- 0. 清理（子表先删；一期 sc_entmgmt 面不动） ----------
DELETE FROM ecos_control.ecos_scenario_binding_link WHERE id LIKE 'bsl\_ent2\_%';
DELETE FROM ecos_control.ecos_scenario_blueprint    WHERE create_by='seed-entmgmt';
DELETE FROM ecos_ai.ecos_scenario_binding            WHERE id LIKE 'sb\_ent2\_%';
DELETE FROM ecos_ai.ecos_business_scenario           WHERE id LIKE 'sc\_ent2\_%';
DELETE FROM ecos_ai.ecos_wagent_tool_contract        WHERE create_by='seed-entmgmt';
DELETE FROM ecos_ai.ecos_wagent_skill                WHERE create_by='seed-entmgmt';
DELETE FROM ecos_ai.ecos_ai_tool_registry            WHERE create_by='seed-entmgmt';
DELETE FROM ecos_ai.ecos_skill                       WHERE created_by='seed-entmgmt';
DELETE FROM ecos_ai.ecos_agent_registry              WHERE id LIKE 'ag\_ent2\_%';

-- ---------- 1. 系统默认智能体 6（数据/本体/知识/认知/W-Agent 编排/安全） ----------
-- status 必须大写 ACTIVE（findActive 谓词）；capability/metadata 是 jsonb；role NOT NULL。
INSERT INTO ecos_ai.ecos_agent_registry (id, name, role, capability, status, endpoint, metadata, created_at, updated_at)
VALUES
 ('ag_ent2_data', '经营数据治理智能体', 'data-governance',
  '["台账装载","DQ 门禁","资产上架","血缘登记"]'::jsonb, 'ACTIVE', '/api/v1/datanet/datasource',
  '{"domain":"entmgmt","tier":"engine","tools":["ent2_ledger_probe","ent2_dq_gate_run","ent2_asset_publish"],"bind_datasets":4}'::jsonb, NOW(), NOW()),
 ('ag_ent2_ontology', '本体建模智能体', 'ontology-modeling',
  '["对象识别","关系抽取","口径核验","映射校验"]'::jsonb, 'ACTIVE', '/api/v1/ecos/ontologies',
  '{"domain":"entmgmt","tier":"engine","tools":["ent2_object_read","ent2_metric_caliber_check","ent2_mapping_validate"],"bind_objects":12}'::jsonb, NOW(), NOW()),
 ('ag_ent2_knowledge', '知识构建智能体', 'knowledge-build',
  '["制度抽取","图谱装配","文档检索","RAG 召回"]'::jsonb, 'ACTIVE', '/api/v1/knowledge/graph/query',
  '{"domain":"entmgmt","tier":"engine","tools":["ent2_kb_doc_search","ent2_graph_query"],"bind_documents":12}'::jsonb, NOW(), NOW()),
 ('ag_ent2_cognitive', '经营认知智能体', 'cognition-analysis',
  '["假设检验","证据消解","指标计算","预测回测"]'::jsonb, 'ACTIVE', '/api/v1/cognitive/beliefs',
  '{"domain":"entmgmt","tier":"engine","tools":["ent2_belief_list","ent2_forecast_submit"],"bind_metrics":12}'::jsonb, NOW(), NOW()),
 ('ag_ent2_wagent', '场景编排智能体（W Agent）', 'wagent-orchestration',
  '["目标理解","缺口分析","计划编排","步骤执行","人工闸门"]'::jsonb, 'ACTIVE', '/api/v1/wagent/goals',
  '{"domain":"entmgmt","tier":"scene","blueprint_states":["DRAFT","DISCOVERING","GAP_ANALYSIS","BLUEPRINT_READY"],"contracts":"V266 core + 本批 4"}'::jsonb, NOW(), NOW()),
 ('ag_ent2_security', '安全审计智能体', 'security-audit',
  '["策略裁决","脱敏核验","审计只读","越界告警"]'::jsonb, 'ACTIVE', '/api/v1/security/policies',
  '{"domain":"entmgmt","tier":"crosscut","readonly":true,"bind_policies":6}'::jsonb, NOW(), NOW());

-- ---------- 2. AI 工具注册 8（backend_kind 一律 REST；operation/status 受 CHECK 约束） ----------
INSERT INTO ecos_ai.ecos_ai_tool_registry
 (id, tool_key, name, description, operation, args_schema_json, backend_kind, backend_endpoint,
  permission_hint, opa_action_type, resource_scope_projection, timeout_ms, status, domain, version_no,
  is_deleted, create_by, update_by, create_time)
VALUES
 ('tool_ent2_ledger_probe','ent2_ledger_probe','台账源探测','列出场台账 CSV 与行数，供装载前校验','read',
  '{"type":"object","required":["datasourceId"],"properties":{"datasourceId":{"type":"string"}}}','REST',
  '/api/v1/datanet/datasource/{datasourceId}/probe','data:datasource:read','read','datasource',5000,'ACTIVE','entmgmt','1',0,'seed-entmgmt','seed-entmgmt',NOW()),
 ('tool_ent2_dq_gate_run','ent2_dq_gate_run','DQ 门禁批次执行','按调度触发质量规则批次','write',
  '{"type":"object","required":["scheduleId"],"properties":{"scheduleId":{"type":"string"}}}','REST',
  '/api/v1/ecos/dq/schedules/{scheduleId}/run','data:dq:run','write','dq_schedule',30000,'ACTIVE','entmgmt','1',0,'seed-entmgmt','seed-entmgmt',NOW()),
 ('tool_ent2_asset_publish','ent2_asset_publish','数据资产上架','登记资源并翻 layer 至 CURATED','write',
  '{"type":"object","required":["resourceId"],"properties":{"resourceId":{"type":"string"},"layer":{"type":"string"}}}','REST',
  '/api/v1/datanet/catalog/register','data:asset:write','write','data_resource',10000,'ACTIVE','entmgmt','1',0,'seed-entmgmt','seed-entmgmt',NOW()),
 ('tool_ent2_object_read','ent2_object_read','本体对象读取','按域列出对象与属性','read',
  '{"type":"object","required":["domainCode"],"properties":{"domainCode":{"type":"string"}}}','REST',
  '/api/v1/ecos/entities','ontology:entity:read','read','ontology_domain',5000,'ACTIVE','entmgmt','1',0,'seed-entmgmt','seed-entmgmt',NOW()),
 ('tool_ent2_metric_caliber_check','ent2_metric_caliber_check','指标口径核验','取当前发布口径与版本','read',
  '{"type":"object","required":["metricCode"],"properties":{"metricCode":{"type":"string"}}}','REST',
  '/api/v1/ontology/calibers/{code}/current','ontology:caliber:read','read','metric',3000,'ACTIVE','entmgmt','1',0,'seed-entmgmt','seed-entmgmt',NOW()),
 ('tool_ent2_kb_doc_search','ent2_kb_doc_search','知识文档检索','按导航目录/标签检索经营制度文档','read',
  '{"type":"object","required":["keyword"],"properties":{"keyword":{"type":"string"},"scope":{"type":"string"}}}','REST',
  '/api/v1/datanet/catalog','knowledge:doc:read','read','kb_nav_category',5000,'ACTIVE','entmgmt','1',0,'seed-entmgmt','seed-entmgmt',NOW()),
 ('tool_ent2_graph_query','ent2_graph_query','经营图谱查询','对象-文档-数据集三跳子图','read',
  '{"type":"object","required":["seedNode"],"properties":{"seedNode":{"type":"string"},"hops":{"type":"integer"}}}','REST',
  '/api/v1/knowledge/graph/query','knowledge:graph:read','read','graph_node',8000,'ACTIVE','entmgmt','1',0,'seed-entmgmt','seed-entmgmt',NOW()),
 ('tool_ent2_forecast_submit','ent2_forecast_submit','经营预测提交','向场景提交预测运行（只建定义，不伪造结果）','write',
  '{"type":"object","required":["scenarioId"],"properties":{"scenarioId":{"type":"string"},"horizon":{"type":"string"}}}','REST',
  '/api/v1/workspace/scenarios/{scenarioId}/forecast-runs/submit','workspace:forecast:run','write','scenario',60000,'ACTIVE','entmgmt','1',0,'seed-entmgmt','seed-entmgmt',NOW());

-- ---------- 3. 技能 4（SkillDao 活读 ecos_ai.ecos_skill；id 走序列） ----------
INSERT INTO ecos_ai.ecos_skill (name, description, version, enabled, category, package_info, created_by, domain, version_no, is_deleted)
VALUES
 ('集团月度经营分析', '串联台账→口径→图谱→认知的月度经营分析技能包', '1.0.0', true, 'business-analysis',
  '{"steps":["ent2_ledger_probe","ent2_dq_gate_run","ent2_metric_caliber_check","ent2_forecast_submit"],"agents":["ag_ent2_data","ag_ent2_cognitive"]}','seed-entmgmt','entmgmt','1',0),
 ('板块经营对标', '板块间毛利/实现率对标与差异归因', '1.0.0', true, 'business-benchmark',
  '{"steps":["ent2_object_read","ent2_graph_query"],"metrics":["ent2_m_profit_by_segment","ent2_m_realization_by_proj"]}','seed-entmgmt','entmgmt','1',0),
 ('项目部资金风险预警', '回款账龄、逾期占比与成本产值比预警', '1.0.0', true, 'risk-warning',
  '{"metrics":["ent2_m_collection_aging","ent2_m_overdue_ratio","ent2_m_cost_to_output"]}','seed-entmgmt','entmgmt','1',0),
 ('经营口径一致性校验', '权责制与收付制双口径冲突检测', '1.0.0', true, 'caliber-governance',
  '{"metrics":["accrual_revenue_amount","cash_collection_amount"],"docs":["ent2_doc_profit_caliber"]}','seed-entmgmt','entmgmt','1',0);

-- ---------- 4. W-Agent 技能 6（tenant_id='default' 沿用 V266 口径；content 走 Git 故 ref 留 NULL） ----------
INSERT INTO ecos_ai.ecos_wagent_skill
 (id, tenant_id, org_id, name, version, summary, content_git_ref, content_version, bound_playbooks_json,
  owner, status, domain, version_no, create_by, update_by, is_deleted)
VALUES
 ('wag-skill-ent2-profit',   'default', NULL, 'ent2_profit_analysis',   '1.0.0', '经营毛利分析：口径→事实→派生',   NULL, '1',
  '["ent2_doc_profit_caliber","ent2_doc_forecast"]'::jsonb::text, 'seed-entmgmt', 'active', 'entmgmt', '1', 'seed-entmgmt','seed-entmgmt',0),
 ('wag-skill-ent2-cash',     'default', NULL, 'ent2_cash_risk_scan',    '1.0.0', '资金风险扫描：账龄→逾期→计划',   NULL, '1',
  '["ent2_doc_cash_rule"]'::jsonb::text, 'seed-entmgmt', 'active', 'entmgmt', '1', 'seed-entmgmt','seed-entmgmt',0),
 ('wag-skill-ent2-output',   'default', NULL, 'ent2_output_realization','1.0.0', '产值实现率核算：阶段确认→回款',   NULL, '1',
  '["ent2_doc_accrual_rule"]'::jsonb::text, 'seed-entmgmt', 'active', 'entmgmt', '1', 'seed-entmgmt','seed-entmgmt',0),
 ('wag-skill-ent2-cost',     'default', NULL, 'ent2_cost_allocation',   '1.0.0', '成本归集与分摊',                   NULL, '1',
  '["ent2_doc_alloc_rule"]'::jsonb::text, 'seed-entmgmt', 'active', 'entmgmt', '1', 'seed-entmgmt','seed-entmgmt',0),
 ('wag-skill-ent2-dq',       'default', NULL, 'ent2_dq_gate_check',     '1.0.0', '数据质量门禁核验',                 NULL, '1',
  '["ent2_doc_dq_gate"]'::jsonb::text, 'seed-entmgmt', 'active', 'entmgmt', '1', 'seed-entmgmt','seed-entmgmt',0),
 ('wag-skill-ent2-close',    'default', NULL, 'ent2_period_close_read', '1.0.0', '期间关账口径读数',                 NULL, '1',
  '["ent2_doc_close_policy"]'::jsonb::text, 'seed-entmgmt', 'active', 'entmgmt', '1', 'seed-entmgmt','seed-entmgmt',0);

-- ---------- 5. W-Agent 场景专属工具契约 4（core 六条由 V266 在档，本批只补经营管控台账面） ----------
INSERT INTO ecos_ai.ecos_wagent_tool_contract
 (id, tenant_id, org_id, name, version, engine, category, side_effect, min_level, required_permission,
  input_schema_json, output_schema_json, timeout_ms, retry_text, is_idempotent, cost_class, is_evidence_output,
  data_classification_text, rollback_plan_text, endpoint_kind, endpoint_url, endpoint_method, owner, sla_text,
  status, deprecated_by, toolset, summary, disclosure, capability_deps_text, domain, version_no, create_by)
VALUES
 ('wag-tool-ent2-ledger', 'default', NULL, 'read_entmgmt_ledger', '1.0.0', 'data', 'read', 'none', 1, 'data:datasource:read',
  '{"type":"object","required":["datasourceId"],"properties":{"datasourceId":{"type":"string"},"ledger":{"type":"string","enum":["contract","output","cost","collection","project","timesheet"]}}}',
  '{"type":"object","properties":{"rows":{"type":"array"},"rowCount":{"type":"integer"},"asOf":{"type":"string"}}}',
  8000, '读幂等，退避重试 ≤2 次；security 不可用 = DENY', 1, 'low', 1,
  'S2 经营台账', '无（纯读）', 'rest', '/api/v1/datanet/datasource', 'GET',
  'datanet DataSourceController list', '设计目标 P95≤1000ms（未实测）',
  'active', NULL, 'entmgmt', '读取六张经营台账 CSV 逻辑源（域1 台账源 ds_entmgmt_ledger）', 'always_loaded',
  'security-engine(policy-evaluate);data-engine(datasource)', 'entmgmt', '1', 'seed-entmgmt'),
 ('wag-tool-ent2-dqgate', 'default', NULL, 'run_entmgmt_dq_gate', '1.0.0', 'data', 'compute', 'draft', 2, 'data:dq:run',
  '{"type":"object","required":["scheduleId"],"properties":{"scheduleId":{"type":"string"}}}',
  '{"type":"object","properties":{"passed":{"type":"boolean"},"issueCount":{"type":"integer"},"score":{"type":"number"}}}',
  30000, '重跑生成新批次，旧批次保留只读；失败转缺口（F10-09 type=fetch）', 0, 'mid', 1,
  'S2 质量结果', '作废=新批次覆盖读数，历史批次不删（IR03）', 'rest', '/api/v1/ecos/dq/schedules/dqsch_entmgmt_dw/run', 'POST',
  'data-engine DqScheduleController run', '设计目标 P95≤10000ms（未实测）',
  'active', NULL, 'entmgmt', '执行经营管控 DW 质量门禁调度（13 条规则直挂）', 'searchable',
  'dq_schedule(dqsch_entmgmt_dw);dq_rule x13', 'entmgmt', '1', 'seed-entmgmt'),
 ('wag-tool-ent2-onto-obj', 'default', NULL, 'read_entmgmt_ontology_objects', '1.0.0', 'ontology', 'read', 'none', 1, 'ontology:entity:read',
  '{"type":"object","required":["domainCode"],"properties":{"domainCode":{"type":"string"}}}',
  '{"type":"object","properties":{"entities":{"type":"array"},"attributes":{"type":"array"}}}',
  5000, '读幂等，退避重试 ≤2 次', 1, 'low', 1,
  'S1 本体定义', '无（纯读）', 'rest', '/api/v1/ecos/entities', 'GET',
  'ontology-engine EntityController list', '设计目标 P95≤500ms（未实测）',
  'active', NULL, 'entmgmt', '读取经营管控五域 12 对象 58 属性（域2）', 'always_loaded',
  'ecos_ontology_domain x5;ecos_ontology_entity x12', 'entmgmt', '1', 'seed-entmgmt'),
 ('wag-tool-ent2-blueprint', 'default', NULL, 'commit_scenario_blueprint', '1.0.0', 'wagent', 'commit', 'production', 3, 'workspace:blueprint:write',
  '{"type":"object","required":["scenarioId","version","contentJson"],"properties":{"scenarioId":{"type":"string"},"version":{"type":"integer"},"contentJson":{"type":"string"}}}',
  '{"type":"object","properties":{"blueprintId":{"type":"string"},"status":{"type":"string"},"artifactDigest":{"type":"string"}}}',
  15000, '同 (scenario_id,version) 幂等跳过；未审批禁提交（approval_required=1 前置）', 1, 'high', 1,
  'S3 场景蓝图', '新版本置旧版本 DEPRECATED，禁物理删（IR03）', 'rest', '/api/v1/workspace/scenarios/{scenarioId}/blueprints', 'POST',
  'workspace ScenarioBlueprintService commit', '设计目标 P95≤5000ms（未实测）',
  'registered', NULL, 'entmgmt', '提交场景编蓝图（状态机唯一持有者 ScenarioBlueprintStateMachine，非法迁移 409）', 'searchable',
  'ecos_scenario_blueprint(V271);BlueprintStatus x16', 'entmgmt', '1', 'seed-entmgmt');

-- ---------- 6. 业务场景 3（一期 sc_entmgmt 保留；department 落域0 真实组织行） ----------
INSERT INTO ecos_ai.ecos_business_scenario
 (id, name, description, business_goal, department, priority, status, budget,
  safety_index_target, actual_safety_index, metrics, create_by, update_by, is_deleted)
VALUES
 ('sc_ent2_group_month', '集团月度经营分析（2026 年度）',
  '面向集团经营决策层的月度经营分析场景：台账→质量→口径→图谱→认知全链读数',
  '每月 5 日前完成集团与四板块经营读数闭环，形成毛利/实现率/回款三类结论',
  'org_ent2_group', 'HIGH', 'ACTIVE', 1200000, 0.95, NULL,
  '["ent2_m_profit_by_segment","ent2_m_realization_by_proj","ent2_m_collection_sum","ent2_m_cost_to_output"]'::jsonb,
  'seed-entmgmt','seed-entmgmt',0),
 ('sc_ent2_segment_bench', '板块经营对标分析',
  '基础设施/数字建造/能源环保/地产四板块横向对标，识别口径差异与结构性差距',
  '季度末输出板块毛利与实现率对标榜，差异归因到对象级属性',
  'org_ent2_infra', 'MEDIUM', 'ACTIVE', 600000, 0.90, NULL,
  '["ent2_m_profit_by_segment","ent2_m_cost_to_output","ent2_m_overdue_ratio"]'::jsonb,
  'seed-entmgmt','seed-entmgmt',0),
 ('sc_ent2_proj_risk', '项目部经营与资金风险预警',
  '项目部层回款账龄、逾期占比与成本产值比预警，跨板块隔离由 ABAC 承载',
  '周度扫描在建项目部，账龄超阈或逾期占比超阈即触发预警与动作建议',
  'org_ent2_energy_p1', 'HIGH', 'ACTIVE', 300000, 0.92, NULL,
  '["ent2_m_collection_aging","ent2_m_overdue_ratio","ent2_m_fte_utilization"]'::jsonb,
  'seed-entmgmt','seed-entmgmt',0);

-- ---------- 7. 六类绑定（本批 3 场景 × 6 = 18 行 + 一期 sc_entmgmt 补 SECURITY_POLICY/INTERFACE 2 行） ----------
-- target_ref NOT NULL + 子查询解析 ⇒ 目标不存在则整事务回滚（内置悬挂闸）。
INSERT INTO ecos_ai.ecos_scenario_binding
 (id, scenario_id, binding_type, target_ref, target_id, target_type, remark, create_by, update_by, is_deleted)
SELECT v.id, v.scenario_id, v.btype, r.ref, r.ref, v.btype,
       v.remark, 'seed-entmgmt', 'seed-entmgmt', 0
  FROM (VALUES
    -- 集团月度经营分析
    ('sb_ent2_gm_ds','sc_ent2_group_month','DATASET','0','集团经营台账治理层资产'),
    ('sb_ent2_gm_obj','sc_ent2_group_month','OBJECT_TYPE','ent_project','经营项目对象'),
    ('sb_ent2_gm_kb','sc_ent2_group_month','KNOWLEDGE_BASE','ent2_doc_profit_caliber','板块经营毛利口径'),
    ('sb_ent2_gm_ag','sc_ent2_group_month','AI_AGENT','ag_ent2_cognitive','经营认知智能体'),
    ('sb_ent2_gm_sp','sc_ent2_group_month','SECURITY_POLICY','abac_ent2_exec_read','集团决策层读全量经营集'),
    ('sb_ent2_gm_if','sc_ent2_group_month','INTERFACE','if_ent2_forecast_list','经营预测列表接口'),
    -- 板块经营对标
    ('sb_ent2_sb_ds','sc_ent2_segment_bench','DATASET','1','板块对标治理层资产'),
    ('sb_ent2_sb_obj','sc_ent2_segment_bench','OBJECT_TYPE','ent_cost_item','成本科子对象'),
    ('sb_ent2_sb_kb','sc_ent2_segment_bench','KNOWLEDGE_BASE','ent2_doc_alloc_rule','成本分摊办法'),
    ('sb_ent2_sb_ag','sc_ent2_segment_bench','AI_AGENT','ag_ent2_ontology','本体建模智能体'),
    ('sb_ent2_sb_sp','sc_ent2_segment_bench','SECURITY_POLICY','abac_ent2_segment_own','板块负责人读本板块'),
    ('sb_ent2_sb_if','sc_ent2_segment_bench','INTERFACE','if_ent2_mapping_validate','数据映射校验接口'),
    -- 项目部风险预警
    ('sb_ent2_pr_ds','sc_ent2_proj_risk','DATASET','2','项目部回款治理层资产'),
    ('sb_ent2_pr_obj','sc_ent2_proj_risk','OBJECT_TYPE','ent2_collection','回款单对象'),
    ('sb_ent2_pr_kb','sc_ent2_proj_risk','KNOWLEDGE_BASE','ent2_doc_cash_rule','资金回款认定规则'),
    ('sb_ent2_pr_ag','sc_ent2_proj_risk','AI_AGENT','ag_ent2_wagent','场景编排智能体'),
    ('sb_ent2_pr_sp','sc_ent2_proj_risk','SECURITY_POLICY','abac_ent2_proj_own_org','项目部主管读本项目部'),
    ('sb_ent2_pr_if','sc_ent2_proj_risk','INTERFACE','if_ent2_dq_run','DQ 批次执行接口'),
    -- 一期场景补齐到六类（只新增 2 类，存量 4 类不动）
    ('sb_ent2_em_sp','sc_entmgmt','SECURITY_POLICY','abac_ent2_groupfin_amount','集团财务读金额与回款'),
    ('sb_ent2_em_if','sc_entmgmt','INTERFACE','if_ent2_graph_query','知识图谱查询接口')
  ) AS v(id, scenario_id, btype, tcode, remark)
 CROSS JOIN LATERAL (
   SELECT CASE v.btype
     WHEN 'DATASET' THEN (SELECT d.resource_id FROM ecos_data.td_data_resource d
                           WHERE d.layer='CURATED' AND d.is_deleted=0
                           ORDER BY d.resource_id LIMIT 1 OFFSET v.tcode::integer)
     WHEN 'OBJECT_TYPE' THEN (SELECT e.id FROM ecos_ontology.ecos_ontology_entity e
                           WHERE e.code = v.tcode ORDER BY e.create_time LIMIT 1)
     WHEN 'KNOWLEDGE_BASE' THEN (SELECT k.id FROM ecos_knowledge.ecos_kb_document k
                           WHERE k.doc_code = v.tcode)
     WHEN 'AI_AGENT' THEN (SELECT a.id FROM ecos_ai.ecos_agent_registry a WHERE a.id = v.tcode)
     WHEN 'SECURITY_POLICY' THEN (SELECT p.policy_id FROM ecos_control.td_abac_policy p WHERE p.policy_id = v.tcode)
     WHEN 'INTERFACE' THEN (SELECT f.id FROM ecos_control.ecos_interface_ref f WHERE f.id = v.tcode)
   END AS ref
 ) r;
-- 注：此处刻意不加 "WHERE r.ref IS NOT NULL" —— target_ref NOT NULL 会让任一解析失败即整事务回滚，
--     即头注声明的内置悬挂闸（加了 WHERE 就把硬闸降级成静默丢行）。

-- ---------- 8. 连边（必需三边 ×4 场景 + GOVERN/EXPOSE 可选边；MAPPING 必带 source_contract） ----------
INSERT INTO ecos_control.ecos_scenario_binding_link
 (id, scenario_id, source_binding_id, target_binding_id, link_type, source_contract, remark,
  domain, version_no, create_by, update_by, is_deleted)
SELECT 'bsl_ent2_'||lower(e.ltype)||'_'||e.scenario_id, e.scenario_id, sb.id, tb.id, e.ltype,
       CASE WHEN e.ltype = 'MAPPING'
            THEN 'ent2://field-mappings/'||(SELECT ee.code FROM ecos_ontology.ecos_ontology_entity ee
                                              JOIN ecos_ai.ecos_scenario_binding mb ON mb.id = tb.id
                                             WHERE ee.id = mb.target_ref LIMIT 1)
       END,
       e.remark, 'entmgmt', '1', 'seed-entmgmt', 'seed-entmgmt', 0
  FROM (VALUES
    ('sc_ent2_group_month','MAPPING','DATASET','OBJECT_TYPE','数据集→对象字段映射'),
    ('sc_ent2_group_month','EXTRACTION','OBJECT_TYPE','KNOWLEDGE_BASE','对象→知识抽取来源'),
    ('sc_ent2_group_month','COGNITION','KNOWLEDGE_BASE','AI_AGENT','知识→智能体认知输入'),
    ('sc_ent2_group_month','GOVERN','SECURITY_POLICY','AI_AGENT','安全策略治理智能体'),
    ('sc_ent2_group_month','EXPOSE','AI_AGENT','INTERFACE','智能体对外出口'),
    ('sc_ent2_segment_bench','MAPPING','DATASET','OBJECT_TYPE','数据集→对象字段映射'),
    ('sc_ent2_segment_bench','EXTRACTION','OBJECT_TYPE','KNOWLEDGE_BASE','对象→知识抽取来源'),
    ('sc_ent2_segment_bench','COGNITION','KNOWLEDGE_BASE','AI_AGENT','知识→智能体认知输入'),
    ('sc_ent2_segment_bench','GOVERN','SECURITY_POLICY','AI_AGENT','安全策略治理智能体'),
    ('sc_ent2_segment_bench','EXPOSE','AI_AGENT','INTERFACE','智能体对外出口'),
    ('sc_ent2_proj_risk','MAPPING','DATASET','OBJECT_TYPE','数据集→对象字段映射'),
    ('sc_ent2_proj_risk','EXTRACTION','OBJECT_TYPE','KNOWLEDGE_BASE','对象→知识抽取来源'),
    ('sc_ent2_proj_risk','COGNITION','KNOWLEDGE_BASE','AI_AGENT','知识→智能体认知输入'),
    ('sc_ent2_proj_risk','GOVERN','SECURITY_POLICY','AI_AGENT','安全策略治理智能体'),
    ('sc_ent2_proj_risk','EXPOSE','AI_AGENT','INTERFACE','智能体对外出口'),
    -- 一期场景三边已在档（bsl_*）不重复；只补两条例可选边
    ('sc_entmgmt','GOVERN','SECURITY_POLICY','AI_AGENT','安全策略治理智能体'),
    ('sc_entmgmt','EXPOSE','AI_AGENT','INTERFACE','智能体对外出口')
  ) AS e(scenario_id, ltype, stype, ttype, remark)
  JOIN ecos_ai.ecos_scenario_binding sb ON sb.scenario_id = e.scenario_id AND sb.binding_type = e.stype
  JOIN ecos_ai.ecos_scenario_binding tb ON tb.scenario_id = e.scenario_id AND tb.binding_type = e.ttype
 WHERE NOT (e.ltype = 'MAPPING' AND sb.target_ref IS NULL);

-- ---------- 9. 场景编蓝图 4（BLUEPRINT_READY：草案可评审，未执行 ⇒ actor/trace/release_* 全 NULL） ----------
INSERT INTO ecos_control.ecos_scenario_blueprint
 (id, tenant_id, org_id, scenario_id, version, status, goal_statement, goal_success_text,
  scope_json_text, dependencies_text, capabilities_text, gaps_text, risks_text, tests_text,
  content_json, approval_required, domain, version_no, create_by, update_by, is_deleted)
SELECT 'bp_ent2_'||s.id, 'default', s.department, s.id, 1, 'BLUEPRINT_READY',
       s.business_goal,
       '达成所需：六类绑定齐备 + 必需三边连通 + 蓝图评审通过后方可 RELEASED',
       '{"bindingTypes":["DATASET","OBJECT_TYPE","KNOWLEDGE_BASE","AI_AGENT","SECURITY_POLICY","INTERFACE"],"requiredEdges":["MAPPING","EXTRACTION","COGNITION"]}'::text,
       '域1 治理层资产 / 域2 本体对象与口径 / 域3 知识文档 / 域0 ABAC 策略 / 域2 接口登记',
       (SELECT string_agg(a.name, ' → ' ORDER BY x.ord)
          FROM (VALUES (1,'ag_ent2_data'),(2,'ag_ent2_ontology'),(3,'ag_ent2_knowledge'),
                       (4,'ag_ent2_cognitive'),(5,'ag_ent2_wagent'),(6,'ag_ent2_security')) x(ord, aid)
          JOIN ecos_ai.ecos_agent_registry a ON a.id = x.aid),
       '缺口：builtin 智能体 status 小写不可见（D-W6-1）；护栏策略表缺（D-W6-3）',
       '风险：跨板块隔离依赖 ABAC 生效；OPA 无策略时 fail-closed 拒绝执行',
       '验收：六类绑定 6/6、必需三边 3/3、孤岛绑定 0、悬挂 0',
       '{"pipeline":["read_entmgmt_ledger","run_entmgmt_dq_gate","read_entmgmt_ontology_objects","ent2_metric_caliber_check","ent2_kb_doc_search","ent2_graph_query","ent2_forecast_submit"],"agents":["ag_ent2_data","ag_ent2_ontology","ag_ent2_knowledge","ag_ent2_cognitive","ag_ent2_wagent","ag_ent2_security"],"humanGateBeforeCommit":true}'::text,
       1, 'entmgmt', '1', 'seed-entmgmt', 'seed-entmgmt', 0
  FROM ecos_ai.ecos_business_scenario s
 WHERE s.id IN ('sc_ent2_group_month','sc_ent2_segment_bench','sc_ent2_proj_risk','sc_entmgmt');

-- ---------- 10. 对账 + 悬挂硬校验（任一非 0 即数据不合格） ----------
SELECT '01 agent_registry(本批)' AS item, count(*)::text AS val FROM ecos_ai.ecos_agent_registry WHERE id LIKE 'ag\_ent2\_%'
UNION ALL SELECT '02 agent ACTIVE 读面可见(本批)', count(*)::text FROM ecos_ai.ecos_agent_registry WHERE id LIKE 'ag\_ent2\_%' AND status='ACTIVE'
UNION ALL SELECT '03 ai_tool_registry(REST)', count(*)::text FROM ecos_ai.ecos_ai_tool_registry WHERE create_by='seed-entmgmt' AND backend_kind='REST'
UNION ALL SELECT '04 skill(ecos_skill)', count(*)::text FROM ecos_ai.ecos_skill WHERE created_by='seed-entmgmt'
UNION ALL SELECT '05 wagent_skill', count(*)::text FROM ecos_ai.ecos_wagent_skill WHERE create_by='seed-entmgmt'
UNION ALL SELECT '06 wagent_tool_contract(本批)', count(*)::text FROM ecos_ai.ecos_wagent_tool_contract WHERE create_by='seed-entmgmt'
UNION ALL SELECT '07 wagent_tool_contract(V266 累计)', count(*)::text FROM ecos_ai.ecos_wagent_tool_contract
UNION ALL SELECT '08 business_scenario(本批)', count(*)::text FROM ecos_ai.ecos_business_scenario WHERE id LIKE 'sc\_ent2\_%'
UNION ALL SELECT '09 binding(本批)', count(*)::text FROM ecos_ai.ecos_scenario_binding WHERE id LIKE 'sb\_ent2\_%'
UNION ALL SELECT '10 binding(全库)', count(*)::text FROM ecos_ai.ecos_scenario_binding WHERE is_deleted=0
UNION ALL SELECT '11 link(本批)', count(*)::text FROM ecos_control.ecos_scenario_binding_link WHERE id LIKE 'bsl\_ent2\_%'
UNION ALL SELECT '12 blueprint', count(*)::text FROM ecos_control.ecos_scenario_blueprint WHERE create_by='seed-entmgmt'
-- 悬挂闸
UNION ALL SELECT '13 DANGLING binding(DATASET)', count(*)::text FROM ecos_ai.ecos_scenario_binding b WHERE b.id LIKE 'sb\_ent2\_%' AND b.binding_type='DATASET'
   AND NOT EXISTS (SELECT 1 FROM ecos_data.td_data_resource d WHERE d.resource_id=b.target_ref AND d.is_deleted=0)
UNION ALL SELECT '14 DANGLING binding(OBJECT_TYPE)', count(*)::text FROM ecos_ai.ecos_scenario_binding b WHERE b.id LIKE 'sb\_ent2\_%' AND b.binding_type='OBJECT_TYPE'
   AND NOT EXISTS (SELECT 1 FROM ecos_ontology.ecos_ontology_entity e WHERE e.id=b.target_ref)
UNION ALL SELECT '15 DANGLING binding(KNOWLEDGE_BASE)', count(*)::text FROM ecos_ai.ecos_scenario_binding b WHERE b.id LIKE 'sb\_ent2\_%' AND b.binding_type='KNOWLEDGE_BASE'
   AND NOT EXISTS (SELECT 1 FROM ecos_knowledge.ecos_kb_document k WHERE k.id=b.target_ref)
UNION ALL SELECT '16 DANGLING binding(AI_AGENT)', count(*)::text FROM ecos_ai.ecos_scenario_binding b WHERE b.id LIKE 'sb\_ent2\_%' AND b.binding_type='AI_AGENT'
   AND NOT EXISTS (SELECT 1 FROM ecos_ai.ecos_agent_registry a WHERE a.id=b.target_ref)
UNION ALL SELECT '17 DANGLING binding(SECURITY_POLICY)', count(*)::text FROM ecos_ai.ecos_scenario_binding b WHERE b.id LIKE 'sb\_ent2\_%' AND b.binding_type='SECURITY_POLICY'
   AND NOT EXISTS (SELECT 1 FROM ecos_control.td_abac_policy p WHERE p.policy_id=b.target_ref)
UNION ALL SELECT '18 DANGLING binding(INTERFACE)', count(*)::text FROM ecos_ai.ecos_scenario_binding b WHERE b.id LIKE 'sb\_ent2\_%' AND b.binding_type='INTERFACE'
   AND NOT EXISTS (SELECT 1 FROM ecos_control.ecos_interface_ref f WHERE f.id=b.target_ref)
UNION ALL SELECT '19 DANGLING link 两端', count(*)::text FROM ecos_control.ecos_scenario_binding_link l
   WHERE l.id LIKE 'bsl\_ent2\_%'
     AND NOT EXISTS (SELECT 1 FROM ecos_ai.ecos_scenario_binding b WHERE b.id=l.source_binding_id)
     AND NOT EXISTS (SELECT 1 FROM ecos_ai.ecos_scenario_binding b WHERE b.id=l.target_binding_id)
UNION ALL SELECT '20 场景缺任一必需边', count(*)::text FROM (
   SELECT s.id FROM ecos_ai.ecos_business_scenario s
    WHERE s.id IN ('sc_ent2_group_month','sc_ent2_segment_bench','sc_ent2_proj_risk','sc_entmgmt')
      AND NOT (SELECT count(DISTINCT l.link_type) FROM ecos_control.ecos_scenario_binding_link l
                WHERE l.scenario_id=s.id AND l.link_type IN ('MAPPING','EXTRACTION','COGNITION')) = 3) x
UNION ALL SELECT '21 六类绑定未齐备场景数', count(*)::text FROM (
   SELECT s.id FROM ecos_ai.ecos_business_scenario s
    WHERE s.id IN ('sc_ent2_group_month','sc_ent2_segment_bench','sc_ent2_proj_risk','sc_entmgmt')
      AND (SELECT count(DISTINCT b.binding_type) FROM ecos_ai.ecos_scenario_binding b
            WHERE b.scenario_id=s.id AND b.is_deleted=0) < 6) x
UNION ALL SELECT '22 孤岛绑定数(无入无边)', count(*)::text FROM ecos_ai.ecos_scenario_binding b
   WHERE b.scenario_id IN ('sc_ent2_group_month','sc_ent2_segment_bench','sc_ent2_proj_risk')
     AND NOT EXISTS (SELECT 1 FROM ecos_control.ecos_scenario_binding_link l
                      WHERE l.source_binding_id=b.id OR l.target_binding_id=b.id)
UNION ALL SELECT '23 MAPPING 边缺 source_contract', count(*)::text FROM ecos_control.ecos_scenario_binding_link l
   WHERE l.id LIKE 'bsl\_ent2\_%' AND l.link_type='MAPPING' AND (l.source_contract IS NULL OR l.source_contract LIKE 'placeholder-%')
UNION ALL SELECT '24 blueprint 场景悬挂', count(*)::text FROM ecos_control.ecos_scenario_blueprint bp
   WHERE bp.create_by='seed-entmgmt' AND NOT EXISTS (SELECT 1 FROM ecos_ai.ecos_business_scenario s WHERE s.id=bp.scenario_id)
UNION ALL SELECT '25 场景 department 悬挂', count(*)::text FROM ecos_ai.ecos_business_scenario s
   WHERE s.id LIKE 'sc\_ent2\_%' AND NOT EXISTS (SELECT 1 FROM ecos_control.td_organization o WHERE o.org_id=s.department)
UNION ALL SELECT '26 一期存量 AI_AGENT 目标悬挂(登记非本批)', count(*)::text FROM ecos_ai.ecos_scenario_binding b
   WHERE b.id='sb_198cf5d3da99' AND NOT EXISTS (SELECT 1 FROM ecos_ai.ecos_agent_registry a WHERE a.id=b.target_ref)
ORDER BY 1;

-- 目标解析审计（供册回写：绑定实际落到了哪些真实 id）
SELECT b.binding_type||' | '||b.scenario_id||' -> '||b.target_ref AS resolved
  FROM ecos_ai.ecos_scenario_binding b WHERE b.id LIKE 'sb\_ent2\_%' ORDER BY b.scenario_id, b.binding_type;

COMMIT;
