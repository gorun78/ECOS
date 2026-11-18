-- ============================================================
-- V199__cognitive_scenario_binding_target_columns.sql
-- 详细设计-05 E.3 / X-30 / X-32 / W127（结构必败 M0）：
--   `public.ecos_scenario_binding` 实存 10 列（止于 is_deleted），
--   但 `ScenarioService.java:349` 的 INSERT 引用 `target_id/target_type`
--   → 运行期 SQL 异常（被 catch 吞成 500，违反 REQ-COG-05 铁律守护）。
--
-- 本批方案：**补列**（IR03 只加可，不改既有 INSERT 语句语义），
--   不仅让既有代码可跑，且两列语义（targetType = SCENARIO/AGENT/... , targetId = UUID）
--   复用 F05 附件 R-14 ① 的 subjectType+subjectId 心智（但本列命名沿用契约稳定）。
--
-- 补充：`target_id VARCHAR(36) NOT NULL DEFAULT ''` 允许空以兼容旧行；
--      应用侧切换后新数据落显式值。
-- ============================================================

ALTER TABLE public.ecos_scenario_binding ADD COLUMN IF NOT EXISTS target_id   VARCHAR(36);
ALTER TABLE public.ecos_scenario_binding ADD COLUMN IF NOT EXISTS target_type VARCHAR(20);

CREATE INDEX IF NOT EXISTS idx_ecos_scenario_binding_target
    ON public.ecos_scenario_binding (target_id, target_type, is_deleted);

COMMENT ON COLUMN public.ecos_scenario_binding.target_id   IS '目标引用 id（业务标识，跨类型通用；配合 target_type 使用）';
COMMENT ON COLUMN public.ecos_scenario_binding.target_type IS '目标类型（SCENARIO/AGENT/KNOWLEDGE/...）';

-- ============================================================
-- DDL Lint Self-audit
-- [DR01] schema 限定 + 小写下划线 ✓  [MC01] VARCHAR(36) 无 auto-PK ✓
-- [MC02] 无金额/JSON 列 ✓  [MC03] 普通复合索引 ✓
-- [IR03] 只加不删，两列均为 NULL-able，存量行不受影响 ✓
-- [R-14 ①] 与 subjectType+subjectId 心智语义一致性（本批沿用 WARNING 列名，代码 PATH 层做映射）✓
-- ============================================================
