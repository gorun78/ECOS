-- ============================================================
-- V319__scenario_cognitive_mind_seed.sql
-- B40/P1-16：场景↔认知 Mind 绑定种子（V188 载体）
-- ============================================================
-- 背景（实证）：
--   workspace `pre-validate.mind` 读 legacy `ecos_scenario_mind`（F05-02/W116 已停写、0 行）；
--   停写文案明确「场景↔Mind 绑定一律改经 cognitive 注册门面写认知域场景-Mind 绑定表（V188）」。
--   认知域 `ecos_cognitive_mind` / `ecos_cognitive_scenario_mind` 均 0 行、且全仓无写路径。
--
-- 本脚本（数据种子，不含任何业务结果）：
--   为演示场景 `sc_ent2_group_month` 建 1 个认知 Mind（is_active=1）并绑定为 PRIMARY，
--   使 E1 detect 可返回真实心智；语义 = 「该场景使用哪个认知能力配置」，非结论/金额。
--
-- 幂等：mind 按 mind_key、绑表按 (scenario_id, mind_id, is_deleted) 不存在才插入。
-- 回滚：DELETE FROM ecos_cognitive.ecos_cognitive_scenario_mind WHERE id='sm_ent2_month';
--       DELETE FROM ecos_cognitive.ecos_cognitive_mind WHERE id='mind_ent2_month';
-- ============================================================

INSERT INTO ecos_cognitive.ecos_cognitive_mind
    (id, mind_key, name, description, capability_mask_json, closed_loop_bounds_json,
     status, is_active, mind_version, domain, version_no, create_by)
SELECT 'mind_ent2_month',
       'mind_entmgmt_monthly',
       '集团月度经营认知心智',
       '集团月度经营分析场景的认知能力配置（诊断/预测/仿真/策略，金额只来自确定性预测服务）',
       '["DIAGNOSE","FORECAST","SIMULATE","STRATEGY"]',
       '[]',
       'ACTIVE', 1, '1.0.0', 'entmgmt', '1', 'seed-entmgmt'
 WHERE NOT EXISTS (
     SELECT 1 FROM ecos_cognitive.ecos_cognitive_mind m
      WHERE m.mind_key = 'mind_entmgmt_monthly' AND m.is_deleted = 0);

INSERT INTO ecos_cognitive.ecos_cognitive_scenario_mind
    (id, scenario_id, mind_id, binding_role, priority, domain, version_no, create_by)
SELECT 'sm_ent2_month', 'sc_ent2_group_month',
       (SELECT m.id FROM ecos_cognitive.ecos_cognitive_mind m
         WHERE m.mind_key = 'mind_entmgmt_monthly' AND m.is_deleted = 0),
       'PRIMARY', 100, 'entmgmt', '1', 'seed-entmgmt'
 WHERE EXISTS (
       SELECT 1 FROM ecos_cognitive.ecos_cognitive_mind m
        WHERE m.mind_key = 'mind_entmgmt_monthly' AND m.is_deleted = 0)
   AND NOT EXISTS (
       SELECT 1 FROM ecos_cognitive.ecos_cognitive_scenario_mind sm
        WHERE sm.scenario_id = 'sc_ent2_group_month' AND sm.is_deleted = 0);

-- ── DDL Lint Self-audit ──────────────────────────────────
-- [IR03] 零 DROP / 零 ALTER；仅 2 行种子数据 ✓
-- [DR01] schema 限定 ecos_cognitive ✓  [ST07] 认知域 schema ✓
-- [语义] mind = 能力配置（capabilityMask/闭环边界空集），不含结论、金额、置信度 ✓
