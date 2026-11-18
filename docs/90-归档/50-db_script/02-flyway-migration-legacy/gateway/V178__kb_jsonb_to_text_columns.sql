-- V178 (卷04 §E.3 V178 行): 知识域 jsonb 列 MC02 治理 —— 新增同名 `_json` TEXT 列 + 幂等回填（双写过渡）
-- 追溯: K-23（知识域 19 个 jsonb 列，规范只允许 TEXT）→ W107/C89；门禁 Mc02JsonbRetirementTest#noJsonbReadRemaining
-- 批准: 需求检视报告 §十四 14.1（2026-09-29）之 R-12=①（严格双条件）+②；执行边界 §14.4 = 脚本文件落地
-- 状态: 仅落迁移脚本文件，**未对 ecos-postgres 实跑**（实跑属 §14.4 未授权项，需逐项再确认）
-- 纪律: DR01~DR08 / MC01~MC03 / ST03-A / ST07 / IR02(手动 psql) / IR03(只加不删；R-12 DROP 例外前置未闭环 ⇒ 本批零 DROP)
--
-- 【2026-09-30 校订】三处按红线与实测收口（原稿口径与规则/现网不符，登记见 docs/40-实现/DDL迁移脚本落地登记-2026-09-30.md §五/§八）：
--   ① 过渡列后缀 `_text` → **`_json`**：DR04 明文"JSON 语义列（无论以存量 JSONB 还是 v1.2 白名单的 TEXT 存）必加 `_json` 后缀"，
--     分册 §E.3 字面的 `_text` 违该红线 ⇒ 以红线为准，卷04 §E.3 同步出勘误。只读实测：现网 `*_text` 列仅 6 个且全为本脚本
--     未涉及的语义（chunk_text/input_text/output_text/parsed_text/main_text），零同名冲突；16 个目标列的 `<col>_json` 兄弟列
--     实测全部不存在（information_schema 命中 0），改后缀零代价。
--   ② 清单第 10/11 行 schema 纠错：`kb_ontology_snapshot` 实测在 **ecos_knowledge**（非 public），且代码按
--     `ecos_knowledge.kb_ontology_snapshot` 限定名读写（`KgMapperService.java:194,202`、`KbEntityInstanceExtractionService.java:528,532`、
--     `EntityLinkerService.java:151`）；public 下不存在该表。
--   ③ 移除原第 16 行 `public.ecos_knowledge_graph_node.properties_json`：该列**名已合规、型为 JSONB**，且
--     `public`=23 行有数据、`ecos_knowledge` 同名表亦有该列（0 行），运行期由 `KnowledgeNodeRepository.java:15` 读取 ⇒
--     生成 `properties_json_json`/`_text` 双写列既非合规路径也无意义；改型 = 违 IR03 + 需代码切换（§14.4 未授权项③）。
--     本批只登记为 MC02 存量偏差（D-9），不进本脚本清单。
--
-- 过渡策略（§E.3 V178 行，经①收口后）: jsonb 列**增加同名 `_json` 列** + 双写迁移期；读写切 `_json` 后旧列停写、
--   不删除（IR03）。列名清单 = 以迁移单源目录 DDL 枚举的知识域 jsonb 列（15 列）；卷04 K-23 口径为 live
--   information_schema 实测 19 列 —— 若实跑时 inventory 有增量列（如 V1~V99 时代无单源 DDL 的实表），
--   按本文件 VALUES 清单模板补行后执行，不改本批已批文件语义。
-- cast 纪律: 仅用 CAST(x AS TEXT)，零 `::` 裸 cast（MC03）。

-- ── 1. 逐列并建 _json + 幂等回填 + 双写期注释 ────────────────────
DO $$
DECLARE
    r        RECORD;
    textcol  TEXT;
BEGIN
    FOR r IN
        SELECT * FROM (VALUES
            -- schema          table                       jsonb 列
            ('ecos_knowledge', 'knowledge_article',        'tags'),
            ('ecos_knowledge', 'knowledge_embedding',      'embedding'),
            ('ecos_knowledge', 'graph_node',               'properties'),
            ('ecos_knowledge', 'graph_edge',               'properties'),
            ('ecos_knowledge', 'graph_subgraph',           'node_ids'),
            ('ecos_knowledge', 'graph_subgraph',           'edge_ids'),
            ('ecos_knowledge', 'kb_doc_chunk',             'metadata'),
            ('ecos_knowledge', 'kb_scheduled_extract',     'ontology_ids'),
            ('ecos_knowledge', 'kg_sync_log',              'report'),
            ('ecos_knowledge', 'kb_ontology_snapshot',     'entity_codes'),
            ('ecos_knowledge', 'kb_ontology_snapshot',     'relationship_codes'),
            ('public',         'kb_lineage_event',         'nodes'),
            ('public',         'kb_lineage_event',         'edges'),
            ('public',         'kb_cognitive_pipeline',    'config'),
            ('public',         'kb_cognitive_pipeline',    'result')
        ) AS t(tbl_schema, tbl_name, col_name)
    LOOP
        -- 表与列都存在且确为 jsonb 才处理（live 漂移容错；列不存在仅跳过，不报错）
        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = r.tbl_schema AND table_name = r.tbl_name
              AND column_name = r.col_name AND udt_name = 'jsonb'
        ) THEN
            textcol := r.col_name || '_json';
            EXECUTE format(
                'ALTER TABLE %I.%I ADD COLUMN IF NOT EXISTS %I TEXT',
                r.tbl_schema, r.tbl_name, textcol);
            -- 幂等回填：仅未回填行；CAST 显式（MC03 禁 `::`）
            EXECUTE format(
                'UPDATE %I.%I SET %I = CAST(%I AS TEXT) WHERE %I IS NULL',
                r.tbl_schema, r.tbl_name, textcol, r.col_name, textcol);
            EXECUTE format(
                'COMMENT ON COLUMN %I.%I.%I IS %L',
                r.tbl_schema, r.tbl_name, textcol,
                'V178/MC02: ' || r.col_name || ' 的 TEXT 形态（双写过渡期；读写切本列后旧 jsonb 列停写）');
            EXECUTE format(
                'COMMENT ON COLUMN %I.%I.%I IS %L',
                r.tbl_schema, r.tbl_name, r.col_name,
                'V178/MC02 定性: 存量 JSONB 列，读写切 ' || textcol || ' 后停写（IR03 只加不删，本批不 DROP）');
        ELSIF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = r.tbl_schema AND table_name = r.tbl_name
              AND column_name = r.col_name
        ) THEN
            EXECUTE format(
                'COMMENT ON COLUMN %I.%I.%I IS %L',
                r.tbl_schema, r.tbl_name, r.col_name,
                'V178 登记: live 形态已非 JSONB（非 TEXT 即视为已收敛），清单保留以供 inventory 对账');
        END IF;
    END LOOP;
END $$;

-- ── 回滚说明 ────────────────────────────────────────────────
-- 本文件只新增可空 _json 列并回填文本快照，旧 jsonb 列/数据零变更。
-- 回滚 = 应用侧读写退回 jsonb 列即可；_json 列成为孤儿可空列，保留不删（IR03）。
-- 重复执行安全：ADD COLUMN IF NOT EXISTS + UPDATE ... WHERE _json IS NULL，幂等。

-- ── DDL Lint Self-audit ──────────────────────────────────
-- [DR01] 全 schema 限定 ✓  [DR04] 过渡列命名 `*_json` + TEXT（2026-09-30 校订①，替代原稿 `_text` 违例形态）✓
-- [MC02] 本脚本不新建任何 JSONB 列 ✓（仅动存量列）  [MC03] 零 `::`（CAST AS TEXT）✓  零 DROP/ALTER DROP ✓
-- 文件名 slug `kb_jsonb_to_text_columns` 为历史描述（IR02 手动执行按文件名指定，不改名以免破坏运维/文档引用）；
--   实际落地形态 = `*_json` TEXT 列（校订①）。
-- [ST07] 仅触达知识域（ecos_knowledge + public 知识/kb_* 表）✓  [IR03] 只加不删、旧列定性停写注释登记 ✓
-- 备注: public.kb_cognitive_pipeline 的 config/result 两列属知识域文件命名（kb_*）但表归认知域收编
--       （V163 新表 ecos_cognitive.ecos_cognitive_pipeline 仍为 JSONB 形态）——跨卷待办已上报（卷05 处理）。
