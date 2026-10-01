-- V233 (卷10 §6.2): 运行实例 ecos_ai.ecos_wagent_run——Run 状态机/预算/幂等（收编 agent_execution 语义上位）
-- 追溯: W245~W277 ↔ C227~C259（卷 10 §七）；需求依据 REQ-WAG-11（F10-11 Run 状态机与预算）
-- 批准: 需求检视报告 §十四 14.1（2026-09-29）之 R-33=①、R-35=①、R-39=推荐项批准（Agent Runtime 唯一性）；执行边界 §14.4 = 脚本文件落地
-- 状态: 仅落迁移脚本文件，**未对 ecos-postgres 实跑**（实跑属 §14.4 未授权项，需逐项再确认）
-- 纪律: DR01~DR08 / MC01~MC03 / ST06 / ST07 / ST09 / IR02 / IR03(只加不删)
-- 收编定性: `ecos_ai.agent_execution`（存量）语义上位至本表，停写口径见卷 10 §6.2"收编"段（存量动作待 R-34/R-40/R-41，本脚本不迁不动旧表）
-- 命名口径: 未列类型者按 §6.1 通用列 + ECOS 类型映射展开；JSON 语义一律 *_json TEXT（2026-09-30 DR04 收口，卷 10 §6.1 同步勘误）

CREATE TABLE IF NOT EXISTS ecos_ai.ecos_wagent_run (
    id                  VARCHAR(36) PRIMARY KEY,              -- MC01 应用侧 UUID
    tenant_id           VARCHAR(64) NOT NULL,
    org_id              VARCHAR(64),
    question_id         VARCHAR(36) NOT NULL,                 -- V229 逻辑引用
    mission_id          VARCHAR(36),                          -- V228 逻辑引用
    parent_run_id       VARCHAR(36),                          -- 父子运行（同表逻辑引用）
    previous_run_id     VARCHAR(36),                          -- 续跑前驱
    plan_source         VARCHAR(12),                          -- PLAYBOOK/EXPLORED/REUSED 等（F10-12）
    plan_json           TEXT,                                 -- Plan DAG（JSON 语义 TEXT，MC02）
    status              VARCHAR(24),
    automation_ceiling  SMALLINT,                             -- 自动化上限（F10-13 探索规划强制人工确认）
    initiated_by        VARCHAR(100),                         -- *_by→VARCHAR(100)
    idempotency_key     VARCHAR(128),                         -- 幂等键（E-IDEMPOTENT-CONFLICT 409 语义，§5.4 错误码表）
    budget_json         TEXT,                                 -- 预算声明（JSON 语义 TEXT）
    consumption_json    TEXT,                                 -- 消耗记账（JSON 语义 TEXT）
    readiness_id        VARCHAR(36),                          -- V230 逻辑引用
    degradations_json   TEXT,                                 -- 生效降级（F10-10）
    situation_snapshot_json TEXT,                             -- 【2026-09-30 G2 补列】Situation 11 项受控快照（卷 10 §5.2 契约唯一落点）；JSON 语义 → `_json` + TEXT（DR04/MC02），禁在 ecos_cognitive_* 建表
    result_ref_text     TEXT,                                 -- 结果引用（引用型，不存业务明细副本，§6.3）
    start_time          TIMESTAMP,
    end_time            TIMESTAMP,
    domain              VARCHAR(50) NOT NULL DEFAULT 'default',
    version_no          VARCHAR(20) NOT NULL,
    create_time         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by           VARCHAR(100),
    update_by           VARCHAR(100),
    is_deleted          SMALLINT NOT NULL DEFAULT 0
);
COMMENT ON TABLE ecos_ai.ecos_wagent_run IS 'W Agent 运行实例（附件 agt_run 重写；agent_execution 语义上位；预算/幂等承载 REQ-WAG-11）';

-- 唯一索引（红线：禁 partial ⇒ 含 is_deleted；幂等键行不物理清除，重跑换新键由应用层保证）
CREATE UNIQUE INDEX IF NOT EXISTS uniq_wagent_run_idem
    ON ecos_ai.ecos_wagent_run(tenant_id, initiated_by, idempotency_key, is_deleted);
CREATE INDEX IF NOT EXISTS idx_wagent_run_question ON ecos_ai.ecos_wagent_run(question_id, status);

-- ── DDL Lint Self-audit ──────────────────────────────────
-- [DR01] ecos_ai. ✓ [DR02] ecos_ ✓ [DR05/06/07/08] 通用列齐 ✓ [MC01] UUID ✓ [MC02] JSON 全 TEXT
-- [唯一索引] 非 partial、含 is_deleted ✓（替代原 WHERE 条件）
-- [ST07] ecos_ai ✓ [ST09] 无跨 schema FK ✓ [IR03] 只加、旧表不动 ✓ [§14.4] 未实跑 ✓
-- [DR04] plan_json/budget_json/consumption_json/degradations_json/situation_snapshot_json 均带 `_json` 后缀 ✓；
--   result_ref_text = 引用型纯串（非 JSON 语义），保持 `_text`（与 §6.3 引用原则一致）
-- 【2026-09-30 G2 补列依据（只读实测）】situation_snapshot_json 原缺：卷 10 §5.2 明载"控制面引用落
--   `ecos_wagent_run` 的 situation_snapshot TEXT"，但本表列清单与 §6.2 行均未承载 ⇒ 文档↔脚本断裂。
--   本机库 sys_man 内 `information_schema.columns LIKE '%situation%'` 实测 5 条命中全部落 ecos_demo 业务列
--   （td_approval_project_ledger / td_risk_ledger，与本域无关）；`ecos_wagent_run` 表体本身不存在（本批未实跑）；
--   全仓 *.java/*.ts/*.tsx Grep `situation_snapshot` = 0 命中 ⇒ 零行 + 零代码引用 ⇒ 建表期直接补合规列名，无兼容代价。
