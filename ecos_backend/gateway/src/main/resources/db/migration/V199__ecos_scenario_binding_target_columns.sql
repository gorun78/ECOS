-- V199 (卷05 附录 E.3 / W127 收敛): 场景绑定表补列 target_id / target_type（结构性修复，"补列"分支按契约定版）
-- 追溯: W127/C109（ScenarioService.java:349 INSERT 引用不存在列 target_id/target_type → 结构必败 M0，X-32）；需求依据 IR05、§四附则1；F 项归属 E.3
-- 批准: 需求检视报告 §十四 14.1（2026-09-29）之 R-13 ①／R-15 ①／R-17 ① 同批授权面（E.3 迁移号 V199）；执行边界 §14.4 = 脚本文件落地
-- 状态: 仅落迁移脚本文件，**未对 ecos-postgres 实跑**（实跑属 §14.4 未授权项，需逐项再确认）
-- 纪律: DR01~DR08 / MC01~MC03 / ST03-A / ST07 / IR02(手动 psql) / IR03(只加不删)
--
-- ── "补列 or 改语句"二选一，以契约为准（E.3 原文）────────────
-- 契约与代码注释（ScenarioService.java:345 "v2.0: targetId 优先，targetRef 兜底；target_type 真资源类型"）
-- 均以 target_id/target_type 为 v2.0 绑定契约字段 ⇒ 取**补列**分支，INSERT 语句不改（改语句属代码侧，
-- 且会使 DTO getTargetId/getTargetType 契约字段无处安放）。
-- 漂移说明：V147 已声明同名补列（迁移文件面存在），但文档实测库内缺该两列（迁移滞后/漂移面）——
-- 本脚本以幂等 ADD COLUMN IF NOT EXISTS 兜底补列，使"执行到 V199"即保证列存在，
-- 与 V147 形态严格一致（VARCHAR(64)/VARCHAR(32)，见 V147:8-9），两条路径收敛同形、互不打架。
-- 注: V147 附带的 chk_binding_target_type CHECK 属存量声明，本脚本**不新建 CHECK**（R-15 同纪律）。

-- ── 1. 补列（幂等；宽度与 V147 声明一致以保证双路径同形）──────
ALTER TABLE public.ecos_scenario_binding
    ADD COLUMN IF NOT EXISTS target_id VARCHAR(64);
ALTER TABLE public.ecos_scenario_binding
    ADD COLUMN IF NOT EXISTS target_type VARCHAR(32);

COMMENT ON COLUMN public.ecos_scenario_binding.target_id   IS 'v2.0 真 PG 主键目标（替代 target_ref 字符串别名；target_ref 保留只读兼容，与 V147 注释同源口径）';
COMMENT ON COLUMN public.ecos_scenario_binding.target_type IS '真资源类型：DATASOURCE/ONTOLOGY_ENTITY/KNOWLEDGE_ARTICLE/AGENT_PROFILE/SECURITY_POLICY/INTERFACE_REF（枚举校验先由应用层白名单把关）';

-- ── 2. 热路径索引（普通索引，无条件子句 MC03；与 V147 同名索引 IF NOT EXISTS 幂等共存）──
CREATE INDEX IF NOT EXISTS idx_scenario_bind_tid
    ON public.ecos_scenario_binding (target_id);

-- ── 3. 幂等与回滚说明 ────────────────────────────────────────
-- 幂等: ADD COLUMN IF NOT EXISTS / CREATE INDEX IF NOT EXISTS / COMMENT 重复覆盖同值，重跑零副作用。
-- 回滚: 只加列加索引，不动既有列与既有数据；旧 INSERT 路径（不含该两列）列可空不受影响，
--       撤销=应用侧回退语句即可，库面对象按 IR03 保留（禁 DROP）。

-- ── DDL Lint Self-audit ──────────────────────────────────────
-- [DR01] schema 限定 + 小写下划线 ✓  [DR05/DR06] 未新增布尔/审计列（沿用既有表基线列）✓
-- [MC02] 无二进制 JSON 列、无裸 NUMERIC ✓  [MC03] 无条件索引/无 RLS/无分区/无数组列/无时区戳列/无裸 cast/无数值序列化语法 ✓
-- [ST03-A] target_id/target_type 均字符串列，无金额/比率/概率列 ✓  [ST07] 操作对象属主控制 schema public 存量表原地补列（白名单内）✓
-- [IR03] 只加不删：不改列宽、不 DROP、不改语句 ✓  [幂等] ✓  [回滚说明] 已附 ✓
