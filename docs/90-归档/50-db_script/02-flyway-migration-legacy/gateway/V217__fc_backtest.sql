-- V217 (卷09 §6.1/§3.4): 回测五指标 ecos_dw.ecos_fc_backtest——CLOSED 期间×粒度×run 的预测 vs 实际对照
-- 追溯: W225↔C207（回测五指标 0）、W226↔C208（LOW_SAMPLE 复审任务）；需求依据 REQ-FC-05
-- 批准: 需求检视报告 §十四 14.1（2026-09-29）之 R-64=①、R-17=①、R-59=①、R-66=①（阈值走 sysman 配置单源，不入 DDL）；执行边界 §14.4 = 脚本文件落地
-- 状态: 仅落迁移脚本文件，**未对 ecos-postgres 实跑**（实跑属 §14.4 未授权项，需逐项再确认）
-- 纪律: DR01~DR08 / MC01~MC03 / ST03-A / ST06 / ST07 / ST09 / IR02(手动 psql) / IR03(只加不删)
-- 号段: 接续卷 07 V213，凭证 = 已批准 R-64 ①
-- 归属: 业务域 `ecos_dw`（cognitive 回测计算 → data-engine 写通道，§3.4/ADR-14）。
-- 阈值不入列：MAPE>15%/覆盖率<80%/n<5 判定源 = sysman 配置门面 `ecos.fc.backtest.review_threshold.*`（R-66①），本表只存结果与标记。
-- 统计量列（mae/mape/coverage）非金额本体，不在 ST03-A 豁免范围（卷 09 §6.2）。

CREATE TABLE IF NOT EXISTS ecos_dw.ecos_fc_backtest (
    id                      VARCHAR(36) PRIMARY KEY,          -- MC01: 应用侧 UUID
    forecast_run_id         VARCHAR(36) NOT NULL,             -- 被回测 run（同 schema 逻辑引用，不建 FK）
    period                  VARCHAR(16) NOT NULL,             -- CLOSED 期间键（yyyMM，§5.1 #18 period 过滤；非 *_date）
    granularity             VARCHAR(16) NOT NULL,             -- PROJECT/DEPARTMENT/STAGE（§3.4 粒度三选）
    project_id              VARCHAR(36),
    department_id           VARCHAR(36),
    stage                   VARCHAR(32),
    metric_id               VARCHAR(36) NOT NULL,             -- 回测对象指标
    mae                     NUMERIC(8,4),                     -- 五指标①：平均绝对误差
    mape                    NUMERIC(8,4),                     -- 五指标②：平均绝对百分比误差（除零守卫在应用层，禁以 1 兜底 C207）
    bias_direction          VARCHAR(16),                      -- 五指标③：偏差方向（OVER/UNDER/NEUTRAL）
    coverage                NUMERIC(5,4),                     -- 五指标④：区间覆盖率（按 [P10,P90]，R-62①）
    data_coverage           NUMERIC(5,4),                     -- 五指标⑤：数据覆盖率
    zero_actual_count       INTEGER,                          -- 实际为 0 被剔除的行数（禁除零披露，C207）
    sample_count            INTEGER,                          -- 有效样本 n
    sample_flag             VARCHAR(16),                      -- LOW_SAMPLE（n<5 不告警，§3.4）/ NULL=正常
    review_task_id          VARCHAR(64),                      -- 复审任务 = runtime-task 真值（禁自造；角色路由经 OPA，R-66①）
    computed_time           TIMESTAMP,                        -- 回测计算时刻
    -- DR06 六列 + DR07/DR08（卷 09 §6.1 每表统一）
    create_time             TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time             TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by               VARCHAR(100),
    update_by               VARCHAR(100),
    is_deleted              SMALLINT NOT NULL DEFAULT 0,      -- DR05
    version_no              VARCHAR(20) NOT NULL DEFAULT '1', -- DR07
    domain                  VARCHAR(50) NOT NULL DEFAULT 'default' -- DR08
);

COMMENT ON TABLE  ecos_dw.ecos_fc_backtest IS '回测五指标结果（业务域 APPLICATION，data-engine 写通道 ADR-14）；仅对 CLOSED 期间计算（§3.4），阈值判定源在 sysman 配置单源（R-66①）不入 DDL';

-- 索引：普通 BTREE + 唯一索引含 is_deleted（红线：禁 partial unique）
CREATE UNIQUE INDEX IF NOT EXISTS uniq_fc_backtest_cell
    ON ecos_dw.ecos_fc_backtest(forecast_run_id, period, granularity, metric_id, is_deleted);
CREATE INDEX IF NOT EXISTS idx_fc_backtest_period  ON ecos_dw.ecos_fc_backtest(period, granularity);

-- ── DDL Lint Self-audit ──────────────────────────────────
-- [DR01] ecos_dw. 限定 ✓  [DR02] ecos_ 前缀 ✓  [DR05] is_deleted SMALLINT ✓
-- [DR06/07/08] 审计六列 + version_no + domain ✓
-- [MC01] VARCHAR(36) 应用侧 UUID ✓  [MC02] mae/mape NUMERIC(8,4)、coverage NUMERIC(5,4)、zero_actual_count INTEGER ✓
-- [ST07] 业务域落 ecos_dw ✓  [ST09] 无跨 schema FK ✓  [IR03] 只加不删 ✓
-- [R-64①] 仅脚本文件落地，未实跑 ✓
