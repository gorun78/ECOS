-- =============================================================================
-- V303 — R7 §2.9.4③ 治理审计事件字段组（#147M）
-- 单源目录：docs/50-db_script/02-flyway-migration/gateway/
--
-- 背景：§2.9.4③ 列出的治理审计事件契约（文档标称 22 项，枚举 21 个字段名；
--       差额 1 项按文档勘误登记待裁，不臆造字段）。本脚本为枚举中的字段补列：
--         * 与既有列同义的 1 项不做新列：timestamp → 既有 created_at
--         * 其余 20 项新增（全部可空，additive，不影响既有哈希链 6 字段输入）
-- 约束：不改既有列、不改哈希链算法（改输入会让历史链验签失效）；新列仅承载治理面证据。
-- =============================================================================

ALTER TABLE public.ecos_audit_log ADD COLUMN IF NOT EXISTS audit_event_id VARCHAR(64);
ALTER TABLE public.ecos_audit_log ADD COLUMN IF NOT EXISTS change_request_id VARCHAR(64);
ALTER TABLE public.ecos_audit_log ADD COLUMN IF NOT EXISTS actor_id VARCHAR(64);
ALTER TABLE public.ecos_audit_log ADD COLUMN IF NOT EXISTS approver_ids TEXT;
ALTER TABLE public.ecos_audit_log ADD COLUMN IF NOT EXISTS domain VARCHAR(64);
ALTER TABLE public.ecos_audit_log ADD COLUMN IF NOT EXISTS object_type VARCHAR(64);
ALTER TABLE public.ecos_audit_log ADD COLUMN IF NOT EXISTS object_id VARCHAR(64);
ALTER TABLE public.ecos_audit_log ADD COLUMN IF NOT EXISTS risk_level VARCHAR(16);
ALTER TABLE public.ecos_audit_log ADD COLUMN IF NOT EXISTS git_repository VARCHAR(64);
ALTER TABLE public.ecos_audit_log ADD COLUMN IF NOT EXISTS git_commit_sha VARCHAR(64);
ALTER TABLE public.ecos_audit_log ADD COLUMN IF NOT EXISTS git_parent_sha VARCHAR(64);
ALTER TABLE public.ecos_audit_log ADD COLUMN IF NOT EXISTS content_hash VARCHAR(128);
ALTER TABLE public.ecos_audit_log ADD COLUMN IF NOT EXISTS target_environment VARCHAR(32);
ALTER TABLE public.ecos_audit_log ADD COLUMN IF NOT EXISTS approval_policy_version VARCHAR(64);
ALTER TABLE public.ecos_audit_log ADD COLUMN IF NOT EXISTS deployment_id VARCHAR(64);
ALTER TABLE public.ecos_audit_log ADD COLUMN IF NOT EXISTS before_hash VARCHAR(128);
ALTER TABLE public.ecos_audit_log ADD COLUMN IF NOT EXISTS after_hash VARCHAR(128);
ALTER TABLE public.ecos_audit_log ADD COLUMN IF NOT EXISTS apply_status VARCHAR(32);
ALTER TABLE public.ecos_audit_log ADD COLUMN IF NOT EXISTS verification_status VARCHAR(32);
ALTER TABLE public.ecos_audit_log ADD COLUMN IF NOT EXISTS trace_id VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_audit_event_id ON public.ecos_audit_log (audit_event_id);
CREATE INDEX IF NOT EXISTS idx_audit_change_request ON public.ecos_audit_log (change_request_id);
CREATE INDEX IF NOT EXISTS idx_audit_git_commit ON public.ecos_audit_log (git_commit_sha);
CREATE INDEX IF NOT EXISTS idx_audit_trace ON public.ecos_audit_log (trace_id);

COMMENT ON COLUMN public.ecos_audit_log.audit_event_id IS
    'R7 §2.9.4③ 治理审计事件业务标识（与自增 id 分离，供跨系统对账）';
COMMENT ON COLUMN public.ecos_audit_log.risk_level IS
    '治理审计分级（L1~L4，映射见 common-api AuditLevel；不改既有 clearance_level）';
COMMENT ON COLUMN public.ecos_audit_log.git_commit_sha IS
    'Git 归档腿提交号（Commit SHA ≠ 发布授权，授权见 PublishManifest）';
COMMENT ON COLUMN public.ecos_audit_log.approval_policy_version IS
    '审批/发布策略版本（可回溯当时按哪版规则放行）';
