-- V278 (卷09 v1.2 §十一 H-3 / FC-05 期间关账真身): 控制域期间状态表 ecos_data.ecos_data_period_status 新建（关账态由"配置期锁"升级为 DB 事实源）
-- 追溯: 卷09 v1.2 §一.10（实测基线："关账当前形态 = 配置期锁，非 period_status 表"，OpenPeriodsPeriodLock:19-25）；需求依据 PRD-09 v1.2 §5.1（关账由财务角色操作并发审计事件；CLOSED 期间 ACTUAL 补录走审批；历史快照永不覆盖）
-- 批准: 卷09 v1.2 §十一 H-3 移交条目（裁定 period_status/closePeriod 落点，属主=本卷02/数据域，本脚本即裁定落地形态）
-- 状态: 仅落迁移脚本文件，**未对 ecos-postgres 实跑**（实跑属未授权项，需逐项再确认后按 IR02 手动 psql 执行）
-- 纪律: DR01~DR08 / MC01~MC03 / ST03-A / ST07（控制域表落 ecos_data） / IR03(只加不删) / IR02(手动 psql)
--
-- ── 表定位与裁定口径 ────────────────────────────────────────
-- ① 控制域表（期间开关是平台控制面状态，不是业务金额事实）⇒ 落 ecos_data（8 schema 封闭枚举内控制域）。
-- ② 关账语义 = 状态行 INSERT/UPDATE（OPEN→CLOSED 单向），事实表历史行**永不回写**（PRD-09 §5.1；
--    "关账后不覆盖历史 run 数据"由应用侧保证：PeriodLock 命中 CLOSED → 导入链 409 ECOS-DATA-022）。
-- ③ status 枚举 CHECK 本批不建（R-15/V197 同纪律），应用侧常量 OPEN/CLOSED 约束。
-- ④ close_detail_json TEXT（DR04: _json 后缀、仅描述用、禁 WHERE/JOIN/索引——MC02）。
CREATE TABLE IF NOT EXISTS ecos_data.ecos_data_period_status (
    id                VARCHAR(36)   NOT NULL,                 -- MC01: 应用侧生成 UUID，DDL 无默认值
    period            VARCHAR(7)    NOT NULL,                 -- 'YYYY-MM'（PeriodLock 端口既有形态）
    status            VARCHAR(10)   NOT NULL DEFAULT 'OPEN',  -- OPEN/CLOSED（应用侧枚举约束）
    closed_by         VARCHAR(64),                            -- 关账操作者（财务角色，经 OPA 裁决；OPEN 行为 NULL）
    closed_at         TIMESTAMP,                              -- 关账时点（OPEN 行为 NULL）
    close_reason      VARCHAR(500),                           -- 关账说明（月结/审批单号等自由文本）
    close_detail_json TEXT,                                   -- DR04: 受控 JSON 序列化 TEXT，仅描述用
    -- DR06~DR08 七基线列
    domain            VARCHAR(50)   NOT NULL DEFAULT 'default',
    version_no        VARCHAR(20)   NOT NULL DEFAULT '1',
    is_deleted        SMALLINT      NOT NULL DEFAULT 0,
    create_by         VARCHAR(64)   NOT NULL DEFAULT 'system',
    update_by         VARCHAR(64),
    create_time       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time       TIMESTAMP,
    CONSTRAINT pk_ecos_data_period_status PRIMARY KEY (id)
);

-- ── 业务唯一（任务裁定：UNIQUE 约束承载，period 单活）──────────
CREATE UNIQUE INDEX IF NOT EXISTS uniq_edps_period
    ON ecos_data.ecos_data_period_status (period, is_deleted);
-- 关账判定缓存查询（SELECT period WHERE status='CLOSED' AND is_deleted=0）支撑索引
CREATE INDEX IF NOT EXISTS idx_edps_status ON ecos_data.ecos_data_period_status (status, is_deleted);

-- ── 注释登记（DR06）──────────────────────────────────────────
COMMENT ON TABLE  ecos_data.ecos_data_period_status IS '期间关账状态表（卷09 H-3：控制域真身；关账=OPEN→CLOSED 单向；历史事实行永不回写；降级路径=配置期锁 ecos.data.facts.closed-periods）';
COMMENT ON COLUMN ecos_data.ecos_data_period_status.period IS '期间键 YYYY-MM（与 PeriodLock 端口入参同形）';
COMMENT ON COLUMN ecos_data.ecos_data_period_status.status IS 'OPEN/CLOSED（应用侧枚举；CLOSED 后导入链对该期间 409 ECOS-DATA-022）';
COMMENT ON COLUMN ecos_data.ecos_data_period_status.closed_by IS '关账操作者（security-engine OPA 财务角色裁决通过后写入；禁硬编码角色映射）';
COMMENT ON COLUMN ecos_data.ecos_data_period_status.close_detail_json IS '关账附加上下文序列化 TEXT（受控 JSON，仅描述用，禁 WHERE/JOIN/索引，MC02）';

-- ── 回滚说明 ──────────────────────────────────────────────
-- 本表新建只增（IR03）；如需回滚 = 配置 ecos.data.facts.period-lock=config 即回到配置期锁
-- 降级形态（DatabasePeriodLock 停装配，OpenPeriodsPeriodLock 接管），表体保留不清算子。
-- 幂等性: CREATE TABLE/INDEX IF NOT EXISTS，重复执行零报错、零重复。

-- ── DDL Lint Self-audit ──────────────────────────────────────
-- [DR01] schema 限定 ecos_data. ✓  [DR02] ecos_ 前缀 ✓  [DR03] 单数列名 ✓（period/status/closed_by…）
-- [DR04] close_detail_json TEXT 且 _json 后缀 ✓  [DR05] is_deleted SMALLINT ✓
-- [DR06] 审计六列（create_by/create_time/update_by/update_time + is_deleted）✓  [DR07] version_no VARCHAR(20) ✓  [DR08] domain VARCHAR(50) ✓
-- [MC01] PK VARCHAR(36) 应用侧 UUID、无默认值、无 gen_random_uuid ✓
-- [MC02] 无 JSONB/数组/二进制；无数值列（状态表无金额，ST03-A 不适用本表）✓
-- [MC03] 无裸 PG 语法（无 ON CONFLICT/RETURNING/::）；普通索引非条件索引 ✓
-- [ST07] 控制域表落 ecos_data ✓  [IR02] 手动 psql ✓  [IR03] 只加不删 ✓
-- [应用侧配套] 关账端点 POST /api/v1/datanet/facts/periods/{yyyMM}/close 经 OPA 裁决 + ecos.audit 事件（runtime-event 单通道），见 H-3 交付代码
