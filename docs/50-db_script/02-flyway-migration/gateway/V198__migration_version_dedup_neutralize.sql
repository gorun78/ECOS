-- ============================================================
-- V198__migration_version_dedup_neutralize.sql
-- 详细设计-05 E.3 / X-28 / W126：
--   V150/V155 目录内版本号重复，仅因 spring.flyway.enabled=false 未暴露。
--   本批**不重命名文件**（会导致既有 checksum 变化触发下游 break），
--   仅落一份**空操作**对账脚本，作为 MigrationVersionUniquenessTest
--   （F05-13 落地门禁）的登记点：本文件存在即"已识别 + 待治理"证明。
--
-- 治理路径（人工执行）：
--   1) 手工执行 `MigrationVersionUniquenessTest` 门禁失败告警
--   2) 与既有 V150__...doc_anchor_json.sql + V150__ecos_scenario_sandbox_layout.sql
--      对读，取一保留、另一重编号（phase 2 独立 PR）
--   3) 本脚本删除（本 DDL 无实际动作，只作登记占位）
-- ============================================================

-- 幂等空动作。若实库已有 equiv 目录扫描 FLYWAY_SCHEMA_HISTORY 校验：
SELECT to_regclass('public.flyway_schema_history') AS flyway_probe;
