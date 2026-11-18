-- V171.1 (卷03 §E.3 / F03-06 行1): Function 审计实参错位数据纠正 —— caller_id 误存函数名移回 function_name，caller_id 置 unknown-legacy
-- 追溯: W80/C63（O-13 实测：public.ecos_function_audit_log 5 行中 function_name 全空，caller_id 存的是函数名 fn_emp_tenure / fn_order_gross_margin / custom_function_6416）
-- 批准: 需求检视报告 §十四 14.1（2026-09-29）之 R-5=①+③（历史 5 行**保留并回填**，成本极低且为"曾经不可归因"的证据）
-- 号段: 与卷02 同版本号冲突，按定版规则取 .1 子版本（V171 整号 = 卷02）
-- 状态: 仅落迁移脚本文件，**未对 ecos-postgres 实跑**（实跑属 §14.4 未授权项，需逐项再确认）
-- 纪律: DR01~DR08 / MC01~MC03 / ST03-A / ST07 / IR02(手动 psql) / IR03(只加不删)
-- 范围纪律：只操作审计表自身数据，不改业务代码；调用点参数顺序修正 + "从 expression 经 FunctionRegistry
--   反查函数名"属代码侧改造（F03-06 目标态列①），SQL 侧无法安全实现表达式→函数名映射，本脚本不伪造
--   （对 function_name 仍为空且 caller_id 无可移值的残余行，留待代码侧回填，见 §3 说明）。
-- 目标方言：PostgreSQL（IR02 手动 psql 单库执行；语句均为标准 SQL，无 :: cast）。
-- 执行前备份（回滚凭据，人工解注执行，不随脚本自动跑）：
--   CREATE TABLE public.ecos_function_audit_log_bak_v171 AS SELECT * FROM public.ecos_function_audit_log;
-- 回滚说明：本脚本仅改 function_name/caller_id 两列且带幂等 WHERE；回滚 = 用上述备份表按 id 还原两列。

-- ── 1. 主修正：function_name 空 且 caller_id 存有"非哨兵值"→ 视为误存函数名，移回并置哨兵 ──
-- 幂等条件：执行后 function_name 非空，二次执行不再命中；'anonymous'/'unknown-legacy' 为哨兵值不移回
--（'anonymous' 属 O-14① 缺省放行产物，不是函数名；由代码侧 AuditContextGuard 治理，不在数据层伪造主体）。
DO $$
BEGIN
    IF to_regclass('public.ecos_function_audit_log') IS NOT NULL THEN
        UPDATE public.ecos_function_audit_log
           SET function_name = caller_id,
               caller_id     = 'unknown-legacy'
         WHERE (function_name IS NULL OR function_name = '')
           AND caller_id IS NOT NULL
           AND caller_id <> ''
           AND caller_id NOT IN ('anonymous', 'unknown-legacy');
        RAISE NOTICE 'V171.1: function_name 移回完成，剩余空行见 §3 说明';
    ELSE
        RAISE NOTICE 'V171.1: public.ecos_function_audit_log 不存在，跳过';
    END IF;
END $$;

-- ── 2. 哨兵统一：历史缺省 'anonymous' 主体改写为 'unknown-legacy'（不伪造主体，仅统一台账标记）──
DO $$
BEGIN
    IF to_regclass('public.ecos_function_audit_log') IS NOT NULL THEN
        UPDATE public.ecos_function_audit_log
           SET caller_id = 'unknown-legacy'
         WHERE caller_id = 'anonymous';
    END IF;
END $$;

-- ── 3. 残余空行登记（只读核对语句，人工执行留痕，不修改数据）────────────────
--   SELECT id, expression, function_name, caller_id, created_at
--     FROM public.ecos_function_audit_log
--    WHERE function_name IS NULL OR function_name = '';
-- 处置：该批行函数名由代码侧 FunctionRegistry 按 expression 反查补录（F03-06），非本脚本职责。

-- ── DDL Lint Self-audit ──────────────────────────────────
-- [DR01] 全语句 schema 限定 public. ✓  [MC03] 无 :: 裸 cast / 无 JSON 操作符 / 无 partial index ✓
-- [IR03] 只加不删：无 DROP/TRUNCATE；UPDATE 仅改两列值且幂等，备份语句默认注释 ✓
-- [ST07] 仅触审计表自身（public 主控制基线），不涉业务域 ✓  [IR02] 手动 psql，未实跑 ✓
-- [ST03-A] 无金额/敏感列新增，无登记项 ✓
