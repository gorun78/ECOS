-- V247 (F02-01 W52 数据域 P0 安全): td_datasource 补密文凭据列 — 对齐 data-engine 代码读写口径
-- 追溯: W52→C41（P0 安全：数据源凭证明文入库）；关联 V164.1（占位哨兵列 credential_encrypted）
-- 背景: DataSourceServiceImpl 运行态读写「凭据列」的实际列名 = password_enc
--   （见 register/updateDataSource 的 INSERT/UPDATE 列清单、mapRow 的 readPasswordEnc()），
--   但历史 DDL 从未落该列 —— 与 V164.1 新加的 credential_encrypted（仅写 __PENDING_REENCRYPT__
--   哨兵、业务层未消费）形成 drift。本脚本把 password_enc 就位于两处 schema（只加不删），
--   使「写→读→脱敏→启动期重加密迁移器」链路对真实库列完整闭合；credential_encrypted 的
--   业务消费口径收敛到 password_enc（F02-01 迁移器写入 password_enc，读路径解密回填同源）。
-- 纪律: IR02(手动 psql) / IR03(只加列不删，两列并存，不迁移重命名) / DR01(schema 限定)
--       / DR06(密文 TEXT 无大数值无 JSON 二进制，MC02 白名单形态) / ST03(控制域凭据加密，ST03-A 不豁免)
-- 归属: td_datasource 现网承数在 public，ecos_data 为引擎控制域隔离目标 —— 两处按表存在性各自增列（V241 同法）
-- 幂等: ADD COLUMN IF NOT EXISTS + 表存在性守卫，可重复执行
-- 安全: password_enc 为 AES-256-GCM 密文（非明文），仅存储引用密文，不承载密钥材料

DO $$
DECLARE
    sch TEXT;
BEGIN
    FOREACH sch IN ARRAY ARRAY['public', 'ecos_data'] LOOP
        IF EXISTS (SELECT 1 FROM information_schema.tables
                   WHERE table_schema = sch AND table_name = 'td_datasource') THEN
            EXECUTE format(
                'ALTER TABLE %I.td_datasource ADD COLUMN IF NOT EXISTS password_enc TEXT', sch);
            EXECUTE format(
                'ALTER TABLE %I.td_datasource ADD COLUMN IF NOT EXISTS credential_encrypted TEXT', sch);
            RAISE NOTICE 'V247: %.td_datasource += password_enc, credential_encrypted', sch;
        END IF;
    END LOOP;
END $$;

COMMENT ON COLUMN public.td_datasource.password_enc IS
    '数据源连接凭证密文（AES-256-GCM，12B IV 内嵌，密钥经 01 册密钥治理；F02-01 写路径新建/改密时加密，读路径永不回显）';
COMMENT ON COLUMN public.td_datasource.credential_encrypted IS
    'F02-01 密文凭据列（V164.1 仅占位哨兵；业务消费口径收敛至 password_enc，本列保留只加不删）';
COMMENT ON COLUMN ecos_data.td_datasource.password_enc IS
    '同 public.td_datasource.password_enc（引擎控制域目标态）';
COMMENT ON COLUMN ecos_data.td_datasource.credential_encrypted IS
    '同 public.td_datasource.credential_encrypted';

-- ── DDL Lint Self-audit ──────────────────────────────────
-- [DR01] public./ecos_data. schema 限定 ✓（无裸表名）
-- [IR03] 只加列不删不改型；credential_encrypted 保留 ✓
-- [DR06] 密文列 TEXT，无裸大精度数值 / JSON 二进制 / 带时区时间类型 ✓
-- [ST03] 控制域凭据以密文存储，ST03-A 列级明文豁免不适用（此处非明文）✓
-- [MC02] 无 JSON 二进制列、无裸 NUMERIC ✓
-- [ST09] 零 FOREIGN KEY ✓
