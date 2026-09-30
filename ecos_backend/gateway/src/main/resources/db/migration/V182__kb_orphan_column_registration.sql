-- V182 (卷04 §E.3 V182 行): 知识域孤儿列处置登记 —— 零 DROP，仅 COMMENT 登记待处置
-- 追溯: 卷04 实测孤儿列（表有列、Mapper 无读写）→ W107/C89 邻项；门禁 =「无 DROP」
-- 批准: 需求检视报告 §十四 14.1（2026-09-29）之 R-12=①+②；执行边界 §14.4 = 脚本文件落地
-- 状态: 仅落迁移脚本文件，**未对 ecos-postgres 实跑**（实跑属 §14.4 未授权项，需逐项再确认）
-- 纪律: DR01~DR08 / MC01~MC03 / ST03-A / ST07 / IR02(手动 psql) / IR03(只加不删；R-12 DROP 例外前置未闭环 ⇒ 本批零 DROP)
--
-- 处置口径（§E.3 V182 原文）: "先补 Mapper 使用或标记停用" —— 属应用侧改造（后续批次，代码变更不在本批）；
--   本脚本只做 DDL 侧登记：逐列 COMMENT ON COLUMN ... IS 'legacy-orphan: 待 Mapper 使用或标停用（W/C..）'。
--   live 列不存在（单源与 live 漂移，如 V51 单源里 knowledge_embedding 无 article_id/chunk_text/model 三列，
--   系 live 加列未回流单源）时探测跳过，不报错。
--   knowledge_embedding.embedding（JSONB）的读写收敛由 V178 承载，此处不重复登记。

DO $$
DECLARE
    c       RECORD;
    note    TEXT;
BEGIN
    FOR c IN SELECT * FROM (VALUES
        ('ecos_knowledge', 'knowledge_embedding', 'article_id',
            'legacy-orphan(V182): 与 document_id 语义重叠且 Mapper 零读写 —— 待 Mapper 使用或标停用（卷04 §E.3 V182 / F04-08）；IR03 不 DROP'),
        ('ecos_knowledge', 'knowledge_embedding', 'chunk_text',
            'legacy-orphan(V182): 正文冗余列，Mapper 零读写，正源=kb_doc_chunk.content —— 待 Mapper 使用或标停用（卷04 §E.3 V182）；IR03 不 DROP'),
        ('ecos_knowledge', 'knowledge_embedding', 'model',
            'legacy-orphan(V182): 与 embedding_model 语义重叠且 Mapper 零读写 —— 待 Mapper 使用或标停用（卷04 §E.3 V182）；IR03 不 DROP'),
        ('ecos_knowledge', 'knowledge_article', 'source_type',
            'legacy-orphan(V182): 与 source 列语义重叠，Mapper 零读写 —— 待 Mapper 使用或标停用（卷04 §E.3 V182）；IR03 不 DROP'),
        ('ecos_knowledge', 'knowledge_article', 'tags',
            'legacy-orphan 双身份(V182): JSONB 读写已由 V178 切 tags_json 双写（2026-09-30 校订：过渡列后缀由 _text 收口为 DR04 的 _json）；Mapper 若弃用则整列停用登记 —— 待 Mapper 使用或标停用；IR03 不 DROP')
    ) AS s(col_schema, tbl_name, col_name, note_text) LOOP
        IF EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = c.col_schema AND table_name = c.tbl_name
                     AND column_name = c.col_name) THEN
            EXECUTE format('COMMENT ON COLUMN %I.%I.%I IS %L', c.col_schema, c.tbl_name, c.col_name, c.note_text);
        END IF;
    END LOOP;
END $$;

-- ── 回滚说明 ────────────────────────────────────────────────
-- 仅改列注释（元数据），无结构/数据变更；回滚 = 恢复原注释或忽略。天然幂等。

-- ── DDL Lint Self-audit ──────────────────────────────────
-- [DR01] schema 限定 ✓  [IR03] 零 DROP、零 ALTER，仅 COMMENT 登记 ✓
-- [MC02] 本脚本不新增 JSONB ✓（存量 jsonb 收敛见 V178）  [ST07] 仅知识域表 ✓
