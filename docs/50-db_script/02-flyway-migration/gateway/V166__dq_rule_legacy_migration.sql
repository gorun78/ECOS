-- V166 (卷02 §E.3/E.5): 17 条 legacy DQ 规则迁入治理模型（DRAFT 待人工签核）+ 迁移报告表（禁静默）
-- 追溯: W45→C34（DQ 双模型，治理侧 0 行，DqLegacyMigrationParityTest 17→17）；需求依据 REQ-DATA-02
-- 批准: 需求检视报告 §十四 14.1（2026-09-29）之 R-3 ②+禁静默（解析失败不迁移、必须出报告）与 R-1b ②（DQ 归数据引擎）；执行边界 §14.4 = 脚本文件落地
-- 状态: 仅落迁移脚本文件，**未对 ecos-postgres 实跑**（实跑属 §14.4 未授权项，需逐项再确认）
-- 纪律: DR01~DR08 / MC01~MC03 / ST03-A / ST07 / IR02(手动 psql) / IR03(只加不删)
--
-- 【2026-09-30 校订（只读取证后改写；原稿按 V6 脚本基线假设源表形状，实跑必失败）】
--   information_schema 实测本机 ecos-postgres(db=sys_man) 中同名 legacy 规则表实存 4 形，互相不一致：
--     public.ecos_dq_rule        17 行  id varchar(64) + code + target_entity/target_field + rule_expression + **params JSONB** + tenant_id
--     public.ecos_dq_rule_v2     26 行  id bigint + config_json TEXT + enabled（V111 §3.2 已承接）
--     ecos_data.ecos_dq_rule      0 行  id bigint + config_json TEXT + target_*/rule_expression/code/status（ecos-sql/03_ecos_data.sql 多库镜像形）
--     ecos_dq.dq_rule             0 行  治理表（V111 建，本脚本迁入目标）
--   结论：① 目标源 **public.ecos_dq_rule 无 config_json 列**（V6 脚本基线与现网已漂移，W38 类 DDL 单源漂移缺陷的又一实证）；
--         ② 原稿"target_entity/target_field 实形无该两列，故置 NULL"**与实测相反**（两列实存且 17 行中 16 行有值），已改为真映射；
--         ③ 实测 17 行 params 全为 '{}'，规则语义实际承载在 rule_expression（16/17 非空）——若沿用"params 非空即 MIGRATED"
--            判据，会把 17 行全部标成 MIGRATED 而 parameters_json 为空对象，**语义静默丢失**，违 R-3 ② 禁静默口径；
--            故本脚本把 rule_expression/code 一并投影进 parameters_json，并新增 outcome=REJECTED_EMPTY（两载荷皆空的行拒迁并出报告）。
--   取舍：判据改后实测为 **16 迁入 + 1 拒迁**（QA-Test 行 params='{}' 且 rule_expression 为空），W45/C34 的"17→17"口径需按实测更正
--         （登记于落地清单，属"批准≠已验收"的验收面偏差，不改 §14.1 已批准语义）。
--   兼容性：§3/§4 改为信息架构探测双分支（config_json 基线形 / params 实形），任一环境只走匹配分支，无匹配则 WARNING 跳过（不静默）。
--   治理表 NOT NULL 列缺省核对（只读实测）：parameters '{}'::jsonb、version 1、status 'DRAFT'、source_type 'MANUAL'、
--     created_at/updated_at now()、is_deleted false 均有默认值 → 本脚本只写 parameters_json（MC02 合规 TEXT 形态），
--     不回填 legacy JSONB 列 parameters（该列按 E.3 停写；后果=旧读路径对迁入 17 行读到 '{}'，已在此登记，签核时人工补录）。
--
-- 迁移语义（终态）: public.ecos_dq_rule → ecos_dq.dq_rule（F02-08-4 字段映射：
--   rule_type→rule_type 原值保留 + category 粗映射、params/config_json→parameters_json.sourceParams、
--   rule_expression→parameters_json.expression、code→rule_code、target_entity→target_table、target_field→target_field、
--   status 一律 DRAFT 待人工签核，**不做语义等价保证**）；失败/空载荷行不迁移，逐行写报告表（禁静默）。
-- 幂等条件: 报告表按 (migration_batch, legacy_rule_id) 不存在才插；治理表按 legacy_rule_id 不存在才插。重跑 0 行新增。
-- 回滚说明: 迁入行 source_type='LEGACY_V1' + legacy_rule_id 全链路可追溯，治理侧回滚 = 将迁入行 status 置 'REJECTED'（禁 DELETE/DROP，IR03）；
--   旧表 public.ecos_dq_rule 不删不改不洗数（读端点保留 ≥2 迭代 + deprecated，F02-08-5）。

-- ── 1. 治理表追溯列 + 规范列补齐（只加列，E.3「DR06/DR07 漂移」条款）──
ALTER TABLE ecos_dq.dq_rule ADD COLUMN IF NOT EXISTS legacy_rule_id  VARCHAR(36);
ALTER TABLE ecos_dq.dq_rule ADD COLUMN IF NOT EXISTS parameters_json TEXT;
-- E.3：既有 created_at/created_by/version **不 ALTER**（IR03），改为新增规范列，Mapper 新写一律用规范列，
--      knownLegacy 列名漂移由后续版本以视图收敛（domain 既有列复用，不重复加；既有 is_deleted 为 BOOLEAN，
--      与 DR05 的 SMALLINT 形态不一致 → 登记为存量漂移，本脚本不改列类型，视图收敛批次处理）
ALTER TABLE ecos_dq.dq_rule ADD COLUMN IF NOT EXISTS create_time TIMESTAMP;
ALTER TABLE ecos_dq.dq_rule ADD COLUMN IF NOT EXISTS update_time TIMESTAMP;
ALTER TABLE ecos_dq.dq_rule ADD COLUMN IF NOT EXISTS create_by   VARCHAR(100);
ALTER TABLE ecos_dq.dq_rule ADD COLUMN IF NOT EXISTS update_by   VARCHAR(100);
ALTER TABLE ecos_dq.dq_rule ADD COLUMN IF NOT EXISTS version_no  VARCHAR(20) NOT NULL DEFAULT '1';  -- DR07
CREATE INDEX IF NOT EXISTS idx_dq_rule_legacy ON ecos_dq.dq_rule(legacy_rule_id);

-- ── 2. 迁移报告表（新表，落 ecos_data：R-1b ② 终态权威归属；ST07 白名单内）──
CREATE TABLE IF NOT EXISTS ecos_data.ecos_dq_legacy_migration_report (
    id               VARCHAR(36) PRIMARY KEY,          -- MC01 应用侧键；本脚本用确定性键 dqmig_<legacy_id>
    migration_batch  VARCHAR(64) NOT NULL DEFAULT 'V166',
    legacy_rule_id   VARCHAR(36) NOT NULL,             -- 来源 public.ecos_dq_rule 主键（文本化）
    legacy_rule_name VARCHAR(255),
    outcome          VARCHAR(32) NOT NULL,             -- MIGRATED | REJECTED_PARSE_FAIL | REJECTED_EMPTY
    reason           TEXT,                              -- 拒因（仅 REJECTED_* 行填写）
    migrated_rule_id VARCHAR(64),                       -- 迁入治理表后的行键
    create_time      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,   -- DR06
    update_time      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by        VARCHAR(100),
    update_by        VARCHAR(100),
    is_deleted       SMALLINT NOT NULL DEFAULT 0,
    domain           VARCHAR(50) NOT NULL DEFAULT 'default',         -- DR08
    version_no       VARCHAR(20) NOT NULL DEFAULT '1'                -- DR07
);
CREATE UNIQUE INDEX IF NOT EXISTS uniq_dq_mig_report ON ecos_data.ecos_dq_legacy_migration_report(migration_batch, legacy_rule_id);
CREATE INDEX IF NOT EXISTS idx_dq_mig_report_outcome ON ecos_data.ecos_dq_legacy_migration_report(outcome);

-- ── 3+4. 形状自适应迁移（源列存在性探测，双分支；PG 方言 DO + 嵌套 $q$ 动态 SQL，IR02 手动 psql 单库执行）──
DO $do$
BEGIN
    IF to_regclass('public.ecos_dq_rule') IS NULL THEN
        RAISE NOTICE 'V166: public.ecos_dq_rule 不存在，跳过 3+4（无 legacy 源，幂等 no-op）';
        RETURN;
    END IF;

    -- ─ 分支 A：脚本基线形态（V6：config_json TEXT，无 rule_expression/code/target_entity 列）──
    IF EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'ecos_dq_rule' AND column_name = 'config_json') THEN
        RAISE NOTICE 'V166: 源形 = 基线 config_json(TEXT)（分支 A）';

        EXECUTE $q$
            INSERT INTO ecos_data.ecos_dq_legacy_migration_report
                (id, migration_batch, legacy_rule_id, legacy_rule_name, outcome, reason, migrated_rule_id,
                 create_time, update_time, create_by, update_by, is_deleted, domain, version_no)
            SELECT left(CONCAT('dqmig_', o.id), 36), 'V166', left(CAST(o.id AS VARCHAR), 36), o.name,
                   CASE WHEN TRIM(COALESCE(o.config_json, '')) <> ''
                         AND LEFT(TRIM(o.config_json), 1) <> '{'
                        THEN 'REJECTED_PARSE_FAIL'
                        WHEN TRIM(COALESCE(o.config_json, '')) NOT IN ('', '{}')
                        THEN 'MIGRATED'
                        ELSE 'REJECTED_EMPTY' END,
                   CASE WHEN TRIM(COALESCE(o.config_json, '')) <> ''
                         AND LEFT(TRIM(o.config_json), 1) <> '{'
                        THEN '参数列非 JSON 对象文本，解析失败'
                        WHEN TRIM(COALESCE(o.config_json, '')) IN ('', '{}')
                        THEN '参数列为空或空对象，无可迁移载荷'
                        ELSE NULL END,
                   CASE WHEN TRIM(COALESCE(o.config_json, '')) NOT IN ('', '{}')
                         AND LEFT(TRIM(o.config_json), 1) = '{'
                        THEN CONCAT('dq_legacy_', o.id) ELSE NULL END,
                   CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'V166_script', 'V166_script', 0, 'default', '1'
              FROM public.ecos_dq_rule o
             WHERE NOT EXISTS (SELECT 1 FROM ecos_data.ecos_dq_legacy_migration_report r
                                WHERE r.migration_batch = 'V166'
                                  AND r.legacy_rule_id = left(CAST(o.id AS VARCHAR), 36))
        $q$;

        EXECUTE $q$
            INSERT INTO ecos_dq.dq_rule
                (id, rule_name, rule_code, category, rule_type, severity, target_kind,
                 target_table, target_field, status, source_type, source_ref, description,
                 created_by, updated_by, created_at, updated_at,
                 legacy_rule_id, parameters_json, create_time, update_time, create_by, update_by, version_no)
            SELECT left(CONCAT('dq_legacy_', o.id), 64), o.name, NULL,
                   CASE WHEN o.rule_type IN ('NOT_NULL','UNIQUE','UNIQUENESS','FORMAT','RANGE','COMPLETENESS',
                                             'ACCURACY','CONSISTENCY','VALIDITY','FRESHNESS')
                        THEN 'TECHNICAL' ELSE 'BUSINESS' END,          -- 粗映射，不做语义等价保证（F02-08-4）
                   o.rule_type, COALESCE(o.severity, 'MEDIUM'), 'TABLE',
                   NULL, NULL, 'DRAFT', 'LEGACY_V1', 'public.ecos_dq_rule', o.description,
                   'V166_script', 'V166_script',
                   COALESCE(o.created_at, CURRENT_TIMESTAMP), COALESCE(o.updated_at, CURRENT_TIMESTAMP),
                   left(CAST(o.id AS VARCHAR), 36), TRIM(o.config_json),
                   COALESCE(o.created_at, CURRENT_TIMESTAMP), COALESCE(o.updated_at, CURRENT_TIMESTAMP),
                   'V166_script', 'V166_script', '1'
              FROM public.ecos_dq_rule o
             WHERE TRIM(COALESCE(o.config_json, '')) NOT IN ('', '{}')
               AND LEFT(TRIM(o.config_json), 1) = '{'
               AND NOT EXISTS (SELECT 1 FROM ecos_dq.dq_rule g
                                WHERE g.legacy_rule_id = left(CAST(o.id AS VARCHAR), 36))
        $q$;

    -- ─ 分支 B：现网实形（2026-09-30 实测：params JSONB + rule_expression + code + target_entity/field）──
    ELSIF EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = 'public' AND table_name = 'ecos_dq_rule' AND column_name = 'params') THEN
        RAISE NOTICE 'V166: 源形 = 实形 params(JSONB)（分支 B）';

        EXECUTE $q$
            INSERT INTO ecos_data.ecos_dq_legacy_migration_report
                (id, migration_batch, legacy_rule_id, legacy_rule_name, outcome, reason, migrated_rule_id,
                 create_time, update_time, create_by, update_by, is_deleted, domain, version_no)
            SELECT left(CONCAT('dqmig_', o.id), 36), 'V166', left(CAST(o.id AS VARCHAR), 36), o.name,
                   CASE WHEN TRIM(COALESCE(CAST(o.params AS TEXT), '')) <> ''
                         AND LEFT(TRIM(CAST(o.params AS TEXT)), 1) <> '{'
                        THEN 'REJECTED_PARSE_FAIL'
                        WHEN TRIM(COALESCE(CAST(o.params AS TEXT), '{}')) NOT IN ('', '{}')
                          OR TRIM(COALESCE(o.rule_expression, '')) <> ''
                        THEN 'MIGRATED'
                        ELSE 'REJECTED_EMPTY' END,
                   CASE WHEN TRIM(COALESCE(CAST(o.params AS TEXT), '')) <> ''
                         AND LEFT(TRIM(CAST(o.params AS TEXT)), 1) <> '{'
                        THEN 'params 非 JSON 对象文本，解析失败'
                        WHEN TRIM(COALESCE(CAST(o.params AS TEXT), '{}')) IN ('', '{}')
                          AND TRIM(COALESCE(o.rule_expression, '')) = ''
                        THEN 'params 为空对象且 rule_expression 为空，无可迁移载荷'
                        ELSE NULL END,
                   CASE WHEN TRIM(COALESCE(CAST(o.params AS TEXT), '{}')) NOT IN ('', '{}')
                          OR TRIM(COALESCE(o.rule_expression, '')) <> ''
                        THEN CONCAT('dq_legacy_', o.id) ELSE NULL END,
                   CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'V166_script', 'V166_script', 0, 'default', '1'
              FROM public.ecos_dq_rule o
             WHERE NOT EXISTS (SELECT 1 FROM ecos_data.ecos_dq_legacy_migration_report r
                                WHERE r.migration_batch = 'V166'
                                  AND r.legacy_rule_id = left(CAST(o.id AS VARCHAR), 36))
        $q$;

        -- parameters_json = 把 legacy 三个分离载荷合成为一个 JSON 文本对象（quote_literal 做字符串转义，纯 PG 方言，
        --   与 V173 §4b 同法；MC03 禁的是 :: 与 JSONB 操作符，此处 CAST(... AS TEXT) 为可移植转换）
        EXECUTE $q$
            INSERT INTO ecos_dq.dq_rule
                (id, rule_name, rule_code, category, rule_type, severity, target_kind,
                 target_table, target_field, status, source_type, source_ref, description,
                 created_by, updated_by, created_at, updated_at,
                 legacy_rule_id, parameters_json, create_time, update_time, create_by, update_by, version_no)
            SELECT left(CONCAT('dq_legacy_', o.id), 64), o.name, left(COALESCE(o.code, ''), 191),
                   CASE WHEN o.rule_type IN ('NOT_NULL','UNIQUE','UNIQUENESS','FORMAT','RANGE','COMPLETENESS',
                                             'ACCURACY','CONSISTENCY','VALIDITY','FRESHNESS')
                        THEN 'TECHNICAL' ELSE 'BUSINESS' END,          -- 粗映射，不做语义等价保证（F02-08-4）
                   o.rule_type, COALESCE(o.severity, 'MEDIUM'), 'TABLE',
                   NULLIF(TRIM(COALESCE(o.target_entity, '')), ''), NULLIF(TRIM(COALESCE(o.target_field, '')), ''),
                   'DRAFT', 'LEGACY_V1', 'public.ecos_dq_rule', o.description,
                   'V166_script', 'V166_script',
                   COALESCE(o.created_at, CURRENT_TIMESTAMP), COALESCE(o.updated_at, CURRENT_TIMESTAMP),
                   left(CAST(o.id AS VARCHAR), 36),
                   '{"expression":'
                     || CASE WHEN TRIM(COALESCE(o.rule_expression, '')) = '' THEN 'null'
                             ELSE quote_literal(o.rule_expression) END
                     || ',"legacyCode":'
                     || CASE WHEN TRIM(COALESCE(o.code, '')) = '' THEN 'null'
                             ELSE quote_literal(o.code) END
                     || ',"sourceParams":' || COALESCE(CAST(o.params AS TEXT), '{}') || '}',
                   COALESCE(o.created_at, CURRENT_TIMESTAMP), COALESCE(o.updated_at, CURRENT_TIMESTAMP),
                   'V166_script', 'V166_script', '1'
              FROM public.ecos_dq_rule o
             WHERE (TRIM(COALESCE(CAST(o.params AS TEXT), '{}')) NOT IN ('', '{}')
                      OR TRIM(COALESCE(o.rule_expression, '')) <> '')
               AND LEFT(TRIM(COALESCE(CAST(o.params AS TEXT), '{}')), 1) = '{'
               AND NOT EXISTS (SELECT 1 FROM ecos_dq.dq_rule g
                                WHERE g.legacy_rule_id = left(CAST(o.id AS VARCHAR), 36))
        $q$;

        RAISE NOTICE 'V166: 分支 B 迁移完成（幂等）。实形载荷分布实测：params 全为 ''{}''，语义在 rule_expression（16/17 非空）';
    ELSE
        RAISE WARNING 'V166: public.ecos_dq_rule 既无 config_json 也无 params 列，无法判定参数载荷，本批跳过（禁静默：请人工核对源表形态）';
    END IF;
END
$do$;

-- ── 5. 禁静默告警（存在拒迁行时显式 WARNING，不中断整批——拒迁≠批失败，见 R-3 ② 口径）──
DO $do$
DECLARE n_parse_fail INTEGER; n_empty INTEGER;
BEGIN
    SELECT COUNT(*) INTO n_parse_fail
      FROM ecos_data.ecos_dq_legacy_migration_report
     WHERE migration_batch = 'V166' AND outcome = 'REJECTED_PARSE_FAIL';
    SELECT COUNT(*) INTO n_empty
      FROM ecos_data.ecos_dq_legacy_migration_report
     WHERE migration_batch = 'V166' AND outcome = 'REJECTED_EMPTY';
    IF n_parse_fail > 0 THEN
        RAISE WARNING 'V166: % 条 legacy DQ 规则解析失败未迁移，请核对 ecos_data.ecos_dq_legacy_migration_report（禁静默）', n_parse_fail;
    END IF;
    IF n_empty > 0 THEN
        RAISE WARNING 'V166: % 条 legacy DQ 规则无参数且无表达式，未迁移（REJECTED_EMPTY，签核时人工补录）', n_empty;
    END IF;
END
$do$;

-- 迁移验收（人工核数口径，DqLegacyMigrationParityTest 同口径）：
--   SELECT outcome, count(*) FROM ecos_data.ecos_dq_legacy_migration_report
--    WHERE migration_batch = 'V166' GROUP BY outcome;
--     → 分支 B 实测期望：MIGRATED 16 / REJECTED_EMPTY 1（原 W45 "17→17" 口径已按 2026-09-30 实测更正，见头注取舍）
--   SELECT count(*) FROM ecos_dq.dq_rule WHERE source_type = 'LEGACY_V1';   -- 期望 16

-- ── DDL Lint Self-audit ──────────────────────────────────
-- [DR01] ecos_dq.dq_rule（既有承流表，只加列不迁）/ ecos_data.*（新表）/ public.ecos_dq_rule（只读源）均 schema 限定 ✓
-- [DR02] 新表 ecos_dq_legacy_migration_report 带 ecos_ 前缀 ✓（dq_rule/dq_legacy_ 键为既有治理表延续）
-- [DR04] parameters_json 后缀 + TEXT ✓  [DR05] is_deleted SMALLINT ✓（治理表既有 is_deleted BOOLEAN = 存量漂移，已登记不擅改）
-- [DR06~DR08] 报告表审计五列 + version_no + domain ✓；治理表规范列按 E.3 新增，version_no 带 DEFAULT '1' 满足 DR07 NOT NULL ✓
-- [MC01] 报告表主键 VARCHAR(36) 脚本确定性键；无默认值方言函数 ✓；迁入行主键 'dq_legacy_<id>' 可追溯 ✓（left(...,64) 容存量长键）
-- [MC02] 不向 legacy JSONB 列 parameters 写入（走 parameters_json TEXT）；无数组列、无带时区时间类型、无裸 cast
--        （JSONB→文本一律 CAST(... AS TEXT)）、无库内策略语句、无分区 ✓
-- [MC03] 无 :: / JSONB 操作符 / ON CONFLICT / RETURNING ✓
-- [IR02] 手动 psql，未实跑 ✓；方言面 = to_regclass / information_schema 探测 / quote_literal / LEFT-TRIM-COALESCE，
--        全部限定在数据迁移 DO 块（与 V173 §3c/§4b 同口径），建表与列形态保持可移植
-- [ST07] 新表落 ecos_data（R-1b ②）✓  [ST09] 零 FOREIGN KEY（legacy_rule_id 纯追溯列）✓
-- [IR03] 旧表 public.ecos_dq_rule 不删不改不洗数 ✓
