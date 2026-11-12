-- V329 (AI 工作台逻辑编排持久化，Wagent 模板融合批 1): AIP 逻辑编排定义表 ecos_ai.ecos_aip_pipeline
-- 追溯: 需求=「智能体编排(Wagent)融入逻辑编排（模板型）」批 1 前置（定义不落库则模板生成物存不住）；
--       修复 P0 = 前端 LogicView 纯内存 CRUD 刷新即丢 + 后端 AIPPipelineController ConcurrentHashMap。
-- 状态: 仅落迁移脚本文件，**未对 ecos-postgres 实跑**（实跑属未授权项，需逐项再确认）。
-- 纪律: DR01~DR08 / MC01(应用侧 UUID) / MC02(JSON 语义 TEXT) / ST06 / ST07(ecos_ai) / ST09(无 FK) / IR03(只加不删)
-- 口径: definition_json 承载画布 nodes/edges/steps 完整定义（前端 LogicView 序列化产物）；
--       template_ref 预留批 2 用——记录来源 Playbook 的 git 引用（*_git_ref→VARCHAR(120)），可空=手工创建。

CREATE TABLE IF NOT EXISTS ecos_ai.ecos_aip_pipeline (
    id              VARCHAR(36) PRIMARY KEY,              -- MC01 应用侧 UUID
    tenant_id       VARCHAR(64) NOT NULL,
    name            VARCHAR(255) NOT NULL,
    description     VARCHAR(500),
    definition_json TEXT NOT NULL DEFAULT '{}',           -- MC02 JSON 语义 TEXT（nodes/edges/steps）
    status          VARCHAR(12) NOT NULL DEFAULT 'draft', -- draft/published
    template_ref    VARCHAR(120),                         -- 批 2：来源 playbook git 引用（可空）
    version_no      VARCHAR(20) NOT NULL DEFAULT '1',
    last_executed_at TIMESTAMP,
    create_time     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by       VARCHAR(100),
    update_by       VARCHAR(100),
    is_deleted      SMALLINT NOT NULL DEFAULT 0
);
COMMENT ON TABLE ecos_ai.ecos_aip_pipeline IS 'AIP 逻辑编排定义（AI 工作台 LogicView 画布持久化；V329 批 1：内存 store 迁 PG；template_ref 预留批 2 Wagent Playbook 模板来源追溯）';
COMMENT ON COLUMN ecos_ai.ecos_aip_pipeline.last_executed_at IS
    '最近执行时刻（execute 端点回写；响应 lastExecutedAt 尾键来源，对齐 DI-2 #147EC 键序）';

-- UNIQUE(name, version_no, is_deleted)（同 V237 口径：禁 partial ⇒ 含 is_deleted）
CREATE UNIQUE INDEX IF NOT EXISTS uniq_aip_pipeline_name_ver
    ON ecos_ai.ecos_aip_pipeline(name, version_no, is_deleted);
CREATE INDEX IF NOT EXISTS idx_aip_pipeline_tenant ON ecos_ai.ecos_aip_pipeline(tenant_id);
CREATE INDEX IF NOT EXISTS idx_aip_pipeline_status ON ecos_ai.ecos_aip_pipeline(status);

-- ── DDL Lint Self-audit ──────────────────────────────────
-- [DR01] ecos_ai. ✓ [DR02] ecos_ ✓ [DR05/06/07/08] 通用列齐 ✓ [MC01] UUID ✓ [MC02] JSON 全 TEXT
-- [唯一索引] 非 partial、含 is_deleted ✓  [ST07] ecos_ai ✓  [ST09] 无 FK ✓  [IR03] 只加不删 ✓
