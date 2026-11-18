-- ============================================================
-- V327__ontology_cognitive_stale_mirror_columns.sql — 本体/认知 stale 镜像列补齐
-- 追溯: 2026-11-11 前端逐页巡检（任务 #34）实测两类 500 BadSqlGrammarException：
--   ① OntologyDao.findPropertiesByEntity → SELECT * FROM ecos_ontology_property WHERE is_deleted=0
--     → search_path 首位 ecos_control 命中 ecos_control.ecos_ontology_property（V3 时代遗留镜像，
--     无 is_deleted/status）⇒ 本体工作台实体属性面 500。
--   ② HypothesisStore.list → SELECT ... subject_type/subject_id/reason/confidence/evidence_ids_json/
--     scenario_id/mind_id FROM ecos_cognitive_hypothesis → search_path 首位 ecos_control 命中
--     ecos_control.ecos_cognitive_hypothesis（V191 落库时 search_path 未含 ecos_cognitive 而建的
--     镜像，无 V296/V297 补列）⇒ 认知资产页 /api/v1/cognitive/hypotheses 500。
--   权威读端：ecos_ontology 域主表（V200 治理方案 Phase 8 已 SET SCHEMA，dev 库 ecos_ontology
--     侧同名列宽 19，含 is_deleted）；ecos_cognitive.ecos_cognitive_hypothesis（V191/V296/V297
--     四批补列齐备，代码 SELECT 22 列全可解析）。
-- 处置: 只加不删（IR03）。本脚本仅 ADD COLUMN IF NOT EXISTS 把两处 stale 镜像补齐到读端口径，
--       禁 DROP/禁数据搬迁（存量行为治理属另批授权项）；同时 DROP 本库 public 侧零行孤儿
--       ecos_ontology_property 不属本批（DROP 撞 R9，登记待裁）。
-- 纪律: DR01~DR08 / MC01~MC03 / ST07 / IR02(手动 psql) / IR03(只加不删)
-- ============================================================

-- ── 1. ecos_control.ecos_ontology_property：补齐本体域读端列（读面 SELECT */is_deleted）──
ALTER TABLE IF EXISTS ecos_control.ecos_ontology_property
    ADD COLUMN IF NOT EXISTS is_deleted  SMALLINT    NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS status      VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN IF NOT EXISTS update_by   VARCHAR(128),
    ADD COLUMN IF NOT EXISTS unique_flag INTEGER     NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS description TEXT,
    ADD COLUMN IF NOT EXISTS create_time TIMESTAMP,
    ADD COLUMN IF NOT EXISTS update_time TIMESTAMP,
    ADD COLUMN IF NOT EXISTS create_by   VARCHAR(100),
    ADD COLUMN IF NOT EXISTS version_no  VARCHAR(20) NOT NULL DEFAULT '1',
    ADD COLUMN IF NOT EXISTS domain      VARCHAR(50) NOT NULL DEFAULT 'default';

COMMENT ON COLUMN ecos_control.ecos_ontology_property.is_deleted IS
    'V327 补齐：读端 WHERE is_deleted=0 依赖；该镜像属 V3 时代遗留，权威本体域读端在 ecos_ontology schema';

-- ── 2. ecos_control.ecos_ontology_relationship：本体关系读面（V200 权威=ecos_ontology）──
ALTER TABLE IF EXISTS ecos_control.ecos_ontology_relationship
    ADD COLUMN IF NOT EXISTS is_deleted  SMALLINT    NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS status      VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN IF NOT EXISTS update_by   VARCHAR(128),
    ADD COLUMN IF NOT EXISTS create_time TIMESTAMP,
    ADD COLUMN IF NOT EXISTS update_time TIMESTAMP,
    ADD COLUMN IF NOT EXISTS create_by   VARCHAR(100),
    ADD COLUMN IF NOT EXISTS version_no  VARCHAR(20) NOT NULL DEFAULT '1',
    ADD COLUMN IF NOT EXISTS domain      VARCHAR(50) NOT NULL DEFAULT 'default';

-- ── 3. ecos_control.ecos_ontology_entity：补差（ecos_ontology 侧 19 列全量口径）──
ALTER TABLE IF EXISTS ecos_control.ecos_ontology_entity
    ADD COLUMN IF NOT EXISTS is_deleted  SMALLINT    NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS status      VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN IF NOT EXISTS update_by   VARCHAR(128),
    ADD COLUMN IF NOT EXISTS create_time TIMESTAMP,
    ADD COLUMN IF NOT EXISTS update_time TIMESTAMP,
    ADD COLUMN IF NOT EXISTS create_by   VARCHAR(100),
    ADD COLUMN IF NOT EXISTS version_no  VARCHAR(20) NOT NULL DEFAULT '1',
    ADD COLUMN IF NOT EXISTS domain      VARCHAR(50) NOT NULL DEFAULT 'default',
    ADD COLUMN IF NOT EXISTS domain_id   VARCHAR(50),
    ADD COLUMN IF NOT EXISTS tenant_id   VARCHAR(32);

-- ── 4. ecos_control.ecos_ontology_action：动作读面（V200 权威=ecos_ontology）──
ALTER TABLE IF EXISTS ecos_control.ecos_ontology_action
    ADD COLUMN IF NOT EXISTS is_deleted  SMALLINT    NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS status      VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN IF NOT EXISTS update_by   VARCHAR(128),
    ADD COLUMN IF NOT EXISTS create_time TIMESTAMP,
    ADD COLUMN IF NOT EXISTS update_time TIMESTAMP,
    ADD COLUMN IF NOT EXISTS create_by   VARCHAR(100),
    ADD COLUMN IF NOT EXISTS version_no  VARCHAR(20) NOT NULL DEFAULT '1',
    ADD COLUMN IF NOT EXISTS domain      VARCHAR(50) NOT NULL DEFAULT 'default';

-- ── 5. ecos_control.ecos_cognitive_hypothesis：认知假设镜像补齐 V296/V297 读列 ──
--    （代码 SELECT 22 列含 subject_type/subject_id/reason/confidence/evidence_ids_json/scenario_id/mind_id）
ALTER TABLE IF EXISTS ecos_control.ecos_cognitive_hypothesis
    ADD COLUMN IF NOT EXISTS evidence_ids_json TEXT,
    ADD COLUMN IF NOT EXISTS scenario_id       VARCHAR(64),
    ADD COLUMN IF NOT EXISTS mind_id           VARCHAR(36),
    ADD COLUMN IF NOT EXISTS subject_type      VARCHAR(32),
    ADD COLUMN IF NOT EXISTS subject_id        VARCHAR(64),
    ADD COLUMN IF NOT EXISTS reason            TEXT,
    ADD COLUMN IF NOT EXISTS confidence        NUMERIC(18,4),
    ADD COLUMN IF NOT EXISTS lifecycle_state   VARCHAR(32),
    ADD COLUMN IF NOT EXISTS old_status        VARCHAR(32);

COMMENT ON COLUMN ecos_control.ecos_cognitive_hypothesis.scenario_id IS
    'V327 补齐：场景维度假设列表（V296）读列；权威实例 = ecos_cognitive.ecos_cognitive_hypothesis';

-- ── 回滚说明 ──────────────────────────────────────────────
-- 仅 ADD COLUMN IF NOT EXISTS；回滚 = 应用侧忽略新列（IR03 禁脚本内 DROP）。
-- 幂等性：全列 ADD IF NOT EXISTS ⇒ 重复执行零报错。
-- dev 实跑后把本脚本头注「状态: 未实跑」回写为「已 dev PG 实跑」（落地登记册同步）。

-- ── DDL Lint Self-audit ──────────────────────────────────────
-- [DR01] 4 张 ecos_control 表均为存量（非新增 CREATE TABLE 前缀门禁对象）✓
-- [DR04] 无 JSONB 新增（evidence_ids_json 用 TEXT，对齐 ecos_cognitive 权威侧）✓
-- [DR05] 无布尔列 ✓ [MC02] confidence 用 NUMERIC(18,4) ✓
-- [ST07] 镜像侧补列不改归属；权威读端（ecos_ontology/ecos_cognitive）形态不动 ✓
-- [IR02] 仅文件 + 手动 psql ✓ [IR03] 只加不删 ✓
