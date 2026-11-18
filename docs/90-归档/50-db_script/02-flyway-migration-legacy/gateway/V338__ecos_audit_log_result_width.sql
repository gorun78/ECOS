-- ============================================================
-- V338__ecos_audit_log_result_width.sql — 审计 result 列宽对齐发布端口径
-- 追溯: 产品级逐功能验证 D142（2026-11-16 活体取证）：冒充端点三支分支
--       （400/428/501）审计事件只有 1 支落库，另 2 支消费者报
--       `ERROR: value too long for type character varying(16)`。
-- 根因: `ecos_audit_log.result` 为 VARCHAR(16)，而 security-engine 两个发布端
--       写的定态码本就超长：
--         ImpersonateController   → REJECTED_BAD_REQUEST(20) / REJECTED_CONFIRM_REQUIRED(25)
--         CryptoKeyAdminController→ REJECTED_CONFIRM_REQUIRED(25) / REJECTED_MASTER_KEY_MISSING(27)
--       只有 NOT_IMPLEMENTED(15) / SUCCESS(7) 能装下 ⇒ 拒绝类审计证据整条丢失。
-- 修法: 仅加宽（16 → 64），语义不变、存量值全部不受影响（只变宽不改型类别）。
--       不截断、不改码值：result 是审计查询的过滤列（AuditController 按 SUCCESS/FAILED
--       判定），截断会让「拒绝原因」退化成不可区分的公共前缀，等于毁证据。
-- 纪律: DR01~DR08 / ST07 / IR02 / IR03(只加宽不删列)
-- 号段: 接 V337；本脚本落地后下一号 V339（须同批改 MigrationVersionUniquenessTest 钉值
--       与 Mc02/Mc03/Dr 三枚 SCRIPT_FLOOR）
-- ============================================================

-- 两张同名表都要覆盖：① public. 为 DDL 单源 V295 锚定的新库形态（从零建库写这张）；
-- ② ecos_control. 是 dev 库运行期真身（search_path 首位，消费者实写这张）。
-- 均用 to_regclass 守卫：任一侧未建出时跳过而非报错，单源重放与存量库都能安全执行。
DO $$
BEGIN
    IF to_regclass('public.ecos_audit_log') IS NOT NULL THEN
        ALTER TABLE public.ecos_audit_log ALTER COLUMN result TYPE VARCHAR(64);
    END IF;
    IF to_regclass('ecos_control.ecos_audit_log') IS NOT NULL THEN
        ALTER TABLE ecos_control.ecos_audit_log ALTER COLUMN result TYPE VARCHAR(64);
    END IF;
END $$;

-- ── 回滚说明 ──────────────────────────────────────────────
-- 回滚须先确认两侧 result 无长度 > 16 的值（拒绝类码 20~27 字，回滚会直接截断失败），再执行：
--   ALTER TABLE public.ecos_audit_log ALTER COLUMN result TYPE VARCHAR(16);
--   ALTER TABLE ecos_control.ecos_audit_log ALTER COLUMN result TYPE VARCHAR(16);
-- 幂等性：PG 对同一目标类型的 ALTER 可重复执行。

-- ── DDL Lint Self-audit ──────────────────────────────────────
-- [DR01] 两处均 schema 限定（public. / ecos_control.）✓ [DR02/03] 无新表 ✓ [DR04] 无 JSONB ✓
-- [DR05] 无布尔列 ✓ [DR06~08] 不涉及基线列增删 ✓ [MC01~03] 不涉及主键/金额 ✓
-- [ST07] public 为主控制 schema、ecos_control 为目标主控制 schema ✓
-- [IR02] 仅文件（dev 实跑见台账 D142）✓ [IR03] 只加宽不删列 ✓
