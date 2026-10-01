-- V167.1 (卷03 §E.2/§E.3 / F03-01): 新建口径主表 ecos_caliber + 口径版本表 ecos_caliber_version（口径主权归 ontology）
-- 追溯: W73/C57（REQ-ONTO-02 / PRD-09 FC-01 的语义主体"caliber"全仓 0 命中，O-10）
-- 批准: 需求检视报告 §十四 14.1（2026-09-29）之 R-6=①（口径定义落 ontology，workspace/cognitive 只读消费）
-- 号段: 与卷02 同版本号冲突，按定版规则取 .1 子版本（V167 整号 = 卷02 biz_fact_model；Flyway 序 167 < 167.1 < 168）
-- 状态: 仅落迁移脚本文件，**未对 ecos-postgres 实跑**（实跑属 §14.4 未授权项，需逐项再确认）
-- 纪律: DR01~DR08 / MC01~MC03 / ST03-A / ST07 / IR02(手动 psql) / IR03(只加不删)
-- 与文档的两处收口差异（红线优先于文档示例）:
--   1) 文档 E.2 唯一索引 `uniq_ecos_caliber_code ... WHERE is_deleted = 0` 为 partial index，违 MC03 →
--      改为复合唯一索引 (code, is_deleted)（与 E.4 "唯一性覆盖 is_deleted" 同口径）；
--   2) 文档 E.2 模板未含 caliber 主表 status 列，但 E.4 明列 `caliber.status` 为检索投影列 → 按 E.4 补齐。
--   时间默认值按 MC03 用 CURRENT_TIMESTAMP（文档示例 NOW() 等价，取可移植写法）。

-- ── 1. 口径主表（控制域语义资产，ST07 落 ecos_ontology；R-1 a 已批准 = 全量 schema 限定名）──
CREATE TABLE IF NOT EXISTS ecos_ontology.ecos_caliber (
    id              VARCHAR(36) PRIMARY KEY,          -- MC01: 应用侧生成 UUID，DDL 无默认值（禁 gen_random_uuid()）
    code            VARCHAR(64) NOT NULL,             -- 口径业务编码（外部检索键）
    name            VARCHAR(255) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',  -- E.4 检索列（见头注差异 2）；状态语义由字典 caliber_status 管理
    unit            VARCHAR(32),                      -- 单位（V2 门禁推导输入）
    currency        VARCHAR(8),                       -- 币种代码
    owner_role      VARCHAR(64),                      -- 口径责任角色
    dimension_json  TEXT,                             -- DR04/MC02: JSON 语义列 _json 后缀 + TEXT，只做存取，禁 WHERE/JOIN/索引（E.4）
    create_time     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- DR06 审计五列
    update_time     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by       VARCHAR(100),
    update_by       VARCHAR(100),
    is_deleted      SMALLINT NOT NULL DEFAULT 0,      -- DR05
    version_no      VARCHAR(20) NOT NULL DEFAULT '1', -- DR07
    domain          VARCHAR(50) NOT NULL DEFAULT 'default' -- DR08
);
COMMENT ON TABLE ecos_ontology.ecos_caliber IS '口径主表（REQ-ONTO-02 / F03-01；R-6 ① 口径主权归 ontology；仅 ontology 可写，cognitive/workspace/kb 经 GET /api/v1/ontology/calibers/{code}/current 只读消费）';
CREATE UNIQUE INDEX IF NOT EXISTS uniq_ecos_caliber_code
    ON ecos_ontology.ecos_caliber(code, is_deleted);   -- 复合唯一含 is_deleted（MC03 禁 partial index，见头注差异 1）
CREATE INDEX IF NOT EXISTS idx_ecos_caliber_domain ON ecos_ontology.ecos_caliber(domain);
CREATE INDEX IF NOT EXISTS idx_ecos_caliber_status ON ecos_ontology.ecos_caliber(status);

-- ── 2. 口径版本表（一次升版一行，行不可变；历史内容以 Git 归档为准，git_ref 指针）──
CREATE TABLE IF NOT EXISTS ecos_ontology.ecos_caliber_version (
    id                  VARCHAR(36) PRIMARY KEY,      -- MC01
    caliber_id          VARCHAR(36) NOT NULL,         -- 指向 ecos_caliber.id（应用侧维护，DDL 不建外键）
    version_no          VARCHAR(20) NOT NULL,         -- DR07 口径版本号（DRAFT→REVIEWING→APPROVED→RETIRED 状态机随 status）
    formula             TEXT,                         -- 表达式明文（MC02：非 JSON 形态）
    additive            SMALLINT,                     -- 可加性数值码：1 ADDITIVE / 2 SEMI / 3 NON（字典 metric_additivity；DR05 不适用 is_* 前缀，非布尔）
    period_granularity  VARCHAR(16),                  -- MONTH / QUARTER / YEAR
    status              VARCHAR(16) NOT NULL DEFAULT 'DRAFT', -- 版本状态（字典 caliber_status）
    approved_by         VARCHAR(100),
    approved_at         TIMESTAMP,
    git_ref             VARCHAR(64),                  -- 历史版本 Git 归档引用（后端规范 §十一：DB 只存在用版本）
    create_time         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, -- DR06
    update_time         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by           VARCHAR(100),
    update_by           VARCHAR(100),
    is_deleted          SMALLINT NOT NULL DEFAULT 0,  -- DR05
    domain              VARCHAR(50) NOT NULL DEFAULT 'default' -- DR08
);
COMMENT ON TABLE ecos_ontology.ecos_caliber_version IS '口径版本表（F03-01；行不可变，一次升版一行；PUBLISHED 指标绑定的 caliber_version 须 status=APPROVED，V1 门禁判据）';
CREATE UNIQUE INDEX IF NOT EXISTS uniq_ecos_caliber_ver
    ON ecos_ontology.ecos_caliber_version(caliber_id, version_no);

-- ── 3. 本脚本无数据迁移（口径为全新实体，O-10 实测库内零口径数据）────────────
-- seed 同步（caliber_status / metric_additivity 两字典 ≥3 行示例，E.3 尾注）属 sysman 字典表写入，
-- 不在本组 8 文件清单内，**未落**（见交付汇报"未落项"）。

-- ── DDL Lint Self-audit ──────────────────────────────────
-- [DR01] 表名小写下划线 + schema 限定 ecos_ontology. ✓  [DR02] ecos_ 前缀 ✓  [DR03] 单数 ✓
-- [DR04] dimension_json TEXT（_json 后缀，无 JSON 类型）✓  [DR05] is_deleted SMALLINT ✓
-- [DR06] create_time/update_time/create_by/update_by/is_deleted 五列齐 ✓
-- [DR07] version_no VARCHAR(20) NOT NULL ✓  [DR08] domain VARCHAR(50) NOT NULL DEFAULT 'default' ✓
-- [MC01] PK VARCHAR(36) 无默认值，无 SERIAL/BIGSERIAL/gen_random_uuid ✓
-- [MC02] 无 JSONB；JSON 语义仅 TEXT；无裸 NUMERIC（本脚本无金额列，ST03-A 无新增登记项）✓
-- [MC03] 无 partial index / CREATE POLICY / PARTITION BY / text[] / timestamptz / :: cast；时间默认 CURRENT_TIMESTAMP ✓
-- [ST07] 控制域新表落引擎 schema ecos_ontology ✓  [IR02] 上线 = 手动 psql -f，未实跑 ✓
-- [IR03] 只加不删：本脚本仅 CREATE，无 DROP/ALTER DROP ✓
