-- V241 (本机联调): td_datasource 补齐 is_default / remark 两列 — 关闭 data-engine DataSourceServiceImpl.mapRow
--   (c86e8c80 起读 is_default；remark 见 DataSourceMapper.xml insert/update) 与现网 DDL 的漂移。
--   症状: GET /datanet/datasource 500 "ResultSet 中找不到栏位名称 is_default"（本机 2026-09-30 实测 public.td_datasource 18 列无此二列）。
-- 纪律: IR02(手动 psql) / IR03(只加列，不删不改类型) / DR05(is_ 前缀) / DR06(is_deleted SMALLINT 同形态，故 is_default 用 SMALLINT 非 BOOLEAN，MC02 白名单内)
-- 归属: td_datasource 为存量表（public 现网形态；ecos_data 为 V47 隔离目标态）— 两处按存在性各自增列，不搬表（ST07 knownLegacy 口径）
-- 幂等: ADD COLUMN IF NOT EXISTS，可重复执行

DO $$
DECLARE
    sch TEXT;
BEGIN
    FOREACH sch IN ARRAY ARRAY['public', 'ecos_data'] LOOP
        IF EXISTS (SELECT 1 FROM information_schema.tables
                   WHERE table_schema = sch AND table_name = 'td_datasource') THEN
            EXECUTE format(
                'ALTER TABLE %I.td_datasource ADD COLUMN IF NOT EXISTS is_default SMALLINT NOT NULL DEFAULT 0', sch);
            EXECUTE format(
                'ALTER TABLE %I.td_datasource ADD COLUMN IF NOT EXISTS remark TEXT', sch);
            RAISE NOTICE 'V241: %.td_datasource += is_default, remark', sch;
        END IF;
    END LOOP;
END $$;

COMMENT ON COLUMN public.td_datasource.is_default IS '是否默认数据源: 0=否/1=是 (应用侧 String 读写, MC02 白名单形态)';
COMMENT ON COLUMN public.td_datasource.remark     IS '备注';
