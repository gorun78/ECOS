-- V177 (卷04 §E.2 逐字块 + R-8 ② 拆分): 情景假设 定义态(ecos_knowledge) × 数值事实(ecos_dw)
-- 追溯: K-48（假设表形态违规）→ W106/C88；REQ-KB-03 / F04-07；错误码 ECOS-KB-060/071/072
-- 批准: 需求检视报告 §十四 14.1（2026-09-29）之 R-8=②（数值事实落业务域 ecos_dw + 写通道唯一，版本/审批语义留 K 层）；
--       R-9=①（假设 value 属派生统计量，ST03-A 逐列登记，不加密）；执行边界 §14.4 = 脚本文件落地
-- 状态: 仅落迁移脚本文件，**未对 ecos-postgres 实跑**（实跑属 §14.4 未授权项，需逐项再确认）
-- 纪律: DR01~DR08 / MC01~MC03 / ST03-A / ST07 / IR02(手动 psql) / IR03(只加不删；R-12 DROP 例外前置未闭环 ⇒ 本批零 DROP)
--
-- 列分布（同 R-8 ② 口径，与 V176 一致）:
--   ecos_knowledge.ecos_kb_assumption     定义态：key/scenario/metric/dim_scope_json/value_type/value_semantic/
--                                          value_ref / 有效期 / reason / evidence_ref / is_unverified /
--                                          版本 / 审批 / 基线列
--   ecos_dw.ecos_kb_assumption_value      数值事实：value NUMERIC(18,4)（value_type=AMOUNT 时可能反推
--                                          业务金额分布 ⇒ ST03-A 逐列登记；写方 = data-engine 代 kb 写，
--                                          但假设值为人工维护知识 ⇒ 应用侧经 data-engine 写通道落库，kb 不直写）
--   两表间 value_ref 弱关联，**不建跨 schema FOREIGN KEY**。
--
-- 【2026-09-30 校订】独立复核后三处收口（仅脚本文件，不实跑；两表均零行、代码零引用，改列名/类型无回归）：
--   ① DR05：文档字面布尔列 `unverified` → `is_unverified`（DR05 强制 is_ 前缀）。
--   ② DR07：version_no INTEGER → VARCHAR(20) NOT NULL DEFAULT '1'。
--   ③ DR08：domain VARCHAR(64) NOT NULL（无 DEFAULT）→ VARCHAR(50) NOT NULL DEFAULT 'default'。

-- schema 前置（ST07 禁止迁移脚本内 CREATE SCHEMA）：目标 schema = ecos_knowledge / ecos_dw 须已存在；
--   2026-09-30 只读实测本机库 sys_man 内上述 schema 均已实存（登记见 docs/40-实现/DDL迁移脚本落地登记-2026-09-30.md §9.2），故原 CREATE SCHEMA 语句删除，不预置。
CREATE TABLE IF NOT EXISTS ecos_knowledge.ecos_kb_assumption (
    id                 VARCHAR(36)    NOT NULL,
    assumption_key     VARCHAR(255)   NOT NULL,       -- scenarioType|metric|dims 规范化键
    scenario_type      VARCHAR(32)    NOT NULL,       -- BASE/CONSERVATIVE/AGGRESSIVE/自定义
    metric_code        VARCHAR(64)    NOT NULL,
    dim_scope_json          TEXT           NOT NULL DEFAULT '{}',   -- JSON 文本（MC02）
    value_type         VARCHAR(10)    NOT NULL,       -- RATE/AMOUNT/DAYS
    value_ref          VARCHAR(36),                   -- R-8 ②: 指向 ecos_dw.ecos_kb_assumption_value.id（弱关联）
    value_semantic     VARCHAR(20)    NOT NULL DEFAULT 'ABSOLUTE',  -- ABSOLUTE/DELTA
    base_assumption_id VARCHAR(36),
    valid_from         VARCHAR(7)     NOT NULL,
    valid_to           VARCHAR(7),
    reason             VARCHAR(500)   NOT NULL,       -- 必填（PRD-04 §3.2-3）
    evidence_ref       VARCHAR(255),
    is_unverified      SMALLINT       NOT NULL DEFAULT 0,   -- evidence_ref 缺失即 1（DR05：is_ 前缀）
    expire_at          TIMESTAMP,
    assumption_version VARCHAR(20)    NOT NULL,
    status             VARCHAR(20)    NOT NULL DEFAULT 'DRAFT',  -- DRAFT/PENDING_APPROVAL/ACTIVE/EXPIRED/SUPERSEDED
    approved_by        VARCHAR(100),
    approved_time      TIMESTAMP,
    trace_id           VARCHAR(64),
    domain             VARCHAR(50)    NOT NULL DEFAULT 'default',
    version_no         VARCHAR(20)    NOT NULL DEFAULT '1',
    is_deleted         SMALLINT       NOT NULL DEFAULT 0,
    create_by          VARCHAR(64)    NOT NULL,
    update_by          VARCHAR(64)    NOT NULL,
    create_time        TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time        TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_ecos_kb_assumption PRIMARY KEY (id),
    CONSTRAINT ck_kba_status CHECK (status IN ('DRAFT','PENDING_APPROVAL','ACTIVE','EXPIRED','SUPERSEDED')),
    CONSTRAINT ck_kba_sem    CHECK (value_semantic IN ('ABSOLUTE','DELTA')),
    CONSTRAINT ck_kba_vt     CHECK (value_type IN ('RATE','AMOUNT','DAYS'))
);
CREATE UNIQUE INDEX IF NOT EXISTS uniq_kba_key_ver ON ecos_knowledge.ecos_kb_assumption(assumption_key, assumption_version, is_deleted);
CREATE INDEX IF NOT EXISTS idx_kba_scenario_status ON ecos_knowledge.ecos_kb_assumption(scenario_type, status, is_deleted);
CREATE INDEX IF NOT EXISTS idx_kba_expire ON ecos_knowledge.ecos_kb_assumption(expire_at, status);

-- ── 2. 数值事实（R-8 ②，业务域 ecos_dw；精度承 §E.2 逐字块 value NUMERIC(18,4)，未改）──
CREATE TABLE IF NOT EXISTS ecos_dw.ecos_kb_assumption_value (
    id                 VARCHAR(36)    NOT NULL,
    assumption_id      VARCHAR(36)    NOT NULL,       -- 反向定位 ecos_knowledge.ecos_kb_assumption.id（弱关联）
    assumption_key     VARCHAR(255)   NOT NULL,       -- 冗余规范化键
    value_type         VARCHAR(10)    NOT NULL,       -- RATE/AMOUNT/DAYS（冗余，单位自证）
    value              NUMERIC(18,4)  NOT NULL,       -- 假设值（value_type=AMOUNT 时 ST03-A 逐列登记，R-9 ①）
    value_semantic     VARCHAR(20)    NOT NULL DEFAULT 'ABSOLUTE',  -- ABSOLUTE/DELTA
    trace_id           VARCHAR(64),
    domain             VARCHAR(50)    NOT NULL DEFAULT 'default',
    version_no         VARCHAR(20)    NOT NULL DEFAULT '1',
    is_deleted         SMALLINT       NOT NULL DEFAULT 0,
    create_by          VARCHAR(64)    NOT NULL,
    update_by          VARCHAR(64)    NOT NULL,
    create_time        TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time        TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_ecos_kb_assumption_value PRIMARY KEY (id),
    CONSTRAINT uniq_kbau_assumption UNIQUE (assumption_id, is_deleted)
);
CREATE INDEX IF NOT EXISTS idx_kbav_key ON ecos_dw.ecos_kb_assumption_value(assumption_key, domain);

COMMENT ON TABLE  ecos_knowledge.ecos_kb_assumption IS '情景假设·定义态（REQ-KB-03/F04-07；R-8 ② 拆分：数值见 ecos_dw.ecos_kb_assumption_value，写通道唯一=经 data-engine');
COMMENT ON TABLE  ecos_dw.ecos_kb_assumption_value  IS '情景假设·数值事实（业务域 ecos_dw；value_type=AMOUNT 的值可反推业务金额分布 ⇒ ST03-A 逐列登记，不加密）';
COMMENT ON COLUMN ecos_dw.ecos_kb_assumption_value.value IS '假设值（RATE/AMOUNT/DAYS 三形态共列，按 value_type 解义；ST03-A 说明行逐列登记 R-9 ①）';

-- ── 回滚说明 ────────────────────────────────────────────────
-- 两表均为零行新表（本批不实跑），回滚 = 撤销本文件（不执行）；实跑后回滚须走 R-12 ① 双条件前置件，禁止盲目 DROP。

-- ── DDL Lint Self-audit ──────────────────────────────────
-- [DR01] schema 限定 ✓  [DR02] 文档定名 ecos_kb_assumption ✓  [DR03] 单数 ✓
-- [DR04] dim_scope_json：`_json` 后缀 + TEXT ✓【2026-09-30 收口】逐字块原字面 `dim_scope` 违 DR04，以红线为准改名（表在库内 MISSING ⇒ 零行；全仓代码零引用，实测命中 0），卷04 §E.2 同步勘误；新表零 JSONB ✓
-- [DR05] is_unverified/is_deleted SMALLINT ✓（文档字面 `unverified` 无 is_ 前缀，2026-09-30 收口；本机库无该表、代码零引用）  [DR06] 审计四列+时间 ✓
-- [DR07] version_no VARCHAR(20) NOT NULL DEFAULT '1' ✓  [DR08] domain VARCHAR(50) NOT NULL DEFAULT 'default' ✓
--        （2026-09-30 收口：§E.2 逐字块 INTEGER / VARCHAR(64) NOT NULL 无 DEFAULT 形态与规范模板冲突，两列均取规范侧）
-- [MC01] PK VARCHAR(36) 应用侧 UUID ✓  零 SERIAL ✓
-- [MC02] 零 JSONB / 零裸 NUMERIC ✓  [MC03] 无 partial index（唯一键含 is_deleted）✓  时间默认 CURRENT_TIMESTAMP ✓  零 `::` cast ✓
-- [ST03-A] value 列 COMMENT 登记 ✓（R-9 ①）  [ST07] 定义态=控制域 ecos_knowledge / 数值=业务域 ecos_dw ✓
-- 跨 schema 无 FOREIGN KEY ✓  [IR03] 零 DROP ✓
