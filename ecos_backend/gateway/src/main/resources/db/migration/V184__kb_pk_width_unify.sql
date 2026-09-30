-- V184 (卷04 §E.3 V184 行): 双镜像 6 对 PK 宽度 36↔64（及 BIGSERIAL）不一致 —— 并建合规新表统一 VARCHAR(36)
-- 追溯: K-17（主键宽度不一致 VARCHAR(36)↔VARCHAR(64)）+ K-22 → W106/C88；门禁 MC01
-- 批准: 需求检视报告 §十四 14.1（2026-09-29）之 R-12=①+②（一律不 DROP，只并建新表）；R-1=a；执行边界 §14.4 = 脚本文件落地
-- 状态: 仅落迁移脚本文件，**未对 ecos-postgres 实跑**（实跑属 §14.4 未授权项，需逐项再确认）
-- 纪律: DR01~DR08 / MC01~MC03 / ST03-A / ST07 / IR02(手动 psql) / IR03(只加不删；R-12 DROP 例外前置未闭环 ⇒ 本批零 DROP)
--
-- 命名说明: 6 对的旧名已被 public/ecos_knowledge 两侧存量表占用（IF NOT EXISTS 无法以新形态落同名表），
--   故并建新表统一 `ecos_kb_*` 前缀（DR02 ecos_ ✓ / DR03 单数 ✓），与 V183 登记表视图 converge_to 一致。
--   两侧旧表全部只停写不迁移不 DROP（R-1 a + R-12 ②），历史行只读；数据回填/双写切换属应用侧后续批次。

-- schema 前置（ST07 禁止迁移脚本内 CREATE SCHEMA）：目标 schema = ecos_knowledge 须已存在；
--   2026-09-30 只读实测本机库 sys_man 内上述 schema 均已实存（登记见 docs/40-实现/DDL迁移脚本落地登记-2026-09-30.md §9.2），故原 CREATE SCHEMA 语句删除，不预置。
CREATE TABLE IF NOT EXISTS ecos_knowledge.ecos_kb_term (
    id              VARCHAR(36)  NOT NULL,
    term_code       VARCHAR(64),                          -- 术语编码
    name            VARCHAR(255) NOT NULL,
    definition      TEXT,
    term_type       VARCHAR(20),                          -- ENTITY/RELATION/METRIC/FUNCTION/CONCEPT
    subject_domain  VARCHAR(64),                          -- 原业务"词条领域"改名，让 domain 归 DR08 语义
    object_type_ref VARCHAR(120),                         -- 引用本体实体（*_ref→VARCHAR(120)）
    parent_term_id  VARCHAR(36),                          -- 自引用（同 schema 内弱关联，不建 FK）
    aliases_json    TEXT         NOT NULL DEFAULT '[]',   -- 原 text[]（MC03 禁）→ DR04 _json TEXT
    examples_json   TEXT         NOT NULL DEFAULT '[]',   -- 原 text[] → _json TEXT
    tags_json       TEXT         NOT NULL DEFAULT '[]',   -- 原 text[] → _json TEXT
    definition_version VARCHAR(20) NOT NULL DEFAULT '1',  -- 词条定义版本（*_version→VARCHAR(20)）
    status          VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    owner_name      VARCHAR(100),                         -- *_by/owner→VARCHAR(100)
    trace_id        VARCHAR(64),
    domain          VARCHAR(50)  NOT NULL DEFAULT 'default',
    version_no      VARCHAR(20)  NOT NULL DEFAULT '1',
    is_deleted      SMALLINT     NOT NULL DEFAULT 0,
    create_by       VARCHAR(100) NOT NULL DEFAULT 'system',
    update_by       VARCHAR(100) NOT NULL DEFAULT 'system',
    create_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_ecos_kb_term PRIMARY KEY (id),
    CONSTRAINT ck_ecos_kb_term_type CHECK (term_type IN ('ENTITY','RELATION','METRIC','FUNCTION','CONCEPT')),
    CONSTRAINT uniq_ecos_kb_term_name UNIQUE (domain, subject_domain, name, is_deleted)
);
CREATE INDEX IF NOT EXISTS idx_ecos_kb_term_type ON ecos_knowledge.ecos_kb_term(term_type, status, is_deleted);

-- ── 2. ecos_kb_document ← 知识文档（旧 ecos_knowledge_document：宽度 36↔64 漂移对）──
CREATE TABLE IF NOT EXISTS ecos_knowledge.ecos_kb_document (
    id               VARCHAR(36)  NOT NULL,
    doc_code         VARCHAR(64),                         -- 业务文档键（幂等导入用）
    title            VARCHAR(255) NOT NULL,
    content          TEXT,
    doc_type         VARCHAR(20),                         -- *_type→VARCHAR(20)
    tags_json        TEXT         NOT NULL DEFAULT '[]',  -- DR04
    entity_types_json TEXT        NOT NULL DEFAULT '[]',  -- DR04
    status           VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    trace_id         VARCHAR(64),
    domain           VARCHAR(50)  NOT NULL DEFAULT 'default',
    version_no       VARCHAR(20)  NOT NULL DEFAULT '1',
    is_deleted       SMALLINT     NOT NULL DEFAULT 0,
    create_by        VARCHAR(100) NOT NULL DEFAULT 'system',
    update_by        VARCHAR(100) NOT NULL DEFAULT 'system',
    create_time      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_ecos_kb_document PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uniq_ecos_kb_document_code ON ecos_knowledge.ecos_kb_document(doc_code, is_deleted);

-- ── 3. ecos_kb_graph_node ← 图谱节点（旧 graph_node / ecos_knowledge_graph_node 两侧宽度/形态不一）──
CREATE TABLE IF NOT EXISTS ecos_knowledge.ecos_kb_graph_node (
    id                 VARCHAR(36)  NOT NULL,
    node_label         VARCHAR(120),                      -- 原 label
    node_type          VARCHAR(20)  NOT NULL DEFAULT 'Concept',
    description        TEXT,
    properties_json    TEXT         NOT NULL DEFAULT '{}', -- 原 properties/properties_json JSONB → DR04 TEXT
    ontology_id        VARCHAR(64),                        -- 溯源四列（V134 口径）
    ontology_version   VARCHAR(20),
    source_resource_id VARCHAR(64),
    source_pk          VARCHAR(255),
    trace_id           VARCHAR(64),
    domain             VARCHAR(50)  NOT NULL DEFAULT 'default',
    version_no         VARCHAR(20)  NOT NULL DEFAULT '1',
    is_deleted         SMALLINT     NOT NULL DEFAULT 0,
    create_by          VARCHAR(100) NOT NULL DEFAULT 'system',
    update_by          VARCHAR(100) NOT NULL DEFAULT 'system',
    create_time        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_ecos_kb_graph_node PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_ecos_kb_gnode_onto ON ecos_knowledge.ecos_kb_graph_node(ontology_id, ontology_version, is_deleted);
CREATE INDEX IF NOT EXISTS idx_ecos_kb_gnode_type ON ecos_knowledge.ecos_kb_graph_node(node_type, domain, is_deleted);

-- ── 4. ecos_kb_graph_edge ← 图谱边（同上宽度不一对）──
CREATE TABLE IF NOT EXISTS ecos_knowledge.ecos_kb_graph_edge (
    id                 VARCHAR(36)  NOT NULL,
    source_node_id     VARCHAR(36)  NOT NULL,             -- 指 ecos_kb_graph_node.id（同 schema 弱关联）
    target_node_id     VARCHAR(36)  NOT NULL,
    edge_type          VARCHAR(64)  NOT NULL,
    properties_json    TEXT         NOT NULL DEFAULT '{}', -- DR04
    weight             NUMERIC(9,6) NOT NULL DEFAULT 1.0,  -- 权重（比率档 NUMERIC(9,6)，禁裸 NUMERIC）
    ontology_id        VARCHAR(64),
    ontology_version   VARCHAR(20),
    source_resource_id VARCHAR(64),
    source_pk          VARCHAR(255),
    trace_id           VARCHAR(64),
    domain             VARCHAR(50)  NOT NULL DEFAULT 'default',
    version_no         VARCHAR(20)  NOT NULL DEFAULT '1',
    is_deleted         SMALLINT     NOT NULL DEFAULT 0,
    create_by          VARCHAR(100) NOT NULL DEFAULT 'system',
    update_by          VARCHAR(100) NOT NULL DEFAULT 'system',
    create_time        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_ecos_kb_graph_edge PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_ecos_kb_gedge_src ON ecos_knowledge.ecos_kb_graph_edge(source_node_id, edge_type, is_deleted);
CREATE INDEX IF NOT EXISTS idx_ecos_kb_gedge_dst ON ecos_knowledge.ecos_kb_graph_edge(target_node_id, edge_type, is_deleted);

-- ── 5. ecos_kb_market_asset ← 市场资产（旧 ecos_marketplace_asset：BIGSERIAL）──
CREATE TABLE IF NOT EXISTS ecos_knowledge.ecos_kb_market_asset (
    id            VARCHAR(36)   NOT NULL,
    name          VARCHAR(255)  NOT NULL,
    description   TEXT,
    asset_category VARCHAR(64),                           -- 数据集/AI模型/API/报表
    owner_name    VARCHAR(100),
    rating        NUMERIC(9,6)  NOT NULL DEFAULT 0,       -- 评分（比率档 NUMERIC(9,6)，禁裸 NUMERIC）
    popularity    INTEGER       NOT NULL DEFAULT 0,       -- 计数→INTEGER
    status        VARCHAR(20)   NOT NULL DEFAULT 'PUBLISHED',
    trace_id      VARCHAR(64),
    domain        VARCHAR(50)   NOT NULL DEFAULT 'default',
    version_no    VARCHAR(20)   NOT NULL DEFAULT '1',
    is_deleted    SMALLINT      NOT NULL DEFAULT 0,
    create_by     VARCHAR(100)  NOT NULL DEFAULT 'system',
    update_by     VARCHAR(100)  NOT NULL DEFAULT 'system',
    create_time   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_ecos_kb_market_asset PRIMARY KEY (id),
    CONSTRAINT ck_ecos_kb_mkt_rating CHECK (rating >= 0 AND rating <= 5)
);
CREATE INDEX IF NOT EXISTS idx_ecos_kb_mkt_cat ON ecos_knowledge.ecos_kb_market_asset(asset_category, status, is_deleted);

-- ── 6. ecos_kb_access_request ← 访问申请（旧 ecos_marketplace_access_request/access_request：BIGSERIAL）──
CREATE TABLE IF NOT EXISTS ecos_knowledge.ecos_kb_access_request (
    id             VARCHAR(36)  NOT NULL,
    asset_id       VARCHAR(36)  NOT NULL,                 -- 指 ecos_kb_market_asset.id（同 schema 弱关联，不建 FK 承旧风格）
    request_reason TEXT,
    applicant_name VARCHAR(100),
    status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    approve_by     VARCHAR(100),
    approve_time   TIMESTAMP,
    trace_id       VARCHAR(64),
    domain         VARCHAR(50)  NOT NULL DEFAULT 'default',
    version_no     VARCHAR(20)  NOT NULL DEFAULT '1',
    is_deleted     SMALLINT     NOT NULL DEFAULT 0,
    create_by      VARCHAR(100) NOT NULL DEFAULT 'system',
    update_by      VARCHAR(100) NOT NULL DEFAULT 'system',
    create_time    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_ecos_kb_access_request PRIMARY KEY (id),
    CONSTRAINT ck_ecos_kb_areq_status CHECK (status IN ('PENDING','APPROVED','REJECTED'))
);
CREATE INDEX IF NOT EXISTS idx_ecos_kb_areq_asset ON ecos_knowledge.ecos_kb_access_request(asset_id, status, is_deleted);

-- ── 7. 旧两侧停写登记（COMMENT，禁 ALTER 旧表；与 V183 台账呼应）──
DO $$
DECLARE
    pairs RECORD;
BEGIN
    FOR pairs IN SELECT table_name, converge_to FROM ecos_knowledge.v_kb_mirror_registry LOOP
        IF EXISTS (SELECT 1 FROM information_schema.tables
                   WHERE table_schema='public' AND table_name=pairs.table_name) THEN
            EXECUTE format('COMMENT ON TABLE public.%I IS %L', pairs.table_name,
                'knownLegacy(V184/MC01): PK 宽度 36↔64/BIGSERIAL 违规，新写切 ' || pairs.converge_to || '（VARCHAR(36) 统一）；本表只停写不迁（R-1 a + IR03 零 DROP）');
        END IF;
        IF EXISTS (SELECT 1 FROM information_schema.tables
                   WHERE table_schema='ecos_knowledge' AND table_name=pairs.table_name) THEN
            EXECUTE format('COMMENT ON TABLE ecos_knowledge.%I IS %L', pairs.table_name,
                'knownLegacy(V184/MC01): 宽度违规存量侧（0 行），新写切 ' || pairs.converge_to || '；只停写不 DROP（R-12 ① 前置未闭环）');
        END IF;
    END LOOP;
END $$;

-- ── 回滚说明 ────────────────────────────────────────────────
-- 新表零行（本批不迁数据）；回滚 = 应用侧不切写 + 保留空表（IR03）。幂等：CREATE ... IF NOT EXISTS + COMMENT 重复安全。

-- ── DDL Lint Self-audit ──────────────────────────────────
-- [DR01] schema 限定 ✓  [DR02] 新表 ecos_ 前缀 ✓  [DR03] 单数 ✓  [DR04] JSON/text[] 全部 _json TEXT ✓
-- [DR05] is_deleted SMALLINT ✓  [DR06] 审计四列 ✓  [DR07] version_no VARCHAR(20) NOT NULL ✓
-- [DR08] domain VARCHAR(50) NOT NULL DEFAULT 'default' ✓（业务 domain 混用列已改名 subject_domain）
-- [MC01] 6 表 PK 全部 VARCHAR(36) 无默认值，零 SERIAL/零 gen_random_uuid ✓
-- [MC02] 零 JSONB；数值列全部 NUMERIC(p,s)（weight/rating NUMERIC(9,6)）✓
-- [MC03] 零 partial index（唯一键均含 is_deleted）/ 零 text[] / 零 timestamptz / 零 `::` / 零 CREATE POLICY ✓
-- [ST07] 全部落 ecos_knowledge ✓  无跨 schema FOREIGN KEY ✓  [IR03] 旧两侧仅 COMMENT，零 DROP ✓
