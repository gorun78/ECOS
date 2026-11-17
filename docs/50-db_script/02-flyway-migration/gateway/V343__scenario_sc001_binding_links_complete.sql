-- ============================================================
-- V343__scenario_sc001_binding_links_complete.sql — 补齐 sc001 的绑定连线
-- 追溯: D120（2026-11-17 活体验证 + 用户裁决「只补连线让可达性通过」）
--
-- 现象（2026-11-17 POST /api/v1/workspace/scenarios/sc001/pre-validate）：
--   {"name":"bindings_reachability","pass":false,"detail":"存在 4 个孤岛绑定，禁止激活"}
--
-- 根因：sc001 有 8 条绑定，但 ecos_scenario_binding_link 只有 2 行：
--   bsl001  sb001(DATASET)      → sb003(OBJECT_TYPE)    MAPPING     ✓ 满足必需边①
--   bsl002  sb007(AI_AGENT)     → sb006(KNOWLEDGE_BASE) COGNITION   ✗ 方向与必需边③相反
-- 于是 sb002 / sb004 / sb005 / sb008 四条绑定 degree=0（IslandBindingGuard 判为孤岛），
-- 且必需边②（OBJECT_TYPE→KNOWLEDGE_BASE）与③（KNOWLEDGE_BASE→AI_AGENT）都没有正向边。
--
-- 必需边口径 = FixedDenominatorEdgePolicy 固定分母 3：
--   MAPPING     DATASET        → OBJECT_TYPE
--   EXTRACTION  OBJECT_TYPE    → KNOWLEDGE_BASE
--   COGNITION   KNOWLEDGE_BASE → AI_AGENT
-- 孤岛口径 = IslandBindingGuard：无任何连边（degree=0）的绑定。
--
-- 处置：补 4 条边，把全部孤岛接入主链，并补出两条必需边的正向行。
--   bsl003  sb002(DATASET)   → sb004(OBJECT_TYPE)  MAPPING     （接入 sb002、sb004）
--   bsl004  sb003(OBJECT_TYPE)→ sb006(KB)          EXTRACTION  （满足必需边②；接入 sb003）
--   bsl005  sb006(KB)        → sb007(AI_AGENT)     COGNITION   （满足必需边③正向）
--   bsl006  sb005(OBJECT_TYPE)→ sb006(KB)          EXTRACTION  （接入 sb005）
--   bsl007  sb007(AI_AGENT)  → sb008(SECURITY_POLICY) REFERENCE （接入 sb008，链尾出口）
--
-- 不改：bsl002 的反向边保留（历史行不动，Schema 只加不删；新正向边 bsl005 与之并存）。
--       四条绑定的 target_ref 不做变更 —— 用户裁决明确「只补连线」，
--       aircraft / sop_maintenance_v2 等目标是否真实存在不参与本判据
--       （EdgeContractProbe 只看 sourceBound/targetBound + 连边行存在性）。
--
-- 幂等: WHERE NOT EXISTS 按 id 去重（沿用 V341 同批形态，避开 PG 专有 upsert 语法）。
-- 纪律: DR01 全 schema 限定 / IR03 只 INSERT / 本表七枚治理列齐全（不受 Dr 门禁约束）。
-- 号段: 接 V342；落地后下一号 V344（须同批核对 MigrationVersionUniquenessTest 与三枚 SCRIPT_FLOOR）。
-- ============================================================

INSERT INTO ecos_control.ecos_scenario_binding_link
    (id, scenario_id, source_binding_id, target_binding_id, link_type, source_contract,
     remark, create_by, update_by, domain)
SELECT 'bsl003', 'sc001', 'sb002', 'sb004', 'MAPPING', 'sc001-dataset-to-object',
       'D120 补齐：sb002(DATASET) 原为孤岛，接入 OBJECT_TYPE 链', 'V343', 'V343', 'default'
 WHERE NOT EXISTS (SELECT 1 FROM ecos_control.ecos_scenario_binding_link WHERE id = 'bsl003');

INSERT INTO ecos_control.ecos_scenario_binding_link
    (id, scenario_id, source_binding_id, target_binding_id, link_type, source_contract,
     remark, create_by, update_by, domain)
SELECT 'bsl004', 'sc001', 'sb003', 'sb006', 'EXTRACTION', 'sc001-object-to-kb',
       'D120 补齐：必需边② OBJECT_TYPE→KNOWLEDGE_BASE 原先无正向行', 'V343', 'V343', 'default'
 WHERE NOT EXISTS (SELECT 1 FROM ecos_control.ecos_scenario_binding_link WHERE id = 'bsl004');

INSERT INTO ecos_control.ecos_scenario_binding_link
    (id, scenario_id, source_binding_id, target_binding_id, link_type, source_contract,
     remark, create_by, update_by, domain)
SELECT 'bsl005', 'sc001', 'sb006', 'sb007', 'COGNITION', 'sc001-kb-to-agent',
       'D120 补齐：必需边③正向行（bsl002 是 AI_AGENT→KB 的反向历史行，保留不动）', 'V343', 'V343', 'default'
 WHERE NOT EXISTS (SELECT 1 FROM ecos_control.ecos_scenario_binding_link WHERE id = 'bsl005');

INSERT INTO ecos_control.ecos_scenario_binding_link
    (id, scenario_id, source_binding_id, target_binding_id, link_type, source_contract,
     remark, create_by, update_by, domain)
SELECT 'bsl006', 'sc001', 'sb005', 'sb006', 'EXTRACTION', 'sc001-pilot-object-to-kb',
       'D120 补齐：sb005(OBJECT_TYPE) 原为孤岛，接入 KNOWLEDGE_BASE', 'V343', 'V343', 'default'
 WHERE NOT EXISTS (SELECT 1 FROM ecos_control.ecos_scenario_binding_link WHERE id = 'bsl006');

INSERT INTO ecos_control.ecos_scenario_binding_link
    (id, scenario_id, source_binding_id, target_binding_id, link_type, source_contract,
     remark, create_by, update_by, domain)
SELECT 'bsl007', 'sc001', 'sb007', 'sb008', 'GOVERN', 'sc001-agent-to-policy',
       'D120 补齐：sb008(SECURITY_POLICY) 原为孤岛，以 GOVERN 挂为链尾（link_type 受 CHECK 约束，仅 MAPPING/EXTRACTION/COGNITION/GOVERN/EXPOSE 合法）', 'V343', 'V343', 'default'
 WHERE NOT EXISTS (SELECT 1 FROM ecos_control.ecos_scenario_binding_link WHERE id = 'bsl007');

-- 回滚：DELETE FROM ecos_control.ecos_scenario_binding_link WHERE create_by = 'V343';

-- DDL Lint Self-audit
-- [DR01] 全 schema 限定 ✓ [DR02/03] 无新表 ✓ [DR04] 无 JSONB ✓ [DR05] 无布尔列 ✓
-- [MC01~03] 不涉主键生成/金额 ✓ [ST07] ecos_control 为控制域主 schema ✓
-- [IR03] 只 INSERT 种子，不删列不删表 ✓
