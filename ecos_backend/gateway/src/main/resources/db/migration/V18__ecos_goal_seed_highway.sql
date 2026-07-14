-- ============================================================
-- V18__ecos_goal_seed_highway.sql
-- 种子数据: 高速公路信科 2026 年目标金字塔示例
-- 1 个 L1 战略 → 3 个 L2 OKR → 8 个 L3 KPI
-- 幂等: 先查是否存在再 INSERT
-- ============================================================

DO $$
DECLARE
    v_l1_id   BIGINT;
    v_l2_1_id BIGINT;
    v_l2_2_id BIGINT;
    v_l2_3_id BIGINT;
BEGIN

    -- ── L1 战略目标 ──────────────────────────────────────
    IF NOT EXISTS (SELECT 1 FROM ecos_wm_goal WHERE name = '2026年营收突破5亿元，净利润率提升至15%') THEN
        INSERT INTO ecos_wm_goal (name, description, goal_type, weight, org_id, owner_user_id,
            start_date, end_date, target_value, current_value, unit, progress, status,
            parent_id)
        VALUES ('2026年营收突破5亿元，净利润率提升至15%',
                '全年营收目标5亿元，净利润率提升至15%，降低公路运营成本10%',
                'STRATEGIC', 100, 'DEPT_ROOT', 'u001',
                '2026-01-01', '2026-12-31', 50000.00, 39000.00, '万元', 78, 'active',
                NULL)
        RETURNING id INTO v_l1_id;
    ELSE
        SELECT id INTO v_l1_id FROM ecos_wm_goal WHERE name = '2026年营收突破5亿元，净利润率提升至15%' LIMIT 1;
    END IF;

    -- ── L2 市场部 OKR ──────────────────────────────────────
    IF NOT EXISTS (SELECT 1 FROM ecos_wm_goal WHERE name = '市场部：拓展ETC用户至300万') THEN
        INSERT INTO ecos_wm_goal (name, description, goal_type, weight, org_id, owner_user_id,
            start_date, end_date, target_value, current_value, unit, progress, status,
            parent_id)
        VALUES ('市场部：拓展ETC用户至300万',
                '通过线上线下渠道拓展ETC用户，覆盖省内主要城市群',
                'OKR', 35, 'DEPT_MARKETING', 'u101',
                '2026-01-01', '2026-12-31', 300.00, 218.00, '万户', 73, 'active',
                v_l1_id)
        RETURNING id INTO v_l2_1_id;
    ELSE
        SELECT id INTO v_l2_1_id FROM ecos_wm_goal WHERE name = '市场部：拓展ETC用户至300万' LIMIT 1;
    END IF;

    -- ── L2 商务部 OKR ──────────────────────────────────────
    IF NOT EXISTS (SELECT 1 FROM ecos_wm_goal WHERE name = '商务部：公路运营成本降低10%') THEN
        INSERT INTO ecos_wm_goal (name, description, goal_type, weight, org_id, owner_user_id,
            start_date, end_date, target_value, current_value, unit, progress, status,
            parent_id)
        VALUES ('商务部：公路运营成本降低10%',
                '优化养护和收费流程，降低全省高速公路运营总成本',
                'OKR', 35, 'DEPT_COMMERCE', 'u201',
                '2026-01-01', '2026-12-31', 10.00, 7.20, '%', 72, 'active',
                v_l1_id)
        RETURNING id INTO v_l2_2_id;
    ELSE
        SELECT id INTO v_l2_2_id FROM ecos_wm_goal WHERE name = '商务部：公路运营成本降低10%' LIMIT 1;
    END IF;

    -- ── L2 财务部 OKR ──────────────────────────────────────
    IF NOT EXISTS (SELECT 1 FROM ecos_wm_goal WHERE name = '财务部：净利润率提升至15%') THEN
        INSERT INTO ecos_wm_goal (name, description, goal_type, weight, org_id, owner_user_id,
            start_date, end_date, target_value, current_value, unit, progress, status,
            parent_id)
        VALUES ('财务部：净利润率提升至15%',
                '通过成本控制和营收增长，将净利润率从12%提升至15%',
                'OKR', 30, 'DEPT_FINANCE', 'u301',
                '2026-01-01', '2026-12-31', 15.00, 13.10, '%', 87, 'active',
                v_l1_id)
        RETURNING id INTO v_l2_3_id;
    ELSE
        SELECT id INTO v_l2_3_id FROM ecos_wm_goal WHERE name = '财务部：净利润率提升至15%' LIMIT 1;
    END IF;

    -- ── L3 KPI: 市场部子目标 (3个) ──────────────────────────
    IF NOT EXISTS (SELECT 1 FROM ecos_wm_goal WHERE name = 'ETC新办用户突破80万户') THEN
        INSERT INTO ecos_wm_goal (name, description, goal_type, weight, org_id, owner_user_id,
            start_date, end_date, target_value, current_value, unit, progress, status,
            parent_id)
        VALUES ('ETC新办用户突破80万户',
                '线上+线下联合推广，年度新增ETC用户80万',
                'KPI', 40, 'DEPT_MARKETING', 'u102',
                '2026-01-01', '2026-12-31', 80.00, 56.00, '万户', 70, 'active',
                v_l2_1_id);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM ecos_wm_goal WHERE name = 'ETC通行费交易额达120亿') THEN
        INSERT INTO ecos_wm_goal (name, description, goal_type, weight, org_id, owner_user_id,
            start_date, end_date, target_value, current_value, unit, progress, status,
            parent_id)
        VALUES ('ETC通行费交易额达120亿',
                '保障ETC车道稳定运行，全年交易金额突破120亿元',
                'KPI', 35, 'DEPT_MARKETING', 'u103',
                '2026-01-01', '2026-12-31', 120.00, 91.00, '亿元', 76, 'active',
                v_l2_1_id);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM ecos_wm_goal WHERE name = '客户满意度提升至92分') THEN
        INSERT INTO ecos_wm_goal (name, description, goal_type, weight, org_id, owner_user_id,
            start_date, end_date, target_value, current_value, unit, progress, status,
            parent_id)
        VALUES ('客户满意度提升至92分',
                '通过NPS调查和客服优化，客户满意度评分从87提升至92',
                'KPI', 25, 'DEPT_MARKETING', 'u104',
                '2026-01-01', '2026-12-31', 92.00, 89.00, '分', 97, 'active',
                v_l2_1_id);
    END IF;

    -- ── L3 KPI: 商务部子目标 (3个) ──────────────────────────
    IF NOT EXISTS (SELECT 1 FROM ecos_wm_goal WHERE name = '养护成本降低至每公里28万元') THEN
        INSERT INTO ecos_wm_goal (name, description, goal_type, weight, org_id, owner_user_id,
            start_date, end_date, target_value, current_value, unit, progress, status,
            parent_id)
        VALUES ('养护成本降低至每公里28万元',
                '引入预防性养护和智能化巡检，将每公里年度养护成本压缩至28万',
                'KPI', 40, 'DEPT_COMMERCE', 'u202',
                '2026-01-01', '2026-12-31', 28.00, 30.50, '万元/公里', 65, 'active',
                v_l2_2_id);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM ecos_wm_goal WHERE name = '收费站人力成本压缩15%') THEN
        INSERT INTO ecos_wm_goal (name, description, goal_type, weight, org_id, owner_user_id,
            start_date, end_date, target_value, current_value, unit, progress, status,
            parent_id)
        VALUES ('收费站人力成本压缩15%',
                '推广自助缴费和无人化收费车道，减少人工收费员编制',
                'KPI', 30, 'DEPT_COMMERCE', 'u203',
                '2026-01-01', '2026-12-31', 15.00, 11.20, '%', 75, 'active',
                v_l2_2_id);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM ecos_wm_goal WHERE name = '机电设备故障率降低20%') THEN
        INSERT INTO ecos_wm_goal (name, description, goal_type, weight, org_id, owner_user_id,
            start_date, end_date, target_value, current_value, unit, progress, status,
            parent_id)
        VALUES ('机电设备故障率降低20%',
                '建立设备全生命周期管理系统，故障率从5.2%降至4.2%以下',
                'KPI', 30, 'DEPT_COMMERCE', 'u204',
                '2026-01-01', '2026-12-31', 4.20, 4.85, '%', 65, 'active',
                v_l2_2_id);
    END IF;

    -- ── L3 KPI: 财务部子目标 (2个) ──────────────────────────
    IF NOT EXISTS (SELECT 1 FROM ecos_wm_goal WHERE name = '营收同比增长不低于20%') THEN
        INSERT INTO ecos_wm_goal (name, description, goal_type, weight, org_id, owner_user_id,
            start_date, end_date, target_value, current_value, unit, progress, status,
            parent_id)
        VALUES ('营收同比增长不低于20%',
                '确保主营业务收入同比增速达到20%，力争25%',
                'KPI', 50, 'DEPT_FINANCE', 'u302',
                '2026-01-01', '2026-12-31', 20.00, 17.00, '%', 85, 'active',
                v_l2_3_id);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM ecos_wm_goal WHERE name = '管理费用占营收比降至6%以下') THEN
        INSERT INTO ecos_wm_goal (name, description, goal_type, weight, org_id, owner_user_id,
            start_date, end_date, target_value, current_value, unit, progress, status,
            parent_id)
        VALUES ('管理费用占营收比降至6%以下',
                '优化行政和差旅支出，管理费用占比从7.5%压缩至6%',
                'KPI', 50, 'DEPT_FINANCE', 'u303',
                '2026-01-01', '2026-12-31', 6.00, 6.80, '%', 78, 'active',
                v_l2_3_id);
    END IF;

END $$;
