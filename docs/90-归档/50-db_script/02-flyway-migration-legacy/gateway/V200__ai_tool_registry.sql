-- V200 (卷06 §六 E 章 6.2 新增 DDL): AI 工具声明式注册表（F06-11，替换硬编码白名单 DataInitializer:83）
-- 追溯: W155/C137；需求依据 REQ-AI-04
-- 批准: 需求检视报告 §十四 14.1（2026-09-29）之 R-19=①+② 并行、R-12=①+②、R-18=②；执行边界 §14.4 = 脚本文件落地
-- 状态: 仅落迁移脚本文件，**未对 ecos-postgres 实跑**（实跑属 §14.4 未授权项，需逐项再确认）
-- 纪律: DR01~DR08 / MC01~MC03 / ST03-A / ST07 / ST09 / IR02(手动 psql) / IR03(只加不删)
-- 归属口径 (R-19 ①+② 并行):
--   1) 新表按 MC/DR 合规形态建于 ecos_ai（ST07 权威 schema）；
--   2) ecos_ai 内 50 张 0 行空壳（26 jsonb / PK64 / 基线列 0/50）待 R-12 同批处置，**本批不 DROP**；
--   3) public 的 5 张在用真身表（sys_agent_session / sys_agent_message / ecos_agent_registry /
--      agent_memory / sys_agent_profile 等，见分册 06 §6.1 实测）**只停写不迁**（注释登记，不搬表）；
--   4) 与既有 ecos_tool_definition（jsonb、双镜像、无基线列）**并建新表而非改造**（R-12 ①+②：
--      零行/违规表 DROP 需"备份+引用扫描"双条件，前置未满足 ⇒ 本批一律并建 + 旧表停写，不写 DROP）。
-- 表名注记 (DR02 收口，2026-09-30): 文档定版表名 `ai_tool_registry` 缺 `ecos_` 前缀，违 DR02（新表强制前缀）。
--   只读取证：本机库无该表、Java/XML/TS 零引用（V201/V204 仅注释逻辑引用）⇒ 改名零兼容代价，
--   定名 `ecos_ai.ecos_ai_tool_registry`；同批 V201/V203/V204 三表同法收口。旧表 ecos_tool_definition 仍停写不删（IR03）。
-- 上线: psql -U postgres -d sys_man -f V200__ai_tool_registry.sql（文件名沿用落地号段 slug，表名见 §1）

-- ── 1. 新表 ──────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS ecos_ai.ecos_ai_tool_registry (
  id VARCHAR(36) NOT NULL,                      -- MC01: 应用侧生成 UUID，DDL 无默认值
  tool_key       VARCHAR(120)  NOT NULL,
  name           VARCHAR(200)  NOT NULL,
  description    TEXT,
  operation      VARCHAR(10)   NOT NULL DEFAULT 'read',
  args_schema_json TEXT        NOT NULL,        -- DR04: JSON 语义列 _json 后缀 + TEXT（文档原名 args_schema，
                                                --   因 MC02"JSON 只用 TEXT"且 DR04 要求 _json 后缀，落地时更名）
  backend_kind   VARCHAR(20)   NOT NULL,        -- REST | SQL | BUILTIN
  backend_endpoint TEXT,
  permission_hint  TEXT,                        -- 例 "scenario:write"（权限提示文本，权威裁决在 security-engine）
  opa_action_type  VARCHAR(40) NOT NULL DEFAULT 'TOOL_CALL',
  resource_scope_projection TEXT,               -- 允许进裁决载荷的入参字段白名单（逗号分隔，非 JSON）
  timeout_ms     INTEGER       NOT NULL DEFAULT 2000,
  status         VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
  trace_id       VARCHAR(64),
  domain         VARCHAR(50)   NOT NULL DEFAULT 'default',  -- DR08（文档 64/'DEFAULT' 收敛为规范形态 50/'default'）
  version_no     VARCHAR(20)   NOT NULL DEFAULT '1',        -- DR07（文档 INTEGER 收敛为 VARCHAR(20)）
  is_deleted     SMALLINT      NOT NULL DEFAULT 0,          -- DR05
  create_by      VARCHAR(64)   NOT NULL,
  update_by      VARCHAR(64),
  create_time    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- DR06 审计五列
  update_time    TIMESTAMP,
  CONSTRAINT pk_ecos_ai_tool_registry PRIMARY KEY (id),
  CONSTRAINT ck_atr_operation CHECK (operation IN ('read','write')),
  CONSTRAINT ck_atr_status    CHECK (status IN ('ACTIVE','DISABLED','DRAFT'))
);
COMMENT ON TABLE ecos_ai.ecos_ai_tool_registry IS 'AI 工具声明式注册表（F06-11；R-19 并建合规新表；旧 ecos_tool_definition 双镜像停写不删）';

-- ── 2. 索引（MC03：无 partial index、无 WHERE 子句）────────
CREATE UNIQUE INDEX IF NOT EXISTS uniq_atr_key ON ecos_ai.ecos_ai_tool_registry (tool_key, is_deleted);
CREATE INDEX IF NOT EXISTS idx_atr_status ON ecos_ai.ecos_ai_tool_registry (status, is_deleted);

-- ── 回滚说明 ──────────────────────────────────────────────
-- 纯新建表，无存量数据改动；回滚 = 应用侧不读写本表即可（表保留为空表，IR03 禁 DROP）。

-- ── DDL Lint Self-audit ──────────────────────────────────
-- [DR01] ecos_ai 前缀限定 + 小写下划线 ✓  [DR02] 新表 ecos_ai_tool_registry 带 ecos_ 前缀 ✓（文档字面缺前缀，已收口，见头注）
-- [DR03] 单数 ✓  [DR04] args_schema_json _json 后缀 + TEXT ✓  [DR05] is_deleted SMALLINT ✓
-- [DR06/07/08] 审计五列 + version_no VARCHAR(20) + domain VARCHAR(50) DEFAULT 'default' ✓
-- [MC01] VARCHAR(36) 应用侧 UUID 无默认值 ✓（禁 gen_random_uuid/SERIAL）
-- [MC02] 零 JSONB、JSON 形态列用 TEXT ✓  [MC03] 无 partial index/RLS/PARTITION/text[]/timestamptz/裸 cast ✓
-- [IR03] 旧表不 DROP 不改名（并建新表 + 停写登记）✓  [ST07] 落 AI 引擎 schema ecos_ai ✓
