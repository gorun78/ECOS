-- V169 (卷02 §E.4): 五层载体登记扩展 — td_data_resource 只加列（layer_bucket/carrier_ref/storage_kind/declared_flag）+ 非条件唯一索引
-- 追溯: W51→C40（SEMANTIC/APPLICATION 载体 0，LayerCarrierPresenceTest）；需求依据 REQ-DATA-03/06、《数据湖存储分层规范》v2.0 五层
-- 批准: 需求检视报告 §十四 14.1（2026-09-29）之 R-1 a（数据域控制表权威归属 ecos_data）；执行边界 §14.4 = 脚本文件落地
-- 状态: 仅落迁移脚本文件，**未对 ecos-postgres 实跑**（实跑属 §14.4 未授权项，需逐项再确认）
-- 纪律: DR01~DR08 / MC01~MC03 / ST03-A / ST07 / IR02(手动 psql) / IR03(只加不删)
-- 只加不删: 本脚本对 ecos_data.td_data_resource **仅加列 + 加索引**，不改既有列型/列名、不删任何对象。
-- 索引形态: (layer, carrier_ref, storage_kind, is_deleted) **非条件唯一索引**（E.4 字面即为组合唯一键，MC03 合规）。
-- 前置加列说明: ecos_data.td_data_resource 镜像形态（ecos-sql/postgresql/03_ecos_data.sql）缺 layer/is_deleted
--   （public 承数侧由 V54/V133 已补），唯一索引依赖二者 → 本脚本以 IF NOT EXISTS 同构补齐（与 V54 口径一致），仍属"只加"。
-- 后续批次登记: E.4 要求的「对 SEMANTIC/APPLICATION 各插入 ≥1 行载体登记」为**数据动作**，须携带真实
--   datasource/resource 键，归 /api/v1/datanet/metadata/resources 注册链路（F02-05 业务批次）与 seed 批次，本批不实插（禁造演示数据）。
-- 幂等: ADD COLUMN IF NOT EXISTS / CREATE ... IF NOT EXISTS，重跑无副作用。回滚说明: 列与索引保留不删（IR03），业务侧停用 declared_flag 消费即可。

-- ── 1. 载体登记列（E.4 四列）──────────────────────────────
ALTER TABLE ecos_data.td_data_resource ADD COLUMN IF NOT EXISTS layer_bucket  VARCHAR(30);
ALTER TABLE ecos_data.td_data_resource ADD COLUMN IF NOT EXISTS carrier_ref   VARCHAR(255);
ALTER TABLE ecos_data.td_data_resource ADD COLUMN IF NOT EXISTS storage_kind  VARCHAR(20);
ALTER TABLE ecos_data.td_data_resource ADD COLUMN IF NOT EXISTS declared_flag SMALLINT NOT NULL DEFAULT 1;

-- ── 2. 唯一索引依赖的基础列补齐（镜像缺列，同构 V54/V133/DR06 口径，只加）──
ALTER TABLE ecos_data.td_data_resource ADD COLUMN IF NOT EXISTS layer     VARCHAR(16) DEFAULT 'RAW';
ALTER TABLE ecos_data.td_data_resource ADD COLUMN IF NOT EXISTS zone      VARCHAR(16);
ALTER TABLE ecos_data.td_data_resource ADD COLUMN IF NOT EXISTS is_deleted SMALLINT   NOT NULL DEFAULT 0;

-- ── 3. 载体登记唯一索引（非条件索引，MC03 ✓）───────────────
CREATE UNIQUE INDEX IF NOT EXISTS uniq_tdr_layer_carrier
    ON ecos_data.td_data_resource(layer, carrier_ref, storage_kind, is_deleted);
CREATE INDEX IF NOT EXISTS idx_tdr_layer_bucket ON ecos_data.td_data_resource(layer_bucket);
CREATE INDEX IF NOT EXISTS idx_tdr_declared     ON ecos_data.td_data_resource(declared_flag);

-- ── DDL Lint Self-audit ──────────────────────────────────
-- [DR01] ecos_data. 限定（无裸表名）✓  [DR05] declared_flag/is_deleted SMALLINT ✓
-- [MC02] 无裸 NUMERIC/无 JSON 二进制列（carrier_ref/storage_kind 均 VARCHAR）✓
-- [MC03] 唯一索引与非条件辅助索引均**无 WHERE 子句**（非 partial）✓；无库内策略语句、无分区、无数组列、
--        无带时区时间类型、无裸 cast ✓
-- [ST07] 目标表落数据引擎 schema ecos_data（R-1 a 终态）✓；public.td_data_resource = 承数侧停写不动 ✓
-- [ST09] 零 FOREIGN KEY ✓  [IR03] 只加列只加索引，不删不改不重建 ✓
