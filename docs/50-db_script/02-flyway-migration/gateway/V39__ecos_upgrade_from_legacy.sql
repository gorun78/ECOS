-- V37: 原产品客户数据升级迁移
-- 三类管控客户: 运营管控(信科/制造) / 战略管控(连锁/平台) / 财务管控(投资集团)

-- 1. 运营管控型 — 信科/制造企业
-- 从老系统导入项目、合同、供应商、仓库数据
DO $$
BEGIN
    -- 迁移项目数据（如果老表存在）
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'legacy_project') THEN
        INSERT INTO ecos_object_data (id, object_type, properties, tenant_id, created_at, updated_at)
        SELECT 
            'proj-' || p.id,
            'Project',
            jsonb_build_object(
                'projectName', p.project_name,
                'budget', p.budget,
                'startDate', p.start_date,
                'status', p.status,
                'manager', p.manager
            ),
            COALESCE(p.tenant_id, 'tenant-a'),
            p.created_at,
            p.updated_at
        FROM legacy_project p
        WHERE p.status != 'DELETED'
        ON CONFLICT (id) DO NOTHING;
    END IF;
END $$;

-- 2. 创建预设指标（各版通用）
INSERT INTO ecos_dq_rule (id, code, name, target_entity, target_field, rule_expression, severity, status, tenant_id)
VALUES
    ('rule-oee-001', 'rule-oee-001', 'OEE综合效率', 'production_line', 'oee', 'value<0.75', 'HIGH', 'ACTIVE', 'tenant-a'),
    ('rule-yield-001', 'rule-yield-001', '良品率', 'production_line', 'yield_rate', 'value<0.90', 'MEDIUM', 'ACTIVE', 'tenant-a'),
    ('rule-material-001', 'rule-material-001', '物料编码合规', 'material', 'code', 'value NOT MATCH ''^[A-Z]{2}-[0-9]{6}$''', 'HIGH', 'ACTIVE', 'tenant-a'),
    ('rule-supplier-001', 'rule-supplier-001', '供应商资质过期', 'supplier', 'cert_expiry', 'value < CURRENT_DATE', 'CRITICAL', 'ACTIVE', 'tenant-a')
ON CONFLICT (id) DO NOTHING;

-- 3. 战略管控型 — 连锁/平台企业（事业部协同视角）
-- 创建事业部实体预设
INSERT INTO ecos_ontology_entity (code, name, description, table_name, tenant_id)
VALUES
    ('BusinessUnit', '事业部', '战略管控的事业部组织', 'ecos_object_data', 'tenant-a'),
    ('KPI', '关键绩效指标', '事业部KPI', 'ecos_object_data', 'tenant-a')
ON CONFLICT (code) DO NOTHING;

-- 4. 财务管控型 — 投资集团（ROI视角）
-- 创建投资实体预设
INSERT INTO ecos_ontology_entity (code, name, description, table_name, tenant_id)
VALUES
    ('Investment', '投资项目', '集团投资项目', 'ecos_object_data', 'tenant-a'),
    ('Portfolio', '资产组合', '投资组合', 'ecos_object_data', 'tenant-a')
ON CONFLICT (code) DO NOTHING;
