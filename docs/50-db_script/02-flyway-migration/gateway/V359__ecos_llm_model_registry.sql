-- ============================================================================
-- V359 — ecos_ai.ecos_llm_model 建表（E-3 模型注册数据入表，用户裁决 2026-11-19）
-- ============================================================================
-- 为什么需要本表（E-3 收口）：
--   `POST /api/v1/ai/models`（#18 registerLlmModel）长期**显式 501 零写入**，理由清单第一条
--   即「llm.models.catalog 属 Spring yml 部署期形态，注册即升版无处可写」。
--   2026-11-19 用户裁决：**模型注册数据入表**（而非写 yml / 走 Git）。
--   本表即该裁决的存储载体；写通道由同批 Controller 代码落地。
--
-- 键与列的口径：
--   * `model_key`    = provider 侧模型短名（如 `deepseek-chat`），与
--                      `LLMGatewayProperties.Models.catalog` 的 Map key 同义。
--                      唯一性靠 `uniq_llm_model_key(model_key, is_deleted)`——软删后可重注册同名。
--   * `provider`     = 提供方标识（deepseek/openrouter/openai/anthropic），供网关路由。
--   * `endpoint`     = 该模型的 API 基址（可空：空则用 provider 的默认 base-url）。
--   * `api_key_ref`  = **仅引用名**（如 `llm-key-ref`），禁存密钥材料（ST04/X-49）。
--   * `context_limit`= 上下文窗口 token 数；`<=0` 表示未登记（消费方据此判"读数不可用"，
--                      禁兜底常量——沿用 `ModelLimit.available` 同口径）。
--   * `capabilities_json` / `cost_class` = 契约声明的能力与成本档（`capabilities`/`costClass`）。
--   * `status`       = `ACTIVE` / `RETIRED`（升版后旧版置 RETIRED，不物理删）。
--   * `version_no`   = 升版计数（每次注册同 model_key 自增）。
--
-- Schema 归属（ST07）：`ecos_ai`——AI 引擎域，与同族 `ecos_ai_tool_registry` 一致。
--   **不落 `ecos_control`**：模型注册是 AI 域资产，非控制域配置。
--
-- 列模板：对齐 `ecos_ai.ecos_ai_tool_registry`（domain / version_no / is_deleted /
--   create_by / create_time / update_by / update_time），其中 `is_deleted` 取 SMALLINT
--   （MC02 类型白名单不含 BOOLEAN）。
--
-- 纪律：IR03 只加不删；MC01 主键 VARCHAR(36) 应用侧 UUID；MC02 类型白名单内；
--       MC03 用标准 SQL（NOW() 而非 PG 专有的其他函数）；DR06~DR08 基线列齐备；
--       ST04/ST05 不写 RLS 策略；ST07 schema 显式限定。
-- ============================================================================

CREATE TABLE IF NOT EXISTS ecos_ai.ecos_llm_model (
    id                  VARCHAR(36)  NOT NULL,
    model_key           VARCHAR(120) NOT NULL,
    provider            VARCHAR(40)  NOT NULL,
    endpoint            VARCHAR(512),
    api_key_ref         VARCHAR(128),
    context_limit       INTEGER      NOT NULL DEFAULT 0,
    capabilities_json   TEXT,
    cost_class          VARCHAR(40),
    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    domain              VARCHAR(50)  NOT NULL DEFAULT 'default',
    version_no          VARCHAR(20)  NOT NULL DEFAULT '1',
    is_deleted          SMALLINT     NOT NULL DEFAULT 0,
    create_by           VARCHAR(64)  NOT NULL,
    create_time         TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_by           VARCHAR(64),
    update_time         TIMESTAMP,
    CONSTRAINT pk_ecos_llm_model PRIMARY KEY (id),
    CONSTRAINT ck_ecos_llm_model_status CHECK (status IN ('ACTIVE', 'RETIRED')),
    CONSTRAINT ck_ecos_llm_model_ctx CHECK (context_limit >= 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS uniq_llm_model_key
    ON ecos_ai.ecos_llm_model (model_key, is_deleted);

CREATE INDEX IF NOT EXISTS idx_llm_model_status
    ON ecos_ai.ecos_llm_model (status, is_deleted);

COMMENT ON TABLE ecos_ai.ecos_llm_model IS 'LLM 模型注册表（E-3，#18 registerLlmModel 的运行期写载体；api_key_ref 仅引用名，禁存密钥材料）';
COMMENT ON COLUMN ecos_ai.ecos_llm_model.api_key_ref IS '密钥引用名（非密钥材料，X-49/ST04）';
COMMENT ON COLUMN ecos_ai.ecos_llm_model.context_limit IS '上下文窗口 token 数；<=0 = 未登记，消费方判读数不可用（禁兜底常量）';
