-- Wave B-3 T17 本体 4 表兼底 + 逻辑删除列补全
-- 来源: 肖国荣 / 日期: 2026-09-12 / 责任人: fullstack-implementer
-- 工件: V120__ecos_ontology_wave31_fallback.sql (Flyway 路径, 在 V119 之后)
--
-- 背景: Wave31 的 repository 层 delete* 方法原 SQL 为 DELETE FROM (物理删除),
--       T17 统一改造为逻辑删除 (UPDATE SET is_deleted=1, status='ARCHIVED')。
--       下列操作需表结构支持:
--       1. 3 个 repository 引用的表 (rule/version/domain) 在历史 migration 中
--          **从未建表** (代码引用但无 schema) — 必须 CREATE 兜底, 含 6 基线列
--       2. entity/property/relationship/action 表已有 schema (V3/V8) 但**缺**
--          is_deleted 列 (及 entity/property/relationship 缺 status 列) —
--          逻辑删除 UPDATE 需要这两列, 必须 ALTER 补齐
--
-- 幂等性: 所有 DDL 用 IF NOT EXISTS, 重复执行不报错不覆盖数据
-- Schema 铁律: 只加不删 (铁律 3.1)
-- 6 基线列规范 (后端规范 §六): id/create_time/update_time/create_by/update_by/is_deleted

-- ═══════════════ 1. 建 3 个从未建表的仓库表 (含 6 基线列) ═══════════════

-- 规则表 (OntologyRuleRepository 引用, 历史无 schema)
CREATE TABLE IF NOT EXISTS public.ecos_ontology_rule (
    id              VARCHAR(64)   PRIMARY KEY,
    entity_id       VARCHAR(64)   NOT NULL,
    code            VARCHAR(255)  NOT NULL,
    name            VARCHAR(255)  NOT NULL,
    rule_type       VARCHAR(64)   DEFAULT 'BINARY',
    expression      TEXT          DEFAULT '',
    action          VARCHAR(255)  DEFAULT '',
    priority        INTEGER       DEFAULT 100,
    enabled         SMALLINT      DEFAULT 1,
    description     TEXT          DEFAULT '',
    status          VARCHAR(32)   NOT NULL DEFAULT 'ACTIVE',
    create_time     TIMESTAMP     DEFAULT now(),
    update_time     TIMESTAMP     DEFAULT now(),
    create_by       VARCHAR(128),
    update_by       VARCHAR(128),
    is_deleted      SMALLINT      NOT NULL DEFAULT 0
);

-- 版本表 (OntologyVersionRepository 引用, 历史无 schema)
CREATE TABLE IF NOT EXISTS public.ecos_ontology_version (
    id              VARCHAR(64)   PRIMARY KEY,
    ontology_id     VARCHAR(64)   NOT NULL,
    version_no      VARCHAR(32)   NOT NULL,
    status          VARCHAR(32)   NOT NULL DEFAULT 'DRAFT',
    snapshot        TEXT,
    change_log      TEXT          DEFAULT '',
    publisher       VARCHAR(128),
    published_at    TIMESTAMP,
    create_time     TIMESTAMP     DEFAULT now(),
    update_time     TIMESTAMP     DEFAULT now(),
    create_by       VARCHAR(128),
    update_by       VARCHAR(128),
    is_deleted      SMALLINT      NOT NULL DEFAULT 0
);

-- 域表 (OntologyDomainRepository 引用, 历史命名为 ecos_domain, 此处为显式 canonical name
-- 兜底 — 历史 V32/V47 等可能已建 ecos_domain, IF NOT EXISTS 幂等无冲突;
-- 若历史未建则本兜底生效, 业务代码按现名引用)
CREATE TABLE IF NOT EXISTS public.ecos_ontology_domain (
    id              VARCHAR(64)   PRIMARY KEY,
    code            VARCHAR(128)  NOT NULL UNIQUE,
    name            VARCHAR(255)  NOT NULL,
    owner           VARCHAR(128),
    description     TEXT          DEFAULT '',
    status          VARCHAR(32)   NOT NULL DEFAULT 'ACTIVE',
    sort_order      INTEGER       DEFAULT 1,
    tenant_id       VARCHAR(64),
    create_time     TIMESTAMP     DEFAULT now(),
    update_time     TIMESTAMP     DEFAULT now(),
    create_by       VARCHAR(128),
    update_by       VARCHAR(128),
    is_deleted      SMALLINT      NOT NULL DEFAULT 0
);

-- ═══════════════ 2. 已有表补逻辑删除列 (ALTER TABLE ADD COLUMN IF NOT EXISTS) ═══════════════
-- 铁律 3.1 "只加不删": 仅新增列, 不动已有列与数据
-- 逻辑删除统一字段: is_deleted (SMALLINT, 默认 0)
-- T17 UPDATE SET is_deleted=1 需要该列; status='ARCHIVED' 需要 status 列 (无则补)

-- ecos_ontology_entity (V3 已有, 无 is_deleted / status)
ALTER TABLE public.ecos_ontology_entity ADD COLUMN IF NOT EXISTS is_deleted SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE public.ecos_ontology_entity ADD COLUMN IF NOT EXISTS status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE';

-- ecos_ontology_property (V3 已有, 无 is_deleted / status)
ALTER TABLE public.ecos_ontology_property ADD COLUMN IF NOT EXISTS is_deleted SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE public.ecos_ontology_property ADD COLUMN IF NOT EXISTS status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE';

-- ecos_ontology_relationship (V3 已有, 无 is_deleted / status)
ALTER TABLE public.ecos_ontology_relationship ADD COLUMN IF NOT EXISTS is_deleted SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE public.ecos_ontology_relationship ADD COLUMN IF NOT EXISTS status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE';

-- ecos_ontology_action (V8 已有, 无 is_deleted, 已有 status)
ALTER TABLE public.ecos_ontology_action ADD COLUMN IF NOT EXISTS is_deleted SMALLINT NOT NULL DEFAULT 0;

-- ecos_ontology (主本体表, 无 is_deleted / status — 见 OntologyRepository.findAllOntologies
-- 已查 status 列, 说明表已有 status, 此处 IF NOT EXISTS 幂等兜底; is_deleted 必加)
ALTER TABLE public.ecos_ontology ADD COLUMN IF NOT EXISTS is_deleted SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE public.ecos_ontology ADD COLUMN IF NOT EXISTS status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE';

-- ═══════════════ 3. update_by 列补全 (逻辑删除 SQL 带 update_by=?) ═══════════════
-- T17 UPDATE SET update_time=now(), update_by=? — 表须有 update_by 列
-- 历史表 (V3/V8) 用 created_at/updated_at 命名(非 create_time/update_time 6 基线列名),
-- 本脚本不重命名列 (铁律 3.1 只加不删), 仅在缺 update_by 的表补 update_by

ALTER TABLE public.ecos_ontology_entity ADD COLUMN IF NOT EXISTS update_by VARCHAR(128);
ALTER TABLE public.ecos_ontology_property ADD COLUMN IF NOT EXISTS update_by VARCHAR(128);
ALTER TABLE public.ecos_ontology_relationship ADD COLUMN IF NOT EXISTS update_by VARCHAR(128);
ALTER TABLE public.ecos_ontology_action ADD COLUMN IF NOT EXISTS update_by VARCHAR(128);
ALTER TABLE public.ecos_ontology ADD COLUMN IF NOT EXISTS update_by VARCHAR(128);
