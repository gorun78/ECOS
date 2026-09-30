-- V188 (卷05 附录 E.2 / F05-02 要点1·3): 场景↔Mind 绑定表 ecos_cognitive_scenario_mind 新建（替代库内缺失的 legacy ecos_scenario_mind/V146）
-- 追溯: W116/C98（mind_id 列全库 0 个；V146 迁移在、库内无表而 ScenarioMindService 仍 INSERT）；需求依据 REQ-COG-01（E1 JOIN 数据源）
-- 批准: 需求检视报告 §十四 14.1（2026-09-29）之 R-13 ①（新建 mind 两表入 ecos_cognitive，本表为第二张）；执行边界 §14.4 = 脚本文件落地
-- 状态: 仅落迁移脚本文件，**未对 ecos-postgres 实跑**（实跑属 §14.4 未授权项，需逐项再确认）
-- 纪律: DR01~DR08 / MC02~MC03 / MC01 / ST03-A / ST07 / IR02(手动 psql) / IR03(只加不删；legacy 名不重建)
--
-- ── legacy 处置（E.3 定版口径）───────────────────────────────
-- V146 定义的 public.ecos_scenario_mind（BIGINT 自增主键 + 二进制 JSON 列 + 两个条件唯一索引，
-- 违 MC01/MC02/MC03 多处）实测库内不存在。按 E.3/F05-02 要点3：**以本表 ecos_cognitive_scenario_mind
-- 替代，不重建 legacy 名**（避免第 4 套孪生）；ScenarioMindService.java:84-115/119 的旧 INSERT
-- 应用侧改指新表，切换前先停写（禁双写造成不可归因）。存量迁移数据动作为零：legacy 表库内
-- 无对象、无行，不存在需要搬运的数据。
--
-- ── 与文档 E.2 草案的合规修正（红线优先，语义不变）────────────
-- 1) version_no INTEGER → VARCHAR(20) NOT NULL DEFAULT '1'（DR07）；
-- 2) domain VARCHAR(64) DEFAULT 'DEFAULT' → VARCHAR(50) NOT NULL DEFAULT 'default'（DR08）；
-- 3) 草案 CHECK（binding_role IN ('PRIMARY','SUPPORTING')）本批不建（与 R-15 同纪律，
--    应用层枚举 + Mapper 白名单先把关，CHECK 待全量切换后补，此处登记）。

-- ── 1. 新表 ecos_cognitive.ecos_cognitive_scenario_mind ───────────
CREATE TABLE IF NOT EXISTS ecos_cognitive.ecos_cognitive_scenario_mind (
    id            VARCHAR(36)  NOT NULL,                 -- MC01: 应用侧生成 UUID，DDL 无默认值
    scenario_id   VARCHAR(36)  NOT NULL,                 -- 场景引用（UUID 主键指向）
    mind_id       VARCHAR(36)  NOT NULL,                 -- Mind 引用 → ecos_cognitive.ecos_cognitive_mind.id（全库首个 mind_id 列，X-19 收敛；不建跨 schema 外键）
    binding_role  VARCHAR(20)  NOT NULL DEFAULT 'PRIMARY', -- PRIMARY/SUPPORTING（枚举 CHECK 延后，见头注）
    priority      INTEGER      NOT NULL DEFAULT 100,     -- 排序权重（小者优先）
    trace_id      VARCHAR(64),                           -- 链路追踪号
    -- DR06~DR08 七基线列
    domain        VARCHAR(50)  NOT NULL DEFAULT 'default',
    version_no    VARCHAR(20)  NOT NULL DEFAULT '1',
    is_deleted    SMALLINT     NOT NULL DEFAULT 0,
    create_by     VARCHAR(64)  NOT NULL,
    update_by     VARCHAR(64),
    create_time   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP,
    CONSTRAINT pk_ecos_cognitive_scenario_mind PRIMARY KEY (id)
);

-- ── 2. 索引（普通唯一索引，逻辑删除列并入键代偿条件索引禁令 MC03）──
CREATE UNIQUE INDEX IF NOT EXISTS uniq_csm_scn_mind
    ON ecos_cognitive.ecos_cognitive_scenario_mind (scenario_id, mind_id, is_deleted);
CREATE INDEX IF NOT EXISTS idx_csm_mind
    ON ecos_cognitive.ecos_cognitive_scenario_mind (mind_id, is_deleted);

-- ── 3. 注释登记 ──────────────────────────────────────────────
COMMENT ON TABLE  ecos_cognitive.ecos_cognitive_scenario_mind IS '场景↔Mind 绑定关系表（定义态资产，R-13 ① 落 ecos_cognitive；替代 legacy ecos_scenario_mind/V146，不重建 legacy 名；E1 detect = ecos_cognitive_mind × 本表 JOIN，仅取 is_active=1 且 is_deleted=0）';
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_scenario_mind.mind_id IS '首个 mind_id 列（X-19：旧库全库 0 个）；指向 ecos_cognitive.ecos_cognitive_mind.id，FK 不建（跨表约束经应用层完整性校验）';
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_scenario_mind.binding_role IS 'PRIMARY/SUPPORTING；CHECK 约束待应用全量切换后补建（本批不建，见头注）';

-- ── 4. 幂等与回滚说明 ────────────────────────────────────────
-- 幂等: CREATE TABLE/INDEX IF NOT EXISTS，重复执行无副作用；无存量数据动作。
-- 回滚: 只新增对象、未改任何既有表；撤销 = 应用侧停写本表即可（IR03 禁 DROP）。

-- ── DDL Lint Self-audit ──────────────────────────────────────
-- [DR01] schema 限定 + 小写下划线 ✓  [DR02] 新表定名 ecos_cognitive_scenario_mind ✓ —— 保留 ADR-8 正名词干 cognitive_* 且带 ecos_ 前缀（2026-09-30 收口；实测库内无该表、代码零引用，与 legacy ecos_scenario_mind 名不重叠）
-- [DR03] 单数 ✓  [DR04] 本表无 JSON 语义列 ✓  [DR05] is_deleted SMALLINT ✓
-- [DR06] 审计五列 ✓  [DR07] version_no VARCHAR(20) NOT NULL ✓  [DR08] domain VARCHAR(50) NOT NULL DEFAULT 'default' ✓
-- [MC01] 主键 VARCHAR(36) DDL 无默认值 ✓  [MC02] 无二进制 JSON 列、无裸 NUMERIC ✓
-- [MC03] 无条件索引/无 RLS/无分区/无数组列/无时区戳列/无裸 cast ✓  [外键] 不建（跨表完整性走应用层）✓
-- [ST03-A] 无金额/比率/概率列，无逐列登记项 ✓  [ST07] 落 ecos_cognitive 白名单 ✓
-- [IR02] 手动 psql 执行 ✓  [IR03] 只加不删；legacy V146 表不重建不改名 ✓
-- [R-13 ①] 新建 mind 两表之二 ✓  [R-15 同纪律] 本批不建 CHECK ✓
