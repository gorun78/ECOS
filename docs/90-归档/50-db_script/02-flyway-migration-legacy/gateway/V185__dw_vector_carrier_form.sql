-- V185 (卷04 §E.3 V185 行): 向量载体形态固化 —— pgvector 自动化幂等脚本 + 未启用档 TEXT 降级列（ecos_dw）
-- 追溯: K-33（pgvector 已建但 V137 自陈"需手工执行"、表 0 行、降级仅 log.warn）+ K-26（裸名/手工态）→ W99/C81；门禁 VectorFormDegradationTest
-- 批准: 需求检视报告 §十四 14.1（2026-09-29）之 R-10=①（文档/切片/embedding 三载体收敛于知识层，禁把向量拉进业务域权威链）；
--       执行边界 §14.4 = 脚本文件落地
-- 状态: 仅落迁移脚本文件，**未对 ecos-postgres 实跑**（实跑属 §14.4 未授权项，需逐项再确认）
-- 纪律: DR01~DR08 / MC01~MC03 / ST03-A / ST07 / IR02(手动 psql) / IR03(只加不删；R-12 DROP 例外前置未闭环 ⇒ 本批零 DROP)
--
-- 双形态口径（本脚本保证两种形态都存在；选择与降级标记由应用侧 PgVectorSupport 完成）:
--   形态 A（pgvector 可用档）: V137 已定义 ecos_knowledge.knowledge_embedding.embedding_vec vector(1536)
--     + HNSW 索引 —— 本脚本**不重复定义、不 ALTER 已有列类型**，仅把"需手工执行"改写为
--     可自动执行的幂等探测分支（扩展不可用即整体跳过，不报错），使脚本在任意档可跑通。
--   形态 B（pgvector 未启用档 / 降级态）: 依 MC05"向量属业务域、不进控制 schema 权威链"的载体固化，
--     在业务域 ecos_dw 并建 TEXT 降级载体表 ecos_dw.ecos_kb_vector_fallback（embedding_text 存 JSON 数值数组文本）。
--   R-10 ① 张力说明: 三载体权威收敛于知识层（形态 A 的 V137 列保留原位、不迁不删，IR03）；
--     ecos_dw 侧仅承载**降级形态副本**（显式 degraded 标记），不构成向量权威链 —— 两口径并立落盘，
--     与 §E.1 争议行"pgvector 未启用档按 TEXT 降级列形态固化"的批准动作一致。
--   探测纪律: DB 侧不做运行期能力判定语义（PgVectorSupport 探测 + vectorDegraded 显式入响应/报告/UI，
--     对治"降级只 log.warn"）；本脚本仅保证 A/B 两形态的 DDL 存在性。

-- ── 1. 形态 A：pgvector 列/索引的幂等自动化（扩展缺失即跳过，不阻断迁移链）──
DO $$
DECLARE
    has_ext BOOLEAN;
BEGIN
    SELECT EXISTS (
        SELECT 1 FROM pg_available_extensions WHERE name = 'vector'
    ) INTO has_ext;

    IF has_ext THEN
        CREATE EXTENSION IF NOT EXISTS vector;

        IF EXISTS (SELECT 1 FROM information_schema.tables
                   WHERE table_schema='ecos_knowledge' AND table_name='knowledge_embedding') THEN
            -- V137 已定义该列时本句为 no-op（不重复定义、不改类型）；live 未跑到 V137 时由本句补齐
            ALTER TABLE ecos_knowledge.knowledge_embedding
                ADD COLUMN IF NOT EXISTS embedding_vec vector(1536);
            COMMENT ON COLUMN ecos_knowledge.knowledge_embedding.embedding_vec IS
                '形态A(V185 自动化固化的 V137 列): pgvector 1536 维；未启用档由应用探测切 ecos_dw.ecos_kb_vector_fallback（形态B），vectorDegraded 显式标记';
            BEGIN
                CREATE INDEX IF NOT EXISTS idx_knowledge_embedding_vec_hnsw
                    ON ecos_knowledge.knowledge_embedding
                    USING hnsw (embedding_vec vector_cosine_ops);
            EXCEPTION WHEN OTHERS THEN
                RAISE NOTICE 'V185: HNSW 索引创建跳过（pgvector 版本或权限限制）: %', SQLERRM;
            END;
        END IF;
    ELSE
        RAISE NOTICE 'V185: pgvector 扩展不可用（未启用档）—— 形态A 跳过，仅落形态B（TEXT 降级载体），运行期由 PgVectorSupport 探测并标记 vectorDegraded';
    END IF;
END $$;

-- ── 2. 形态 B：TEXT 降级载体（业务域 ecos_dw，MC05 口径）──────────────
-- schema 前置（ST07 禁止迁移脚本内 CREATE SCHEMA）：目标 schema = ecos_dw 须已存在；
--   2026-09-30 只读实测本机库 sys_man 内上述 schema 均已实存（登记见 docs/40-实现/DDL迁移脚本落地登记-2026-09-30.md §9.2），故原 CREATE SCHEMA 语句删除，不预置。

CREATE TABLE IF NOT EXISTS ecos_dw.ecos_kb_vector_fallback (
    id                 VARCHAR(36)   NOT NULL,
    embedding_ref      VARCHAR(120)  NOT NULL,            -- 来源载体定位符: ecos_knowledge.knowledge_embedding.id（外部键，*_ref→VARCHAR(120)）
    document_ref       VARCHAR(64),                       -- 原文档键（knowledge_embedding.document_id 为 VARCHAR(64) 外部键）
    chunk_index        INTEGER,                           -- 计数/序号→INTEGER
    embedding_model    VARCHAR(128)  NOT NULL,            -- 模型标识（承旧列宽度）
    embedding_dim      INTEGER       NOT NULL,            -- 向量维度（512/1536/...）
    embedding_text     TEXT          NOT NULL,            -- 降级形态：JSON 数值数组文本（DR04 TEXT，零 JSONB/零 vector 依赖）
    degraded_kind      VARCHAR(20)   NOT NULL DEFAULT 'NO_PGVector',  -- 降级形态种类: NO_PGVector / PROBE_FAILED（显式降级标记，对治 K-33 静默降级）
    trace_id           VARCHAR(64),
    domain             VARCHAR(50)   NOT NULL DEFAULT 'default',
    version_no         VARCHAR(20)   NOT NULL DEFAULT '1',
    is_deleted         SMALLINT      NOT NULL DEFAULT 0,
    create_by          VARCHAR(100)  NOT NULL DEFAULT 'system',
    update_by          VARCHAR(100)  NOT NULL DEFAULT 'system',
    create_time        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_ecos_kb_vector_fallback PRIMARY KEY (id),
    CONSTRAINT uniq_ecos_kb_vec_fb_ref UNIQUE (embedding_ref, is_deleted)
);
CREATE INDEX IF NOT EXISTS idx_ecos_kb_vec_fb_model ON ecos_dw.ecos_kb_vector_fallback(embedding_model, domain, is_deleted);

COMMENT ON TABLE  ecos_dw.ecos_kb_vector_fallback IS
  '形态B(V185/MC05): 向量 TEXT 降级载体（业务域 ecos_dw；pgvector 未启用档由应用写入，vectorDegraded 显式标记，非权威链）';
COMMENT ON COLUMN ecos_dw.ecos_kb_vector_fallback.embedding_text IS
  'JSON 数值数组文本（如 "[0.013,-0.2,...]"，1536/512 维由 embedding_dim 自证）；MC02 合规形态：TEXT，禁 JSONB';

-- ── 回滚说明 ────────────────────────────────────────────────
-- 形态 A 各句为 IF NOT EXISTS/探测跳过（V137 已实跑时零增量）；形态 B 为零行新表。
-- 回滚 = 应用侧停止读写 fallback 表；DDL 按 IR03 保留不 DROP。幂等：可整文件重复执行。

-- ── DDL Lint Self-audit ──────────────────────────────────
-- [DR01] schema 限定 ✓  [DR02] 新表 ecos_ 前缀 ✓  [DR03] 单数 ✓  [DR04] embedding_text TEXT（降级形态）✓
-- [DR05] is_deleted SMALLINT ✓  [DR06] 审计四列 ✓  [DR07] version_no VARCHAR(20) ✓  [DR08] domain VARCHAR(50) DEFAULT 'default' ✓
-- [MC01] PK VARCHAR(36) 无默认值，零 SERIAL ✓  [MC02] 零 JSONB/零裸 NUMERIC ✓
-- [MC03] 零 partial index / CREATE POLICY / PARTITION BY / text[] / timestamptz / `::` ✓（HNSW 为 V137 既有形态固化的例外，pgvector 专有索引类型，包在探测分支内）
-- [ST07] 降级载体落业务域 ecos_dw；知识层既有列仅幂等补齐不迁移（R-10 ① + IR03）✓
