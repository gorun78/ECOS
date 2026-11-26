-- ============================================================================
-- V367 — 场景运行算法执行与执行载体（2026-11-26 场景工作台下一步落地 · 批次 A/H）
-- ============================================================================
-- 目标（只加不删）：
--   1) ecos_scenario_run 增「算法执行结果/状态/耗时」与「执行载体三列」，
--      支撑「选算法 → 真执行 → 落真实数值 → LLM 只解释」与
--      「心智模型 / 场景编排 / 智能体」三选一的主载体；
--   2) ecos_algorithm_registry 增 category/description/executor_kind，
--      并把 analysis_type 词表扩到六类（BELIEF/CAUSAL/FORECAST/SIMULATION/DECISION/DIAGNOSTIC）。
--
-- 纪律：IR03 只加不删；MC01 主键 VARCHAR(36) 应用侧 UUID；MC02 类型白名单；
--       MC03 标准 SQL；ST07 落 ecos_control（workspace 主控制域）；
--       DR06 审计列齐备；DR07 version_no；DR08 domain；不建 FK（ST09）；本批无金额列。
--
-- IR03-EXEMPT: 2026-11-26 六类算法目录扩词表——DROP/ADD CHECK 属约束替换（非删列/删表/数据回改），
--              依据批准计划批次 A；仅改 CHECK 词表，不动任何列与数据。
--
-- 口径：executor_type NULL = 存量行，读侧按 MENTAL_MODEL 兼容；
--       executor_kind=DETERMINISTIC_SPI 为旧 L2 算法（保留 implementation_ref URL，不参与问题运行）。
-- ============================================================================

-- ── 1. 运行记录：算法结果 + 执行载体 ──────────────────────────────────────
ALTER TABLE ecos_control.ecos_scenario_run
    ADD COLUMN IF NOT EXISTS algorithm_result     JSONB,
    ADD COLUMN IF NOT EXISTS algorithm_status     VARCHAR(16),
    ADD COLUMN IF NOT EXISTS algorithm_elapsed_ms INTEGER,
    ADD COLUMN IF NOT EXISTS executor_type        VARCHAR(24),
    ADD COLUMN IF NOT EXISTS executor_id          VARCHAR(64),
    ADD COLUMN IF NOT EXISTS executor_session_id  VARCHAR(64);

COMMENT ON COLUMN ecos_control.ecos_scenario_run.algorithm_result IS
    '算法执行结构化结果（V367；{code,metrics,assumptions,trace}，LLM 只能解释不得新增数字）';
COMMENT ON COLUMN ecos_control.ecos_scenario_run.algorithm_status IS
    '算法执行状态（V367）：SUCCEEDED|FAILED|SKIPPED|UNAVAILABLE';
COMMENT ON COLUMN ecos_control.ecos_scenario_run.algorithm_elapsed_ms IS
    '算法执行真实耗时毫秒（V367）';
COMMENT ON COLUMN ecos_control.ecos_scenario_run.executor_type IS
    '主执行载体（V367）：MENTAL_MODEL|SCENARIO_PIPELINE|AI_AGENT；NULL=存量按 MENTAL_MODEL 兼容';
COMMENT ON COLUMN ecos_control.ecos_scenario_run.executor_id IS
    '主执行载体 id（V367；心智模型 code / 逻辑编排 id / 智能体 id）';
COMMENT ON COLUMN ecos_control.ecos_scenario_run.executor_session_id IS
    '智能体会话 id（V367；仅 AI_AGENT 载体使用，便于复盘追溯）';

CREATE INDEX IF NOT EXISTS idx_scenario_run_executor
    ON ecos_control.ecos_scenario_run (scenario_id, executor_type, create_time DESC);

-- ── 2. 算法目录：分类 / 描述 / 执行器类型 ──────────────────────────────────
ALTER TABLE ecos_control.ecos_algorithm_registry
    ADD COLUMN IF NOT EXISTS category      VARCHAR(32),
    ADD COLUMN IF NOT EXISTS description   TEXT,
    ADD COLUMN IF NOT EXISTS executor_kind VARCHAR(24) NOT NULL DEFAULT 'JAVA';

COMMENT ON COLUMN ecos_control.ecos_algorithm_registry.category IS
    '六类算法归属（V367）：BELIEF|CAUSAL|FORECAST|SIMULATION|DECISION|DIAGNOSTIC';
COMMENT ON COLUMN ecos_control.ecos_algorithm_registry.description IS
    '一句话说明与输入假设（V367；禁空壳，必须写清限制）';
COMMENT ON COLUMN ecos_control.ecos_algorithm_registry.executor_kind IS
    '执行器类型（V367）：JAVA（cognitive 纯 Java）|PYTHON_SANDBOX（研究沙盒）|DETERMINISTIC_SPI（旧 L2 URL）';

ALTER TABLE ecos_control.ecos_algorithm_registry
    DROP CONSTRAINT IF EXISTS ck_ecos_algorithm_type;
ALTER TABLE ecos_control.ecos_algorithm_registry
    ADD CONSTRAINT ck_ecos_algorithm_type
    CHECK (analysis_type IN ('BELIEF', 'CAUSAL', 'FORECAST', 'SIMULATION', 'DECISION', 'DIAGNOSTIC'));

ALTER TABLE ecos_control.ecos_algorithm_registry
    DROP CONSTRAINT IF EXISTS ck_ecos_algorithm_category;
ALTER TABLE ecos_control.ecos_algorithm_registry
    ADD CONSTRAINT ck_ecos_algorithm_category
    CHECK (category IS NULL OR category IN ('BELIEF', 'CAUSAL', 'FORECAST', 'SIMULATION', 'DECISION', 'DIAGNOSTIC'));

ALTER TABLE ecos_control.ecos_algorithm_registry
    DROP CONSTRAINT IF EXISTS ck_ecos_algorithm_executor;
ALTER TABLE ecos_control.ecos_algorithm_registry
    ADD CONSTRAINT ck_ecos_algorithm_executor
    CHECK (executor_kind IN ('JAVA', 'PYTHON_SANDBOX', 'DETERMINISTIC_SPI'));

-- ── 3. 旧 4 条登记补分类（保留 URL 与执行语义，不冒充新执行器）─────────────
UPDATE ecos_control.ecos_algorithm_registry
SET category = 'CAUSAL', executor_kind = 'DETERMINISTIC_SPI',
    description = '旧 L2 因果诊断面（POST /cognitive/diagnose），问题运行不直接执行',
    update_by = 'V367', update_time = NOW()
WHERE id = 'alg-causal-diagnose';

UPDATE ecos_control.ecos_algorithm_registry
SET category = 'FORECAST', executor_kind = 'DETERMINISTIC_SPI',
    description = '旧六要素确定性预测（/cognitive/forecast-runs/compute），问题运行不直接执行',
    update_by = 'V367', update_time = NOW()
WHERE id = 'alg-forecast-deterministic';

UPDATE ecos_control.ecos_algorithm_registry
SET category = 'SIMULATION', executor_kind = 'DETERMINISTIC_SPI',
    description = '旧情景仿真面（/cognitive/scenario/simulate），问题运行不直接执行',
    update_by = 'V367', update_time = NOW()
WHERE id = 'alg-simulation-scenario';

UPDATE ecos_control.ecos_algorithm_registry
SET category = 'SIMULATION', executor_kind = 'DETERMINISTIC_SPI',
    description = '旧反事实推演面（/cognitive/counterfactual），问题运行不直接执行',
    update_by = 'V367', update_time = NOW()
WHERE id = 'alg-counterfactual';
