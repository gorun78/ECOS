-- V210 (卷07 §六 E 章 E-2 目标形态): 沙盘画布布局表 合规重建（W183/W184）
-- 追溯: W183/C165、W184/C166；需求依据 REQ-WS-02 / REQ-DB-01
-- 批准: 需求检视报告 §十四 14.1（2026-09-29）之 R-25=①、R-30=①+②；执行边界 §14.4 = 脚本文件落地
-- 状态: 仅落迁移脚本文件，**未对 ecos-postgres 实跑**（实跑属 §14.4 未授权项，需逐项再确认）
-- 纪律: DR01~DR08 / MC01~MC03 / ST03-A / ST07 / ST09 / IR02(手动 psql) / IR03(只加不删)
-- 【2026-09-30 校订】DR03 收口：本表原按分册 07 L1073「新表统一 `_v2` 后缀」定名 ecos_scenario_sandbox_layout_v2，
--   而 DR03 反例即 `ecos_workflow_task_v2`（版本后缀禁入表名），故改名 ecos_scenario_canvas_layout。旧同名停写表保留原名的策略不变（IR03 不删旧表）。
--   安全性实证：本表零行（本批不实跑）、全仓 Java/XML/TS 对该名 0 引用；库内旧同名停写表名未被占用。
--   文件名保留 V*__*__ecos_scenario_sandbox_layout_v2（psql -f 引用不变），文件名↔表名映射登记于落地清单。
-- 命名策略（分册 07 L1073）: 新表命名合规化（原 `_v2` 后缀违 DR03，已收口，见下）；旧 public.ecos_scenario_sandbox_layout（V150，PK BIGINT
--   IDENTITY 违 MC01、layout_jsonb JSONB 违 MC02、与另一 V150 版本号重复 X-82）停写不删除；对账走 V213。
-- 落点 (MC06): 控制域现基线 = public（目标 ecos_control，ADR-12）⇒ 新表落 public.，
--   迁 ecos_control 后由 `ecos.db.control-schema` 配置注入改前缀，本批不预置。
-- E-2/E-3 定版: `layout_json TEXT`（React Flow {nodes,edges,viewport} 序列化属"非检索非约束"典型，
--   纯存不参与查询，MC02 合规形态）；乐观锁保留业务列 `layout_version INTEGER`，**另补 DR 基线列
--   version_no/domain**，两列语义不混用（D 章声明：layout_version=画布编辑乐观锁，
--   version_no=记录行版本基线列）。
-- 上线: psql -U postgres -d sys_man -f V210__ecos_scenario_sandbox_layout_v2.sql

-- ── 1. 新表 ──────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.ecos_scenario_canvas_layout (
    id             VARCHAR(36) PRIMARY KEY,       -- MC01: 应用侧 UUID（旧 BIGINT IDENTITY 违 MC01）
    scenario_id    VARCHAR(36) NOT NULL,          -- 指向 public.ecos_scenario_definition.id（1:1，逻辑引用无 FK）
    layout_json    TEXT NOT NULL,                 -- DR04/MC02: 原名 layout_jsonb → _json + TEXT；不参与 WHERE/JOIN/索引
    layout_version INTEGER NOT NULL DEFAULT 1,    -- 乐观锁（业务语义，与 version_no 基线列不混用，E-2 声明）
    create_time    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- DR06 审计五列
    update_time    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by      VARCHAR(64) NOT NULL DEFAULT 'system',
    update_by      VARCHAR(64) NOT NULL DEFAULT 'system',
    is_deleted     SMALLINT  NOT NULL DEFAULT 0,  -- DR05
    domain         VARCHAR(50) NOT NULL DEFAULT 'default',  -- DR08（旧表缺，X-45）
    version_no     VARCHAR(20) NOT NULL DEFAULT '1'         -- DR07（旧表缺；语义=行版本基线，非画布乐观锁）
);
COMMENT ON TABLE  public.ecos_scenario_canvas_layout IS '沙盘画布布局表（React Flow 序列化 TEXT 化；乐观锁 layout_version 与基线 version_no 双列并存语义分离；旧表停写不删）';
COMMENT ON COLUMN public.ecos_scenario_canvas_layout.layout_version IS '画布编辑乐观锁（防并发覆盖，业务语义）';
COMMENT ON COLUMN public.ecos_scenario_canvas_layout.version_no IS 'DR07 记录行基线版本列（与 layout_version 语义无关，D 章声明）';

-- ── 2. 索引（旧表 scenario_id UNIQUE 的 1:1 约束以普通唯一索引保持；MC03 无 partial）──
CREATE UNIQUE INDEX IF NOT EXISTS uniq_scen_canvas_scenario ON public.ecos_scenario_canvas_layout(scenario_id);

-- ── 3. 存量登记（注释，不动表不迁数）──────────────────────
-- 旧表 public.ecos_scenario_sandbox_layout：停写不 DROP（IR03）；旧行 BIGINT id → UUID 重映射与
-- layout_jsonb 文本化属存量迁移待授权项（§14.4）；X-82 的 V150 版本号重复对（ecos_entity_table_mapping_
-- doc_anchor_json）属确定性缺陷直改项，不在本批处置。

-- ── 回滚说明 ──────────────────────────────────────────────
-- 纯新建表零数据迁移；回滚 = 应用 Mapper 指回旧表名（E-7.2），新表保留为空表不 DROP（IR03）。

-- ── DDL Lint Self-audit ──────────────────────────────────
-- [DR01] public 限定 ✓  [DR02] ecos_ 前缀 ✓  [DR03] 单数 ✓
-- [DR04] layout_json _json + TEXT ✓  [DR05] is_deleted SMALLINT ✓  [DR06/07/08] 齐备 ✓
-- [MC01] VARCHAR(36) UUID 零 IDENTITY ✓  [MC02] 零 JSONB ✓
-- [MC03] 唯一索引无 WHERE；无裸 cast/PARTITION/text[]/timestamptz ✓
-- [ST03-A] 无金额/敏感列新增 ✓  [ST07] 控制域落 public（MC06 注记）✓  [IR03] 旧表停写不删 ✓
