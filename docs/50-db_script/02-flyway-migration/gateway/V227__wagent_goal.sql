-- V227 (卷10 §6.2): 业务目标 ecos_ai.ecos_wagent_goal——W Agent 编排对象链根对象
-- 追溯: W245~W277 ↔ C227~C259（卷 10 §七）；需求依据 REQ-WAG-05/06（F10-05 对象链与意图识别 / F10-06 槽位来源）
-- 批准: 需求检视报告 §十四 14.1（2026-09-29）之 R-33=①（三前缀收编 ST07 五枚举，落 ecos_ai，表名 ecos_wagent_*）、R-35=①（附件形态按 ECOS 模板重写）；执行边界 §14.4 = 脚本文件落地
-- 状态: 仅落迁移脚本文件，**未对 ecos-postgres 实跑**（实跑属 §14.4 未授权项，需逐项再确认）
-- 纪律: DR01~DR08 / MC01~MC03 / ST03-A / ST04/ST05(不写 RLS) / ST06(不建审计表) / ST07 / ST09 / IR02(手动 psql) / IR03(只加不删)
-- 命名口径: 文档未列类型的列按 §6.1 通用列 + ECOS 类型映射展开（name→VARCHAR(255)、*_json→TEXT（JSON 语义）、纯文本列 *_text→TEXT、*_id→VARCHAR(36)、*_version→VARCHAR(20)、owner→VARCHAR(100)、计数→INTEGER）
-- JSON 命名口径: 【2026-09-30 DR04 收口】卷 10 §6.1 原字面 `*_text` 违 DR04（JSON 语义列必加 `_json` 后缀），以红线为准；分册 §6.1 映射行同步勘误，逐列改名见 docs/40-实现/DDL迁移脚本落地登记-2026-09-30.md §五。本表 JSON 语义列改名 = scope_json / slots_source_json；
--   纯文本与引用指针列（time_range_text / success_criteria_text / raw/summary 型）保留 *_text 形态，判定口径与残留清单见登记 §9.5 DR04 行

CREATE TABLE IF NOT EXISTS ecos_ai.ecos_wagent_goal (
    id                  VARCHAR(36) PRIMARY KEY,              -- MC01: 应用侧 UUID，DDL 无默认值（R-35①：无 text PK/ULID）
    tenant_id           VARCHAR(64) NOT NULL,                 -- §6.1 通用列（三列并带承 REQ-PLT-24/25，R-36 挂账）
    org_id              VARCHAR(64),
    name                VARCHAR(255),
    goal_type           VARCHAR(20),
    scope_json          TEXT,                                 -- 范围描述（JSON 语义一律 TEXT，MC02）
    time_range_text     TEXT,
    as_of_date          DATE,
    metric_id           VARCHAR(36),                          -- 指标引用（卷 03 本体域；ST09 不建跨 schema FK）
    metric_version      VARCHAR(20),
    caliber_version     VARCHAR(20),                          -- 口径版本引用（主权在 ecos_ontology，只读）
    success_criteria_text TEXT,
    owner_id            VARCHAR(100),
    due_date            DATE,
    status              VARCHAR(20),
    slots_source_json   TEXT,                                 -- 槽位来源（F10-06）
    inferred_count      INTEGER,                              -- 推断槽位数
    supersedes_goal_id  VARCHAR(36),                          -- 前代 Goal（逻辑引用同表）
    domain              VARCHAR(50) NOT NULL DEFAULT 'default', -- DR08
    version_no          VARCHAR(20) NOT NULL,                 -- DR07
    create_time         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by           VARCHAR(100),
    update_by           VARCHAR(100),
    is_deleted          SMALLINT NOT NULL DEFAULT 0           -- DR05
);
COMMENT ON TABLE ecos_ai.ecos_wagent_goal IS 'W Agent 业务目标（附件 agt_business_goal 按 ECOS 模板重写；R-33① 落 ecos_ai，不建 ecos_agent schema / agt_ 前缀）';
CREATE INDEX IF NOT EXISTS idx_wagent_goal_tenant  ON ecos_ai.ecos_wagent_goal(tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_wagent_goal_owner   ON ecos_ai.ecos_wagent_goal(owner_id);

-- ── DDL Lint Self-audit ──────────────────────────────────
-- [DR01] ecos_ai. 限定 ✓  [DR02] ecos_ 前缀 ✓  [DR05] is_deleted SMALLINT ✓  [DR06/07/08] 通用列齐 ✓
-- [MC01] VARCHAR(36) UUID 无默认函数 ✓  [MC02] JSON 语义一律 *_json TEXT（DR04 收口）；无数组列；时间列无时区形态 ✓
-- [ST04/05] 不写行级安全策略 ✓  [ST06] 无审计表 ✓  [ST07] 五枚举 ecos_ai ✓  [ST09] 无 FK ✓  [X-6] 不分区 ✓  [IR03] 只加不删 ✓
