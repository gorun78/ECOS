-- V1.5: P2-5 自学习案例库
CREATE TABLE IF NOT EXISTS ecos_decision_case (
    id          BIGSERIAL    PRIMARY KEY,
    title       VARCHAR(256) NOT NULL,
    scenario    TEXT,                    -- 情景描述
    tags        TEXT[],                  -- 标签数组
    decision    JSONB        DEFAULT '{}', -- 决策内容
    result      JSONB        DEFAULT '{}', -- 执行结果
    feedback    VARCHAR(32)  DEFAULT 'pending', -- pending/positive/negative
    source      VARCHAR(64),             -- 来源: causal/agent/manual
    created_by  VARCHAR(128),
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- Index for keyword search
CREATE INDEX IF NOT EXISTS idx_decision_case_tags ON ecos_decision_case USING GIN(tags);
CREATE INDEX IF NOT EXISTS idx_decision_case_title ON ecos_decision_case(title);

-- Seed 5 cases
INSERT INTO ecos_decision_case (title, scenario, tags, decision, result, feedback, source) VALUES
    ('数据质量提升对通行效率的影响', '当数据质量从60%提升至90%时，通行效率提升15%', ARRAY['数据质量','通行效率','交通安全'], '{"action":"提升数据采集频率","params":{"frequency":"5min"}}', '{"efficiency_gain":0.15,"cost_change":-0.02}', 'positive', 'causal'),
    ('养护预算优化方案', '通过资产数字化实现养护预算优化，养护成本降低20%', ARRAY['养护成本','资产管理','预算优化'], '{"action":"引入预测性养护模型","params":{"model":"random_forest","threshold":0.8}}', '{"cost_reduction":0.20,"coverage":0.95}', 'positive', 'agent'),
    ('应急响应速度提升策略', '部署边缘计算节点，应急响应时间从30min降至8min', ARRAY['应急响应','边缘计算','实时监控'], '{"action":"部署边缘计算节点","params":{"nodes":12,"locations":["枢纽","隧道"]}}', '{"response_time_reduction":73,"availability":0.999}', 'positive', 'agent'),
    ('智慧路网建设规划', '建设智慧路网后，拥堵指数下降25%，用户满意度提升10点', ARRAY['智慧路网','用户满意度','拥堵治理'], '{"action":"智慧路网基础设施升级","params":{"phase":2,"budget_m":500}}', '{"congestion_drop":0.25,"satisfaction_gain":10}', 'positive', 'causal'),
    ('桥梁安全预警系统', '部署桥梁结构监测系统，预警准确率92%，误报率3%', ARRAY['桥梁安全','预警系统','结构监测'], '{"action":"部署IoT传感器网络","params":{"sensors":200,"interval":"1h"}}', '{"precision":0.92,"false_positive":0.03}', 'positive', 'agent');
