-- =============================================================================
-- entmgmt_demo_seed_v2_w5_knowledge.sql
-- 二期 W5 · 域3 知识工作台「定义面 + 认知资产」初始化（企业经营管控场景）
--
-- 覆盖：知识体系（导航目录树 + 标签）→ 知识条目（制度/口径/办法文档，双载体同 id）
--       → 图谱（活动套 graph_node/graph_edge，从域2 对象与域1 数据集派生）
--       → 双层导入源配置（本体层 + 数据层）→ 认知知识体系（belief/evidence/hypothesis）
--
-- 契约依据（2026-10-17 information_schema 实查 + 源码实读，非推定）：
--   * 导航读面 = kb-engine NavTaxonDao：kb_nav_category(path,level) / kb_nav_tag(tag_name) /
--     kb_nav_article_rel(node_id,scope∈{category,tag})；列由 V290 补齐（本脚本前置依赖）。
--   * 文章列表按 kb_nav_article_rel.article_id → ecos_knowledge.knowledge_article.id（NavTaxonDao:273）；
--     检索侧按 kb_nav_article_rel.article_id → 文档 id（KnowledgeRetrievalServiceImplDao 头注 §PMO-B T1）
--     ⇒ 同一篇知识用「同 id 双载体」落 knowledge_article + ecos_kb_document，两条读路径都能命中。
--   * 图谱活动套 = ecos_knowledge.graph_node/graph_edge（KnowledgeNodeMapper @Insert 实证）；
--     双形态中的 ecos_kb_graph_* 不灌（ST02 standard 档走 PG 表，禁实启 Neo4j）。
--   * 认知三表 NN 列（information_schema 实查）：
--     belief(variable_name,domain,distribution jsonb,version,is_manual_override,status)
--     evidence(evidence_code,source_type,blob jsonb,confidence,is_conflict,refuting_evidence_ids jsonb,status)
--     hypothesis(hypothesis_code,statement,evidence_ids jsonb,is_valid,status)
--     词表：evidence.status∈{ACTIVE,CONFLICTED}；belief.status 同；hypothesis 生命周期 PROPOSED|EVIDENCED|BELIEVED|REFUTED。
--   * 跨域引用全部走 JOIN 派生（对象=域2 ecos_ontology_entity，数据集=域1 td_data_resource，
--     指标=域2 ecos_metric_definition，文档=本脚本 §3），seed 后即无悬挂；末尾对账硬校验。
--
-- 禁 seed（红线，规划册 §2.2）：抽取产物 ecos_kb_extract_candidate/extraction_drafts、
--   认知结果面 ecos_cognitive_outcome_evaluation、运行态 ecos_wm_*/ecos_decision_*。
--   本脚本 sys_extraction_source 只落「源配置」，extracted_at 留 NULL 表示从未执行。
--
-- 幂等：按 create_by='seed-entmgmt' + domain='entmgmt' 命名空间先删后插，可重复执行；
--       sys_extraction_source 无 create_by 列 ⇒ 按 id 前缀 'seed-entmgmt-v2:es:%' 清理。
-- 实跑（2026-10-17 连跑两次，计数一致 ⇒ 幂等成立）：nav_category 8 / nav_tag 12 / kb_document 12 /
--       knowledge_article 12 / article_rel 30(=12 CATEGORY + 18 TAG) / graph_node 28(=12 对象 + 4 数据集 + 12 文档) /
--       graph_edge 37(=10 关系 + 4 MAPPED_TO + 23 DESCRIBES) / extraction_source 8 / belief 6 / evidence 8 / hypothesis 4，
--       8 项 DANGLING/BAD_* 全为 0。
-- 前置：V290__kb_nav_code_contract_columns.sql 已实跑（缺列则本脚本 §1/§2/§5 会失败）。
-- 跑法：docker exec -i ecos-postgres psql -U postgres -d sys_man -v ON_ERROR_STOP=1 -f - < 本文件
-- =============================================================================

BEGIN;

-- ---------- 0. 清理（子表先删） ----------
DELETE FROM ecos_cognitive.ecos_cognitive_belief      WHERE create_by='seed-entmgmt';
DELETE FROM ecos_cognitive.ecos_cognitive_hypothesis  WHERE create_by='seed-entmgmt';
DELETE FROM ecos_cognitive.ecos_cognitive_evidence    WHERE create_by='seed-entmgmt';
DELETE FROM ecos_knowledge.kb_nav_article_rel         WHERE create_by='seed-entmgmt' AND domain='entmgmt';
DELETE FROM ecos_knowledge.kb_nav_tag                 WHERE create_by='seed-entmgmt' AND domain='entmgmt';
DELETE FROM ecos_knowledge.kb_nav_category            WHERE create_by='seed-entmgmt' AND domain='entmgmt';
DELETE FROM ecos_knowledge.graph_edge                 WHERE create_by='seed-entmgmt' AND domain='entmgmt';
DELETE FROM ecos_knowledge.graph_node                 WHERE create_by='seed-entmgmt';
DELETE FROM ecos_knowledge.sys_extraction_source      WHERE id LIKE 'seed-entmgmt-v2:es:%';
DELETE FROM ecos_knowledge.knowledge_article          WHERE create_by='seed-entmgmt';
DELETE FROM ecos_knowledge.ecos_kb_document           WHERE create_by='seed-entmgmt' AND domain='entmgmt';

-- ---------- 1. 知识体系：导航目录树（4 一级 + 4 二级；V175 列与 V290 代码列同写） ----------
INSERT INTO ecos_knowledge.kb_nav_category
    (id, parent_id, name, description, sort_order, color_token, domain, trace_id, version_no, is_deleted,
     create_by, update_by, create_time, update_time, path, level)
VALUES
    (md5('seed:entmgmt-v2:navc:mgmt_system'),  NULL, '经营管理制度', '集团经营管控总纲与责任分工类知识', 1, 'blue',   'entmgmt', 'seed-entmgmt', '1', 0,
     'seed-entmgmt','seed-entmgmt', now(), now(), '/经营管理制度', 1),
    (md5('seed:entmgmt-v2:navc:caliber'),      NULL, '指标口径与核算办法', '权责发生制/收付实现制双口径与指标计算规则', 2, 'emerald', 'entmgmt', 'seed-entmgmt', '1', 0,
     'seed-entmgmt','seed-entmgmt', now(), now(), '/指标口径与核算办法', 1),
    (md5('seed:entmgmt-v2:navc:data_gov'),     NULL, '数据治理规范', '近源层装载、质量门禁与资产上架规范', 3, 'amber',  'entmgmt', 'seed-entmgmt', '1', 0,
     'seed-entmgmt','seed-entmgmt', now(), now(), '/数据治理规范', 1),
    (md5('seed:entmgmt-v2:navc:perform'),      NULL, '履约与回款规则', '阶段验收、归属分摊与回款账龄业务规则', 4, 'violet', 'entmgmt', 'seed-entmgmt', '1', 0,
     'seed-entmgmt','seed-entmgmt', now(), now(), '/履约与回款规则', 1),
    (md5('seed:entmgmt-v2:navc:caliber_acc'),  md5('seed:entmgmt-v2:navc:caliber'),  '权责发生制细则', '产值确认额与实现率的确认条件', 1, 'emerald', 'entmgmt', 'seed-entmgmt', '1', 0,
     'seed-entmgmt','seed-entmgmt', now(), now(), '/指标口径与核算办法/权责发生制细则', 2),
    (md5('seed:entmgmt-v2:navc:caliber_cash'), md5('seed:entmgmt-v2:navc:caliber'),  '收付实现制细则', '回款到账期间归属与账龄起算', 2, 'emerald', 'entmgmt', 'seed-entmgmt', '1', 0,
     'seed-entmgmt','seed-entmgmt', now(), now(), '/指标口径与核算办法/收付实现制细则', 2),
    (md5('seed:entmgmt-v2:navc:dg_raw'),       md5('seed:entmgmt-v2:navc:data_gov'), '近源层装载规范', 'CSV 台账源入湖的表名与列契约', 1, 'amber', 'entmgmt', 'seed-entmgmt', '1', 0,
     'seed-entmgmt','seed-entmgmt', now(), now(), '/数据治理规范/近源层装载规范', 2),
    (md5('seed:entmgmt-v2:navc:dg_dq'),        md5('seed:entmgmt-v2:navc:data_gov'), '数据质量规则手册', 'DQ 规则分级与门禁阈值释义', 2, 'amber', 'entmgmt', 'seed-entmgmt', '1', 0,
     'seed-entmgmt','seed-entmgmt', now(), now(), '/数据治理规范/数据质量规则手册', 2);

-- ---------- 2. 标签字典（12；name 与 tag_name 双写） ----------
INSERT INTO ecos_knowledge.kb_nav_tag
    (id, category_id, name, description, color_token, domain, trace_id, version_no, is_deleted,
     create_by, update_by, create_time, update_time, tag_name)
SELECT md5('seed:entmgmt-v2:navt:'||v.name), c.id, v.name, v.descr, v.color, 'entmgmt', 'seed-entmgmt', '1', 0,
       'seed-entmgmt','seed-entmgmt', now(), now(), v.name
  FROM (VALUES
    ('权责发生制','口径类标签','emerald',  md5('seed:entmgmt-v2:navc:caliber_acc')),
    ('收付实现制','口径类标签','emerald',  md5('seed:entmgmt-v2:navc:caliber_cash')),
    ('实现率','指标类标签','blue',     md5('seed:entmgmt-v2:navc:caliber')),
    ('经营毛利','指标类标签','blue',     md5('seed:entmgmt-v2:navc:caliber')),
    ('关账','流程类标签','amber',      md5('seed:entmgmt-v2:navc:caliber')),
    ('近源层','数据类标签','violet',   md5('seed:entmgmt-v2:navc:dg_raw')),
    ('治理层','数据类标签','violet',   md5('seed:entmgmt-v2:navc:dg_raw')),
    ('质量门禁','数据类标签','violet', md5('seed:entmgmt-v2:navc:dg_dq')),
    ('归属分摊','履约类标签','orange', md5('seed:entmgmt-v2:navc:perform')),
    ('回款账龄','履约类标签','orange', md5('seed:entmgmt-v2:navc:perform')),
    ('公共成本池','成本类标签','red',   md5('seed:entmgmt-v2:navc:caliber')),
    ('主数据','治理类标签','slate',    md5('seed:entmgmt-v2:navc:data_gov'))
  ) AS v(name, descr, color, ccode)
  JOIN ecos_knowledge.kb_nav_category c ON c.id = v.ccode;

-- ---------- 3. 知识文档（12 篇；id 与 §4 文章 id 同值 = 双载体同 id） ----------
INSERT INTO ecos_knowledge.ecos_kb_document
    (id, doc_code, title, content, doc_type, tags_json, entity_types_json, status, trace_id, domain,
     version_no, is_deleted, create_by, update_by, create_time, update_time)
SELECT md5('seed:entmgmt-v2:kbdoc:'||v.code), v.code, v.title, v.body, v.dtype,
       v.tags, v.entities, 'PUBLISHED', 'seed-entmgmt', 'entmgmt', 'v1', 0,
       'seed-entmgmt','seed-entmgmt', now(), now()
  FROM (VALUES
    ('ent2_doc_accrual_rule','权责发生制产值确认办法','阶段验收单经监理签认后，按合同基数计算实现率；实现率=确认额/合同基数，越界由规则 ent2_r_rate_range 拦截。','POLICY',
     '["权责发生制","实现率"]','["ent2_output_stage","ent_contract"]'),
    ('ent2_doc_cash_rule','收付实现制回款归属细则','回款按到账日期归属会计期间，账龄自合同节点应付日起算；跨期到账不得回填已关账期间。','POLICY',
     '["收付实现制","回款账龄"]','["ent2_collection","ent2_period"]'),
    ('ent2_doc_close_policy','期间关账管理制度','期间置为 CLOSED 后事实行不可追加或修改，由规则 ent2_r_close_immutable 与动作 ent2_a_close_period 双约束。','POLICY',
     '["关账"]','["ent2_period"]'),
    ('ent2_doc_profit_caliber','经营毛利口径说明','毛利=权责口径验收确认额-分摊后归集成本；公共成本池按归口与工时双规则二次分摊。','CALIBER',
     '["经营毛利","公共成本池"]','["ent_project","ent_cost_item"]'),
    ('ent2_doc_alloc_rule','归属分摊规则说明','项目归属比例合计应为 1，板块/部门/项目部三级归口；超限由规则 ent2_r_ratio_sum 告警。','POLICY',
     '["归属分摊"]','["ent_project","ent2_org_unit"]'),
    ('ent2_doc_fte_rule','人力投入与费率规则','单人单期 FTE 上限为 1，小时费率取年度内部核算价；工时是公共成本二次分摊的分母之一。','POLICY',
     '["主数据"]','["ent2_staff","ent2_timesheet"]'),
    ('ent2_doc_raw_load','近源层装载规范','六本 CSV 台账原样落 ecos_dw.ods_biz_*，表名必须匹配无模式限定；SINK 从不建表，须预建。','STANDARD',
     '["近源层"]','["ent_project","ent2_period"]'),
    ('ent2_doc_dq_gate','数据质量门禁手册','12 条场景 DQ 规则分 ERROR/WARN 两级，ERROR 未清零则治理层上架门禁拒绝放行。','STANDARD',
     '["质量门禁","治理层"]','["ent_cost_item","ent2_collection"]'),
    ('ent2_doc_asset_publish','数据资产上架流程','治理层表经目录登记后方可被本体映射引用；markCurated 只翻已登记行的层级。','STANDARD',
     '["治理层"]','["ent2_output_stage"]'),
    ('ent2_doc_master_data','主数据管理办法','项目、合同、客户、人员四类主数据由板块总经理提名、集团数据治理岗核准，编码在台账期即冻结。','POLICY',
     '["主数据"]','["ent_project","ent2_customer","ent2_staff"]'),
    ('ent2_doc_forecast','经营预测作业指引','预测由场景工作台 WAgent 发起，输入取治理层四表最近三期间；结果落 forecast_run 属执行面。','PLAYBOOK',
     '["实现率","经营毛利"]','["ent_project","ent2_period"]'),
    ('ent2_doc_ontology_map','本体与数据映射对照表','12 对象 ↔ 治理层四表的 25 组属性-列映射，源标识一律为 <对象码>.<属性码>。','PLAYBOOK',
     '["治理层","主数据"]','["ent_cost_item","ent2_timesheet"]')
  ) AS v(code, title, body, dtype, tags, entities);

-- ---------- 4. 知识文章载体（与 §3 同 id，供导航文章列表读路径） ----------
INSERT INTO ecos_knowledge.knowledge_article
    (id, title, content, source, domain, tags, status, created_at, updated_at, category, source_type, tags_json,
     create_time, update_time, create_by, update_by, version_no, is_deleted, trace_id)
SELECT d.id, d.title, d.content, 'entmgmt-知识初始化', d.domain, d.tags_json::jsonb, 'PUBLISHED', now(), now(),
       c.name, 'POLICY', d.tags_json, now(), now(), 'seed-entmgmt', 'seed-entmgmt', 'v1', 0, 'seed-entmgmt'
  FROM ecos_knowledge.ecos_kb_document d
  JOIN ecos_knowledge.kb_nav_category c
    ON c.id = md5('seed:entmgmt-v2:navc:'||CASE d.doc_type
              WHEN 'POLICY'   THEN CASE WHEN d.doc_code LIKE '%cash%' OR d.doc_code LIKE '%close%' OR d.doc_code LIKE '%accrual%' OR d.doc_code LIKE '%alloc%' OR d.doc_code LIKE '%fte%' THEN 'perform' ELSE 'mgmt_system' END
              WHEN 'CALIBER'  THEN 'caliber'
              WHEN 'STANDARD' THEN 'data_gov'
              ELSE 'mgmt_system' END)
 WHERE d.create_by='seed-entmgmt' AND d.domain='entmgmt';

-- ---------- 5. 文章 × 目录/标签关联（12 条 category + 18 条 tag = 30；双列同写） ----------
INSERT INTO ecos_knowledge.kb_nav_article_rel
    (id, article_id, tag_id, category_id, relation_type, domain, trace_id, version_no, is_deleted,
     create_by, update_by, create_time, update_time, node_id, scope)
SELECT md5('seed:entmgmt-v2:navrel:c:'||k.id), k.id, NULL, c.id, 'CATEGORY', 'entmgmt', 'seed-entmgmt', '1', 0,
       'seed-entmgmt','seed-entmgmt', now(), now(), c.id, 'category'
  FROM ecos_knowledge.knowledge_article k
  JOIN ecos_knowledge.kb_nav_category c
    ON c.name = k.category AND c.domain='entmgmt' AND c.create_by='seed-entmgmt'
 WHERE k.create_by='seed-entmgmt';

INSERT INTO ecos_knowledge.kb_nav_article_rel
    (id, article_id, tag_id, category_id, relation_type, domain, trace_id, version_no, is_deleted,
     create_by, update_by, create_time, update_time, node_id, scope)
SELECT md5('seed:entmgmt-v2:navrel:t:'||a.article_id||':'||a.tag_id), a.article_id, a.tag_id, NULL, 'TAG', 'entmgmt', 'seed-entmgmt', '1', 0,
       'seed-entmgmt','seed-entmgmt', now(), now(), a.tag_id, 'tag'
  FROM (SELECT DISTINCT k.id AS article_id, t.id AS tag_id
          FROM ecos_knowledge.knowledge_article k
          JOIN ecos_knowledge.ecos_kb_document d ON d.id = k.id
          CROSS JOIN LATERAL jsonb_array_elements_text(d.tags_json::jsonb) AS tg(tag_name)
          JOIN ecos_knowledge.kb_nav_tag t ON t.tag_name = tg.tag_name
         WHERE k.create_by='seed-entmgmt') a;

-- ---------- 6. 图谱节点（对象 12 + 治理层数据集 4，全部由已 seed 行派生 ⇒ 无悬挂） ----------
INSERT INTO ecos_knowledge.graph_node
    (id, label, properties, domain, created_at, node_type, description, updated_at, properties_json,
     create_time, update_time, create_by, update_by, version_no, is_deleted, trace_id)
SELECT md5('seed:entmgmt-v2:gn:obj:'||e.code), e.name,
       jsonb_build_object('entityCode', e.code, 'entityId', e.id, 'ontologyId', e.ontology_id,
                          'domainCode', coalesce(d.code,'entmgmt'), 'propertyCount',
                          (SELECT count(*) FROM ecos_ontology.ecos_ontology_property p WHERE p.entity_id=e.id)),
       'entmgmt', now(), 'ONTOLOGY_OBJECT', e.description, now(), NULL,
       now(), now(), 'seed-entmgmt','seed-entmgmt', 'v1', 0, 'seed-entmgmt'
  FROM ecos_ontology.ecos_ontology_entity e
  LEFT JOIN ecos_ontology.ecos_ontology_domain d ON d.id = e.domain_id
 WHERE e.domain='entmgmt';

INSERT INTO ecos_knowledge.graph_node
    (id, label, properties, domain, created_at, node_type, description, updated_at, properties_json,
     create_time, update_time, create_by, update_by, version_no, is_deleted, trace_id)
SELECT md5('seed:entmgmt-v2:gn:ds:'||r.resource_id), r.resource_name,
       jsonb_build_object('resourceId', r.resource_id, 'layer', r.layer, 'datasourceId', r.datasource_id,
                          'fieldCount', r.field_count, 'recordCount', r.record_count),
       'entmgmt', now(), 'CURATED_DATASET', r.description, now(), NULL,
       now(), now(), 'seed-entmgmt','seed-entmgmt', 'v1', 0, 'seed-entmgmt'
  FROM ecos_data.td_data_resource r
 WHERE r.resource_id LIKE 'res_entmgmt_cur%';

-- ---------- 7. 图谱边（关系 10 + 映射 4 + 文档描述对象若干；端点全部 JOIN 已存在节点） ----------
INSERT INTO ecos_knowledge.graph_edge
    (id, source_id, target_id, type, properties, weight, created_at, updated_at, properties_json,
     domain, create_time, update_time, create_by, update_by, version_no, is_deleted, trace_id)
SELECT md5('seed:entmgmt-v2:ge:rel:'||r.code),
       md5('seed:entmgmt-v2:gn:obj:'||se.code), md5('seed:entmgmt-v2:gn:obj:'||te.code),
       r.relationship_type, jsonb_build_object('relationshipCode', r.code, 'name', r.name), 1.0, now(), now(), NULL,
       'entmgmt', now(), now(), 'seed-entmgmt','seed-entmgmt', 'v1', 0, 'seed-entmgmt'
  FROM ecos_ontology.ecos_ontology_relationship r
  JOIN ecos_ontology.ecos_ontology_entity se ON se.id = r.source_entity_id
  JOIN ecos_ontology.ecos_ontology_entity te ON te.id = r.target_entity_id
  JOIN ecos_knowledge.graph_node sn ON sn.id = md5('seed:entmgmt-v2:gn:obj:'||se.code)
  JOIN ecos_knowledge.graph_node tn ON tn.id = md5('seed:entmgmt-v2:gn:obj:'||te.code)
 WHERE r.domain='entmgmt';

INSERT INTO ecos_knowledge.graph_edge
    (id, source_id, target_id, type, properties, weight, created_at, updated_at, properties_json,
     domain, create_time, update_time, create_by, update_by, version_no, is_deleted, trace_id)
SELECT md5('seed:entmgmt-v2:ge:map:'||m.id),
       md5('seed:entmgmt-v2:gn:obj:'||m.entity_code), md5('seed:entmgmt-v2:gn:ds:'||(m.field_mappings->>'datasetId')),
       'MAPPED_TO', jsonb_build_object('mappingId', m.id, 'pairCount',
              jsonb_array_length(m.field_mappings->'fieldMappings'), 'datasourceId', m.datasource_id), 1.0, now(), now(), NULL,
       'entmgmt', now(), now(), 'seed-entmgmt','seed-entmgmt', 'v1', 0, 'seed-entmgmt'
  FROM ecos_ontology.ecos_entity_table_mapping m
  JOIN ecos_knowledge.graph_node sn ON sn.id = md5('seed:entmgmt-v2:gn:obj:'||m.entity_code)
  JOIN ecos_knowledge.graph_node tn ON tn.id = md5('seed:entmgmt-v2:gn:ds:'||(m.field_mappings->>'datasetId'))
 WHERE m.domain='entmgmt';

-- 文档节点（须先于 DESCRIBES 边：边源端引用本批文档节点 id）
INSERT INTO ecos_knowledge.graph_node
    (id, label, properties, domain, created_at, node_type, description, updated_at, properties_json,
     create_time, update_time, create_by, update_by, version_no, is_deleted, trace_id)
SELECT md5('seed:entmgmt-v2:gn:doc:'||d.id), d.title,
       jsonb_build_object('docCode', d.doc_code, 'docType', d.doc_type, 'status', d.status),
       'entmgmt', now(), 'KB_DOCUMENT', left(d.content, 200), now(), NULL,
       now(), now(), 'seed-entmgmt','seed-entmgmt', 'v1', 0, 'seed-entmgmt'
  FROM ecos_knowledge.ecos_kb_document d WHERE d.create_by='seed-entmgmt';

INSERT INTO ecos_knowledge.graph_edge
    (id, source_id, target_id, type, properties, weight, created_at, updated_at, properties_json,
     domain, create_time, update_time, create_by, update_by, version_no, is_deleted, trace_id)
SELECT md5('seed:entmgmt-v2:ge:doc:'||d.id||':'||e.code),
       md5('seed:entmgmt-v2:gn:doc:'||d.id), md5('seed:entmgmt-v2:gn:obj:'||e.code),
       'DESCRIBES', jsonb_build_object('docCode', d.doc_code, 'title', d.title), 0.8, now(), now(), NULL,
       'entmgmt', now(), now(), 'seed-entmgmt','seed-entmgmt', 'v1', 0, 'seed-entmgmt'
  FROM ecos_knowledge.ecos_kb_document d
  CROSS JOIN LATERAL jsonb_array_elements_text(d.entity_types_json::jsonb) AS ec(code)
  JOIN ecos_ontology.ecos_ontology_entity e ON e.code = ec.code AND e.domain='entmgmt'
  JOIN ecos_knowledge.graph_node tn ON tn.id = md5('seed:entmgmt-v2:gn:obj:'||e.code)
 WHERE d.create_by='seed-entmgmt' AND d.domain='entmgmt';

-- ---------- 8. 双层导入源配置（本体层 + 数据层；只配置不执行，extracted_at 留 NULL） ----------
INSERT INTO ecos_knowledge.sys_extraction_source (id, source_type, source_id, source_title, source_excerpt, extracted_at, extractor_version)
SELECT 'seed-entmgmt-v2:es:'||md5(v.stype||':'||v.sid), v.stype, v.sid, v.stitle, v.excerpt, NULL, 'seed-config-v2'
  FROM (VALUES
    ('ONTOLOGY_DOMAIN', 'ent2_contract',   '本体域·合同履约域',   '项目-合同-客户-分包履约链对象与口径，待抽取为知识条目'),
    ('ONTOLOGY_DOMAIN', 'ent2_costfund',   '本体域·成本资金域',   '成本归集、分摊规则、资金计划与回款，待抽取为知识条目'),
    ('ONTOLOGY_DOMAIN', 'ent2_capacity',   '本体域·资源产能域',   '人员、工时与产能投入，待抽取为知识条目'),
    ('ONTOLOGY_DOMAIN', 'ent2_perf',       '本体域·经营绩效域',   '产值确认、期间关账与绩效指标，待抽取为知识条目'),
    ('DATASET',         'res_entmgmt_cur_stage',      '治理层·项目阶段产值事实', '权责发生制事实表，作为口径类知识的数据层来源'),
    ('DATASET',         'res_entmgmt_cur_cost',       '治理层·项目成本归集事实', '直接/分摊成本事实表，作为成本类知识的数据层来源'),
    ('DATASET',         'res_entmgmt_cur_resource',   '治理层·人员资源投入事实', 'FTE 与工时事实表，作为产能类知识的数据层来源'),
    ('DATASET',         'res_entmgmt_cur_attribution','治理层·项目归属分摊事实', '归属比例事实表，作为分摊类知识的数据层来源')
  ) AS v(stype, sid, stitle, excerpt);

-- ---------- 9. 认知知识体系 · 证据（8 条；source_ref 全部指向本批真实行） ----------
INSERT INTO ecos_cognitive.ecos_cognitive_evidence
    (id, evidence_code, tenant_scope, source_type, source_ref, blob, confidence, is_conflict,
     refuting_evidence_ids, effective_time, expire_time, status, create_time, update_time,
     create_by, update_by, is_deleted)
VALUES
    (md5('seed:entmgmt-v2:ev:accrual_3p'), 'ent2_ev_accrual_3p', 'tenant-a', 'DATASET', 'res_entmgmt_cur_stage',
     '{"observation":"最近三个期间综合实现率均值 0.71，标准差 0.05","metric":"accrual_revenue_amount","window":"3P"}'::jsonb,
     0.86, false, '[]'::jsonb, now() - interval '30 days', now() + interval '150 days', 'ACTIVE', now(), now(), 'seed-entmgmt','seed-entmgmt', 0),
    (md5('seed:entmgmt-v2:ev:cash_aging'), 'ent2_ev_cash_aging', 'tenant-a', 'DATASET', 'res_entmgmt_cur_attribution',
     '{"observation":"归属比例合计校验 0 违例，分摊口径自洽","rule":"ent2_r_ratio_sum"}'::jsonb,
     0.90, false, '[]'::jsonb, now() - interval '30 days', now() + interval '150 days', 'ACTIVE', now(), now(), 'seed-entmgmt','seed-entmgmt', 0),
    (md5('seed:entmgmt-v2:ev:cost_overrun'), 'ent2_ev_cost_overrun', 'tenant-a', 'DATASET', 'res_entmgmt_cur_cost',
     '{"observation":"公共成本池占比 21%，两期间抬升 3.4 个百分点","metric":"operating_profit_amount"}'::jsonb,
     0.74, false, '[]'::jsonb, now() - interval '20 days', now() + interval '160 days', 'ACTIVE', now(), now(), 'seed-entmgmt','seed-entmgmt', 0),
    (md5('seed:entmgmt-v2:ev:policy_close'), 'ent2_ev_policy_close', 'tenant-a', 'KB_DOC', md5('seed:entmgmt-v2:kbdoc:ent2_doc_close_policy'),
     '{"citation":"期间关账管理制度：CLOSED 期间事实行不可追加或修改","docCode":"ent2_doc_close_policy"}'::jsonb,
     0.95, false, '[]'::jsonb, now() - interval '90 days', now() + interval '275 days', 'ACTIVE', now(), now(), 'seed-entmgmt','seed-entmgmt', 0),
    (md5('seed:entmgmt-v2:ev:caliber_diff'), 'ent2_ev_caliber_diff', 'tenant-a', 'KB_DOC', md5('seed:entmgmt-v2:kbdoc:ent2_doc_profit_caliber'),
     '{"citation":"毛利按权责口径确认额减分摊后成本，与收付口径差额须单列","docCode":"ent2_doc_profit_caliber"}'::jsonb,
     0.88, false, '[]'::jsonb, now() - interval '60 days', now() + interval '305 days', 'ACTIVE', now(), now(), 'seed-entmgmt','seed-entmgmt', 0),
    (md5('seed:entmgmt-v2:ev:metric_cash'), 'ent2_ev_metric_cash', 'tenant-a', 'METRIC', md5('seed:entmgmt-v2:met:ent2_m_collection_sum'),
     '{"observation":"回款合计按到账期间归属，与产值确认期间错配","metric":"ent2_m_collection_sum"}'::jsonb,
     0.66, true, jsonb_build_array(md5('seed:entmgmt-v2:ev:accrual_3p')), now() - interval '15 days', now() + interval '165 days', 'CONFLICTED', now(), now(), 'seed-entmgmt','seed-entmgmt', 0),
    (md5('seed:entmgmt-v2:ev:metric_accrual'), 'ent2_ev_metric_accrual', 'tenant-a', 'METRIC', md5('seed:entmgmt-v2:met:ent2_m_stage_output_sum'),
     '{"observation":"确认额期间归属以验收签认日为准，与到账口径冲突已在证据对中标记","metric":"ent2_m_stage_output_sum"}'::jsonb,
     0.69, true, jsonb_build_array(md5('seed:entmgmt-v2:ev:metric_cash')), now() - interval '15 days', now() + interval '165 days', 'CONFLICTED', now(), now(), 'seed-entmgmt','seed-entmgmt', 0),
    (md5('seed:entmgmt-v2:ev:capacity_fte'), 'ent2_ev_capacity_fte', 'tenant-a', 'DATASET', 'res_entmgmt_cur_resource',
     '{"observation":"平均 FTE 利用率 0.83，两名项目经理跨项目分摊冲突","rule":"ent2_r_fte_range"}'::jsonb,
     0.79, false, '[]'::jsonb, now() - interval '10 days', now() + interval '170 days', 'ACTIVE', now(), now(), 'seed-entmgmt','seed-entmgmt', 0);

-- ---------- 10. 认知知识体系 · 心智（6 条；last_evidence_id 指向 §9） ----------
INSERT INTO ecos_cognitive.ecos_cognitive_belief
    (id, variable_name, tenant_scope, domain, distribution, version, snapshot_version, is_manual_override,
     override_reason, last_evidence_id, status, create_time, update_time, create_by, update_by, is_deleted,
     lifecycle_state, prob_kind, calibration_ref, model_ref, scope_ref, as_of_time)
VALUES
    (md5('seed:entmgmt-v2:bl:realization'), 'q_realization_ratio', 'tenant-a', 'entmgmt',
     '{"kind":"NORMAL","mean":0.71,"std":0.05,"p10":0.64,"p90":0.78}'::jsonb, 1, 1, false, NULL,
     md5('seed:entmgmt-v2:ev:accrual_3p'), 'ACTIVE', now(), now(), 'seed-entmgmt','seed-entmgmt', 0,
     'EVIDENCED', 'POSTERIOR', md5('seed:entmgmt-v2:met:ent2_m_realization_by_proj'), 'sc_entmgmt', 'segment:all', now()),
    (md5('seed:entmgmt-v2:bl:profit'), 'fy_operating_profit_margin', 'tenant-a', 'entmgmt',
     '{"kind":"NORMAL","mean":0.112,"std":0.021,"p10":0.085,"p90":0.139}'::jsonb, 1, 1, false, NULL,
     md5('seed:entmgmt-v2:ev:caliber_diff'), 'ACTIVE', now(), now(), 'seed-entmgmt','seed-entmgmt', 0,
     'EVIDENCED', 'POSTERIOR', md5('seed:entmgmt-v2:met:ent2_m_profit_by_segment'), 'sc_entmgmt', 'segment:all', now()),
    (md5('seed:entmgmt-v2:bl:costpool'), 'p_cost_pool_overrun', 'tenant-a', 'entmgmt',
     '{"kind":"BERNOULLI","p":0.34}'::jsonb, 1, 1, false, NULL,
     md5('seed:entmgmt-v2:ev:cost_overrun'), 'ACTIVE', now(), now(), 'seed-entmgmt','seed-entmgmt', 0,
     'EVIDENCED', 'POSTERIOR', md5('seed:entmgmt-v2:met:ent2_m_cost_to_output'), 'sc_entmgmt', 'segment:all', now()),
    (md5('seed:entmgmt-v2:bl:aging'), 'collection_aging_days', 'tenant-a', 'entmgmt',
     '{"kind":"NORMAL","mean":68,"std":14,"p10":50,"p90":86}'::jsonb, 1, 1, false, NULL,
     md5('seed:entmgmt-v2:ev:metric_cash'), 'CONFLICTED', now(), now(), 'seed-entmgmt','seed-entmgmt', 0,
     'CONTESTED', 'POSTERIOR', md5('seed:entmgmt-v2:met:ent2_m_collection_aging'), 'sc_entmgmt', 'segment:all', now()),
    (md5('seed:entmgmt-v2:bl:capacity'), 'staff_fte_utilization', 'tenant-a', 'entmgmt',
     '{"kind":"NORMAL","mean":0.83,"std":0.06,"p10":0.73,"p90":0.92}'::jsonb, 1, 1, false, NULL,
     md5('seed:entmgmt-v2:ev:capacity_fte'), 'ACTIVE', now(), now(), 'seed-entmgmt','seed-entmgmt', 0,
     'EVIDENCED', 'POSTERIOR', md5('seed:entmgmt-v2:met:ent2_m_fte_utilization'), 'sc_entmgmt', 'segment:all', now()),
    (md5('seed:entmgmt-v2:bl:closeintegrity'), 'closed_period_immutability_ok', 'tenant-a', 'entmgmt',
     '{"kind":"BERNOULLI","p":0.97}'::jsonb, 1, 1, false, NULL,
     md5('seed:entmgmt-v2:ev:policy_close'), 'ACTIVE', now(), now(), 'seed-entmgmt','seed-entmgmt', 0,
     'BELIEVED', 'POSTERIOR', NULL, 'sc_entmgmt', 'segment:all', now());

-- ---------- 11. 认知知识体系 · 假设（4 条；evidence_ids 全部指向 §9 真实行） ----------
INSERT INTO ecos_cognitive.ecos_cognitive_hypothesis
    (id, hypothesis_code, tenant_scope, statement, domain, metric_ref, evidence_ids, is_valid,
     invalid_at, invalid_reason, status, create_time, update_time, create_by, update_by, is_deleted, lifecycle_state)
VALUES
    (md5('seed:entmgmt-v2:hy:aging_up'), 'ent2_hy_aging_up', 'tenant-a',
     '若Q4回款账龄均值继续高于68天，则收付口径与权责口径的期间错配将放大板块毛利波动', 'entmgmt',
     md5('seed:entmgmt-v2:met:ent2_m_collection_aging'),
     jsonb_build_array(md5('seed:entmgmt-v2:ev:metric_cash'), md5('seed:entmgmt-v2:ev:metric_accrual')),
     true, NULL, NULL, 'EVIDENCED', now(), now(), 'seed-entmgmt','seed-entmgmt', 0, 'PROPOSED'),
    (md5('seed:entmgmt-v2:hy:pool_shift'), 'ent2_hy_pool_shift', 'tenant-a',
     '公共成本池按工时二次分摊可把板块间毛利差收敛到 2 个百分点以内', 'entmgmt',
     md5('seed:entmgmt-v2:met:ent2_m_cost_to_output'),
     jsonb_build_array(md5('seed:entmgmt-v2:ev:cost_overrun'), md5('seed:entmgmt-v2:ev:capacity_fte')),
     true, NULL, NULL, 'EVIDENCED', now(), now(), 'seed-entmgmt','seed-entmgmt', 0, 'PROPOSED'),
    (md5('seed:entmgmt-v2:hy:realization_hold'), 'ent2_hy_realization_hold', 'tenant-a',
     '综合实现率在三期间内可稳定于 0.70±0.05，前提是无新增关账后追加确认', 'entmgmt',
     md5('seed:entmgmt-v2:met:ent2_m_realization_by_proj'),
     jsonb_build_array(md5('seed:entmgmt-v2:ev:accrual_3p'), md5('seed:entmgmt-v2:ev:policy_close')),
     true, NULL, NULL, 'BELIEVED', now(), now(), 'seed-entmgmt','seed-entmgmt', 0, 'EVIDENCED'),
    (md5('seed:entmgmt-v2:hy:cash_first'), 'ent2_hy_cash_first', 'tenant-a',
     '以收付口径作为经营复盘主口径（已被双口径制度否证，保留为已否证样本）', 'entmgmt',
     md5('seed:entmgmt-v2:met:ent2_m_collection_sum'),
     jsonb_build_array(md5('seed:entmgmt-v2:ev:caliber_diff')),
     false, now() - interval '5 days', '与 ent2_doc_profit_caliber 的双口径分工制度冲突', 'REFUTED', now(), now(),
     'seed-entmgmt','seed-entmgmt', 0, 'REFUTED');

-- ---------- 12. 对账 + 悬挂硬校验（任一非 0 即视为对接失败） ----------
SELECT 'nav_category' k, count(*) v FROM ecos_knowledge.kb_nav_category WHERE domain='entmgmt' AND create_by='seed-entmgmt'
UNION ALL SELECT 'nav_tag', count(*) FROM ecos_knowledge.kb_nav_tag WHERE domain='entmgmt' AND create_by='seed-entmgmt'
UNION ALL SELECT 'kb_document', count(*) FROM ecos_knowledge.ecos_kb_document WHERE domain='entmgmt' AND create_by='seed-entmgmt'
UNION ALL SELECT 'knowledge_article', count(*) FROM ecos_knowledge.knowledge_article WHERE domain='entmgmt' AND create_by='seed-entmgmt'
UNION ALL SELECT 'article_rel', count(*) FROM ecos_knowledge.kb_nav_article_rel WHERE domain='entmgmt' AND create_by='seed-entmgmt'
UNION ALL SELECT 'graph_node', count(*) FROM ecos_knowledge.graph_node WHERE domain='entmgmt' AND create_by='seed-entmgmt'
UNION ALL SELECT 'graph_edge', count(*) FROM ecos_knowledge.graph_edge WHERE domain='entmgmt' AND create_by='seed-entmgmt'
UNION ALL SELECT 'extraction_source', count(*) FROM ecos_knowledge.sys_extraction_source WHERE id LIKE 'seed-entmgmt-v2:es:%'
UNION ALL SELECT 'cog_belief', count(*) FROM ecos_cognitive.ecos_cognitive_belief WHERE domain='entmgmt' AND create_by='seed-entmgmt'
UNION ALL SELECT 'cog_evidence', count(*) FROM ecos_cognitive.ecos_cognitive_evidence WHERE create_by='seed-entmgmt'
UNION ALL SELECT 'cog_hypothesis', count(*) FROM ecos_cognitive.ecos_cognitive_hypothesis WHERE domain='entmgmt' AND create_by='seed-entmgmt'
UNION ALL SELECT 'DANGLING_rel_node', count(*) FROM ecos_knowledge.kb_nav_article_rel r
  WHERE r.create_by='seed-entmgmt' AND r.scope='category'
    AND NOT EXISTS (SELECT 1 FROM ecos_knowledge.kb_nav_category c WHERE c.id=r.node_id)
UNION ALL SELECT 'DANGLING_rel_tag', count(*) FROM ecos_knowledge.kb_nav_article_rel r
  WHERE r.create_by='seed-entmgmt' AND r.scope='tag'
    AND NOT EXISTS (SELECT 1 FROM ecos_knowledge.kb_nav_tag t WHERE t.id=r.node_id)
UNION ALL SELECT 'DANGLING_rel_article', count(*) FROM ecos_knowledge.kb_nav_article_rel r
  WHERE r.create_by='seed-entmgmt' AND NOT EXISTS (SELECT 1 FROM ecos_knowledge.knowledge_article a WHERE a.id=r.article_id)
UNION ALL SELECT 'DANGLING_edge_endpoint', count(*) FROM ecos_knowledge.graph_edge e
  WHERE e.create_by='seed-entmgmt'
    AND (NOT EXISTS (SELECT 1 FROM ecos_knowledge.graph_node n WHERE n.id=e.source_id)
      OR NOT EXISTS (SELECT 1 FROM ecos_knowledge.graph_node n WHERE n.id=e.target_id))
UNION ALL SELECT 'DANGLING_belief_evidence', count(*) FROM ecos_cognitive.ecos_cognitive_belief b
  WHERE b.create_by='seed-entmgmt' AND b.last_evidence_id IS NOT NULL
    AND NOT EXISTS (SELECT 1 FROM ecos_cognitive.ecos_cognitive_evidence ev WHERE ev.id=b.last_evidence_id)
UNION ALL SELECT 'DANGLING_hypo_evidence', count(*) FROM ecos_cognitive.ecos_cognitive_hypothesis h,
  jsonb_array_elements_text(h.evidence_ids) eid
  WHERE h.create_by='seed-entmgmt' AND NOT EXISTS (SELECT 1 FROM ecos_cognitive.ecos_cognitive_evidence ev WHERE ev.id = eid)
UNION ALL SELECT 'DANGLING_ev_source', count(*) FROM ecos_cognitive.ecos_cognitive_evidence ev
  WHERE ev.create_by='seed-entmgmt' AND (
       (ev.source_type='DATASET' AND NOT EXISTS (SELECT 1 FROM ecos_data.td_data_resource r WHERE r.resource_id=ev.source_ref))
    OR (ev.source_type='KB_DOC'   AND NOT EXISTS (SELECT 1 FROM ecos_knowledge.ecos_kb_document d WHERE d.id=ev.source_ref))
    OR (ev.source_type='METRIC'   AND NOT EXISTS (SELECT 1 FROM ecos_ontology.ecos_metric_definition m WHERE m.id=ev.source_ref)))
UNION ALL SELECT 'DANGLING_bl_calib_metric', count(*) FROM ecos_cognitive.ecos_cognitive_belief b
  WHERE b.create_by='seed-entmgmt' AND b.calibration_ref IS NOT NULL
    AND NOT EXISTS (SELECT 1 FROM ecos_ontology.ecos_metric_definition m WHERE m.id=b.calibration_ref)
UNION ALL SELECT 'BAD_ev_status_vocab', count(*) FROM ecos_cognitive.ecos_cognitive_evidence
  WHERE create_by='seed-entmgmt' AND status NOT IN ('ACTIVE','CONFLICTED')
UNION ALL SELECT 'BAD_hy_status_vocab', count(*) FROM ecos_cognitive.ecos_cognitive_hypothesis
  WHERE create_by='seed-entmgmt' AND status NOT IN ('PROPOSED','EVIDENCED','BELIEVED','REFUTED');

COMMIT;
