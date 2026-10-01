-- V211 (卷07 §六 E 章 E-2 目标形态): 场景决策回执表 合规重建 + 溯源明细表（W174/W175）
-- 追溯: W174/C156、W175/C157；需求依据 REQ-FC-04
-- 批准: 需求检视报告 §十四 14.1（2026-09-29）之 **R-30=①+② 受控组合**（§14.1 L524 已查认：
--   "Decision 底座唯一在 cognitive，workspace 经 DecisionGateway 适配；FC-04 五必填以列扩展承载，
--   禁第二套底座"）；执行边界 §14.4 = 脚本文件落地
-- 状态: 仅落迁移脚本文件，**未对 ecos-postgres 实跑**（实跑属 §14.4 未授权项，需逐项再确认）
-- 纪律: DR01~DR08 / MC01~MC03 / ST03-A / ST07 / ST09 / IR02(手动 psql) / IR03(只加不删)
-- 【2026-09-30 校订】DR03 收口：本表原按分册 07 L1073「新表统一 `_v2` 后缀」定名 ecos_decision_record_v2，
--   而 DR03 反例即 `ecos_workflow_task_v2`（版本后缀禁入表名），故改名 ecos_scenario_decision_record。旧同名停写表保留原名的策略不变（IR03 不删旧表）。
--   安全性实证：本表零行（本批不实跑）、全仓 Java/XML/TS 对该名 0 引用；库内旧同名停写表名未被占用。
--   文件名保留 V*__*__ecos_decision_record_v2（psql -f 引用不变），文件名↔表名映射登记于落地清单。
-- 命名策略（分册 07 L1073）: 新表命名合规化（原 `_v2` 后缀违 DR03，已收口，见下）；旧 public.ecos_decision_record（V151，mind_id BIGINT、
--   3 JSONB、五必填仅 2 项成列 X-78、Java 0 读 0 写 X-37）停写不删除；对账走 V213。
-- 落点与属主（R-30 口径逐条）:
--   * 本表属**控制域**（ST07 主控制 schema，现 public / 目标 ecos_control，MC06 配置注记）；
--   * R-30 ①: 决策**底座/能力**唯一在 cognitive（DecisionService，经 api 门面，workspace 侧经
--     DecisionGateway 适配），本表**不是第二套决策底座**，只是场景侧"决策回执/执行记录"台账；
--   * R-30 ②: 权威记录表 = ecos_scenario_decision_record（本表），V209.decision_record_id 指向本表；
--     cognitive 侧 ecos_decision（V103）的合规改造属分册 05 W/C 项，两表各司其职（须在 ARCH_SPEC §4.3 写清，
--     防被当重复表误删）；
--   * R-30 ①+② 组合项"FC-04 五必填以列扩展承载" ⇒ 五必填成实列（头注下），禁另建扩展表（③ 违例）。
-- E-3 定版: action_plan/source_refs/compliance_check 3 JSONB → `_json TEXT`；五必填拆实列后
--   action_plan_json 仅存剩余叙述；hash 溯源需按 ref 查 → 明细表 ecos_decision_source_ref；
--   合规结论需按 verdict 过滤 → compliance_verdict 实列 + compliance_detail_json。
-- 上线: psql -U postgres -d sys_man -f V211__ecos_decision_record_v2.sql

-- ── 1. 决策回执表（FC-04 五必填成实列）──────────────────
CREATE TABLE IF NOT EXISTS public.ecos_scenario_decision_record (
    id               VARCHAR(36) PRIMARY KEY,     -- MC01: 应用侧 UUID（旧 VARCHAR(64) 违 MC01）
    scenario_id      VARCHAR(36) NOT NULL,        -- 指向 public.ecos_scenario_definition.id（逻辑引用无 FK）
    mind_id          VARCHAR(36),                 -- 指向 public.ecos_scenario_mind_variant.id（旧 BIGINT 分叉修正）
    forecast_run_id  VARCHAR(36),                 -- REQ-FC-04 回链：指向 public.ecos_scenario_execution.id（09 册事实面接缝）
    run_mode         VARCHAR(16),                 -- 决策发生时 run_mode 快照（FORMAL/SANDBOX 语义随 V209，仅快照不加 CHECK）
    owner_user_id    VARCHAR(36) NOT NULL,        -- 五必填①：责任人（R-30 列扩展承载）
    due_date         DATE,                        -- 五必填②：截止日（X-78 旧表无列，v2 成列）
    approver_user_id VARCHAR(36) NOT NULL,        -- 五必填③：审批人
    expected_impact  NUMERIC(18,2),               -- 五必填④：预期影响（ST03-A 金额形态 NUMERIC(18,2)）
    control_metric   VARCHAR(128) NOT NULL,       -- 五必填⑤：控制指标
    action_plan_json TEXT,                         -- 处置方案剩余叙述（五必填已拆实列）DR04/MC02
    source_refs_json TEXT,                        -- hash 溯源快照位；权威明细在 ecos_decision_source_ref
    compliance_verdict        VARCHAR(16),        -- E-3: 合规结论实列（可按 verdict 过滤）
    compliance_detail_json    TEXT,               -- E-3: 合规明细快照 DR04/MC02；裁决权威在 security-engine，本列仅回执缓存
    proposed_by      VARCHAR(64),                 -- 旧列语义保持（用户 ID，非 PII，E-5）
    accepted_by      VARCHAR(64),
    accepted_at      TIMESTAMP,
    create_time      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- DR06 审计五列
    update_time      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by        VARCHAR(64) NOT NULL DEFAULT 'system',
    update_by        VARCHAR(64) NOT NULL DEFAULT 'system',
    is_deleted       SMALLINT  NOT NULL DEFAULT 0,
    domain           VARCHAR(50) NOT NULL DEFAULT 'default',        -- DR08（旧表缺）
    version_no       VARCHAR(20) NOT NULL DEFAULT '1',              -- DR07（旧表缺）
    CONSTRAINT ck_scen_dec_run_mode CHECK (run_mode IS NULL OR run_mode IN ('FORMAL','SANDBOX'))
);
COMMENT ON TABLE  public.ecos_scenario_decision_record IS '场景决策回执表（R-30①+②：场景侧记录台账，非第二套决策底座；底座唯一在 cognitive 经 DecisionGateway；FC-04 五必填实列）';

-- ── 2. 溯源明细表（E-3：hash 溯源需按 ref 查，替代 JSON 内查询）──
CREATE TABLE IF NOT EXISTS public.ecos_decision_source_ref (
    id          VARCHAR(36) PRIMARY KEY,          -- MC01
    decision_id VARCHAR(36) NOT NULL,             -- 指向 public.ecos_scenario_decision_record.id
    ep          VARCHAR(120) NOT NULL,            -- 来源端点（*_ref 语义列宽 120）
    hash        VARCHAR(64) NOT NULL,             -- 推理输出 hash（*_hash → VARCHAR(64)）
    mind_id     VARCHAR(36),                      -- 产生该输出时激活的心智
    ts          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 溯源时刻
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- DR06
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by   VARCHAR(64) NOT NULL DEFAULT 'system',
    update_by   VARCHAR(64) NOT NULL DEFAULT 'system',
    is_deleted  SMALLINT  NOT NULL DEFAULT 0,
    domain      VARCHAR(50) NOT NULL DEFAULT 'default',
    version_no  VARCHAR(20) NOT NULL DEFAULT '1'
);
COMMENT ON TABLE public.ecos_decision_source_ref IS '决策溯源明细表（E-3 定版 (decision_id, ep, hash, mind_id, ts)；1:N 替代 source_refs JSONB）';
CREATE INDEX IF NOT EXISTS idx_edsr_decision ON public.ecos_decision_source_ref(decision_id);
CREATE INDEX IF NOT EXISTS idx_edsr_hash     ON public.ecos_decision_source_ref(hash);

-- ── 3. 索引（主表；MC03 无 partial）────────────────────────
CREATE INDEX IF NOT EXISTS idx_scen_dec_scenario ON public.ecos_scenario_decision_record(scenario_id, is_deleted);
CREATE INDEX IF NOT EXISTS idx_scen_dec_mind     ON public.ecos_scenario_decision_record(mind_id);
CREATE INDEX IF NOT EXISTS idx_scen_dec_run      ON public.ecos_scenario_decision_record(forecast_run_id);

-- ── 4. 存量登记（注释，不动表不迁数）──────────────────────
-- 旧表 public.ecos_decision_record：停写不 DROP（IR03）；旧行 action_plan JSONB 内嵌"截止日/预期影响/
-- 控制指标"三值的抽取回填属存量迁移待授权项（§14.4），且旧值可能缺失（五必填当年仅 2 项成列），
-- 不做猜测性解析；V151 遗留数据经 V213 v_legacy_ecos_decision_record 只读取证。

-- ── 回滚说明 ──────────────────────────────────────────────
-- 纯新建表零数据迁移；回滚 = 应用 Mapper 指回旧表名（E-7.2），两张新表保留为空表不 DROP（IR03）。

-- ── DDL Lint Self-audit ──────────────────────────────────
-- [DR01] public 限定 ✓  [DR02] ecos_ 前缀 ✓  [DR03] 单数 ✓
-- [DR04] action_plan_json/source_refs_json/compliance_detail_json 全 _json+TEXT ✓
-- [DR05] is_deleted SMALLINT ✓  [DR06/07/08] 两表齐备 ✓
-- [MC01] 两表 VARCHAR(36) UUID ✓  [MC02] 零 JSONB（旧 3 JSONB 清零路径 新表面完成）✓
-- [MC03] 无 partial index/RLS/PARTITION/text[]/timestamptz/裸 cast ✓
-- [ST03-A] expected_impact NUMERIC(18,2)（登记见汇报）✓  [ST07] 控制域落 public（R-30 属主注记）✓
-- [R-30] 未建第二套底座/扩展表（③ 违例项未采纳）；compliance 列为回执缓存非裁决权威 ✓  [IR03] 旧表停写不删 ✓
