-- V277 (卷09 v1.2 §十一 H-1 / §1.4 U-3 / §六 载体登记): 预测运行载体 ecos_dw.ecos_application_forecast_run **只加四列**（六要素物理化 + 异步任务回链 + 幂等复用回链）
-- 追溯: 卷09 v1.2 §7.1 W227/C209（六要素结构面已消、物理列缺 ⇒ 新登记 H-1）、W244/C226（版本列缺并入 H-1）、§1.4 U-3（ForecastRunSnapshotReader 无实现 ⇒ 与 H-1/H-5 同批结案）；需求依据 PRD-09 v1.2 §2.1（run 主表要素）+ §3.1（六要素断言点：snapshot_id / formula_version 必须可落库定位）
-- 批准: 卷09 v1.2 §十一 H-1 移交条目（口径 = 卷05 R-17 ① 已批准的"确定性产物落 ecos_dw + 经 data-engine 唯一写通道"，本脚本不改变归属，只把契约要素列补全）
-- 状态: 仅落迁移脚本文件，**未对 ecos-postgres 实跑**（实跑属未授权项，需逐项再确认后按 IR02 手动 psql 执行）
-- 纪律: IR03(只加不删) / DR01~DR08 / MC01~MC03 / ST07(落 ecos_dw) / ST08(cognitive 禁直连本表，读经 data-engine REST)
--
-- ── 为什么正好是这四列（缺一即六要素/运维语义断裂）────────────────
-- snapshot_id        ← 六要素之"输入快照"。V197 现有 caliber_id/caliber_version/as_of_time 三要素，
--                      snapshot_id 缺 ⇒ 六要素断言点"SNAPSHOT_FROZEN 态必须非空 + checksum 可验"无落库处，
--                      ForecastRunSnapshotReader.getRun() 无法定位冻结范围（卷09 §1.4 U-3 断链根因）。
-- formula_version    ← 六要素之"公式版本"。现状由计算层 DeterministicForecastCalculator.SixFactors /
--                      contentHash 内存承载，重开进程即失，历史 run 公式文本不可由快照重建 ⇒ 必须落列。
--                      宽度对齐既有 caliber_version VARCHAR(20)（同源版本号形态，非自增整数）。
-- reused_from_run_id ← 幂等重跑语义（PRD-09 §2.1 "相同 (caliberVersion, asOfTime, scopeHash,
--                      scenarioSet, overridesHash) 且既有 SUCCEEDED run ⇒ 默认返回既有 runId + reused:true"）。
--                      新建 run（force=true）需可追溯到被复用的基准 run，否则 diff=0 对账无锚。
-- task_id            ← 异步运行回链（PRD-09 §七 1 / 铁律 v2.0 §1.6 任务统一入口）：
--                      >50 项目或 >10 万行时 RUNNING 转 runtime-task，响应回真实 taskId，
--                      前端 AsyncTaskCenter 消费 td_runtime_task_status。禁自造 jobId ⇒ 必须有落库处。
--
-- ── 1. 增列（全部可空：V197 存量行无这些要素，禁回填伪造值）──────
ALTER TABLE ecos_dw.ecos_application_forecast_run
    ADD COLUMN IF NOT EXISTS snapshot_id        VARCHAR(64),
    ADD COLUMN IF NOT EXISTS formula_version    VARCHAR(20),
    ADD COLUMN IF NOT EXISTS reused_from_run_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS task_id            VARCHAR(64);

-- ── 2. 注释登记（六要素与运维语义的列级口径，供 Mapper/reader 侧对齐）──
COMMENT ON COLUMN ecos_dw.ecos_application_forecast_run.snapshot_id IS '六要素之输入快照标识（SNAPSHOT_FROZEN 后非空，指向 PRD-02 DATA-01 表五 ecos_forecast_input_snapshot；缺列期由计算层内存承载的历史行留 NULL，禁伪造回填）';
COMMENT ON COLUMN ecos_dw.ecos_application_forecast_run.formula_version IS '六要素之公式版本（与 caliber_version 同形态 VARCHAR(20) 版本号，非自增；APPROVED 口径公式的不可变版本引用，历史 run 公式文本由此列 + caliber_id 快照重建）';
COMMENT ON COLUMN ecos_dw.ecos_application_forecast_run.reused_from_run_id IS '幂等复用来源运行键（PRD-09 §2.1：命中同 caliberVersion+asOfTime+scopeHash+scenarioSet+overridesHash 的既有 SUCCEEDED run 时响应 reused=true；force=true 新建时本列指向被复用的基准 forecast_run_id，供 diff=0 对账锚定）';
COMMENT ON COLUMN ecos_dw.ecos_application_forecast_run.task_id IS '异步运行任务键（铁律 v2.0 §1.6 经 runtime-task ITaskManagementService.submitTask 返回的真实 taskId；禁自造 jobId；同步小样本运行为 NULL）';

-- ── 3. 索引（普通索引，MC03 禁条件索引；只加不删，既有索引未动）──
CREATE INDEX IF NOT EXISTS idx_afcr_task   ON ecos_dw.ecos_application_forecast_run (task_id);
CREATE INDEX IF NOT EXISTS idx_afcr_reused ON ecos_dw.ecos_application_forecast_run (reused_from_run_id);

-- ── 4. 明确不在本脚本范围（避免越界，逐条给判据）──────────────────
-- ① 金额列（amount/contribution/deltaProfit）: **不加**。V197 无金额列是设计决定——金额只出现在响应与
--    审计事件、明细走 *_result_json TEXT（ST03-A 现状登记见卷09 §六），加金额列会造出第二金额事实源。
-- ② 期间关账 / outcome 载体 / 回测表: 属 H-3/H-4（卷02/卷05 属主），本脚本不碰。
-- ③ V215~V218（ecos_dw.ecos_fc_run/_result_detail/_backtest/_action_ext）: 卷09 §六 定性"预留未启用"，
--    本批**不启用、不删**（IR03）；是否启用由 H-3/H-4/H-5 结案时裁定。
-- ④ scope_hash/overrides_hash（幂等键本体）: 暂不加列——现有 (caliber_version, as_of_time, scenario_id,
--    overrides_json) 四列足以确定性重算 hash，加列即冗余事实源。若 H-5 实装出现性能问题，另立脚本裁决。
-- ⑤ status 枚举 CHECK: V197 已声明本批不建（R-15 同纪律），沿用不反转。

-- ── 5. 回滚说明 ──────────────────────────────────────────────
-- PG 增列可逆但本仓红线 IR03 禁 DROP COLUMN：如需回滚，只停写新列并回退 Mapper/reader 代码，
-- 列体保留（NULL 值无副作用）。回滚不得 DELETE 历史行。
-- 幂等性: ADD COLUMN IF NOT EXISTS + COMMENT + CREATE INDEX IF NOT EXISTS，重复执行零报错、零重复。

-- ── DDL Lint Self-audit ──────────────────────────────────────
-- [DR01] schema 限定 ecos_dw. ✓  [DR03] 单数列名 ✓  [DR04] 无新增 JSON 语义列（既有 *_json 未动）✓
-- [DR05] 无布尔列新增（不涉 is_* 形态）✓  [DR06~DR08] 七基线列 V197 已在位、本脚本不重复不加删 ✓
-- [MC01] 未改主键；新增列全为业务键引用，宽度对齐 V197 同族列（forecast_run_id/caliber_version 同源）✓
-- [MC02] 无二进制 JSON / 无数组 / 无 JSONB ✓  [MC03] 无条件索引、无裸 cast ✓
-- [ST03] 无金额列新增 ✓（见 §4-①）  [ST07] 落 ecos_dw 业务域 ✓  [IR03] 只加不删，旧列旧索引未动 ✓
-- [ST08] 读路径纪律（应用侧，非 DDL 可表达）：cognitive 侧 ForecastRunSnapshotReader 实现只经 data-engine REST 取本表数据，禁直连 ecos_dw
-- [V197 接续] 本脚本是 V197 的**增量后继**，不替代 V197；两者同表、只加列，无冲突
