-- ============================================================
-- V341__ontology_action_definition_migrate_legacy.sql — 旧动作表种子迁入 action_definition
-- 追溯: 产品级逐功能验证 D125（2026-11-17 活体取证）
--   旧表 ecos_control.ecos_ontology_action  = 3 行（act001/act002/act003，2026-11-10 种子）
--   新表 ecos_ontology.action_definition    = 0 行
--   OntologyDao 的 action 族此前是 return-0 空桩 ⇒ 接口 200 但库中零行（写入假成功）
--
-- 背景:
--   V200 把「实体级动作表」合并重塑为 action_definition(id, code, name, type, config jsonb,
--   created_at)。旧表未被 DROP（Flyway 禁用，脚本只落文件；库中是历史手建态），
--   于是新旧并存：旧表有数据无人读，新表被读但为空。
--
-- 处置（D125 选项①）:
--   1. 把 3 行旧种子按字段映射迁入 action_definition：
--        entity_id    → config 里的 entityId 键
--        name         → name
--        action_type  → type
--        rule_json    → config 里的 ruleJson 键
--        strategy     → config 里的 strategy 键
--        status       → config 里的 status 键
--      code 旧表无对应列，按 id 生成（act001 → ACT_001），保持可追溯。
--   2. 旧表**不删除**（schema 只加不删纪律），仅停止写入 —— 写面已由
--      OntologyDao/R2 改指 action_definition（本批代码变更）。
--
-- 幂等: WHERE NOT EXISTS 按 id 去重 —— 重放零副作用，已迁过的行不覆盖。
-- 纪律: DR01(全 schema 限定) / DR04(config 为既有 jsonb 列，非新增) / IR03(不删表不删列)
-- 号段: 接 V340；落地后下一号 V342（须同批核对 MigrationVersionUniquenessTest 与三枚 SCRIPT_FLOOR）。
-- ============================================================

INSERT INTO ecos_ontology.action_definition (id, code, name, type, config, created_at)
SELECT
    a.id,
    upper(replace(a.id, 'act', 'ACT_'))                      AS code,
    a.name,
    COALESCE(a.action_type, 'CUSTOM')                        AS type,
    jsonb_strip_nulls(jsonb_build_object(
        'entityId',   a.entity_id,
        'ruleJson',   a.rule_json,
        'strategy',   a.strategy,
        'status',     a.status
    ))                                                       AS config,
    a.created_at
FROM ecos_control.ecos_ontology_action a
WHERE a.is_deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM ecos_ontology.action_definition d WHERE d.id = a.id
  );

-- ── 校验（人工执行时可用，自动化可忽略）─────────────────────
-- SELECT count(*) FROM ecos_ontology.action_definition;   -- 期望 3
-- SELECT id, code, name, type, config FROM ecos_ontology.action_definition ORDER BY id;

-- ── 回滚 ────────────────────────────────────────────────────
-- DELETE FROM ecos_ontology.action_definition WHERE id IN ('act001','act002','act003');

-- ── DDL Lint Self-audit ──────────────────────────────────────
-- [DR01] 全程 schema 限定 ✓  [DR02/03] 无新表 ✓  [DR04] config 是既有 jsonb 列 ✓
-- [DR05] 无布尔列新增 ✓  [DR06~08] 不涉基线列增删 ✓  [MC01~03] 不涉主键/金额 ✓
-- [ST07] ecos_ontology 为金I引擎 schema ✓  [IR03] 不删表不删列 ✓
