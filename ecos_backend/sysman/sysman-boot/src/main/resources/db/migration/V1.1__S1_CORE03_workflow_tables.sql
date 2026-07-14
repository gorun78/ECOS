-- ============================================================
-- S1-CORE03: Workflow Designer — Database Migration
-- PostgreSQL
-- ============================================================

-- 1. ecos_workflow_instance — 流程实例
CREATE TABLE IF NOT EXISTS ecos_workflow_instance (
    id                  VARCHAR(50) PRIMARY KEY,
    workflow_id         VARCHAR(50) NOT NULL,
    workflow_name       VARCHAR(200),
    version_no          VARCHAR(20),
    status              VARCHAR(50) NOT NULL,          -- Created/Running/Waiting/Completed/Failed/Suspended/Terminated
    trigger_type        VARCHAR(50),
    triggered_by        VARCHAR(100),
    triggered_object_id VARCHAR(100),
    trigger_event       VARCHAR(200),
    variables           JSONB DEFAULT '{}',
    context             JSONB,
    current_node_ids    JSONB,                          -- JSON array of active node IDs
    started_at          TIMESTAMP,
    completed_at        TIMESTAMP,
    error_message       TEXT,
    retry_count         INT DEFAULT 0,
    created_at          TIMESTAMP DEFAULT NOW(),
    updated_at          TIMESTAMP DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_wf_instance_status ON ecos_workflow_instance(status);
CREATE INDEX IF NOT EXISTS idx_wf_instance_object ON ecos_workflow_instance(triggered_object_id);
CREATE INDEX IF NOT EXISTS idx_wf_instance_workflow ON ecos_workflow_instance(workflow_id);

-- 2. ecos_workflow_task — 任务
CREATE TABLE IF NOT EXISTS ecos_workflow_task (
    id              VARCHAR(50) PRIMARY KEY,
    instance_id     VARCHAR(50) NOT NULL,
    node_id         VARCHAR(100) NOT NULL,
    task_type       VARCHAR(50) NOT NULL,              -- APPROVAL/EXECUTION/AGENT/INVESTIGATION
    title           VARCHAR(500),
    assignee        VARCHAR(100),
    candidate_users JSONB,                               -- JSON array
    candidate_roles JSONB,                               -- JSON array
    status          VARCHAR(50) DEFAULT 'New',          -- New/Assigned/InProgress/Completed/Rejected/Transferred
    priority        VARCHAR(20) DEFAULT 'NORMAL',
    form_schema     JSONB,
    form_data       JSONB,
    result          JSONB,
    agent_result    JSONB,
    due_date        TIMESTAMP,
    completed_at    TIMESTAMP,
    completed_by    VARCHAR(100),
    created_at      TIMESTAMP DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_wf_task_status ON ecos_workflow_task(status);
CREATE INDEX IF NOT EXISTS idx_wf_task_assignee ON ecos_workflow_task(assignee);
CREATE INDEX IF NOT EXISTS idx_wf_task_instance ON ecos_workflow_task(instance_id);

-- 3. ecos_workflow_approval — 审批记录
CREATE TABLE IF NOT EXISTS ecos_workflow_approval (
    id              VARCHAR(50) PRIMARY KEY,
    task_id         VARCHAR(50) NOT NULL,
    instance_id     VARCHAR(50) NOT NULL,
    approver        VARCHAR(100) NOT NULL,
    decision        VARCHAR(50) NOT NULL,              -- Approved/Rejected/Transferred
    opinion         TEXT,
    form_data       JSONB,
    created_at      TIMESTAMP DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_wf_approval_task ON ecos_workflow_approval(task_id);
CREATE INDEX IF NOT EXISTS idx_wf_approval_instance ON ecos_workflow_approval(instance_id);

-- 4. ecos_workflow_log — 执行日志
CREATE TABLE IF NOT EXISTS ecos_workflow_log (
    id              VARCHAR(50) PRIMARY KEY,
    instance_id     VARCHAR(50) NOT NULL,
    node_id         VARCHAR(100),
    node_type       VARCHAR(50),
    event_type      VARCHAR(100) NOT NULL,             -- NodeStarted/NodeCompleted/TaskCreated/TaskAssigned/AgentInvoked/AgentCompleted/InstanceStarted/InstanceCompleted
    message         TEXT,
    details         JSONB,
    duration_ms     BIGINT,
    trace_id        VARCHAR(100),
    created_at      TIMESTAMP DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_wf_log_instance ON ecos_workflow_log(instance_id, created_at);
CREATE INDEX IF NOT EXISTS idx_wf_log_trace ON ecos_workflow_log(trace_id);
