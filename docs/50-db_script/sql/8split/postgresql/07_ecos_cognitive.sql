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
