-- V169.1 (卷03 §E.3): 本体表族补 DR06 审计五列 + version_no(DR07) + domain(DR08)，全部 ADD COLUMN IF NOT EXISTS
-- 追溯: W77/W87（O-11 审计列缺失 / O-15 public 有数据 + ecos_ontology 0 行镜像的双侧表族）；C61（同族）
-- 批准: 需求检视报告 §十四 14.1（2026-09-29）之 R-1=a（本体表族 schema 收编方向 = ecos_ontology；本脚本只补列不迁数据，物理迁移属 §14.4 未授权项）
-- 号段: 与卷02 同版本号冲突，按定版规则取 .1 子版本（V169 整号 = 卷02）
-- 状态: 仅落迁移脚本文件，**未对 ecos-postgres 实跑**（实跑属 §14.4 未授权项，需逐项再确认）
-- 纪律: DR01~DR08 / MC01~MC03 / ST03-A / ST07 / IR02(手动 psql) / IR03(只加不删)
-- 范围口径（依 §E.3 V169 行 + O-15 实测清单）: 本体表族 =
--   ecos_ontology / ecos_ontology_entity / ecos_ontology_property / ecos_ontology_relationship /
--   ecos_ontology_action / ecos_ontology_rule / ecos_object_data / ecos_object_version /
--   ecos_glossary_term / ecos_business_glossary；public 与 ecos_ontology 镜像两侧同时补
--   （ALTER TABLE IF EXISTS 幂等，镜像缺表时仅 WARNING 跳过）。
-- 禁改已有列（IR03/铁律 3.1）：旧 created_at/updated_at **不重命名**为 create_time/update_time ——
--   改名属 ALTER RENAME，违"只加不删"与新列并存双写过渡；代码切读 create_time 属后续授权项。
-- F03-04 的 ecos_ontology_relationship.cardinality VARCHAR(16) 新列属功能批次，不在本脚本 §E.3 表述内，未落。

-- ── public 侧（canonical，O-15 实测有数据）──────────────────────────
ALTER TABLE IF EXISTS public.ecos_ontology ADD COLUMN IF NOT EXISTS create_time TIMESTAMP,
    ADD COLUMN IF NOT EXISTS update_time TIMESTAMP,
    ADD COLUMN IF NOT EXISTS create_by   VARCHAR(100),
    ADD COLUMN IF NOT EXISTS update_by   VARCHAR(100),
    ADD COLUMN IF NOT EXISTS is_deleted  SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS version_no  VARCHAR(20) NOT NULL DEFAULT '1',
    ADD COLUMN IF NOT EXISTS domain      VARCHAR(50) NOT NULL DEFAULT 'default';

ALTER TABLE IF EXISTS public.ecos_ontology_entity ADD COLUMN IF NOT EXISTS create_time TIMESTAMP,
    ADD COLUMN IF NOT EXISTS update_time TIMESTAMP,
    ADD COLUMN IF NOT EXISTS create_by   VARCHAR(100),
    ADD COLUMN IF NOT EXISTS update_by   VARCHAR(100),        -- V120/V122 已补则本行 no-op
    ADD COLUMN IF NOT EXISTS is_deleted  SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS version_no  VARCHAR(20) NOT NULL DEFAULT '1',
    ADD COLUMN IF NOT EXISTS domain      VARCHAR(50) NOT NULL DEFAULT 'default';

ALTER TABLE IF EXISTS public.ecos_ontology_property ADD COLUMN IF NOT EXISTS create_time TIMESTAMP,
    ADD COLUMN IF NOT EXISTS update_time TIMESTAMP,
    ADD COLUMN IF NOT EXISTS create_by   VARCHAR(100),
    ADD COLUMN IF NOT EXISTS update_by   VARCHAR(100),
    ADD COLUMN IF NOT EXISTS is_deleted  SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS version_no  VARCHAR(20) NOT NULL DEFAULT '1',
    ADD COLUMN IF NOT EXISTS domain      VARCHAR(50) NOT NULL DEFAULT 'default';

ALTER TABLE IF EXISTS public.ecos_ontology_relationship ADD COLUMN IF NOT EXISTS create_time TIMESTAMP,
    ADD COLUMN IF NOT EXISTS update_time TIMESTAMP,
    ADD COLUMN IF NOT EXISTS create_by   VARCHAR(100),
    ADD COLUMN IF NOT EXISTS update_by   VARCHAR(100),
    ADD COLUMN IF NOT EXISTS is_deleted  SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS version_no  VARCHAR(20) NOT NULL DEFAULT '1',
    ADD COLUMN IF NOT EXISTS domain      VARCHAR(50) NOT NULL DEFAULT 'default';

ALTER TABLE IF EXISTS public.ecos_ontology_action ADD COLUMN IF NOT EXISTS create_time TIMESTAMP,
    ADD COLUMN IF NOT EXISTS update_time TIMESTAMP,
    ADD COLUMN IF NOT EXISTS create_by   VARCHAR(100),
    ADD COLUMN IF NOT EXISTS update_by   VARCHAR(100),
    ADD COLUMN IF NOT EXISTS is_deleted  SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS version_no  VARCHAR(20) NOT NULL DEFAULT '1',
    ADD COLUMN IF NOT EXISTS domain      VARCHAR(50) NOT NULL DEFAULT 'default';

ALTER TABLE IF EXISTS public.ecos_ontology_rule ADD COLUMN IF NOT EXISTS create_time TIMESTAMP,  -- V120 锚点已含同名列时 no-op
    ADD COLUMN IF NOT EXISTS update_time TIMESTAMP,
    ADD COLUMN IF NOT EXISTS create_by   VARCHAR(100),
    ADD COLUMN IF NOT EXISTS update_by   VARCHAR(100),
    ADD COLUMN IF NOT EXISTS is_deleted  SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS version_no  VARCHAR(20) NOT NULL DEFAULT '1',
    ADD COLUMN IF NOT EXISTS domain      VARCHAR(50) NOT NULL DEFAULT 'default';

ALTER TABLE IF EXISTS public.ecos_object_data ADD COLUMN IF NOT EXISTS create_time TIMESTAMP,
    ADD COLUMN IF NOT EXISTS update_time TIMESTAMP,
    ADD COLUMN IF NOT EXISTS create_by   VARCHAR(100),
    ADD COLUMN IF NOT EXISTS update_by   VARCHAR(100),
    ADD COLUMN IF NOT EXISTS is_deleted  SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS version_no  VARCHAR(20) NOT NULL DEFAULT '1',
    ADD COLUMN IF NOT EXISTS domain      VARCHAR(50) NOT NULL DEFAULT 'default';

ALTER TABLE IF EXISTS public.ecos_object_version ADD COLUMN IF NOT EXISTS create_time TIMESTAMP,
    ADD COLUMN IF NOT EXISTS update_time TIMESTAMP,
    ADD COLUMN IF NOT EXISTS create_by   VARCHAR(100),
    ADD COLUMN IF NOT EXISTS update_by   VARCHAR(100),
    ADD COLUMN IF NOT EXISTS is_deleted  SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS version_no  VARCHAR(20) NOT NULL DEFAULT '1',
    ADD COLUMN IF NOT EXISTS domain      VARCHAR(50) NOT NULL DEFAULT 'default';

ALTER TABLE IF EXISTS public.ecos_glossary_term ADD COLUMN IF NOT EXISTS create_time TIMESTAMP,
    ADD COLUMN IF NOT EXISTS update_time TIMESTAMP,
    ADD COLUMN IF NOT EXISTS create_by   VARCHAR(100),
    ADD COLUMN IF NOT EXISTS update_by   VARCHAR(100),
    ADD COLUMN IF NOT EXISTS is_deleted  SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS version_no  VARCHAR(20) NOT NULL DEFAULT '1',
    ADD COLUMN IF NOT EXISTS domain      VARCHAR(50) NOT NULL DEFAULT 'default';

ALTER TABLE IF EXISTS public.ecos_business_glossary ADD COLUMN IF NOT EXISTS create_time TIMESTAMP,
    ADD COLUMN IF NOT EXISTS update_time TIMESTAMP,
    ADD COLUMN IF NOT EXISTS create_by   VARCHAR(100),
    ADD COLUMN IF NOT EXISTS update_by   VARCHAR(100),
    ADD COLUMN IF NOT EXISTS is_deleted  SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS version_no  VARCHAR(20) NOT NULL DEFAULT '1',
    ADD COLUMN IF NOT EXISTS domain      VARCHAR(50) NOT NULL DEFAULT 'default';

-- ── ecos_ontology 镜像侧（V47/V122 登记的 0 行 ghost 或同名镜像；缺表仅 WARNING）──
ALTER TABLE IF EXISTS ecos_ontology.ecos_ontology_entity ADD COLUMN IF NOT EXISTS create_time TIMESTAMP,
    ADD COLUMN IF NOT EXISTS update_time TIMESTAMP,
    ADD COLUMN IF NOT EXISTS create_by   VARCHAR(100),
    ADD COLUMN IF NOT EXISTS update_by   VARCHAR(100),
    ADD COLUMN IF NOT EXISTS is_deleted  SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS version_no  VARCHAR(20) NOT NULL DEFAULT '1',
    ADD COLUMN IF NOT EXISTS domain      VARCHAR(50) NOT NULL DEFAULT 'default';

ALTER TABLE IF EXISTS ecos_ontology.ecos_ontology_property ADD COLUMN IF NOT EXISTS create_time TIMESTAMP,
    ADD COLUMN IF NOT EXISTS update_time TIMESTAMP,
    ADD COLUMN IF NOT EXISTS create_by   VARCHAR(100),
    ADD COLUMN IF NOT EXISTS update_by   VARCHAR(100),
    ADD COLUMN IF NOT EXISTS is_deleted  SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS version_no  VARCHAR(20) NOT NULL DEFAULT '1',
    ADD COLUMN IF NOT EXISTS domain      VARCHAR(50) NOT NULL DEFAULT 'default';

ALTER TABLE IF EXISTS ecos_ontology.ecos_ontology_relationship ADD COLUMN IF NOT EXISTS create_time TIMESTAMP,
    ADD COLUMN IF NOT EXISTS update_time TIMESTAMP,
    ADD COLUMN IF NOT EXISTS create_by   VARCHAR(100),
    ADD COLUMN IF NOT EXISTS update_by   VARCHAR(100),
    ADD COLUMN IF NOT EXISTS is_deleted  SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS version_no  VARCHAR(20) NOT NULL DEFAULT '1',
    ADD COLUMN IF NOT EXISTS domain      VARCHAR(50) NOT NULL DEFAULT 'default';

ALTER TABLE IF EXISTS ecos_ontology.ecos_ontology_action ADD COLUMN IF NOT EXISTS create_time TIMESTAMP,
    ADD COLUMN IF NOT EXISTS update_time TIMESTAMP,
    ADD COLUMN IF NOT EXISTS create_by   VARCHAR(100),
    ADD COLUMN IF NOT EXISTS update_by   VARCHAR(100),
    ADD COLUMN IF NOT EXISTS is_deleted  SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS version_no  VARCHAR(20) NOT NULL DEFAULT '1',
    ADD COLUMN IF NOT EXISTS domain      VARCHAR(50) NOT NULL DEFAULT 'default';

ALTER TABLE IF EXISTS ecos_ontology.ecos_ontology_rule ADD COLUMN IF NOT EXISTS create_time TIMESTAMP,
    ADD COLUMN IF NOT EXISTS update_time TIMESTAMP,
    ADD COLUMN IF NOT EXISTS create_by   VARCHAR(100),
    ADD COLUMN IF NOT EXISTS update_by   VARCHAR(100),
    ADD COLUMN IF NOT EXISTS is_deleted  SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS version_no  VARCHAR(20) NOT NULL DEFAULT '1',
    ADD COLUMN IF NOT EXISTS domain      VARCHAR(50) NOT NULL DEFAULT 'default';

ALTER TABLE IF EXISTS ecos_ontology.ecos_object_data ADD COLUMN IF NOT EXISTS create_time TIMESTAMP,
    ADD COLUMN IF NOT EXISTS update_time TIMESTAMP,
    ADD COLUMN IF NOT EXISTS create_by   VARCHAR(100),
    ADD COLUMN IF NOT EXISTS update_by   VARCHAR(100),
    ADD COLUMN IF NOT EXISTS is_deleted  SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS version_no  VARCHAR(20) NOT NULL DEFAULT '1',
    ADD COLUMN IF NOT EXISTS domain      VARCHAR(50) NOT NULL DEFAULT 'default';

ALTER TABLE IF EXISTS ecos_ontology.ecos_object_version ADD COLUMN IF NOT EXISTS create_time TIMESTAMP,
    ADD COLUMN IF NOT EXISTS update_time TIMESTAMP,
    ADD COLUMN IF NOT EXISTS create_by   VARCHAR(100),
    ADD COLUMN IF NOT EXISTS update_by   VARCHAR(100),
    ADD COLUMN IF NOT EXISTS is_deleted  SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS version_no  VARCHAR(20) NOT NULL DEFAULT '1',
    ADD COLUMN IF NOT EXISTS domain      VARCHAR(50) NOT NULL DEFAULT 'default';

ALTER TABLE IF EXISTS ecos_ontology.ecos_business_glossary ADD COLUMN IF NOT EXISTS create_time TIMESTAMP,
    ADD COLUMN IF NOT EXISTS update_time TIMESTAMP,
    ADD COLUMN IF NOT EXISTS create_by   VARCHAR(100),
    ADD COLUMN IF NOT EXISTS update_by   VARCHAR(100),
    ADD COLUMN IF NOT EXISTS is_deleted  SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS version_no  VARCHAR(20) NOT NULL DEFAULT '1',
    ADD COLUMN IF NOT EXISTS domain      VARCHAR(50) NOT NULL DEFAULT 'default';

-- ── 回填说明（幂等）: 已有行的 is_deleted/version_no/domain 由 DEFAULT 自动落值；
--   create_time/update_time 故意不带默认（避免把"补列时刻"伪装成"创建时刻"），
--   历史行留 NULL，代码写入时显式赋值。无 UPDATE 数据迁移段。
-- ── 回滚说明: ADD COLUMN 按 IR03 不回收（只加不删）；如需回退仅停读新列即可，数据零损失。

-- ── DDL Lint Self-audit ──────────────────────────────────
-- [DR01] 全语句 schema 限定（public. / ecos_ontology.）✓  [DR06] 五列 ✓  [DR07] version_no VARCHAR(20) ✓  [DR08] domain ✓
-- [DR05] is_deleted SMALLINT NOT NULL DEFAULT 0 ✓
-- [MC02/MC03] 无 JSON 类型 / 无 :: cast / 无 partial index / 时间语义列 TIMESTAMP；本脚本无金额列（ST03-A 无登记项）✓
-- [IR03] 只加不删：无 DROP / 无 RENAME / 无 ALTER COLUMN TYPE；旧 created_at 保留双写过渡 ✓
-- [ST07] 仅触白名单 schema public + ecos_ontology，不新建 schema ✓  [IR02] 手动 psql，未实跑 ✓
