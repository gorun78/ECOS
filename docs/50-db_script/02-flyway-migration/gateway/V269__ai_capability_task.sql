-- V269 (补充设计-认知域与智能域 v1.0 §10.4/10.5): 能力调用台账 ecos_ai.ecos_ai_capability_task
-- 追溯: REQ-AI-03（可观测计量）/ REQ-AI-08（Model Router）；§10.4 ModelCapabilityExecutor 异步句柄
--       §10.5 统一结果结构（capabilityId/status/modelRef/modelVersion/latencyMs/quality）
-- 归属: 控制域 AI 引擎 schema（ST07；与 V230~V236 wagent 族同域 ecos_ai）
-- 红线:
--   * 本表只存**引用与元数据**，禁复制大文件或完整敏感输入（§10.5 末段）；
--   * 唯一 LLM/Embedding 出口仍为 llm-gateway；小模型（OCR/ASR/…）经同一网关体系接入，
--     **不新建第二套并行小模型管理**（§16.8 捷径禁令）→ 本表是调用台账，不是模型注册表
--     （模型注册与门禁在 CM registry / sys_config 族）；
--   * 降级不满足质量门槛时 status 必须 DEGRADED/FAILED，禁伪装 SUCCEEDED（§10.6）；
--   * 非 Token 模型按调用次数/输入规模计量（cost_unit 列），与 LLM_USAGE 事件同源互补（§10.6 计量条）。
-- 状态: 2026-10-27 已对 dev PG（sys_man）实跑（#146g 授权迁移实跑批，手动 psql ON_ERROR_STOP=1 exit 0；库面证据：ecos_ai.ecos_ai_capability_task 在位 0 行）
-- 纪律: DR01~DR08 / MC01~MC03 / ST07 / IR02 / IR03(只加不删)

CREATE TABLE IF NOT EXISTS ecos_ai.ecos_ai_capability_task (
    id                     VARCHAR(36) PRIMARY KEY,          -- MC01 应用侧 UUID（即 taskId）
    tenant_id              VARCHAR(64) NOT NULL,
    org_id                 VARCHAR(64),
    task_kind              VARCHAR(10) NOT NULL,             -- SYNC / ASYNC（§10.4：同步直执 / 异步经 runtime-task 提交）
    capability_id          VARCHAR(50) NOT NULL,             -- 能力 ID（§10.2 目录；词表单源 AiCapabilityCatalog）
    tool_contract_ref      VARCHAR(100),                     -- 触发的 Tool Contract 引用（operationClass 等机校字段单源 V270）
    input_ref              VARCHAR(200) NOT NULL,            -- 输入引用（文件/片段/向量引用 ID），禁存正文
    model_ref              VARCHAR(100),                     -- 选中模型（Model Router 决策产物，§10.3）
    model_version          VARCHAR(20),
    output_ref             VARCHAR(200),                     -- 结果引用（OCR 结果等落 runtime-access/MinIO 通道，业务库只存引用）
    status                 VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING/SUBMITTED/RUNNING/SUCCEEDED/DEGRADED/FAILED/CANCELED
    quality_score          NUMERIC(6,5),                     -- 可选质量分（0..1 同族精度；MC02 带精度）
    quality_kind           VARCHAR(30),                      -- MODEL_ESTIMATE 等（§8.3 同词表纪律，禁冒充统计概率）
    calibrated             SMALLINT,                         -- NULL=未声明；0/1。UNCALIBRATED 断言不得用于权威裁决（§10.2 末段）
    cost_unit              VARCHAR(40),                      -- 计量口径（TOKEN/CALL/INPUT_SCALE/...，§10.6 非 Token 计量）
    latency_ms             BIGINT,
    degraded               SMALLINT NOT NULL DEFAULT 0,      -- DR05 禁布尔 → SMALLINT 0/1（同 V232 同族口径）
    warnings_text          TEXT,                             -- JSON 数组（MC02）：降级原因/质量不满足/备用模型未达门槛等
    selected_model_reason  TEXT,                             -- 路由决策留痕（§10.3: requestedCapability/候选/未选原因/预算/策略引用）
    trace_id               VARCHAR(64),
    run_id                 VARCHAR(36),
    submitter              VARCHAR(100),
    domain                 VARCHAR(50) NOT NULL DEFAULT 'default',
    version_no             VARCHAR(20) NOT NULL,
    create_time            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by              VARCHAR(100),
    update_by              VARCHAR(100),
    is_deleted             SMALLINT NOT NULL DEFAULT 0
);
COMMENT ON TABLE ecos_ai.ecos_ai_capability_task IS
    '能力调用台账（补充设计 v1.0 §10.4/10.5）：只存引用与元数据；LLM/Embedding 唯一出口仍 llm-gateway；小模型节点复用，禁第二套管理（§10.6/SUP-MODEL-01~03）';
CREATE INDEX IF NOT EXISTS idx_cv269_cap_tid  ON ecos_ai.ecos_ai_capability_task (tenant_id, capability_id, is_deleted);
CREATE INDEX IF NOT EXISTS idx_cv269_cap_run  ON ecos_ai.ecos_ai_capability_task (run_id, is_deleted);
CREATE INDEX IF NOT EXISTS idx_cv269_cap_stat ON ecos_ai.ecos_ai_capability_task (status, is_deleted);

-- ── 回滚说明 ──────────────────────────────────────────────
-- 数据回滚：仅新增表。回滚 = 应用侧停用 /api/v1/ai/capabilities 族端点；删表只允许人工裁决（IR03 禁脚本内 DROP）。
-- 幂等性：CREATE TABLE/INDEX IF NOT EXISTS ⇒ 重复执行零报错。

-- ── DDL Lint Self-audit ──────────────────────────────────────
-- [DR01] schema 限定（ecos_ai.）✓ [DR02/03] 新表单数 ✓ [DR04] JSON 全 TEXT ✓
-- [DR05] 布尔一律 SMALLINT 0/1 ✓ [DR06~08] 七基线列齐 ✓
-- [MC01] UUID 主键 ✓ [MC02] quality_score NUMERIC(6,5) 带精度；BIGINT latency ✓
-- [MC03] 无条件索引 / 无 RLS / TIMESTAMP 无时区 ✓
-- [ST07] 控制域 AI schema ✓ [IR02] 仅文件 ✓ [IR03] 只加 ✓
