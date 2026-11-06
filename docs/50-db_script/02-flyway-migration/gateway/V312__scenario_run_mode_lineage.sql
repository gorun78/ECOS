-- ============================================================
-- V312__scenario_run_mode_lineage.sql — 场景运行 runMode 与沙盘 lineage（详细设计-场景工作台 v1.0 §5）
-- 追溯: c-design/详细设计-场景工作台.md §5.1 铁律 1/2/6（runMode 独立维度、产物带可验证标记、
--       缺 runMode 的历史产物不得默认正式可信）；SW-05 沙盘隔离
-- 兼容: ecos_ai.ecos_scenario_run 仍在 workspace 运行编排写入面（V125；2026-11-03 只读实测该表位于 ecos_ai schema）；V197 的产物表
--       ecos_dw.ecos_application_forecast_run 属业务域权威产物，本迁移不改其结构。
-- 只写红线: ADD COLUMN IF NOT EXISTS；历史行 run_mode 缺省 FORMAL 但 sandbox_lineage='UNKNOWN'
--           标记其来源未验证（应用侧不得把 UNKNOWN 当正式可信，见 GET /runs 返回）。
-- 纪律: DR01~DR08 / MC01~MC03 / IR02 / IR03(只加不删)
-- ============================================================

ALTER TABLE ecos_ai.ecos_scenario_run
    ADD COLUMN IF NOT EXISTS run_mode VARCHAR(16) NOT NULL DEFAULT 'FORMAL';
ALTER TABLE ecos_ai.ecos_scenario_run
    ADD COLUMN IF NOT EXISTS source_run_id VARCHAR(64);
ALTER TABLE ecos_ai.ecos_scenario_run
    ADD COLUMN IF NOT EXISTS sandbox_lineage VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN';

CREATE INDEX IF NOT EXISTS idx_scenario_run_mode
    ON ecos_ai.ecos_scenario_run (scenario_id, run_mode, is_deleted);

COMMENT ON COLUMN ecos_ai.ecos_scenario_run.run_mode IS
    '运行维度 FORMAL/SANDBOX（与 run_type 正交；沙盘产物禁止被正式运行引用）';
COMMENT ON COLUMN ecos_ai.ecos_scenario_run.source_run_id IS
    '沙盘复制来源运行 ID（仅 SANDBOX 或由沙盘晋升的正式运行填写，禁编造）';
COMMENT ON COLUMN ecos_ai.ecos_scenario_run.sandbox_lineage IS
    '沙盘血缘：SANDBOX / PROMOTED / NONE / UNKNOWN（UNKNOWN = 迁移前历史行，来源未验证，不得默认正式可信）';

-- ── 回滚说明 ──────────────────────────────────────────────
-- 仅加列与索引；回滚 = 应用侧忽略新列（读面回退旧 SELECT），列保留（IR03 禁脚本内 DROP）。
-- 幂等性：ADD COLUMN IF NOT EXISTS + CREATE INDEX IF NOT EXISTS ⇒ 重复执行零报错。

-- ── DDL Lint Self-audit ──────────────────────────────────────
-- [DR01] 既有表 ecos_ai.ecos_scenario_run（V125 单源；2026-11-03 只读实测 schema=ecos_ai）✓ [DR04] 无 JSONB 新增 ✓
-- [DR05] 无布尔列 ✓ [MC02] 无二进制浮点 ✓ [MC03] is_deleted 并入索引键 ✓
-- [ST07] 存量 knownLegacy 表只加列不改归属 ✓ [ST09] 无 FK ✓ [IR02] 仅文件 ✓ [IR03] 只加 ✓
