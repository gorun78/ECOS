-- ============================================================================
-- V371 — Context Builder 认知/本体来源的 RLS 读策略种子（2026-11-26 用户授权）
-- ============================================================================
-- 背景（活体取证）：`POST /api/v1/cognitive/context/build` 的 COGNITIVE 腿
--   （sourceType=COGNITIVE，sourceId=cog_hyp_seed_001）被安全裁决面以
--   `RLS_NO_POLICY_MATCHED` 拒绝（403S）。`SecurityEngineContextTrimmer` 的来源→表映射
--   在 2026-11-26 已按资产前缀路由到真载体（belief/hypothesis/model），但三张表
--   与 ONTOLOGY 腿的 `ecos_ontology.ecos_ontology_entity` 都**没有**命中策略
--   ⇒ denyIfEmpty=true 下恒 DENY。
--
-- 处置口径（与 V318 / V362 同形，最小授权）：
--   * 只授 `role_admin`、`filter_expr='1=1'`（裁剪器只接受恒真谓词且 noPolicyMatched=false）；
--   * 不绑定具体 user、不放开其他角色、不改动任何既有策略行；
--   * 逐条 WHERE NOT EXISTS 幂等（MC03：不用 ON CONFLICT）。
--
-- 纪律：IR03 只加不删（纯 INSERT 无 DDL）；ST07 策略表归属 ecos_control；
--       source_kind 取 legacy_text（与既有 rls_ctx_* 行一致，渲染走 filter_expr 兜底）。
-- ============================================================================

INSERT INTO ecos_control.ecos_rls_policy
    (id, policy_name, table_name, filter_expr, role_id, user_id, priority, enabled,
     description, created_by, created_at, updated_at, resource_id,
     source_kind, version_no, is_deleted, domain)
SELECT 'rls_ctx_ontology_read_role', 'context-source-demo-read-role',
       'ecos_ontology.ecos_ontology_entity', '1=1',
       'role_admin', NULL, '10', 'true',
       'B39 演示口径：管理/分析角色可读本体实体元数据（Context Builder ONTOLOGY 源，V371）',
       'V371', NOW(), NOW(), NULL,
       'legacy_text', '1', '0', 'default'
WHERE NOT EXISTS (SELECT 1 FROM ecos_control.ecos_rls_policy WHERE id = 'rls_ctx_ontology_read_role');

INSERT INTO ecos_control.ecos_rls_policy
    (id, policy_name, table_name, filter_expr, role_id, user_id, priority, enabled,
     description, created_by, created_at, updated_at, resource_id,
     source_kind, version_no, is_deleted, domain)
SELECT 'rls_ctx_belief_read_role', 'context-source-demo-read-role',
       'ecos_cognitive.ecos_cognitive_belief', '1=1',
       'role_admin', NULL, '10', 'true',
       'B39 演示口径：管理/分析角色可读认知信念资产（Context Builder COGNITIVE 源，V371）',
       'V371', NOW(), NOW(), NULL,
       'legacy_text', '1', '0', 'default'
WHERE NOT EXISTS (SELECT 1 FROM ecos_control.ecos_rls_policy WHERE id = 'rls_ctx_belief_read_role');

INSERT INTO ecos_control.ecos_rls_policy
    (id, policy_name, table_name, filter_expr, role_id, user_id, priority, enabled,
     description, created_by, created_at, updated_at, resource_id,
     source_kind, version_no, is_deleted, domain)
SELECT 'rls_ctx_hypothesis_read_role', 'context-source-demo-read-role',
       'ecos_cognitive.ecos_cognitive_hypothesis', '1=1',
       'role_admin', NULL, '10', 'true',
       'B39 演示口径：管理/分析角色可读认知假设资产（Context Builder COGNITIVE 源，V371）',
       'V371', NOW(), NOW(), NULL,
       'legacy_text', '1', '0', 'default'
WHERE NOT EXISTS (SELECT 1 FROM ecos_control.ecos_rls_policy WHERE id = 'rls_ctx_hypothesis_read_role');

INSERT INTO ecos_control.ecos_rls_policy
    (id, policy_name, table_name, filter_expr, role_id, user_id, priority, enabled,
     description, created_by, created_at, updated_at, resource_id,
     source_kind, version_no, is_deleted, domain)
SELECT 'rls_ctx_model_read_role', 'context-source-demo-read-role',
       'ecos_cognitive.ecos_cognitive_model', '1=1',
       'role_admin', NULL, '10', 'true',
       'B39 演示口径：管理/分析角色可读认知模型资产（Context Builder COGNITIVE 源，V371）',
       'V371', NOW(), NOW(), NULL,
       'legacy_text', '1', '0', 'default'
WHERE NOT EXISTS (SELECT 1 FROM ecos_control.ecos_rls_policy WHERE id = 'rls_ctx_model_read_role');
