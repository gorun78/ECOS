-- V161 (PMO-74.8 H8-T1, L2): 遥测表 DDL 单源化收编 — ecos_spans / ecos_token_usage
-- 背景: 原 gateway/telemetry/TelemetryTableInitializer 在 ApplicationReadyEvent 时
--       jdbc.execute("CREATE TABLE ...") 运行时隐式建表（L2 红线）。本脚本将建表
--       语义全量收编至迁移单源，代码侧初始化器删除；遥测写入路径（QuotaFilter /
--       UsageCollector / PostgresSpanExporter，纯 DML）不动，runtime-monitor 搬迁
--       登记为 H8 后续子指令。
-- 历史链: V20 建表(public) → V47 SET SCHEMA ecos_audit（后续运维又迁 ecos_security）
--         → 运行时代码在 public 重建 → 本脚本以 public（主控制 schema, 现网代码
--         非限定名实际解析落点）为准收编。schema 归位 ecos_audit/ecos_security 属
--         后续批次（R9 只加不删，不做迁移动作）。
-- 规范: DR06 审计 5 字段 + DR07 version_no + DR08 domain；IR03 只加不删；幂等 IF NOT EXISTS

-- ── 1. 新环境建表（与 V20 列形态一致 + 审计列）────────────────────
CREATE TABLE IF NOT EXISTS public.ecos_spans (
    span_id        VARCHAR(64) PRIMARY KEY,           -- 技术追踪键（OTel W3C trace/span id），非业务对象主键，knownLegacy 列形态保留
    trace_id       VARCHAR(64) NOT NULL,
    parent_span_id VARCHAR(64),
    operation_name VARCHAR(512),
    service_name   VARCHAR(128),
    http_method    VARCHAR(16),
    http_path      VARCHAR(512),
    http_status    INT DEFAULT 0,
    start_time     TIMESTAMP NOT NULL DEFAULT NOW(),
    end_time       TIMESTAMP,
    duration_ms    BIGINT DEFAULT 0,
    status         VARCHAR(16) DEFAULT 'OK',
    attributes     JSONB,
    created_at     TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.ecos_token_usage (
    id                BIGSERIAL PRIMARY KEY,          -- knownLegacy: V20 起 BIGSERIAL 自增技术键，存量不 RENAME（R9/IR03）
    trace_id          VARCHAR(64),
    model             VARCHAR(64),
    operation         VARCHAR(256),
    prompt_tokens     INT DEFAULT 0,
    completion_tokens INT DEFAULT 0,
    total_tokens      INT DEFAULT 0,
    cost_estimate     NUMERIC(10,6) DEFAULT 0,        -- MC02: 金额列唯一形态 NUMERIC(p,s)
    latency_ms        BIGINT DEFAULT 0,
    created_at        TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_spans_trace      ON public.ecos_spans(trace_id);
CREATE INDEX IF NOT EXISTS idx_spans_created    ON public.ecos_spans(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_spans_path       ON public.ecos_spans(http_path);
CREATE INDEX IF NOT EXISTS idx_token_usage_trace   ON public.ecos_token_usage(trace_id);
CREATE INDEX IF NOT EXISTS idx_token_usage_created ON public.ecos_token_usage(created_at DESC);

-- ── 2. 存量表补审计 5 字段 + domain + version_no（DR06/07/08，只加不删）──
ALTER TABLE public.ecos_spans ADD COLUMN IF NOT EXISTS create_time TIMESTAMP NOT NULL DEFAULT NOW();
ALTER TABLE public.ecos_spans ADD COLUMN IF NOT EXISTS update_time TIMESTAMP NOT NULL DEFAULT NOW();
ALTER TABLE public.ecos_spans ADD COLUMN IF NOT EXISTS create_by   VARCHAR(100);
ALTER TABLE public.ecos_spans ADD COLUMN IF NOT EXISTS update_by   VARCHAR(100);
ALTER TABLE public.ecos_spans ADD COLUMN IF NOT EXISTS is_deleted  SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE public.ecos_spans ADD COLUMN IF NOT EXISTS domain      VARCHAR(50) NOT NULL DEFAULT 'default';
ALTER TABLE public.ecos_spans ADD COLUMN IF NOT EXISTS version_no  VARCHAR(20) NOT NULL DEFAULT '1';

ALTER TABLE public.ecos_token_usage ADD COLUMN IF NOT EXISTS create_time TIMESTAMP NOT NULL DEFAULT NOW();
ALTER TABLE public.ecos_token_usage ADD COLUMN IF NOT EXISTS update_time TIMESTAMP NOT NULL DEFAULT NOW();
ALTER TABLE public.ecos_token_usage ADD COLUMN IF NOT EXISTS create_by   VARCHAR(100);
ALTER TABLE public.ecos_token_usage ADD COLUMN IF NOT EXISTS update_by   VARCHAR(100);
ALTER TABLE public.ecos_token_usage ADD COLUMN IF NOT EXISTS is_deleted  SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE public.ecos_token_usage ADD COLUMN IF NOT EXISTS domain      VARCHAR(50) NOT NULL DEFAULT 'default';
ALTER TABLE public.ecos_token_usage ADD COLUMN IF NOT EXISTS version_no  VARCHAR(20) NOT NULL DEFAULT '1';

COMMENT ON TABLE public.ecos_spans IS 'P3-5 HTTP span 遥测表（H8-T1 起 DDL 单源，运行时代码禁隐式建表）';
COMMENT ON TABLE public.ecos_token_usage IS 'P3-5 LLM token 用量遥测表（H8-T1 起 DDL 单源）';

-- ── DDL Lint Self-audit ──────────────────────────────────
-- [DR06/07/08] 审计 5 字段 + version_no + domain 补齐 ✓
-- [IR03] 零 DROP / 零改列，仅 IF NOT EXISTS 建表 + ADD COLUMN ✓
-- [MC02] cost_estimate NUMERIC(10,6) ✓
-- [ST07] 落主控制 schema public（沿用现网代码非限定名解析落点；跨域搬迁另批）✓
