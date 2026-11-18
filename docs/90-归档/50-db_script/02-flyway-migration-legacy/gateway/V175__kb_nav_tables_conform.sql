-- V175 (卷04 §E.2 逐字块): kb_nav_* 知识导航表合规形态（schema 限定 + MC01/02/03 + DR04~DR08 基线）
-- 追溯: K-19（kb_nav_* 缺失/伪 404）/ K-22（MC01 主键）/ K-23（MC02 JSON 形态）→ W106/C88、W107/C89；错误码 ECOS-KB-050
-- 批准: 需求检视报告 §十四 14.1（2026-09-29）之 R-12=①（严格双条件）+②；执行边界 §14.4 = 脚本文件落地
-- 状态: 仅落迁移脚本文件，**未对 ecos-postgres 实跑**（实跑属 §14.4 未授权项，需逐项再确认）
-- 纪律: DR01~DR08 / MC01~MC03 / ST03-A / ST07 / IR02(手动 psql) / IR03(只加不删；R-12 DROP 例外前置未闭环 ⇒ 本批零 DROP)
--
-- 与 V155~V157 存量形态的关系（重要）:
--   V155/V156 已建 ecos_knowledge.kb_nav_category / kb_nav_tag（PK DEFAULT gen_random_uuid()
--   违 MC01，partial unique index 违 MC03）；V157 已建 kb_nav_article_rel（BIGSERIAL 违 MC01）。
--   本脚本按已批准 §E.2 逐字块建"合规形态"：IF NOT EXISTS 对同名已存在表为 no-op（不 ALTER 已有列，
--   不改既有类型，R-12 ① 的"零行+零引用可 DROP 重建"前置件（备份+引用扫描+代码切换）本批未闭环 ⇒
--   一律并建/保表，旧违规定性停写：
--     - gen_random_uuid() 默认值列：应用侧改传 UUID 后即等效停写违规默认值（默认值仅兜底，不再使用）；
--     - partial unique index（V155/V156/V157 的 WHERE is_deleted=0）：保留不新建，可移植唯一性以
--       本脚本 (domain, parent_id, name, is_deleted) 复合唯一键表达（同表已存在时为 no-op，待 R-12 闭环后重建）。
--
-- 【2026-09-30 校订】独立复核后本脚本内三处收口（仅脚本文件，不实跑）：
--   ① DR07：三表 version_no 由 §E.2 逐字块 INTEGER DEFAULT 1 → VARCHAR(20) NOT NULL DEFAULT '1'（规范模板优先）。
--      代码兼容性已实证：NavTaxonServiceImpl 自增语句为
--      version_no = (CAST(version_no AS INTEGER) + 1)::text，VARCHAR(20) 存 '1'/'2'… 亦可被 CAST 还原。
--   ② DR08：三表 domain 由 NOT NULL（无 DEFAULT）→ VARCHAR(50) NOT NULL DEFAULT 'default'。
--   ③ DR02（**未收口，登记为待裁决项**）：表名 kb_nav_category / kb_nav_tag / kb_nav_article_rel 缺 ecos_ 前缀，
--      违 DR02 强制前缀。未改名的唯一原因是**现网运行代码按限定名引用**：kb-engine-impl 内
--      ecos_knowledge.kb_nav_category / kb_nav_tag / kb_nav_article_rel 均有硬编码 SQL（改表名 = 改业务代码，
--      属 §14.4 未授权项③）。故本批保留文档定名，冲突如实登记，待"表名收口 + Mapper/SQL 切换"专项一并处理。

-- schema 前置（ST07 禁止迁移脚本内 CREATE SCHEMA）：目标 schema = ecos_knowledge 须已存在；
--   2026-09-30 只读实测本机库 sys_man 内上述 schema 均已实存（登记见 docs/40-实现/DDL迁移脚本落地登记-2026-09-30.md §9.2），故原 CREATE SCHEMA 语句删除，不预置。
CREATE TABLE IF NOT EXISTS ecos_knowledge.kb_nav_category (
    id            VARCHAR(36)  NOT NULL,
    parent_id     VARCHAR(36),
    name          VARCHAR(200) NOT NULL,
    description   TEXT,
    sort_order    INTEGER      NOT NULL DEFAULT 0,
    color_token   VARCHAR(64),
    domain        VARCHAR(50)  NOT NULL DEFAULT 'default',
    trace_id      VARCHAR(64),
    version_no    VARCHAR(20)  NOT NULL DEFAULT '1',
    is_deleted    SMALLINT     NOT NULL DEFAULT 0,
    create_by     VARCHAR(64)  NOT NULL,
    update_by     VARCHAR(64)  NOT NULL,
    create_time   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_kb_nav_category PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_kb_nav_category_domain_parent ON ecos_knowledge.kb_nav_category(domain, parent_id, is_deleted);
CREATE UNIQUE INDEX IF NOT EXISTS uniq_kb_nav_category ON ecos_knowledge.kb_nav_category(domain, parent_id, name, is_deleted);

-- ── 2. kb_nav_tag（§E.2 注释"同形态（tag: name/category_id/color_token）"展开）──
CREATE TABLE IF NOT EXISTS ecos_knowledge.kb_nav_tag (
    id            VARCHAR(36)  NOT NULL,
    category_id   VARCHAR(36),
    name          VARCHAR(200) NOT NULL,
    description   TEXT,
    color_token   VARCHAR(64),
    domain        VARCHAR(50)  NOT NULL DEFAULT 'default',
    trace_id      VARCHAR(64),
    version_no    VARCHAR(20)  NOT NULL DEFAULT '1',
    is_deleted    SMALLINT     NOT NULL DEFAULT 0,
    create_by     VARCHAR(64)  NOT NULL,
    update_by     VARCHAR(64)  NOT NULL,
    create_time   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_kb_nav_tag PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_kb_nav_tag_domain_category ON ecos_knowledge.kb_nav_tag(domain, category_id, is_deleted);
CREATE UNIQUE INDEX IF NOT EXISTS uniq_kb_nav_tag ON ecos_knowledge.kb_nav_tag(domain, category_id, name, is_deleted);

-- ── 3. kb_nav_article_rel（§E.2 注释"同形态（rel: article_id/tag_id/relation_type）"展开）──
CREATE TABLE IF NOT EXISTS ecos_knowledge.kb_nav_article_rel (
    id             VARCHAR(36)  NOT NULL,
    article_id     VARCHAR(36)  NOT NULL,
    tag_id         VARCHAR(36),
    category_id    VARCHAR(36),
    relation_type  VARCHAR(20)  NOT NULL DEFAULT 'TAG',
    domain         VARCHAR(50)  NOT NULL DEFAULT 'default',
    trace_id       VARCHAR(64),
    version_no     VARCHAR(20)  NOT NULL DEFAULT '1',
    is_deleted     SMALLINT     NOT NULL DEFAULT 0,
    create_by      VARCHAR(64)  NOT NULL,
    update_by      VARCHAR(64)  NOT NULL,
    create_time    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_kb_nav_article_rel PRIMARY KEY (id),
    CONSTRAINT ck_kbnav_rel_type CHECK (relation_type IN ('TAG', 'CATEGORY'))
);
CREATE INDEX IF NOT EXISTS idx_kb_nav_rel_article ON ecos_knowledge.kb_nav_article_rel(article_id, is_deleted);
CREATE UNIQUE INDEX IF NOT EXISTS uniq_kb_nav_article_rel
    ON ecos_knowledge.kb_nav_article_rel(article_id, tag_id, category_id, relation_type, is_deleted);

COMMENT ON TABLE ecos_knowledge.kb_nav_category     IS '知识导航·业务目录树（卷04 §E.2 合规形态；V155 存量违规定性停写见文件头）';
COMMENT ON TABLE ecos_knowledge.kb_nav_tag          IS '知识导航·标签字典（卷04 §E.2 合规形态；V156 存量违规定性停写见文件头）';
COMMENT ON TABLE ecos_knowledge.kb_nav_article_rel  IS '知识导航·文章×标签/目录关联（卷04 §E.2 合规形态；V157 存量 BIGSERIAL 表停写、由 V179 路线并建新表）';

-- ── 回滚说明 ────────────────────────────────────────────────
-- 仅 CREATE IF NOT EXISTS，无数据变更。回滚 = 不执行本文件即回到原状；
-- 若已实跑且需撤销，只允许撤销"本次新建成功且零行零引用"的表（须走 R-12 ① 双条件前置件），禁止盲目 DROP。

-- ── DDL Lint Self-audit ──────────────────────────────────
-- [DR01] schema 限定 + 小写下划线 ✓  [DR03] 单数 ✓（nav_category/nav_tag/article_rel 沿用文档定名）
-- [MC01] PK VARCHAR(36) 无默认值、零 SERIAL ✓
-- [MC02] 无 JSONB 列 ✓  [MC03] 无 partial index / CREATE POLICY / PARTITION BY / text[] / timestamptz ✓
-- [DR05] is_deleted SMALLINT ✓  [DR06] create_time/update_time/create_by/update_by ✓
-- [DR07] version_no VARCHAR(20) NOT NULL DEFAULT '1' ✓（2026-09-30 收口：§E.2 逐字块 INTEGER 形态与规范模板冲突，取规范侧；代码侧
--        NavTaxonServiceImpl 以 (CAST(version_no AS INTEGER) + 1)::text 自增，VARCHAR(20) 兼容）
-- [DR08] domain VARCHAR(50) NOT NULL DEFAULT 'default' ✓（2026-09-30 收口：逐字块无 DEFAULT → 补默认值，写入零回归）
-- [IR03] 零 DROP / 零 ALTER DROP ✓  [ST07] 落 ecos_knowledge ✓
