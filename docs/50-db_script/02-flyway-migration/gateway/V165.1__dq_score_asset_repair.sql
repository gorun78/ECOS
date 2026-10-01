-- V165.1 (卷02 §E.3，版本号=卷02 定版子版本，避让卷01 V165): DQ 资产评分表补齐（V112 声明未落库的缺表修复，关闭 W48）
-- 追溯: W48→C37（dq_score_asset DDL 声明未落库，SchemaDriftLintTest 库表 vs 单源差集为空）；需求依据 REQ-DATA-02/04
-- 批准: 需求检视报告 §十四 14.1（2026-09-29）之 R-1b ②（DQ 归数据引擎 schema，ecos_dq 承认并入 ecos_data）；执行边界 §14.4 = 脚本文件落地
-- 状态: 仅落迁移脚本文件，**未对 ecos-postgres 实跑**（实跑属 §14.4 未授权项，需逐项再确认）
-- 纪律: DR01~DR08 / MC01~MC03 / ST03-A / ST07 / IR02(手动 psql) / IR03(只加不删)
-- {control} 解析: 依已批准 R-1b ②，本册 E.3 的 {control} 落点解析为 `ecos_data`（文档字面 SQL 仅替换 schema 限定名，列形态逐字采用）。
--   既有 ecos_dq.dq_score_asset（V112 形态含违规类型列）= knownLegacy 只停写不迁；消费侧 Mapper 切限定名 + 配置注入
--   （MC06，DqScoreServiceImpl 读表改 ecos_data.）属业务改造批次，本批不动代码。
-- 幂等: CREATE TABLE/INDEX IF NOT EXISTS，重跑无副作用。回滚说明: 新表无存量数据依赖，如需回滚仅停写（禁 DROP，IR03）。
--
-- 【2026-09-30 校订两处】
--   ① 表名收口 DR02：文档字面 `dq_score_asset` 无 ecos_ 前缀，违 DR02（新表强制前缀，反例即"无 ecos_"）。
--      只读取证：本机 ecos-postgres 中 `%score_asset%` **零命中**（W48"声明未落库"实证成立）→ 新表命名无历史包袱，
--      故定名 `ecos_data.ecos_dq_score_asset`；差异已登记，消费侧切换仍属业务改造批次（本批不动代码）。
--   ② DR08 默认值收口：文档字面 DEFAULT 'dq' 违 DR08 规定形态（DEFAULT 'default'）→ 改为 'default'，
--      子系统区分仍由 asset_type/调用侧显式值表达。
--   ③ 形态差集登记（不改本脚本，交签核）：V112 声明形态列为 rolled_up_scores/last_evaluated_at，
--      而 Java 侧实际引用为 `rolled_score`（DqReportServiceImpl:339）、`ecos_dq.dq_score_asset`（15+ 处，含 ON CONFLICT 写法 DqScoreService:46）
--      与本册文档形态 dimensions_json/snapshot_at 三者互不一致 = W38/W48 类 DDL 单源漂移的又一实证。
--      本表按已批准文档形态建表（列名走 DR04 的 _json 后缀），列收敛与 Mapper 切换登记为后续批次整改项。

-- ── 1. 缺失表补齐（卷02 §E.3 L608-626 字面 SQL，表名/默认值按 ①② 收口）──
CREATE TABLE IF NOT EXISTS ecos_data.ecos_dq_score_asset (
    id            VARCHAR(36) PRIMARY KEY,
    asset_type    VARCHAR(20) NOT NULL,
    asset_id      VARCHAR(64) NOT NULL,
    overall_score NUMERIC(5,2) NOT NULL,
    grade         VARCHAR(4)   NOT NULL,
    dimensions_json TEXT,                      -- MC02：TEXT，不参与检索
    snapshot_at   TIMESTAMP    NOT NULL,
    domain        VARCHAR(50)  NOT NULL DEFAULT 'default',   -- DR08 规定形态；子系统值由写入侧显式提供
    version_no    VARCHAR(20)  NOT NULL,
    is_deleted    SMALLINT     NOT NULL DEFAULT 0,
    create_time   TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_time   TIMESTAMP    NOT NULL DEFAULT NOW(),
    create_by     VARCHAR(100),
    update_by     VARCHAR(100)
);
CREATE INDEX IF NOT EXISTS idx_ecos_dq_score_asset_asset ON ecos_data.ecos_dq_score_asset(asset_type, asset_id);

-- ── DDL Lint Self-audit ──────────────────────────────────
-- [DR01] ecos_data. 限定 ✓（无裸表名）  [DR04] dimensions_json 后缀 + TEXT 类型非检索 ✓
-- [DR05] is_deleted SMALLINT ✓  [DR06] 审计五列 create_time/update_time/create_by/update_by/is_deleted ✓
-- [DR07] version_no VARCHAR(20) NOT NULL ✓  [DR08] domain VARCHAR(50) NOT NULL DEFAULT 'default' ✓（文档字面 'dq' 已收口，见头注②）
-- [MC01] 主键 VARCHAR(36) 应用侧 UUID，DDL 无默认值函数 ✓
-- [MC02] overall_score 带精度 NUMERIC(5,2)（评分非金额，形态按文档字面）；无 JSON 二进制列 ✓
-- [MC03] 无库内策略语句/分区/数组列/带时区时间类型/裸 cast ✓；idx_ecos_dq_score_asset_asset 为非条件索引 ✓
-- [ST07] DQ 治理表落数据引擎 schema ecos_data（R-1b ② 已批准口径）✓  [ST09] 零 FOREIGN KEY ✓
-- [DR02] 新表定名 ecos_dq_score_asset 带 ecos_ 前缀 ✓（文档字面 dq_score_asset 违 DR02，已收口，差异登记头注①）
-- [IR03] 既有 ecos_dq.dq_score_asset（V112 声明形态，实测未落库）不动不删 ✓
