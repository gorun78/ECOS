-- ============================================================
-- V190__cognitive_scenario_mind_binding.sql
-- 详细设计-05 E.3 / F05-02 / R-13 ① ：
--   把 E3/E4 读侧所需 mind_id / scenario_id 列回填到既有心智表上
--   （public.ecos_cognitive_belief V129 / public.ecos_cognitive_hypothesis V128）
--
-- 纪律：
--   IR03 只加不删（ALTER ... ADD COLUMN IF NOT EXISTS）
--   DR06~DR08 缺基线列不在本批修复（属 R-13 大收敛）
--   表名 `{schema}.` 限定 铁律 §四附则1
--   主键字段 VARCHAR(64) 与 V129/V128 现有主键宽度一致（禁引入新 PK 形态）
--
-- 语义：
--   mind_id     VARCHAR(36) → 指 ecos_cognitive.ecos_cognitive_mind.id（跨 schema 无 FK，应用层校验）
--   scenario_id VARCHAR(36) → 指场景 id（public.ecos_business_scenario / 未来 ecos_control.ecos_business_scenario）
--   两列可空 → 兼容存量行（无绑定的历史 belief/hypo）；读侧带 mind 参数时 WHERE mind_id=? 严格过滤
-- ============================================================

ALTER TABLE public.ecos_cognitive_belief     ADD COLUMN IF NOT EXISTS mind_id      VARCHAR(36);
ALTER TABLE public.ecos_cognitive_belief     ADD COLUMN IF NOT EXISTS scenario_id  VARCHAR(36);
ALTER TABLE public.ecos_cognitive_hypothesis ADD COLUMN IF NOT EXISTS mind_id      VARCHAR(36);
ALTER TABLE public.ecos_cognitive_hypothesis ADD COLUMN IF NOT EXISTS scenario_id  VARCHAR(36);

-- 热路径索引：E3 严格 mind 过滤 / E4 未映射行展示
CREATE INDEX IF NOT EXISTS idx_ecos_cognitive_belief_mind
    ON public.ecos_cognitive_belief (mind_id, scenario_id, is_deleted);
CREATE INDEX IF NOT EXISTS idx_ecos_cognitive_hypo_mind
    ON public.ecos_cognitive_hypothesis (mind_id, scenario_id, is_deleted);

COMMENT ON COLUMN public.ecos_cognitive_belief.mind_id      IS '所属认知模型资产 id（V187 定版；双写切换前可空，读侧带 ?mind= 参数才生效）';
COMMENT ON COLUMN public.ecos_cognitive_belief.scenario_id  IS '所属场景 id（E4 读侧场景上下文过滤；双写切换前可空）';
COMMENT ON COLUMN public.ecos_cognitive_hypothesis.mind_id  IS '所属认知模型资产 id（同 belief.mind_id，E3 读侧）';
COMMENT ON COLUMN public.ecos_cognitive_hypothesis.scenario_id IS '所属场景 id（E3 读侧）';

-- ============================================================
-- DDL Lint Self-audit
-- [DR01] schema 限定 + 小写下划线 ✓  [DR05/DR06/DR07/DR08] 既有表单批不改
-- [MC01] 主键宽度沿用 V129/V128 已有 VARCHAR(64)（本批未新增 PK）✓
-- [MC02] 无 jsonb/金额/裸 NUMERIC 列变化 ✓  [MC03] 普通索引、无 partial、无 bare cast ✓
-- [ST03-A] 无敏感列 ✓  [ST07] 落 public（既有表未归位，R-13 收敛时随表一并迁 ecos_cognitive）
-- [IR02] 手动 psql ✓  [IR03] 只加不删，两列可空不破坏存量 ✓
-- [R-13 ①] 双写切换前的过渡列 ✓  [F05-02] E1~E4 数据源建表后，本列同一批填入 ✓
-- ============================================================
