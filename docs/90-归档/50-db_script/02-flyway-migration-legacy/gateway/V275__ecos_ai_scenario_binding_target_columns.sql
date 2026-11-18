-- ============================================================
-- V275__ecos_ai_scenario_binding_target_columns.sql
-- 卷07 场景读面 live 收口（三项前端改造·议题3 全栈核验 2026-10-11）：
--   V147/V199 的补列脚本在本机 dev 库（ecos-postgres/sys_man）从未实跑
--   —— V200__schema_governance 已把 `ecos_scenario_binding` 移入 `ecos_ai`，
--   两条历史脚本按 public/裸名寻址已不可能命中现位表；
--   WorkspaceDataDao.listBindingsByScenario/insertBinding（V199 契约列面）
--   实跑报 `column "target_id" does not exist`（ScenarioService.fillAggregates → 500）。
--
-- 本批方案：对表实际所在 schema（ecos_ai）按 **V199 列定义** 幂等补列，
--   不 ALTER 其他任何列/约束/数据（IR03 只加不删；NULL-able 不影响存量行）。
--   列语义沿用 V199：target_id=真引用 id（VARCHAR(36)），target_type=目标类型（VARCHAR(20)）。
-- 实跑授权：PMO「授权实跑」增量迁移（同 V271 批次口径）。
-- ============================================================

ALTER TABLE ecos_ai.ecos_scenario_binding ADD COLUMN IF NOT EXISTS target_id   VARCHAR(36);
ALTER TABLE ecos_ai.ecos_scenario_binding ADD COLUMN IF NOT EXISTS target_type VARCHAR(20);

CREATE INDEX IF NOT EXISTS idx_ecos_scenario_binding_target
    ON ecos_ai.ecos_scenario_binding (target_id, target_type, is_deleted);

COMMENT ON COLUMN ecos_ai.ecos_scenario_binding.target_id   IS '目标引用 id（业务标识，跨类型通用；配合 target_type 使用。V199/X-30 补列，V275 落位 ecos_ai 幂等重放）';
COMMENT ON COLUMN ecos_ai.ecos_scenario_binding.target_type IS '目标类型（SCENARIO/AGENT/KNOWLEDGE/...；V199/X-30 补列，V275 落位 ecos_ai 幂等重放）';

-- ============================================================
-- DDL Lint Self-audit
-- [DR01] schema 限定 + 小写下划线 ✓  [MC01] VARCHAR(36) 无 auto-PK ✓
-- [MC02] 无金额/JSON 列 ✓  [MC03] 普通复合索引（IF NOT EXISTS）✓
-- [IR03] 只加不删；两列 NULL-able，存量行不受影响 ✓
-- [ST07] 控制域表落当前基线 schema（ecos_ai，V200 治理后实位），不新立 schema ✓
-- ============================================================
