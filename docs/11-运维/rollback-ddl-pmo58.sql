-- ============================================================
-- PMO-58 T4: 回滚 DDL 留档 (可回滚前置, 不落运行库)
-- 来源: 肖国荣 / 日期: 2026-09-13 / 责任人: fullstack-implementer
-- 授权 PMO: docs/04-本体/PMO-58-ecos-ontology-schema-ghost表物理清理.md
--
-- § 用途
--   V126 (见 db/migration/V126__ecos_ontology_schema_ghost_cleanup.sql) 对
--   sys_man.ecos_ontology schema 下 8 张 ghost 表执行不可逆 DROP.
--   本文件提供:
--     (a) 8 张表原始建表 DDL (从 V3/V8 + V47/V120 基线删出, 字段/6 基线列全等)
--     (b) 数据快照语句 `INSERT INTO ... SELECT FROM public.*` (本 PMO 前
--         提为 8 表空集; 若 DROP 前已写入则自动携带, 留档供误操作恢复)
--     (c) schema 保留声明 (DROP 8 表后 ecos_ontology schema 仍存在, 因
--         17 张非 ghost 活表, 见 PMO-58 前置决策 2)
--
-- § 调用方式 (仅在 V126 误 DROP 后 ВЫПОЛНЯТЬ 此脚本 CASE-BY-CASE)
--   docker exec ecos-postgres psql -U postgres -d sys_man \
--     -v ON_ERROR_STOP=1 -f /tmp/rollback-ddl-pmo58.sql
--   执行后重新跑 V122 幂等段补齐 COMMENT 登记
--
-- § 红线 (对应 PMO-58 禁止清单)
--   ① 本脚本 **不** 触及 public schema 任何对象
--   ② 本脚本 **不** DROP 任何表 (纯 CREATE)
--   ③ 数据恢复语句基于 IF NOT EXISTS + ON CONFLICT DO NOTHING 幂等
-- ============================================================

-- ============ (a) 8 张表原始 DDL (源 V3 / V8 / V120) ============

-- 1. ecos_ontology.ecos_ontology_entity  (source: V3__ecos_ontology.sql + V120 补列)
CREATE TABLE IF NOT EXISTS ecos_ontology.ecos_ontology_entity (
    id              VARCHAR(36)  PRIMARY KEY,
    ontology_id     VARCHAR(36)  NOT NULL,
    code            VARCHAR(100) NOT NULL,
    name            VARCHAR(200) NOT NULL,
    description     TEXT,
    entity_type     VARCHAR(50),
    sort_order      INTEGER,
    created_at      TIMESTAMP,
    updated_at      TIMESTAMP,
    domain_id       VARCHAR(50),
    tenant_id       VARCHAR(32),
    is_deleted      SMALLINT     NOT NULL DEFAULT 0,
    status          VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    update_by       VARCHAR(128)
);

-- 2. ecos_ontology.ecos_ontology_property (source: V3__ecos_ontology.sql + V120 补列)
CREATE TABLE IF NOT EXISTS ecos_ontology.ecos_ontology_property (
    id                  VARCHAR(36)  PRIMARY KEY,
    entity_id           VARCHAR(36)  NOT NULL,
    code                VARCHAR(100) NOT NULL,
    name                VARCHAR(200),
    property_type       VARCHAR(50),
    required_flag       INTEGER,
    searchable_flag     INTEGER,
    sort_order          INTEGER,
    created_at          TIMESTAMP,
    function_type       VARCHAR(32),
    function_expression TEXT,
    is_deleted          SMALLINT     NOT NULL DEFAULT 0,
    status              VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    update_by           VARCHAR(128)
);

-- 3. ecos_ontology.ecos_ontology_relationship (source: V3__ecos_ontology.sql + V120 补列)
CREATE TABLE IF NOT EXISTS ecos_ontology.ecos_ontology_relationship (
    id               VARCHAR(36)  PRIMARY KEY,
    source_entity_id VARCHAR(36)  NOT NULL,
    target_entity_id VARCHAR(36)  NOT NULL,
    code             VARCHAR(100),
    name             VARCHAR(200),
    relationship_type VARCHAR(50),
    created_at       TIMESTAMP,
    is_deleted       SMALLINT     NOT NULL DEFAULT 0,
    status           VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    update_by        VARCHAR(128)
);

-- 4. ecos_ontology.ecos_ontology_action (source: V8__ecos_ontology_action.sql + V120 补列)
--    补 note: V8 原始列含 action_type/rule_json/strategy; 本部署侧运行库表实际
--    字段经 V120/V122 收敛后 与 ontology-impl 仓库 OntologyAction 实体对齐 (id/entity_id/
--    name/action_type/rule_json 4 头 + 6 基线列). 回滚 DDL 以运行库实际 schema 为准.
CREATE TABLE IF NOT EXISTS ecos_ontology.ecos_ontology_action (
    id              VARCHAR(64)  PRIMARY KEY,
    entity_id       VARCHAR(64)  NOT NULL,
    name            VARCHAR(255) NOT NULL,
    action_type     VARCHAR(64)  NOT NULL DEFAULT 'CUSTOM',
    rule_json       TEXT         DEFAULT '',
    strategy        VARCHAR(255) DEFAULT '',
    status          VARCHAR(32)  DEFAULT 'ACTIVE',
    created_at      TIMESTAMP    DEFAULT NOW(),
    updated_at      TIMESTAMP    DEFAULT NOW(),
    is_deleted      SMALLINT     NOT NULL DEFAULT 0,
    update_by       VARCHAR(128)
);

-- 5. ecos_ontology.ecos_ontology_rule (source: V120__ecos_ontology_wave31_fallback.sql)
CREATE TABLE IF NOT EXISTS ecos_ontology.ecos_ontology_rule (
    id              VARCHAR(64)  PRIMARY KEY,
    entity_id       VARCHAR(64)  NOT NULL,
    code            VARCHAR(255) NOT NULL,
    name            VARCHAR(255) NOT NULL,
    rule_type       VARCHAR(64)  DEFAULT 'BINARY',
    expression      TEXT         DEFAULT '',
    action          VARCHAR(255) DEFAULT '',
    priority        INTEGER      DEFAULT 100,
    enabled         SMALLINT     DEFAULT 1,
    description     TEXT         DEFAULT '',
    status          VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    create_time     TIMESTAMP    DEFAULT now(),
    update_time     TIMESTAMP    DEFAULT now(),
    create_by       VARCHAR(128),
    update_by       VARCHAR(128),
    is_deleted      SMALLINT     NOT NULL DEFAULT 0
);

-- 6. ecos_ontology.ecos_ontology_version (source: V120__ecos_ontology_wave31_fallback.sql)
CREATE TABLE IF NOT EXISTS ecos_ontology.ecos_ontology_version (
    id              VARCHAR(64)  PRIMARY KEY,
    ontology_id     VARCHAR(64)  NOT NULL,
    version_no      VARCHAR(32)  NOT NULL,
    status          VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',
    snapshot        TEXT,
    change_log      TEXT         DEFAULT '',
    publisher       VARCHAR(128),
    published_at    TIMESTAMP,
    create_time     TIMESTAMP    DEFAULT now(),
    update_time     TIMESTAMP    DEFAULT now(),
    create_by       VARCHAR(128),
    update_by       VARCHAR(128),
    is_deleted      SMALLINT     NOT NULL DEFAULT 0
);

-- 7. ecos_ontology.ecos_domain (source: 历史 V 系列 + V47 搬入; T1 实证 0 行)
--    canonical 侧 = public.ecos_domain (T1 实证 exists=true, 行数在 T1 快照)
CREATE TABLE IF NOT EXISTS ecos_ontology.ecos_domain (
    id              VARCHAR(64)  PRIMARY KEY,
    code            VARCHAR(128) NOT NULL UNIQUE,
    name            VARCHAR(255) NOT NULL,
    owner           VARCHAR(128),
    description     TEXT         DEFAULT '',
    status          VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    sort_order      INTEGER      DEFAULT 1,
    tenant_id       VARCHAR(64),
    create_time     TIMESTAMP    DEFAULT now(),
    update_time     TIMESTAMP    DEFAULT now(),
    create_by       VARCHAR(128),
    update_by       VARCHAR(128),
    is_deleted      SMALLINT     NOT NULL DEFAULT 0
);

-- 8. ecos_ontology.ecos_business_glossary (source: 历史 V 系列 + V122)
CREATE TABLE IF NOT EXISTS ecos_ontology.ecos_business_glossary (
    id                 BIGSERIAL  PRIMARY KEY,
    term               VARCHAR(255) NOT NULL UNIQUE,
    definition         TEXT         NOT NULL,
    synonyms           JSONB        DEFAULT '{}',
    domain_id          VARCHAR(64),
    status             VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    create_time        TIMESTAMP    NOT NULL DEFAULT now(),
    update_time        TIMESTAMP    NOT NULL DEFAULT now(),
    create_by          VARCHAR(128) DEFAULT 'system',
    update_by          VARCHAR(128) DEFAULT 'system',
    is_deleted         SMALLINT     NOT NULL DEFAULT 0
);

-- ============ (b) 数据快照段 (本 PMO 前 提为 空集) ============
-- 真前提: 8 表 T1 前 8/8 count=0. 下列 INSERT 语句在 alias 侧 0 行
--         前提下为 no-op, 留档供"V126 执行前 T1↔T2 之间出现新写入" 的
--         兜底误操作恢复: 按 canonical 侧 SELECT 直接灌回 alias 侧.
-- CANONICAL 映射对照 (T20 共识, 见 V122 COMMENT):
--   ecos_ontology.ecos_ontology_entity        ↔ public.ecos_ontology_entity
--   ecos_ontology.ecos_ontology_property      ↔ public.ecos_ontology_property
--   ecos_ontology.ecos_ontology_relationship  ↔ public.ecos_ontology_relationship
--   ecos_ontology.ecos_ontology_action        ↔ public.ecos_ontology_action
--   ecos_ontology.ecos_ontology_rule          ↔ public.ecos_ontology_rule
--   ecos_ontology.ecos_ontology_version       ↔ public.ecos_ontology_version
--   ecos_ontology.ecos_domain                 ↔ public.ecos_domain
--   ecos_ontology.ecos_business_glossary      ↔ public.ecos_business_glossary

-- 1-6. 本体定义族 6 表 (V122 已证 canonical 侧同名字段全等, 一 SELECT 覆盖)
INSERT INTO ecos_ontology.ecos_ontology_entity (id, ontology_id, code, name, description,
    entity_type, sort_order, created_at, updated_at, domain_id, tenant_id,
    is_deleted, status, update_by)
SELECT id, ontology_id, code, name, description,
    entity_type, sort_order, created_at, updated_at, domain_id, tenant_id,
    is_deleted, status, update_by
FROM public.ecos_ontology_entity
ON CONFLICT (id) DO NOTHING;

INSERT INTO ecos_ontology.ecos_ontology_property (id, entity_id, code, name, property_type,
    required_flag, searchable_flag, sort_order, created_at,
    function_type, function_expression, is_deleted, status, update_by)
SELECT id, entity_id, code, name, property_type,
    required_flag, searchable_flag, sort_order, created_at,
    function_type, function_expression, is_deleted, status, update_by
FROM public.ecos_ontology_property
ON CONFLICT (id) DO NOTHING;

INSERT INTO ecos_ontology.ecos_ontology_relationship (id, source_entity_id, target_entity_id,
    code, name, relationship_type, created_at, is_deleted, status, update_by)
SELECT id, source_entity_id, target_entity_id,
    code, name, relationship_type, created_at, is_deleted, status, update_by
FROM public.ecos_ontology_relationship
ON CONFLICT (id) DO NOTHING;

INSERT INTO ecos_ontology.ecos_ontology_action (id, entity_id, name,
    action_type, rule_json, strategy, status, created_at, updated_at,
    is_deleted, update_by)
SELECT id, entity_id, name,
    action_type, rule_json, strategy, status, created_at, updated_at,
    is_deleted, update_by
FROM public.ecos_ontology_action
ON CONFLICT (id) DO NOTHING;

INSERT INTO ecos_ontology.ecos_ontology_rule (id, entity_id, code, name,
    rule_type, expression, action, priority, enabled, description,
    status, create_time, update_time, create_by, update_by, is_deleted)
SELECT id, entity_id, code, name,
    rule_type, expression, action, priority, enabled, description,
    status, create_time, update_time, create_by, update_by, is_deleted
FROM public.ecos_ontology_rule
ON CONFLICT (id) DO NOTHING;

INSERT INTO ecos_ontology.ecos_ontology_version (id, ontology_id, version_no,
    status, snapshot, change_log, publisher, published_at,
    create_time, update_time, create_by, update_by, is_deleted)
SELECT id, ontology_id, version_no,
    status, snapshot, change_log, publisher, published_at,
    create_time, update_time, create_by, update_by, is_deleted
FROM public.ecos_ontology_version
ON CONFLICT (id) DO NOTHING;

-- 7. alias: ecos_ontology.ecos_domain ← canonical: public.ecos_domain (T1 实证存在)
INSERT INTO ecos_ontology.ecos_domain (id, code, name, owner, description,
    status, sort_order, tenant_id, create_time, update_time,
    create_by, update_by, is_deleted)
SELECT id, code, name, owner, description,
    status, sort_order, tenant_id, create_time, update_time,
    create_by, update_by, is_deleted
FROM public.ecos_domain
ON CONFLICT (id) DO NOTHING;

-- 8. alias: ecos_ontology.ecos_business_glossary ← canonical: public.ecos_business_glossary
INSERT INTO ecos_ontology.ecos_business_glossary (id, term, definition, synonyms,
    domain_id, status, create_time, update_time,
    create_by, update_by, is_deleted)
SELECT id, term, definition, synonyms,
    domain_id, status, create_time, update_time,
    create_by, update_by, is_deleted
FROM public.ecos_business_glossary
ON CONFLICT (id) DO NOTHING;

-- ============================================================
-- § (c) 说明: schema 保留 (非回滚项)
--   PMO-58 T1 延伸核查证实 ecos_ontology 下 17 张活表, DROP SCHEMA
--   从未被纳入 V126 名单. 因此本回滚文档不需要 "recreate schema"
--   步骤 — 运行库的 ecos_ontology schema 在 V126 前后均存在.
-- ============================================================

-- ============================================================
-- § (d) 4 条外键回滚 (T3 中止留痕补充)
--   来源: 见 docs/10-审查证据/pmo-58/pmo58-abort-evidence.md
--   若 V126 已执行 DROP TABLE 且需要恢复 FK 约束 (含跨引擎的
--   wk_cognitive 侧), 按本段 ALTER ADD CONSTRAINT 重建. 列存在性
--   以 (a) 8 表 DDL 为准:
--     - 3 条 FK 附着 8 ghost 表之间: property→entity / entity→domain /
--       business_glossary→domain
--     - fk_cog_goal_domain 附着 ecos_cognitive.ecos_wm_goal (07 脚本侧建)
-- ============================================================
ALTER TABLE ecos_ontology.ecos_ontology_property
    ADD CONSTRAINT fk_onto_prop_ent
    FOREIGN KEY (entity_id) REFERENCES ecos_ontology.ecos_ontology_entity(id);

ALTER TABLE ecos_ontology.ecos_ontology_entity
    ADD CONSTRAINT fk_onto_ent_domain
    FOREIGN KEY (domain_id) REFERENCES ecos_ontology.ecos_domain(id);

ALTER TABLE ecos_ontology.ecos_business_glossary
    ADD CONSTRAINT fk_onto_gloss_domain
    FOREIGN KEY (domain_id) REFERENCES ecos_ontology.ecos_domain(id);

-- 以下 1 条 FK 属认知引擎 schema, 不在 PMO-58 8 表死锁范围内;
-- 回滚仅留档, 需 PMO 授权后手动执行:
ALTER TABLE ecos_cognitive.ecos_wm_goal
    ADD CONSTRAINT fk_cog_goal_domain
    FOREIGN KEY (domain_id) REFERENCES ecos_ontology.ecos_domain(id);
