-- ============================================================================
-- ECOS Cognitive Domain — PostgreSQL DDL
-- Schema: ecos_cognitive
-- Architecture Constraint: ADD-ONLY — never DROP column/table
-- ============================================================================

CREATE SCHEMA IF NOT EXISTS ecos_cognitive;

-- ============================================
-- Table: ecos_wm_goal
-- Description: 世界模型目标表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_cognitive.ecos_wm_goal (
    id                 BIGSERIAL     PRIMARY KEY,
    name               VARCHAR(255)  NOT NULL,
    description        TEXT,
    parent_id          BIGINT,
    goal_type          VARCHAR(32)   DEFAULT 'STRATEGIC',
    weight             INTEGER       DEFAULT 50,
    progress           INTEGER       DEFAULT 0 CHECK (progress >= 0 AND progress <= 100),
    status             VARCHAR(32)   DEFAULT 'PLANNED',
    org_id             VARCHAR(64),
    owner_user_id      VARCHAR(64),
    start_date         DATE,
    end_date           DATE,
    target_value       DECIMAL(18,2),
    current_value      DECIMAL(18,2),
    unit               VARCHAR(32),
    linked_workflow_id VARCHAR(64),
    domain_id          VARCHAR(50),
    kpi_formula        VARCHAR(256),
    measure_frequency  VARCHAR(16)   DEFAULT 'MONTHLY',
    alert_threshold_warn     DECIMAL(5,2) DEFAULT 80.0,
    alert_threshold_critical DECIMAL(5,2) DEFAULT 50.0,
    tenant_id          VARCHAR(64),
    created_at         TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMP     NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_cognitive.ecos_wm_goal IS '世界模型目标表';
COMMENT ON COLUMN ecos_cognitive.ecos_wm_goal.goal_type IS '目标类型: STRATEGIC/TACTICAL/OPERATIONAL';
COMMENT ON COLUMN ecos_cognitive.ecos_wm_goal.kpi_formula IS 'KPI公式';
COMMENT ON COLUMN ecos_cognitive.ecos_wm_goal.measure_frequency IS '度量频率: DAILY/WEEKLY/MONTHLY/QUARTERLY';
CREATE INDEX IF NOT EXISTS idx_cog_goal_parent  ON ecos_cognitive.ecos_wm_goal(parent_id);
CREATE INDEX IF NOT EXISTS idx_cog_goal_status  ON ecos_cognitive.ecos_wm_goal(status);
CREATE INDEX IF NOT EXISTS idx_cog_goal_org     ON ecos_cognitive.ecos_wm_goal(org_id);
CREATE INDEX IF NOT EXISTS idx_cog_goal_tenant  ON ecos_cognitive.ecos_wm_goal(tenant_id);
ALTER TABLE ecos_cognitive.ecos_wm_goal
    ADD CONSTRAINT fk_cog_goal_parent FOREIGN KEY (parent_id) REFERENCES ecos_cognitive.ecos_wm_goal(id);
ALTER TABLE ecos_cognitive.ecos_wm_goal
    ADD CONSTRAINT fk_cog_goal_domain FOREIGN KEY (domain_id) REFERENCES ecos_ontology.ecos_domain(id);

-- ============================================
-- Table: ecos_wm_causal_link
-- Description: 目标因果链表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_cognitive.ecos_wm_causal_link (
    id                     BIGSERIAL PRIMARY KEY,
    source_goal_id         BIGINT    NOT NULL,
    target_goal_id         BIGINT    NOT NULL,
    relationship_type      VARCHAR(32) DEFAULT 'POSITIVE',
    description            TEXT,
    time_lag_days          INTEGER     DEFAULT 0,
    correlation_coefficient DECIMAL(4,3) DEFAULT 0.0,
    created_at             TIMESTAMP   NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_cognitive.ecos_wm_causal_link IS '目标因果链表';
CREATE INDEX IF NOT EXISTS idx_cog_causal_src ON ecos_cognitive.ecos_wm_causal_link(source_goal_id);
CREATE INDEX IF NOT EXISTS idx_cog_causal_tgt ON ecos_cognitive.ecos_wm_causal_link(target_goal_id);
ALTER TABLE ecos_cognitive.ecos_wm_causal_link
    ADD CONSTRAINT fk_cog_causal_src FOREIGN KEY (source_goal_id) REFERENCES ecos_cognitive.ecos_wm_goal(id);
ALTER TABLE ecos_cognitive.ecos_wm_causal_link
    ADD CONSTRAINT fk_cog_causal_tgt FOREIGN KEY (target_goal_id) REFERENCES ecos_cognitive.ecos_wm_goal(id) ON DELETE CASCADE;

-- ============================================
-- Table: ecos_wm_scenario
-- Description: 世界模型场景表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_cognitive.ecos_wm_scenario (
    id           BIGSERIAL     PRIMARY KEY,
    name         VARCHAR(255)  NOT NULL,
    description  TEXT,
    config_json  TEXT          DEFAULT '{}',
    status       VARCHAR(32)   DEFAULT 'DRAFT',
    tenant_id    VARCHAR(64),
    created_at   TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP     NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_cognitive.ecos_wm_scenario IS '世界模型场景表';
CREATE INDEX IF NOT EXISTS idx_cog_wmscen_status ON ecos_cognitive.ecos_wm_scenario(status);

-- ============================================
-- Table: ecos_wm_goal_log
-- Description: 目标变更日志表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_cognitive.ecos_wm_goal_log (
    id           BIGSERIAL  PRIMARY KEY,
    goal_id      BIGINT     NOT NULL,
    change_type  VARCHAR(32),
    old_value    TEXT,
    new_value    TEXT,
    changed_by   VARCHAR(64),
    changed_at   TIMESTAMP  NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_cognitive.ecos_wm_goal_log IS '目标变更日志表';
CREATE INDEX IF NOT EXISTS idx_cog_goallog_goal ON ecos_cognitive.ecos_wm_goal_log(goal_id);
CREATE INDEX IF NOT EXISTS idx_cog_goallog_time ON ecos_cognitive.ecos_wm_goal_log(changed_at);
ALTER TABLE ecos_cognitive.ecos_wm_goal_log
    ADD CONSTRAINT fk_cog_goallog_goal FOREIGN KEY (goal_id) REFERENCES ecos_cognitive.ecos_wm_goal(id) ON DELETE CASCADE;

-- ============================================
-- Table: ecos_goal_tracking
-- Description: 目标追踪表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_cognitive.ecos_goal_tracking (
    id            VARCHAR(36)  PRIMARY KEY,
    goal_id       BIGINT       NOT NULL,
    progress      INTEGER      CHECK (progress >= 0 AND progress <= 100),
    actual_value  NUMERIC(18,2),
    note          TEXT,
    recorded_at   DATE,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_cognitive.ecos_goal_tracking IS '目标追踪表';
CREATE INDEX IF NOT EXISTS idx_cog_tracking_goal ON ecos_cognitive.ecos_goal_tracking(goal_id, recorded_at);
ALTER TABLE ecos_cognitive.ecos_goal_tracking
    ADD CONSTRAINT fk_cog_tracking_goal FOREIGN KEY (goal_id) REFERENCES ecos_cognitive.ecos_wm_goal(id) ON DELETE CASCADE;

-- ============================================
-- Table: ecos_world_scenarios
-- Description: 世界场景配置表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_cognitive.ecos_world_scenarios (
    id          VARCHAR(64)  PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    goal_ids    TEXT         DEFAULT '[]',
    status      VARCHAR(32)  DEFAULT 'draft',
    tenant_id   VARCHAR(64),
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_cognitive.ecos_world_scenarios IS '世界场景配置表';
CREATE INDEX IF NOT EXISTS idx_cog_worldscen_status ON ecos_cognitive.ecos_world_scenarios(status);

-- ============================================
-- Table: ecos_biz_department
-- Description: 业务部门表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_cognitive.ecos_biz_department (
    id          VARCHAR(64)  PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    manager     VARCHAR(64),
    parent_id   VARCHAR(64),
    tenant_id   VARCHAR(64),
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_cognitive.ecos_biz_department IS '业务部门表';
CREATE INDEX IF NOT EXISTS idx_cog_dept_parent ON ecos_cognitive.ecos_biz_department(parent_id);

-- ============================================
-- Table: ecos_biz_project
-- Description: 业务项目表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_cognitive.ecos_biz_project (
    id              VARCHAR(64)  PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    project_type    VARCHAR(32),
    dept_id         VARCHAR(64),
    customer_name   VARCHAR(256),
    contract_amount DECIMAL(18,2),
    status          VARCHAR(32)  DEFAULT 'ACTIVE',
    start_date      DATE,
    end_date        DATE,
    manager         VARCHAR(64),
    goal_id         BIGINT,
    tenant_id       VARCHAR(64),
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_cognitive.ecos_biz_project IS '业务项目表';
CREATE INDEX IF NOT EXISTS idx_cog_proj_dept ON ecos_cognitive.ecos_biz_project(dept_id);
ALTER TABLE ecos_cognitive.ecos_biz_project
    ADD CONSTRAINT fk_cog_proj_goal FOREIGN KEY (goal_id) REFERENCES ecos_cognitive.ecos_wm_goal(id);

-- ============================================
-- Table: ecos_biz_contract
-- Description: 业务合同表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_cognitive.ecos_biz_contract (
    id            VARCHAR(64)  PRIMARY KEY,
    contract_no   VARCHAR(128),
    contract_type VARCHAR(32),
    project_id    VARCHAR(64),
    party_name    VARCHAR(256),
    amount        DECIMAL(18,2),
    signed_date   DATE,
    status        VARCHAR(32) DEFAULT 'ACTIVE',
    tenant_id     VARCHAR(64),
    created_at    TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP   NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_cognitive.ecos_biz_contract IS '业务合同表';
CREATE INDEX IF NOT EXISTS idx_cog_contract_proj ON ecos_cognitive.ecos_biz_contract(project_id);
ALTER TABLE ecos_cognitive.ecos_biz_contract
    ADD CONSTRAINT fk_cog_contract_proj FOREIGN KEY (project_id) REFERENCES ecos_cognitive.ecos_biz_project(id);

-- ============================================
-- Table: ecos_biz_metric
-- Description: 业务指标表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_cognitive.ecos_biz_metric (
    id            BIGSERIAL     PRIMARY KEY,
    dept_id       VARCHAR(64),
    metric_type   VARCHAR(32),
    metric_value  DECIMAL(18,2),
    target_value  DECIMAL(18,2),
    metric_month  VARCHAR(7),
    goal_id       BIGINT,
    tenant_id     VARCHAR(64),
    created_at    TIMESTAMP     NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_cognitive.ecos_biz_metric IS '业务指标表';
CREATE INDEX IF NOT EXISTS idx_cog_metric_dept ON ecos_cognitive.ecos_biz_metric(dept_id);
ALTER TABLE ecos_cognitive.ecos_biz_metric
    ADD CONSTRAINT fk_cog_metric_goal FOREIGN KEY (goal_id) REFERENCES ecos_cognitive.ecos_wm_goal(id);

-- ============================================
-- Table: ecos_biz_target
-- Description: 业务目标表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_cognitive.ecos_biz_target (
    id            BIGSERIAL     PRIMARY KEY,
    dept_id       VARCHAR(64),
    target_type   VARCHAR(32),
    target_value  DECIMAL(18,2),
    target_year   INT,
    goal_id       BIGINT,
    tenant_id     VARCHAR(64),
    created_at    TIMESTAMP     NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_cognitive.ecos_biz_target IS '业务目标表';
ALTER TABLE ecos_cognitive.ecos_biz_target
    ADD CONSTRAINT fk_cog_target_goal FOREIGN KEY (goal_id) REFERENCES ecos_cognitive.ecos_wm_goal(id);

-- ============================================================================
-- 认知真身 11 表（详细设计-05 §E.5 多库基线 / R-13 ① 归位口径）
-- 单源 = docs/50-db_script/02-flyway-migration/gateway/{V163,V187,V188,V191,V192,V193}
--        + 后置 ALTER 列面（V261 注册中心 9 列、V262 probability）
-- 与单源的差异仅限**多库基线特有**的取舍，列名/类型/宽度/默认值零漂移：
--   1) 单源内的 INSERT…SELECT 存量复制段（V191/V192/V193）不入本基线 —— 本文件用于空库重建，
--      无 public 侧旧表可复制；存量对账只在单源迁移链执行；
--   2) V163 的 config_json/result_json 单源实为 JSONB + 条件唯一索引（MC02/MC03 已知偏差，
--      收敛登记在 E.3/V197 轨道）⇒ 基线按合规终态落（TEXT + 含 is_deleted 的普通唯一索引），
--      违例不复制进多库基线；
--   3) 不含 ecos_dw.ecos_application_forecast_run（V197）：确定性计算产物 = 业务域落点，经
--      data-engine 唯一写通道提交，不属控制域 5+1 schema 基线（铁律 §0.6 / ADR-14）。
-- 上方 ecos_wm_* / ecos_biz_* 为 R-13 ① 定性的错放表（0 行、只停写、不 DROP 不迁移，IR03）。
-- 执行状态：仅落脚本文件，未实跑 ecos-postgres（§14.4 未授权项）。
-- ============================================================================

-- --------------------------------------------
-- Table: ecos_cognitive.ecos_cognitive_mind（认知模型资产注册表，V187 / F05-02）
-- --------------------------------------------
CREATE TABLE IF NOT EXISTS ecos_cognitive.ecos_cognitive_mind (
    id                      VARCHAR(36)   NOT NULL,
    mind_key                VARCHAR(120)  NOT NULL,
    name                    VARCHAR(200)  NOT NULL,
    description             TEXT,
    capability_mask_json    TEXT          NOT NULL DEFAULT '[]',
    closed_loop_bounds_json TEXT          NOT NULL DEFAULT '[]',
    status                  VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
    is_active               SMALLINT      NOT NULL DEFAULT 1,
    mind_version            VARCHAR(20)   NOT NULL,
    trace_id                VARCHAR(64),
    domain                  VARCHAR(50)   NOT NULL DEFAULT 'default',
    version_no              VARCHAR(20)   NOT NULL DEFAULT '1',
    is_deleted              SMALLINT      NOT NULL DEFAULT 0,
    create_by               VARCHAR(64)   NOT NULL,
    update_by               VARCHAR(64),
    create_time             TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time             TIMESTAMP,
    CONSTRAINT pk_pg_mirror_cognitive_mind PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uniq_pg_mirror_cm_key_ver
    ON ecos_cognitive.ecos_cognitive_mind (mind_key, mind_version, is_deleted);
CREATE INDEX IF NOT EXISTS idx_pg_mirror_cm_active
    ON ecos_cognitive.ecos_cognitive_mind (is_active, is_deleted);
COMMENT ON TABLE  ecos_cognitive.ecos_cognitive_mind IS '认知模型资产注册表（ADR-8 正名 cognitive_*，禁 kb_* 误名；R-13 ① 归位 ecos_cognitive）';
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_mind.capability_mask_json IS '能力掩码 TEXT JSON 数组；序列化只经 MindCapabilityMaskCodec（REQ-COG-04）';

-- --------------------------------------------
-- Table: ecos_cognitive.ecos_cognitive_scenario_mind（场景↔Mind 绑定表，V188）
-- --------------------------------------------
CREATE TABLE IF NOT EXISTS ecos_cognitive.ecos_cognitive_scenario_mind (
    id            VARCHAR(36)   NOT NULL,
    scenario_id   VARCHAR(36)   NOT NULL,
    mind_id       VARCHAR(36)   NOT NULL,
    binding_role  VARCHAR(20)   NOT NULL DEFAULT 'PRIMARY',
    priority      INTEGER       NOT NULL DEFAULT 100,
    trace_id      VARCHAR(64),
    domain        VARCHAR(50)   NOT NULL DEFAULT 'default',
    version_no    VARCHAR(20)   NOT NULL DEFAULT '1',
    is_deleted    SMALLINT      NOT NULL DEFAULT 0,
    create_by     VARCHAR(64)   NOT NULL,
    update_by     VARCHAR(64),
    create_time   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP,
    CONSTRAINT pk_pg_mirror_cognitive_scn_mind PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uniq_pg_mirror_csm_scn_mind
    ON ecos_cognitive.ecos_cognitive_scenario_mind (scenario_id, mind_id, is_deleted);
CREATE INDEX IF NOT EXISTS idx_pg_mirror_csm_mind
    ON ecos_cognitive.ecos_cognitive_scenario_mind (mind_id, is_deleted);
COMMENT ON TABLE  ecos_cognitive.ecos_cognitive_scenario_mind IS '场景↔Mind 绑定关系表（E1 detect = ecos_cognitive_mind × 本表 JOIN，仅取 is_active=1 且 is_deleted=0）';

-- --------------------------------------------
-- Table: ecos_cognitive.ecos_cognitive_belief（不确定性判断表，V191 + V262）
-- --------------------------------------------
CREATE TABLE IF NOT EXISTS ecos_cognitive.ecos_cognitive_belief (
    id                 VARCHAR(36)   NOT NULL,
    variable_name      VARCHAR(128)  NOT NULL,
    tenant_scope       VARCHAR(64),
    domain             VARCHAR(64)   NOT NULL DEFAULT 'default',
    distribution_json  TEXT          NOT NULL DEFAULT '[]',
    version            INTEGER       NOT NULL DEFAULT 1,
    snapshot_version   INTEGER,
    is_manual_override SMALLINT      NOT NULL DEFAULT 0,
    override_reason    TEXT,
    last_evidence_id   VARCHAR(64),
    status             VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE',
    mind_id            VARCHAR(36),
    scenario_id        VARCHAR(36),
    version_no         VARCHAR(20)   NOT NULL DEFAULT '1',
    is_deleted         SMALLINT      NOT NULL DEFAULT 0,
    create_by          VARCHAR(64)   NOT NULL DEFAULT 'system',
    update_by          VARCHAR(64),
    create_time        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time        TIMESTAMP,
    probability        NUMERIC(6,5),
    CONSTRAINT pk_pg_mirror_cognitive_belief PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uniq_pg_mirror_belief_var_ver
    ON ecos_cognitive.ecos_cognitive_belief (variable_name, domain, version, is_deleted);
CREATE INDEX IF NOT EXISTS idx_pg_mirror_belief_domain ON ecos_cognitive.ecos_cognitive_belief (domain, is_deleted);
CREATE INDEX IF NOT EXISTS idx_pg_mirror_belief_mind   ON ecos_cognitive.ecos_cognitive_belief (mind_id, scenario_id);
CREATE INDEX IF NOT EXISTS idx_pg_mirror_belief_prob   ON ecos_cognitive.ecos_cognitive_belief (probability, is_deleted);
COMMENT ON TABLE  ecos_cognitive.ecos_cognitive_belief IS '不确定性判断表（ADR-9 心智层 P 库，R-13 ① 归位 ecos_cognitive）';
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_belief.probability IS 'E2 算式唯一数值来源（0..1，NUMERIC(6,5)）；NULL=未标定 ⇒ 应用侧从 Top-N 剔除并计 missingProbability';

-- --------------------------------------------
-- Table: ecos_cognitive.ecos_cognitive_hypothesis（认知假设表，V191）
-- --------------------------------------------
CREATE TABLE IF NOT EXISTS ecos_cognitive.ecos_cognitive_hypothesis (
    id                VARCHAR(36)   NOT NULL,
    hypothesis_code   VARCHAR(128)  NOT NULL,
    tenant_scope      VARCHAR(64),
    statement         TEXT          NOT NULL,
    domain            VARCHAR(64)   NOT NULL DEFAULT 'default',
    metric_ref        VARCHAR(128),
    evidence_ids_json TEXT          NOT NULL DEFAULT '[]',
    is_valid          SMALLINT      NOT NULL DEFAULT 1,
    invalid_at        TIMESTAMP,
    invalid_reason    TEXT,
    status            VARCHAR(16)   NOT NULL DEFAULT 'VALID',
    old_status        VARCHAR(20),
    mind_id           VARCHAR(36),
    scenario_id       VARCHAR(36),
    version_no        VARCHAR(20)   NOT NULL DEFAULT '1',
    is_deleted        SMALLINT      NOT NULL DEFAULT 0,
    create_by         VARCHAR(64)   NOT NULL DEFAULT 'system',
    update_by         VARCHAR(64),
    create_time       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time       TIMESTAMP,
    CONSTRAINT pk_pg_mirror_cognitive_hypothesis PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uniq_pg_mirror_hypo_code ON ecos_cognitive.ecos_cognitive_hypothesis (hypothesis_code, is_deleted);
CREATE INDEX IF NOT EXISTS idx_pg_mirror_hypo_domain        ON ecos_cognitive.ecos_cognitive_hypothesis (domain, invalid_at);
CREATE INDEX IF NOT EXISTS idx_pg_mirror_hypo_mind          ON ecos_cognitive.ecos_cognitive_hypothesis (mind_id, scenario_id);
COMMENT ON TABLE  ecos_cognitive.ecos_cognitive_hypothesis IS '认知假设表（ADR-9 心智层 H 库；status 终态 4 值枚举 R-15 ①，old_status 存映射前留痕）';

-- --------------------------------------------
-- Table: ecos_cognitive.ecos_cognitive_evidence（认知证据表，V191）
-- --------------------------------------------
CREATE TABLE IF NOT EXISTS ecos_cognitive.ecos_cognitive_evidence (
    id                         VARCHAR(36)   NOT NULL,
    evidence_code              VARCHAR(128)  NOT NULL,
    tenant_scope               VARCHAR(64),
    source_type                VARCHAR(32)   NOT NULL,
    source_ref                 VARCHAR(256),
    blob_json                  TEXT          NOT NULL DEFAULT '{}',
    confidence                 NUMERIC(5,4)  NOT NULL DEFAULT 0.5,
    is_conflict                SMALLINT      NOT NULL DEFAULT 0,
    refuting_evidence_ids_json TEXT          NOT NULL DEFAULT '[]',
    effective_time             TIMESTAMP,
    expire_time                TIMESTAMP,
    status                     VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE',
    domain                     VARCHAR(50)   NOT NULL DEFAULT 'default',
    version_no                 VARCHAR(20)   NOT NULL DEFAULT '1',
    is_deleted                 SMALLINT      NOT NULL DEFAULT 0,
    create_by                  VARCHAR(64)   NOT NULL DEFAULT 'system',
    update_by                  VARCHAR(64),
    create_time                TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time                TIMESTAMP,
    CONSTRAINT pk_pg_mirror_cognitive_evidence PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uniq_pg_mirror_ev_code ON ecos_cognitive.ecos_cognitive_evidence (evidence_code, is_deleted);
CREATE INDEX IF NOT EXISTS idx_pg_mirror_ev_source        ON ecos_cognitive.ecos_cognitive_evidence (source_type, status);
CREATE INDEX IF NOT EXISTS idx_pg_mirror_ev_time          ON ecos_cognitive.ecos_cognitive_evidence (create_time);
COMMENT ON TABLE  ecos_cognitive.ecos_cognitive_evidence IS '认知证据表（ADR-9 感知/心智层；blob/refuting 原二进制 JSON 已合规化为 TEXT）';

-- --------------------------------------------
-- Table: ecos_cognitive.ecos_cognitive_model（认知模型注册表，V192 + V261 注册中心列）
-- --------------------------------------------
CREATE TABLE IF NOT EXISTS ecos_cognitive.ecos_cognitive_model (
    id                   VARCHAR(36)   NOT NULL,
    model_id             VARCHAR(64)   NOT NULL,
    model_type           VARCHAR(32)   NOT NULL,
    version              INTEGER       NOT NULL DEFAULT 1,
    features_json        TEXT,
    backtest_score       NUMERIC(8,6),
    status               VARCHAR(16)   NOT NULL DEFAULT 'active',
    domain               VARCHAR(50)   NOT NULL DEFAULT 'default',
    version_no           VARCHAR(20)   NOT NULL DEFAULT '1',
    is_deleted           SMALLINT      NOT NULL DEFAULT 0,
    create_by            VARCHAR(64)   NOT NULL DEFAULT 'system',
    update_by            VARCHAR(64),
    create_time          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time          TIMESTAMP,
    cognitive_model_type VARCHAR(8),
    registry_state       VARCHAR(16)   NOT NULL DEFAULT 'DRAFT',
    content_hash         VARCHAR(64),
    supersedes_id        VARCHAR(36),
    gate_contract        VARCHAR(16)   NOT NULL DEFAULT 'PENDING',
    gate_regression      VARCHAR(16)   NOT NULL DEFAULT 'PENDING',
    gate_backtest        VARCHAR(16)   NOT NULL DEFAULT 'PENDING',
    gate_calibration     VARCHAR(16)   NOT NULL DEFAULT 'PENDING',
    published_at         TIMESTAMP,
    published_by         VARCHAR(64),
    CONSTRAINT pk_pg_mirror_cognitive_model PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uniq_pg_mirror_model_id_ver ON ecos_cognitive.ecos_cognitive_model (model_id, version, is_deleted);
CREATE INDEX IF NOT EXISTS idx_pg_mirror_model_type           ON ecos_cognitive.ecos_cognitive_model (model_type, status);
CREATE INDEX IF NOT EXISTS idx_pg_mirror_model_domain         ON ecos_cognitive.ecos_cognitive_model (domain, is_deleted);
CREATE INDEX IF NOT EXISTS idx_pg_mirror_model_state          ON ecos_cognitive.ecos_cognitive_model (registry_state, is_deleted);
CREATE INDEX IF NOT EXISTS idx_pg_mirror_model_cm             ON ecos_cognitive.ecos_cognitive_model (cognitive_model_type, registry_state);
CREATE INDEX IF NOT EXISTS idx_pg_mirror_model_supersed       ON ecos_cognitive.ecos_cognitive_model (supersedes_id);
COMMENT ON TABLE  ecos_cognitive.ecos_cognitive_model IS '认知模型注册表（ADR-8 模型资产落盘；REQ-COG-07：CM-01~07 类型 + 四类发布门禁全 PASS 才升 PUBLISHED + 发布行不可变）';

-- --------------------------------------------
-- Table: ecos_cognitive.ecos_cognitive_run_invalidation（作废推演结论留痕表，V192）
-- --------------------------------------------
CREATE TABLE IF NOT EXISTS ecos_cognitive.ecos_cognitive_run_invalidation (
    id            VARCHAR(36)   NOT NULL,
    event_id      VARCHAR(64)   NOT NULL,
    hypothesis_id VARCHAR(64)   NOT NULL,
    run_id        VARCHAR(64)   NOT NULL,
    auto_detected SMALLINT      NOT NULL DEFAULT 1,
    superseded_at TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    detail        TEXT,
    domain        VARCHAR(50)   NOT NULL DEFAULT 'default',
    version_no    VARCHAR(20)   NOT NULL DEFAULT '1',
    is_deleted    SMALLINT      NOT NULL DEFAULT 0,
    create_by     VARCHAR(64)   NOT NULL DEFAULT 'system',
    update_by     VARCHAR(64),
    create_time   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP,
    CONSTRAINT pk_pg_mirror_run_invalidation PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uniq_pg_mirror_run_inv_evt_run ON ecos_cognitive.ecos_cognitive_run_invalidation (event_id, run_id, is_deleted);
CREATE INDEX IF NOT EXISTS idx_pg_mirror_run_inv_run             ON ecos_cognitive.ecos_cognitive_run_invalidation (run_id);
CREATE INDEX IF NOT EXISTS idx_pg_mirror_run_inv_hyp             ON ecos_cognitive.ecos_cognitive_run_invalidation (hypothesis_id);
CREATE INDEX IF NOT EXISTS idx_pg_mirror_run_inv_time            ON ecos_cognitive.ecos_cognitive_run_invalidation (superseded_at);
COMMENT ON TABLE  ecos_cognitive.ecos_cognitive_run_invalidation IS '失效→作废推演结论关联留痕表（ADR-9 心智事件；run_id 指向 ecos_dw 产物面，随 R-17 ①）';

-- --------------------------------------------
-- Table: ecos_cognitive.ecos_cognitive_pipeline（认知管线定义表，V163 单源；本段按 MC02/MC03 合规形态镜像）
-- --------------------------------------------
CREATE TABLE IF NOT EXISTS ecos_cognitive.ecos_cognitive_pipeline (
    id          VARCHAR(36)   NOT NULL,
    pipeline_id VARCHAR(64)   NOT NULL,
    name        VARCHAR(255),
    status      VARCHAR(32)   NOT NULL DEFAULT 'DRAFT',
    config_json TEXT,
    result_json TEXT,
    create_by   VARCHAR(100),
    update_by   VARCHAR(100),
    create_time TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted  SMALLINT      NOT NULL DEFAULT 0,
    domain      VARCHAR(50)   NOT NULL DEFAULT 'default',
    version_no  VARCHAR(20)   NOT NULL DEFAULT '1',
    CONSTRAINT pk_pg_mirror_cognitive_pipeline PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uniq_pg_mirror_pipeline_pid ON ecos_cognitive.ecos_cognitive_pipeline (pipeline_id, is_deleted);
CREATE INDEX IF NOT EXISTS idx_pg_mirror_pipeline_domain      ON ecos_cognitive.ecos_cognitive_pipeline (domain);
COMMENT ON TABLE  ecos_cognitive.ecos_cognitive_pipeline IS '认知管线定义表（cognitive-engine 自有，ST07 收编；旧 public.kb_cognitive_pipeline 停写保留）';

-- --------------------------------------------
-- Table: ecos_cognitive.ecos_business_scenario（业务场景主表，V193）
-- --------------------------------------------
CREATE TABLE IF NOT EXISTS ecos_cognitive.ecos_business_scenario (
    id                  VARCHAR(36)   NOT NULL,
    name                VARCHAR(255)  NOT NULL,
    description         TEXT          DEFAULT '',
    business_goal       TEXT          DEFAULT '',
    department          VARCHAR(128),
    priority            VARCHAR(16)            DEFAULT 'MEDIUM',
    status              VARCHAR(32)            DEFAULT 'DRAFT',
    budget              VARCHAR(64),
    safety_index_target NUMERIC(8,4),
    actual_safety_index NUMERIC(8,4),
    metrics_json        TEXT,
    domain              VARCHAR(50)   NOT NULL DEFAULT 'default',
    version_no          VARCHAR(20)   NOT NULL DEFAULT '1',
    is_deleted          SMALLINT      NOT NULL DEFAULT 0,
    create_by           VARCHAR(64)   NOT NULL DEFAULT 'system',
    update_by           VARCHAR(64),
    create_time         TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time         TIMESTAMP,
    CONSTRAINT pk_pg_mirror_business_scenario PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_pg_mirror_bizscn_status ON ecos_cognitive.ecos_business_scenario (status, is_deleted);
CREATE INDEX IF NOT EXISTS idx_pg_mirror_bizscn_dept   ON ecos_cognitive.ecos_business_scenario (department, is_deleted);
COMMENT ON TABLE  ecos_cognitive.ecos_business_scenario IS '业务场景主表（认知域真身之一，R-13 ① 归位；budget 旧串值形态原样承接，金额实列化属场景层 v2/V205 轨道）';

-- --------------------------------------------
-- Table: ecos_cognitive.ecos_scenario_binding（场景六类绑定表，V193）
-- --------------------------------------------
CREATE TABLE IF NOT EXISTS ecos_cognitive.ecos_scenario_binding (
    id           VARCHAR(36)   NOT NULL,
    scenario_id  VARCHAR(64)   NOT NULL,
    binding_type VARCHAR(32)   NOT NULL,
    target_ref   VARCHAR(255)  NOT NULL,
    target_id    VARCHAR(36),
    target_type  VARCHAR(20),
    remark       TEXT          DEFAULT '',
    domain       VARCHAR(50)   NOT NULL DEFAULT 'default',
    version_no   VARCHAR(20)   NOT NULL DEFAULT '1',
    is_deleted   SMALLINT      NOT NULL DEFAULT 0,
    create_by    VARCHAR(64)   NOT NULL DEFAULT 'system',
    update_by    VARCHAR(64),
    create_time  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  TIMESTAMP,
    CONSTRAINT pk_pg_mirror_scenario_binding PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_pg_mirror_bind_scn  ON ecos_cognitive.ecos_scenario_binding (scenario_id, is_deleted);
CREATE INDEX IF NOT EXISTS idx_pg_mirror_bind_type ON ecos_cognitive.ecos_scenario_binding (binding_type, is_deleted);
CREATE INDEX IF NOT EXISTS idx_pg_mirror_bind_tid  ON ecos_cognitive.ecos_scenario_binding (target_id, target_type);
COMMENT ON TABLE  ecos_cognitive.ecos_scenario_binding IS '场景六类绑定表（target_ref 旧别名只读兼容；target_id/target_type 真引用列，值域以 V199 契约为准）';

-- --------------------------------------------
-- Table: ecos_cognitive.ecos_scenario_binding_link（场景绑定关系边表，V193）
-- --------------------------------------------
CREATE TABLE IF NOT EXISTS ecos_cognitive.ecos_scenario_binding_link (
    id                VARCHAR(36)   NOT NULL,
    scenario_id       VARCHAR(64)   NOT NULL,
    source_binding_id VARCHAR(64)   NOT NULL,
    target_binding_id VARCHAR(64)   NOT NULL,
    link_type         VARCHAR(16)   NOT NULL,
    source_contract   VARCHAR(128),
    remark            TEXT          DEFAULT '',
    domain            VARCHAR(50)   NOT NULL DEFAULT 'default',
    version_no        VARCHAR(20)   NOT NULL DEFAULT '1',
    is_deleted        SMALLINT      NOT NULL DEFAULT 0,
    create_by         VARCHAR(64)   NOT NULL DEFAULT 'system',
    update_by         VARCHAR(64),
    create_time       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time       TIMESTAMP,
    CONSTRAINT pk_pg_mirror_binding_link PRIMARY KEY (id),
    CONSTRAINT ck_pg_mirror_blink_link_type CHECK (
        link_type IN ('MAPPING', 'EXTRACTION', 'COGNITION', 'GOVERN', 'EXPOSE')
    )
);
CREATE INDEX IF NOT EXISTS idx_pg_mirror_blink_scn ON ecos_cognitive.ecos_scenario_binding_link (scenario_id, is_deleted);
CREATE INDEX IF NOT EXISTS idx_pg_mirror_blink_src ON ecos_cognitive.ecos_scenario_binding_link (source_binding_id);
CREATE INDEX IF NOT EXISTS idx_pg_mirror_blink_tgt ON ecos_cognitive.ecos_scenario_binding_link (target_binding_id);
CREATE INDEX IF NOT EXISTS idx_pg_mirror_blink_type ON ecos_cognitive.ecos_scenario_binding_link (link_type);
COMMENT ON TABLE  ecos_cognitive.ecos_scenario_binding_link IS '场景绑定关系边表（§0.6.2；link_type 值域与 V160 完全一致，非新枚举收敛）';
