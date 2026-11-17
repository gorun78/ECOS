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

-- ============================================================================
-- 认知真身 11 表多库镜像（详细设计-05 §E.5「ecos-sql/mysql 侧同步补认知表」/ R-13 ① 归位口径）
-- 单源 = docs/50-db_script/02-flyway-migration/gateway/ 下 V163 + V187 + V188 + V191 + V192 + V193
--        （列面已含 V197 轨道之外的全部后置 ALTER：V261 注册中心列、V262 probability 列）
-- 上方 ecos_wm_* / ecos_biz_* 11 张为 R-13 ① 定性的**错放表**（0 行、只停写，不迁移不 DROP，IR03），
-- 本段才是认知域权威表面；两套并存期间以本段为准（旧段保留供空库重建兼容，禁据此认定归属）。
-- 方言适配纪律（多库兼容，数据库访问规范 v1.2 MC01~MC03 / DR04~DR08）：
--   1) JSON 语义列（*_json）单源 PG 侧已是 TEXT ⇒ MySQL 侧用 LONGTEXT（MC02 禁二进制 JSON）；
--      单源 V163 的 config_json/result_json 实为 JSONB 且带条件唯一索引（MC02/MC03 已知偏差，
--      收敛登记在 E.3/V197 轨道）⇒ **违例不复制到多库基线**，本段按合规终态形态落（LONGTEXT +
--      含 is_deleted 的普通唯一索引）；
--   2) MySQL 的 TEXT/LONGTEXT 族不支持字面量 DEFAULT ⇒ 单源的 DEFAULT ''/'[]'/'{}' 列在本段降为
--      可空无默认，缺省值由应用侧（Mapper/Codec）补形；NOT NULL 的数值/字符串列默认值原样保留；
--   3) 主键一律 VARCHAR(36) 应用侧生成 UUID、DDL 无默认值（MC01）；无 AUTO_INCREMENT、无序列；
--   4) 全部普通索引（MC03 禁条件索引），逻辑删除列 is_deleted 并入复合键代偿；
--   5) 本表清单不含 ecos_dw.ecos_application_forecast_run（V197，确定性计算产物 = 业务域落点，
--      经 data-engine 唯一写通道提交，不属控制域 5+1 schema 多库基线，见铁律 §0.6 / ADR-14）。
-- 落位说明：§E.5 原文写的 `ecos-sql/mysql/` 侧现为**空占位目录**（V200 Schema 治理后 DDL 单源归档到
--   docs/50-db_script/01-ddl-多方言/，见 cda0810d），故镜像只落本目录一处，禁两处双写造成漂移。
-- 执行状态：仅落脚本文件，未对任何 MySQL 实例实跑（§14.4 未授权项）。
-- ============================================================================

-- --------------------------------------------
-- Table: ecos_cognitive_mind（认知模型资产注册表，V187 / F05-02）
-- --------------------------------------------
CREATE TABLE IF NOT EXISTS ecos_cognitive_mind (
    id                    VARCHAR(36)   NOT NULL,
    mind_key              VARCHAR(120)  NOT NULL,
    name                  VARCHAR(200)  NOT NULL,
    description           LONGTEXT,
    capability_mask_json  LONGTEXT      NOT NULL,
    closed_loop_bounds_json LONGTEXT    NOT NULL,
    status                VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
    is_active             SMALLINT      NOT NULL DEFAULT 1,
    mind_version          VARCHAR(20)   NOT NULL,
    trace_id              VARCHAR(64),
    domain                VARCHAR(50)   NOT NULL DEFAULT 'default',
    version_no            VARCHAR(20)   NOT NULL DEFAULT '1',
    is_deleted            SMALLINT      NOT NULL DEFAULT 0,
    create_by             VARCHAR(64)   NOT NULL,
    update_by             VARCHAR(64),
    create_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time           DATETIME,
    PRIMARY KEY (id)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='认知模型资产注册表（ADR-8 正名 cognitive_*；旧 kb_mind_registry 仅存于文档，无库内对象）';

CREATE UNIQUE INDEX uniq_cm_key_ver ON ecos_cognitive_mind(mind_key, mind_version, is_deleted);
CREATE INDEX idx_cm_active         ON ecos_cognitive_mind(is_active, is_deleted);

-- --------------------------------------------
-- Table: ecos_cognitive_scenario_mind（场景↔Mind 绑定关系表，V188）
-- --------------------------------------------
CREATE TABLE IF NOT EXISTS ecos_cognitive_scenario_mind (
    id            VARCHAR(36)   NOT NULL,
    scenario_id   VARCHAR(36)   NOT NULL,
    mind_id       VARCHAR(36)   NOT NULL,
    binding_role  VARCHAR(20)   NOT NULL DEFAULT 'PRIMARY',
    priority      INT           NOT NULL DEFAULT 100,
    trace_id      VARCHAR(64),
    domain        VARCHAR(50)   NOT NULL DEFAULT 'default',
    version_no    VARCHAR(20)   NOT NULL DEFAULT '1',
    is_deleted    SMALLINT      NOT NULL DEFAULT 0,
    create_by     VARCHAR(64)   NOT NULL,
    update_by     VARCHAR(64),
    create_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME,
    PRIMARY KEY (id)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='场景↔Mind 绑定关系表（全库首个 mind_id 列，X-19 收敛；不建跨表外键）';

CREATE UNIQUE INDEX uniq_csm_scn_mind ON ecos_cognitive_scenario_mind(scenario_id, mind_id, is_deleted);
CREATE INDEX idx_csm_mind             ON ecos_cognitive_scenario_mind(mind_id, is_deleted);

-- --------------------------------------------
-- Table: ecos_cognitive_belief（不确定性判断表，V191 + V262 probability）
-- --------------------------------------------
CREATE TABLE IF NOT EXISTS ecos_cognitive_belief (
    id                 VARCHAR(36)   NOT NULL,
    variable_name      VARCHAR(128)  NOT NULL,
    tenant_scope       VARCHAR(64),
    domain             VARCHAR(64)   NOT NULL DEFAULT 'default',
    distribution_json  LONGTEXT      NOT NULL,
    version            INT           NOT NULL DEFAULT 1,
    snapshot_version   INT,
    is_manual_override SMALLINT      NOT NULL DEFAULT 0,
    override_reason    LONGTEXT,
    last_evidence_id   VARCHAR(64),
    status             VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE',
    mind_id            VARCHAR(36),
    scenario_id        VARCHAR(36),
    version_no         VARCHAR(20)   NOT NULL DEFAULT '1',
    is_deleted         SMALLINT      NOT NULL DEFAULT 0,
    create_by          VARCHAR(64)   NOT NULL DEFAULT 'system',
    update_by          VARCHAR(64),
    create_time        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time        DATETIME,
    probability        DECIMAL(6,5),
    PRIMARY KEY (id)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='不确定性判断表（ADR-9 心智层 P 库；probability 为 E2 算式唯一数值来源，NULL=未标定）';

CREATE UNIQUE INDEX uniq_cv191_belief_var_ver ON ecos_cognitive_belief(variable_name, domain, version, is_deleted);
CREATE INDEX idx_cv191_belief_domain          ON ecos_cognitive_belief(domain, is_deleted);
CREATE INDEX idx_cv191_belief_mind            ON ecos_cognitive_belief(mind_id, scenario_id);
CREATE INDEX idx_cv262_belief_prob            ON ecos_cognitive_belief(probability, is_deleted);

-- --------------------------------------------
-- Table: ecos_cognitive_hypothesis（认知假设表，V191 / R-15 终态 4 值枚举）
-- --------------------------------------------
CREATE TABLE IF NOT EXISTS ecos_cognitive_hypothesis (
    id               VARCHAR(36)   NOT NULL,
    hypothesis_code  VARCHAR(128)  NOT NULL,
    tenant_scope     VARCHAR(64),
    statement        LONGTEXT      NOT NULL,
    domain           VARCHAR(64)   NOT NULL DEFAULT 'default',
    metric_ref       VARCHAR(128),
    evidence_ids_json LONGTEXT     NOT NULL,
    is_valid         SMALLINT      NOT NULL DEFAULT 1,
    invalid_at       DATETIME,
    invalid_reason   LONGTEXT,
    status           VARCHAR(16)   NOT NULL DEFAULT 'VALID',
    old_status       VARCHAR(20),
    mind_id          VARCHAR(36),
    scenario_id      VARCHAR(36),
    version_no       VARCHAR(20)   NOT NULL DEFAULT '1',
    is_deleted       SMALLINT      NOT NULL DEFAULT 0,
    create_by        VARCHAR(64)   NOT NULL DEFAULT 'system',
    update_by        VARCHAR(64),
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      DATETIME,
    PRIMARY KEY (id)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='认知假设表（ADR-9 心智层 H 库；old_status 存映射前旧枚举留痕，禁 DDL 伪造历史）';

CREATE UNIQUE INDEX uniq_cv191_hypo_code   ON ecos_cognitive_hypothesis(hypothesis_code, is_deleted);
CREATE INDEX idx_cv191_hypo_domain         ON ecos_cognitive_hypothesis(domain, invalid_at);
CREATE INDEX idx_cv191_hypo_mind           ON ecos_cognitive_hypothesis(mind_id, scenario_id);

-- --------------------------------------------
-- Table: ecos_cognitive_evidence（认知证据表，V191）
-- --------------------------------------------
CREATE TABLE IF NOT EXISTS ecos_cognitive_evidence (
    id                         VARCHAR(36)   NOT NULL,
    evidence_code              VARCHAR(128)  NOT NULL,
    tenant_scope               VARCHAR(64),
    source_type                VARCHAR(32)   NOT NULL,
    source_ref                 VARCHAR(256),
    blob_json                  LONGTEXT      NOT NULL,
    confidence                 DECIMAL(5,4)  NOT NULL DEFAULT 0.5,
    is_conflict                SMALLINT      NOT NULL DEFAULT 0,
    refuting_evidence_ids_json LONGTEXT      NOT NULL,
    effective_time             DATETIME,
    expire_time                DATETIME,
    status                     VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE',
    domain                     VARCHAR(50)   NOT NULL DEFAULT 'default',
    version_no                 VARCHAR(20)   NOT NULL DEFAULT '1',
    is_deleted                 SMALLINT      NOT NULL DEFAULT 0,
    create_by                  VARCHAR(64)   NOT NULL DEFAULT 'system',
    update_by                  VARCHAR(64),
    create_time                DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time                DATETIME,
    PRIMARY KEY (id)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='认知证据表（ADR-9 感知/心智层；confidence 0~1 带精度，MC01）';

CREATE UNIQUE INDEX uniq_cv191_ev_code ON ecos_cognitive_evidence(evidence_code, is_deleted);
CREATE INDEX idx_cv191_ev_source       ON ecos_cognitive_evidence(source_type, status);
CREATE INDEX idx_cv191_ev_time         ON ecos_cognitive_evidence(create_time);

-- --------------------------------------------
-- Table: ecos_cognitive_model（认知模型注册表，V192 + V261 注册中心列）
-- --------------------------------------------
CREATE TABLE IF NOT EXISTS ecos_cognitive_model (
    id                   VARCHAR(36)   NOT NULL,
    model_id             VARCHAR(64)   NOT NULL,
    model_type           VARCHAR(32)   NOT NULL,
    version              INT           NOT NULL DEFAULT 1,
    features_json        LONGTEXT,
    backtest_score       DECIMAL(8,6),
    status               VARCHAR(16)   NOT NULL DEFAULT 'active',
    domain               VARCHAR(50)   NOT NULL DEFAULT 'default',
    version_no           VARCHAR(20)   NOT NULL DEFAULT '1',
    is_deleted           SMALLINT      NOT NULL DEFAULT 0,
    create_by            VARCHAR(64)   NOT NULL DEFAULT 'system',
    update_by            VARCHAR(64),
    create_time          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time          DATETIME,
    cognitive_model_type VARCHAR(8),
    registry_state       VARCHAR(16)   NOT NULL DEFAULT 'DRAFT',
    content_hash         VARCHAR(64),
    supersedes_id        VARCHAR(36),
    gate_contract        VARCHAR(16)   NOT NULL DEFAULT 'PENDING',
    gate_regression      VARCHAR(16)   NOT NULL DEFAULT 'PENDING',
    gate_backtest        VARCHAR(16)   NOT NULL DEFAULT 'PENDING',
    gate_calibration     VARCHAR(16)   NOT NULL DEFAULT 'PENDING',
    published_at         DATETIME,
    published_by         VARCHAR(64),
    PRIMARY KEY (id)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='认知模型注册表（ADR-8 模型资产落盘；registry_state 四类发布门禁全 PASS 才升 PUBLISHED，PUBLISHED 行不可变）';

CREATE UNIQUE INDEX uniq_cv192_model_id_ver ON ecos_cognitive_model(model_id, version, is_deleted);
CREATE INDEX idx_cv192_model_type           ON ecos_cognitive_model(model_type, status);
CREATE INDEX idx_cv192_model_domain         ON ecos_cognitive_model(domain, is_deleted);
CREATE INDEX idx_cv261_model_state          ON ecos_cognitive_model(registry_state, is_deleted);
CREATE INDEX idx_cv261_model_cm             ON ecos_cognitive_model(cognitive_model_type, registry_state);
CREATE INDEX idx_cv261_model_supersed       ON ecos_cognitive_model(supersedes_id);

-- --------------------------------------------
-- Table: ecos_cognitive_run_invalidation（失效→作废推演结论留痕表，V192）
-- --------------------------------------------
CREATE TABLE IF NOT EXISTS ecos_cognitive_run_invalidation (
    id            VARCHAR(36)   NOT NULL,
    event_id      VARCHAR(64)   NOT NULL,
    hypothesis_id VARCHAR(64)   NOT NULL,
    run_id        VARCHAR(64)   NOT NULL,
    auto_detected SMALLINT      NOT NULL DEFAULT 1,
    superseded_at DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    detail        LONGTEXT,
    domain        VARCHAR(50)   NOT NULL DEFAULT 'default',
    version_no    VARCHAR(20)   NOT NULL DEFAULT '1',
    is_deleted    SMALLINT      NOT NULL DEFAULT 0,
    create_by     VARCHAR(64)   NOT NULL DEFAULT 'system',
    update_by     VARCHAR(64),
    create_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME,
    PRIMARY KEY (id)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='失效→作废推演结论关联留痕表（ADR-9 心智事件；run_id 指向 ecos_dw 产物面，随 R-17 ①）';

CREATE UNIQUE INDEX uniq_cv192_run_inv_evt_run ON ecos_cognitive_run_invalidation(event_id, run_id, is_deleted);
CREATE INDEX idx_cv192_run_inv_run             ON ecos_cognitive_run_invalidation(run_id);
CREATE INDEX idx_cv192_run_inv_hyp             ON ecos_cognitive_run_invalidation(hypothesis_id);
CREATE INDEX idx_cv192_run_inv_time            ON ecos_cognitive_run_invalidation(superseded_at);

-- --------------------------------------------
-- Table: ecos_cognitive_pipeline（认知管线定义表，V163 单源；本段按 MC02/MC03 合规形态镜像）
-- --------------------------------------------
CREATE TABLE IF NOT EXISTS ecos_cognitive_pipeline (
    id          VARCHAR(36)   NOT NULL,
    pipeline_id VARCHAR(64)   NOT NULL,
    name        VARCHAR(255),
    status      VARCHAR(32)   NOT NULL DEFAULT 'DRAFT',
    config_json LONGTEXT,
    result_json LONGTEXT,
    create_by   VARCHAR(100),
    update_by   VARCHAR(100),
    create_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted  SMALLINT      NOT NULL DEFAULT 0,
    domain      VARCHAR(50)   NOT NULL DEFAULT 'default',
    version_no  VARCHAR(20)   NOT NULL DEFAULT '1',
    PRIMARY KEY (id)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='认知管线定义表（cognitive-engine 自有，ST07 收编；旧 kb_cognitive_pipeline 停写保留）';

CREATE UNIQUE INDEX uniq_ecos_cognitive_pipeline_pid ON ecos_cognitive_pipeline(pipeline_id, is_deleted);
CREATE INDEX idx_ecos_cognitive_pipeline_domain      ON ecos_cognitive_pipeline(domain);

-- --------------------------------------------
-- Table: ecos_business_scenario（业务场景主表，V193）
-- --------------------------------------------
CREATE TABLE IF NOT EXISTS ecos_business_scenario (
    id                  VARCHAR(36)   NOT NULL,
    name                VARCHAR(255)  NOT NULL,
    description         LONGTEXT,
    business_goal       LONGTEXT,
    department          VARCHAR(128),
    priority            VARCHAR(16)            DEFAULT 'MEDIUM',
    status              VARCHAR(32)            DEFAULT 'DRAFT',
    budget              VARCHAR(64),
    safety_index_target DECIMAL(8,4),
    actual_safety_index DECIMAL(8,4),
    metrics_json        LONGTEXT,
    domain              VARCHAR(50)   NOT NULL DEFAULT 'default',
    version_no          VARCHAR(20)   NOT NULL DEFAULT '1',
    is_deleted          SMALLINT      NOT NULL DEFAULT 0,
    create_by           VARCHAR(64)   NOT NULL DEFAULT 'system',
    update_by           VARCHAR(64),
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time         DATETIME,
    PRIMARY KEY (id)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='业务场景主表（认知域真身之一，R-13 ① 归位；budget 为旧串值形态原样承接，金额实列化属场景层 v2/V205 轨道）';

CREATE INDEX idx_cv193_bizscn_status ON ecos_business_scenario(status, is_deleted);
CREATE INDEX idx_cv193_bizscn_dept   ON ecos_business_scenario(department, is_deleted);

-- --------------------------------------------
-- Table: ecos_scenario_binding（场景六类绑定表，V193）
-- --------------------------------------------
CREATE TABLE IF NOT EXISTS ecos_scenario_binding (
    id           VARCHAR(36)   NOT NULL,
    scenario_id  VARCHAR(64)   NOT NULL,
    binding_type VARCHAR(32)   NOT NULL,
    target_ref   VARCHAR(255)  NOT NULL,
    target_id    VARCHAR(36),
    target_type  VARCHAR(20),
    remark       LONGTEXT,
    domain       VARCHAR(50)   NOT NULL DEFAULT 'default',
    version_no   VARCHAR(20)   NOT NULL DEFAULT '1',
    is_deleted   SMALLINT      NOT NULL DEFAULT 0,
    create_by    VARCHAR(64)   NOT NULL DEFAULT 'system',
    update_by    VARCHAR(64),
    create_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME,
    PRIMARY KEY (id)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='场景六类绑定表（target_ref 旧字符串别名只读兼容；target_id/target_type 真引用列，值域以 V199 契约为准）';

CREATE INDEX idx_cv193_bind_scn  ON ecos_scenario_binding(scenario_id, is_deleted);
CREATE INDEX idx_cv193_bind_type ON ecos_scenario_binding(binding_type, is_deleted);
CREATE INDEX idx_cv193_bind_tid  ON ecos_scenario_binding(target_id, target_type);

-- --------------------------------------------
-- Table: ecos_scenario_binding_link（场景绑定关系边表，V193）
-- --------------------------------------------
CREATE TABLE IF NOT EXISTS ecos_scenario_binding_link (
    id                VARCHAR(36)   NOT NULL,
    scenario_id       VARCHAR(64)   NOT NULL,
    source_binding_id VARCHAR(64)   NOT NULL,
    target_binding_id VARCHAR(64)   NOT NULL,
    link_type         VARCHAR(16)   NOT NULL,
    source_contract   VARCHAR(128),
    remark            LONGTEXT,
    domain            VARCHAR(50)   NOT NULL DEFAULT 'default',
    version_no        VARCHAR(20)   NOT NULL DEFAULT '1',
    is_deleted        SMALLINT      NOT NULL DEFAULT 0,
    create_by         VARCHAR(64)   NOT NULL DEFAULT 'system',
    update_by         VARCHAR(64),
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time       DATETIME,
    PRIMARY KEY (id),
    CONSTRAINT ck_cv193_blink_link_type CHECK (link_type IN ('MAPPING','EXTRACTION','COGNITION','GOVERN','EXPOSE'))
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='场景绑定关系边表（§0.6.2；link_type 值域与 V160 完全一致，非新枚举收敛）';

CREATE INDEX idx_cv193_blink_scn  ON ecos_scenario_binding_link(scenario_id, is_deleted);
CREATE INDEX idx_cv193_blink_src  ON ecos_scenario_binding_link(source_binding_id);
CREATE INDEX idx_cv193_blink_tgt  ON ecos_scenario_binding_link(target_binding_id);
CREATE INDEX idx_cv193_blink_type ON ecos_scenario_binding_link(link_type);
