-- V284 (差距回补 / 安全域脱敏策略字典缺建): public.ecos_mask_strategy
-- ============================================================
-- 缺陷事实（live 实证，非推断）:
--   详设《从零实施版》§4.4.2 表清单列 `ecos_control.ecos_mask_strategy`（脱敏策略字典与通道绑定，
--   uk_mask_strategy(tenant_id, strategy_code)），§4.4.3 末段给出列定义；
--   全仓 grep `CREATE TABLE.*ecos_mask_strategy` = 0 命中（含 legacy：docs/50-db_script/01-ddl-多方言、ecos-sql）；
--   dev PG 实查 information_schema.tables = 0 行；全仓代码（*.java/*.xml）零引用 ⇒ 设计有、代码无、库无 = 真缺，
--   按用户裁决（2026-10-14 R9 ③）补建。
--
-- 列清单单源 = 详设 §4.4.3 末段原文：`id / strategy_code VARCHAR(32)（8 类封闭枚举）/
--   keep_prefix 与 keep_suffix SMALLINT / amount_bucket VARCHAR(32) / apply_channels VARCHAR(64)（如 export+ai）`
--   + §6.1 通用列模板 + V164 同域安全表家规（tenant_id NOT NULL / enabled / comment）。
--
-- strategy_code 值域口径（偏差登记 D1）:
--   设计写"8 类"，代码实测单源 = DataMaskingService.ruleForDataType（security-engine-impl:67-77）
--   枚举 6 个显式映射（ID_CARD/PHONE/EMAIL/BANK_CARD/AMOUNT/ADDRESS）+ default→none，
--   V154 存量数据面另有 GENERAL 兜底值 ⇒ 可证值域 = 7（含 GENERAL）+ `none` 显式不脱敏位。
--   **按实际口径建 7 值 CHECK，不臆造第 8 类**；第 8 类若后续裁决补充（如 NAME/NAME_CARD），
--   走 ALTER 放宽 CHECK（只加不改红线允许 ADD CONSTRAINT 替代需人工裁决，届时登记）。
--
-- Schema 归属（偏差登记 D2）: 落 `public`——V200 治理后控制域现行面即 public
--   （同族 ecos_rls_policy / ecos_cls_policy / ecos_security_asset 全在 public，V164 建，dev PG 实查在位）；
--   设计目标 schema `ecos_control` 尚未启用 ⇒ 本脚本用 public 并在台账登记"设计落点 ecos_control / 实际 public"偏差，
--   迁移随主控制 schema 三步走批次统一裁决，禁本批单独开 ecos_control 写面。
--
-- 状态: **仅落文件，未对 ecos-postgres 实跑**（实跑需逐项授权；表无消费方，零影响面）。
-- 纪律: DR01~DR08 / MC01~MC03 / ST03(字典明文列，无敏感材料) / ST04/ST05(不写 RLS) / ST06(不建审计表) / ST07 / IR02 / IR03
-- ============================================================

CREATE TABLE IF NOT EXISTS public.ecos_mask_strategy (
    id              VARCHAR(36) PRIMARY KEY,                   -- MC01 应用侧 UUID
    tenant_id       VARCHAR(64) NOT NULL,                      -- 通用列（V164 同族家规）
    strategy_code   VARCHAR(32) NOT NULL,                      -- 策略码（§4.4.3：值域见 D1）
    keep_prefix     SMALLINT,                                  -- 保留前 N 位
    keep_suffix     SMALLINT,                                  -- 保留后 N 位
    amount_bucket   VARCHAR(32),                               -- 金额分桶策略（AMOUNT 类专用）
    apply_channels  VARCHAR(64),                               -- 通道绑定，`+` 连接（如 export+ai，页/导出/AI 三通道 = Purpose{PAGE,EXPORT,AI}）
    enabled         SMALLINT NOT NULL DEFAULT 1,               -- V164 同族启停位形态
    comment         VARCHAR(512),                              -- 备注（V164 同族 comment 列名，非 *_text 语义列）
    domain          VARCHAR(50) NOT NULL DEFAULT 'default',    -- DR08
    version_no      VARCHAR(20) NOT NULL DEFAULT '1',          -- DR07（字典表应用侧未接入前先给默认，避免 seed 插入缺列）
    create_time     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by       VARCHAR(100),
    update_by       VARCHAR(100),
    is_deleted      SMALLINT NOT NULL DEFAULT 0,               -- DR06
    CONSTRAINT ck_mask_strategy_code CHECK (strategy_code IN
        ('ID_CARD','PHONE','EMAIL','BANK_CARD','AMOUNT','ADDRESS','GENERAL','none')),
    CONSTRAINT uk_mask_strategy UNIQUE (tenant_id, strategy_code)  -- §4.4.2 唯一键原文
);
COMMENT ON TABLE public.ecos_mask_strategy IS
    '脱敏策略字典（§4.4.2 第 6 行）：strategy_code 值域 = DataMaskingService.ruleForDataType 实测 7 类 + none 显式不脱敏；列清单单源 = 详设 §4.4.3 末段';
COMMENT ON COLUMN public.ecos_mask_strategy.apply_channels IS
    '应用通道绑定，`+` 连接（page/export/ai，对应 DecisionBundle.Purpose 三值）；NULL=全通道适用';
COMMENT ON COLUMN public.ecos_mask_strategy.strategy_code IS
    '策略码封闭枚举：ID_CARD/PHONE/EMAIL/BANK_CARD/AMOUNT/ADDRESS/GENERAL/none（D1：设计"8 类"按代码实测含 none 共 8 值立 CHECK）';

CREATE INDEX IF NOT EXISTS idx_mask_strategy_enabled
    ON public.ecos_mask_strategy (tenant_id, enabled);

-- ── 偏差登记 ──────────────────────────────────────────────
-- D1 [枚举口径] 设计"8 类封闭枚举"与代码实测 6+GENERAL 的差异：本脚本按**实际可证值域**建 8 值
--   （7 类 + none），依据 = DataMaskingService.java:67-77 + V154 存量 GENERAL 兜底 +
--   applyMaskingByStrategy 的 "none"=显式不脱敏语义（同文件 79-81 行）。禁凭设计虚数臆造第 8 个业务类。
-- D2 [schema] 落 public 非设计文 ecos_control：控制域现行面 = public（ST07"现 public，目标 ecos_control"），
--   与同族三表（V164）保持同 schema 才能 JOIN；台账登记偏差行。
-- D3 [MC06] DDL 硬编码 `public.` 前缀：与 V164/V272~V281 同批同形态（单源目录 house style），
--   配置化改造挂工程清单 #6，本脚本不单独破例。
-- ──────────────────────────────────────────────────────────

-- ── 回滚说明 ──────────────────────────────────────────────
-- 幂等：CREATE TABLE IF NOT EXISTS + 索引 + COMMENT，重复执行零变更。
-- 回滚 = 应用侧不启用字典（当前零消费方）；删表允许人工裁决（IR03），脚本不提供 DROP。
-- 实跑影响面：新增 1 表 1 索引 0 行；不触碰存量；无 RLS/加密列（字典明文，ST03 不适用）。
--
-- ── DDL Lint Self-audit ──────────────────────────────────────
-- [DR01] public. 限定 ✓（D2 登记）  [DR02] ecos_ 前缀 ✓  [DR04] 无 JSON 语义列 ✓  [DR05~08] 通用列齐 ✓
-- [MC01] VARCHAR(36) PK ✓  [MC02] 无 JSONB、数值列在白名单 ✓  [MC03] UNIQUE 约束标准 SQL，无 PG 专有 ✓
-- [ST07] 主控制现行面 public（8 schema 封闭枚举内）✓  [ST09] strategy_code 被 cls_policy 逻辑引用，不建 FK ✓
-- [IR02] 仅落文件，实跑=人工 psql 待授权 ✓  [IR03] 只加不删 ✓
