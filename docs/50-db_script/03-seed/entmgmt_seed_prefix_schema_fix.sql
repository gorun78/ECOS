-- =============================================================================
-- entmgmt_seed_prefix_schema_fix.sql — 种子重放前置：ecos_control 侧 stale 镜像补列
-- 日期：2026-11-11（前端巡检任务 #36）
--
-- 背景：重建库后 td_* / 场景 / 智能体等表由裸名 DDL 建在 ecos_control（search_path 首位），
--       而 V241/V247/V108/V296 等列增量迁移只对 public/ecos_data/ecos_cognitive 侧生效，
--       ecos_control 侧从未收到这些列 ⇒ 种子重放与运行代码读面会 BadSqlGrammar。
-- 性质：一次性前置脚本（不占迁移 V 号；按 V327 同法，IR03 只加不删，幂等可重复执行）。
-- 用法：docker exec -i ecos-postgres psql -U postgres -d sys_man -v ON_ERROR_STOP=1 < 本文件
-- =============================================================================

\set ON_ERROR_STOP on

-- ── 1. td_datasource：V241(is_default/remark) + V247(password_enc/credential_encrypted) ──
ALTER TABLE IF EXISTS ecos_control.td_datasource
    ADD COLUMN IF NOT EXISTS is_default           SMALLINT    NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS remark               TEXT,
    ADD COLUMN IF NOT EXISTS password_enc         TEXT,
    ADD COLUMN IF NOT EXISTS credential_encrypted TEXT,
    ADD COLUMN IF NOT EXISTS last_collect_time    TIMESTAMP,
    ADD COLUMN IF NOT EXISTS metadata_config      JSONB       NOT NULL DEFAULT '{}'::jsonb;

-- ── 2. td_data_resource：V169(layer_bucket) 裸名版已到 ecos_control，V133(zone) 确认 ──
ALTER TABLE IF EXISTS ecos_control.td_data_resource
    ADD COLUMN IF NOT EXISTS layer_bucket VARCHAR(30),
    ADD COLUMN IF NOT EXISTS zone         VARCHAR(16);

-- ── 3. td_catalog_item：V108(tenant_id) ──
ALTER TABLE IF EXISTS ecos_control.td_catalog_item
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64);

-- ── 4. 场景三件套（ecos_control 侧为活表，V312 的 run_mode 三列此前已手工补齐）──
ALTER TABLE IF EXISTS ecos_control.ecos_scenario_run
    ADD COLUMN IF NOT EXISTS run_mode         VARCHAR(16) NOT NULL DEFAULT 'FORMAL',
    ADD COLUMN IF NOT EXISTS source_run_id    VARCHAR(64),
    ADD COLUMN IF NOT EXISTS sandbox_lineage  VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN';

-- ── 5. ecos_control.ecos_agent_registry / ecos_skill：V316 读面列 ──
--   （AgentRegistryRepository/SkillDao 裸名读 ecos_control 侧；若种子头注 ⚠ 项
--    实测 ecos_ai 侧也有表，则两态并存时以运行 search_path 命中侧 = ecos_control 为准）
ALTER TABLE IF EXISTS ecos_control.ecos_agent_registry
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT NOW();

ALTER TABLE IF EXISTS ecos_control.ecos_skill
    ADD COLUMN IF NOT EXISTS domain      VARCHAR(50) NOT NULL DEFAULT 'default',
    ADD COLUMN IF NOT EXISTS version_no  VARCHAR(20) NOT NULL DEFAULT '1',
    ADD COLUMN IF NOT EXISTS is_deleted  SMALLINT    NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS package_info TEXT;

-- ── 验证：种子重放目标列面回查（执行后人工核对 0 行缺失）──
SELECT 'td_datasource' AS tbl,
       count(*) FILTER (WHERE NOT EXISTS (
           SELECT 1 FROM information_schema.columns c
           WHERE c.table_schema='ecos_control' AND c.table_name='td_datasource'
             AND c.column_name=x.col)) AS missing_cols
FROM (VALUES ('is_default'),('remark'),('password_enc'),('credential_encrypted'),
             ('last_collect_time'),('metadata_config')) x(col)
UNION ALL
SELECT 'td_data_resource',
       count(*) FILTER (WHERE NOT EXISTS (
           SELECT 1 FROM information_schema.columns c
           WHERE c.table_schema='ecos_control' AND c.table_name='td_data_resource'
             AND c.column_name IN (x.col)))
FROM (VALUES ('layer_bucket'),('zone'),('layer')) x(col)
UNION ALL
SELECT 'td_catalog_item',
       count(*) FILTER (WHERE NOT EXISTS (
           SELECT 1 FROM information_schema.columns c
           WHERE c.table_schema='ecos_control' AND c.table_name='td_catalog_item'
             AND c.column_name=x.col))
FROM (VALUES ('tenant_id')) x(col);
