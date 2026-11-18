-- V228 (卷10 §6.2): 任务书 ecos_ai.ecos_wagent_mission——Goal 之下的委派任务与 Playbook 绑定
-- 追溯: W245~W277 ↔ C227~C259（卷 10 §七）；需求依据 REQ-WAG-12（F10-12 Plan DAG·Playbook 优先）
-- 批准: 需求检视报告 §十四 14.1（2026-09-29）之 R-33=①、R-35=①、R-40=推荐项批准（收编只定性）；执行边界 §14.4 = 脚本文件落地
-- 状态: 仅落迁移脚本文件，**未对 ecos-postgres 实跑**（实跑属 §14.4 未授权项，需逐项再确认）
-- 纪律: DR01~DR08 / MC01~MC03 / ST03-A / ST06 / ST07 / ST09 / IR02 / IR03(只加不删)
-- 命名口径: 文档未列类型的列按 §6.1 通用列 + ECOS 类型映射展开；JSON 语义一律 *_json TEXT（【2026-09-30 DR04 收口】卷 10 §6.1 原字面 `*_text` 违 DR04（JSON 语义列必加 `_json` 后缀），以红线为准；分册 §6.1 映射行同步勘误，逐列改名见 docs/40-实现/DDL迁移脚本落地登记-2026-09-30.md §五）

CREATE TABLE IF NOT EXISTS ecos_ai.ecos_wagent_mission (
    id                  VARCHAR(36) PRIMARY KEY,              -- MC01 应用侧 UUID
    tenant_id           VARCHAR(64) NOT NULL,
    org_id              VARCHAR(64),
    goal_id             VARCHAR(36) NOT NULL,                 -- 指向 ecos_wagent_goal.id（同 schema 逻辑引用，不建 FK）
    name                VARCHAR(255),
    stage               VARCHAR(24),
    owner_id            VARCHAR(100),
    due_date            DATE,
    playbook_id         VARCHAR(36),                          -- Playbook 资产引用（历史版本走 Git，铁律 §3.1）
    playbook_git_ref    VARCHAR(120),                         -- *_git_ref→VARCHAR(120)
    playbook_version    VARCHAR(20),
    is_temporary        SMALLINT NOT NULL DEFAULT 0,          -- DR05
    blocked_reason_text TEXT,
    domain              VARCHAR(50) NOT NULL DEFAULT 'default',
    version_no          VARCHAR(20) NOT NULL,
    create_time         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by           VARCHAR(100),
    update_by           VARCHAR(100),
    is_deleted          SMALLINT NOT NULL DEFAULT 0
);
COMMENT ON TABLE ecos_ai.ecos_wagent_mission IS 'W Agent 任务书（附件 agt_mission 重写；R-33① ecos_ai 五枚举内）';
CREATE INDEX IF NOT EXISTS idx_wagent_mission_goal    ON ecos_ai.ecos_wagent_mission(goal_id);
CREATE INDEX IF NOT EXISTS idx_wagent_mission_tenant  ON ecos_ai.ecos_wagent_mission(tenant_id, stage);

-- ── DDL Lint Self-audit ──────────────────────────────────
-- [DR01] ecos_ai. ✓ [DR02] ecos_ ✓ [DR05] is_* SMALLINT ✓ [DR06/07/08] 通用列齐 ✓
-- [MC01] UUID 无默认函数 ✓ [MC02] JSON 全 TEXT、*_json=TEXT（DR04 收口）✓ [ST07] ecos_ai ✓ [ST09] 无跨 schema FK ✓ [IR03] 只加 ✓
-- [§14.4] 仅脚本文件落地，未实跑 ✓
