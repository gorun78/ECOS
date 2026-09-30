-- V165 (卷01 §6.2): 加密密钥治理表建表（替代 knownLegacy 的 td_crypto_key 写入，关闭 W37 缺钥静默建钥）
-- 追溯: W37→C27（P0 安全，AES-256-GCM 唯一允许值 + 禁自动建钥 + 轮换）；需求依据 REQ-DB-03、REQ-SEC-01
-- 批准: 需求检视报告 §十四 14.1（2026-09-29）之 R-18 ②（废除一键绕过）/R-35 ① 口径；执行边界 §14.4 = 脚本文件落地
-- 状态: 仅落迁移脚本文件，**未对 ecos-postgres 实跑**（实跑属 §14.4 未授权项，需逐项再确认）
-- 纪律: DR01~DR08 / MC01~MC03 / ST03-A / ST07 / IR02(手动 psql) / IR03(只加不删)
-- 安全红线（R7）: master_key_ref 仅存引用名（env/KMS key id），本表结构不承载任何密钥材料；
--       种子/轮换由 security-engine 密钥治理服务写入，禁在 DDL/脚本中嵌入真实 key

-- ── 1. 密钥治理表（真新表，按 DR06 规范审计列建；2026-09-30 校订：01 册字面块的 created_at/created_by 系 DR06 反例，已改规范列名）──
CREATE TABLE IF NOT EXISTS public.ecos_security_crypto_key (
    id             VARCHAR(36) PRIMARY KEY,
    key_id         VARCHAR(64)  NOT NULL,
    algorithm      VARCHAR(32)  NOT NULL,            -- AES-256-GCM（唯一允许值，W37）
    key_version    SMALLINT     NOT NULL,
    status         VARCHAR(16)  NOT NULL DEFAULT 'active',  -- active|retired|destroyed
    master_key_ref VARCHAR(128) NOT NULL,            -- 仅引用名（env/KMS key id），禁存密钥材料（R7）
    rotate_at      TIMESTAMP,
    retired_at     TIMESTAMP,
    create_time    TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_time    TIMESTAMP    NOT NULL DEFAULT NOW(),
    create_by      VARCHAR(100),
    update_by      VARCHAR(100),
    version_no     VARCHAR(20)  NOT NULL,
    is_deleted     SMALLINT     NOT NULL DEFAULT 0,
    domain         VARCHAR(50)  NOT NULL DEFAULT 'default',
    tenant_id      VARCHAR(36)
);
CREATE UNIQUE INDEX IF NOT EXISTS uniq_crypto_key_ver ON public.ecos_security_crypto_key (key_id, key_version);

-- ── DDL Lint Self-audit ──────────────────────────────────
-- [DR01] public. 限定 ✓  [DR02] ecos_ 前缀 ✓  [DR03] 单数 ✓
-- [DR05] is_deleted SMALLINT ✓  [DR06] 审计五列 create_time/update_time/create_by/update_by/is_deleted ✓（01 册 §6.2 字面块的 created_at/created_by 为 DR06 反例，已同步勘误）
-- [DR07] version_no VARCHAR(20) NOT NULL ✓  [DR08] domain 默认 'default' ✓
-- [MC01] 主键 VARCHAR(36) 应用侧 UUID，DDL 无默认值函数 ✓  [MC02] 无裸大精度数值、无 JSON 二进制列 ✓
-- [MC03] 无库内行级安全策略语句、无分区表子句、无数组列类型、无带时区时间类型、无裸 cast ✓（uniq_crypto_key_ver 非条件索引）
-- [ST07] 安全域落主控制 schema public（卷01 §6.1）✓  [ST09] 零 FOREIGN KEY ✓
-- [R7]  表结构不含密钥材料列（仅 master_key_ref 引用名）✓  [IR03] 新表只建不删，替代 td_crypto_key 采用停写不删策略 ✓
