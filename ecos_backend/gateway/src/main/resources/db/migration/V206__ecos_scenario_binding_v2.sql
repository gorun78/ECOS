-- V206 (卷07 §六 E 章 E-2 目标形态): 场景绑定表 合规重建（W168/W169/W184）
-- 追溯: W168/C150、W169/C151、W184/C166；需求依据 REQ-WS-02 §2.2/§2.4
-- 批准: 需求检视报告 §十四 14.1（2026-09-29）之 R-25=①、R-26=①、R-30=①+②；执行边界 §14.4 = 脚本文件落地
-- 状态: 仅落迁移脚本文件，**未对 ecos-postgres 实跑**（实跑属 §14.4 未授权项，需逐项再确认）
-- 纪律: DR01~DR08 / MC01~MC03 / ST03-A / ST07 / ST09 / IR02(手动 psql) / IR03(只加不删)
-- 【2026-09-30 校订】DR03 收口：本表原按分册 07 L1073「新表统一 `_v2` 后缀」定名 ecos_scenario_binding_v2，
--   而 DR03 反例即 `ecos_workflow_task_v2`（版本后缀禁入表名），故改名 ecos_scenario_asset_binding。旧同名停写表保留原名的策略不变（IR03 不删旧表）。
--   安全性实证：本表零行（本批不实跑）、全仓 Java/XML/TS 对该名 0 引用；库内旧同名停写表名未被占用。
--   文件名保留 V*__*__ecos_scenario_binding_v2（psql -f 引用不变），文件名↔表名映射登记于落地清单。
-- 命名策略（分册 07 L1073）: 新表命名合规化（原 `_v2` 后缀违 DR03，已收口，见下）；旧 public.ecos_scenario_binding（V123:27 + V147）停写不删除；
--   岛标语义只在新表面生效（**不是对旧表 ALTER 加列**，E-7 批次纪律零 ALTER 既有表），
--   旧表读路径由 V213 对账视图显式补 `0 AS is_island`（文档示例 FALSE 因 DR05 统一 SMALLINT，见下）。
-- 落点 (MC06): 控制域现基线 = public（目标 ecos_control，ADR-12）⇒ 新表落 public.；
--   迁 ecos_control 后由 `ecos.db.control-schema` 配置注入改前缀，本批不预置。
-- X-73 定版: 三套词汇表收敛为**后端 BINDING_ENDPOINTS 六值权威词表**（DATASET/OBJECT_TYPE/KNOWLEDGE_BASE/
--   AI_AGENT/SECURITY_POLICY/INTERFACE），CHECK 落在写路径列 `binding_type` 上（旧表 CHECK 锁的是
--   target_type 且值域是前端那套）；前端→后端旧值映射（DATASOURCE→DATASET 等）在 V213 对账视图承载，
--   历史值回填属存量动作待授权。
-- R-26 ① 口径: 岛标为保存期打标（DRAFT 容忍），激活期 409 由服务端 IslandBindingGuard 判定，
--   **DDL 侧不做拒绝逻辑**（判定依据是边可达性，归 V207 边表 + 服务层事务）。
-- 上线: psql -U postgres -d sys_man -f V206__ecos_scenario_binding_v2.sql

-- ── 1. 新表 ──────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.ecos_scenario_asset_binding (
    id             VARCHAR(36) PRIMARY KEY,       -- MC01: 应用侧 UUID（旧 VARCHAR(64) 违 MC01）
    scenario_id    VARCHAR(36) NOT NULL,          -- 指向 public.ecos_scenario_definition.id（逻辑引用，不建 FK）
    binding_type   VARCHAR(32) NOT NULL,          -- 六值权威词表（后端 BINDING_ENDPOINTS，X-73 统一）
    target_ref     VARCHAR(255),                  -- 资源标识（沿用旧列宽，保迁移无损；*_ref 语义）
    target_id      VARCHAR(64),                   -- 真资源主键（外部业务键，沿用 V147 语义）
    target_type    VARCHAR(32),                   -- 旧真资源类型（保留只读兼容；新表写路径以 binding_type 为准）
    remark         TEXT,
    is_island      SMALLINT NOT NULL DEFAULT 0,   -- DR05: 岛标（文档示例 BOOLEAN/FALSE，按红线收敛 SMALLINT 0/1）
    island_reason  VARCHAR(64),                  -- E-2 定版 VARCHAR(64)
    create_time    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- DR06 审计五列
    update_time    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by      VARCHAR(64) NOT NULL DEFAULT 'system',
    update_by      VARCHAR(64) NOT NULL DEFAULT 'system',
    is_deleted     SMALLINT  NOT NULL DEFAULT 0,  -- DR05
    domain         VARCHAR(50) NOT NULL DEFAULT 'default',  -- DR08（旧表缺）
    version_no     VARCHAR(20) NOT NULL DEFAULT '1',        -- DR07（旧表缺）
    CONSTRAINT ck_scen_bind_binding_type CHECK (binding_type IN
        ('DATASET','OBJECT_TYPE','KNOWLEDGE_BASE','AI_AGENT','SECURITY_POLICY','INTERFACE'))
);
COMMENT ON TABLE  public.ecos_scenario_asset_binding IS '场景六类绑定表（binding_type 六值 CHECK 单列权威；旧 ecos_scenario_binding 停写不删）';
COMMENT ON COLUMN public.ecos_scenario_asset_binding.is_island IS '孤岛标记（保存期打标，R-26① DRAFT 容忍；激活期 409 在服务层，DDL 不承载拒绝逻辑）';

-- ── 2. 索引（E-4：保持旧三件；MC03 无 partial）─────────────
CREATE INDEX IF NOT EXISTS idx_scen_bind_scenario ON public.ecos_scenario_asset_binding(scenario_id);
CREATE INDEX IF NOT EXISTS idx_scen_bind_type     ON public.ecos_scenario_asset_binding(binding_type);
CREATE INDEX IF NOT EXISTS idx_scen_bind_tid      ON public.ecos_scenario_asset_binding(target_id);

-- ── 3. 存量登记（注释，不动表不迁数）──────────────────────
-- 旧表 public.ecos_scenario_binding：本批后停写不 DROP（IR03）；其中绑定种子 sb001~sb008（V123:55-65）
-- 属 AOC 演示数据，剥离至 database/demo_seed.sql 待授权（E-6），历史 target_type 前端词表值 →
-- 后端六值映射回填亦待授权（F07-05-3）。

-- ── 回滚说明 ──────────────────────────────────────────────
-- 纯新建表零数据迁移；回滚 = 应用 Mapper 指回旧表名（E-7.2），新表保留为空表不 DROP（IR03）。

-- ── DDL Lint Self-audit ──────────────────────────────────
-- [DR01] public 限定 ✓  [DR02] ecos_ 前缀 ✓  [DR03] 单数 ✓  [DR04] remark 纯文本无 JSON 列 ✓
-- [DR05] is_island/is_deleted SMALLINT ✓（BOOLEAN 形态零命中）  [DR06/07/08] 审计五列 + version_no + domain ✓
-- [MC01] VARCHAR(36) UUID ✓  [MC02] 零 JSONB ✓
-- [MC03] 无 partial index/RLS/PARTITION/text[]/timestamptz/裸 cast ✓
-- [ST03-A] 无金额/敏感列新增 ✓  [ST07] 控制域落 public（MC06 注记）✓  [IR03] 旧表停写不删 ✓
