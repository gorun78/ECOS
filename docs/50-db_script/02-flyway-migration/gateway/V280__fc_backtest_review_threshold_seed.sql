-- V280 (详设-09 v1.2 §十一 H-4 要点 3 / W226 阈值配置单源): 回测与复审四枚判定阈值落 sysman 配置门面单表分组
-- 追溯:
--   R-66 ①（需求检视报告 §13.10）「回测阈值只准来自配置单源，禁前端传阈值」⇒ 代码侧对偶面 = 禁硬编码缺省，
--   未配置一律 null 上浮并 fail-closed（判据缺失时不给结论，禁"代码默认 vs 库内值"两态漂移，X-36 同族）；
--   详设-09 §六 H-4「阈值入 config_group；禁前端传阈值；任务只走 runtime-task」；
--   V217__fc_backtest.sql 表注释已声明"阈值不入列，判定源 = ecos.fc.backtest.review_threshold.*"⇒ 本脚本补齐该声明的数据面。
-- 需求依据: PRD-09 §5.2 公式与守卫原文（MAPE>15% / 覆盖率<80% / n<5 ⇒ LOW_SAMPLE 不参与告警）——
--           本组值即 PRD 原值，改判定线只改本行不改代码；公式定义本身在 PRD，不在本脚本重定义。
-- 批准: 与 V277~V279 同批 = **仅落迁移脚本文件，未对 ecos-postgres 实跑**（实跑属逐项授权项，见详设-09 §十二 门禁声明）。
-- 归属纪律: `sys_config` 属 **sysman 域**（控制域主 schema）。认知引擎代码侧零 sys_config 语句，
--           读全部经 `SysConfigService` 门面（守卫：BacktestThresholdSingleSourceTest + CognitiveConfigSingleSourceTest）。
-- 键名单源: 五枚键名字面量只出现在 `CognitiveConfigQueryService`（KEY_BACKTEST_*），业务侧只调门面方法；
--           `BacktestReviewThresholdReader` 是本组键的唯一读缝构造点，且零缺省常量。
-- 有意不播种的一枚: `ecos.fc.backtest.review_threshold.on_target_band` 保持"不存在"态 ——
--           PRD-09 §5.2 偏差方向原文是"取 Σ(预测−实际) 的符号"，无死区；配了死区才会把小额净偏收进 BALANCED，
--           属口径变更（须先改 PRD 再播种）。未配置时应用侧按纯符号判定，并在 numericCaliber 明示
--           "on_target_band unset"（禁读者把纯符号误当死区判定）。

-- ── 种子行（幂等：config_key UNIQUE，ON CONFLICT DO NOTHING ⇒ 重跑零重复、不覆盖运维已改的值）──
INSERT INTO sys_config (id, config_group, config_key, config_value, config_type, config_label, config_label_en, description, sort_order, status) VALUES
  ('cfg-fc-bt-min-samples', 'fc.backtest', 'ecos.fc.backtest.review_threshold.min_samples', '5', 'number',
   '回测·最小样本量(LOW_SAMPLE)', 'Backtest Min Sample Count',
   'PRD-09 §5.2 守卫原文 n<5 ⇒ LOW_SAMPLE，不参与告警。n 取"预测与实际双侧齐备"的实测有效样本数，不是请求声明的行数（拿应有行充样本会让守卫形同虚设）。未配置 / 空白 / 非整数 ⇒ 应用侧判 null 并 fail-closed 记 LOW_SAMPLE（禁截断猜值、禁代码缺省）', 1, 'active'),
  ('cfg-fc-bt-mape-ratio', 'fc.backtest', 'ecos.fc.backtest.review_threshold.mape_ratio', '0.15', 'number',
   '回测·MAPE 越限线', 'Backtest MAPE Ceiling',
   'PRD/R-66 原文 MAPE>15%。数值沿用既有确定性评估面（DeterministicErrorCalculator 产出）的聚合 MAPE，本组阈值只做比较、禁回测侧重算；zero-actual 行按 PRD §5.2 排除在比率之外。既有评估面没产出 MAPE ⇒ 该闸不评估并记 MAPE_GATE_UNEVALUABLE（禁代 0 或代 1）', 2, 'active'),
  ('cfg-fc-bt-min-interval-cov', 'fc.backtest', 'ecos.fc.backtest.review_threshold.min_interval_coverage', '0.80', 'number',
   '回测·区间覆盖率下限', 'Backtest Interval Coverage Min',
   'R-66 原文 覆盖率<80% 的区间面：实际 ∈ [P10,P90] 的格子数 / 可判格子数。分母只含三值齐备的样本；缺 P10/P90 或实际值的行进排除集并逐条记原因（禁由 P50 或邻值插值合成，R-62 ①）。无可判样本 ⇒ 该闸既不通过也不越限（两态严格可分）', 3, 'active'),
  ('cfg-fc-bt-min-data-cov', 'fc.backtest', 'ecos.fc.backtest.review_threshold.min_data_coverage', '0.80', 'number',
   '回测·数据覆盖率下限', 'Backtest Data Coverage Min',
   'R-66 原文 覆盖率<80% 的数据面：该期 ACTUAL 格子数 / 应预测格子数（应预测数取库内评估行数，禁前端传）。ACTUAL=0 与 ACTUAL 未回灌是两种真态，前者计分子、后者计缺项并逐枚列 missingRefs（DG-6 空集与故障严格可分）', 4, 'active')
ON CONFLICT (config_key) DO NOTHING;

-- ── 注释登记 ──────────────────────────────────────────────
COMMENT ON TABLE sys_config IS '系统配置单表（config_group 分组，铁律 §十 配置单源）；回测/复审判定阈值见 config_group=''fc.backtest''（V280，详设-09 H-4）';
