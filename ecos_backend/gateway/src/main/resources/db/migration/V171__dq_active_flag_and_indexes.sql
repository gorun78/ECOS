-- V171 (卷02 §E.5): DQ 治理侧 is_active 补齐 + 评分/规则辅助索引（事实侧 V167 已含，本脚本不重复；只加）
-- 追溯: W45→C34、W48→C37 收尾索引面 / F02-06-3（唯一活跃列方案，同 §E.2 差异⑤）；需求依据 REQ-DATA-02/04
-- 批准: 需求检视报告 §十四 14.1（2026-09-29）之 R-1b ②（DQ 治理表 ecos_data/ecos_dq 承流口径）/R-1 a；执行边界 §14.4 = 脚本文件落地
-- 状态: 仅落迁移脚本文件，**未对 ecos-postgres 实跑**（实跑属 §14.4 未授权项，需逐项再确认）
-- 纪律: DR01~DR08 / MC01~MC03 / ST03-A / ST07 / IR02(手动 psql) / IR03(只加不删)
-- 范围界定:
--   ① 事实/快照五表的 is_active 与 (… , is_active) 组合唯一索引 **已由 V167 落地** → 本脚本不重复添加；
--   ② DQ 治理规则表 ecos_dq.dq_rule（V111 既有承流表，E.3「只加列」同法）补 is_active（DRAFT→ACTIVE 生命周期
--      的「当前活跃」承载列，替代表达式/条件索引形态）；
--   ③ V165.1 新表 ecos_data.ecos_dq_score_asset 补等级扫描/趋势索引（对应 /api/v1/dq/scores/grade 与 trend 读路径）。
-- 幂等: ADD COLUMN IF NOT EXISTS / CREATE INDEX IF NOT EXISTS，重跑无副作用。
-- 回滚说明: 只加列只加索引，无数据改写；回滚=业务侧不消费 is_active（禁 DROP，IR03）。
--
-- 【2026-09-30 校订三处】① 列名收口 DR05：文档字面 active_flag → is_active（本批新表新列，库内与 Java/XML 零引用，
--     改名无兼容代价；V167 同批同改）。② 表名收口 DR02：V165.1 新表定名 ecos_dq_score_asset（原 ecos_data.dq_score_asset
--     无 ecos_ 前缀）。③ 删除 idx_dq_rule_legacy_r171：与 V166 §1 的 idx_dq_rule_legacy 同列重复建索引（冗余写放大）。

-- ── 1. DQ 治理规则表 is_active（SMALLINT，DR05；默认 1=活跃候选，签核流程置 0）──
ALTER TABLE ecos_dq.dq_rule ADD COLUMN IF NOT EXISTS is_active SMALLINT NOT NULL DEFAULT 1;

-- ── 2. 辅助索引（全部非条件索引，MC03 ✓）──────────────────
CREATE INDEX IF NOT EXISTS idx_dq_rule_status_active ON ecos_dq.dq_rule(status, is_active);
CREATE INDEX IF NOT EXISTS idx_ecos_dq_score_asset_grade ON ecos_data.ecos_dq_score_asset(grade, snapshot_at);
CREATE INDEX IF NOT EXISTS idx_ecos_dq_score_asset_time  ON ecos_data.ecos_dq_score_asset(snapshot_at);

-- ── DDL Lint Self-audit ──────────────────────────────────
-- [DR01] ecos_dq.dq_rule（既有承流表，只加列/索引，E.3 口径）/ ecos_data.ecos_dq_score_asset（V165.1 新表）均 schema 限定 ✓
-- [DR02] 所引新表带 ecos_ 前缀 ✓  [DR05] is_active SMALLINT ✓（文档字面 active_flag 已收口）
-- [MC02] 无新增数值/JSON 列 ✓
-- [MC03] 三条索引均**无 WHERE 子句**（非 partial，替代 V111 既有条件索引的新增面）✓；无库内策略语句、无分区、
--        无数组列、无带时区时间类型、无裸 cast ✓；索引命名 idx_{表名}_{字段} ✓；无与 V166 重复的同列索引 ✓
-- [ST07] 未新建 schema；新索引仅落白名单 schema（ecos_data）与既有承流表（ecos_dq，只加不迁 R-1b ②）✓
-- [ST09] 零 FOREIGN KEY ✓  [IR03] 只加不删；不改写既有数据（默认值由 DDL 承担）✓
