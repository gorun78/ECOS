-- =============================================================================
-- V326 — CLI 生产化批 D：L3 审批凭证列组（ecos_ai.agent_approval 只加不删）
-- 单源目录：docs/50-db_script/02-flyway-migration/gateway/
--
-- 背景：L3（ontology publish / knowledge publish / action commit）今天只做「token 非空」判定，
--       凭证不校验、不存储、不绑人 ⇒ 链路无法闭环（WAgentOrchestrator 审批闸仅判空）。
--       本脚本为 ecos_ai.agent_approval（V50:89 建立，原 8 列）补齐凭证与四眼所需列。
--
-- 设计口径：
--   · 只存摘要不存明文（token_digest = SHA-256 hex），明文只在签发响应出现一次；
--   · 一次性消费靠 consumed_at 的 CAS（UPDATE ... WHERE consumed_at IS NULL）；
--   · 四眼 = approved_by（审批人）≠ requested_by（申请人/提交人），服务端强制；
--   · scope 绑定目标对象（CANDIDATE_PUBLISH + candidateId / ACTION_COMMIT + actionId），
--     防「一个 token 通用」；
--   · trace_id 与 V303 已在 ecos_audit_log 建立的同名列同义，供「凭证操作 ↔ 审计行」串联。
--
-- 约束：additive only（IR03 只加不删）；不改既有 8 列、不改既有索引；幂等可复跑。
-- 演练：BEGIN; \i V326...; ROLLBACK;（dev PG 由网关批次授权后实跑）
-- =============================================================================

ALTER TABLE ecos_ai.agent_approval ADD COLUMN IF NOT EXISTS token_digest   VARCHAR(128);
ALTER TABLE ecos_ai.agent_approval ADD COLUMN IF NOT EXISTS scope_type     VARCHAR(32);
ALTER TABLE ecos_ai.agent_approval ADD COLUMN IF NOT EXISTS scope_id       VARCHAR(64);
ALTER TABLE ecos_ai.agent_approval ADD COLUMN IF NOT EXISTS requested_by   VARCHAR(100);
ALTER TABLE ecos_ai.agent_approval ADD COLUMN IF NOT EXISTS expired_at     TIMESTAMP;
ALTER TABLE ecos_ai.agent_approval ADD COLUMN IF NOT EXISTS consumed_at    TIMESTAMP;
ALTER TABLE ecos_ai.agent_approval ADD COLUMN IF NOT EXISTS trace_id       VARCHAR(64);

-- 凭证查找高频面（摘要唯一：同一摘要不得签发两次）
CREATE UNIQUE INDEX IF NOT EXISTS uk_agent_approval_token_digest
    ON ecos_ai.agent_approval (token_digest);

-- 按目标对象列凭证（发布/提交前查「该对象是否已批」）
CREATE INDEX IF NOT EXISTS idx_agent_approval_scope
    ON ecos_ai.agent_approval (scope_type, scope_id);

-- 过期清扫与链路核对
CREATE INDEX IF NOT EXISTS idx_agent_approval_expired
    ON ecos_ai.agent_approval (expired_at);
CREATE INDEX IF NOT EXISTS idx_agent_approval_trace
    ON ecos_ai.agent_approval (trace_id);

COMMENT ON COLUMN ecos_ai.agent_approval.token_digest IS
    '审批凭证 SHA-256 摘要（禁存明文；明文仅签发响应出现一次）';
COMMENT ON COLUMN ecos_ai.agent_approval.scope_type IS
    '凭证作用域类型：CANDIDATE_PUBLISH / ACTION_COMMIT / RUN_APPROVAL（与 WAgentApprovalScope 单源）';
COMMENT ON COLUMN ecos_ai.agent_approval.scope_id IS
    '作用域对象 ID（candidateId / actionId）；凭证不得跨对象复用';
COMMENT ON COLUMN ecos_ai.agent_approval.requested_by IS
    '申请人（提交该 L3 动作的操作者）；四眼要求 approved_by ≠ requested_by';
COMMENT ON COLUMN ecos_ai.agent_approval.expired_at IS
    '凭证过期时刻；过期即拒绝（E-POLICY），不自动续期';
COMMENT ON COLUMN ecos_ai.agent_approval.consumed_at IS
    '一次性消费时刻；消费后不可再用（CAS 置位，重放必拒）';
COMMENT ON COLUMN ecos_ai.agent_approval.trace_id IS
    '链路标识（与 ecos_audit_log.trace_id 同义，供凭证操作与审计行串联核对）';
