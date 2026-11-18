-- V164.1 (卷02 §E.5/E.3 前置 F02-01，版本号=卷02 定版子版本，避让卷01 V164): 数据源凭证密文化列就位 + 明文迁移占位标记
-- 追溯: W52→C41（P0 安全：数据源凭证明文入库，控制域不可豁免）；需求依据 REQ-DATA-05、REQ-DB-03（ST03/ST03-A④）
-- 批准: 需求检视报告 §十四 14.1（2026-09-29）之 R-1 a（新写落 ecos_data，public 只停写）；执行边界 §14.4 = 脚本文件落地
-- 状态: 仅落迁移脚本文件，**未对 ecos-postgres 实跑**（实跑属 §14.4 未授权项，需逐项再确认）
-- 纪律: DR01~DR08 / MC01~MC03 / ST03-A / ST07 / IR02(手动 psql) / IR03(只加不删)
-- 迁移语义（重要）: 本脚本只做**列就位与占位标记**——credential_encrypted 仅写固定哨兵值
--   '__PENDING_REENCRYPT__'，**不复制任何明文口令进新列**；真正的加密写路径（runtime-access 加密 +
--   V165 密钥治理表供 key、AES-256-GCM）属业务代码改造批次（F02-01 / DatasourceCredentialCipherTest），本批不做。
--   明文仍留在 connection_config 内，由业务改造批次完成重加密后再行收敛（IR03：本批不删列不洗数据）。
-- 幂等条件: UPDATE 仅命中「明文凭据段非空 且 credential_encrypted 为空」的行；重跑 0 行受影响。
-- 回滚说明: 只加列不删数据；如需回滚仅停止业务侧消费该列（列保留，IR03 禁 DROP），哨兵值可 UPDATE 置回 NULL。
-- schema 说明: ecos_data.td_datasource = R-1 a 裁决后的权威归属表（空镜像，列先行就位）；
--   public.td_datasource = 现状承数表（D-7 双镜像、写全落 public），明文迁移标记打在承数侧。

-- ── 1. 密文凭证列就位（两侧 schema 同构加列，只加不删）────────
ALTER TABLE ecos_data.td_datasource ADD COLUMN IF NOT EXISTS credential_encrypted TEXT;
ALTER TABLE public.td_datasource    ADD COLUMN IF NOT EXISTS credential_encrypted TEXT;
COMMENT ON COLUMN ecos_data.td_datasource.credential_encrypted IS '加密后的连接凭证（AES-256-GCM 密文，业务改造批次写入；哨兵值 __PENDING_REENCRYPT__=已检出明文待重加密）';
COMMENT ON COLUMN public.td_datasource.credential_encrypted    IS '同上（knownLegacy 承数侧，仅迁移标记，业务新写走 ecos_data）';

-- ── 2. 明文口令迁移占位标记（幂等 UPDATE；不复制明文）─────────
-- 条件：connection_config（承载 JDBC 连接参数含 password 段的 TEXT 列）非空且含 password 键，
--       且 credential_encrypted 尚未置位 → 打 __PENDING_REENCRYPT__ 哨兵，供业务改造批次逐行重加密后清除。
UPDATE ecos_data.td_datasource
   SET credential_encrypted = '__PENDING_REENCRYPT__'
 WHERE connection_config IS NOT NULL
   AND connection_config <> ''
   AND connection_config LIKE '%"password"%'
   AND (credential_encrypted IS NULL OR credential_encrypted = '');

UPDATE public.td_datasource
   SET credential_encrypted = '__PENDING_REENCRYPT__'
 WHERE connection_config IS NOT NULL
   AND connection_config <> ''
   AND connection_config LIKE '%"password"%'
   AND (credential_encrypted IS NULL OR credential_encrypted = '');

-- ── 3. 待处理清单查询口径（人工核对用，非写语句）──────────────
-- SELECT datasource_id FROM public.td_datasource WHERE credential_encrypted = '__PENDING_REENCRYPT__';

-- ── DDL Lint Self-audit ──────────────────────────────────
-- [DR01] ecos_data./public. schema 限定 ✓（无裸表名）
-- [MC02] credential_encrypted 用 TEXT（密文基串），无裸 NUMERIC、无 JSON 二进制列 ✓
-- [MC03] 无库内策略语句、无分区、无数组列、无带时区时间类型、无裸 cast、无 JSON 二进制操作符 ✓（LIKE/SUBSTR 可移植）
-- [ST07] 只落 ecos_data（引擎控制域白名单）+ public（主控制承数侧），未新建 schema ✓
-- [ST09] 零新增 FOREIGN KEY ✓  [IR03] 只加列；明文列不删不洗（收敛属后续授权批次）✓
-- [ST03-A] 新增敏感列登记候选: ecos_data.td_datasource.credential_encrypted / public.td_datasource.credential_encrypted
--          （密文，非豁免对象）；既有明文承载体 connection_config 待业务批次登记 —— 均已列入本批汇报，不改登记表文件
