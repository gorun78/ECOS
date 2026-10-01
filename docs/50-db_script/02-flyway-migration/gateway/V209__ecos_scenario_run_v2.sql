-- V209 (卷07 §六 E 章 E-2 目标形态): 场景运行表 合规重建（W172/W184/W185）
-- 追溯: W172/C154、W184/C166、W185/C167；需求依据 REQ-WS-04 §4.1 / REQ-API-06
-- 批准: 需求检视报告 §十四 14.1（2026-09-29）之 R-25=①（含全量端口校正，属部署项不产生 DDL）、
--   R-30=①+② 受控组合；执行边界 §14.4 = 脚本文件落地
-- 状态: 仅落迁移脚本文件，**未对 ecos-postgres 实跑**（实跑属 §14.4 未授权项，需逐项再确认）
-- 纪律: DR01~DR08 / MC01~MC03 / ST03-A / ST07 / ST09 / IR02(手动 psql) / IR03(只加不删)
-- 【2026-09-30 校订】DR03 收口：本表原按分册 07 L1073「新表统一 `_v2` 后缀」定名 ecos_scenario_run_v2，
--   而 DR03 反例即 `ecos_workflow_task_v2`（版本后缀禁入表名），故改名 ecos_scenario_execution。旧同名停写表保留原名的策略不变（IR03 不删旧表）。
--   安全性实证：本表零行（本批不实跑）、全仓 Java/XML/TS 对该名 0 引用；库内旧同名停写表名未被占用。
--   文件名保留 V*__*__ecos_scenario_run_v2（psql -f 引用不变），文件名↔表名映射登记于落地清单。
-- 命名策略（分册 07 L1073）: 新表命名合规化（原 `_v2` 后缀违 DR03，已收口，见下）；旧 public.ecos_scenario_run（V125）停写不删除；对账走 V213。
-- 落点 (MC06): 控制域现基线 = public（目标 ecos_control，ADR-12）⇒ 新表落 public.，
--   迁 ecos_control 后由 `ecos.db.control-schema` 配置注入改前缀，本批不预置。
-- R-25 ①（A 案）定版: FORMAL/SANDBOX **不扩 run_type 枚举**，新增正交列 `run_mode`（"算什么"与
--   "是否隔离"语义正交不可共列）；`run_mode` 为可选附加字段默认 FORMAL（A31 兼容，W169/D-4-1）。
--   `run_type` CHECK 值域**本轮不设**：三方值域并存（X-33/G2-5），权威定版属 PRD-08 §4.1 回写项，
--   待值域定版后以附加 CHECK 演进（先设 CHECK 锁错词表风险 > 收益）。
-- R-30 ①+② 定版: 废弃旧 `decision_id` 空引用（X-37，注释指向 V103 ecos_decision 而本域另有
--   ecos_decision_record）⇒ v2 列名 `decision_record_id`，权威表 = public.ecos_scenario_decision_record（V211）；
--   决策能力底座唯一在 cognitive（经 api 门面/DecisionGateway），本列仅场景侧记录引用，禁第二套底座。
-- 上线: psql -U postgres -d sys_man -f V209__ecos_scenario_run_v2.sql

-- ── 1. 新表 ──────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.ecos_scenario_execution (
    id                  VARCHAR(36) PRIMARY KEY,  -- MC01: 应用侧 UUID（旧 VARCHAR(64) 违 MC01）
    scenario_id         VARCHAR(36) NOT NULL,     -- 指向 public.ecos_scenario_definition.id（逻辑引用无 FK）
    run_type            VARCHAR(32) NOT NULL,     -- 值域待 PRD-08 §4.1 定版（X-33），本批不加 CHECK（头注已述）
    run_mode            VARCHAR(16) NOT NULL DEFAULT 'FORMAL',   -- R-25①: 正交列（FORMAL 正式 / SANDBOX 演练）
    status              VARCHAR(32) NOT NULL DEFAULT 'RUNNING',
    metric              VARCHAR(255),             -- 关注指标（仅名称，计算归 09 册事实面）
    deviation           NUMERIC(10,4),            -- E-5: DECIMAL→NUMERIC 拼写统一（精度显式）
    diagnosis_result_json  TEXT,                  -- DR04/MC02: JSONB→_json+TEXT（大载荷不参与 WHERE）
    forecast_result_json   TEXT,                  -- 同上；下钻明细属 09 册 ecos_fc_* 事实面，禁靠 JSON 内查询
    simulation_result_json TEXT,                  -- 同上
    strategy_result_json   TEXT,                  -- 同上
    decision_record_id  VARCHAR(36),              -- R-30②: 指向 public.ecos_scenario_decision_record.id（废弃旧 decision_id 空引用）
    is_degraded         SMALLINT NOT NULL DEFAULT 0,  -- DR05: 旧 degraded BOOLEAN 违例改名
    trace_id            VARCHAR(64),              -- W185: MDC 全链 traceId 接力
    create_time         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- DR06 审计五列
    update_time         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by           VARCHAR(64) NOT NULL DEFAULT 'system',
    update_by           VARCHAR(64) NOT NULL DEFAULT 'system',
    is_deleted          SMALLINT  NOT NULL DEFAULT 0,
    domain              VARCHAR(50) NOT NULL DEFAULT 'default',        -- DR08（旧表缺）
    version_no          VARCHAR(20) NOT NULL DEFAULT '1',              -- DR07（旧表缺）
    CONSTRAINT ck_scen_exec_run_mode CHECK (run_mode IN ('FORMAL','SANDBOX')),
    CONSTRAINT ck_scen_exec_status   CHECK (status IN ('RUNNING','SUCCEEDED','FAILED'))  -- X-74 补
);
COMMENT ON TABLE  public.ecos_scenario_execution IS '场景运行记录表（run_mode 正交列 R-25①；decision_record_id 属主 R-30②；旧 ecos_scenario_run 停写不删）';
COMMENT ON COLUMN public.ecos_scenario_execution.run_type IS '运行目的；三方值域并存（X-33/G2-5），权威值域待 PRD-08 §4.1 定版后以附加 CHECK 演进';
COMMENT ON COLUMN public.ecos_scenario_execution.run_mode IS 'FORMAL=正式 / SANDBOX=演练（F07-08 写白名单 DAO 唯一通道校验输入；与 run_type 语义正交）';
COMMENT ON COLUMN public.ecos_scenario_execution.decision_record_id IS '场景决策回执引用（权威表 ecos_scenario_decision_record；决策底座在 cognitive 经门面，禁第二套底座）';

-- ── 2. 索引（E-4：旧四件保持 + run_mode；MC03 无 partial）──
CREATE INDEX IF NOT EXISTS idx_scen_exec_scenario ON public.ecos_scenario_execution(scenario_id);
CREATE INDEX IF NOT EXISTS idx_scen_exec_status   ON public.ecos_scenario_execution(status);
CREATE INDEX IF NOT EXISTS idx_scen_exec_type     ON public.ecos_scenario_execution(run_type);
CREATE INDEX IF NOT EXISTS idx_scen_exec_time     ON public.ecos_scenario_execution(create_time DESC);  -- PG/MySQL8 皆可（E-4）
CREATE INDEX IF NOT EXISTS idx_scen_exec_mode     ON public.ecos_scenario_execution(run_mode, scenario_id);

-- ── 3. 存量登记（注释，不动表不迁数）──────────────────────
-- 旧表 public.ecos_scenario_run：停写不 DROP（IR03）；其 `decision_id` 空引用列保持原样不回填
-- （存量动作待授权）；旧行 → v2 的 UUID 重映射与 degraded→is_degraded 值翻译属迁移待授权项。

-- ── 回滚说明 ──────────────────────────────────────────────
-- 纯新建表零数据迁移；回滚 = 应用 Mapper 指回旧表名（E-7.2），新表保留为空表不 DROP（IR03）。

-- ── DDL Lint Self-audit ──────────────────────────────────
-- [DR01] public 限定 ✓  [DR02] ecos_ 前缀 ✓  [DR03] 单数 ✓
-- [DR04] 四个结果列全 _json + TEXT ✓  [DR05] is_degraded/is_deleted SMALLINT（degraded BOOLEAN 已废）✓
-- [DR06/07/08] 审计五列 + version_no + domain ✓
-- [MC01] VARCHAR(36) UUID ✓  [MC02] 零 JSONB（旧 4 JSONB 清零路径 新表面完成）✓
-- [MC03] 无 partial index/RLS/PARTITION/text[]/timestamptz/裸 cast ✓
-- [ST03-A] deviation NUMERIC(10,4) 精度显式（登记见汇报）✓  [ST07] 控制域落 public（MC06 注记）✓
-- [IR03] 旧表停写不删、零 ALTER ✓  [R-24②] 部署形态项，本脚本不含任何 gateway/代理内容 ✓
