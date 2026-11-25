-- ============================================================================
-- V365 — 场景运行分级与 L3 研究谱系（RD-Agent 模式接入首切片，2026-11-25）
-- ============================================================================
-- 目标（只加不删）：
--   1) 运行记录补「运行分级 / 谱系 / 研究预算 / 停止原因」控制列，支撑三级运行
--      （L1 心智模型 / L2 算法 / L3 RD-Agent 研究循环）与多轮谱系回溯；
--   2) 新增运行产物引用表（产物实体落对象存储，DB 只存引用与摘要）；
--   3) 不新建「实验」平行表：谱系长在 ecos_scenario_run 上，保持一次运行一行。
--
-- 纪律：IR03 只加不删；MC01 主键 VARCHAR(36) 应用侧 UUID；MC02 类型白名单；
--       MC03 标准 SQL；ST07 落 ecos_control（workspace 主控制域）；
--       DR06 审计列齐备；DR07 version_no；DR08 domain；不建 FK（ST09）；
--       本批无金额列（MC04 不适用）。
--
-- 口径：
--   run_level 缺省 NULL ⇒ 存量行与 L1/L2 路径行为零变化；L3 显式写 'RD_AGENT'。
--   parent_run_id / sota_run_id 为逻辑引用（ST09 不建 FK），指向同表 id。
--   研究轮次的 run_mode 固定 SANDBOX（不计 isFormalRunExists），
--   仅当候选审批通过后由人发起 FORMAL 运行，正式口径不被研究轮次污染。
-- ============================================================================

-- ── 1. 运行记录：分级 + 谱系 + 预算控制列 ────────────────────────────────
ALTER TABLE ecos_control.ecos_scenario_run
    ADD COLUMN IF NOT EXISTS run_level     VARCHAR(16),
    ADD COLUMN IF NOT EXISTS parent_run_id VARCHAR(36),
    ADD COLUMN IF NOT EXISTS sota_run_id   VARCHAR(36),
    ADD COLUMN IF NOT EXISTS branch_key    VARCHAR(64),
    ADD COLUMN IF NOT EXISTS loop_index    INTEGER,
    ADD COLUMN IF NOT EXISTS experiment_spec JSONB,
    ADD COLUMN IF NOT EXISTS budget        JSONB,
    ADD COLUMN IF NOT EXISTS stop_reason   VARCHAR(32);

COMMENT ON COLUMN ecos_control.ecos_scenario_run.run_level IS
    '运行分级（V365）：MENTAL_MODEL=L1 心智模型问答 / ALGORITHM=L2 算法分析 / RD_AGENT=L3 研究循环；NULL=存量行或未分级';
COMMENT ON COLUMN ecos_control.ecos_scenario_run.parent_run_id IS
    '谱系父运行 id（V365；L3 多轮研究指向上轮，逻辑引用不建 FK）';
COMMENT ON COLUMN ecos_control.ecos_scenario_run.sota_run_id IS
    '谱系选优 id（V365；记录本支最近一次 decision=true 的运行，供回放与对比）';
COMMENT ON COLUMN ecos_control.ecos_scenario_run.branch_key IS
    '研究分支键（V365；同一问题下并行探索的分支标识，缺省空）';
COMMENT ON COLUMN ecos_control.ecos_scenario_run.loop_index IS
    '研究轮次（V365；L3 内从 0 起，非 L3 为 NULL）';
COMMENT ON COLUMN ecos_control.ecos_scenario_run.experiment_spec IS
    '实验规格（V365；L3 的目标/数据切片/成功标准/评估口径 JSON）';
COMMENT ON COLUMN ecos_control.ecos_scenario_run.budget IS
    '研究预算（V365；{maxLoops,maxSecondsPerLoop,maxTotalSeconds,maxTokens} 冻结快照）';
COMMENT ON COLUMN ecos_control.ecos_scenario_run.stop_reason IS
    '停止原因（V365）：ACCEPTED/BUDGET_EXHAUSTED/MAX_LOOPS/NO_PROGRESS/FAILED/HUMAN_STOPPED';

CREATE INDEX IF NOT EXISTS idx_scenario_run_lineage
    ON ecos_control.ecos_scenario_run (scenario_id, parent_run_id, is_deleted);
CREATE INDEX IF NOT EXISTS idx_scenario_run_level
    ON ecos_control.ecos_scenario_run (scenario_id, run_level, create_time DESC);

-- ── 2. 运行产物引用表（产物实体落对象存储，DB 只存引用与摘要）──────────────
CREATE TABLE IF NOT EXISTS ecos_control.ecos_scenario_run_artifact (
    id                  VARCHAR(36)  NOT NULL,
    run_id              VARCHAR(36)  NOT NULL,
    scenario_id         VARCHAR(36)  NOT NULL,
    artifact_type       VARCHAR(32)  NOT NULL,
    storage_ref         VARCHAR(512) NOT NULL,
    digest              VARCHAR(80)  NOT NULL,
    size_bytes          BIGINT,
    domain              VARCHAR(50)  NOT NULL DEFAULT 'default',
    version_no          VARCHAR(20)  NOT NULL DEFAULT '1',
    is_deleted          SMALLINT     NOT NULL DEFAULT 0,
    create_by           VARCHAR(64)  NOT NULL,
    create_time         TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_by           VARCHAR(64),
    update_time         TIMESTAMP,
    CONSTRAINT pk_ecos_scenario_run_artifact PRIMARY KEY (id)
);

COMMENT ON TABLE ecos_control.ecos_scenario_run_artifact IS
    '场景运行产物引用（V365）：L3 研究的代码/日志/图表/快照摘要，实体落对象存储，本表只存引用与 sha256';
COMMENT ON COLUMN ecos_control.ecos_scenario_run_artifact.artifact_type IS
    '产物类型（V365）：CODE/LOG/METRIC/CHART/SNAPSHOT/ADVICE';
COMMENT ON COLUMN ecos_control.ecos_scenario_run_artifact.storage_ref IS
    '对象存储引用（V365；bucket/key 形态，禁存正文）';
COMMENT ON COLUMN ecos_control.ecos_scenario_run_artifact.digest IS
    '内容摘要（V365；sha256:<hex>，回放与防篡改锚）';

CREATE INDEX IF NOT EXISTS idx_run_artifact_run
    ON ecos_control.ecos_scenario_run_artifact (run_id, is_deleted);
