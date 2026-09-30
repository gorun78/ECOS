-- V187 (卷05 附录 E.2 / F05-02 要点1): 认知模型资产注册表 ecos_cognitive_mind 新建（E1 数据源从"不存在"建成）
-- 追溯: W116/C98（E1 数据源物理缺失，全库无 mind 表）、W117/C99（kb_* 误名正名 cognitive_*，ADR-8 明令）；需求依据 REQ-COG-01（E1 detect 数据源）
-- 批准: 需求检视报告 §十四 14.1（2026-09-29）之 R-13 ①（认知真身归位 ecos_cognitive：本批新建 mind 两表入 ecos_cognitive + 存量 belief/hypothesis 原地合规化）；执行边界 §14.4 = 脚本文件落地
-- 状态: 仅落迁移脚本文件，**未对 ecos-postgres 实跑**（实跑属 §14.4 未授权项，需逐项再确认）
-- 纪律: DR01~DR08 / MC01~MC03 / ST03-A / ST07 / IR02(手动 psql) / IR03(只加不删；存量 11 张错放表只停写)
--
-- ── schema 占用声明（R-13 ① 已批准口径）──────────────────────
-- ecos_cognitive 现被 11 张 0 行的 ecos_biz_* / ecos_wm_*（业务域/世界模型错放表）占用
-- （W124/C106，schema 反义）。本批对该 11 张表**只定性停写，不 DROP、不迁移、不改名**；
-- 本脚本仅在同一 schema 内新建认知真身表，不触碰错放表。
--
-- ── 与文档 E.2 草案的合规修正（红线优先，语义不变）────────────
-- 1) capability_mask / closed_loop_bounds → capability_mask_json / closed_loop_bounds_json
--    （DR04：JSON 语义列必带 _json 后缀 + TEXT 形态；契约出参字段名 capabilityMask/closedLoopBounds
--     由应用侧 MindCapabilityMaskCodec 映射，列名不影响 API 契约）；
-- 2) version_no INTEGER → VARCHAR(20) NOT NULL DEFAULT '1'（DR07 唯一形态）；
-- 3) domain VARCHAR(64) DEFAULT 'DEFAULT' → VARCHAR(50) NOT NULL DEFAULT 'default'（DR08 唯一形态）；
-- 4) 草案中的 CHECK 约束（status 枚举 / is_active 0-1 / capability_mask 形如 JSON 数组）本批**不建**：
--    与 R-15 同纪律——枚举/形态守护先由应用层（MindCapabilityMask 枚举 + Mapper 白名单）把关，
--    CHECK 待应用全量切换后补建（禁"先加约束后改代码"造成线上拒写）。此处登记，不视为遗漏。
-- 5) JSON 列一律 TEXT（MC02 新表禁二进制 JSON 列）；主键 VARCHAR(36) 应用侧生成 UUID，
--    DDL 无默认值（MC01，禁序列/UUID 函数默认值）。

-- ── 1. 新表 ecos_cognitive.ecos_cognitive_mind ────────────────────
CREATE TABLE IF NOT EXISTS ecos_cognitive.ecos_cognitive_mind (
    id                    VARCHAR(36)   NOT NULL,               -- MC01: 应用侧生成 UUID，DDL 无默认值
    mind_key              VARCHAR(120)  NOT NULL,               -- Mind 业务键（跨版本稳定标识）
    name                  VARCHAR(200)  NOT NULL,               -- 展示名
    description           TEXT,                                 -- 描述
    capability_mask_json  TEXT          NOT NULL DEFAULT '[]',  -- 能力掩码 JSON 数组字符串（DETECT/FORECAST/SIMULATE/PLAN/REVIEW，REQ-COG-04）；DR04 _json+TEXT
    closed_loop_bounds_json TEXT        NOT NULL DEFAULT '[]',  -- 闭环边界 JSON 数组字符串，如 ["PRE","POST"]；DR04
    status                VARCHAR(20)   NOT NULL DEFAULT 'DRAFT', -- DRAFT/PUBLISHED/SUPERSEDED（枚举校验延后，见头注 4）
    is_active             SMALLINT      NOT NULL DEFAULT 1,     -- DR05 is_* SMALLINT
    mind_version          VARCHAR(20)   NOT NULL,               -- Mind 资产版本号（*_version→VARCHAR(20)）
    trace_id              VARCHAR(64),                          -- 链路追踪号
    -- DR06~DR08 七基线列
    domain                VARCHAR(50)   NOT NULL DEFAULT 'default', -- DR08
    version_no            VARCHAR(20)   NOT NULL DEFAULT '1',       -- DR07
    is_deleted            SMALLINT      NOT NULL DEFAULT 0,         -- DR06
    create_by             VARCHAR(64)   NOT NULL,
    update_by             VARCHAR(64),
    create_time           TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time           TIMESTAMP,
    CONSTRAINT pk_ecos_cognitive_mind PRIMARY KEY (id)
);

-- ── 2. 索引（全部普通索引，禁条件索引 MC03；逻辑删除列并入复合键代偿）──
CREATE UNIQUE INDEX IF NOT EXISTS uniq_cm_key_ver
    ON ecos_cognitive.ecos_cognitive_mind (mind_key, mind_version, is_deleted);
CREATE INDEX IF NOT EXISTS idx_cm_active
    ON ecos_cognitive.ecos_cognitive_mind (is_active, is_deleted);

-- ── 3. 注释登记 ──────────────────────────────────────────────
COMMENT ON TABLE  ecos_cognitive.ecos_cognitive_mind IS '认知模型资产注册表（ADR-8 正名 cognitive_*，禁 kb_* 误名；R-13 ① 归位 ecos_cognitive；旧 E1 数据源 kb_mind_registry 仅存于文档，无库内对象）';
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_mind.capability_mask_json  IS '能力掩码 TEXT JSON 数组；序列化只经 MindCapabilityMaskCodec（REQ-COG-04，枚举 DETECT/FORECAST/SIMULATE/PLAN/REVIEW）';
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_mind.closed_loop_bounds_json IS '闭环边界 TEXT JSON 数组，枚举 PRE/POST；非法值读侧容错为 [] + issue 记录（F05-03）';
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_mind.status IS 'DRAFT/PUBLISHED/SUPERSEDED；CHECK 约束待应用全量切换后补建（本批不建，见头注）';

-- ── 4. 幂等与回滚说明 ────────────────────────────────────────
-- 幂等: CREATE TABLE/INDEX IF NOT EXISTS，重复执行无副作用；无存量数据动作（新表 0 行起步）。
-- 回滚: 本批不建 CHECK、不改任何既有表；如需撤销应用接入，只停写本表即可（IR03 禁 DROP；
--       本表为新建表，若确未实跑过本脚本则库内无对象，无需回滚动作）。

-- ── DDL Lint Self-audit ──────────────────────────────────────
-- [DR01] 全列 schema 限定 + 小写下划线 ✓  [DR02] 新表定名 ecos_cognitive_mind ✓ —— 既保留 ADR-8 正名词干 cognitive_*（禁 kb_* 误名），又满足 DR02 ecos_ 前缀（2026-09-30 收口：原稿沿用文档字面 cognitive_mind 缺前缀；实测本机库无该表、Java/XML/TS 零引用，改名无兼容代价）
-- [DR03] 单数 ✓  [DR04] JSON 语义列 _json 后缀 + TEXT ✓  [DR05] is_active/is_deleted SMALLINT ✓
-- [DR06] 审计五列 create_time/update_time/create_by/update_by/is_deleted ✓  [DR07] version_no VARCHAR(20) NOT NULL ✓
-- [DR08] domain VARCHAR(50) NOT NULL DEFAULT 'default' ✓  [MC01] 主键 VARCHAR(36) DDL 无默认值 ✓
-- [MC02] 无二进制 JSON 列、无裸 NUMERIC ✓  [MC03] 无条件索引/无 RLS/无分区/无数组列/无时区戳列/无裸 cast ✓
-- [ST03-A] 本表无金额/比率/概率列，无逐列登记项 ✓  [ST07] 落 ecos_cognitive（控制域五引擎 schema 白名单内）✓
-- [IR02] 手动 psql 执行（Flyway 禁用）✓  [IR03] 只加不删；11 张错放表只停写不动 ✓
-- [R-13 ①] 本批 = 新建 mind 两表入 ecos_cognitive + 存量原地合规化 ✓  [R-15 同纪律] 本批不建 CHECK ✓
