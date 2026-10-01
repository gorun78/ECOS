-- ============================================================================
-- ECOS Cognitive Domain — Oracle DDL
-- Schema: ecos_cognitive
-- Architecture Constraint: ADD-ONLY — never DROP column/table
-- ============================================================================

BEGIN
   EXECUTE IMMEDIATE 'CREATE USER ecos_cognitive IDENTIFIED BY ecos_cognitive DEFAULT TABLESPACE USERS QUOTA UNLIMITED ON USERS';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

GRANT CREATE SESSION, CREATE TABLE, CREATE SEQUENCE, CREATE INDEX TO ecos_cognitive;

-- ============================================
-- Sequence for ecos_wm_goal
-- ============================================
BEGIN
   EXECUTE IMMEDIATE 'CREATE SEQUENCE ecos_cognitive.seq_wm_goal START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

-- ============================================
-- Table: ecos_wm_goal
-- Description: 世界模型目标表
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_cognitive.ecos_wm_goal (
    id                 NUMBER(19)     PRIMARY KEY,
    name               VARCHAR2(255)  NOT NULL,
    description        CLOB,
    parent_id          NUMBER(19),
    goal_type          VARCHAR2(32)   DEFAULT ''STRATEGIC'',
    weight             NUMBER(10)     DEFAULT 50,
    progress           NUMBER(10)     DEFAULT 0 CHECK (progress >= 0 AND progress <= 100),
    status             VARCHAR2(32)   DEFAULT ''PLANNED'',
    org_id             VARCHAR2(64),
    owner_user_id      VARCHAR2(64),
    start_date         DATE,
    end_date           DATE,
    target_value       NUMBER(18,2),
    current_value      NUMBER(18,2),
    unit               VARCHAR2(32),
    linked_workflow_id VARCHAR2(64),
    domain_id          VARCHAR2(50),
    kpi_formula        VARCHAR2(256),
    measure_frequency  VARCHAR2(16)   DEFAULT ''MONTHLY'',
    alert_threshold_warn     NUMBER(5,2) DEFAULT 80.0,
    alert_threshold_critical NUMBER(5,2) DEFAULT 50.0,
    tenant_id          VARCHAR2(64),
    created_at         TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at         TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_cognitive.ecos_wm_goal IS '世界模型目标表';
COMMENT ON COLUMN ecos_cognitive.ecos_wm_goal.goal_type IS '目标类型: STRATEGIC/TACTICAL/OPERATIONAL';
COMMENT ON COLUMN ecos_cognitive.ecos_wm_goal.kpi_formula IS 'KPI公式';
COMMENT ON COLUMN ecos_cognitive.ecos_wm_goal.measure_frequency IS '度量频率: DAILY/WEEKLY/MONTHLY/QUARTERLY';

BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_cog_goal_parent ON ecos_cognitive.ecos_wm_goal(parent_id)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_cog_goal_status ON ecos_cognitive.ecos_wm_goal(status)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_cog_goal_org ON ecos_cognitive.ecos_wm_goal(org_id)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_cog_goal_tenant ON ecos_cognitive.ecos_wm_goal(tenant_id)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
   EXECUTE IMMEDIATE 'ALTER TABLE ecos_cognitive.ecos_wm_goal ADD CONSTRAINT fk_cog_goal_parent FOREIGN KEY (parent_id) REFERENCES ecos_cognitive.ecos_wm_goal(id)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
   EXECUTE IMMEDIATE 'ALTER TABLE ecos_cognitive.ecos_wm_goal ADD CONSTRAINT fk_cog_goal_domain FOREIGN KEY (domain_id) REFERENCES ecos_ontology.ecos_domain(id)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

CREATE OR REPLACE TRIGGER ecos_cognitive.trg_wm_goal_id
BEFORE INSERT ON ecos_cognitive.ecos_wm_goal
FOR EACH ROW
BEGIN
   IF :NEW.id IS NULL THEN
      :NEW.id := ecos_cognitive.seq_wm_goal.NEXTVAL;
   END IF;
END;
/

-- ============================================
-- Sequence for ecos_wm_causal_link
-- ============================================
BEGIN
   EXECUTE IMMEDIATE 'CREATE SEQUENCE ecos_cognitive.seq_wm_causal_link START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

-- ============================================
-- Table: ecos_wm_causal_link
-- Description: 目标因果链表
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_cognitive.ecos_wm_causal_link (
    id                     NUMBER(19) PRIMARY KEY,
    source_goal_id         NUMBER(19)   NOT NULL,
    target_goal_id         NUMBER(19)   NOT NULL,
    relationship_type      VARCHAR2(32) DEFAULT ''POSITIVE'',
    description            CLOB,
    time_lag_days          NUMBER(10)   DEFAULT 0,
    correlation_coefficient NUMBER(4,3) DEFAULT 0.0,
    created_at             TIMESTAMP    DEFAULT SYSTIMESTAMP NOT NULL
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_cognitive.ecos_wm_causal_link IS '目标因果链表';

BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_cog_causal_src ON ecos_cognitive.ecos_wm_causal_link(source_goal_id)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_cog_causal_tgt ON ecos_cognitive.ecos_wm_causal_link(target_goal_id)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
   EXECUTE IMMEDIATE 'ALTER TABLE ecos_cognitive.ecos_wm_causal_link ADD CONSTRAINT fk_cog_causal_src FOREIGN KEY (source_goal_id) REFERENCES ecos_cognitive.ecos_wm_goal(id)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
   EXECUTE IMMEDIATE 'ALTER TABLE ecos_cognitive.ecos_wm_causal_link ADD CONSTRAINT fk_cog_causal_tgt FOREIGN KEY (target_goal_id) REFERENCES ecos_cognitive.ecos_wm_goal(id) ON DELETE CASCADE';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

CREATE OR REPLACE TRIGGER ecos_cognitive.trg_wm_causal_link_id
BEFORE INSERT ON ecos_cognitive.ecos_wm_causal_link
FOR EACH ROW
BEGIN
   IF :NEW.id IS NULL THEN
      :NEW.id := ecos_cognitive.seq_wm_causal_link.NEXTVAL;
   END IF;
END;
/

-- ============================================
-- Sequence for ecos_wm_scenario
-- ============================================
BEGIN
   EXECUTE IMMEDIATE 'CREATE SEQUENCE ecos_cognitive.seq_wm_scenario START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

-- ============================================
-- Table: ecos_wm_scenario
-- Description: 世界模型场景表
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_cognitive.ecos_wm_scenario (
    id           NUMBER(19)     PRIMARY KEY,
    name         VARCHAR2(255)  NOT NULL,
    description  CLOB,
    config_json  CLOB           DEFAULT ''{}'',
    status       VARCHAR2(32)   DEFAULT ''DRAFT'',
    tenant_id    VARCHAR2(64),
    created_at   TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at   TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_cognitive.ecos_wm_scenario IS '世界模型场景表';

BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_cog_wmscen_status ON ecos_cognitive.ecos_wm_scenario(status)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

CREATE OR REPLACE TRIGGER ecos_cognitive.trg_wm_scenario_id
BEFORE INSERT ON ecos_cognitive.ecos_wm_scenario
FOR EACH ROW
BEGIN
   IF :NEW.id IS NULL THEN
      :NEW.id := ecos_cognitive.seq_wm_scenario.NEXTVAL;
   END IF;
END;
/

-- ============================================
-- Sequence for ecos_wm_goal_log
-- ============================================
BEGIN
   EXECUTE IMMEDIATE 'CREATE SEQUENCE ecos_cognitive.seq_wm_goal_log START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

-- ============================================
-- Table: ecos_wm_goal_log
-- Description: 目标变更日志表
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_cognitive.ecos_wm_goal_log (
    id           NUMBER(19)  PRIMARY KEY,
    goal_id      NUMBER(19)  NOT NULL,
    change_type  VARCHAR2(32),
    old_value    CLOB,
    new_value    CLOB,
    changed_by   VARCHAR2(64),
    changed_at   TIMESTAMP   DEFAULT SYSTIMESTAMP NOT NULL
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_cognitive.ecos_wm_goal_log IS '目标变更日志表';

BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_cog_goallog_goal ON ecos_cognitive.ecos_wm_goal_log(goal_id)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_cog_goallog_time ON ecos_cognitive.ecos_wm_goal_log(changed_at)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
   EXECUTE IMMEDIATE 'ALTER TABLE ecos_cognitive.ecos_wm_goal_log ADD CONSTRAINT fk_cog_goallog_goal FOREIGN KEY (goal_id) REFERENCES ecos_cognitive.ecos_wm_goal(id) ON DELETE CASCADE';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

CREATE OR REPLACE TRIGGER ecos_cognitive.trg_wm_goal_log_id
BEFORE INSERT ON ecos_cognitive.ecos_wm_goal_log
FOR EACH ROW
BEGIN
   IF :NEW.id IS NULL THEN
      :NEW.id := ecos_cognitive.seq_wm_goal_log.NEXTVAL;
   END IF;
END;
/

-- ============================================
-- Table: ecos_goal_tracking
-- Description: 目标追踪表
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_cognitive.ecos_goal_tracking (
    id            VARCHAR2(36)  PRIMARY KEY,
    goal_id       NUMBER(19)    NOT NULL,
    progress      NUMBER(10)    CHECK (progress >= 0 AND progress <= 100),
    actual_value  NUMBER(18,2),
    note          CLOB,
    recorded_at   DATE,
    created_at    TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_cognitive.ecos_goal_tracking IS '目标追踪表';

BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_cog_tracking_goal ON ecos_cognitive.ecos_goal_tracking(goal_id, recorded_at)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
   EXECUTE IMMEDIATE 'ALTER TABLE ecos_cognitive.ecos_goal_tracking ADD CONSTRAINT fk_cog_tracking_goal FOREIGN KEY (goal_id) REFERENCES ecos_cognitive.ecos_wm_goal(id) ON DELETE CASCADE';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

-- ============================================
-- Table: ecos_world_scenarios
-- Description: 世界场景配置表
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_cognitive.ecos_world_scenarios (
    id          VARCHAR2(64)  PRIMARY KEY,
    name        VARCHAR2(255) NOT NULL,
    description CLOB,
    goal_ids    CLOB          DEFAULT ''[]'',
    status      VARCHAR2(32)  DEFAULT ''draft'',
    tenant_id   VARCHAR2(64),
    created_at  TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at  TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_cognitive.ecos_world_scenarios IS '世界场景配置表';

BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_cog_worldscen_status ON ecos_cognitive.ecos_world_scenarios(status)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

-- ============================================
-- Table: ecos_biz_department
-- Description: 业务部门表
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_cognitive.ecos_biz_department (
    id          VARCHAR2(64)  PRIMARY KEY,
    name        VARCHAR2(255) NOT NULL,
    manager     VARCHAR2(64),
    parent_id   VARCHAR2(64),
    tenant_id   VARCHAR2(64),
    created_at  TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at  TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_cognitive.ecos_biz_department IS '业务部门表';

BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_cog_dept_parent ON ecos_cognitive.ecos_biz_department(parent_id)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

-- ============================================
-- Table: ecos_biz_project
-- Description: 业务项目表
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_cognitive.ecos_biz_project (
    id              VARCHAR2(64)  PRIMARY KEY,
    name            VARCHAR2(255) NOT NULL,
    project_type    VARCHAR2(32),
    dept_id         VARCHAR2(64),
    customer_name   VARCHAR2(256),
    contract_amount NUMBER(18,2),
    status          VARCHAR2(32)  DEFAULT ''ACTIVE'',
    start_date      DATE,
    end_date        DATE,
    manager         VARCHAR2(64),
    goal_id         NUMBER(19),
    tenant_id       VARCHAR2(64),
    created_at      TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at      TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_cognitive.ecos_biz_project IS '业务项目表';

BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_cog_proj_dept ON ecos_cognitive.ecos_biz_project(dept_id)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
   EXECUTE IMMEDIATE 'ALTER TABLE ecos_cognitive.ecos_biz_project ADD CONSTRAINT fk_cog_proj_goal FOREIGN KEY (goal_id) REFERENCES ecos_cognitive.ecos_wm_goal(id)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

-- ============================================
-- Table: ecos_biz_contract
-- Description: 业务合同表
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_cognitive.ecos_biz_contract (
    id            VARCHAR2(64)  PRIMARY KEY,
    contract_no   VARCHAR2(128),
    contract_type VARCHAR2(32),
    project_id    VARCHAR2(64),
    party_name    VARCHAR2(256),
    amount        NUMBER(18,2),
    signed_date   DATE,
    status        VARCHAR2(32) DEFAULT ''ACTIVE'',
    tenant_id     VARCHAR2(64),
    created_at    TIMESTAMP    DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at    TIMESTAMP    DEFAULT SYSTIMESTAMP NOT NULL
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_cognitive.ecos_biz_contract IS '业务合同表';

BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_cog_contract_proj ON ecos_cognitive.ecos_biz_contract(project_id)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
   EXECUTE IMMEDIATE 'ALTER TABLE ecos_cognitive.ecos_biz_contract ADD CONSTRAINT fk_cog_contract_proj FOREIGN KEY (project_id) REFERENCES ecos_cognitive.ecos_biz_project(id)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

-- ============================================
-- Sequence for ecos_biz_metric
-- ============================================
BEGIN
   EXECUTE IMMEDIATE 'CREATE SEQUENCE ecos_cognitive.seq_biz_metric START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

-- ============================================
-- Table: ecos_biz_metric
-- Description: 业务指标表
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_cognitive.ecos_biz_metric (
    id            NUMBER(19)     PRIMARY KEY,
    dept_id       VARCHAR2(64),
    metric_type   VARCHAR2(32),
    metric_value  NUMBER(18,2),
    target_value  NUMBER(18,2),
    metric_month  VARCHAR2(7),
    goal_id       NUMBER(19),
    tenant_id     VARCHAR2(64),
    created_at    TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_cognitive.ecos_biz_metric IS '业务指标表';

BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_cog_metric_dept ON ecos_cognitive.ecos_biz_metric(dept_id)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
   EXECUTE IMMEDIATE 'ALTER TABLE ecos_cognitive.ecos_biz_metric ADD CONSTRAINT fk_cog_metric_goal FOREIGN KEY (goal_id) REFERENCES ecos_cognitive.ecos_wm_goal(id)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

CREATE OR REPLACE TRIGGER ecos_cognitive.trg_biz_metric_id
BEFORE INSERT ON ecos_cognitive.ecos_biz_metric
FOR EACH ROW
BEGIN
   IF :NEW.id IS NULL THEN
      :NEW.id := ecos_cognitive.seq_biz_metric.NEXTVAL;
   END IF;
END;
/

-- ============================================
-- Sequence for ecos_biz_target
-- ============================================
BEGIN
   EXECUTE IMMEDIATE 'CREATE SEQUENCE ecos_cognitive.seq_biz_target START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

-- ============================================
-- Table: ecos_biz_target
-- Description: 业务目标表
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_cognitive.ecos_biz_target (
    id            NUMBER(19)     PRIMARY KEY,
    dept_id       VARCHAR2(64),
    target_type   VARCHAR2(32),
    target_value  NUMBER(18,2),
    target_year   NUMBER(10),
    goal_id       NUMBER(19),
    tenant_id     VARCHAR2(64),
    created_at    TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_cognitive.ecos_biz_target IS '业务目标表';

BEGIN
   EXECUTE IMMEDIATE 'ALTER TABLE ecos_cognitive.ecos_biz_target ADD CONSTRAINT fk_cog_target_goal FOREIGN KEY (goal_id) REFERENCES ecos_cognitive.ecos_wm_goal(id)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

CREATE OR REPLACE TRIGGER ecos_cognitive.trg_biz_target_id
BEFORE INSERT ON ecos_cognitive.ecos_biz_target
FOR EACH ROW
BEGIN
   IF :NEW.id IS NULL THEN
      :NEW.id := ecos_cognitive.seq_biz_target.NEXTVAL;
   END IF;
END;
/
