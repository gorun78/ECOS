-- V201 (卷06 §六 E 章 6.2 新增 DDL): 工具-角色权限绑定表（F06-02 载荷投影与权限映射）
-- 追溯: W144/C126；需求依据 REQ-AI-01
-- 批准: 需求检视报告 §十四 14.1（2026-09-29）之 R-19=①+② 并行、R-18=②；执行边界 §14.4 = 脚本文件落地
-- 状态: 仅落迁移脚本文件，**未对 ecos-postgres 实跑**（实跑属 §14.4 未授权项，需逐项再确认）
-- 纪律: DR01~DR08 / MC01~MC03 / ST03-A / ST07 / ST09 / IR02(手动 psql) / IR03(只加不删)
-- R-18 ② 口径（DDL 侧）: 取密/ABAC 一律经 security-engine REST ⇒ **本批不建任何策略副本表**。
--   本表仅登记"哪个角色可调用哪个工具 + 载荷需要哪些主体属性"的**映射声明**；
--   required_attributes / obligation_expectation 两列是**验收对账用的期望形态**（缓存语义），
--   裁决权威与策略本体在 security-engine（policy evaluate / OPA），本表列不参与运行期裁决判定。
-- 归属口径 (R-19): 新表建于 ecos_ai（MC/DR 合规形态）；与 R-20 无涉。
-- 上线: psql -U postgres -d sys_man -f V201__ai_tool_permission_binding.sql

-- ── 1. 新表 ──────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS ecos_ai.ecos_ai_tool_permission_binding (
  id VARCHAR(36) NOT NULL,                       -- MC01: 应用侧生成 UUID，DDL 无默认值
  tool_key VARCHAR(120) NOT NULL,                -- 指向 ecos_ai.ecos_ai_tool_registry.tool_key（跨表逻辑引用，不建 FK）
  role_key VARCHAR(64) NOT NULL,                 -- 允许调用该工具的角色
  required_attributes TEXT,                       -- 载荷必需主体属性（roles/department/dataScope）；**权威在 security-engine，本列仅声明缓存**
  obligation_expectation TEXT,                    -- 期望 obligations 形态（mask/rowFilter），用于验收对账；**权威在 security-engine，本列仅声明缓存**
  domain VARCHAR(50) NOT NULL DEFAULT 'default',  -- DR08（文档 64/'DEFAULT' 收敛为规范形态）
  version_no VARCHAR(20) NOT NULL DEFAULT '1',    -- DR07（文档 INTEGER 收敛为 VARCHAR(20)）
  is_deleted SMALLINT NOT NULL DEFAULT 0,         -- DR05
  create_by VARCHAR(64) NOT NULL,
  update_by VARCHAR(64),
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- DR06 审计五列
  update_time TIMESTAMP,
  CONSTRAINT pk_ai_tool_perm PRIMARY KEY (id)
);
COMMENT ON TABLE ecos_ai.ecos_ai_tool_permission_binding IS '工具-角色权限绑定声明表（F06-02；禁把 LLM 任意入参塞进裁决载荷；策略权威在 security-engine，本表仅映射声明）';

-- ── 2. 索引（MC03：无 partial index）──────────────────────
CREATE INDEX IF NOT EXISTS idx_atpb_tool ON ecos_ai.ecos_ai_tool_permission_binding (tool_key, is_deleted);
CREATE INDEX IF NOT EXISTS idx_atpb_role ON ecos_ai.ecos_ai_tool_permission_binding (role_key, is_deleted);

-- ── 回滚说明 ──────────────────────────────────────────────
-- 纯新建表，无存量数据改动；回滚 = 应用侧不读写本表即可（表保留为空表，IR03 禁 DROP）。

-- ── DDL Lint Self-audit ──────────────────────────────────
-- [DR01] ecos_ai 限定 ✓  [DR02] 新表 ecos_ai_tool_permission_binding 带 ecos_ 前缀 ✓（文档字面缺前缀，已收口，见 V200 头注）
-- [DR03] 单数 ✓  [DR04] 无 JSON 语义列（required_attributes/obligation_expectation 为逗号分文本声明）✓
-- [DR05] is_deleted SMALLINT ✓  [DR06/07/08] 审计五列 + version_no VARCHAR(20) + domain 'default' ✓
-- [MC01] VARCHAR(36) 应用侧 UUID ✓  [MC02] 零 JSONB ✓
-- [MC03] 无 partial index/RLS/PARTITION/text[]/timestamptz/裸 cast ✓
-- [R-18②] 未建策略副本表；策略语义列已标"权威在 security-engine，本列仅缓存" ✓
-- [IR03] 零 DROP/零 ALTER 既有表 ✓  [ST07] 落 ecos_ai（AI 引擎控制 schema）✓
