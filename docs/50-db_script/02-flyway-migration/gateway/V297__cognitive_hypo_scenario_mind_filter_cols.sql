-- V297 (#146ay 差距分析 §8.4.2 行 432「关键表字段」余项收口 / 详设 §8.4.2 承参维度口径):
--   ecos_cognitive.ecos_cognitive_hypothesis 补 E3 读链承参过滤列
--     mind_id / scenario_id（详设 §8.4.2：mind_id 可空，E3 带 mind 参时严格等值过滤；
--     scenario_id 承场景维度）。
--
-- 批准口径: /goal「完整补齐差距、不漏项、无假绿」批内 DDL 只加不删（IR03）；
--          既有列零改动。本脚本为 V296 同批承流腿：V296 落了 §8.4.2 五枚权威列
--          （subject_type/subject_id/reason/confidence/evidence_ids_json），但 E3 契约
--          （HypothesisReader 读链 WHERE is_deleted=0 AND scenario_id=? [AND mind_id=?]）
--          的过滤列在权威载体上缺位，且读腿载体 public.ecos_cognitive_hypothesis 不存在
--          （dev PG \dt 实测）⇒ 本脚本补两枚过滤列，读腿随批切指权威载体。
-- 状态: 2026-10-31 dev PG（sys_man）已实跑零错；同日幂等复跑一次终稿全量
--          （2 枚 ALTER + 2 枚 COMMENT 零错，2 枚 "already exists, skipping" NOTICE 零副作用）。
--
-- 纪律: DR01 / DR02(无新表) / DR04(无 JSON 列) / DR05(无布尔列) / DR06 / DR07 /
--       MC01 / MC02(白名单类型) / MC03(不建索引不涉时区) / ST07 / IR02(手动 psql) / IR03
--
-- ── 设计依据（docs/30-设计/ECOS详细设计说明书-从零实施版-2026-10-12.md §8.4.2）──────
-- hypothesis 条目原文：「`mind_id VARCHAR(36)` 可空，E3 带 mind 参时严格等值过滤」
--   + 承参维度（禁场景专有列 ⇒ subject_type/subject_id 已在 V296 落；本脚本补 E3
--   读链实际 WHERE 用到的两枚过滤列）。E3 场景维度（必带 scenario_id=?）同样缺位，
--   同口径补建 scenario_id。
-- 不建索引（MC03 门禁面与 V292/V296 同纪律）：E3 读链的量级（场景内假设面）与
--   uniq_ecos_cognitive_hypothesis_code 既有面已足够；索引需求出现后走独立迁移。
-- 已知连带缺断面（本批不扩域，登记台账）：ecos_cognitive.ecos_cognitive_belief 同样缺
--   scenario_id/mind_id（E2 BeliefScorer / E4 BeliefReader 的过滤列同源缺位）⇒
--   登记余项另行排期，本脚本不越权扩到 belief 表。
--
-- ── 1. hypothesis 加两枚承参过滤列（幂等形态）─────────────────────────────────────────
ALTER TABLE ecos_cognitive.ecos_cognitive_hypothesis
    ADD COLUMN IF NOT EXISTS mind_id      VARCHAR(36);
ALTER TABLE ecos_cognitive.ecos_cognitive_hypothesis
    ADD COLUMN IF NOT EXISTS scenario_id  VARCHAR(36);

-- ── 2. 注释登记 ─────────────────────────────────────────────────────────────────────────
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_hypothesis.mind_id IS 'R4 承参过滤列（详设 §8.4.2：E3 带 mind 参时严格等值过滤，可空）；V297 补建，存量 NULL=未挂 Mind';
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_hypothesis.scenario_id IS 'R4 承参过滤列（E3 场景维度 WHERE scenario_id=?）；V297 补建，存量 NULL=未挂场景';

-- ── 3. 幂等与回滚说明 ────────────────────────────────────────────────────────────────────
-- 幂等: ADD COLUMN IF NOT EXISTS，重复执行无副作用。
-- 回滚: IR03 禁 DROP 列；回退即停写新列（可空列不破坏存量读面）。

-- ── DDL Lint Self-audit ─────────────────────────────────────────────────────────────────
-- [DR01] schema 限定 + 小写下划线 ✓  [DR02] 无新表  [DR03] n/a
-- [DR04] 无 JSON 语义列  [DR05] 无布尔列  [DR06] 不触碰审计五列 ✓
-- [DR07] 存量表版本列不动  [DR08] n/a
-- [MC01] 无新表/无主键改动  [MC02] VARCHAR(36) 在白名单 ✓  [MC03] 无新建索引 ✓
-- [ST07] 落 ecos_cognitive（控制域五引擎白名单）✓  [IR02] 手动 psql（Flyway 禁用）✓
-- [IR03] 只加不删：零 DROP/零改既有列 ✓
