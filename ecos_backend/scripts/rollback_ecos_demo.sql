-- ============================================================================
-- ecos_demo 一键回滚脚本（T2）
-- ----------------------------------------------------------------------------
-- 用途
--   回滚上一批次导入 sys_man.ecos_demo 的 197 张业务表（MySQL 源库 touzi 转换而来），
--   **只删非 dw_ 前缀的表**，保留 11 张原有 dw_* 演示表（92 行）不动。
--   与 ecos_demo_indexes.sql 配套：DROP TABLE ... CASCADE 会连带删除表上的全部索引，
--   无需先单独删索引。
--
-- 前置条件
--   1. 目标库为 ecos-postgres 容器内的 sys_man；执行者确认「确实要回滚」——
--      本脚本是破坏性操作，不可撤销，执行前请自行备份（pg_dump -n ecos_demo）。
--   2. 已确认无需保留 ecos_demo 中 197 张业务表的任何数据。
--   3. 仅作用于 ecos_demo schema；不触碰 public 及其他 schema 的任何对象。
--
-- 执行方式
--   docker cp rollback_ecos_demo.sql ecos-postgres:/tmp/ && \
--   docker exec ecos-postgres psql -U postgres -d sys_man -f /tmp/rollback_ecos_demo.sql
--
-- 预期结果
--   执行完打印 remaining_tables = 11（仅剩 dw_* 演示表）。
-- ============================================================================

DO $$
DECLARE
  r record;
BEGIN
  -- 只遍历非 dw_ 前缀的表；'dw\_%' 中反斜杠为 LIKE 默认转义符，匹配字面量 dw_ 前缀
  FOR r IN SELECT table_name FROM information_schema.tables
           WHERE table_schema = 'ecos_demo' AND table_name NOT LIKE 'dw\_%' LOOP
    EXECUTE format('DROP TABLE IF EXISTS ecos_demo.%I CASCADE', r.table_name);
  END LOOP;
END $$;

-- 收尾打印剩余表数（正常应为 11）
SELECT count(*) AS remaining_tables
FROM information_schema.tables
WHERE table_schema = 'ecos_demo';
