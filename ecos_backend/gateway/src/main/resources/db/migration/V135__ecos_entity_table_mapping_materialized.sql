-- ============================================================
-- V135__ecos_entity_table_mapping_materialized.sql
-- 实体→DW 表映射契约新增实例化开关列 materialized（Q2 裁决）
-- ============================================================
-- 来源: 肖国荣 / 日期: 2026-09-19 / 批次: B3-1（DW 实例读取 + 映射契约查询）
-- 依据: docs/plans/knowledge-workbench-replan-v1.md
--       §2.1 实例化范围（Q2 裁决）+ §8 开放问题裁决 Q2 + 附录 A D2
--
-- 背景:
--   Q2 裁决 —— 图谱实例化范围「以映射存在性为准（有映射即可实例化）」，
--   映射表新增 materialized 布尔列（默认 true）用于**显式关闭**某实体的实例化。
--   无映射实体（接口/动作等）无 DW 实例来源，强行实例化只产空节点并放大存储（风险 R4）。
--
-- 语义:
--   materialized = TRUE  → 该实体参与图谱实例化（默认，历史数据自动为 TRUE）
--   materialized = FALSE → 显式关闭该实体的图谱实例化
--
-- Schema 铁律: 只加不删（铁律 3.1）+ ADD COLUMN IF NOT EXISTS 幂等
-- 执行方式: Flyway 已禁用（spring.flyway.enabled=false），本脚本为规范记录，
--           需人工执行到目标库（sys_man）后方可生效。
--
-- 表定位: public.ecos_entity_table_mapping（本体工作台映射契约，D8/Q2）
-- ============================================================

ALTER TABLE public.ecos_entity_table_mapping
    ADD COLUMN IF NOT EXISTS materialized BOOLEAN NOT NULL DEFAULT TRUE;

COMMENT ON COLUMN public.ecos_entity_table_mapping.materialized
    IS '是否参与图谱实例化: TRUE=参与(默认), FALSE=显式关闭 (Q2 裁决, B3-1)';
