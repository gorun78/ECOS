-- V123__ecos_business_scenarios.sql
-- ECOS 场景工作台 — 业务场景实体化（PMO-50）
-- 表: ecos_business_scenario (场景主表, 目标/指标/部门/预算)
--     ecos_scenario_binding (场景六类绑定: 数据集/对象类型/知识库/AI Agent/安全策略/接口)
-- 说明: id 用 VARCHAR(64) 兼容既有 ScenarioController 的 id 方案（sc001 / sc_xxxxxx）
--       审计六列按规范: id, create_time, update_time, create_by, update_by, is_deleted

CREATE TABLE IF NOT EXISTS ecos_business_scenario (
    id                  VARCHAR(64) PRIMARY KEY,
    name                VARCHAR(255) NOT NULL,
    description         TEXT DEFAULT '',
    business_goal       TEXT DEFAULT '',
    department          VARCHAR(128),
    priority            VARCHAR(16)  DEFAULT 'MEDIUM',   -- CRITICAL / HIGH / MEDIUM / LOW
    status              VARCHAR(32)  DEFAULT 'DRAFT',    -- DRAFT / ACTIVE / COMPLETED / SUSPENDED
    budget              VARCHAR(64),
    safety_index_target DECIMAL(8,4),
    actual_safety_index DECIMAL(8,4),
    metrics             JSONB DEFAULT '{}'::jsonb,       -- 附加指标（integrityScore 等）
    create_time         TIMESTAMP NOT NULL DEFAULT NOW(),
    update_time         TIMESTAMP NOT NULL DEFAULT NOW(),
    create_by           VARCHAR(64)  DEFAULT 'system',
    update_by           VARCHAR(64)  DEFAULT 'system',
    is_deleted          SMALLINT     NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS ecos_scenario_binding (
    id             VARCHAR(64) PRIMARY KEY,
    scenario_id    VARCHAR(64) NOT NULL,
    binding_type   VARCHAR(32) NOT NULL,   -- DATASET / OBJECT_TYPE / KNOWLEDGE_BASE / AI_AGENT / SECURITY_POLICY / INTERFACE
    target_ref     VARCHAR(255) NOT NULL,  -- 资源标识（数据集 id / 对象类型 code / 知识库 id 等）
    remark         TEXT DEFAULT '',
    create_time    TIMESTAMP NOT NULL DEFAULT NOW(),
    update_time    TIMESTAMP NOT NULL DEFAULT NOW(),
    create_by      VARCHAR(64)  DEFAULT 'system',
    update_by      VARCHAR(64)  DEFAULT 'system',
    is_deleted     SMALLINT     NOT NULL DEFAULT 0
);

-- ── 索引 ────────────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_biz_scenario_status    ON ecos_business_scenario(status);
CREATE INDEX IF NOT EXISTS idx_biz_scenario_dept      ON ecos_business_scenario(department);
CREATE INDEX IF NOT EXISTS idx_scenario_bindsc        ON ecos_scenario_binding(scenario_id);
CREATE INDEX IF NOT EXISTS idx_scenario_bindst        ON ecos_scenario_binding(binding_type);

-- ── 种子数据：航空 AOC 三场景（与前端 initialScenarios / 原内存 seed 对齐） ──
INSERT INTO ecos_business_scenario (id, name, description, business_goal, department, priority, status, budget, safety_index_target, actual_safety_index)
VALUES
    ('sc001', '飞行运行安全', '建立覆盖飞行员、飞机、航线的全方位安全监控体系', '建立覆盖飞行员、飞机、航线的全方位安全监控体系', '飞行运行部', 'HIGH', 'ACTIVE', '850万', 0.9500, 0.8700),
    ('sc002', '客舱服务质量提升', '通过旅客反馈数据优化客舱服务流程', '通过旅客反馈数据优化客舱服务流程', '客舱服务部', 'MEDIUM', 'ACTIVE', '320万', 0.9000, 0.7800),
    ('sc003', '航空燃油效率优化', '降低燃油消耗，实现绿色飞行', '降低燃油消耗，实现绿色飞行', '运行控制中心', 'CRITICAL', 'ACTIVE', '1200万', 0.9200, 0.8300)
ON CONFLICT (id) DO NOTHING;

-- 种子绑定（与原 seed bindings 对齐）
INSERT INTO ecos_scenario_binding (id, scenario_id, binding_type, target_ref)
VALUES
    ('sb001', 'sc001', 'DATASET', 'ds_flight_schedules'),
    ('sb002', 'sc001', 'DATASET', 'ds_passenger_feedback'),
    ('sb003', 'sc001', 'OBJECT_TYPE', 'flight'),
    ('sb004', 'sc001', 'OBJECT_TYPE', 'aircraft'),
    ('sb005', 'sc001', 'OBJECT_TYPE', 'pilot'),
    ('sb006', 'sc001', 'KNOWLEDGE_BASE', 'sop_maintenance_v2'),
    ('sb007', 'sc001', 'AI_AGENT', 'agent_safety_monitor'),
    ('sb008', 'sc001', 'SECURITY_POLICY', 'pol_gdpr_compliance')
ON CONFLICT (id) DO NOTHING;
