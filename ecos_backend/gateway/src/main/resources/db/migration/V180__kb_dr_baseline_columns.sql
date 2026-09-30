-- V180 (卷04 §E.3 V180 行): 知识域存量表 DR 基线列补齐 —— domain + 审计五列 + version_no + is_deleted（全部 IF NOT EXISTS）
-- 追溯: K-25（domain 列 9/30 → 目标 30/30；审计基线列缺失）→ W110/C92；门禁 DrBaselineColumnsTest
-- 批准: 需求检视报告 §十四 14.1（2026-09-29）之 R-12=①+②；执行边界 §14.4 = 脚本文件落地
-- 状态: 仅落迁移脚本文件，**未对 ecos-postgres 实跑**（实跑属 §14.4 未授权项，需逐项再确认）
-- 纪律: DR01~DR08 / MC01~MC03 / ST03-A / ST07 / IR02(手动 psql) / IR03(只加不删；R-12 DROP 例外前置未闭环 ⇒ 本批零 DROP)
--
-- 口径说明:
--   1) 卷04 实测 30 表 = live information_schema 知识域表全集；本文件清单 = 迁移单源可枚举的 24 张存量表 +
--      V175~V179/V184 新建合规表（已自带基线列，重复执行 no-op）。实跑时以 SchemaInventory 对账，
--      单源无 DDL 的 live 表（如 public.extraction_drafts、public.ecos_knowledge_document 等 v1 时代实表）
--      已尽量列入，缺失者按同一模板补行。
--   2) 列已存在 = no-op（不改宽度、不改语义 —— 如 knowledge_article.domain VARCHAR(64) 可空、
--      sys_compliance_rule.domain VARCHAR(128) 等业务含义混用列，保持原样，待各表切写 ecos_* 新表后自然退役）。
--   3) 审计五列 = create_time/update_time/create_by/update_by + is_deleted（逻辑删除），加 DR07 version_no、DR08 domain。

DO $$
DECLARE
    t        RECORD;
    fulltbl  TEXT;
BEGIN
    FOR t IN SELECT * FROM (VALUES
        -- ── ecos_knowledge 存量（V51/V115/V136/V138/V140/V141/V142/V155~V157 等）──
        ('ecos_knowledge', 'knowledge_article'),
        ('ecos_knowledge', 'knowledge_embedding'),
        ('ecos_knowledge', 'graph_node'),
        ('ecos_knowledge', 'graph_edge'),
        ('ecos_knowledge', 'graph_subgraph'),
        ('ecos_knowledge', 'kg_sync_log'),
        ('ecos_knowledge', 'kb_extract_watermark'),
        ('ecos_knowledge', 'kb_extract_candidate'),
        ('ecos_knowledge', 'kb_extract_audit'),
        ('ecos_knowledge', 'kb_scheduled_extract'),
        ('ecos_knowledge', 'kb_doc'),
        ('ecos_knowledge', 'kb_doc_chunk'),
        ('ecos_knowledge', 'kb_nav_category'),
        ('ecos_knowledge', 'kb_nav_tag'),
        ('ecos_knowledge', 'kb_nav_article_rel'),
        -- ── public 知识域/kb_* 存量（含 v1 时代无单源 DDL 的 live 表，存在才处理）──
        ('public', 'kb_ontology_snapshot'),
        ('public', 'kb_lineage_event'),
        ('public', 'extraction_drafts'),
        ('public', 'ecos_knowledge_document'),
        ('public', 'ecos_knowledge_graph_node'),
        ('public', 'sys_compliance_rule'),
        ('public', 'sys_rule_version'),
        ('public', 'sys_extraction_source'),
        ('public', 'ecos_glossary_term'),
        ('public', 'ecos_glossary_term_relation'),
        ('public', 'ecos_marketplace_asset'),
        ('public', 'ecos_marketplace_access_request'),
        ('public', 'access_request')
    ) AS s(tbl_schema, tbl_name) LOOP
        fulltbl := format('%I.%I', t.tbl_schema, t.tbl_name);
        IF EXISTS (SELECT 1 FROM information_schema.tables
                   WHERE table_schema = t.tbl_schema AND table_name = t.tbl_name) THEN
            -- DR08 分区键（已存在即 no-op，不动业务混用列）
            EXECUTE format('ALTER TABLE %s ADD COLUMN IF NOT EXISTS domain VARCHAR(50) NOT NULL DEFAULT %L', fulltbl, 'default');
            -- DR06 审计列
            EXECUTE format('ALTER TABLE %s ADD COLUMN IF NOT EXISTS create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP', fulltbl);
            EXECUTE format('ALTER TABLE %s ADD COLUMN IF NOT EXISTS update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP', fulltbl);
            EXECUTE format('ALTER TABLE %s ADD COLUMN IF NOT EXISTS create_by VARCHAR(100)', fulltbl);
            EXECUTE format('ALTER TABLE %s ADD COLUMN IF NOT EXISTS update_by VARCHAR(100)', fulltbl);
            -- DR07 版本 + DR05 逻辑删除
            EXECUTE format('ALTER TABLE %s ADD COLUMN IF NOT EXISTS version_no VARCHAR(20) NOT NULL DEFAULT %L', fulltbl, '1');
            EXECUTE format('ALTER TABLE %s ADD COLUMN IF NOT EXISTS is_deleted SMALLINT NOT NULL DEFAULT 0', fulltbl);
            -- trace_id 基础化（V181 之外的表统一具备链路位；V181 仍按 §E.3 单列登记 kb_extract_audit/kg_sync_log）
            EXECUTE format('ALTER TABLE %s ADD COLUMN IF NOT EXISTS trace_id VARCHAR(64)', fulltbl);
        END IF;
    END LOOP;
END $$;

-- ── domain 业务混用列登记（不改列，只 COMMENT，供切写新表后退役）──
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema='public' AND table_name='ecos_glossary_term' AND column_name='domain') THEN
        COMMENT ON COLUMN public.ecos_glossary_term.domain IS
            'V180 登记: 本列为业务"词条领域"（非 DR08 分区键，宽度 128 保留）。合规分区键随 ecos_kb_term 新表（V184）启用，本表停写后退役';
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema='public' AND table_name='sys_compliance_rule' AND column_name='domain') THEN
        COMMENT ON COLUMN public.sys_compliance_rule.domain IS
            'V180 登记: 业务"规则领域"（非 DR08 分区键）。夹具 53 行按 R-12 ② 以 domain/is_deleted 逻辑隔离，不 DELETE（K-21）';
    END IF;
END $$;

-- ── 回滚说明 ────────────────────────────────────────────────
-- 全部为可空/带默认值的新增列，存量读写不受影响；回滚 = 应用侧忽略新列（列保留，IR03 不 DROP）。
-- 幂等：ADD COLUMN IF NOT EXISTS，可重复执行。

-- ── DDL Lint Self-audit ──────────────────────────────────
-- [DR01] schema 限定 ✓  [DR05] is_deleted SMALLINT ✓  [DR06] 审计四列+is_deleted ✓
-- [DR07] version_no VARCHAR(20) NOT NULL DEFAULT '1' ✓  [DR08] domain VARCHAR(50) NOT NULL DEFAULT 'default' ✓
-- [MC02] 零新增 JSONB/裸 NUMERIC ✓  [MC03] 零 partial index/`::`/timestamptz ✓（DEFAULT CURRENT_TIMESTAMP）
-- [IR03] 只加不删 ✓  [ST07] 仅触达知识域表（ecos_knowledge + public 知识/kb_*/sys_compliance 族）✓
-- 夹具处置: sys_compliance_rule 53 测试行不 DELETE，以 domain/is_deleted 逻辑隔离（§E.3 禁则 + R-12 ②）✓
