-- ============================================================
-- V112__ecos_dq_scoring_and_version.sql
-- PMO-48-B T7a: DQ 6 维评分快照 + 资产分两表 + 规则表加 2 列
-- ============================================================
-- 来源: PMO-48-B-数据质量规则状态机与评分引擎.md / 数据质量管理方案 v1.1-decided §2 & §4.3
--
-- §4.3 评分两表说明:
--   dq_score_snapshot — 6 维度评分快照 (每次评估独立一行)
--   dq_score_asset    — 资产级评分 (每资产 6 维加权汇总)
--
-- 规则表加 2 列 (审批生命周期):
--   approved_at — submit → approve 时间
--   applied_at  — approve → ACTIVE 切换时间
--
-- 铁律 3.1 遵循:
--   - schema 只加不删: 所有 CREATE 用 IF NOT EXISTS
--   - 加列用 ADD COLUMN IF NOT EXISTS (幂等)
--   - 不删除 dq_rule 任何已有列 (V111 已创建)
--
-- 防御性:
--   - 已 V111 后就绪的 ecos_dq 无需重建 (IF NOT EXISTS)
--
-- 关联任务:
--   - PMO-48-A T1 (V111) — 双轨规则合并 + 5 张表
--   - PMO-48-B T7   (gateway 三滤波器) — 不在本迁移
--   - PMO-48-B T8   (DqGovernanceService 评分引擎) — 消费本两表
-- ============================================================


-- ============================================================
-- 1. 加 2 列到 ecos_dq.dq_rule (审批生命周期)
-- ------------------------------------------------------------
-- approved_at: submit → approve 时间 (status: IN_REVIEW → ACTIVE 的开关)
-- applied_at:  approve → ACTIVE 切换时间 (引入规则正式有效)
--
-- 场景:
--   - DRAFT 创建                  — approved_at/applied_at 均 NULL
--   - DRAFT → IN_REVIEW (submit)  — 均 NULL
--   - IN_REVIEW → ACTIVE (approve)— approved_at = NOW(); applied_at 占位 NULL
--   - ACTIVE 切换到新的版本生效     — applied_at = 新版本的生效时点
-- ============================================================

-- 防御: 若 V111 未先跑, dq_rule 表不存在; ADD COLUMN 会因引 Schema 缺失报错
--       在 V111 已落地前提下, 加列为 IF NOT EXISTS (幂等)
ALTER TABLE ecos_dq.dq_rule ADD COLUMN IF NOT EXISTS approved_at TIMESTAMP;
ALTER TABLE ecos_dq.dq_rule ADD COLUMN IF NOT EXISTS applied_at  TIMESTAMP;

COMMENT ON COLUMN ecos_dq.dq_rule.approved_at IS '审批通过时间 (submit → approve: IN_REVIEW → ACTIVE 切换)';
COMMENT ON COLUMN ecos_dq.dq_rule.applied_at  IS '正式生效时间 (approve → ACTIVE 切换时设置, 应用方使用)';


-- ============================================================
-- 2. 建 ecos_dq.dq_score_snapshot (6 维度评分快照, 方案 §4.3)
-- ============================================================
-- 维度 (rule_type 6 维, 与 DqGovernanceController 维度注册表同源):
--   ACCURACY / CONSISTENCY / FRESHNESS / UNIQUENESS / VALIDITY / COMPLETENESS
--
-- 范围 (scope_type):
--   FIELD      — 单字段 (target_field 必填)
--   TABLE      — 表级 (target_table 必填)
--   DATASOURCE — 数据源级
--   DOMAIN     — 域级
--   SYSTEM     — 系统级
--
-- score_value 0.0 - 1.0 (规范化积分)
-- weight      该维度在资产级汇总中的权重 (默认 1.0, T8 评分引擎按目标值配置)
--
-- 设计说明:
--   - BIGSERIAL 主键 (不依赖业务 id, 避免 uuid 字符串长度管理)
--   - 每次评估独立一行, 时间序列保留 (时序查询时走 evaluated_at DESC)
--   - sample_size / distinct_rule_count 是评估规模诊断位 (T8 填充)
--   - metadata JSONB 存评估上下文 (如 sampleStats / threshold / relatedAlertId)
-- ============================================================

CREATE SCHEMA IF NOT EXISTS ecos_dq;

CREATE TABLE IF NOT EXISTS ecos_dq.dq_score_snapshot (
    id                   BIGSERIAL    PRIMARY KEY,
    dimension            VARCHAR(16)  NOT NULL,
    scope_type           VARCHAR(16)  NOT NULL,
    scope_id             VARCHAR(64)  NOT NULL,
    score_value          DOUBLE PRECISION NOT NULL,
    weight               DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    sample_size          INT,
    distinct_rule_count  INT          NOT NULL DEFAULT 0,
    evaluated_at         TIMESTAMP    NOT NULL DEFAULT NOW(),
    metadata             JSONB
);

CREATE INDEX IF NOT EXISTS idx_dq_score_scope_time ON ecos_dq.dq_score_snapshot (scope_type, scope_id, evaluated_at DESC);

COMMENT ON TABLE  ecos_dq.dq_score_snapshot IS 'DQ 6 维度评分快照 (PMO-48-B T8 评分引擎写入)';
COMMENT ON COLUMN ecos_dq.dq_score_snapshot.dimension           IS '评估维度: ACCURACY/CONSISTENCY/FRESHNESS/UNIQUENESS/VALIDITY/COMPLETENESS';
COMMENT ON COLUMN ecos_dq.dq_score_snapshot.scope_type          IS '范围类型: FIELD/TABLE/DATASOURCE/DOMAIN/SYSTEM';
COMMENT ON COLUMN ecos_dq.dq_score_snapshot.scope_id            IS '范围实体 id (与 scope_type 匹配, TABLE 时为 datasource:table, FIELD 时为 datasource:table:field)';
COMMENT ON COLUMN ecos_dq.dq_score_snapshot.score_value         IS '该次评估的维度分, 0.0 - 1.0 规范化 (PASS_RATE)';
COMMENT ON COLUMN ecos_dq.dq_score_snapshot.weight              IS '该维度在资产级汇总中的权重 (T8 按目标设置, 默认 1.0)';
COMMENT ON COLUMN ecos_dq.dq_score_snapshot.sample_size         IS '本次评估使用的样本量 (T8 用于诊断小样本偏差)';
COMMENT ON COLUMN ecos_dq.dq_score_snapshot.distinct_rule_count IS '本次评估命中的规则数 (去重统计)';
COMMENT ON COLUMN ecos_dq.dq_score_snapshot.metadata            IS '评估上下文 (sampleStats / threshold / relatedAlertId 等)';


-- ============================================================
-- 3. 建 ecos_dq.dq_score_asset (资产级评分, 每资产 6 维加权汇总)
-- ============================================================
-- asset_type: DATASOURCE / TABLE (T8 评分模型先覆盖这两类; FIELD/DOMAIN/SYSTEM 候选扩展)
-- asset_id:   资产唯一 id (DATASOURCE 时为 datasource_id; TABLE 时为 table_qualified_name)
--
-- overall_score:  6 维度评分按 dimension weights 加权汇总, 0.0 - 1.0
-- rolled_up_scores JSONB: 6 维评分快照, 形如:
--   [{"dimension":"ACCURACY","score":0.98,"weight":1.5}, ...]
--
-- 设计说明:
--   - 复合主键 (asset_type, asset_id), 每资产最新一条
--   - is_deleted 软删标记 (与 dq_rule 一致, 不物理删除)
--   - T8 评分引擎写时 UPSERT: INSERT ... ON CONFLICT DO UPDATE
-- ============================================================

CREATE TABLE IF NOT EXISTS ecos_dq.dq_score_asset (
    asset_type        VARCHAR(16)     NOT NULL,
    asset_id          VARCHAR(64)     NOT NULL,
    overall_score     DOUBLE PRECISION NOT NULL,
    rolled_up_scores  JSONB           NOT NULL DEFAULT '[]'::jsonb,
    last_evaluated_at TIMESTAMP       NOT NULL DEFAULT NOW(),
    is_deleted        BOOLEAN         NOT NULL DEFAULT FALSE,
    PRIMARY KEY (asset_type, asset_id)
);

CREATE INDEX IF NOT EXISTS idx_dq_score_asset_type ON ecos_dq.dq_score_asset (asset_type, asset_id);

COMMENT ON TABLE  ecos_dq.dq_score_asset IS 'DQ 资产级评分 (每资产 6 维加权汇总, PMO-48-B T8 评分引擎写入)';
COMMENT ON COLUMN ecos_dq.dq_score_asset.asset_type        IS '资产类型: DATASOURCE / TABLE (预留 FIELD/DOMAIN/SYSTEM 扩展)';
COMMENT ON COLUMN ecos_dq.dq_score_asset.asset_id          IS '资产唯一 id (DATASOURCE 时为 datasource_id; TABLE 时为 table_qualified_name)';
COMMENT ON COLUMN ecos_dq.dq_score_asset.overall_score     IS '加权汇总分, 0.0 - 1.0';
COMMENT ON COLUMN ecos_dq.dq_score_asset.rolled_up_scores  IS '各维度明细 JSON: [{dimension, score, weight}, ...]';
COMMENT ON COLUMN ecos_dq.dq_score_asset.last_evaluated_at IS '最后一次评估时间';
COMMENT ON COLUMN ecos_dq.dq_score_asset.is_deleted        IS '逻辑删除标记 (TRUE=软删, 列表过滤)';


-- ============================================================
-- 4. dq_rule 表注释补强 (双轨合并 + V112 加列)
-- ============================================================

COMMENT ON TABLE ecos_dq.dq_rule IS 'DQ 规则主表 (双轨合并后的统一规则表, PMO-48-A T1 + PMO-48-B V112)';


-- ============================================================
-- END V112
-- 产出:
--   ecos_dq.dq_rule          (+ approved_at, + applied_at)  — 2 列幂等加列
--   ecos_dq.dq_score_snapshot  (1 索引: scope_type + scope_id + evaluated_at DESC)
--   ecos_dq.dq_score_asset     (1 索引: asset_type + asset_id)
-- ============================================================
