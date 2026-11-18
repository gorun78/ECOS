-- V279 (卷09 v1.2 §十一 H-3 / outcome 物理载体): 业务域行动结果表 ecos_dw.ecos_application_action_outcome 新建（"预期影响 vs 实际影响"可查询载体）
-- 追溯: 卷09 v1.2 §一.10 实测（FactImportService:266-303 读实证——writeActionOutcome 现状只发审计事件 + 触达列，五表无 outcome 物理列）；需求依据 PRD-09 v1.2 §4.2（outcome 物理载体裁定项 H-3；回灌唯一通路 POST /api/v1/datanet/facts/action-outcomes）
-- 批准: 卷09 v1.2 §十一 H-3 移交条目（"裁定列形态"）——**裁定=加表不加列**，判据三条：
--   ① 加列否决：outcome 落在 V167 五事实表任一列上 ⇒ 回灌必须 UPDATE 行；PUBLISHED 行禁 UPDATE（ECOS-DATA-021 已实装守卫），
--      加列即造出"必须绕过不可变守卫"的写路径，自相矛盾。
--   ② 基数否决：一个 action/事实行可多次回灌 outcome（滚动复核），1:N 只能靠追加行承载（append-only，IR03 友好）。
--   ③ 域归属：V218 public.ecos_fc_action_ext 是控制域**决策侧**扩展（expected_impact 属计划值），非事实回灌载体；
--      本表落业务域 ecos_dw（ST07/ADR-14，写路径只归 data-engine 唯一写通道；cognitive 禁直连）。
-- 状态: 仅落迁移脚本文件，**未对 ecos-postgres 实跑**（实跑属未授权项，需逐项再确认后按 IR02 手动 psql 执行）
-- 纪律: DR01~DR08 / MC01~MC03 / ST03-A（金额列逐列登记，见下）/ ST07 / IR03

CREATE TABLE IF NOT EXISTS ecos_dw.ecos_application_action_outcome (
    id               VARCHAR(36)    NOT NULL,                 -- MC01: 应用侧生成 UUID，DDL 无默认值
    forecast_run_id  VARCHAR(64),                             -- 关联预测运行业务键（V197 uniq_afcr_run_id 同形；可空=非预测链回灌）
    action_id        VARCHAR(64),                             -- 行动/建议业务键（W 侧动作标识，可空）
    fact_type        VARCHAR(32)    NOT NULL,                 -- 关联事实类型（ATTRIBUTION/STAGE/RESOURCE/COST/FORECAST_SNAPSHOT）
    fact_id          VARCHAR(36)    NOT NULL,                 -- 关联事实行主键（V167 五表 id，MC01 同宽）
    period           VARCHAR(7),                              -- 归属期间 YYYY-MM（检索维度，非关账键）
    outcome          VARCHAR(1000)  NOT NULL,                 -- 处置结论（自由文本，业务口径由上游场景定义）
    outcome_comment  VARCHAR(2000),                           -- 备注/说明（可空；列名单数、避开保留词 comment，DR03/PG 关键字面）
    expected_impact  NUMERIC(18,2),                           -- ST03-A 金额列①：预期影响（计划值，CNY 元；豁免登记见下）
    actual_impact    NUMERIC(18,2),                           -- ST03-A 金额列②：实际影响（回灌真值，CNY 元；豁免登记见下）
    currency         VARCHAR(8)     NOT NULL DEFAULT 'CNY',   -- 币种（V167 五表同形；DQ-F05 单币种口径由应用侧承）
    batch_id         VARCHAR(64),                             -- 关联导入批次（既有 outcome 审计检索路径不变）
    idempotency_key  VARCHAR(64),                             -- 回灌幂等键（应用侧查重后 INSERT；可空=非幂等写）
    detail_json      TEXT,                                    -- DR04: 扩展上下文序列化 TEXT，仅描述用，禁 WHERE/JOIN/索引（MC02）
    -- DR06~DR08 七基线列
    domain           VARCHAR(50)    NOT NULL DEFAULT 'default',
    version_no       VARCHAR(20)    NOT NULL DEFAULT '1',
    is_deleted       SMALLINT       NOT NULL DEFAULT 0,
    create_by        VARCHAR(64)    NOT NULL DEFAULT 'system',
    update_by        VARCHAR(64),
    create_time      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP,
    CONSTRAINT pk_ecos_application_action_outcome PRIMARY KEY (id)
);

-- ── 索引（查询端点定位键，禁全库扫描 IR04/性能面）──────────────
CREATE INDEX IF NOT EXISTS idx_eaao_run   ON ecos_dw.ecos_application_action_outcome (forecast_run_id, is_deleted);
CREATE INDEX IF NOT EXISTS idx_eaao_act   ON ecos_dw.ecos_application_action_outcome (action_id, is_deleted);
CREATE INDEX IF NOT EXISTS idx_eaao_fact  ON ecos_dw.ecos_application_action_outcome (fact_id, is_deleted);
-- 幂等键业务唯一（可空列；PG UNIQUE 允许多个 NULL，非幂等写不受影响）
CREATE UNIQUE INDEX IF NOT EXISTS uniq_eaao_idem
    ON ecos_dw.ecos_application_action_outcome (idempotency_key, is_deleted);

-- ── 注释登记（DR06）──────────────────────────────────────────
COMMENT ON TABLE  ecos_dw.ecos_application_action_outcome IS '行动结果回灌载体（卷09 H-3：预期影响 vs 实际影响可查询物理表；append-only 只增不改；写路径=data-engine 唯一写通道（ADR-14/ST08 cognitive 禁直连本表））';
COMMENT ON COLUMN ecos_dw.ecos_application_action_outcome.expected_impact IS '预期影响金额 NUMERIC(18,2)（ST03-A 豁免登记：业务域 CURATED 层明文存储；RLS+CLS+审计承接；导出/AI 通道经 security-engine mask。见登记表文本 H-3-①）';
COMMENT ON COLUMN ecos_dw.ecos_application_action_outcome.actual_impact IS '实际影响金额 NUMERIC(18,2)（ST03-A 豁免登记同上口径，H-3-②；"实际"为回灌真值，与 expected_impact 成对呈现，差值即行动复盘读数）';
COMMENT ON COLUMN ecos_dw.ecos_application_action_outcome.idempotency_key IS '回灌幂等键（应用侧先查 uniq_eaao_idem 后 INSERT，重复提交返回首写 outcome id，不产生第二行）';
COMMENT ON COLUMN ecos_dw.ecos_application_action_outcome.detail_json IS '扩展上下文序列化 TEXT（受控 JSON，仅描述用，禁 WHERE/JOIN/索引，MC02）';

-- ── 回滚说明 ──────────────────────────────────────────────
-- 本表新建只增（IR03）；回滚 = 应用侧停写本表（writeActionOutcome 回退"审计事件+触达列"形态），
-- 表体与既有行保留不清算子（禁 DELETE/DROP）。
-- 幂等性: CREATE TABLE/INDEX IF NOT EXISTS，重复执行零报错、零重复。

-- ── ST03-A 列级加密豁免登记（文本随交付报告提交，文档追加归主会话）──
--  ecos_dw.ecos_application_action_outcome.expected_impact NUMERIC(18,2) — 预期影响金额（CNY）
--  ecos_dw.ecos_application_action_outcome.actual_impact   NUMERIC(18,2) — 实际影响金额（CNY）
--  豁免理由：业务域 CURATED 层金额事实列，与 V167 五表 amount/contract_base、V197 deviation 同口径；
--  防护承接：行级 RLS + 列级 CLS（分册 01 决策管道）+ ecos.audit 审计事件 + 导出/AI 经 security-engine mask。

-- ── DDL Lint Self-audit ──────────────────────────────────────
-- [DR01] schema 限定 ecos_dw. ✓  [DR02] ecos_ 前缀 ✓  [DR03] 单数列名 ✓（outcome_comment 避开保留词 comment）
-- [DR04] detail_json TEXT 且 _json 后缀 ✓  [DR05] is_deleted SMALLINT ✓
-- [DR06] 审计六列 ✓  [DR07] version_no VARCHAR(20) ✓  [DR08] domain VARCHAR(50) ✓
-- [MC01] PK VARCHAR(36) 应用侧 UUID、无 gen_random_uuid ✓
-- [MC02] 金额列 NUMERIC(18,2) 显式精度 ✓（ST03 禁 FLOAT/MONEY）；无 JSONB/数组/二进制 ✓
-- [MC03] 无裸 PG 语法；UNIQUE 索引为普通列组合（非条件索引）✓
-- [ST03-A] 两个金额列已随交付报告逐列登记豁免文本 ✓
-- [ST07] 落业务域 ecos_dw ✓  [ADR-14/ST08] 唯一写通道=data-engine（应用侧由 BusinessDomainWriteArchTest 把守）✓
-- [IR02] 手动 psql ✓  [IR03] 只加不删、append-only 语义 ✓
