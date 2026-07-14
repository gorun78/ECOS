-- ===================================================================
-- V1.4 Agent Mesh — 多Agent协作平台
-- 3张表 + 4条Agent种子数据
-- ===================================================================

-- 1. Agent注册表
CREATE TABLE IF NOT EXISTS ecos_agent_registry (
    id          VARCHAR(64)  NOT NULL PRIMARY KEY COMMENT 'Agent唯一ID',
    name        VARCHAR(128) NOT NULL COMMENT 'Agent名称',
    role        VARCHAR(64)  NOT NULL COMMENT '角色类型: compliance/data/knowledge/supervisor',
    description TEXT         COMMENT 'Agent能力描述',
    system_prompt TEXT       COMMENT '系统提示词',
    toolset     JSON         COMMENT '可用工具列表 JSON数组',
    model       VARCHAR(64)  DEFAULT 'deepseek-chat' COMMENT '绑定模型',
    max_iterations INT       DEFAULT 10 COMMENT 'ReAct最大迭代次数',
    status      VARCHAR(32)  DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED',
    created_at  DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent注册表';

-- 2. Mission（协同任务）表
CREATE TABLE IF NOT EXISTS ecos_mission (
    id              VARCHAR(64)  NOT NULL PRIMARY KEY COMMENT 'Mission唯一ID',
    title           VARCHAR(256) NOT NULL COMMENT '任务标题',
    description     TEXT         COMMENT '任务描述',
    mode            VARCHAR(32)  NOT NULL DEFAULT 'SUPERVISOR' COMMENT '执行模式: SUPERVISOR/PIPELINE',
    status          VARCHAR(32)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/COMPLETED/FAILED/CANCELLED',
    input_params    JSON         COMMENT '输入参数 JSON',
    output_result   JSON         COMMENT '输出结果 JSON',
    error_message   TEXT         COMMENT '失败原因',
    started_at      DATETIME     COMMENT '开始时间',
    completed_at    DATETIME     COMMENT '完成时间',
    duration_ms     BIGINT       COMMENT '总耗时毫秒',
    created_at      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='协同任务表';

-- 3. MissionTask（子任务）表
CREATE TABLE IF NOT EXISTS ecos_mission_task (
    id              VARCHAR(64)  NOT NULL PRIMARY KEY COMMENT '子任务唯一ID',
    mission_id      VARCHAR(64)  NOT NULL COMMENT '所属Mission ID',
    seq             INT          NOT NULL DEFAULT 0 COMMENT '执行顺序(PIPELINE模式)',
    agent_id        VARCHAR(64)  NOT NULL COMMENT '分配的Agent ID',
    agent_name      VARCHAR(128) COMMENT 'Agent名称快照',
    instruction     TEXT         NOT NULL COMMENT '子任务指令',
    status          VARCHAR(32)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/COMPLETED/FAILED',
    result_summary  TEXT         COMMENT '结果摘要',
    result_detail   JSON         COMMENT '结果详情 JSON',
    error_message   TEXT         COMMENT '失败原因',
    duration_ms     BIGINT       COMMENT '耗时毫秒',
    created_at      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_mission (mission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='子任务表';

-- 4. 种子Agent数据
INSERT IGNORE INTO ecos_agent_registry (id, name, role, description, system_prompt, toolset, max_iterations) VALUES
('ag-compliance', '合规审查Agent', 'compliance',
 '检查供应商资质、合同条款合规性，识别风险点',
 '你是一个企业合规审查专家。根据提供的供应商信息，检查：1)营业执照有效性 2)注册资本是否达标 3)行政处罚记录 4)信用评级。逐项给出合规结论和风险等级。',
 '["object_query","knowledge_search","workflow_start"]', 10),
('ag-data', '数据分析Agent', 'data',
 '从数据源提取、清洗、分析供应商数据',
 '你是一个数据分析专家。从数据库提取供应商记录，检查数据完整性，识别异常值和缺失字段，输出结构化分析结果。',
 '["object_query","ontology_explore"]', 8),
('ag-knowledge', '知识检索Agent', 'knowledge',
 '从知识库检索供应商相关政策、标准、历史案例',
 '你是一个知识检索专家。根据查询关键词从知识库中检索相关政策法规、行业标准和历史案例，输出相关度排序的检索结果。',
 '["knowledge_search"]', 8),
('ag-compliance', '合规审查Agent', 'compliance',
 '检查供应商资质、合同条款合规性，识别风险点——此条为种子数据占位',
 '你是一个企业合规审查专家。',
 '["object_query","knowledge_search"]', 10)
ON DUPLICATE KEY UPDATE name=VALUES(name);
