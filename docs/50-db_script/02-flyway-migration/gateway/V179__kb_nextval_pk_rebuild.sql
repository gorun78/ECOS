-- V179 (卷04 §E.3 V179 行): nextval/BIGSERIAL 知识域表 MC01 治理 —— 并建合规新表（PK VARCHAR(36)），旧表只停写
-- 追溯: K-22（知识域仅 3 表合规；14 列 nextval/BIGSERIAL 系）→ W106/C88；门禁「新表合规 + 老表标记 knownLegacy」
-- 批准: 需求检视报告 §十四 14.1（2026-09-29）之 R-12=①（严格双条件）+②（一律不 DROP，只并建新表）；执行边界 §14.4 = 脚本文件落地
-- 状态: 仅落迁移脚本文件，**未对 ecos-postgres 实跑**（实跑属 §14.4 未授权项，需逐项再确认）
-- 纪律: DR01~DR08 / MC01~MC03 / ST03-A / ST07 / IR02(手动 psql) / IR03(只加不删；R-12 DROP 例外前置未闭环 ⇒ 本批零 DROP)
--
-- 范围切分（避免与同批其它脚本重复）:
--   - ecos_knowledge.kb_nav_article_rel（BIGSERIAL）→ 由 V175 合规形态承载，本文件不再并建；
--   - public/ecos_knowledge 的 ecos_glossary_term、ecos_marketplace_asset、ecos_marketplace_access_request、
--     ecos_knowledge_document、ecos_knowledge_graph_node/edge（双镜像 6 对，BIGSERIAL/宽度混杂）
--     → 统一由 V184 并建 VARCHAR(36) 新表，本文件不再并建；
--   - kb_cognitive_pipeline → V163 已并建 ecos_cognitive.ecos_cognitive_pipeline（认知域卷05 续处理其 JSONB）；
--   - kb_scheduled_extract → V153 已 DEPRECATED（新行统一 td_runtime_task_plan），弃表不并建。
-- 数据回填：本批不做（E.3 口径 = 旧表只停写）；回填/双写切换属应用侧后续批次。

-- schema 前置（ST07 禁止迁移脚本内 CREATE SCHEMA）：目标 schema = ecos_knowledge 须已存在；
--   2026-09-30 只读实测本机库 sys_man 内上述 schema 均已实存（登记见 docs/40-实现/DDL迁移脚本落地登记-2026-09-30.md §9.2），故原 CREATE SCHEMA 语句删除，不预置。
CREATE TABLE IF NOT EXISTS ecos_knowledge.ecos_kb_ontology_snapshot (
    id                        VARCHAR(36)  NOT NULL,
    ontology_id               VARCHAR(64)  NOT NULL,      -- 外部业务键（本体 ID）
    snap_version              VARCHAR(20)  NOT NULL,      -- 快照版本号（*_version→VARCHAR(20)，DR 基线）
    entity_codes_json         TEXT         NOT NULL DEFAULT '[]',  -- DR04: JSON 一律 _json TEXT，禁 JSONB
    relationship_codes_json   TEXT         NOT NULL DEFAULT '[]',  -- DR04
    schema_hash               VARCHAR(64),                -- *_hash→VARCHAR(64)
    trace_id                  VARCHAR(64),
    domain                    VARCHAR(50)  NOT NULL DEFAULT 'default',
    version_no                VARCHAR(20)  NOT NULL DEFAULT '1',
    is_deleted                SMALLINT     NOT NULL DEFAULT 0,
    create_by                 VARCHAR(100) NOT NULL DEFAULT 'system',
    update_by                 VARCHAR(100) NOT NULL DEFAULT 'system',
    create_time               TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time               TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_ecos_kb_ontology_snapshot PRIMARY KEY (id),
    CONSTRAINT uniq_ecos_kb_onto_snap UNIQUE (ontology_id, snap_version, is_deleted)
);
CREATE INDEX IF NOT EXISTS idx_ecos_kb_onto_snap_dom ON ecos_knowledge.ecos_kb_ontology_snapshot(domain, is_deleted);

-- ── 2. ecos_kb_lineage_event ← public.kb_lineage_event（V118，BIGSERIAL）──
CREATE TABLE IF NOT EXISTS ecos_knowledge.ecos_kb_lineage_event (
    id            VARCHAR(36)  NOT NULL,
    event_id      VARCHAR(64)  NOT NULL,                  -- 事件业务键（原 event_id UNIQUE）
    query_text    TEXT,                                   -- 解析输入描述
    lineage_format VARCHAR(20),                            -- *_format/*_kind→VARCHAR(20): openlineage/atlas
    nodes_json    TEXT         NOT NULL DEFAULT '[]',     -- DR04
    edges_json    TEXT         NOT NULL DEFAULT '[]',     -- DR04
    parse_time    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    trace_id      VARCHAR(64),
    domain        VARCHAR(50)  NOT NULL DEFAULT 'default',
    version_no    VARCHAR(20)  NOT NULL DEFAULT '1',
    is_deleted    SMALLINT     NOT NULL DEFAULT 0,
    create_by     VARCHAR(100) NOT NULL DEFAULT 'system',
    update_by     VARCHAR(100) NOT NULL DEFAULT 'system',
    create_time   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_ecos_kb_lineage_event PRIMARY KEY (id),
    CONSTRAINT uniq_ecos_kb_lineage_evt UNIQUE (event_id, is_deleted)
);
CREATE INDEX IF NOT EXISTS idx_ecos_kb_lineage_parse ON ecos_knowledge.ecos_kb_lineage_event(parse_time, lineage_format, is_deleted);

-- ── 3. ecos_kg_sync_log ← ecos_knowledge.kg_sync_log（V115 + V136 report 列，BIGSERIAL）──
CREATE TABLE IF NOT EXISTS ecos_knowledge.ecos_kg_sync_log (
    id             VARCHAR(36)   NOT NULL,
    object_type    VARCHAR(20)   NOT NULL,                -- *_type→VARCHAR(20)
    sync_op        VARCHAR(20)   NOT NULL,                -- 原 op 列（语义化改名，避开保留倾向）
    job_id         VARCHAR(120)  NOT NULL,                -- 外部作业键 *_ref 档 VARCHAR(120)
    status         VARCHAR(20)   NOT NULL,                -- status→VARCHAR(20)
    progress       INTEGER       NOT NULL DEFAULT 0,
    node_count     INTEGER       NOT NULL DEFAULT 0,      -- 计数→INTEGER（原 nodes）
    edge_count     INTEGER       NOT NULL DEFAULT 0,      -- 计数→INTEGER（原 edges）
    error_message  VARCHAR(200),                          -- 原 VARCHAR(1024)，按 summary 档收敛，超长截断由应用侧负责
    report_json    TEXT,                                  -- 原 report JSONB → DR04 _json TEXT
    finished_time  TIMESTAMP,
    trace_id       VARCHAR(64),                           -- 与 V181 口径一致（新表直接带列）
    domain         VARCHAR(50)   NOT NULL DEFAULT 'default',
    version_no     VARCHAR(20)   NOT NULL DEFAULT '1',
    is_deleted     SMALLINT      NOT NULL DEFAULT 0,
    create_by      VARCHAR(100)  NOT NULL DEFAULT 'system',
    update_by      VARCHAR(100)  NOT NULL DEFAULT 'system',
    create_time    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_ecos_kg_sync_log PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_ecos_kg_sync_log_job ON ecos_knowledge.ecos_kg_sync_log(job_id, create_time);
CREATE INDEX IF NOT EXISTS idx_ecos_kg_sync_log_dom  ON ecos_knowledge.ecos_kg_sync_log(domain, status, is_deleted);

-- ── 4. ecos_kb_extract_watermark ← ecos_knowledge.kb_extract_watermark（V136，BIGSERIAL）──
CREATE TABLE IF NOT EXISTS ecos_knowledge.ecos_kb_extract_watermark (
    id           VARCHAR(36)  NOT NULL,
    ontology_id  VARCHAR(64)  NOT NULL,                   -- 外部业务键
    entity_code  VARCHAR(100) NOT NULL,                   -- 承旧表宽度（本体实体 code）
    resource_id  VARCHAR(64)  NOT NULL,                   -- DW 层数据资源 ID（外部键）
    watermark    VARCHAR(255),                            -- 增量水位（字符串形态，承旧表）
    trace_id     VARCHAR(64),
    domain       VARCHAR(50)  NOT NULL DEFAULT 'default',
    version_no   VARCHAR(20)  NOT NULL DEFAULT '1',
    is_deleted   SMALLINT     NOT NULL DEFAULT 0,
    create_by    VARCHAR(100) NOT NULL DEFAULT 'system',
    update_by    VARCHAR(100) NOT NULL DEFAULT 'system',
    create_time  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_ecos_kb_extract_watermark PRIMARY KEY (id),
    CONSTRAINT uniq_ecos_kb_extract_wm UNIQUE (ontology_id, entity_code, resource_id, is_deleted)
);

-- ── 5. ecos_kb_extract_candidate ← ecos_knowledge.kb_extract_candidate（V140，BIGSERIAL）──
CREATE TABLE IF NOT EXISTS ecos_knowledge.ecos_kb_extract_candidate (
    id                 VARCHAR(36)  NOT NULL,
    doc_id             VARCHAR(64),                      -- 外部文档业务键（extraction_drafts.id）
    entity_name        VARCHAR(255),                     -- name→VARCHAR(255)
    entity_type        VARCHAR(20),                      -- *_type→VARCHAR(20)
    relation_code      VARCHAR(64),                      -- 关系 code（原 relation）
    subject_id         VARCHAR(36),                      -- 指向合规新表节点 ID
    object_id          VARCHAR(36),                      -- 同上
    confidence         NUMERIC(5,4),                     -- 概率→NUMERIC(5,4)（ST03 形态）
    status             VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    review_note        TEXT,
    ontology_id        VARCHAR(64),                      -- 溯源四列（与 graph_node 对齐）
    ontology_version   VARCHAR(20),
    source_resource_id VARCHAR(64),
    source_pk          VARCHAR(255),
    trace_id           VARCHAR(64),
    domain             VARCHAR(50)   NOT NULL DEFAULT 'default',
    version_no         VARCHAR(20)   NOT NULL DEFAULT '1',
    is_deleted         SMALLINT      NOT NULL DEFAULT 0,
    create_by          VARCHAR(100)  NOT NULL DEFAULT 'system',
    update_by          VARCHAR(100)  NOT NULL DEFAULT 'system',
    create_time        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_ecos_kb_extract_candidate PRIMARY KEY (id),
    CONSTRAINT ck_ecos_kbeic_status CHECK (status IN ('PENDING','APPROVED','REJECTED','MERGED'))
);
CREATE INDEX IF NOT EXISTS idx_ecos_kbeic_doc    ON ecos_knowledge.ecos_kb_extract_candidate(doc_id, is_deleted);
CREATE INDEX IF NOT EXISTS idx_ecos_kbeic_status ON ecos_knowledge.ecos_kb_extract_candidate(status, is_deleted);

-- ── 6. ecos_kb_extract_audit ← ecos_knowledge.kb_extract_audit（V142，BIGSERIAL）──
CREATE TABLE IF NOT EXISTS ecos_knowledge.ecos_kb_extract_audit (
    id            VARCHAR(36)  NOT NULL,
    job_id        VARCHAR(64)  NOT NULL,                  -- 外部作业键
    task_id       VARCHAR(64),                            -- runtime-task 反查
    extract_tier  VARCHAR(20)  NOT NULL DEFAULT 'standard',
    extract_mode  VARCHAR(20),
    status        VARCHAR(20),
    duration_ms   BIGINT,                                 -- 耗时（非金额，保留 BIGINT）
    rows_total    BIGINT,
    rows_ok       BIGINT,
    rows_failed   BIGINT,
    mismatched    BIGINT,
    error_message TEXT,
    suggestion    TEXT,
    trace_id      VARCHAR(64),                            -- 与 V181 口径一致（新表直接带列）
    domain        VARCHAR(50)  NOT NULL DEFAULT 'default',
    version_no    VARCHAR(20)  NOT NULL DEFAULT '1',
    is_deleted    SMALLINT     NOT NULL DEFAULT 0,
    create_by     VARCHAR(100) NOT NULL DEFAULT 'system',
    update_by     VARCHAR(100) NOT NULL DEFAULT 'system',
    create_time   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_ecos_kb_extract_audit PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_ecos_kbeat_job ON ecos_knowledge.ecos_kb_extract_audit(job_id, create_time);

-- ── 7. 旧表 knownLegacy 定性登记（COMMENT 登记，不 DROP 不改结构；IR03）──
DO $$
DECLARE
    pairs RECORD;
BEGIN
    FOR pairs IN SELECT * FROM (VALUES
        ('public',         'kb_ontology_snapshot'),
        ('public',         'kb_lineage_event'),
        ('ecos_knowledge', 'kg_sync_log'),
        ('ecos_knowledge', 'kb_extract_watermark'),
        ('ecos_knowledge', 'kb_extract_candidate'),
        ('ecos_knowledge', 'kb_extract_audit')
    ) AS t(tbl_schema, tbl_name) LOOP
        IF EXISTS (SELECT 1 FROM information_schema.tables
                   WHERE table_schema = pairs.tbl_schema AND table_name = pairs.tbl_name) THEN
            EXECUTE format('COMMENT ON TABLE %I.%I IS %L', pairs.tbl_schema, pairs.tbl_name,
                'knownLegacy(MC01/V179): BIGSERIAL 主键，新写切 ecos_knowledge.ecos_* 合规并建新表；本表只停写不迁移不 DROP（R-12 ① 前置未闭环 + IR03）');
        END IF;
    END LOOP;
END $$;

-- ── 回滚说明 ────────────────────────────────────────────────
-- 新表全部零行（本批不实跑、不迁数据）；回滚 = 应用侧不切写新表即无效化本文件效果；
-- 实跑后如需撤销仅当"零行+零引用"双条件成立（R-12 ①），否则保留空壳不回退。
-- 幂等：CREATE TABLE/INDEX IF NOT EXISTS + COMMENT 可重复执行。

-- ── DDL Lint Self-audit ──────────────────────────────────
-- [DR01] schema 限定 + 小写下划线 ✓  [DR02] 新表 ecos_ 前缀 ✓（旧表名被镜像/停写占用，新名 ecos_kb_*/ecos_kg_*）
-- [DR03] 单数 ✓  [DR04] JSON 形态列 *_json TEXT、零 JSONB ✓  [DR05] is_deleted SMALLINT ✓
-- [DR06] create_time/update_time/create_by/update_by ✓  [DR07] version_no VARCHAR(20) NOT NULL ✓
-- [DR08] domain VARCHAR(50) NOT NULL DEFAULT 'default' ✓（本批新表无 tenant_id 列，租户维度由 domain 承载）
-- [MC01] PK VARCHAR(36) 无默认值、新表零 SERIAL/BIGSERIAL ✓
-- [MC02] 零 JSONB / 零裸 NUMERIC ✓（confidence NUMERIC(5,4)）
-- [MC03] 无 partial index（唯一性以 (key..., is_deleted) 复合唯一键表达）/ 无 CREATE POLICY / PARTITION BY /
--        text[] / timestamptz / `::` / JSONB 操作符 ✓；时间默认 CURRENT_TIMESTAMP ✓
-- [ST07] 全部落 ecos_knowledge ✓  无跨 schema FOREIGN KEY ✓  [IR03] 旧表仅 COMMENT 定性，零 DROP ✓
