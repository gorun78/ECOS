-- V205 (卷07 §六 E 章 E-2 目标形态): 业务场景主表 合规重建（W184/W171）
-- 追溯: W184/C166、W171/C153；需求依据 REQ-WS-01 / REQ-WS-02
-- 批准: 需求检视报告 §十四 14.1（2026-09-29）之 R-24=②（B 案，部署形态不产生 DDL）、R-25=①、R-30=①+②；执行边界 §14.4 = 脚本文件落地
-- 状态: 仅落迁移脚本文件，**未对 ecos-postgres 实跑**（实跑属 §14.4 未授权项，需逐项再确认）
-- 纪律: DR01~DR08 / MC01~MC03 / ST03-A / ST07 / ST09 / IR02(手动 psql) / IR03(只加不删)
-- 【2026-09-30 校订】DR03 收口：本表原按分册 07 L1073「新表统一 `_v2` 后缀」定名 ecos_business_scenario_v2，
--   而 DR03 反例即 `ecos_workflow_task_v2`（版本后缀禁入表名），故改名 ecos_scenario_definition。旧同名停写表保留原名的策略不变（IR03 不删旧表）。
--   安全性实证：本表零行（本批不实跑）、全仓 Java/XML/TS 对该名 0 引用；库内旧同名停写表名未被占用。
--   文件名保留 V*__*__ecos_business_scenario_v2（psql -f 引用不变），文件名↔表名映射登记于落地清单。
-- 命名策略（分册 07 L1073）: 新表命名合规化（原策略"统一 `_v2` 后缀"违 DR03，2026-09-30 收口为 `ecos_` + 业务对象单数名，见下）；旧表 public.ecos_business_scenario **停写不删除**（IR03），
--   存量数据迁移与旧表归档属存量动作**待授权**；只读对账走 V213 的 v_legacy_* 视图，不建同名视图
--   （同 schema 对象名冲突，E-2 显式否决）。
-- 落点 (MC06): 控制域现基线 = public（目标 ecos_control，ADR-12）⇒ 本批新表落 `public.`，
--   迁 ecos_control 后由 `ecos.db.control-schema` 配置注入改前缀，本批不预置。
-- X-75 定版: 旧 `budget VARCHAR(64)` 存金额（种子 '850万'）违"金额列唯一形态 NUMERIC(p,s)"
--   ⇒ 新表用 `budget NUMERIC(18,2)` + `budget_currency VARCHAR(8)`；历史串值**不做自动解析**
--   （'850万' vs 8500000 歧义太大），存量换算登记为待授权迁移项（§14.4）。
-- E-3 定版: 旧 `metrics JSONB` 中 integrityScore/mappingCompleteness 属**计算产物禁落库**
--   （防第二完整度源，X-25，只由 /completeness 现算）；其余指标以 `metrics_json TEXT` 承载。
-- 上线: psql -U postgres -d sys_man -f V205__ecos_business_scenario_v2.sql

-- ── 1. 新表 ──────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.ecos_scenario_definition (
    id                  VARCHAR(36) PRIMARY KEY,  -- MC01: 应用侧生成 UUID，DDL 无默认值（旧 VARCHAR(64) 违 MC01）
    name                VARCHAR(255) NOT NULL,
    description         TEXT,
    business_goal       TEXT,
    department          VARCHAR(128),
    priority            VARCHAR(16)  NOT NULL DEFAULT 'MEDIUM',
    status              VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',
    budget              NUMERIC(18,2),            -- ST03-A: 金额唯一形态 NUMERIC(p,s)（X-75 定版）
    budget_currency     VARCHAR(8),               -- ISO 4217（如 CNY/USD）
    safety_index_target NUMERIC(8,4),             -- E-5: DECIMAL→NUMERIC 拼写统一（精度显式，非违规形式项）
    actual_safety_index NUMERIC(8,4),             -- E-5: 同上
    metrics_json        TEXT,                     -- DR04/MC02: JSON 只用 TEXT + _json 后缀；计算产物不落库
    create_time         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- DR06 审计五列
    update_time         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by           VARCHAR(64) NOT NULL DEFAULT 'system',
    update_by           VARCHAR(64) NOT NULL DEFAULT 'system',
    is_deleted          SMALLINT  NOT NULL DEFAULT 0,                  -- DR05
    domain              VARCHAR(50) NOT NULL DEFAULT 'default',        -- DR08（旧表缺，X-45）
    version_no          VARCHAR(20) NOT NULL DEFAULT '1',              -- DR07（旧表缺，X-45）
    CONSTRAINT ck_scen_def_priority CHECK (priority IN ('CRITICAL','HIGH','MEDIUM','LOW')),   -- X-74 补 CHECK
    CONSTRAINT ck_scen_def_status   CHECK (status   IN ('DRAFT','ACTIVE','COMPLETED','SUSPENDED'))
);
COMMENT ON TABLE  public.ecos_scenario_definition IS '业务场景主表（MC/DR 合规重建；旧 public.ecos_business_scenario 停写不删，对账走 V213 v_legacy_* 视图）';
COMMENT ON COLUMN public.ecos_scenario_definition.budget IS '预算金额，NUMERIC(18,2)（ST03-A）；旧 VARCHAR 串值不做自动解析，存量换算待授权';
COMMENT ON COLUMN public.ecos_scenario_definition.status IS '值域按 V123 注释四态定版；服务端状态机（ScenarioStatusMachine, F07-07）为权威迁移校验，若终态值域扩展随 R-24/R-25 后续批以附加 CHECK 演进';

-- ── 2. 索引（E-4：保持旧三件 + 补复合；MC03 无 partial）────
CREATE INDEX IF NOT EXISTS idx_scen_def_status ON public.ecos_scenario_definition(status);
CREATE INDEX IF NOT EXISTS idx_scen_def_dept   ON public.ecos_scenario_definition(department);
CREATE INDEX IF NOT EXISTS idx_scen_def_statdel ON public.ecos_scenario_definition(status, is_deleted, domain);

-- ── 3. 存量登记（注释，不动表不迁数）──────────────────────
-- 旧表 public.ecos_business_scenario：本批后停写（写通道唯一 = F07-19 ScenarioBindingMapper 指新表 ecos_scenario_definition），
-- 不 DROP、不改名（IR03）；其中 AOC 演示种子 sc001~sc003（V123:47-52）剥离至
-- ecos_backend/database/demo_seed.sql 属存量动作待授权（E-6/X-48），本脚本不修改 V123、不删库内行。

-- ── 回滚说明 ──────────────────────────────────────────────
-- 纯新建表零数据迁移；回滚 = 应用 Mapper 指回旧表名（E-7.2），新表保留为空表不 DROP（IR03）。

-- ── DDL Lint Self-audit ──────────────────────────────────
-- [DR01] public 显式限定 + 小写下划线 ✓  [DR02] ecos_ 前缀 ✓  [DR03] 单数 ✓
-- [DR04] metrics_json _json + TEXT ✓  [DR05] is_deleted SMALLINT ✓  [DR06/07/08] 审计五列 + version_no + domain ✓
-- [MC01] VARCHAR(36) 应用侧 UUID 无默认值 ✓  [MC02] 零 JSONB ✓
-- [MC03] 无 partial index/RLS/PARTITION/text[]/timestamptz/裸 cast ✓
-- [ST03-A] budget NUMERIC(18,2)、safety_index_* NUMERIC(8,4) 精度显式 ✓（金额列登记见汇报）
-- [ST07] 控制域落 public（目标 ecos_control，MC06 配置注记）✓  [IR03] 旧表停写不删、零数据迁移 ✓
