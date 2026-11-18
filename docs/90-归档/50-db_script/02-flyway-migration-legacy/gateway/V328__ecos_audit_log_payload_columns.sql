-- ============================================================
-- V328 — ecos_audit_log 补 changes / category 两列（只加不删）
-- ============================================================
-- 缺陷（2026-11-11 活体实测，CLI 生产化批 C 验收阻塞项）：
--   public.ecos_audit_log 由 V295 锚定表体，仅含
--   id/event_type/user_id/username/operation/action/resource/entity_type/
--   entity_id/detail/ip_address/result/created_at/prev_hash/curr_hash/hash_algorithm。
--   而三处代码写入路径均按「审计事件契约」使用 changes(jsonb) 与 category：
--     · sysman/impl/.../sysman/dao/AuditLogDao.java:55   ← AuditAspect full 模式哈希链双写
--     · sysman-boot/.../boot/dao/AuthDao.java:95          ← 工作站不匹配审计
--     · ai-engine-impl/.../ai/dao/ToolDao.java:29         ← 沙盒拦截审计
--   V303 只补了 audit_event_id/change_request_id/actor_id/approver_ids/domain/
--   object_type/object_id/risk_level/... 与 trace_id，**从未补 changes / category**。
--   ⇒ 任何「从零建库」的实例上，上述三条写入路径恒失败：
--      AuditAspect 侧表现为 WARN「R1.2 full 模式哈希链双写失败: bad SQL grammar …」，
--      ecos_audit_log 恒 0 行 ⇒ §三.2「CLI trace_id == 审计表 trace_id」无从对账。
--
-- 依据：IR03 只加不删（ADD COLUMN IF NOT EXISTS，幂等）；DR04 不做列级回改。
-- 口径：`changes` 为审计事件载荷（代码按 jsonb 绑定 → CAST(? AS jsonb) 需要 jsonb 类型）；
--       `category` 为审计分类（调用方字面量 'audit' 等，长度参照 domain 取 64）。
-- ============================================================

ALTER TABLE public.ecos_audit_log ADD COLUMN IF NOT EXISTS changes JSONB;
ALTER TABLE public.ecos_audit_log ADD COLUMN IF NOT EXISTS category VARCHAR(64);

COMMENT ON COLUMN public.ecos_audit_log.changes IS
    '审计事件载荷（jsonb）。AuditAspect/AuthDao/ToolDao 三条写入路径共用；V328 补列（V295 锚表遗漏）';
COMMENT ON COLUMN public.ecos_audit_log.category IS
    '审计分类（如 audit / security）。V328 补列（V295 锚表遗漏）';
