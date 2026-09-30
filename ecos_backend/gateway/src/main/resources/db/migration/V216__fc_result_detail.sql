-- V216 (卷09 §6.1): 预测结果明细 ecos_dw.ecos_fc_result_detail——格×指标行级六要素完备（C209）
-- 追溯: W218↔C200（草案非 TEXT JSON 列→TEXT）、W227↔C209（六要素实列）、W243↔C225（R-62① 三值 P10/P50/P90 全存全展）；需求依据 REQ-FC-02/03
-- 批准: 需求检视报告 §十四 14.1（2026-09-29）之 R-64=①、R-17=①、R-59=①、R-62=①（三值全存全展）；执行边界 §14.4 = 脚本文件落地
-- 状态: 仅落迁移脚本文件，**未对 ecos-postgres 实跑**（实跑属 §14.4 未授权项，需逐项再确认）
-- 纪律: DR01~DR08 / MC01~MC03 / ST03-A / ST06 / ST07 / ST09 / IR02(手动 psql) / IR03(只加不删)
-- 号段: 接续卷 07 V213，凭证 = 已批准 R-64 ①
-- 归属: 业务域 `ecos_dw`，cognitive-engine 计算 → data-engine 写通道落库（R-17① + R-59①，ADR-14）；不由 cognitive 建表写入。
-- 金额列 ST03-A: 明文存储 + 读取通道 security-engine CLS 列过滤裁决（R-65①）；
--   待登记表条目 ID = TBD-卷09-2026-09-29（逐列：amount / amount_p10 / amount_p50 / amount_p90）。
-- SUCCEEDED 前逐行六要素自检门禁（C209）⇒ 六要素列 NOT NULL。

CREATE TABLE IF NOT EXISTS ecos_dw.ecos_fc_result_detail (
    id                      VARCHAR(36) PRIMARY KEY,          -- MC01: 应用侧 UUID；响应体 detailId
    forecast_run_id         VARCHAR(36) NOT NULL,             -- 六要素①：指向 ecos_dw.ecos_fc_run.id（同 schema 逻辑引用；ST09 禁跨 schema FK，本处同 schema 仍按纪律不建 FK，由写通道校验）
    caliber_version         VARCHAR(20) NOT NULL,             -- 六要素②
    as_of_time              TIMESTAMP NOT NULL,               -- 六要素③
    snapshot_id             VARCHAR(36) NOT NULL,             -- 六要素④
    formula_version         VARCHAR(20) NOT NULL,             -- 六要素⑤
    source_ref_json         TEXT NOT NULL,                    -- 六要素⑥：ACTUAL/PLAN→事实行 ID+合同 change_version；PROFILE_IMPUTED→profile_key+版本+置信档；MANUAL_OVERRIDE→assumption_key+审批人+生效期；COMPUTED→下层 detailId[]（§4.2）
    metric_id               VARCHAR(36) NOT NULL,             -- 指标引用（卷 03 ecos_metric_definition / 视图侧；无跨 schema FK）
    metric_code             VARCHAR(64),                      -- 冗余展示码（检索投影，模板附则 2）
    period                  VARCHAR(16) NOT NULL,             -- 期间键（yyyMM 或季度/年粒度字符串，§5.1 #10 period 下钻；非 *_date 故不成 DATE）
    granularity             VARCHAR(16) NOT NULL DEFAULT 'TOTAL', -- 格维度：PROJECT/DEPARTMENT/STAGE/TOTAL
    project_id              VARCHAR(36),                      -- granularity=PROJECT 时填充
    department_id           VARCHAR(36),                      -- granularity=DEPARTMENT 时填充
    stage                   VARCHAR(32),                      -- granularity=STAGE 时填充（环节）
    amount                  NUMERIC(18,2),                    -- 金额列唯一形态 NUMERIC(18,2)（MC02 v1.2）；ST03-A 待登记 TBD-卷09-2026-09-29
    amount_p10              NUMERIC(18,2),                    -- 区间 P10；ST03-A 待登记 TBD-卷09-2026-09-29
    amount_p50              NUMERIC(18,2),                    -- 区间 P50（R-62① 三值全存全展）；ST03-A 待登记 TBD-卷09-2026-09-29
    amount_p90              NUMERIC(18,2),                    -- 区间 P90；ST03-A 待登记 TBD-卷09-2026-09-29
    rate_value              NUMERIC(5,4),                     -- 比率值（realization_rate / attribution_ratio 等）
    source_type             VARCHAR(20) NOT NULL,             -- ACTUAL/PLAN/PROFILE_IMPUTED/MANUAL_OVERRIDE/COMPUTED（行级来源，§5.1 统一响应约定）
    parent_detail_id        VARCHAR(36),                      -- COMPUTED 构成树父行（证据抽屉逐层下钻至叶子，§4.2）
    -- DR06 六列 + DR07/DR08（卷 09 §6.1 每表统一）
    create_time             TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time             TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by               VARCHAR(100),
    update_by               VARCHAR(100),
    is_deleted              SMALLINT NOT NULL DEFAULT 0,      -- DR05
    version_no              VARCHAR(20) NOT NULL DEFAULT '1', -- DR07
    domain                  VARCHAR(50) NOT NULL DEFAULT 'default' -- DR08
);

COMMENT ON TABLE  ecos_dw.ecos_fc_result_detail IS '预测结果明细（格×指标；业务域 APPLICATION，data-engine 唯一写通道 ADR-14）；行级六要素 NOT NULL（C209），SUCCEEDED 后只读（C210 应用层）';
COMMENT ON COLUMN ecos_dw.ecos_fc_result_detail.amount IS '预测金额主值（P50 同值存储冗余展示由后端保证，禁前端插值——R-62①）；明文+CLS 读取裁决，ST03-A 待登记';
COMMENT ON COLUMN ecos_dw.ecos_fc_result_detail.parent_detail_id IS '构成树父明细行（COMPUTED 逐层展开；导出/AI 回答必须复用本行集，禁旁路 §4.2）';

-- 索引（普通 BTREE；run 内格子定位走唯一性由应用写通道校验，区间下钻走维度列）
CREATE INDEX IF NOT EXISTS idx_fc_detail_run       ON ecos_dw.ecos_fc_result_detail(forecast_run_id);
CREATE INDEX IF NOT EXISTS idx_fc_detail_run_cell  ON ecos_dw.ecos_fc_result_detail(forecast_run_id, metric_id, period, granularity);
CREATE INDEX IF NOT EXISTS idx_fc_detail_parent    ON ecos_dw.ecos_fc_result_detail(parent_detail_id);
CREATE INDEX IF NOT EXISTS idx_fc_detail_project   ON ecos_dw.ecos_fc_result_detail(project_id, period);

-- ── DDL Lint Self-audit ──────────────────────────────────
-- [DR01] ecos_dw. 限定 ✓  [DR02] ecos_ 前缀 ✓  [DR04] source_ref_json _json 后缀 + TEXT ✓
-- [DR05] is_deleted SMALLINT ✓  [DR06/07/08] 审计六列 + version_no + domain ✓
-- [MC01] VARCHAR(36) 应用侧 UUID ✓  [MC02] JSON 全 TEXT；金额/区间 NUMERIC(18,2)、rate_value NUMERIC(5,4) ✓
-- [ST03-A] 金额列逐列注释登记（TBD-卷09-2026-09-29），未自称"明文即可" ✓
-- [ST07] 业务域产物落 ecos_dw ✓  [ST09] 无跨 schema FK ✓  [IR03] 只加不删 ✓
-- [R-64①] 仅脚本文件落地，未实跑 ✓
