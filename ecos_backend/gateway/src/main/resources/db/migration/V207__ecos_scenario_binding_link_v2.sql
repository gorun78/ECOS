-- V207 (卷07 §六 E 章 E-2 目标形态): 场景绑定关系边表 合规重建（W167/W184）
-- 追溯: W167/C149、W184/C166；需求依据 REQ-WS-02 §2.2 / 铁律 §0.6.2
-- 批准: 需求检视报告 §十四 14.1（2026-09-29）之 R-25=①、R-27=①、R-30=①+②；执行边界 §14.4 = 脚本文件落地
-- 状态: 仅落迁移脚本文件，**未对 ecos-postgres 实跑**（实跑属 §14.4 未授权项，需逐项再确认）
-- 纪律: DR01~DR08 / MC01~MC03 / ST03-A / ST07 / ST09 / IR02(手动 psql) / IR03(只加不删)
-- 【2026-09-30 校订】DR03 收口：本表原按分册 07 L1073「新表统一 `_v2` 后缀」定名 ecos_scenario_binding_link_v2，
--   而 DR03 反例即 `ecos_workflow_task_v2`（版本后缀禁入表名），故改名 ecos_scenario_binding_edge。旧同名停写表保留原名的策略不变（IR03 不删旧表）。
--   安全性实证：本表零行（本批不实跑）、全仓 Java/XML/TS 对该名 0 引用；库内旧同名停写表名未被占用。
--   文件名保留 V*__*__ecos_scenario_binding_link_v2（psql -f 引用不变），文件名↔表名映射登记于落地清单。
-- 命名策略（分册 07 L1073）: 新表命名合规化（原 `_v2` 后缀违 DR03，已收口，见下）；旧 public.ecos_scenario_binding_link（V160，头注自称
--   "红线合规"但 PK VARCHAR(64) 违 MC01，X-76）停写不删除；对账走 V213 v_legacy_* 视图。
-- 落点 (MC06): 控制域现基线 = public（目标 ecos_control，ADR-12）⇒ 新表落 public.，
--   迁 ecos_control 后由 `ecos.db.control-schema` 配置注入改前缀，本批不预置。
-- X-48 破占位: `source_contract VARCHAR(128) NOT NULL` + CHECK 禁 'placeholder-%' 前缀
--   （旧种子 bsl001/bsl002 即 placeholder ⇒ 新数据面上占位契约不可能再写入）。
-- E-4 补防重边: `(scenario_id, source_binding_id, target_binding_id, link_type)` 普通唯一
--   （presentEdges 计数前置条件；MC03 禁 partial，故不加 WHERE）。
-- 上线: psql -U postgres -d sys_man -f V207__ecos_scenario_binding_link_v2.sql

-- ── 1. 新表 ──────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.ecos_scenario_binding_edge (
    id                 VARCHAR(36) PRIMARY KEY,   -- MC01: 应用侧 UUID（旧表头注自称合规实违 MC01，X-76）
    scenario_id        VARCHAR(36) NOT NULL,      -- 指向 public.ecos_scenario_definition.id（逻辑引用无 FK）
    source_binding_id  VARCHAR(36) NOT NULL,      -- 指向 public.ecos_scenario_asset_binding.id
    target_binding_id  VARCHAR(36) NOT NULL,      -- 同上
    link_type          VARCHAR(16) NOT NULL,      -- MAPPING/EXTRACTION/COGNITION/GOVERN/EXPOSE（沿用 V160 五值）
    source_contract    VARCHAR(128) NOT NULL,     -- §0.6.2.2 必带契约引用（旧表可空，新表收紧）
    remark             TEXT,
    create_time        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- DR06 审计五列（V160 原有，保持）
    update_time        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by          VARCHAR(64) NOT NULL DEFAULT 'system',
    update_by          VARCHAR(64) NOT NULL DEFAULT 'system',
    is_deleted         SMALLINT  NOT NULL DEFAULT 0,                  -- DR05
    domain             VARCHAR(50) NOT NULL DEFAULT 'default',        -- DR08（V160 已有，保持）
    version_no         VARCHAR(20) NOT NULL DEFAULT '1',              -- DR07（V160 已有，保持）
    CONSTRAINT chk_scen_link_link_type CHECK (link_type IN ('MAPPING','EXTRACTION','COGNITION','GOVERN','EXPOSE')),
    CONSTRAINT chk_scen_link_no_placeholder CHECK (source_contract NOT LIKE 'placeholder-%')  -- X-48
);
COMMENT ON TABLE  public.ecos_scenario_binding_edge IS '场景绑定关系边表（六类三层有向边，§0.6.2；占位契约 CHECK 禁入；旧表停写不删）';
COMMENT ON COLUMN public.ecos_scenario_binding_edge.source_contract IS '跨工作台契约引用（如 ecos_entity_table_mapping.id），§0.6.2.2 必带；禁 placeholder- 前缀（X-48）';

-- ── 2. 索引（E-4：旧四件保持 + (scenario_id, link_type) + 防重边唯一；MC03 无 partial）──
CREATE INDEX IF NOT EXISTS idx_scen_link_sco  ON public.ecos_scenario_binding_edge(scenario_id);
CREATE INDEX IF NOT EXISTS idx_scen_link_src  ON public.ecos_scenario_binding_edge(source_binding_id);
CREATE INDEX IF NOT EXISTS idx_scen_link_tgt  ON public.ecos_scenario_binding_edge(target_binding_id);
CREATE INDEX IF NOT EXISTS idx_scen_link_typ  ON public.ecos_scenario_binding_edge(link_type);
CREATE INDEX IF NOT EXISTS idx_scen_link_sco_typ ON public.ecos_scenario_binding_edge(scenario_id, link_type);
CREATE UNIQUE INDEX IF NOT EXISTS uniq_scen_link_edge ON public.ecos_scenario_binding_edge
    (scenario_id, source_binding_id, target_binding_id, link_type);

-- ── 3. 存量登记（注释，不动表不迁数）──────────────────────
-- 旧表 public.ecos_scenario_binding_link：停写不 DROP（IR03）；其中占位契约种子 bsl001/bsl002
-- （V160:59-63，source_contract='placeholder-…'）属演示数据，剥离至 database/demo_seed.sql 待授权
-- （E-6）；新表 CHECK 使该类值在新面不可能存在，对账期经 V213 视图可见旧值。

-- ── 回滚说明 ──────────────────────────────────────────────
-- 纯新建表零数据迁移；回滚 = 应用 Mapper 指回旧表名（E-7.2），新表保留为空表不 DROP（IR03）。

-- ── DDL Lint Self-audit ──────────────────────────────────
-- [DR01] public 限定 ✓  [DR02] ecos_ 前缀 ✓  [DR03] 单数 ✓  [DR04] remark 纯文本 ✓
-- [DR05] is_deleted SMALLINT ✓  [DR06/07/08] 审计五列 + version_no + domain ✓
-- [MC01] VARCHAR(36) UUID ✓  [MC02] 零 JSONB ✓
-- [MC03] 唯一索引无 WHERE 子句（非 partial）✓；CHECK 用 NOT LIKE 非裸 cast ✓
-- [ST03-A] 无金额/敏感列新增 ✓  [ST07] 控制域落 public（MC06 注记）✓  [IR03] 旧表停写不删 ✓
