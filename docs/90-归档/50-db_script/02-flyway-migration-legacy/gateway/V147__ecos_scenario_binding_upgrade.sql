-- ============================================================
-- V147__ecos_scenario_binding_upgrade.sql — binding 增加真 ID（PMO-60 v2.0）
-- 场景绑定从 target_ref 字符串别名升级为真 PG 主键引用；
-- target_ref 保留只读兼容，不改不删既有列。
-- ============================================================

ALTER TABLE ecos_scenario_binding
    ADD COLUMN IF NOT EXISTS target_id   VARCHAR(64),
    ADD COLUMN IF NOT EXISTS target_type VARCHAR(32);

CREATE INDEX IF NOT EXISTS idx_scenario_bind_tid ON ecos_scenario_binding (target_id);

COMMENT ON COLUMN ecos_scenario_binding.target_id IS 'v2.0 真 PG 主键（替代 target_ref 字符串别名；target_ref 保留只读兼容）';
COMMENT ON COLUMN ecos_scenario_binding.target_type IS '真资源类型：DATASOURCE|ONTOLOGY_ENTITY|KNOWLEDGE_ARTICLE|AGENT_PROFILE|SECURITY_POLICY|INTERFACE_REF';

-- CHECK 约束限枚举（B14 加固：防跨类型混用 dangling ref）
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_binding_target_type') THEN
        ALTER TABLE ecos_scenario_binding
            ADD CONSTRAINT chk_binding_target_type
            CHECK (target_type IS NULL OR target_type IN (
                'DATASOURCE', 'ONTOLOGY_ENTITY', 'KNOWLEDGE_ARTICLE',
                'AGENT_PROFILE', 'SECURITY_POLICY', 'INTERFACE_REF'
            ));
    END IF;
END $$;
