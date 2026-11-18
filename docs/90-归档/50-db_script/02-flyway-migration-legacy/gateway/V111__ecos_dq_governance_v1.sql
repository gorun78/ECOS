-- ============================================================
-- V111__ecos_dq_governance_v1.sql
-- PMO-48-A T1: 数据质量治理 schema 与双轨规则合并
-- ============================================================
-- 来源: PMO-48-A-数据质量基础设施.md / 数据质量管理方案 v1.1-decided
-- 决策 #1 落地: 双轨规则合并 (legacy v1/v2 + quality_rule → ecos_dq.dq_rule)
--
-- 铁律 3.1 遵循:
--   - schema 只加不删: 所有 CREATE 用 IF NOT EXISTS
--   - 不物理删除旧表 (ecos_dq_rule / ecos_dq_rule_v2 / ecos_quality_rule /
--     ecos_dq_issue / ecos_quality_evaluation 均保留不动)
--   - 不改旧表结构
--   - 迁移使用 ON CONFLICT (id) DO NOTHING 防重复跑
--
-- 防御性:
--   - 旧表均可能不存在 (运行时建表 / 历史迁移缺失), 迁移块使用
--     to_regclass() 探测, 表不存在则跳过, 不报错
-- ============================================================


-- ============================================================
-- 1. 创建 ecos_dq schema
-- ============================================================
CREATE SCHEMA IF NOT EXISTS ecos_dq;


-- ============================================================
-- 2. 新建 5 张表
-- ============================================================

-- ------------------------------------------------------------
-- 2.1 dq_rule — 规则主表 (合并双轨 + 多域 + 版本)
--     category: BUSINESS / TECHNICAL / COMPLIANCE
--     status:   DRAFT / IN_REVIEW / ACTIVE / DEPRECATED / SUPERSEDED / REJECTED
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ecos_dq.dq_rule (
    id                  VARCHAR(64)     PRIMARY KEY,
    rule_name           VARCHAR(255)    NOT NULL,
    rule_code           VARCHAR(191)    UNIQUE,
    category            VARCHAR(32)     NOT NULL,
    domain              VARCHAR(128),
    rule_type           VARCHAR(64)     NOT NULL,
    severity            VARCHAR(16)     NOT NULL DEFAULT 'MEDIUM',
    target_kind         VARCHAR(32)     NOT NULL,
    target_id           VARCHAR(64),
    target_table        VARCHAR(191),
    target_field        VARCHAR(191),
    target_pipeline_id  VARCHAR(64),
    parameters          JSONB           NOT NULL DEFAULT '{}'::jsonb,
    status              VARCHAR(32)     NOT NULL DEFAULT 'DRAFT',
    version             INT             NOT NULL DEFAULT 1,
    approved_by         VARCHAR(128),
    effective_date      BIGINT,
    expiry_date         BIGINT,
    source_type         VARCHAR(32)     NOT NULL DEFAULT 'MANUAL',
    source_ref          VARCHAR(64),
    description         TEXT            DEFAULT '',
    created_by          VARCHAR(128),
    updated_by          VARCHAR(128),
    created_at          TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP       NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMP,
    is_deleted          BOOLEAN         NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_dq_rule_category   ON ecos_dq.dq_rule(category)        WHERE is_deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_dq_rule_status     ON ecos_dq.dq_rule(status)          WHERE is_deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_dq_rule_target     ON ecos_dq.dq_rule(target_kind, target_id) WHERE is_deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_dq_rule_domain     ON ecos_dq.dq_rule(domain)          WHERE is_deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_dq_rule_rule_type  ON ecos_dq.dq_rule(rule_type)       WHERE is_deleted = FALSE;

COMMENT ON TABLE  ecos_dq.dq_rule IS '数据质量规则主表 (合并双轨, 支持多域/版本)';
COMMENT ON COLUMN ecos_dq.dq_rule.category        IS '规则类别: BUSINESS / TECHNICAL / COMPLIANCE';
COMMENT ON COLUMN ecos_dq.dq_rule.status          IS '规则状态: DRAFT / IN_REVIEW / ACTIVE / DEPRECATED / SUPERSEDED / REJECTED';
COMMENT ON COLUMN ecos_dq.dq_rule.source_type     IS '规则来源: MANUAL / LEGACY_V2 / LEGACY_QUALITY / KB_SYNC';
COMMENT ON COLUMN ecos_dq.dq_rule.source_ref      IS '来源追溯 (legacy 表:原始行id)';


-- ------------------------------------------------------------
-- 2.2 dq_rule_version — 规则版本快照 (只增不删, 审计追溯)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ecos_dq.dq_rule_version (
    id                  VARCHAR(64)     PRIMARY KEY,
    rule_id             VARCHAR(64)     NOT NULL,
    version_number      INT             NOT NULL,
    snapshot            JSONB           NOT NULL,
    changed_by          VARCHAR(128),
    changed_at          TIMESTAMP       NOT NULL DEFAULT NOW(),
    change_note         TEXT,
    UNIQUE (rule_id, version_number)
);

CREATE INDEX IF NOT EXISTS idx_dq_rule_version_rule ON ecos_dq.dq_rule_version(rule_id, version_number DESC);

COMMENT ON TABLE ecos_dq.dq_rule_version IS '规则版本快照 (每次修改存档, 审计追溯)';


-- ------------------------------------------------------------
-- 2.3 dq_rule_check — 规则执行记录 (调度/事件/手动)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ecos_dq.dq_rule_check (
    id              VARCHAR(36)     PRIMARY KEY,
    rule_id         VARCHAR(64)     NOT NULL,
    schedule_id     VARCHAR(64),
    trigger_type    VARCHAR(16)     NOT NULL,
    executed_at     TIMESTAMP       NOT NULL DEFAULT NOW(),
    passed          BOOLEAN         NOT NULL DEFAULT FALSE,
    total_rows      BIGINT          DEFAULT 0,
    failed_rows     BIGINT          DEFAULT 0,
    pass_rate       DOUBLE PRECISION,
    latency_ms      INT,
    error_message   TEXT,
    sample_size     INT,
    sample_failures JSONB
);

CREATE INDEX IF NOT EXISTS idx_dq_check_rule_time ON ecos_dq.dq_rule_check(rule_id, executed_at DESC);
CREATE INDEX IF NOT EXISTS idx_dq_check_time      ON ecos_dq.dq_rule_check(executed_at DESC);

COMMENT ON TABLE  ecos_dq.dq_rule_check IS '规则执行记录 (调度/事件/手动触发)';
COMMENT ON COLUMN ecos_dq.dq_rule_check.trigger_type IS '触发类型: SCHEDULE / EVENT / MANUAL';


-- ------------------------------------------------------------
-- 2.4 dq_alert_record — 告警事件 (P0-P3 分级, 升级链路)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ecos_dq.dq_alert_record (
    id                VARCHAR(36)     PRIMARY KEY,
    rule_id           VARCHAR(64)     NOT NULL,
    alert_level       VARCHAR(4)      NOT NULL,
    alert_type        VARCHAR(32)     NOT NULL,
    asset_id          VARCHAR(64),
    asset_name        VARCHAR(191),
    rule_name         VARCHAR(255),
    message           TEXT            NOT NULL,
    payload           JSONB,
    status            VARCHAR(16)     NOT NULL DEFAULT 'PENDING',
    escalated_to      VARCHAR(4),
    notify_count      INT             NOT NULL DEFAULT 0,
    last_notify_at    TIMESTAMP,
    notify_channels   JSONB,
    ack_by            VARCHAR(128),
    ack_at            TIMESTAMP,
    resolved_by       VARCHAR(128),
    resolved_at       TIMESTAMP,
    resolved_note     TEXT,
    created_at        TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_dq_alert_status ON ecos_dq.dq_alert_record(status) WHERE status IN ('PENDING','NOTIFIED');
CREATE INDEX IF NOT EXISTS idx_dq_alert_time   ON ecos_dq.dq_alert_record(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_dq_alert_level  ON ecos_dq.dq_alert_record(alert_level);
CREATE INDEX IF NOT EXISTS idx_dq_alert_rule   ON ecos_dq.dq_alert_record(rule_id, created_at DESC);

COMMENT ON TABLE  ecos_dq.dq_alert_record IS '告警事件记录 (P0-P3 分级, 升级链路)';
COMMENT ON COLUMN ecos_dq.dq_alert_record.status       IS '告警状态: PENDING / NOTIFIED / ACKED / RESOLVED / IGNORED / ESCALATED';
COMMENT ON COLUMN ecos_dq.dq_alert_record.escalated_to IS '升级到的告警级别 (如 P1)';


-- ------------------------------------------------------------
-- 2.5 dq_work_order — 工单 (告警→处理→验证→关闭)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ecos_dq.dq_work_order (
    id                  VARCHAR(36)     PRIMARY KEY,
    order_no            VARCHAR(64)     UNIQUE NOT NULL,
    alert_id            VARCHAR(36)     NOT NULL,
    rule_id             VARCHAR(64)     NOT NULL,
    asset_id            VARCHAR(64),
    title               VARCHAR(255)    NOT NULL,
    description         TEXT,
    status              VARCHAR(16)     NOT NULL DEFAULT 'PENDING',
    handling_mode       VARCHAR(16)     NOT NULL DEFAULT 'MANUAL',
    severity            VARCHAR(16),
    assigned_to         VARCHAR(128),
    assigned_at         TIMESTAMP,
    repair_action       VARCHAR(64),
    repair_status       VARCHAR(16),
    repair_log          JSONB,
    verified_by         VARCHAR(128),
    verified_at         TIMESTAMP,
    verify_pass         BOOLEAN,
    verify_note         TEXT,
    resolved_by         VARCHAR(128),
    resolved_at         TIMESTAMP,
    resolution_note     TEXT,
    preventive_actions  JSONB,
    rca_result          JSONB,
    rca_confidence      DOUBLE PRECISION,
    rca_analyzed_at     TIMESTAMP,
    retry_count         INT             NOT NULL DEFAULT 0,
    created_at          TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP       NOT NULL DEFAULT NOW(),
    closed_at           TIMESTAMP,
    is_deleted          BOOLEAN         NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_wo_status   ON ecos_dq.dq_work_order(status) WHERE is_deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_wo_alert    ON ecos_dq.dq_work_order(alert_id);
CREATE INDEX IF NOT EXISTS idx_wo_asset    ON ecos_dq.dq_work_order(asset_id) WHERE is_deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_wo_severity ON ecos_dq.dq_work_order(severity, status) WHERE is_deleted = FALSE;

COMMENT ON TABLE  ecos_dq.dq_work_order IS '工单 (告警→处理→验证→关闭, 溯源 RCA)';
COMMENT ON COLUMN ecos_dq.dq_work_order.status        IS '工单状态: PENDING / ASSIGNED / IN_WORK / RESOLVED / VERIFIED / CLOSED / REJECTED';
COMMENT ON COLUMN ecos_dq.dq_work_order.handling_mode IS '处理模式: MANUAL / AUTO_REPAIR / EXEMPT';


-- ============================================================
-- 3. 旧表数据迁移 (双轨合并 — 决策 #1)
--    原则: 幂等 (ON CONFLICT DO NOTHING), 防御 (to_regclass 探测)
--    不删除任何旧表 (铁律 3.1)
-- ============================================================

-- ------------------------------------------------------------
-- 3.1 迁移 legacy v1: ecos_dq_rule
--     源: ecos_rule.ecos_dq_rule (V6 创建 + V47 移 schema)
--     特征: id BIGSERIAL → 保留为文本 id (source_ref 标原 id)
--     category: TECHNICAL (KB 同步的才标 COMPLIANCE)
--     target_kind: TABLE (默认, legacy 无明确 target 维度)
-- ------------------------------------------------------------
DO $$
BEGIN
    IF to_regclass('ecos_rule.ecos_dq_rule') IS NOT NULL THEN
        INSERT INTO ecos_dq.dq_rule
            (id, rule_name, rule_type, category, status, version,
             source_type, source_ref, severity, parameters, description,
             target_kind, rule_code,
             created_at, updated_at, is_deleted)
        SELECT
            id::text,
            name,
            rule_type,
            'TECHNICAL',
            CASE WHEN enabled THEN 'ACTIVE' ELSE 'DEPRECATED' END,
            1,
            'LEGACY_V1',
            'ecos_rule.ecos_dq_rule:' || id::text,
            COALESCE(severity, 'MEDIUM'),
            COALESCE(config_json::jsonb, '{}'::jsonb),
            COALESCE(description, ''),
            'TABLE',
            'legacy_v1_' || id::text,
            COALESCE(created_at, NOW()),
            NOW(),
            FALSE
        FROM ecos_rule.ecos_dq_rule
        WHERE id IS NOT NULL AND name IS NOT NULL
        ON CONFLICT (id) DO NOTHING;
    ELSE
        RAISE NOTICE 'ecos_rule.ecos_dq_rule 不存在, 跳过迁移';
    END IF;
END $$;

-- ------------------------------------------------------------
-- 3.2 迁移 legacy v2: ecos_dq_rule_v2
--     源: 运行时创建 (DqRepository 实际查询的表), 位置可能不在
--          expect 的 schema。用 search_path 通用解析 (search_path 已含
--          各 domain schema + public)。
--     特征: id BIGSERIAL (同 v1), 列结构同 v1
-- ------------------------------------------------------------
DO $$
BEGIN
    IF to_regclass('public.ecos_dq_rule_v2') IS NOT NULL THEN
        INSERT INTO ecos_dq.dq_rule
            (id, rule_name, rule_type, category, status, version,
             source_type, source_ref, severity, parameters, description,
             target_kind, rule_code,
             created_at, updated_at, is_deleted)
        SELECT
            id::text,
            name,
            rule_type,
            'TECHNICAL',
            CASE WHEN enabled THEN 'ACTIVE' ELSE 'DEPRECATED' END,
            1,
            'LEGACY_V2',
            'ecos_dq_rule_v2:' || id::text,
            COALESCE(severity, 'MEDIUM'),
            COALESCE(config_json::jsonb, '{}'::jsonb),
            COALESCE(description, ''),
            'TABLE',
            'legacy_v2_' || id::text,
            COALESCE(created_at, NOW()),
            NOW(),
            FALSE
        FROM public.ecos_dq_rule_v2
        WHERE id IS NOT NULL AND name IS NOT NULL
        ON CONFLICT (id) DO NOTHING;
    ELSIF to_regclass('ecos_rule.ecos_dq_rule_v2') IS NOT NULL THEN
        INSERT INTO ecos_dq.dq_rule
            (id, rule_name, rule_type, category, status, version,
             source_type, source_ref, severity, parameters, description,
             target_kind, rule_code,
             created_at, updated_at, is_deleted)
        SELECT
            id::text,
            name,
            rule_type,
            'TECHNICAL',
            CASE WHEN enabled THEN 'ACTIVE' ELSE 'DEPRECATED' END,
            1,
            'LEGACY_V2',
            'ecos_rule.ecos_dq_rule_v2:' || id::text,
            COALESCE(severity, 'MEDIUM'),
            COALESCE(config_json::jsonb, '{}'::jsonb),
            COALESCE(description, ''),
            'TABLE',
            'legacy_v2_' || id::text,
            COALESCE(created_at, NOW()),
            NOW(),
            FALSE
        FROM ecos_rule.ecos_dq_rule_v2
        WHERE id IS NOT NULL AND name IS NOT NULL
        ON CONFLICT (id) DO NOTHING;
    ELSE
        RAISE NOTICE 'ecos_dq_rule_v2 不存在 (public / ecos_rule), 跳过迁移';
    END IF;
END $$;

-- ------------------------------------------------------------
-- 3.3 迁移新版 7 类规则: ecos_quality_rule
--     源: public.ecos_quality_rule (QualityServiceImpl @PostConstruct 运行时建表)
--     特征: rule_id VARCHAR(64) 已是文本主键, 直接保留
--     target 列格式可能是 "table:field" / "entity:entityId" / 纯表名
--     规则类型推断 (按方案 §2.x 7 类规则对齐):
--       NOT_NULL/UNIQUE/FORMAT/RANGE/CONSISTENCY/REGEX/STATistical
--     category: 统一 TECHNICAL (KB 同步标 COMPLIANCE, 由后续 KB sync 任务负责)
-- ------------------------------------------------------------
DO $$
BEGIN
    IF to_regclass('public.ecos_quality_rule') IS NOT NULL THEN
        INSERT INTO ecos_dq.dq_rule
            (id, rule_name, rule_type, category, status, version,
             source_type, source_ref, severity, parameters, description,
             target_kind, target_table, target_field, rule_code,
             created_at, updated_at, is_deleted)
        SELECT
            rule_id,
            rule_name,
            rule_type,
            'TECHNICAL',
            CASE WHEN enabled THEN 'ACTIVE' ELSE 'DEPRECATED' END,
            1,
            'LEGACY_QUALITY',
            'ecos_quality_rule:' || rule_id,
            COALESCE(severity, 'MEDIUM'),
            COALESCE(parameters, '{}'::jsonb),
            COALESCE(description, ''),
            'TABLE',
            -- 解析 target: 含冒号时取冒号前=table, 后=field; 否则 target 视为表
            CASE
                WHEN target LIKE '%:%' THEN split_part(target, ':', 1)
                ELSE target
            END,
            CASE
                WHEN target LIKE '%:%' THEN split_part(target, ':', 2)
                ELSE NULL
            END,
            'legacy_quality_' || rule_id,
            COALESCE(created_at, NOW()),
            NOW(),
            FALSE
        FROM public.ecos_quality_rule
        WHERE rule_id IS NOT NULL
        ON CONFLICT (id) DO NOTHING;
    ELSIF to_regclass('ecos_rule.ecos_quality_rule') IS NOT NULL THEN
        INSERT INTO ecos_dq.dq_rule
            (id, rule_name, rule_type, category, status, version,
             source_type, source_ref, severity, parameters, description,
             target_kind, target_table, target_field, rule_code,
             created_at, updated_at, is_deleted)
        SELECT
            rule_id,
            rule_name,
            rule_type,
            'TECHNICAL',
            CASE WHEN enabled THEN 'ACTIVE' ELSE 'DEPRECATED' END,
            1,
            'LEGACY_QUALITY',
            'ecos_rule.ecos_quality_rule:' || rule_id,
            COALESCE(severity, 'MEDIUM'),
            COALESCE(parameters, '{}'::jsonb),
            COALESCE(description, ''),
            'TABLE',
            CASE
                WHEN target LIKE '%:%' THEN split_part(target, ':', 1)
                ELSE target
            END,
            CASE
                WHEN target LIKE '%:%' THEN split_part(target, ':', 2)
                ELSE NULL
            END,
            'legacy_quality_' || rule_id,
            COALESCE(created_at, NOW()),
            NOW(),
            FALSE
        FROM ecos_rule.ecos_quality_rule
        WHERE rule_id IS NOT NULL
        ON CONFLICT (id) DO NOTHING;
    ELSE
        RAISE NOTICE 'ecos_quality_rule 不存在 (public / ecos_rule), 跳过迁移';
    END IF;
END $$;

-- ============================================================
-- END V111
-- 产出: 5 张表 + 16 个索引
--   ecos_dq.dq_rule          (5 索引: category / status / target / domain / rule_type)
--   ecos_dq.dq_rule_version  (1 索引: rule + version DESC)
--   ecos_dq.dq_rule_check    (2 索引: rule+time / time)
--   ecos_dq.dq_alert_record  (4 索引: status / time / level / rule+time)
--   ecos_dq.dq_work_order    (4 索引: status / alert / asset / severity+status)
-- 迁移: 3 个 DO 块 (v1 / v2 / quality_rule), 防御 (to_regclass) + 幂等 (ON CONFLICT DO NOTHING)
-- ============================================================
