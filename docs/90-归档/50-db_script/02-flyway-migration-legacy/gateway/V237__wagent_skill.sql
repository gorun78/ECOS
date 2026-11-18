-- V237 (卷10 §6.2): 技能 ecos_ai.ecos_wagent_skill——DB 只在用版本，全文走 Git 归档（铁律 §3.1）
-- 追溯: W245~W277 ↔ C227~C259（卷 10 §七）；需求依据 REQ-WAG-16/17（技能面，§5.4）
-- 批准: 需求检视报告 §十四 14.1（2026-09-29）之 R-33=①、R-35=①、R-40=推荐项批准、R-41=推荐项批准（存量 `public.ecos_skill` 0 行待收编，本脚本只建新表不动旧表）；执行边界 §14.4 = 脚本文件落地
-- 状态: 仅落迁移脚本文件，**未对 ecos-postgres 实跑**（实跑属 §14.4 未授权项，需逐项再确认）
-- 纪律: DR01~DR08 / MC01~MC03 / ST06 / ST07 / ST09 / IR02 / IR03(只加不删)
-- Git 口径: Skill 全文不入 DB（`content_git_ref` 指向 runtime Git 归档通道；前端版本/Git UI 走 gitService 单通道只传 repositoryId——铁律前端 6）。

CREATE TABLE IF NOT EXISTS ecos_ai.ecos_wagent_skill (
    id                  VARCHAR(36) PRIMARY KEY,              -- MC01 应用侧 UUID
    tenant_id           VARCHAR(64) NOT NULL,
    org_id              VARCHAR(64),
    name                VARCHAR(255) NOT NULL,
    version             VARCHAR(20) NOT NULL,
    summary             VARCHAR(200),                         -- 文档明载 VARCHAR(200)
    content_git_ref     VARCHAR(120),                         -- *_git_ref→VARCHAR(120)
    content_version     VARCHAR(20),
    bound_playbooks_json TEXT,                                -- 绑定 Playbook 集（JSON 语义 TEXT）
    owner               VARCHAR(100),
    status              VARCHAR(12),
    domain              VARCHAR(50) NOT NULL DEFAULT 'default',
    version_no          VARCHAR(20) NOT NULL,
    create_time         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by           VARCHAR(100),
    update_by           VARCHAR(100),
    is_deleted          SMALLINT NOT NULL DEFAULT 0
);
COMMENT ON TABLE ecos_ai.ecos_wagent_skill IS 'W Agent 技能目录（附件 agt_skill 重写；DB 只存在用版本、历史走 Git；public.ecos_skill 收编口径见卷 10 §6.2，存量动作待 R-41）';

-- UNIQUE(name, version)（同 V236 口径：禁 partial ⇒ 含 is_deleted）
CREATE UNIQUE INDEX IF NOT EXISTS uniq_wagent_skill_name_ver
    ON ecos_ai.ecos_wagent_skill(name, version, is_deleted);
CREATE INDEX IF NOT EXISTS idx_wagent_skill_status ON ecos_ai.ecos_wagent_skill(status);

-- ── DDL Lint Self-audit ──────────────────────────────────
-- [DR01] ecos_ai. ✓ [DR02] ecos_ ✓ [DR05/06/07/08] 通用列齐 ✓ [MC01] UUID ✓ [MC02] JSON 全 TEXT
-- [唯一索引] 非 partial、含 is_deleted ✓  [ST07] ecos_ai ✓  [ST09] 无 FK ✓  [IR03] 旧表 ecos_skill 不动 ✓ [§14.4] 未实跑 ✓
