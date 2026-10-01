-- V183 (卷04 §E.3 V183 行): 双镜像 6 对写切换登记 —— 新写一律 ecos_knowledge 限定；public 侧只停写不迁（R-1 选项 a）
-- 追溯: K-17（public↔ecos_knowledge 双镜像 6 对，实数据全在 public，引擎 schema 侧 0 行）→ W110/C92；门禁 SchemaInventoryGateTest
-- 批准: 需求检视报告 §十四 14.1（2026-09-29）之 R-1=a（新写 schema 限定、存量只停写不迁移）；执行边界 §14.4 = 脚本文件落地
-- 状态: 仅落迁移脚本文件，**未对 ecos-postgres 实跑**（实跑属 §14.4 未授权项，需逐项再确认）
-- 纪律: DR01~DR08 / MC01~MC03 / ST03-A / ST07 / IR02(手动 psql) / IR03(只加不删；R-12 DROP 例外前置未闭环 ⇒ 本批零 DROP)
--
-- 形态说明（"视图/注释形式的切换登记，禁改旧表"）:
--   1) 静态登记表视图 ecos_knowledge.v_kb_mirror_registry：6 对镜像的权威侧/停写侧台账（不依赖旧表列结构，
--      零断裂风险），供 SchemaInventoryGateTest 与人工对账消费；
--   2) 两侧表 COMMENT 定性登记（旧表零 ALTER）；
--   3) public 侧存量实数据**不迁移**（R-1 a）：新写切 ecos_knowledge 侧（PK 宽度违规对由 V184 并建的
--      ecos_kb_* 统一 VARCHAR(36) 新表承载），public 历史行转为只读 legacy；
--   4) 不建跨 schema 同名列视图投影旧表（两侧列结构已漂移，投影即脆断）——刻意取舍，已在此声明。

-- schema 前置（ST07 禁止迁移脚本内 CREATE SCHEMA）：目标 schema = ecos_knowledge 须已存在；
--   2026-09-30 只读实测本机库 sys_man 内上述 schema 均已实存（登记见 docs/40-实现/DDL迁移脚本落地登记-2026-09-30.md §9.2），故原 CREATE SCHEMA 语句删除，不预置。
CREATE OR REPLACE VIEW ecos_knowledge.v_kb_mirror_registry AS
SELECT * FROM (VALUES
    -- 表名（去 schema）                              权威写侧            public 停写侧形态                      收敛去向
    ('ecos_glossary_term',              'ecos_knowledge', 'public（V7/V143 系，BIGSERIAL+text[]）', 'V184 ecos_knowledge.ecos_kb_term'),
    ('ecos_knowledge_document',         'ecos_knowledge', 'public（v1 时代实表，宽度待对账）',       'V184 ecos_knowledge.ecos_kb_document'),
    ('ecos_knowledge_graph_node',       'ecos_knowledge', 'public（V108，id VARCHAR(64)+JSONB）',   'V184 ecos_knowledge.ecos_kb_graph_node'),
    ('ecos_knowledge_graph_edge',       'ecos_knowledge', 'public（live 存在，单源无 DDL）',        'V184 ecos_knowledge.ecos_kb_graph_edge'),
    ('ecos_marketplace_asset',          'ecos_knowledge', 'public（V7，BIGSERIAL）',                'V184 ecos_knowledge.ecos_kb_market_asset'),
    ('ecos_marketplace_access_request', 'ecos_knowledge', 'public（V7，BIGSERIAL；文档名 access_request）', 'V184 ecos_knowledge.ecos_kb_access_request')
) AS reg(table_name, write_authority_schema, legacy_public_shape, converge_to);

COMMENT ON VIEW ecos_knowledge.v_kb_mirror_registry IS
  'V183 双镜像切换登记台账（K-17 6 对）：write_authority=新写唯一侧（schema 限定）；public 侧只停写不迁（R-1 a）；converge_to=PK 宽度/形态违规对的合规新表（V184）';

-- ── 2. 两侧表 COMMENT 定性登记（存在才登记，禁改旧表结构）──────────
DO $$
DECLARE
    pairs RECORD;
    note_legacy TEXT;
    note_auth   TEXT;
BEGIN
    note_legacy := 'knownLegacy(V183/R-1 a): 双镜像 public 侧，自 V183 起**只停写不迁移**；实数据保留只读，新写切 ecos_knowledge 限定表（台账见 ecos_knowledge.v_kb_mirror_registry）';
    note_auth   := '权威写侧(V183/R-1 a): 新写一律本 schema 限定；本表形态若违 MC01（PK 宽度/BIGSERIAL），收敛至 V184 并建 ecos_kb_* 新表，本表随切写停写';
    FOR pairs IN SELECT table_name FROM ecos_knowledge.v_kb_mirror_registry LOOP
        IF EXISTS (SELECT 1 FROM information_schema.tables
                   WHERE table_schema='public' AND table_name=pairs.table_name) THEN
            EXECUTE format('COMMENT ON TABLE public.%I IS %L', pairs.table_name, note_legacy);
        END IF;
        IF EXISTS (SELECT 1 FROM information_schema.tables
                   WHERE table_schema='ecos_knowledge' AND table_name=pairs.table_name) THEN
            EXECUTE format('COMMENT ON TABLE ecos_knowledge.%I IS %L', pairs.table_name, note_auth);
        END IF;
    END LOOP;
END $$;

-- ── 回滚说明 ────────────────────────────────────────────────
-- 仅建登记表视图 + COMMENT 元数据，零表结构/数据变更；回滚 = DROP VIEW 亦无害，但按 IR03 保留亦无碍。幂等。

-- ── DDL Lint Self-audit ──────────────────────────────────
-- [DR01] schema 限定 ✓  [MC03] 视图零 `::`/零 timestamptz ✓  [ST07] 登记视图落控制域 ecos_knowledge ✓
-- [IR03] 旧表零 ALTER/零 DROP ✓（只 COMMENT）  R-1 a 口径：public 侧只停写不迁 ✓
-- 切换纪律声明: Mapper/SQL 侧必须显式 ecos_knowledge. 前缀（撤 search_path 依赖属 H22 严格顺序项，另批执行）✓
