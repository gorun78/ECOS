-- V208 (卷07 §六 E 章 E-2 目标形态): 场景多心智表 合规重建 + 激活表 + 引用明细表（W184）
-- 追溯: W184/C166；需求依据 REQ-WS-02 / REQ-KB-04（cognitive 三表只读引用）
-- 批准: 需求检视报告 §十四 14.1（2026-09-29）之 R-25=①、R-30=①+②；执行边界 §14.4 = 脚本文件落地
-- 状态: 仅落迁移脚本文件，**未对 ecos-postgres 实跑**（实跑属 §14.4 未授权项，需逐项再确认）
-- 纪律: DR01~DR08 / MC01~MC03 / ST03-A / ST07 / ST09 / IR02(手动 psql) / IR03(只加不删)
-- 【2026-09-30 校订】DR03 收口：本表原按分册 07 L1073「新表统一 `_v2` 后缀」定名 ecos_scenario_mind_v2，
--   而 DR03 反例即 `ecos_workflow_task_v2`（版本后缀禁入表名），故改名 ecos_scenario_mind_variant。旧同名停写表保留原名的策略不变（IR03 不删旧表）。
--   安全性实证：本表零行（本批不实跑）、全仓 Java/XML/TS 对该名 0 引用；库内旧同名停写表名未被占用。
--   文件名保留 V*__*__ecos_scenario_mind_v2（psql -f 引用不变），文件名↔表名映射登记于落地清单。
-- 命名策略（分册 07 L1073）: 新表命名合规化（原 `_v2` 后缀违 DR03，已收口，见下）；旧 public.ecos_scenario_mind（V146，PK BIGINT IDENTITY
--   违 MC01、5 JSONB 违 MC02、2 partial unique 违 MC03/X-81）停写不删除；对账走 V213。
-- 落点 (MC06): 控制域现基线 = public（目标 ecos_control，ADR-12）⇒ 新表落 public.，
--   迁 ecos_control 后由 `ecos.db.control-schema` 配置注入改前缀，本批不预置。
-- E-2/E-3/E-4 定版:
--   1) PK VARCHAR(36) 应用侧 UUID；
--   2) 5 个 JSONB → `_json TEXT`；evidence/hypothesis/model 三类引用需可 JOIN ⇒ 抽明细表
--      ecos_scenario_mind_ref(mind_id, ref_kind, ref_id)，_json 列降级为快照；
--   3) cognitive_endpoints **移出本表**（端点配置属配置项，经 sysman 配置单源 config_group/subsystem，
--      AGENTS.md 其他约束 4 + 后端规范 §十，禁表内自存）；
--   4) `active_mind` 哨兵（违 DR05）+ partial unique（违 MC03）⇒ 改**独立 1:1 激活表**
--      ecos_scenario_active_mind(scenario_id PK, mind_id)，单激活由 PK 天然保证，切换原子性由服务层事务（F07-19-2）；
--   5) 标签唯一改 `(scenario_id, mind_label, deleted_guard)` 普通唯一（E-4：deleted_guard 默认
--      1970-01-01 纪元值，软删时写删除时刻；MC03 禁 partial 的替代形态）。
-- 上线: psql -U postgres -d sys_man -f V208__ecos_scenario_mind_v2.sql

-- ── 1. 心智表 ─────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.ecos_scenario_mind_variant (
    id                 VARCHAR(36) PRIMARY KEY,   -- MC01: 应用侧 UUID（旧 BIGINT IDENTITY 违 MC01）
    scenario_id        VARCHAR(36) NOT NULL,      -- 指向 public.ecos_scenario_definition.id（1:N，逻辑引用无 FK）
    mind_label         VARCHAR(64) NOT NULL DEFAULT 'base',
    deleted_guard      TIMESTAMP NOT NULL DEFAULT TIMESTAMP '1970-01-01 00:00:00',
        -- E-4: 软删守卫列（活行=纪元值，软删时写删除时刻），配合下方普通唯一索引替代 partial unique；
        --     文档写法 '1970-01-01'::TIMESTAMP 违 MC03 裸 cast 禁令，落地改 typed literal 形态
    initial_belief_json TEXT NOT NULL,            -- MC02/DR04: 原名 initial_belief_jsonb（违命名）→ _json + TEXT
    evidence_refs_json TEXT,                      -- E-3: 快照位；权威明细在 ecos_scenario_mind_ref
    hypothesis_refs_json TEXT,                    -- 同上
    model_refs_json   TEXT,                       -- 同上
    initial_confidence DOUBLE PRECISION NOT NULL DEFAULT 0.5,
        -- E-5: 非金额浮点（置信度允许浮点），已登记免门禁误报
    create_time        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- DR06 审计五列
    update_time        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by          VARCHAR(64) NOT NULL DEFAULT 'system',
    update_by          VARCHAR(64) NOT NULL DEFAULT 'system',
    is_deleted         SMALLINT  NOT NULL DEFAULT 0,                  -- DR05
    domain             VARCHAR(50) NOT NULL DEFAULT 'default',        -- DR08（旧表缺）
    version_no         VARCHAR(20) NOT NULL DEFAULT '1'               -- DR07（旧表缺）
);
COMMENT ON TABLE public.ecos_scenario_mind_variant IS '场景多心智变体表（JSONB→TEXT、cognitive_endpoints 移出经 sysman 配置、active_mind 改激活表；旧表停写不删）';

-- ── 2. 独立激活表（替代 active_mind 哨兵 + partial unique，E-2/E-4）──
CREATE TABLE IF NOT EXISTS public.ecos_scenario_active_mind (
    scenario_id  VARCHAR(36) PRIMARY KEY,        -- MC01: 即 public.ecos_scenario_definition.id（1:1，PK 保证单激活）
    mind_id      VARCHAR(36) NOT NULL,           -- 指向 public.ecos_scenario_mind_variant.id（逻辑引用无 FK）
    create_time  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- DR06
    update_time  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by    VARCHAR(64) NOT NULL DEFAULT 'system',
    update_by    VARCHAR(64) NOT NULL DEFAULT 'system',
    is_deleted   SMALLINT  NOT NULL DEFAULT 0,
    domain       VARCHAR(50) NOT NULL DEFAULT 'default',
    version_no   VARCHAR(20) NOT NULL DEFAULT '1'
);
COMMENT ON TABLE public.ecos_scenario_active_mind IS '场景激活心智 1:1 表（MC03 安全形态替代 partial unique；切换原子性由服务层事务 F07-19-2）';

-- ── 3. 心智引用明细表（E-3：引用集合需可 JOIN，替代数组式 JSON）──
CREATE TABLE IF NOT EXISTS public.ecos_scenario_mind_ref (
    id          VARCHAR(36) PRIMARY KEY,         -- MC01
    mind_id     VARCHAR(36) NOT NULL,            -- 指向 public.ecos_scenario_mind_variant.id
    ref_kind    VARCHAR(20) NOT NULL,            -- EVIDENCE | HYPOTHESIS | MODEL（E-3 三值）
    ref_id      VARCHAR(36) NOT NULL,            -- cognitive 三表 id（跨域只读引用，属主在 ecos_cognitive，不建 FK）
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- DR06
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by   VARCHAR(64) NOT NULL DEFAULT 'system',
    update_by   VARCHAR(64) NOT NULL DEFAULT 'system',
    is_deleted  SMALLINT  NOT NULL DEFAULT 0,
    domain      VARCHAR(50) NOT NULL DEFAULT 'default',
    version_no  VARCHAR(20) NOT NULL DEFAULT '1',
    CONSTRAINT ck_esmr_kind CHECK (ref_kind IN ('EVIDENCE','HYPOTHESIS','MODEL'))
);
COMMENT ON TABLE public.ecos_scenario_mind_ref IS '心智引用明细表（E-3：evidence/hypothesis/model 引用 1:N 明细；cognitive 侧仅存 id 引用、只读）';
CREATE INDEX IF NOT EXISTS idx_esmr_mind ON public.ecos_scenario_mind_ref(mind_id, ref_kind);
CREATE UNIQUE INDEX IF NOT EXISTS uniq_esmr_ref ON public.ecos_scenario_mind_ref(mind_id, ref_kind, ref_id);

-- ── 4. 索引（E-4；MC03 无 partial）────────────────────────
CREATE INDEX IF NOT EXISTS idx_scen_mind_var_scenario ON public.ecos_scenario_mind_variant (scenario_id, is_deleted);
CREATE UNIQUE INDEX IF NOT EXISTS uniq_scen_mind_var_scenario_label
    ON public.ecos_scenario_mind_variant (scenario_id, mind_label, deleted_guard);  -- 纪元值 ⇒ 活行唯一；软删行按删除时刻并存

-- ── 5. 存量登记（注释，不动表不迁数）──────────────────────
-- 旧表 public.ecos_scenario_mind：停写不 DROP（IR03）；cognitive 三表（evidence/hypothesis/belief，
-- V127~V129）仅以 id 引用心智、不 ALTER（V146 原口径保持）；旧行 BIGINT id → UUID 的存量映射属
-- 迁移待授权项（§14.4），本批不生成。

-- ── 回滚说明 ──────────────────────────────────────────────
-- 纯新建表零数据迁移；回滚 = 应用 Mapper 指回旧表名（E-7.2），三张新表保留为空表不 DROP（IR03）。

-- ── DDL Lint Self-audit ──────────────────────────────────
-- [DR01] public 限定 ✓  [DR02] ecos_ 前缀 ✓  [DR03] 单数 ✓
-- [DR04] initial_belief_json/evidence_refs_json/hypothesis_refs_json/model_refs_json 全 _json+TEXT ✓
-- [DR05] is_deleted SMALLINT（active_mind 哨兵已废）✓  [DR06/07/08] 三表齐备 ✓
-- [MC01] 三表均 VARCHAR(36) 应用侧 UUID，零 IDENTITY/SERIAL/gen_random_uuid ✓
-- [MC02] 零 JSONB ✓  [MC03] 唯一索引均无 WHERE；deleted_guard 默认值用 typed literal 非裸 cast ✓
-- [ST03-A] initial_confidence 为非金额浮点（E-5 登记）✓  [ST07] 控制域落 public；跨域引用无 FK ✓
-- [IR03] 旧表停写不删 ✓
