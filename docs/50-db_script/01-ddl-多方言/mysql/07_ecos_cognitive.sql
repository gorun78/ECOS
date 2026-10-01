-- ============================================================================
-- ECOS Cognitive Domain — MySQL DDL
-- Architecture Constraint: ADD-ONLY — never DROP column/table
-- ============================================================================

-- ============================================
-- Table: ecos_wm_goal
-- Description: 世界模型目标表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_wm_goal (
    id                      BIGINT        NOT NULL AUTO_INCREMENT,
    name                    VARCHAR(255)  NOT NULL,
    description             LONGTEXT,
    parent_id               BIGINT,
    goal_type               VARCHAR(32)   DEFAULT 'STRATEGIC',
    weight                  INT           DEFAULT 50,
    progress                INT           DEFAULT 0,
    status                  VARCHAR(32)   DEFAULT 'PLANNED',
    org_id                  VARCHAR(64),
    owner_user_id           VARCHAR(64),
    start_date              DATE,
    end_date                DATE,
    target_value            DECIMAL(18,2),
    current_value           DECIMAL(18,2),
    unit                    VARCHAR(32),
    linked_workflow_id      VARCHAR(64),
    domain_id               VARCHAR(50),
    kpi_formula             VARCHAR(256),
    measure_frequency       VARCHAR(16)   DEFAULT 'MONTHLY',
    alert_threshold_warn    DECIMAL(5,2)  DEFAULT 80.0,
    alert_threshold_critical DECIMAL(5,2) DEFAULT 50.0,
    tenant_id               VARCHAR(64),
    created_at              DATETIME      NOT NULL DEFAULT NOW(),
    updated_at              DATETIME      NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id),
    CONSTRAINT chk_cog_goal_progress CHECK (progress >= 0 AND progress <= 100)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='世界模型目标表';

CREATE INDEX idx_cog_goal_parent  ON ecos_wm_goal(parent_id);
CREATE INDEX idx_cog_goal_status  ON ecos_wm_goal(status);
CREATE INDEX idx_cog_goal_org     ON ecos_wm_goal(org_id);
CREATE INDEX idx_cog_goal_tenant  ON ecos_wm_goal(tenant_id);

-- ============================================
-- Table: ecos_wm_causal_link
-- Description: 目标因果链表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_wm_causal_link (
    id                      BIGINT        NOT NULL AUTO_INCREMENT,
    source_goal_id          BIGINT        NOT NULL,
    target_goal_id          BIGINT        NOT NULL,
    relationship_type       VARCHAR(32)   DEFAULT 'POSITIVE',
    description             LONGTEXT,
    time_lag_days           INT           DEFAULT 0,
    correlation_coefficient DECIMAL(4,3)  DEFAULT 0.0,
    created_at              DATETIME      NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='目标因果链表';

CREATE INDEX idx_cog_causal_src ON ecos_wm_causal_link(source_goal_id);
CREATE INDEX idx_cog_causal_tgt ON ecos_wm_causal_link(target_goal_id);

-- ============================================
-- Table: ecos_wm_scenario
-- Description: 世界模型场景表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_wm_scenario (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    name         VARCHAR(255)  NOT NULL,
    description  LONGTEXT,
    config_json  LONGTEXT,
    status       VARCHAR(32)   DEFAULT 'DRAFT',
    tenant_id    VARCHAR(64),
    created_at   DATETIME      NOT NULL DEFAULT NOW(),
    updated_at   DATETIME      NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='世界模型场景表';

CREATE INDEX idx_cog_wmscen_status ON ecos_wm_scenario(status);

-- ============================================
-- Table: ecos_wm_goal_log
-- Description: 目标变更日志表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_wm_goal_log (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    goal_id      BIGINT        NOT NULL,
    change_type  VARCHAR(32),
    old_value    LONGTEXT,
    new_value    LONGTEXT,
    changed_by   VARCHAR(64),
    changed_at   DATETIME      NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='目标变更日志表';

CREATE INDEX idx_cog_goallog_goal ON ecos_wm_goal_log(goal_id);
CREATE INDEX idx_cog_goallog_time ON ecos_wm_goal_log(changed_at);

-- ============================================
-- Table: ecos_goal_tracking
-- Description: 目标追踪表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_goal_tracking (
    id            VARCHAR(36)   NOT NULL,
    goal_id       BIGINT        NOT NULL,
    progress      INT,
    actual_value  DECIMAL(18,2),
    note          LONGTEXT,
    recorded_at   DATE,
    created_at    DATETIME      NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id),
    CONSTRAINT chk_cog_tracking_progress CHECK (progress >= 0 AND progress <= 100)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='目标追踪表';

CREATE INDEX idx_cog_tracking_goal ON ecos_goal_tracking(goal_id, recorded_at);

-- ============================================
-- Table: ecos_world_scenarios
-- Description: 世界场景配置表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_world_scenarios (
    id          VARCHAR(64)   NOT NULL,
    name        VARCHAR(255)  NOT NULL,
    description LONGTEXT,
    goal_ids    LONGTEXT,
    status      VARCHAR(32)   DEFAULT 'draft',
    tenant_id   VARCHAR(64),
    created_at  DATETIME      NOT NULL DEFAULT NOW(),
    updated_at  DATETIME      NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='世界场景配置表';

CREATE INDEX idx_cog_worldscen_status ON ecos_world_scenarios(status);

-- ============================================
-- Table: ecos_biz_department
-- Description: 业务部门表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_biz_department (
    id          VARCHAR(64)   NOT NULL,
    name        VARCHAR(255)  NOT NULL,
    manager     VARCHAR(64),
    parent_id   VARCHAR(64),
    tenant_id   VARCHAR(64),
    created_at  DATETIME      NOT NULL DEFAULT NOW(),
    updated_at  DATETIME      NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='业务部门表';

CREATE INDEX idx_cog_dept_parent ON ecos_biz_department(parent_id);

-- ============================================
-- Table: ecos_biz_project
-- Description: 业务项目表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_biz_project (
    id              VARCHAR(64)   NOT NULL,
    name            VARCHAR(255)  NOT NULL,
    project_type    VARCHAR(32),
    dept_id         VARCHAR(64),
    customer_name   VARCHAR(256),
    contract_amount DECIMAL(18,2),
    status          VARCHAR(32)   DEFAULT 'ACTIVE',
    start_date      DATE,
    end_date        DATE,
    manager         VARCHAR(64),
    goal_id         BIGINT,
    tenant_id       VARCHAR(64),
    created_at      DATETIME      NOT NULL DEFAULT NOW(),
    updated_at      DATETIME      NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='业务项目表';

CREATE INDEX idx_cog_proj_dept ON ecos_biz_project(dept_id);

-- ============================================
-- Table: ecos_biz_contract
-- Description: 业务合同表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_biz_contract (
    id            VARCHAR(64)   NOT NULL,
    contract_no   VARCHAR(128),
    contract_type VARCHAR(32),
    project_id    VARCHAR(64),
    party_name    VARCHAR(256),
    amount        DECIMAL(18,2),
    signed_date   DATE,
    status        VARCHAR(32)   DEFAULT 'ACTIVE',
    tenant_id     VARCHAR(64),
    created_at    DATETIME      NOT NULL DEFAULT NOW(),
    updated_at    DATETIME      NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='业务合同表';

CREATE INDEX idx_cog_contract_proj ON ecos_biz_contract(project_id);

-- ============================================
-- Table: ecos_biz_metric
-- Description: 业务指标表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_biz_metric (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    dept_id       VARCHAR(64),
    metric_type   VARCHAR(32),
    metric_value  DECIMAL(18,2),
    target_value  DECIMAL(18,2),
    metric_month  VARCHAR(7),
    goal_id       BIGINT,
    tenant_id     VARCHAR(64),
    created_at    DATETIME      NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='业务指标表';

CREATE INDEX idx_cog_metric_dept ON ecos_biz_metric(dept_id);

-- ============================================
-- Table: ecos_biz_target
-- Description: 业务目标表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_biz_target (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    dept_id       VARCHAR(64),
    target_type   VARCHAR(32),
    target_value  DECIMAL(18,2),
    target_year   INT,
    goal_id       BIGINT,
    tenant_id     VARCHAR(64),
    created_at    DATETIME      NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='业务目标表';
