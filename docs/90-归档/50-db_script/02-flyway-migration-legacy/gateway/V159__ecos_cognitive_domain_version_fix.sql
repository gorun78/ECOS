-- ============================================================
-- V159__ecos_cognitive_domain_version_fix.sql
-- 认知表族 domain / version_no 缺口补齐
-- 策略: R9 只加不删 + 幂等（ADD COLUMN IF NOT EXISTS），无 DROP / ALTER COLUMN（IR03）
-- 缺口:
--   1) ecos_cognitive_model      (V124) — 缺 domain (DR08) + version_no (DR07)
--   2) ecos_cognitive_evidence   (V127) — 缺 version_no (DR07)
--   3) ecos_cognitive_hypothesis (V128) — 缺 version_no (DR07)
--   4) ecos_cognitive_belief     (V129) — 缺 version_no (DR07)
-- 说明: V127 evidence 表已有 domain VARCHAR(64)，仅补 version_no；
--       不动已有列（R9: 只加不删，不 ALTER 既有列）
-- 幂等: 所有 DDL 使用 ADD COLUMN IF NOT EXISTS
-- 上线: psql -U postgres -d sys_man -f V159__ecos_cognitive_domain_version_fix.sql
-- ============================================================

-- ============================================================
-- T1. ecos_cognitive_model — 补齐 domain (DR08) + version_no (DR07)
-- ============================================================
ALTER TABLE ecos_cognitive_model
    ADD COLUMN IF NOT EXISTS domain VARCHAR(50) NOT NULL DEFAULT 'default';
ALTER TABLE ecos_cognitive_model
    ADD COLUMN IF NOT EXISTS version_no VARCHAR(20) NOT NULL DEFAULT '1';

COMMENT ON COLUMN ecos_cognitive_model.domain     IS '多租户预留域 (DR08 规范)';
COMMENT ON COLUMN ecos_cognitive_model.version_no IS '乐观锁版本号 (DR07 规范)';

-- ============================================================
-- T2. ecos_cognitive_evidence — 仅补 version_no（DR07），已有 domain VARCHAR(64) 不动
-- ============================================================
ALTER TABLE ecos_cognitive_evidence
    ADD COLUMN IF NOT EXISTS version_no VARCHAR(20) NOT NULL DEFAULT '1';

COMMENT ON COLUMN ecos_cognitive_evidence.version_no IS '乐观锁版本号 (DR07 规范)';

-- ============================================================
-- T3. ecos_cognitive_hypothesis — 仅补 version_no（DR07），已有 domain VARCHAR(64) 不动
-- ============================================================
ALTER TABLE ecos_cognitive_hypothesis
    ADD COLUMN IF NOT EXISTS version_no VARCHAR(20) NOT NULL DEFAULT '1';

COMMENT ON COLUMN ecos_cognitive_hypothesis.version_no IS '乐观锁版本号 (DR07 规范)';

-- ============================================================
-- T4. ecos_cognitive_belief — 仅补 version_no（DR07），已有 domain VARCHAR(64) 不动
-- ============================================================
ALTER TABLE ecos_cognitive_belief
    ADD COLUMN IF NOT EXISTS version_no VARCHAR(20) NOT NULL DEFAULT '1';

COMMENT ON COLUMN ecos_cognitive_belief.version_no IS '乐观锁版本号 (DR07 规范)';
