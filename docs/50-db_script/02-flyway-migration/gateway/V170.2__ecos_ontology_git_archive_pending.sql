-- V170.2 (卷03 §E.3 / F03-15): 本体版本 × Git 归档补偿队列表
-- 追溯: W83/C66（O-21：ecos_ontology_version 归档 best-effort，无补偿 → 归档可能永久缺）
-- 关联: V170.1 已给 public.ecos_ontology_version 与 ecos_ontology.ecos_ontology_version 补 git_ref 列
-- 批准: 需求检视报告 §十四 14.1（2026-09-29）之 R-5=①+③；R-5③ = Function/版本归档"归主控制域"
-- 号段: V170 整 = 卷02；V170.1 = 卷03 快照列合规化；V170.2 = 本批补偿队（同主版本子序列）
-- 状态: 仅落迁移脚本文件，**未对 ecos-postgres 实跑**（实跑属 §14.4 未授权项，需逐项再确认）
-- 纪律: DR01~DR08 / MC01~MC03 / ST07 / IR02(手动 psql) / IR03(只加不删)
-- 定位: 控制域 → 落 public.ecos_ontology_git_archive_pending，与 V244 ecos_runtime_audit_retry 同族
--       由 runtime/runtime-task 统一消费（禁止引擎自建 @Scheduled，铁律 §2.5-4）
-- 语义（F03-15）:
--   发布成功但 Git 归档未 COMMITTED → 本表 pending 入队 → runtime-task 指数退避重试
--   → 3 次仍失败 exhausted + critical 告警（不回滚发布，发布与归档分离）
--
-- C66 回写依据: 后端规范 §十一「DB 只存在用版本，历史版本走 Git」；历史内容不再共存 DB 与 Git

-- ── 1. 主表（public 侧；ecos_ontology 镜像侧本身缺表则为 V47 幽灵表，不重复建镜像）──
CREATE TABLE IF NOT EXISTS public.ecos_ontology_git_archive_pending (
    id                  VARCHAR(36)   PRIMARY KEY,          -- MC01 应用侧 UUID
    ontology_id         VARCHAR(64)   NOT NULL,             -- 关联 ecos_ontology_version.ontology_id
    version_id          VARCHAR(64)   NOT NULL,             -- 关联 ecos_ontology_version.id
    target_version_no   VARCHAR(20)   NOT NULL,             -- 归档目标的本体版本号（如 1.0.2）
    repository_id       VARCHAR(64)   NOT NULL DEFAULT 'ontology',
    commit_message      VARCHAR(512),                -- 归档时使用的 commitMessage
    author_name         VARCHAR(100),                -- 归档归属（publisher 或 system）
    payload             TEXT,                         -- 待写 Git 的 snapshot 原始文本（MC02 TEXT）
    attempts            SMALLINT      NOT NULL DEFAULT 0,
    next_retry_at       TIMESTAMP     NOT NULL,
    last_error          VARCHAR(1000),
    status              VARCHAR(16)   NOT NULL DEFAULT 'pending', -- pending / closed / exhausted
    last_commit_id      VARCHAR(64),                   -- 成功 commit 时回填
    trace_id            VARCHAR(64),
    create_time         TIMESTAMP     NOT NULL DEFAULT NOW(),  -- DR06
    update_time         TIMESTAMP     NOT NULL DEFAULT NOW(),
    create_by           VARCHAR(100),
    update_by           VARCHAR(100),
    is_deleted          SMALLINT      NOT NULL DEFAULT 0,    -- DR05
    version_no          VARCHAR(20)   NOT NULL DEFAULT '1.0.0', -- DR07 行版本
    domain              VARCHAR(50)   NOT NULL DEFAULT 'default' -- DR08
);
COMMENT ON TABLE public.ecos_ontology_git_archive_pending IS
    '本体版本 Git 归档补偿队列（W83/C66：F03-15 归档非 COMMITTED 时入队，runtime-task 指数退避重放；3 次失败 exhausted + 告警，不回滚发布）';
COMMENT ON COLUMN public.ecos_ontology_git_archive_pending.target_version_no IS
    '归档目标的本体版本号（source of truth 在 ecos_ontology_version.version_no；本列冻结归档入队时的快照，防归档期间版本漂移）';
COMMENT ON COLUMN public.ecos_ontology_git_archive_pending.payload IS
    '归档应写入 Git 的 snapshot 原始文本（MC02 受控 JSON 只准 TEXT；本列为"待写 Git 的内容"，非 DB 历史存档）';

CREATE INDEX IF NOT EXISTS idx_ontology_git_archive_due
    ON public.ecos_ontology_git_archive_pending (status, next_retry_at);
CREATE INDEX IF NOT EXISTS idx_ontology_git_archive_onto
    ON public.ecos_ontology_git_archive_pending (ontology_id, version_id, is_deleted);

-- ── DDL Lint Self-audit ──────────────────────────────────
-- [DR01] schema 限定 ✓  [DR02] 新表带 ecos_ 前缀 ✓  [DR03] 单数无 _v2 后缀 ✓
-- [DR04] 未新增 JSON 语义列（payload 与 V244 ecos_runtime_audit_retry.payload 同族，均为"待投放的原始文本"字面名） ✓
-- [DR05~DR08] is_deleted / create_time / update_time / create_by / update_by / version_no / domain 齐 ✓
-- [MC01] PK VARCHAR(36) 应用侧生成，无 SERIAL/BIGSERIAL/gen_random_uuid() ✓
-- [MC02] 无 JSON 类型列 ✓  [MC03] 无 partial index / :: cast / CREATE POLICY / PARTITION BY ✓
-- [ST07] 落 public（控制域主控制 schema 现名，与 V244 ecos_runtime_audit_retry 同族） ✓
-- [IR02] 手动 psql，未实跑 ✓  [IR03] 无 DROP / 无 RENAME / 无 ALTER TYPE ✓
