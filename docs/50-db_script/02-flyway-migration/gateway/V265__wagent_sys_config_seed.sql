-- V265 (卷10 §6.4 seed 与字典): W Agent 编排域六组配置字典落 sysman 配置门面单表分组 sys_config
-- 追溯: W245~W277 ↔ C227~C259（卷 10 §七）；本脚本承载 F10-08（阈值外置）/ F10-10（降级白名单）/
--       F10-11（Run 预算）/ F10-17~F10-19（工具集与披露）/ F10-21（自动化等级上限）/ F10-23（发布目标适配器），
--       需求依据 REQ-WAG-08/10/11/17~19/21/23；§6.4 明载"新增字典组（经 sysman api 门面，单表分组
--       config_group='wagent.*'）……每项 seed ≥3 行、幂等 ON CONFLICT DO NOTHING、与 V 号同 commit"
-- 批准: 需求检视报告 §十四 14.1（2026-09-29）之 R-33=①、R-35=①、R-36、R-43=推荐项；执行边界 §14.4 = 脚本文件落地
-- 状态: 仅落迁移脚本文件，**未对 ecos-postgres 实跑**（实跑属 §14.4 未授权项，需逐项再确认）
--       ⇒ 本批 39 行 seed 的"配置生效"没有任何库侧证据；应用侧读不到行时按代码内保守分支回落
--       （阈值缺省 = 该 check 记 UNKNOWN 而非 pass，见 F10-08"失败 = UNKNOWN 且计入缺项，禁算 pass"）
-- 归属纪律: `sys_config` 属 **sysman 域**（控制域配置单表，铁律 §十 配置/字典单表分组）。
--       W Agent（ai-engine）代码侧不得出现 sys_config 的任何语句 —— 读/写全部经 `SysConfigService` 门面
--       （先例口径同 V263 认知域、V264 智能域；守卫同 CognitiveConfigSingleSourceTest 同类）
-- 实测落位（2026-10-10 只读取证，本机 ecos-postgres / 库 sys_man）：
--       `SELECT table_schema FROM information_schema.tables WHERE table_name='sys_config'` = **ecos_control**
--       （`SHOW search_path` = `ecos_control, ecos_data, ecos_ontology, ecos_ai, ecos_cognitive,
--        ecos_knowledge, ecos_infra, ecos_dw, public` ⇒ 首位即 ecos_control）
--       ⇒ 本脚本沿用 V13/V263/V264 的**非限定** `sys_config` 写法（同形态、同 search_path 解析结果 =
--          ecos_control.sys_config）；加 `ecos_control.` 限定反而与本目录既有口径分裂，限定化统一留给
--          schema 归位批（登记表 §五 DR01 行同判）
-- 库侧对账（只读实测，防重键）：`config_group` 现存 9 组 = data-engine/global/cognitive 系以外
--       （data-engine 49、global 11、data-foundation 6、biz-semantics 5、operations 5、g4-agent 4、
--        knowledge 12、ontology-engine 12、security 4），`config_key LIKE 'wagent%'` = **0 行**，
--       `config_group LIKE 'wagent%'` = **0 行** ⇒ 本批六组 39 键全为新增，与既有键零冲突
--       （注：V263 `cognitive`、V264 `ai-engine` 两组在本机库亦 0 行 ⇒ 印证 §14.4"脚本已落地、未实跑"）
-- 分组与键的分工: `config_group` 取 §6.4 冻结的六组字面名（分组=运维在配置 UI 的归类维度，
--       经 SysConfigService 按 config_group 批量读取）；`config_key` 自带完整命名空间（同 V263
--       group='cognitive' / key='ecos.cognitive.enabled' 的先例形态），保证全局 UNIQUE 且不依赖拼接
-- 多行值编码口径: 复合值（降级白名单 applies_to+requires_ack、工具集成员清单）用 `k=v;k=v` / 逗号分隔
--       串，沿用 V263 `cfg-cog-domain-alias`（'经营=FORECAST;运营=DETECT'）既有仓内形态；
--       新增 JSON 形态值不改表列（无新列 ⇒ DR04/MC02 不适用），禁在本表加 `*_json` 列（属 §14.4 未授权补列面）
-- 号段: 接续最大主号 V264 取 V265（登记表 §二"新增按接续最大号取，不回填空洞 V241~V248"）

-- ── 组 1/6: wagent.readiness.threshold（profile × layer × check_code 阈值，F10-08）──────────────
-- 定级规则本身不在此表（"任一 blocker→D；≥2 fail→C；任一 warn→B；全 pass→A" 是代码内确定性算法，
-- LLM 不参与，见 §4.4）；本组只外置**判 pass/warn/fail 的比较界**。
-- check_code 与四层探测项一一对应（卷 10 §6.4 表 + F10-08 伪码）：
--   D 层 = data.check_coverage / data.staleness_days   I 层 = onto.caliber_defined / onto.conflict_count
--   K 层 = know.sample_size / know.applicability        C 层 = cog.cm_ready / cog.backtest_ready
-- profile='default' 为兜底档；profile 专属行（forecast）优先于 default（ThresholdPolicy.resolve 先具体后通配）。
INSERT INTO sys_config (id, config_group, config_key, config_value, config_type, config_label, config_label_en, description, sort_order, status) VALUES
  ('cfg-wagent-thr-dft-d-cov', 'wagent.readiness.threshold', 'wagent.readiness.default.data.check_coverage', '0.80', 'number',
   'D 层·数据覆盖率下限', 'D-Layer Data Coverage Min',
   'F10-08 D 层 data.check_coverage：目标口径覆盖的期间/组织单元比例 < 本值 ⇒ 该检查项 fail，计入缺项；取值域 (0,1]，越界/非数 ⇒ 本项记 UNKNOWN（禁算 pass）', 1, 'active'),
  ('cfg-wagent-thr-dft-d-stale', 'wagent.readiness.threshold', 'wagent.readiness.default.data.staleness_days', '7', 'number',
   'D 层·数据新鲜度上限(天)', 'D-Layer Staleness Days Max',
   'F10-08 D 层 data.staleness_days：最近一次批次入湖距 as-of 的天数 > 本值 ⇒ warn，> 本值×2 ⇒ fail；探测超时(3s/层) 记 UNKNOWN 并计入缺项', 2, 'active'),
  ('cfg-wagent-thr-dft-i-cal', 'wagent.readiness.threshold', 'wagent.readiness.default.onto.caliber_defined', 'true', 'boolean',
   'I 层·口径必须已定义', 'I-Layer Caliber Defined',
   'F10-08 I 层 onto.check_metric：true = 目标指标必须绑定 PUBLISHED 口径版本（V167.1/V168.1 metric_definition + caliber 绑定链）否则该层 fail；本值置 false 仅用于演示档，生产禁关（关 = 数字无口径可追溯）', 3, 'active'),
  ('cfg-wagent-thr-dft-i-conf', 'wagent.readiness.threshold', 'wagent.readiness.default.onto.conflict_count_max', '0', 'number',
   'I 层·口径冲突数上限', 'I-Layer Caliber Conflict Max',
   'F10-08 I 层 onto.detect_conflict：同指标多口径冲突数 > 本值 ⇒ fail（默认 0 = 零冲突容忍）；等于本值+1 记 warn 并在缺口表落 blocker', 4, 'active'),
  ('cfg-wagent-thr-dft-k-n', 'wagent.readiness.threshold', 'wagent.readiness.default.know.sample_size_min', '30', 'number',
   'K 层·最小样本量', 'K-Layer Sample Size Min',
   'F10-08 K 层 know.select_profiles：可用画像/历史样本量 < 本值 ⇒ warn 并挂 DG-D1（展示样本量与置信区间）；联动 §4.5"样本 n<30 或 <10"两档降级', 5, 'active'),
  ('cfg-wagent-thr-dft-k-nfloor', 'wagent.readiness.threshold', 'wagent.readiness.default.know.sample_size_trend_floor', '10', 'number',
   'K 层·趋势判断下限', 'K-Layer Sample Trend Floor',
   '样本量 < 本值（默认 10）⇒ 不再给区间估计、改判"仅趋势方向"并强制 DG-D1 requires_ack（§4.5 末行两档口径的第二个界）；必须 ≤ sample_size_min，应用侧校验不满足时回落本值=sample_size_min/3 并记 WARN', 6, 'active'),
  ('cfg-wagent-thr-dft-k-app', 'wagent.readiness.threshold', 'wagent.readiness.default.know.applicability_min', '0.60', 'number',
   'K 层·画像适用度下限', 'K-Layer Applicability Min',
   'F10-08 K 层 know.select_profiles：命中画像对当前 scope 的 applicability < 本值 ⇒ 视为"缺历史画像先验"，挂 DG-K1 并需用户确认；取值域 (0,1]', 7, 'active'),
  ('cfg-wagent-thr-dft-c-cm', 'wagent.readiness.threshold', 'wagent.readiness.default.cog.cm_ready', 'true', 'boolean',
   'C 层·CM 必须就绪', 'C-Layer CM Ready',
   'F10-08 C 层 cog.model_availability：true = 意图所需 CM 类型（卷 05 CM-01~05）必须在册且 status=发布态，否则该层 blocker（定级直接判 D）；探测走 GET /api/v1/cognitive/models', 8, 'active'),
  ('cfg-wagent-thr-dft-c-bt', 'wagent.readiness.threshold', 'wagent.readiness.default.cog.backtest_ready', 'true', 'boolean',
   'C 层·回测必须就绪', 'C-Layer Backtest Ready',
   'F10-08 C 层 cog.backtest_ready：true = 预测/推演类意图要求该 CM 版本已有一次回测留痕（卷 09 F09-13 ecos_fc_backtest）；未就绪记 warn 不阻断，结论须带证据等级标注', 9, 'active'),
  ('cfg-wagent-thr-fc-d-cov', 'wagent.readiness.threshold', 'wagent.readiness.forecast.data.check_coverage', '0.90', 'number',
   'forecast 档·覆盖率下限', 'Forecast Profile Coverage Min',
   '预测类问题（intent=FORECAST）的 D 层加严档：覆盖率 < 0.90 即 fail（预测对外部数据完整性最敏感，卷 09 F09-07 口径链）；profile 命中优先级高于 default', 10, 'active'),
  ('cfg-wagent-thr-fc-k-n', 'wagent.readiness.threshold', 'wagent.readiness.forecast.know.sample_size_min', '50', 'number',
   'forecast 档·最小样本量', 'Forecast Profile Sample Size Min',
   '预测类问题的 K 层加严档（区间估计对 n 敏感）：< 50 ⇒ warn 挂 DG-D1，< 10（trend_floor 沿用 default 行）⇒ 只给方向', 11, 'active'),
  ('cfg-wagent-thr-fc-c-bt', 'wagent.readiness.threshold', 'wagent.readiness.forecast.cog.backtest_ready', 'true', 'boolean',
   'forecast 档·回测必须就绪', 'Forecast Profile Backtest Ready',
   '预测类问题把 default 档的"warn 不阻断"升为 blocker 语义：本值为 true 且该 CM 版本无回测留痕 ⇒ C 层 blocker（卷 09 F09-13 与 §9.6 硬门槛"认知模型发布门禁通过 100%"对齐）', 12, 'active')
ON CONFLICT (config_key) DO NOTHING;

-- ── 组 2/6: wagent.degrade.whitelist（预登记降级白名单八值，F10-10）───────────────────────────
-- 红线：配置单源可**关**不可**开新**（DegradePolicyTest#configCanOnlyDisableNeverInvent）。
--       ⇒ 本组**只允许**登记 WAgentDegradeEnum 的八个既有码；新增未注册码 = 应用侧 unregisteredDegradeIsRejected 拒绝。
-- 语义来源 = 卷 10 §4.5 失败与降级路径表 + F10-10（每值绑定 applies_to 与 requires_ack）。
-- Run/结论 VO 必带 degradations[]，前端降级横幅单源读该字段（卷 10 §三 组件契约 DegradeBanner）。
INSERT INTO sys_config (id, config_group, config_key, config_value, config_type, config_label, config_label_en, description, sort_order, status) VALUES
  ('cfg-wagent-dg-k1', 'wagent.degrade.whitelist', 'wagent.degrade.whitelist.dg_k1',
   'enabled=true;applies_to=readiness.k,narrative.compose;requires_ack=true', 'string',
   '降级·缺历史画像先验', 'Degrade DG-K1 Missing Profile Prior',
   'DG-K1（§4.5 行 3）：画像缺失或 applicability 低于阈值 ⇒ 结论标注"缺历史画像先验，区间偏宽"，**需用户确认**；绑定 K 层 know.applicability_min（组 1）', 1, 'active'),
  ('cfg-wagent-dg-d1', 'wagent.degrade.whitelist', 'wagent.degrade.whitelist.dg_d1',
   'enabled=true;applies_to=step.data_sample,readiness.k;requires_ack=true', 'string',
   '降级·小样本', 'Degrade DG-D1 Small Sample',
   'DG-D1（§4.5 末行）：样本 n<30 ⇒ 展示样本量与区间；n<10 ⇒ 转趋势判断（不给区间）；requires_ack=true 与 §9.6 硬门槛"叙述数字与结果一致"同向', 2, 'active'),
  ('cfg-wagent-dg-c1', 'wagent.degrade.whitelist', 'wagent.degrade.whitelist.dg_c1',
   'enabled=true;applies_to=tool.kg_query;requires_ack=false', 'string',
   '降级·图库受限', 'Degrade DG-C1 Graph Limited',
   'DG-C1（§4.5 行 5）：图库不可用或查询受限 ⇒ 仅一层邻居 + 超时 3s（kb-engine POST /api/v1/kb/graph/query 侧行为，Agent 只读状态不自建限流）', 3, 'active'),
  ('cfg-wagent-dg-c2', 'wagent.degrade.whitelist', 'wagent.degrade.whitelist.dg_c2',
   'enabled=true;applies_to=tool.metric_caliber;requires_ack=true', 'string',
   '降级·口径受限', 'Degrade DG-C2 Caliber Limited',
   'DG-C2（§4.5 行 4）：列权限受限 ⇒ 规则/SQL 兜底并标注"口径降级"；兜底路径的裁决仍归 security-engine（禁 Agent 侧自判，铁律 §2.4-7 / W273）', 4, 'active'),
  ('cfg-wagent-dg-l1', 'wagent.degrade.whitelist', 'wagent.degrade.whitelist.dg_l1',
   'enabled=true;applies_to=narrative.compose;requires_ack=false', 'string',
   '降级·叙述模板化', 'Degrade DG-L1 Narrative Template',
   'DG-L1（§4.5 行 2）：llm-gateway 不可用 ⇒ 叙述段转模板化输出；**数字段不受影响**（数字来自 CM/事实，红线"数字不来自 LLM"，§0.2 行 2）', 5, 'active'),
  ('cfg-wagent-dg-t1', 'wagent.degrade.whitelist', 'wagent.degrade.whitelist.dg_t1',
   'enabled=true;applies_to=step.wait_task;requires_ack=true', 'string',
   '降级·异步任务超时', 'Degrade DG-T1 Task Timeout',
   'DG-T1（F10-15 + §4.4 重规划/唤醒链）：wait_task 超时经 runtime-monitor 定时探测置 timed_out ⇒ 结论按"任务未完成"降级出稿并需用户确认是否续跑；对账唤醒属 runtime-task 周期任务（禁自建调度，W255）', 6, 'active'),
  ('cfg-wagent-dg-m1', 'wagent.degrade.whitelist', 'wagent.degrade.whitelist.dg_m1',
   'enabled=true;applies_to=llm.route;requires_ack=false', 'string',
   '降级·模型路由抖动', 'Degrade DG-M1 Model Route Fallback',
   'DG-M1（§4.5 行 7）：路由抖动 ⇒ 回退链下一候选模型（Model Router 归卷 06 F06-18，本册不自建）；回退成功率门槛 ≥99% 属 §9.6 混沌层，载体未建 ⇒ 记"未执行"', 7, 'active'),
  ('cfg-wagent-dg-m2', 'wagent.degrade.whitelist', 'wagent.degrade.whitelist.dg_m2',
   'enabled=true;applies_to=llm.route,data_classification;requires_ack=true', 'string',
   '降级·密级路由受限', 'Degrade DG-M2 Classification Route',
   'DG-M2（§9.6 硬门槛"密级路由违规 0"的反面处置）：目标模型不满足数据密级 ⇒ 只能降级到合规候选并需用户确认；密级判定入参来自 security-engine CLS 策略（RLS/CLS 经 REST，禁引擎内实现）', 8, 'active')
ON CONFLICT (config_key) DO NOTHING;

-- ── 组 3/6: wagent.budget（Run 预算默认值，F10-11 / 卷 10 §6.4）─────────────────────────────
-- 口径：默认值来自本组，**租户可下调不可上调**（F10-11 明文）；耗尽 → 429 E-WA-BUDGET 并提示拆分问题。
-- 落库形态：`ecos_ai.ecos_wagent_run.budget_json`（V233）存本次 Run 解析后的预算快照，本组是解析源。
INSERT INTO sys_config (id, config_group, config_key, config_value, config_type, config_label, config_label_en, description, sort_order, status) VALUES
  ('cfg-wagent-budget-steps', 'wagent.budget', 'wagent.budget.max_steps', '60', 'number',
   '预算·Step 上限', 'Budget Max Steps',
   'F10-11 默认 60 Step（RunBudgetTest#stepCountOver60Terminates 的基准值）；超限即终止并出 completed_degraded/failed 标注，禁静默截断', 1, 'active'),
  ('cfg-wagent-budget-llm', 'wagent.budget', 'wagent.budget.max_llm_calls', '40', 'number',
   '预算·LLM 调用上限', 'Budget Max LLM Calls',
   'F10-11 默认 40 次 LLM 调用；计量唯一出口 = llm-gateway（ecos_control.sys_config 的 ecos.ai.session.token_limit 属会话面 V264，两者不互换）', 2, 'active'),
  ('cfg-wagent-budget-sync', 'wagent.budget', 'wagent.budget.sync_timeout_seconds', '60', 'number',
   '预算·同步超时(秒)', 'Budget Sync Timeout Seconds',
   'F10-11 同步 Run（POST /api/v1/wagent/runs 不带 Prefer）默认 60s；到点即 timed_out，HTTP 侧回 200 + 终态（禁把超时包成 500）', 3, 'active'),
  ('cfg-wagent-budget-async', 'wagent.budget', 'wagent.budget.async_timeout_minutes', '30', 'number',
   '预算·异步超时(分)', 'Budget Async Timeout Minutes',
   'F10-11 异步 Run（Prefer: respond-async ⇒ 202 + run_id）默认 30 分；与 data-engine"管道不执行超过 30 分钟同步任务"红线同值，超 30 分的取数一律转 wait_task（F10-15），不撑长本值', 4, 'active'),
  ('cfg-wagent-budget-replan', 'wagent.budget', 'wagent.budget.max_replan_rounds', '3', 'number',
   '预算·重规划轮上限', 'Budget Max Replan Rounds',
   '§4.4 重规划：从失败点之后重算 Plan 并重跑七项校验，replanCount ≤ 3（F10-12 PlanValidator），超限即 failed + E-WA-POLICY 可读理由', 5, 'active'),
  ('cfg-wagent-budget-refill', 'wagent.budget', 'wagent.budget.max_refill_rounds', '3', 'number',
   '预算·补齐循环轮上限', 'Budget Max Refill Rounds',
   'F10-09 缺口补齐复评循环 refillRound ≤ 3（ReadinessGapServiceTest#refillLoopCapsAtThreeRounds），超限输出缺口并转 awaiting_input', 6, 'active')
ON CONFLICT (config_key) DO NOTHING;

-- ── 组 4/6: wagent.toolset（工具集分组与渐进披露阈值，F10-17~F10-19）─────────────────────────
-- 成员清单必须与 V266 的 `ecos_ai.ecos_wagent_tool_contract.toolset` 列逐字一致（两处同批改，禁单侧漂移）；
-- 目录内**不存在**任何通用 CRUD / query_db 型工具（F10-19 明文排除，与卷 06 C138 同罪）。
INSERT INTO sys_config (id, config_group, config_key, config_value, config_type, config_label, config_label_en, description, sort_order, status) VALUES
  ('cfg-wagent-toolset-core', 'wagent.toolset', 'wagent.toolset.core',
   'query_project_profit,get_project_acceptance_status,check_metric_caliber,find_historical_profile,freeze_data_snapshot,submit_readiness_task', 'string',
   '工具集·core 事实与就绪', 'Toolset Core',
   '业务语义工具首批 12 个的 core 子集（读事实 + 口径核验 + 画像检索 + 快照冻结 + 就绪任务提交）；D/I/K 层 Readiness 探测与缺口补齐只用本集，逐工具契约见 ecos_ai.ecos_wagent_tool_contract（V266）', 1, 'active'),
  ('cfg-wagent-toolset-analytics', 'wagent.toolset', 'wagent.toolset.analytics',
   'run_profit_forecast,run_cognitive_model,select_knowledge_profiles', 'string',
   '工具集·analytics 认知计算', 'Toolset Analytics',
   '预测/诊断/画像选择子集（C 层确定性计算，数字一律来自 CM-01~05 或 Data 事实列，§0.2 行 2 红线）；Agent 侧不实现计算，只经 endpoint 调 cognitive-engine', 2, 'active'),
  ('cfg-wagent-toolset-action', 'wagent.toolset', 'wagent.toolset.action',
   'create_decision_option,create_action_draft,get_action_effect', 'string',
   '工具集·action 决策与行动', 'Toolset Action',
   '决策选项/行动草稿/效果回读子集；**发布不在本集**（发布唯一链路 = Candidate → CommitService → Git commit+tag，F10-23/W259；目录中无 publish/commit 型工具）；受 automation.max_level 三重上限约束', 3, 'active'),
  ('cfg-wagent-toolset-disclosure', 'wagent.toolset', 'wagent.toolset.disclosure.schema_budget_ratio',
   '0.30', 'number',
   '渐进披露·Schema 预算占比', 'Progressive Disclosure Schema Budget Ratio',
   'F10-18：已注册工具 Schema 总量 > 上下文预算 30% ⇒ 自动切换渐进披露（tool_search/tool_describe/tool_validate 三元元工具常驻，其余按需载入）；另硬约束 always_loaded 计数 < 10（ToolContractValidationTest#alwaysLoadedCountBelowTen）', 4, 'active')
ON CONFLICT (config_key) DO NOTHING;

-- ── 组 5/6: wagent.automation.max_level（自动化等级上限，F10-21）─────────────────────────────
-- 生效级 = min(ToolContract.min_level, Run.automation_ceiling, TenantPolicy.max_level)（附件 AC-03）；
-- **L3 无自动路径**：StepWriter 对 L3 强制 approval_token 非空，本组 l3_auto_allowed 恒 false 且配置不可改为 true。
INSERT INTO sys_config (id, config_group, config_key, config_value, config_type, config_label, config_label_en, description, sort_order, status) VALUES
  ('cfg-wagent-auto-dflt', 'wagent.automation.max_level', 'wagent.automation.max_level.tenant_default', 'L1', 'string',
   '自动化·租户默认上限', 'Automation Tenant Default Cap',
   'F10-21：渐进 shadow→assist→auto **默认 assist**（= L1 只读+建议、写一律走 Candidate），租户只能下调不可上调；本值与 WAgentEnums.AutomationLevel 冻结枚举一致（PRD→代码 enum→DDL CHECK 三段一致，卷 07 R-15 同类预防）', 1, 'active'),
  ('cfg-wagent-auto-commit', 'wagent.automation.max_level', 'wagent.automation.max_level.commit_ceiling', 'L2', 'string',
   '自动化·发布类动作上限', 'Automation Commit Ceiling',
   '涉写通道（publish/commit 语义）的**系统级**上限 L2 = 必须人工审批闸门（human_gate Step + approval_token，F10-13 探索规划同样强制人工确认）；探索型 Plan（plan_source=EXPLORED）无论本值如何都必须先过 human_gate 才可进 executing（硬约束写在 RunStateMachine.canTransition，不靠提示词）', 2, 'active'),
  ('cfg-wagent-auto-l3', 'wagent.automation.max_level', 'wagent.automation.max_level.l3_auto_allowed', 'false', 'boolean',
   '自动化·L3 自动路径开关', 'Automation L3 Auto Allowed',
   'F10-21 红线："**L3 无自动路径**"⇒ 本值恒 false 且**配置不可开**（置 true 亦被应用侧拒绝 + 记 E-WA-POLICY；AutomationLevelTest#l3NeverAutoTriggeredByConfig 的判据来源）；本行存在是为让"不可开"成为可核对的数据面事实，而非提供开关', 3, 'active'),
  ('cfg-wagent-auto-mode', 'wagent.automation.max_level', 'wagent.automation.max_level.mode_default', 'assist', 'string',
   '自动化·渐进模式默认', 'Automation Progress Mode Default',
   'F10-21 渐进三档（shadow|assist|auto）的租户默认位 = assist；shadow 只做影子运行不出建议，auto 仅在 L1 只读面允许且仍需 Kill Switch 可停（红线"运行可停止"§0.2 行 8）', 4, 'active')
ON CONFLICT (config_key) DO NOTHING;

-- ── 组 6/6: wagent.action_publish_target.adapter（行动发布目标适配器，F10-23 写通道白名单）────
-- 红线（§0.2 行 7 + F10-03(1)）：Agent **不直连库表**、不写他域表 ⇒ 每个 publish_target 只能绑一个
--   **既有**属主端点/底座接口；endpoint_url 逐条为 2026-10-10 只读实测的在位 Controller 映射（未实跑可达性）。
-- forbidden_targets 行 = 黑名单显式化（禁 query_db / JDBC / 对方表直写 / 引擎内反射），供门禁与 §9.6 安全层取数。
INSERT INTO sys_config (id, config_group, config_key, config_value, config_type, config_label, config_label_en, description, sort_order, status) VALUES
  ('cfg-wagent-adapt-task', 'wagent.action_publish_target.adapter', 'wagent.action_publish_target.adapter.task',
   'POST /api/v1/task/submit', 'string',
   '适配器·待办任务', 'Adapter Task Submit',
   'F10-09 缺口 type=task 与 F10-21 Kill Switch 后的续跑任务唯一落点：gateway TaskController:144 `POST /api/v1/task/submit`（runtime-task ITaskManagementService 库接口的 facade 形态）。**不建任务表、不自建调度**（W255/C237 M0 直改口径：调度一律经 runtime-task）；runtime-task 无 @RestController（X-9），跨进程 REST 假定不成立 ⇒ 本适配器只在 gateway 态成立（R-45）', 1, 'active'),
  ('cfg-wagent-adapt-action', 'wagent.action_publish_target.adapter', 'wagent.action_publish_target.adapter.scenario_action',
   'POST /api/v1/workspace/scenarios/{id}/actions', 'string',
   '适配器·场景行动', 'Adapter Scenario Action',
   '行动草稿发布到场景域行动面的目标端点：workspace ScenarioActionController:39（同文件 :63 提供 /actions/{actionId}/approve 审批闸门）。CommitService 经 CandidateCommitPort 调**属主**端点，Agent 不写 workspace 表（§6.3 读写边界）', 2, 'active'),
  ('cfg-wagent-adapt-onto', 'wagent.action_publish_target.adapter', 'wagent.action_publish_target.adapter.ontology_action',
   'POST /api/v1/ontology/actions/{id}/execute', 'string',
   '适配器·本体动作类型', 'Adapter Ontology Action Execute',
   '本体定义类候选（ActionType / 口径 / 映射）的属主执行面：ontology-engine OntologyActionApiController:56；语义/口径/知识类候选**拒绝批量接口**（BulkApproveGuardTest，F10-23 末段）', 3, 'active'),
  ('cfg-wagent-adapt-effect', 'wagent.action_publish_target.adapter', 'wagent.action_publish_target.adapter.effect_writeback',
   'POST /api/v1/datanet/facts/action-outcomes', 'string',
   '适配器·效果回灌（W→D 唯一管道）', 'Adapter Effect Writeback',
   'T10 结果回灌唯一通道 = data-engine 事实导入面（FactController:92，PUBLISHED 行重复回灌 → 409 ECOS-DATA-021）；**禁独立回灌路径**（§9.5 T10 行"只经 data-engine 唯一管道"，联动卷 02 唯一写通道 F02-05）', 4, 'active'),
  ('cfg-wagent-adapt-forbidden', 'wagent.action_publish_target.adapter', 'wagent.action_publish_target.adapter.forbidden_targets',
   'query_db,jdbc_direct,engine_table_direct_write,in_jvm_reflection,raw_sql_from_llm', 'string',
   '适配器·禁用目标黑名单', 'Adapter Forbidden Targets',
   '显式排除清单（F10-19"目录中不存在任何通用 CRUD 或 query_db 型工具"、§0.2 行 7"Agent 不直连库表"、W273/Z-7 小样本判定归 security-engine 策略项、R-18 禁同 JVM 反射）：endpoint_kind ∉ {rest} 或 engine ∈ 本清单 ⇒ ToolContractRegistry 注册期拒绝 + Policy Guard pre-step 二次拒绝；门禁标识 AgentToolCatalogTest#noGenericCrudOrRawSqlToolExposed', 5, 'active')
ON CONFLICT (config_key) DO NOTHING;

-- ── 注释登记 ──────────────────────────────────────────────
COMMENT ON TABLE sys_config IS '系统配置单表（config_group 分组，铁律 §十 配置单源）；W Agent 编排域六组运行期键见 config_group LIKE ''wagent.%''（V265，共 39 行：readiness.threshold 12 / degrade.whitelist 8 / budget 6 / toolset 4 / automation.max_level 4 / action_publish_target.adapter 5）';

-- ── 回滚说明 ──────────────────────────────────────────────
-- 数据回滚：应用侧读不到行即按**保守分支**回落（阈值缺 ⇒ 该 check 记 UNKNOWN 并计入缺项、定级不下探为 pass；
--           预算缺 ⇒ 取代码内 F10-11 冻结默认；降级白名单缺 ⇒ 该 DG 不可用（配置可关不可开新）；
--           automation 缺 ⇒ 按最严档 L0；publish 适配器缺 ⇒ 该 publish_target 拒绝写入）⇒ 回滚不产生隐性放行。
--           回滚动作 = 运维把本批 39 行 status 置 'inactive'（本脚本不 DROP、不 DELETE，IR03）。
-- 幂等性：INSERT ... ON CONFLICT (config_key) DO NOTHING ⇒ 重复执行零报错、零覆盖（不冲掉运维已改值）。
--         （V13:8 定义 config_key VARCHAR(128) NOT NULL UNIQUE ⇒ ON CONFLICT 目标即该唯一约束）
-- 键名唯一性：六组组名与全部 39 键字面取自卷 10 §6.4/§6.2/F10-08~F10-23 的冻结写法，
--         禁"顺手改名统一"（配置键与 API 同规 = 只增不改）。
-- 上调禁区：wagent.budget.* 与 wagent.automation.max_level.tenant_default 运维侧可**下调**不可上调；
--         wagent.automation.max_level.l3_auto_allowed 与 wagent.degrade.whitelist.* 的新增均被应用侧拒绝。

-- ── DDL Lint Self-audit ──────────────────────────────────────
-- [DR01] 目标表 sys_config 沿用既有单源脚本非限定形态（V13/V263/V264 同形态；实测解析 = ecos_control.sys_config，
--        search_path 首位即 ecos_control）；限定化属 schema 归位批 ✓
-- [DR02/DR03] 无新建表 ⇒ 前缀/版本后缀不适用 ✓
-- [DR04/DR05] 无新增列（仅数据行）；复合值用既有 config_value TEXT 承载，形态沿用 V263 k=v;k=v 先例，未引入 JSON 列 ✓
-- [DR06~DR08] 无新表新列 ⇒ 七基线列不适用；sys_config 既有列 created_at/updated_at 属现网 legacy 形态（IR03 不改名）✓
-- [MC01] 无新主键；id 沿用 'cfg-*' 串形态（既有族，≤64 字符实测最长 'cfg-wagent-thr-fc-d-cov'=19）✓
-- [MC02] 无 JSONB、无 *_json 列、无 cast ✓  [MC03] 无索引/分区/PG 专有语法 ✓
-- [金额] 本批 config_value 全部为开关/阈值/上限/端点串，**无金额语义值** ⇒ 不触发 ST03-A 逐列登记（§六 全量对账口径）
-- [ST03-A] 无敏感列：39 行值均为阈值/枚举/端点路径，非个人数据、不含任何密钥引用 ✓
-- [ST07/ST08] 落主控制 schema 的配置表（sysman 域），不入业务域 ecos_dw、不入 ecos_ai ✓
-- [ST09] 无 FOREIGN KEY、不触碰他域表 ✓
-- [跨域] W Agent 引擎侧零 sys_config 语句（经 SysConfigService 门面），本文件属 DDL 单源目录而非引擎代码 ✓
-- [IR02] 手动 psql 执行面（Flyway 禁用），本批只落文件、未实跑 ✓
-- [IR03] 不 DROP、不改名、不 DELETE、不 UPDATE 既有行 ✓
-- [C14.4] 与 V266 同批（seed 与工具目录两处成员清单必须逐字一致）；未新建 Kafka topic、未改业务代码 ✓
