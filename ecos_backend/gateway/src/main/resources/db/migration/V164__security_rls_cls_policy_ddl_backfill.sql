-- V164 (卷01 §6.2): 安全域 RLS/CLS 策略表 + 敏感数据目录 DDL 单源补齐（关闭 W38：现状有表无脚本）
-- 追溯: W38→C28 / W30-W31（RLS 参数化谓词）；需求依据 REQ-SEC-01（§1.1 六类敏感对象目录）、REQ-DB-01/02
-- 批准: 需求检视报告 §十四 14.1（2026-09-29）之 R-35 ①（附件 PG RLS 代码块作废，改 security-engine REST 谓词对象）口径；执行边界 §14.4 = 脚本文件落地
-- 状态: 仅落迁移脚本文件，**未对 ecos-postgres 实跑**（实跑属 §14.4 未授权项，需逐项再确认）
-- 纪律: DR01~DR08 / MC01~MC03 / ST03-A / ST07 / IR02(手动 psql) / IR03(只加不删)
-- 策略（IR03）: 现网 rls/cls 表已存在 → 本脚本 CREATE IF NOT EXISTS 记录**现网真实形态**（单源=可空库重建出与生产一致的表），
--   目标态列一律以 ALTER ADD COLUMN IF NOT EXISTS 增补；不重建、不删列、不改名、不改类型。
-- 【2026-09-30 校订】原 §6.2 字面块把 id/priority/enabled/filter_expr/description/created_by 写成"目标态"形态，
--   与 ecos-postgres 实测（information_schema.columns 只读取证，见下表）不一致，空库重建会与
--   RowLevelSecurityServiceImpl / ColumnLevelSecurityServiceImpl 的现有 JdbcTemplate SQL 失配。
--   本文件按实测形态改写 CREATE，目标态列下移为 ALTER；卷01 §6.2 同步出同一勘误（差异①~⑥）。
--   实测基线（2026-09-30 只读）：ecos_rls_policy/ecos_cls_policy 均 id varchar(64)、priority integer default 0、
--   enabled boolean default true、created_by varchar(64)、created_at/updated_at timestamp default now()、resource_id varchar(64)；
--   rls.filter_expr varchar(512) NOT NULL；cls 有 visible_cols text NOT NULL / blocked_cols text / description text，
--   **cls 现网无** column_name/mode/scope_type/scope_id/allowed_columns_json（属目标态，本文件 §2 增补）。
-- 归属（卷01 §6.1）: 安全域 = 控制域主控制 schema public（ADR-12 S1 后 ecos_control）；策略表无物理外键

-- ── 1. RLS 策略表（现网形态镜像 + 参数化谓词增补）───────────────
CREATE TABLE IF NOT EXISTS public.ecos_rls_policy (
    id            VARCHAR(64)  PRIMARY KEY,            -- knownLegacy：现网 varchar(64)，MC01 只约束新表
    policy_name   VARCHAR(128) NOT NULL,
    table_name    VARCHAR(128) NOT NULL,
    filter_expr   VARCHAR(512) NOT NULL,               -- knownLegacy：现网 varchar(512)（目标态谓词走 predicate_template）
    role_id       VARCHAR(64),
    user_id       VARCHAR(64),
    priority      INTEGER      DEFAULT 0,              -- knownLegacy：现网 integer
    enabled       BOOLEAN      DEFAULT true,           -- knownLegacy：现网 boolean（MC02 只约束新表；收敛随 Mapper 切换批次）
    description   TEXT,
    created_by    VARCHAR(64),
    created_at    TIMESTAMP    DEFAULT NOW(),
    updated_at    TIMESTAMP    DEFAULT NOW(),
    resource_id   VARCHAR(64)
);
-- 目标态增补列（W30-W31 参数化谓词主体；MC01：无默认值方言函数）
ALTER TABLE public.ecos_rls_policy ADD COLUMN IF NOT EXISTS predicate_template VARCHAR(1000);
ALTER TABLE public.ecos_rls_policy ADD COLUMN IF NOT EXISTS bindings_json      TEXT;
ALTER TABLE public.ecos_rls_policy ADD COLUMN IF NOT EXISTS source_kind        VARCHAR(32) NOT NULL DEFAULT 'legacy_text';  -- legacy_text|parameterized
-- DR06~DR08 规范列（可空/带默认新增，不改既有列名与读取语义，E.3 漂移同法）
-- 【2026-09-30 四补收口】原稿此处只补 6 列、漏 `create_by`（现网 legacy 同义列为 `created_by VARCHAR(64)`）。
--   依据卷02 §E.3 末（分册 02:634）批准口径："现有 created_at/created_by/version **不 ALTER**（IR03），
--   改为**新增**规范列 create_time/update_time/create_by/update_by/version_no/domain（可空），Mapper 新写一律用规范列"
--   ⇒ 七列是**齐补**而非择列补；同批 V173 对 ecos_workflow/ecos_workflow_approval 即按 7/7 补齐，本脚本属少补。
--   门禁标识 = W60/C49 `AuditColumnPresenceTest`（缺列必判 FAIL）。补列代价：仅脚本文字，未实跑（§14.4）。
ALTER TABLE public.ecos_rls_policy ADD COLUMN IF NOT EXISTS create_time TIMESTAMP;
ALTER TABLE public.ecos_rls_policy ADD COLUMN IF NOT EXISTS update_time TIMESTAMP;
ALTER TABLE public.ecos_rls_policy ADD COLUMN IF NOT EXISTS create_by   VARCHAR(100);
ALTER TABLE public.ecos_rls_policy ADD COLUMN IF NOT EXISTS update_by   VARCHAR(100);
ALTER TABLE public.ecos_rls_policy ADD COLUMN IF NOT EXISTS version_no  VARCHAR(20) NOT NULL DEFAULT '1';
ALTER TABLE public.ecos_rls_policy ADD COLUMN IF NOT EXISTS is_deleted  SMALLINT    NOT NULL DEFAULT 0;
ALTER TABLE public.ecos_rls_policy ADD COLUMN IF NOT EXISTS domain      VARCHAR(50) NOT NULL DEFAULT 'default';
CREATE INDEX IF NOT EXISTS idx_rls_policy_table ON public.ecos_rls_policy (table_name, enabled, priority);

-- ── 2. CLS 列级策略表（现网形态镜像 + 目标态范围模型增补）────────
CREATE TABLE IF NOT EXISTS public.ecos_cls_policy (
    id            VARCHAR(64)  PRIMARY KEY,            -- knownLegacy：现网 varchar(64)
    policy_name   VARCHAR(128) NOT NULL,
    table_name    VARCHAR(128) NOT NULL,
    visible_cols  TEXT         NOT NULL,               -- 现网承载列（JSON 数组文本；无 _json 后缀 = DR04 存量漂移，knownLegacy）
    blocked_cols  TEXT,
    role_id       VARCHAR(64),
    user_id       VARCHAR(64),
    priority      INTEGER      DEFAULT 0,
    enabled       BOOLEAN      DEFAULT true,
    description   TEXT,
    created_by    VARCHAR(64),
    created_at    TIMESTAMP    DEFAULT NOW(),
    updated_at    TIMESTAMP    DEFAULT NOW(),
    resource_id   VARCHAR(64)
);
-- 目标态增补列（卷01 §5 mode/scope 模型：NULL column_name = 整表默认）
ALTER TABLE public.ecos_cls_policy ADD COLUMN IF NOT EXISTS column_name     VARCHAR(128);
ALTER TABLE public.ecos_cls_policy ADD COLUMN IF NOT EXISTS mode            VARCHAR(16) NOT NULL DEFAULT 'deny';
ALTER TABLE public.ecos_cls_policy ADD COLUMN IF NOT EXISTS allowed_columns_json TEXT;  -- 【2026-09-30 校订】原名 allowed_columns 违 DR04（JSON 语义列须 `_json` 后缀）；本列为本批新增、零代码引用、现网不存在（只读实测 information_schema 命中 0）⇒ 直接定名，无兼容代价
ALTER TABLE public.ecos_cls_policy ADD COLUMN IF NOT EXISTS scope_type      VARCHAR(16) NOT NULL DEFAULT 'ROLE';  -- USER|ROLE|GLOBAL
ALTER TABLE public.ecos_cls_policy ADD COLUMN IF NOT EXISTS scope_id        VARCHAR(64);
-- DR06~DR08 规范列（`create_by` 补列依据同上 RLS 块四补收口注）
ALTER TABLE public.ecos_cls_policy ADD COLUMN IF NOT EXISTS create_time TIMESTAMP;
ALTER TABLE public.ecos_cls_policy ADD COLUMN IF NOT EXISTS update_time TIMESTAMP;
ALTER TABLE public.ecos_cls_policy ADD COLUMN IF NOT EXISTS create_by   VARCHAR(100);
ALTER TABLE public.ecos_cls_policy ADD COLUMN IF NOT EXISTS update_by   VARCHAR(100);
ALTER TABLE public.ecos_cls_policy ADD COLUMN IF NOT EXISTS version_no  VARCHAR(20) NOT NULL DEFAULT '1';
ALTER TABLE public.ecos_cls_policy ADD COLUMN IF NOT EXISTS is_deleted  SMALLINT    NOT NULL DEFAULT 0;
ALTER TABLE public.ecos_cls_policy ADD COLUMN IF NOT EXISTS domain      VARCHAR(50) NOT NULL DEFAULT 'default';
CREATE INDEX IF NOT EXISTS idx_cls_policy_table ON public.ecos_cls_policy (table_name, scope_type, enabled);

-- ── 3. 敏感数据目录（真新表：REQ-SEC-01 §1.1 六类对象的落库形态，全量按 DR/MC 建）──
CREATE TABLE IF NOT EXISTS public.ecos_security_asset (
    id             VARCHAR(36) PRIMARY KEY,            -- MC01：应用侧 UUID，DDL 无默认值
    asset_key      VARCHAR(192) NOT NULL,              -- schema.table.column 或 schema.table
    kind           VARCHAR(16)  NOT NULL,              -- COLUMN|TABLE
    sensitivity    VARCHAR(8)   NOT NULL,              -- S0|S1|S2|S3|S4
    guard_combo    VARCHAR(64)  NOT NULL,              -- rls+cls+mask 形式（固定 token 组合，禁自由文本）
    exemption_ref  VARCHAR(64),                        -- 指向 ST03-A 豁免登记表条目号（无外键）
    owner_role     VARCHAR(64),
    description    VARCHAR(500),
    version_no     VARCHAR(20)  NOT NULL,              -- DR07
    is_deleted     SMALLINT     NOT NULL DEFAULT 0,    -- DR05/DR06
    domain         VARCHAR(50)  NOT NULL DEFAULT 'default',  -- DR08
    tenant_id      VARCHAR(36),
    create_time    TIMESTAMP    NOT NULL DEFAULT NOW(),-- DR06
    update_time    TIMESTAMP    NOT NULL DEFAULT NOW(),
    create_by      VARCHAR(100),
    update_by      VARCHAR(100)
);
-- 唯一性：文档字面为条件唯一索引（WHERE is_deleted = 0）。此处保留已批准形态，
-- 跨库等价（MySQL/Oracle 无部分索引）由 W38 镜像脚本 + 应用侧软删判定承接；偏差已登记落地清单。
CREATE UNIQUE INDEX IF NOT EXISTS uniq_sec_asset_key ON public.ecos_security_asset (tenant_id, asset_key) WHERE is_deleted = 0;

-- ── DDL Lint Self-audit ──────────────────────────────────
-- [DR01] 全部 schema 限定（public.）✓  [DR02] ecos_ 前缀 ✓  [DR03] 单数 ✓
-- [DR04] bindings_json/allowed_columns_json 合规（`_json` 后缀 + TEXT 非检索列）；visible_cols/blocked_cols 无 `_json` 后缀 = 存量漂移，knownLegacy 登记（偏差 D-7）
-- [DR05] 新表 is_deleted SMALLINT ✓；存量 enabled boolean = knownLegacy（MC02 只约束新表，收敛随 Mapper 切换批次）
-- [DR06] 新表 ecos_security_asset 用规范五列 create_time/update_time/create_by/update_by/is_deleted ✓；
--        存量 rls/cls 保留 created_at/updated_at/created_by 既有列名 + 规范列以新增可空列补齐（IR03 + E.3 漂移条款）
-- [DR07] version_no VARCHAR(20) NOT NULL ✓（存量表补齐列带 DEFAULT '1' 以免历史行违反 NOT NULL）
-- [DR08] domain VARCHAR(50) NOT NULL DEFAULT 'default' ✓
-- [MC01] 新表主键 VARCHAR(36) 应用侧 UUID、DDL 无默认值函数 ✓；存量 id varchar(64) 按现网镜像保留
-- [MC02] 新表无 JSON 二进制列、无数组、无带时区时间类型 ✓（存量 enabled/boolean 见上）
-- [MC03] 无库内行级安全策略语句、无分区表子句、无裸 cast、无 JSON 二进制操作符 ✓
--        例外登记：uniq_sec_asset_key 带 WHERE is_deleted = 0 —— 卷 01 §6.2 已批准字面形态（部分索引是 DR 索引命名表认可的"部分索引"类），
--        与本目录 V111/V163 既用形态一致；跨库等价由 W38 mysql/oracle 镜像脚本承接
-- [ST07] 安全域落主控制 schema public ✓  [ST09] 零 FOREIGN KEY ✓  [IR03] 只加不删、不重建不改名 ✓
