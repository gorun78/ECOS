-- V154 (PMO-data10): Data Asset Security Classification — 3 新表 + 2 ALTER
-- 维度：分级字典 / 业务分类树 / 资产业务视图 / 字段级敏感度 / CLS+RLS 加 resource_id 列
-- 规范：DR01~08 + IR02/03/04/05 + ST03/04/05/06
-- 前置：V150 (schema 隔离 + security profile)、V58 (CATEGORY 5 seed 仍存在，二轨)

-- ── 0. Schema guard ──────────────────────────────────────
CREATE SCHEMA IF NOT EXISTS ecos_data;

-- ── 1. 数据分级字典 ──────────────────────────────────────
CREATE TABLE IF NOT EXISTS ecos_data.ecos_data_level_def (
    level_code        VARCHAR(20)  PRIMARY KEY,
    level_value       INTEGER      NOT NULL,
    level_name        VARCHAR(100) NOT NULL,
    level_description TEXT,
    mask_strategy_json JSONB       DEFAULT '{"type": "none"}'::jsonb,
    rls_strategy_json  JSONB       DEFAULT '{"type": "no_filter"}'::jsonb,
    sort_order        INTEGER      DEFAULT 0,
    create_time       TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_time       TIMESTAMP    NOT NULL DEFAULT NOW(),
    create_by         VARCHAR(100),
    update_by         VARCHAR(100),
    is_deleted        SMALLINT     NOT NULL DEFAULT 0,
    version_no        VARCHAR(20)  NOT NULL DEFAULT '1',
    domain            VARCHAR(50)  NOT NULL DEFAULT 'default'
);
COMMENT ON TABLE  ecos_data.ecos_data_level_def IS '数据分级字典（L1..L4）— data-engine 唯一权威（替代已 deprecated 的 DataClassificationServiceImpl 内存 4 级）';
CREATE INDEX IF NOT EXISTS idx_data_level_code ON ecos_data.ecos_data_level_def(level_code);

-- ── 2. 业务分类树（≤3 级，V58 td_data_category 双轨）─────
CREATE TABLE IF NOT EXISTS ecos_data.ecos_data_category_tree (
    category_id     VARCHAR(36)  PRIMARY KEY,
    name            VARCHAR(200) NOT NULL,
    parent_id       VARCHAR(36),
    level           SMALLINT     DEFAULT 1,
    description     TEXT,
    icon            VARCHAR(50),
    create_time     TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_time     TIMESTAMP    NOT NULL DEFAULT NOW(),
    create_by       VARCHAR(100),
    update_by       VARCHAR(100),
    is_deleted      SMALLINT     NOT NULL DEFAULT 0,
    version_no      VARCHAR(20)  NOT NULL DEFAULT '1',
    domain          VARCHAR(50)  NOT NULL DEFAULT 'default'
);
COMMENT ON TABLE ecos_data.ecos_data_category_tree IS '业务分类树（最多 3 级，资产/目录主用）';
CREATE INDEX IF NOT EXISTS idx_category_tree_parent ON ecos_data.ecos_data_category_tree(parent_id);
CREATE INDEX IF NOT EXISTS idx_category_tree_level  ON ecos_data.ecos_data_category_tree(level);

-- ── 3. 资产业务视图（ADR：td_data_resource 物理层 + 本表业务层）──
CREATE TABLE IF NOT EXISTS ecos_data.ecos_data_asset (
    asset_id          VARCHAR(36)  PRIMARY KEY,
    resource_id       VARCHAR(64)  NOT NULL,
    asset_name        VARCHAR(256) NOT NULL,
    business_desc     TEXT,
    owner             VARCHAR(100),
    owner_org         VARCHAR(128),
    data_grain        VARCHAR(64),
    category_id       VARCHAR(36),
    sensitivity_level VARCHAR(20)  DEFAULT 'L1',
    category_status   VARCHAR(20)  DEFAULT 'PENDING',
    last_tagged_by    VARCHAR(100),
    last_tagged_at    TIMESTAMP,
    create_time       TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_time       TIMESTAMP    NOT NULL DEFAULT NOW(),
    create_by         VARCHAR(100),
    update_by         VARCHAR(100),
    is_deleted        SMALLINT     NOT NULL DEFAULT 0,
    version_no        VARCHAR(20)  NOT NULL DEFAULT '1',
    domain            VARCHAR(50)  NOT NULL DEFAULT 'default'
);
COMMENT ON TABLE ecos_data.ecos_data_asset IS '数据资产（业务视图）— PMO-data10 资产 CRUD / 分级 / 分类主表';
CREATE UNIQUE INDEX IF NOT EXISTS uniq_asset_resource_domain ON ecos_data.ecos_data_asset(resource_id, domain);
CREATE INDEX IF NOT EXISTS idx_asset_sensitivity ON ecos_data.ecos_data_asset(sensitivity_level);
CREATE INDEX IF NOT EXISTS idx_asset_category    ON ecos_data.ecos_data_asset(category_id);
CREATE INDEX IF NOT EXISTS idx_asset_owner       ON ecos_data.ecos_data_asset(owner);

-- ── 4. 字段级敏感度（CLS 必须落到列）────────────────────
--    关键约束：confirmed=false 的字段 → security-engine 不生成脱敏策略
--    即"LLM 推荐 → 人工确认 → 触发发事件 → 才落库生效"
CREATE TABLE IF NOT EXISTS ecos_data.ecos_data_asset_field (
    field_asset_id    VARCHAR(36)  PRIMARY KEY,
    asset_id          VARCHAR(36)  NOT NULL,
    field_id          VARCHAR(64)  NOT NULL,
    field_name        VARCHAR(256) NOT NULL,
    field_type        VARCHAR(64),
    data_type         VARCHAR(64)  DEFAULT 'GENERAL',
    field_sensitivity VARCHAR(20)  DEFAULT 'L1',
    mask_strategy     VARCHAR(50)  DEFAULT 'none',
    recommend_level   VARCHAR(20),
    recommend_source  VARCHAR(50),
    confirmed         BOOLEAN      DEFAULT FALSE,
    create_time       TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_time       TIMESTAMP    NOT NULL DEFAULT NOW(),
    create_by         VARCHAR(100),
    update_by         VARCHAR(100),
    is_deleted        SMALLINT     NOT NULL DEFAULT 0,
    version_no        VARCHAR(20)  NOT NULL DEFAULT '1',
    domain            VARCHAR(50)  NOT NULL DEFAULT 'default'
);
COMMENT ON TABLE ecos_data.ecos_data_asset_field IS '资产-字段级敏感度 — 参考 td_data_field；mask_strategy: none/middle4/prefix3/suffix4/full';
CREATE INDEX IF NOT EXISTS idx_asset_field_asset ON ecos_data.ecos_data_asset_field(asset_id);
CREATE INDEX IF NOT EXISTS idx_asset_field_sens  ON ecos_data.ecos_data_asset_field(field_sensitivity);
CREATE INDEX IF NOT EXISTS idx_asset_field_conf  ON ecos_data.ecos_data_asset_field(confirmed);

-- ── 5. CLS 策略加 resource_id 列（IR03 只加不删）─────────
ALTER TABLE ecos_cls_policy ADD COLUMN IF NOT EXISTS resource_id VARCHAR(64);
CREATE INDEX IF NOT EXISTS idx_cls_policy_resource ON ecos_cls_policy(resource_id);
COMMENT ON COLUMN ecos_cls_policy.resource_id IS 'PMO-data10 资产驱动 CLS（与 table_name 双轨，老行 resource_id 为空走 table_name 兜底）';

-- ── 6. RLS 策略加 resource_id 列 ────────────────────────
ALTER TABLE ecos_rls_policy ADD COLUMN IF NOT EXISTS resource_id VARCHAR(64);
CREATE INDEX IF NOT EXISTS idx_rls_policy_resource ON ecos_rls_policy(resource_id);
COMMENT ON COLUMN ecos_rls_policy.resource_id IS 'PMO-data10 资产驱动 RLS（兼容 table_name）';

-- ── 7. Seed: 4 级字典（幂等）────────────────────────────
INSERT INTO ecos_data.ecos_data_level_def (level_code, level_value, level_name, level_description, mask_strategy_json, rls_strategy_json, sort_order)
VALUES
('L1', 1, '公开',     '公开数据，无敏感度，公开可见', '{"type": "none"}'::jsonb, '{"type": "no_filter"}'::jsonb, 1),
('L2', 2, '内部',     '内部业务数据，禁止外传，按部门授权', '{"type": "none"}'::jsonb, '{"type": "dept_filter"}'::jsonb, 2),
('L3', 3, '敏感',     '含 PII（身份证/手机号/银行卡等），强制脱敏 + 列级过滤', '{"type": "mask_pii"}'::jsonb, '{"type": "no_filter"}'::jsonb, 3),
('L4', 4, '核心机密', '核心业务数据（核心用户/支付/风控），列级脱敏 + 行级 RLS（高 clearance 才放行）',
 '{"type": "mask_pii_strict"}'::jsonb, '{"type": "clearance_filter", "min_level": 4}'::jsonb, 4)
ON CONFLICT (level_code) DO NOTHING;

-- ── 8. Seed: 业务分类树 5 条（V58 旧表保留，新表同步初始化）──
INSERT INTO ecos_data.ecos_data_category_tree (category_id, name, parent_id, level, description, icon, sort_order)
VALUES
('biz_root',      'Business Asset Root',    NULL,      1, 'Root business asset category',                          'Folder',   0),
('biz_pii',       'Personal Identity Data', 'biz_root',2, 'PII: IDCard / Phone / Email / BankCard / Address',         'Key',      1),
('biz_financial', 'Financial Data',         'biz_root',2, 'Financial: balance / transaction / statement',             'Coins',    2),
('biz_operational','Operational Data',      'biz_root',2, 'Raw operational logs and metadata',                        'BookOpen', 3),
('biz_app',       'Application Data Mart',  'biz_root',2, 'Application-level data marts',                             'Layers',   4)
ON CONFLICT (category_id) DO NOTHING;

-- ── DDL Lint Self-audit ──────────────────────────────────
-- [DR02] 4 新表 ecos_ 前缀；2 老表 ALTER 加 resource_id 不动已有列 ✓
-- [DR04] JSONB 一律 _json 后缀（mask_strategy_json / rls_strategy_json） ✓
-- [DR05] is_deleted SMALLINT ✓
-- [DR06/07/08] 5 审计字段 + version_no + domain 全齐 ✓
-- [DR01/03] 全小写下划线，单数业务对象 ✓
-- [IR03] 零 DROP / 零 ALTER 已有列；只加列 ✓
-- [ST03/04/05/06] mask_strategy_json 不存敏感明文；行过滤由 service 计算；Kafka audit 在 service 层发
