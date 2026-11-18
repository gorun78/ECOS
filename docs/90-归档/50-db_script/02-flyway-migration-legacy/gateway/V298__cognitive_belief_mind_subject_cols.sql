-- V298 (#146az 台账 v1.0.224 新登记余项：belief 表承参过滤缺断面收口 / 详设 §8.4.2 列面口径):
--   ecos_cognitive.ecos_cognitive_belief 补归属维度三枚可空列
--     mind_id VARCHAR(36)  / subject_type VARCHAR(20) / subject_id VARCHAR(36)
--
-- 背景（取证结论）:
--   ① V190 把 mind_id+scenario_id 加到了 public.ecos_cognitive_belief（V129 旧载体，双写停写面）；
--   ② V191 converge 到 ecos_cognitive. 权威载体时未承 mind_id/scenario_id；
--   ③ V262 补 probability 到权威载体，头注明确"public 只停写，不再加列"；
--   ④ 详设 §8.4.2（5479 行）对 belief 的归属列明文 = subject_type/subject_id/mind_id 三枚，
--      无 scenario_id —— 场景维度经场景↔Mind 绑定表（ecos_cognitive_scenario_mind）推导；
--   ⑤ E2/E4 读链（BeliefScorer/BeliefReader）WHERE 含 scenario_id=? ⇒ 在权威载体上恒 E501。
--
-- 本脚本处置（方案 A，用户裁决 2026-10-31）:
--   只补 mind_id + subject_type + subject_id 三枚（对齐详设 §8.4.2 列面，绝不在 belief 表私设
--   scenario 列）；场景过滤改由绑表生态型子查询（mind_id IN (场景绑表)）在应用侧完成，DDL 不建。
--   列全可空 ⇒ 存量行 NULL（未挂 Mind / 未填主体），读侧以 IS NOT NULL 守护。
--
-- 批准口径: /goal「完整补齐差距、不漏项、无假绿」批内 DDL 只加不删（IR03）；既有列零改动。
-- 状态: 2026-10-31 dev PG（sys_man / ecos-postgres）实跑零错＋幂等复跑（3× ALTER 均
--       "already exists, skipping"，information_schema 回读 mind_id VARCHAR(36) /
--       subject_type VARCHAR(20) / subject_id VARCHAR(36) 在位）。
--
-- 纪律: DR01(小写下划线+schema限定) / DR02(无新表) / DR04(无JSON语义列) / DR05(无布尔列) /
--       DR06(不触审计五列) / DR07(存量表版本列不动) / MC01(无主键改动) / MC02(白名单类型) /
--       MC03(不建索引，索引需求出现后另走独立迁移) / ST07(落ecos_cognitive) / IR02(手动psql) /
--       IR03(只加不删)
--
-- ── 1. 三枚可空列（幂等形态）─────────────────────────────────────────────────────────────────
ALTER TABLE ecos_cognitive.ecos_cognitive_belief
    ADD COLUMN IF NOT EXISTS mind_id      VARCHAR(36);
ALTER TABLE ecos_cognitive.ecos_cognitive_belief
    ADD COLUMN IF NOT EXISTS subject_type VARCHAR(20);
ALTER TABLE ecos_cognitive.ecos_cognitive_belief
    ADD COLUMN IF NOT EXISTS subject_id   VARCHAR(36);

-- ── 2. 注释登记 ────────────────────────────────────────────────────────────────────────────
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_belief.mind_id IS
    'R4 归属维度：所属 Mind 聚合根 id（详设 §8.4.2；可空，存量 NULL=未挂 Mind；读侧带 mind 参时 AND mind_id=? 严格过滤）';
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_belief.subject_type IS
    'R4 归属维度：主体类型字面量（详设 §8.4.2 subject_type VARCHAR(20)；可空，存量 NULL 维持原行为）';
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_belief.subject_id IS
    'R4 归属维度：主体 id（详设 §8.4.2 subject_id VARCHAR(36)；可空）';

-- ── 3. 为什么不在 belief 表建 scenario_id（设计决策注）─────────────────────────────────────
-- 详设 §8.4.2 对 belief 的归属列面 = subject_type/subject_id/mind_id 三枚，无 scenario_id；
-- 场景维度通过 ecos_cognitive_scenario_mind（V190/V188 建）多对多关系建模，推导：
--   WHERE mind_id IN (SELECT mind_id FROM ecos_cognitive_scenario_mind
--                     WHERE scenario_id = ? AND is_deleted = 0)
-- 这与 V297（hypothesis 表）的两枚列策略不同：hypothesis 的 E3 读链直接带 scenario_id=?，
-- 而 belief 的 E4 读链（详设 §8.4.2 belief 读链入口）是经 Mind 绑定的；两者设计语义不同，
-- 不镜像，防列语义错拼。
--
-- ── 4. 幂等与回滚 ───────────────────────────────────────────────────────────────────────────
-- 幂等: ADD COLUMN IF NOT EXISTS，重复执行无副作用。
-- 回滚: IR03 禁 DROP 列；回退即停写新列（可空列不破坏存量读面）。

-- ── DDL Lint Self-audit ─────────────────────────────────────────────────────────────────────
-- [DR01] schema 限定 + 小写下划线 ✓  [DR02] 无新表  [DR03] n/a
-- [DR04] 无 JSON 语义列  [DR05] 无布尔列  [DR06] 不触碰审计五列 ✓
-- [DR07] 存量表版本列不动  [DR08] n/a
-- [MC01] 无新表/无主键改动  [MC02] VARCHAR(36/20) 在白名单 ✓  [MC03] 无新建索引 ✓
-- [ST07] 落 ecos_cognitive（控制域五引擎白名单）✓  [IR02] 手动 psql（Flyway 禁用）✓
-- [IR03] 只加不删：零 DROP/零改既有列 ✓
