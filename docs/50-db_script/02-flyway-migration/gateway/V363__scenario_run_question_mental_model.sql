-- ============================================================================
-- V363 — 场景运行记录扩展（问题驱动运行 + 心智模型，2026-11-24）
-- ============================================================================
-- 目标（只加不删）：
--   1) 运行记录补「用户问题 / 识别意图 / 心智模型 / 假设信念快照 / 采用算法 /
--      执行过程 / 决策建议」七列，支撑问题驱动的运行链路与多次运行对比；
--   2) 心智模型目录：新增 ecos_mental_model_registry，登记 5 个内置心智模型并
--      指向其承载编排（pipelineId），禁空壳（每个都必须有真实承载）。
--
-- 纪律：IR03 只加不删；MC01 主键 VARCHAR(36) 应用侧 UUID；MC02 类型白名单；
--       MC03 标准 SQL；ST07 落 ecos_control（workspace 主控制域）；
--       金额列 NUMERIC(p,s)（本批无金额列）；不建 FK（ST09）。
-- ============================================================================

-- ── 1. 运行记录扩展列 ──────────────────────────────────────────────────────
ALTER TABLE ecos_control.ecos_scenario_run
    ADD COLUMN IF NOT EXISTS question        TEXT,
    ADD COLUMN IF NOT EXISTS intent          VARCHAR(32),
    ADD COLUMN IF NOT EXISTS intent_detail   TEXT,
    ADD COLUMN IF NOT EXISTS mental_model    VARCHAR(32),
    ADD COLUMN IF NOT EXISTS hypotheses      JSONB,
    ADD COLUMN IF NOT EXISTS algorithm_id    VARCHAR(36),
    ADD COLUMN IF NOT EXISTS exec_trace      JSONB,
    ADD COLUMN IF NOT EXISTS advice          JSONB;

COMMENT ON COLUMN ecos_control.ecos_scenario_run.question IS
    '用户输入的场景问题原文（V363；问题驱动运行的输入锚点，多次对比按此分组）';
COMMENT ON COLUMN ecos_control.ecos_scenario_run.intent IS
    '智能体识别的意图码（V363；DIAGNOSE/FORECAST/EVALUATE/DECIDE/COMPARE 等）';
COMMENT ON COLUMN ecos_control.ecos_scenario_run.intent_detail IS
    '意图识别说明（V363；智能体给出的判断依据，供用户核对）';
COMMENT ON COLUMN ecos_control.ecos_scenario_run.mental_model IS
    '本次采用的心智模型码（V363；FIRST_PRINCIPLES/INVERT/PROBABILISTIC/COMPOUND/INCENTIVE）';
COMMENT ON COLUMN ecos_control.ecos_scenario_run.hypotheses IS
    '假设与信念快照（V363；用户确认后的 [{text,kind,source,confirmed}]）';
COMMENT ON COLUMN ecos_control.ecos_scenario_run.algorithm_id IS
    '采用的算法登记 id（V363；对应 ecos_algorithm_registry.id，可空=未走算法库）';
COMMENT ON COLUMN ecos_control.ecos_scenario_run.exec_trace IS
    '执行过程节点流水（V363；[{step,status,detail,elapsedMs}]，供运行页展示执行路径）';
COMMENT ON COLUMN ecos_control.ecos_scenario_run.advice IS
    '决策建议（V363；{summary,actions[],risks[],confidence}，LLM 生成或确定性推导）';

CREATE INDEX IF NOT EXISTS idx_scenario_run_question
    ON ecos_control.ecos_scenario_run (scenario_id, mental_model, create_time DESC);

-- ── 2. 心智模型目录 ────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS ecos_control.ecos_mental_model_registry (
    id                  VARCHAR(36)  NOT NULL,
    code                VARCHAR(32)  NOT NULL,
    name                VARCHAR(128) NOT NULL,
    description         TEXT,
    -- 承载编排：心智模型不是空概念，执行必须落回 AIP 逻辑编排（真实可跑）
    pipeline_id         VARCHAR(64),
    prompt_template     TEXT,
    sort_order          INTEGER      NOT NULL DEFAULT 0,
    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    domain              VARCHAR(50)  NOT NULL DEFAULT 'default',
    version_no          VARCHAR(20)  NOT NULL DEFAULT '1',
    is_deleted          SMALLINT     NOT NULL DEFAULT 0,
    create_by           VARCHAR(64)  NOT NULL,
    create_time         TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_by           VARCHAR(64),
    update_time         TIMESTAMP,
    CONSTRAINT pk_ecos_mental_model_registry PRIMARY KEY (id),
    CONSTRAINT ck_mental_model_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uniq_mental_model_code
    ON ecos_control.ecos_mental_model_registry (code, is_deleted);

COMMENT ON TABLE ecos_control.ecos_mental_model_registry IS
    '心智模型目录（V363）：运行页可选的心智模型清单，每项指向承载其执行的 AIP 编排';
COMMENT ON COLUMN ecos_control.ecos_mental_model_registry.pipeline_id IS
    '承载编排 id（AIP pipeline）；为空表示尚未接线，运行页须如实标注不可执行';

-- ── 3. 内置心智模型种子（pipeline_id 由应用侧在第三批接线后回填）─────────
INSERT INTO ecos_control.ecos_mental_model_registry
    (id, code, name, description, prompt_template, sort_order, status, create_by, create_time)
SELECT 'mm-first-principles', 'FIRST_PRINCIPLES', '第一性原理',
       '把问题拆到不可再分的基本事实，从零重构方案，避免类比式跟风',
       '你是第一性原理分析助手。请把问题拆解到最基本的事实与约束，剔除所有未经证实的假设，再从这些基本事实出发重建可行方案。输出：基本事实清单 / 被剔除的惯例 / 重构方案 / 关键风险。',
       10, 'ACTIVE', 'V363', NOW()
WHERE NOT EXISTS (SELECT 1 FROM ecos_control.ecos_mental_model_registry WHERE id = 'mm-first-principles');

INSERT INTO ecos_control.ecos_mental_model_registry
    (id, code, name, description, prompt_template, sort_order, status, create_by, create_time)
SELECT 'mm-invert', 'INVERT', '逆向思考',
       '先问「怎样必然失败」，再倒推必须避免与必须做到的事',
       '你是逆向思考分析助手。请先设想该问题彻底失败的场景，列出导致失败的关键原因，再倒推为避免失败必须避免什么、必须做到什么。输出：失败路径清单 / 根因 / 反向行动项 / 早期预警信号。',
       20, 'ACTIVE', 'V363', NOW()
WHERE NOT EXISTS (SELECT 1 FROM ecos_control.ecos_mental_model_registry WHERE id = 'mm-invert');

INSERT INTO ecos_control.ecos_mental_model_registry
    (id, code, name, description, prompt_template, sort_order, status, create_by, create_time)
SELECT 'mm-probabilistic', 'PROBABILISTIC', '概率思维',
       '用概率分布而非单一结论表达判断，关注期望值与尾部风险',
       '你是概率思维分析助手。请对问题中的关键变量给出概率区间估计与依据，计算期望结果，并指出尾部风险与信息缺口。输出：关键变量及概率 / 期望值 / 尾部风险 / 需要补充的信息。',
       30, 'ACTIVE', 'V363', NOW()
WHERE NOT EXISTS (SELECT 1 FROM ecos_control.ecos_mental_model_registry WHERE id = 'mm-probabilistic');

INSERT INTO ecos_control.ecos_mental_model_registry
    (id, code, name, description, prompt_template, sort_order, status, create_by, create_time)
SELECT 'mm-compound', 'COMPOUND', '复利',
       '关注长期累积效应与临界点，评估小改进的长期价值',
       '你是复利思维分析助手。请识别问题中可积累的要素，估算其随时间累积的效应与临界点，比较短期收益与长期复利。输出：可积累要素 / 累积效应测算 / 临界点 / 长期行动建议。',
       40, 'ACTIVE', 'V363', NOW()
WHERE NOT EXISTS (SELECT 1 FROM ecos_control.ecos_mental_model_registry WHERE id = 'mm-compound');

INSERT INTO ecos_control.ecos_mental_model_registry
    (id, code, name, description, prompt_template, sort_order, status, create_by, create_time)
SELECT 'mm-incentive', 'INCENTIVE', '激励机制',
       '从各方激励结构解释行为，识别利益错配与可行激励设计',
       '你是激励机制分析助手。请分析问题涉及各方的激励结构，解释当前行为背后的利益动因，识别激励错配，并给出可行的激励调整方案。输出：各方激励结构 / 行为解释 / 错配点 / 调整方案。',
       50, 'ACTIVE', 'V363', NOW()
WHERE NOT EXISTS (SELECT 1 FROM ecos_control.ecos_mental_model_registry WHERE id = 'mm-incentive');

-- ── 4. 会议类场景模板种子（首页「场景模板」真实可选）─────────────────────
INSERT INTO ecos_control.ecos_scenario_template
    (id, name, category, description, definition_defaults, default_analysis, status, create_by, create_time)
SELECT 'tpl-meeting-market', '市场分析会议', 'MEETING', '面向市场机会与竞争格局的分析会议，输出市场判断与切入建议',
       '{"goalStatement":"识别本季度市场机会与竞争威胁，形成可执行的市场策略","successCriteria":["市场规模与增速可量化","竞争格局变化有证据","策略建议与资源匹配"]}',
       '["CAUSAL","FORECAST"]', 'ACTIVE', 'V363', NOW()
WHERE NOT EXISTS (SELECT 1 FROM ecos_control.ecos_scenario_template WHERE id = 'tpl-meeting-market');

INSERT INTO ecos_control.ecos_scenario_template
    (id, name, category, description, definition_defaults, default_analysis, status, create_by, create_time)
SELECT 'tpl-meeting-om', '经营管理会议', 'MEETING', '经营指标复盘与偏差归因会议，输出改善动作',
       '{"goalStatement":"复盘本期经营指标达成情况并定位偏差根因","successCriteria":["关键指标达成率呈现","偏差根因可追溯","改善动作有责任人与时限"]}',
       '["CAUSAL","FORECAST"]', 'ACTIVE', 'V363', NOW()
WHERE NOT EXISTS (SELECT 1 FROM ecos_control.ecos_scenario_template WHERE id = 'tpl-meeting-om');

INSERT INTO ecos_control.ecos_scenario_template
    (id, name, category, description, definition_defaults, default_analysis, status, create_by, create_time)
SELECT 'tpl-meeting-gm', '总经理会议', 'MEETING', '跨部门重大事项决策会议，输出决策结论与落地计划',
       '{"goalStatement":"对本期重大经营事项形成决策结论并明确落地计划","successCriteria":["议题充分论证","决策结论明确","落地计划可跟踪"]}',
       '["CAUSAL","SIMULATION"]', 'ACTIVE', 'V363', NOW()
WHERE NOT EXISTS (SELECT 1 FROM ecos_control.ecos_scenario_template WHERE id = 'tpl-meeting-gm');

INSERT INTO ecos_control.ecos_scenario_template
    (id, name, category, description, definition_defaults, default_analysis, status, create_by, create_time)
SELECT 'tpl-meeting-tech', '技术管理会议', 'MEETING', '技术路线与研发投入评审会议，输出技术决策',
       '{"goalStatement":"评审技术路线可行性与研发资源投入优先级","successCriteria":["技术风险已识别","投入产出有测算","优先级排序有依据"]}',
       '["CAUSAL","SIMULATION"]', 'ACTIVE', 'V363', NOW()
WHERE NOT EXISTS (SELECT 1 FROM ecos_control.ecos_scenario_template WHERE id = 'tpl-meeting-tech');

INSERT INTO ecos_control.ecos_scenario_template
    (id, name, category, description, definition_defaults, default_analysis, status, create_by, create_time)
SELECT 'tpl-meeting-prod', '月度生产调度会议', 'MEETING', '月度产能与交付调度会议，输出排产与资源调配方案',
       '{"goalStatement":"平衡月度产能与交付需求，形成排产与资源调配方案","successCriteria":["产能负荷可量化","瓶颈工序已识别","调配方案可执行"]}',
       '["FORECAST","SIMULATION"]', 'ACTIVE', 'V363', NOW()
WHERE NOT EXISTS (SELECT 1 FROM ecos_control.ecos_scenario_template WHERE id = 'tpl-meeting-prod');
