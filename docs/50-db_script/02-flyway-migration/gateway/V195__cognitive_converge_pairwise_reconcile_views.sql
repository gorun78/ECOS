-- V195 (卷05 附录 E.3 / R-13 ① 分批收敛 第5批·收官): 认知真身 14 张收敛总登记 + 镜像↔真身逐对行级对账视图（v_conv_* 只读）
-- 追溯: W124/C106、W125/C107（E.3 V190~V195 分批收敛的验收面）；X-23（14 张计数清单）、X-29（迁移↔实库漂移）
-- 需求依据: REQ-COG-05；文档编写/数据库访问规范：对账视图形态沿用 V213 先例（v_legacy_*/只读、不接业务链路）
-- 批准: 需求检视报告 §十四 14.1 之 R-13 ①；执行边界 §14.4 = 脚本文件落地（**不实跑库**）
-- 纪律: DR01~DR08 / MC01~MC03 / ST07 / IR02(手动 psql) / IR03(只加不删)
-- 前置: 本脚本视图引用 V191~V193 所建 ecos_cognitive 镜像表——单源按版本号顺序执行天然满足；手动 psql 单跑本文件前须先确认 V191~V193 已执行（IR02 人工核对，禁脚本内 IF NOT EXISTS 兜底建表改形）。
--
-- ──────────────────────────────────────────────────────────
-- 认知真身 14 张收敛总账（X-23 计数清单 ↔ 批次落点）：
--   1  public.ecos_cognitive_hypothesis      → ecos_cognitive 镜像+复制  V191（过渡列 V190）
--   2  public.ecos_cognitive_belief          → 同上                      V191（过渡列 V190）
--   3  public.ecos_cognitive_evidence        → 同上                      V191
--   4  public.ecos_cognitive_model           → ecos_cognitive 镜像+复制  V192
--   5  public.ecos_cognitive_run_invalidation→ 同上                      V192
--   6  public.kb_cognitive_pipeline          → ecos_cognitive.ecos_cognitive_pipeline（**V163 已收编**，V192 停写登记；guid 前置 V196）
--   7  public.ecos_scenario_run              → 产物轨道 **V197**（R-17 ①：ecos_dw.ecos_application_forecast_run，只停写不镜像，避免双权威）
--   8  public.ecos_business_scenario         → ecos_cognitive 镜像+复制  V193
--   9  public.ecos_scenario_binding          → 同上                      V193
--   10 public.ecos_scenario_binding_link     → 同上                      V193
--   11 public.ecos_wm_scenario               → 登记+限定读声明           V194（镜像待 §14.4 授权批；guid V196）
--   12 public.ecos_world_scenarios           → 同上                      V194（guid 不适用：V10 PK 为 VARCHAR(64)）
--   13 public.ecos_world_scenario（单数）    → 漂移登记                  V194（单源无 CREATE，列面不可证 ⇒ 镜像暂缓，前置=单源补登 DDL）
--   14 public.ecos_world_scenario_impact     → 漂移登记                  V194（同上；X-23 "_impact 10" 对应名的唯一 corpus 可证候选）
-- 口径申明：设计-05 称"14 张"，X-23 计数清单以缩写列出 13 项（world_scenario/_impact 连写歧义）；本账按
--   「清单 13 项 + 盘点 §B 单数 ecos_world_scenario」取 14 全登记，不可证两张漂移表**不并建镜像**（防凭记忆手写列面），
--   实差属 R-13 权威清单执行细案待裁面。
-- ──────────────────────────────────────────────────────────

-- ── 1. 镜像↔真身逐对对账视图（8 对，V191~V193 产物；只读，不接业务链路）──
-- 判读：missing_in_target>0 ⇒ 存量复制未跑全/旧表新增行未双写；orphan_in_target>0 ⇒ 目标侧有真表无之行（禁 DELETE，清理=置 is_deleted=1 人工裁决）。
CREATE OR REPLACE VIEW public.v_conv_cognitive_belief AS
SELECT 'public.ecos_cognitive_belief' AS source_table,
       'ecos_cognitive.ecos_cognitive_belief' AS target_table,
       (SELECT COUNT(1) FROM public.ecos_cognitive_belief) AS source_rows,
       (SELECT COUNT(1) FROM ecos_cognitive.ecos_cognitive_belief) AS target_rows,
       (SELECT COUNT(1) FROM public.ecos_cognitive_belief o
         WHERE NOT EXISTS (SELECT 1 FROM ecos_cognitive.ecos_cognitive_belief n WHERE n.id = o.id)) AS missing_in_target,
       (SELECT COUNT(1) FROM ecos_cognitive.ecos_cognitive_belief n
         WHERE NOT EXISTS (SELECT 1 FROM public.ecos_cognitive_belief o WHERE o.id = n.id)) AS orphan_in_target;

CREATE OR REPLACE VIEW public.v_conv_cognitive_hypothesis AS
SELECT 'public.ecos_cognitive_hypothesis' AS source_table,
       'ecos_cognitive.ecos_cognitive_hypothesis' AS target_table,
       (SELECT COUNT(1) FROM public.ecos_cognitive_hypothesis) AS source_rows,
       (SELECT COUNT(1) FROM ecos_cognitive.ecos_cognitive_hypothesis) AS target_rows,
       (SELECT COUNT(1) FROM public.ecos_cognitive_hypothesis o
         WHERE NOT EXISTS (SELECT 1 FROM ecos_cognitive.ecos_cognitive_hypothesis n WHERE n.id = o.id)) AS missing_in_target,
       (SELECT COUNT(1) FROM ecos_cognitive.ecos_cognitive_hypothesis n
         WHERE NOT EXISTS (SELECT 1 FROM public.ecos_cognitive_hypothesis o WHERE o.id = n.id)) AS orphan_in_target;

CREATE OR REPLACE VIEW public.v_conv_cognitive_evidence AS
SELECT 'public.ecos_cognitive_evidence' AS source_table,
       'ecos_cognitive.ecos_cognitive_evidence' AS target_table,
       (SELECT COUNT(1) FROM public.ecos_cognitive_evidence) AS source_rows,
       (SELECT COUNT(1) FROM ecos_cognitive.ecos_cognitive_evidence) AS target_rows,
       (SELECT COUNT(1) FROM public.ecos_cognitive_evidence o
         WHERE NOT EXISTS (SELECT 1 FROM ecos_cognitive.ecos_cognitive_evidence n WHERE n.id = o.id)) AS missing_in_target,
       (SELECT COUNT(1) FROM ecos_cognitive.ecos_cognitive_evidence n
         WHERE NOT EXISTS (SELECT 1 FROM public.ecos_cognitive_evidence o WHERE o.id = n.id)) AS orphan_in_target;

CREATE OR REPLACE VIEW public.v_conv_cognitive_model AS
SELECT 'public.ecos_cognitive_model' AS source_table,
       'ecos_cognitive.ecos_cognitive_model' AS target_table,
       (SELECT COUNT(1) FROM public.ecos_cognitive_model) AS source_rows,
       (SELECT COUNT(1) FROM ecos_cognitive.ecos_cognitive_model) AS target_rows,
       (SELECT COUNT(1) FROM public.ecos_cognitive_model o
         WHERE NOT EXISTS (SELECT 1 FROM ecos_cognitive.ecos_cognitive_model n WHERE n.id = o.id)) AS missing_in_target,
       (SELECT COUNT(1) FROM ecos_cognitive.ecos_cognitive_model n
         WHERE NOT EXISTS (SELECT 1 FROM public.ecos_cognitive_model o WHERE o.id = n.id)) AS orphan_in_target;

CREATE OR REPLACE VIEW public.v_conv_cognitive_run_invalidation AS
SELECT 'public.ecos_cognitive_run_invalidation' AS source_table,
       'ecos_cognitive.ecos_cognitive_run_invalidation' AS target_table,
       (SELECT COUNT(1) FROM public.ecos_cognitive_run_invalidation) AS source_rows,
       (SELECT COUNT(1) FROM ecos_cognitive.ecos_cognitive_run_invalidation) AS target_rows,
       (SELECT COUNT(1) FROM public.ecos_cognitive_run_invalidation o
         WHERE NOT EXISTS (SELECT 1 FROM ecos_cognitive.ecos_cognitive_run_invalidation n WHERE n.id = o.id)) AS missing_in_target,
       (SELECT COUNT(1) FROM ecos_cognitive.ecos_cognitive_run_invalidation n
         WHERE NOT EXISTS (SELECT 1 FROM public.ecos_cognitive_run_invalidation o WHERE o.id = n.id)) AS orphan_in_target;

CREATE OR REPLACE VIEW public.v_conv_business_scenario AS
SELECT 'public.ecos_business_scenario' AS source_table,
       'ecos_cognitive.ecos_business_scenario' AS target_table,
       (SELECT COUNT(1) FROM public.ecos_business_scenario) AS source_rows,
       (SELECT COUNT(1) FROM ecos_cognitive.ecos_business_scenario) AS target_rows,
       (SELECT COUNT(1) FROM public.ecos_business_scenario o
         WHERE NOT EXISTS (SELECT 1 FROM ecos_cognitive.ecos_business_scenario n WHERE n.id = o.id)) AS missing_in_target,
       (SELECT COUNT(1) FROM ecos_cognitive.ecos_business_scenario n
         WHERE NOT EXISTS (SELECT 1 FROM public.ecos_business_scenario o WHERE o.id = n.id)) AS orphan_in_target;

CREATE OR REPLACE VIEW public.v_conv_scenario_binding AS
SELECT 'public.ecos_scenario_binding' AS source_table,
       'ecos_cognitive.ecos_scenario_binding' AS target_table,
       (SELECT COUNT(1) FROM public.ecos_scenario_binding) AS source_rows,
       (SELECT COUNT(1) FROM ecos_cognitive.ecos_scenario_binding) AS target_rows,
       (SELECT COUNT(1) FROM public.ecos_scenario_binding o
         WHERE NOT EXISTS (SELECT 1 FROM ecos_cognitive.ecos_scenario_binding n WHERE n.id = o.id)) AS missing_in_target,
       (SELECT COUNT(1) FROM ecos_cognitive.ecos_scenario_binding n
         WHERE NOT EXISTS (SELECT 1 FROM public.ecos_scenario_binding o WHERE o.id = n.id)) AS orphan_in_target;

CREATE OR REPLACE VIEW public.v_conv_scenario_binding_link AS
SELECT 'public.ecos_scenario_binding_link' AS source_table,
       'ecos_cognitive.ecos_scenario_binding_link' AS target_table,
       (SELECT COUNT(1) FROM public.ecos_scenario_binding_link) AS source_rows,
       (SELECT COUNT(1) FROM ecos_cognitive.ecos_scenario_binding_link) AS target_rows,
       (SELECT COUNT(1) FROM public.ecos_scenario_binding_link o
         WHERE NOT EXISTS (SELECT 1 FROM ecos_cognitive.ecos_scenario_binding_link n WHERE n.id = o.id)) AS missing_in_target,
       (SELECT COUNT(1) FROM ecos_cognitive.ecos_scenario_binding_link n
         WHERE NOT EXISTS (SELECT 1 FROM public.ecos_scenario_binding_link o WHERE o.id = n.id)) AS orphan_in_target;

COMMENT ON VIEW public.v_conv_cognitive_belief IS 'V190~V195 收敛对账（只读，禁接业务链路，V213 同纪律）';

-- ── 回滚说明 ──────────────────────────────────────────────
-- CREATE OR REPLACE VIEW 幂等可重跑；回滚 = DROP VIEW 属人工裁决（IR03 本脚本零 DROP）。

-- ── DDL Lint Self-audit ──────────────────────────────────
-- [DR01] 视图/表引用全 schema 限定 ✓  视图名 v_conv_* 只读对账面（V213 先例）✓
-- [MC01/MC02] 无表列变化 ✓  [MC03] COUNT(1) 无 SELECT * 裸形、无 ILIKE/RETURNING/ON CONFLICT ✓
-- [IR02] 手动 psql（前置=V191~V193 已执行）✓  [IR03] 只加不删 ✓
-- [R-13 ①] V190~V195 分批收官：14 张全登记、10 张有可执行轨道（镜像/收编/产物）、4 张待授权或漂移补登面，零遗漏零 DROP ✓
