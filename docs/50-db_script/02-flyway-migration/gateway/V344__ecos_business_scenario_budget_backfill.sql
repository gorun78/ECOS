-- ============================================================
-- V344__ecos_business_scenario_budget_backfill.sql — budget 旧列中文金额回填 budget_amount
-- 追溯: RT #14（2026-11-17 用户裁决「按建议执行」→ 选项 H1）
--
-- 现象：ecos_control.ecos_business_scenario.budget 是 character varying(64)，
--       243 行存量里 80 行有值，形态混杂：
--         750万(36) 约五百万(33) 850万(4) 1200万元(3) 3500000(3) 1,250,000(3)
--         1200万(1) 320万(1) 900 万(1) 12.5万(1)
--       其中 41 行已由 D3 正确写入 budget_amount（numeric(18,2)），39 行仍为空。
--
-- 背景订正：缺陷台账记 D3「已修复」，但实际修复范围只是「新增 budget_amount 数值列」，
--   旧 budget 列原封未动且仍在被写 ⇒ 按 MC01「金额列唯一形态 NUMERIC」判存量违规。
--   RT 层因此判未达成，本批是对该结论的落实。
--
-- 处置（H1，用户裁决）：
--   1. 把剩余 39 行按形态解析写入 budget_amount（已解析的 41 行不动）
--   2. 解析不了的形态**不瞎猜**，保持 NULL 并计入下方校验查询供人工复核
--   3. budget 列保留不删（schema 只加不删），并在列注释里标记为「展示用、不参与计算」
--
-- 解析规则（按优先级，全部写成 CASE 分支）：
--   a. 纯数字 + 千分位：去掉逗号空格后直接转 numeric
--   b. "<数字>万元" / "<数字>万"：数字 × 10000
--   c. "约<中文数字>百万"：中文数字映射后 × 1000000（"约"表示近似，取值不精确）
--   d. 其它：保持 NULL（不猜）
--
-- 幂等: 只对 budget_amount IS NULL 的行生效，重放时已回填的行不在范围内 ⇒ 零副作用。
-- 纪律: MC01 金额列 NUMERIC(18,2) ✓ / MC02 不新增列 ✓ / IR03 不删列 ✓ / DR01 全 schema 限定 ✓
-- 号段: 接 V343；落地后下一号 V345（须同批核对 MigrationVersionUniquenessTest 与三枚 SCRIPT_FLOOR）。
-- ============================================================

-- ── 1) "<数字>万元" / "<数字>万"（可含空格）──
UPDATE ecos_control.ecos_business_scenario
   SET budget_amount = ROUND(
           CAST(regexp_replace(budget, '[^0-9.]', '', 'g') AS numeric) * 10000, 2)
 WHERE budget_amount IS NULL
   AND budget ~ '^[0-9.]+\s*万(元)?$';

-- ── 2) "约<中文数字>百万"：常见中文数字映射 ──
UPDATE ecos_control.ecos_business_scenario
   SET budget_amount = CASE
           WHEN budget LIKE '%五百万%' THEN 5000000.00
           WHEN budget LIKE '%三百万%' THEN 3000000.00
           WHEN budget LIKE '%八百万%' THEN 8000000.00
           WHEN budget LIKE '%一千万%' THEN 10000000.00
           WHEN budget LIKE '%二百万%' THEN 2000000.00
           WHEN budget LIKE '%四百万%' THEN 4000000.00
           WHEN budget LIKE '%六百万%' THEN 6000000.00
           WHEN budget LIKE '%七百万%' THEN 7000000.00
           WHEN budget LIKE '%九百万%' THEN 9000000.00
           WHEN budget LIKE '%一百万%' THEN 1000000.00
           ELSE NULL
       END
 WHERE budget_amount IS NULL
   AND budget LIKE '%百万%';

-- ── 3) 纯数字（含千分位逗号、前后空格）──
UPDATE ecos_control.ecos_business_scenario
   SET budget_amount = ROUND(
           CAST(regexp_replace(budget, '[^0-9.]', '', 'g') AS numeric), 2)
 WHERE budget_amount IS NULL
   AND budget ~ '^[0-9,\s]+(\.[0-9]+)?$'
   AND regexp_replace(budget, '[^0-9.]', '', 'g') <> '';

-- ── 4) 旧列标注为展示用途（不改型，只加注释）──
COMMENT ON COLUMN ecos_control.ecos_business_scenario.budget IS
    'RT#14/V344：人类可读展示列（历史中文金额原值），不参与任何计算。'
    '金额权威列 = budget_amount（numeric(18,2)，MC01）。新写入请只写 budget_amount。';

-- ── 校验（人工复核用）────────────────────────────────────────
-- 回填后仍无 budget_amount 的行（应只剩 "约"-前缀已覆盖之外的非标形态）：
--   SELECT id, budget FROM ecos_control.ecos_business_scenario
--    WHERE budget IS NOT NULL AND budget <> '' AND budget_amount IS NULL;
-- 回填总数：SELECT count(*) FROM ecos_control.ecos_business_scenario WHERE budget_amount IS NOT NULL;

-- ── 回滚 ────────────────────────────────────────────────────
-- 本脚本只写 budget_amount 且只针对原本为 NULL 的行，回滚需按 id 备份还原（不属于单条语句能安全回退的范围）。
-- 如必须回退：UPDATE ... SET budget_amount = NULL WHERE create_time > 'V344 执行时刻' AND budget_amount IS NOT NULL;
