-- ============================================================
-- V122__ecos_ontology_schema_converge.sql
-- Wave D T20 双套本体表 schema 收敛（登记 + 幂等公开兜底）
-- 来源: 全栈开发工程师 / 日期: 2026-09-13
--
-- §调查结论 (实证于 sys_man 库, 2026-09-13)
-- ------------------------------------------------------------
-- 一、双套并存真相:
--   1. ecos_ontology schema (V47__ecos_schema_isolation.sql 建的) 下的
--      6 张表均为 **0 行 ghost** (V47 上生产建库时机早于/晚于
--      public 侧实际写入时机, 数据 100% 落在 public):
--      ecos_ontology_entity/property/relationship/action/rule/version
--      → count(*) 各自 = 0
--   2. public schema 下同名 6 张表 **有真实数据**:
--      entity=15  rows, ontology=8 rows, rule=1 row, ...
--   3. V47 的 `ALTER DATABASE sys_man SET search_path TO
--      ecos_ontology, ..., public` 在本库未生效 (pg_database.datsearch_path
--      为默认, session 级 search_path = "$user", public), 代码运行时
--      无 schema 前缀 SQL 全部回退命中 public.
--   4. 代码 has_url 审视 (T20 Step1):
--      - `git grep -ni "ecos_ontology\." ecos_backend/engine/ontology-engine/`
--        = **0 命中** — 无 Java/XML 打 `ecos_ontology.` schema 前缀。
--      - 唯一显式 `public.ecos_ontology_data` 前缀 9 处位于
--        OntologyDataController (V119 表), 这是 public 侧显式声明,
--        非别名引用, 无需改。
--   5. 附加 V47 遗留双套: `ecos_domain` / `ecos_business_glossary`
--      同时存在于 public 与 ecos_ontology 两个 schema (alias 侧 0 行)。
--
-- 二、代码实际命中:
--   全部 repo/DAO SQL 无 schema 前缀 → search_path 回退 public 侧命中;
--   `public.ecos_ontology_data` 显式带前缀 → 同样命中 public 侧。
--   ⇒ **别名 schema 侧为沉默 ghost (silent ghost)** — 无数据、无代码引用,
--      不影响主业务读一致性。
--
-- §收敛策略 (红线下最小动作)
-- ------------------------------------------------------------
-- 只加不删铁律 3.1: 不 DROP / 不 ALTER 已有列 / 不 ALTER TABLE 位移;
-- 也不 UPSERT/复制数据。V122 只做 3 件事:
--   1. **登记 (COMMIT) alias ghost 状态** — 给 ecos_ontology schema
--      下 6 张 ghost 表加 COMMENT, 标注 "历史 V47 产物, 无数据,
--      未来清理前需走 PMO 专项".
--   2. **幂等 public 兜底 DDL** — 对未来全新库 (V47 通过的
--      SET SCHEMA 未命中 IF EXISTS 空表场景) 提供同构 public 侧
--      最小兜底列 (is_deleted / status / update_by), 全部
--      `ADD COLUMN IF NOT EXISTS` 幂等, 对现有库 no-op.
--   3. **不建 alias 兼容视图** — 因 public 是 canonical, alias 是
--      空 ghost, 反向 view 无数据位置, 无 need (PMO Step2
--      "data 在别名 schema" 的分支不适用).
--
-- §代码去前缀 N=0
--   grep `ecos_ontology\.` (带 schema 前缀) 在 backend 全域 0 命中,
--   无需修改任何 Java/XML 文件; 本 Task 不触 T12-T19 已提交代码.
--
-- 幂等性: 全部 DDL 用 IF NOT EXISTS / IF EXISTS, 重复执行无副作用.
--   (2026-09-13 最终闸门 P1-1 修订: PostgreSQL 的 COMMENT ON TABLE **不支持**
--   IF EXISTS 子句 — PG 16 实测 `COMMENT ON TABLE IF EXISTS ...` 直接报
--   "syntax error at or near EXISTS". 本脚本原 (a) 6 条裸
--   `COMMENT ON TABLE ecos_ontology.xxx` (ghost 表缺失时整脚本直接中断,
--   §2 public 侧 DDL 未跑) 与 (b) 2 条 `COMMENT ON TABLE IF EXISTS ...`
--   (非法语法, 即便对存在的表也解析失败) 均不可用. 现将 8 条 COMMENT 统一
--   收口为 **单 DO 块 + 每表各一段 IF EXISTS (方案 A)**: 先查 pg_class
--   确认表存在才 EXECUTE COMMENT, ghost 表缺失时静默跳过、整脚本零报错,
--   表均存在则登记 (含原 2 条非法 `COMMENT ON TABLE IF EXISTS` 的
--   ecos_domain / ecos_business_glossary). 注: 方案 B (DO 块 + VALUES
--   FOR 循环) 因 `FOR t IN SELECT 单列` 触发 PL/pgSQL "loop variable of
--   loop over rows must be a record variable or list of scalar variables"
--   实测不可用, 故弃 B 用 A. 已 PG 16 实证 (ghost 缺失 / 存在两分支).)

-- ============================================================
-- §1. alias ghost 侧 COMMENT 登记 (不 DROP / 不清数据)
-- ============================================================
-- 目的: 给未来清理/审计的开发者留下"该表为 V47 遗留 ghost、
-- 无数据、canonical 在 public"的显式标记, 避免误以为该侧有业务数据.
-- [P1-1] 8 张表 COMMENT 收口为单 DO 块 + 每表各一段 IF EXISTS (方案 A,
--   一张表一段): 先查 pg_class 确认表存在才 EXECUTE COMMENT, ghost 表
--   缺失时静默跳过、整脚本零报错; 表均存在则覆盖登记 (含原 2 条非法
--   `COMMENT ON TABLE IF EXISTS` 的 ecos_domain / ecos_business_glossary).
--   全部 8 张: ontology 6 张建表 + V47 搬移 2 张 (ecos_domain /
--   ecos_business_glossary). 已 PG 16 实测 (ghost 缺失 / 存在两分支).

DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
             WHERE n.nspname = 'ecos_ontology' AND c.relname = 'ecos_ontology_entity' AND c.relkind IN ('r', 'v')) THEN
    EXECUTE format('COMMENT ON TABLE ecos_ontology.%I IS %L', 'ecos_ontology_entity',
                   'WAVE_D_T20: V47 建, 0 行 ghost. Canonical = public.ecos_ontology_entity. 未来清理需走 PMO 专项 drop.');
  END IF;

  IF EXISTS (SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
             WHERE n.nspname = 'ecos_ontology' AND c.relname = 'ecos_ontology_property' AND c.relkind IN ('r', 'v')) THEN
    EXECUTE format('COMMENT ON TABLE ecos_ontology.%I IS %L', 'ecos_ontology_property',
                   'WAVE_D_T20: V47 建, 0 行 ghost. Canonical = public.ecos_ontology_property. 未来清理需走 PMO 专项 drop.');
  END IF;

  IF EXISTS (SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
             WHERE n.nspname = 'ecos_ontology' AND c.relname = 'ecos_ontology_relationship' AND c.relkind IN ('r', 'v')) THEN
    EXECUTE format('COMMENT ON TABLE ecos_ontology.%I IS %L', 'ecos_ontology_relationship',
                   'WAVE_D_T20: V47 建, 0 行 ghost. Canonical = public.ecos_ontology_relationship. 未来清理需走 PMO 专项 drop.');
  END IF;

  IF EXISTS (SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
             WHERE n.nspname = 'ecos_ontology' AND c.relname = 'ecos_ontology_action' AND c.relkind IN ('r', 'v')) THEN
    EXECUTE format('COMMENT ON TABLE ecos_ontology.%I IS %L', 'ecos_ontology_action',
                   'WAVE_D_T20: V47 建, 0 行 ghost. Canonical = public.ecos_ontology_action. 未来清理需走 PMO 专项 drop.');
  END IF;

  IF EXISTS (SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
             WHERE n.nspname = 'ecos_ontology' AND c.relname = 'ecos_ontology_rule' AND c.relkind IN ('r', 'v')) THEN
    EXECUTE format('COMMENT ON TABLE ecos_ontology.%I IS %L', 'ecos_ontology_rule',
                   'WAVE_D_T20: V47 建, 0 行 ghost. Canonical = public.ecos_ontology_rule. 未来清理需走 PMO 专项 drop.');
  END IF;

  IF EXISTS (SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
             WHERE n.nspname = 'ecos_ontology' AND c.relname = 'ecos_ontology_version' AND c.relkind IN ('r', 'v')) THEN
    EXECUTE format('COMMENT ON TABLE ecos_ontology.%I IS %L', 'ecos_ontology_version',
                   'WAVE_D_T20: V47 建, 0 行 ghost. Canonical = public.ecos_ontology_version. 未来清理需走 PMO 专项 drop.');
  END IF;

  IF EXISTS (SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
             WHERE n.nspname = 'ecos_ontology' AND c.relname = 'ecos_domain' AND c.relkind IN ('r', 'v')) THEN
    EXECUTE format('COMMENT ON TABLE ecos_ontology.%I IS %L', 'ecos_domain',
                   'WAVE_D_T20: V47 搬, 0 行 ghost. Canonical = public.ecos_domain. 未来清理需走 PMO 专项 drop.');
  END IF;

  IF EXISTS (SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
             WHERE n.nspname = 'ecos_ontology' AND c.relname = 'ecos_business_glossary' AND c.relkind IN ('r', 'v')) THEN
    EXECUTE format('COMMENT ON TABLE ecos_ontology.%I IS %L', 'ecos_business_glossary',
                   'WAVE_D_T20: V47 搬, 0 行 ghost. Canonical = public.ecos_business_glossary. 未来清理需走 PMO 专项 drop.');
  END IF;
END $$;



-- ============================================================
-- §2. public 侧幂等兜底 (CREATE TABLE IF NOT EXISTS / ADD COLUMN IF NOT EXISTS)
-- ============================================================
-- 目的:
--   - 若未来环境曾完整跑过 V47 SET SCHEMA 且 public 侧 6 表已被搬走,
--     此处 CREATE TABLE IF NOT EXISTS 在 public 侧重建同构空表,
--     立刻加上一致性逻辑删除列 (is_deleted / status / update_by);
--   - 对现有库 (public 6 表都在), 全部 DDL 幂等 no-op.
--
-- 列取值以 V3/V8 原始 DDL + V120 已补列 为基准锚点.
-- 不含 PK 重建 (PRINCIPAL KEY 已建则 IF NOT EXISTS no-op).

-- 2.1 ecos_ontology_entity (V3 已建 public, V120 已补 is_deleted/status/update_by)
--     全部 IF NOT EXISTS 幂等 no-op, 对现有库零修改 (现有库 public 6 表已实体存在, 历史
--     顺带列由 V120 维护, 此处仅对未来全新库建 public 锚点)。
CREATE TABLE IF NOT EXISTS public.ecos_ontology_entity (
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

ALTER TABLE public.ecos_ontology_entity ADD COLUMN IF NOT EXISTS is_deleted SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE public.ecos_ontology_entity ADD COLUMN IF NOT EXISTS status     VARCHAR(32) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE public.ecos_ontology_entity ADD COLUMN IF NOT EXISTS update_by  VARCHAR(128);

-- 2.2 ecos_ontology_property
CREATE TABLE IF NOT EXISTS public.ecos_ontology_property (
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

ALTER TABLE public.ecos_ontology_property ADD COLUMN IF NOT EXISTS is_deleted SMALLINT    NOT NULL DEFAULT 0;
ALTER TABLE public.ecos_ontology_property ADD COLUMN IF NOT EXISTS status     VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE public.ecos_ontology_property ADD COLUMN IF NOT EXISTS update_by  VARCHAR(128);

-- 2.3 ecos_ontology_relationship
CREATE TABLE IF NOT EXISTS public.ecos_ontology_relationship (
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

ALTER TABLE public.ecos_ontology_relationship ADD COLUMN IF NOT EXISTS is_deleted SMALLINT    NOT NULL DEFAULT 0;
ALTER TABLE public.ecos_ontology_relationship ADD COLUMN IF NOT EXISTS status     VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE public.ecos_ontology_relationship ADD COLUMN IF NOT EXISTS update_by  VARCHAR(128);

-- 2.4 ecos_ontology_action (V8 已建 public, 状态/预置列完整)
--     V120 已补 is_deleted; update_by 在此再幂等兜底 (若 V120 未跑从 环境)
ALTER TABLE public.ecos_ontology_action ADD COLUMN IF NOT EXISTS is_deleted SMALLINT    NOT NULL DEFAULT 0;
ALTER TABLE public.ecos_ontology_action ADD COLUMN IF NOT EXISTS update_by  VARCHAR(128);

-- 2.5 ecos_ontology_rule (V120 已建 public 锚点, 完整 6 基线列)
--     IF NOT EXISTS no-op; 如 V120 缺位本行 CREATE 生效.
CREATE TABLE IF NOT EXISTS public.ecos_ontology_rule (
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

-- 2.6 ecos_ontology_version (V120 已建 public 锚点, 完整 6 基线列)
CREATE TABLE IF NOT EXISTS public.ecos_ontology_version (
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

-- 2.7 ecos_ontology (主本体表 — V120 已补 is_deleted/status/update_by)
ALTER TABLE public.ecos_ontology ADD COLUMN IF NOT EXISTS is_deleted SMALLINT    NOT NULL DEFAULT 0;
ALTER TABLE public.ecos_ontology  ADD COLUMN IF NOT EXISTS status     VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE public.ecos_ontology  ADD COLUMN IF NOT EXISTS update_by  VARCHAR(128);
ALTER TABLE public.ecos_ontology  ADD COLUMN IF NOT EXISTS tenant_id  VARCHAR(32);


-- ============================================================
-- §3. 登记说明: alias schema ghost 未来清理路径 (仅注释, 不 DROP)
-- ============================================================
-- 本脚本 **不 DROP、不搬移、不复制数据** — 严格守 PMO 铁律 3.1.
-- 未来若要 drop ecos_ontology.* alias 6 张 + 2 张 ghost 表,
-- 需先:
--   (a) 恢复 V47 search_path 后确认全后端 SQL 无 schema 引用 —
--       本次 T20 已完成, 代码全域无 `ecos_ontology.` 前缀;
--   (b) 后端 curl + E2E 验证 1 个 full-cycle 不回归;
--   (c) 提交 PMO 专项 `DROP TABLE ecos_ontology.ecos_ontology_*`
--       (不可在本脚本 DROP, 避免跨版回滚风险).
--
-- 交付凭证:
--   V122__ecos_ontology_schema_converge.sql — 本文件
--   代码去前缀 N=0 (grep 全域 0 命中)
--   public 侧列补齐幂等 no-op (对现有库零修改)
--   alias 侧 COMMENT 登记 8 张 ghost (6 ontology + ecos_domain + ecos_business_glossary)
