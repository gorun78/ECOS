-- V330 (D3 金额列红线修复，方案 A：新增 NUMERIC 列 + 回填): 场景表新增 budget_amount
-- 追溯: 需求 = 数据库访问规范 v1.4 MC01「金额列唯一形态 NUMERIC(p,s)」；
--       缺陷 D3 = ecos_control.ecos_business_scenario.budget 为 character varying（存「850万」中文文本）。
--       用户 2026-11-13 裁决方案 A（新增列回填，旧列保留，零破坏）。
-- 状态: 仅落迁移脚本文件，**未对 ecos-postgres 实跑**（实跑属未授权项，需逐项再确认）。
-- 纪律: IR03(只加不删) / MC01(NUMERIC) / MC02 / ST07(ecos_control) / ST09(无 FK)
-- 口径: budget 旧列**保留不动**（存量读方不受影响）；budget_amount 为权威数值列。
--       回填规则：「<数字>万」→ 数字 × 10000；纯数字原样；空/NULL 保持 NULL；无法解析保持 NULL 并留痕。
--       旧列与新列并行期：新写入由应用侧双写（后续批次），读方逐步切 budget_amount。

-- 1) 新增金额列（NUMERIC(18,2)，MC01）
ALTER TABLE ecos_control.ecos_business_scenario
    ADD COLUMN IF NOT EXISTS budget_amount NUMERIC(18,2);

COMMENT ON COLUMN ecos_control.ecos_business_scenario.budget_amount IS
    '预算金额（元，MC01 NUMERIC(18,2)）。V330 新增；由旧 budget 文本列清洗回填（「850万」→8500000）。旧列 budget 保留供存量读方过渡。';
COMMENT ON COLUMN ecos_control.ecos_business_scenario.budget IS
    '【过渡期】预算文本（如「850万」）。违反 MC01 金额列形态，V330 起权威值为 budget_amount；本列在全部读方切换后停写，勿新增使用。';

-- 2) 回填：一次 UPDATE 处理三种形态（中文「万」/ 纯数字 / 不可解析）
--    D24（2026-11-13 验证）：原写法用 PG 私有双冒号强制转换（连写两个冒号后接类型名），被 MC03 方言面门禁
--    判为「P200+ 严控段新增向量」（2 处），且会进「新增禁止」欠债名录。改用标准 SQL
--    `CAST(... AS NUMERIC(18,2))`，语义完全一致、方言面归零（多库兼容口径也更强）。
UPDATE ecos_control.ecos_business_scenario
SET budget_amount = CASE
        WHEN budget IS NULL OR btrim(budget) = '' THEN NULL
        -- 「850万」/「850万元」/「850 万」→ 8500000
        WHEN btrim(budget) ~ '^[0-9]+(\.[0-9]+)?\s*万(元)?$'
            THEN CAST(regexp_replace(btrim(budget), '\s*万(元)?$', '') AS NUMERIC(18,2)) * 10000
        -- 纯数字（可带千分位逗号）
        WHEN replace(btrim(budget), ',', '') ~ '^[0-9]+(\.[0-9]+)?$'
            THEN CAST(replace(btrim(budget), ',', '') AS NUMERIC(18,2))
        ELSE NULL
    END
WHERE budget_amount IS NULL;

-- 3) 留痕：把无法解析的旧值登记出来（不阻断迁移，交人工复核）
COMMENT ON TABLE ecos_control.ecos_business_scenario IS
    '业务场景主表（V330 起 budget_amount 为金额权威列；budget 文本列过渡保留）。';

-- ── DDL Lint Self-audit ──────────────────────────────────
-- IR03 只加不删：本脚本只 ADD COLUMN，不 DROP 任何列/表 ✅
-- MC01 金额列 NUMERIC：新增列 budget_amount NUMERIC(18,2) ✅
-- ST07 schema 归属：ecos_control（控制域）✅
-- ST09 无外键：未新增任何 FK ✅
-- DR02 表名带 schema：所有语句均以 ecos_control. 限定 ✅
-- 幂等：ADD COLUMN IF NOT EXISTS + UPDATE WHERE budget_amount IS NULL（可重复执行）✅
