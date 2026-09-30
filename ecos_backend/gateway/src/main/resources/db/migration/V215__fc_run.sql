-- V215 (卷09 §6.1): 预测运行主记录 ecos_dw.ecos_fc_run——六要素之运行级承载 + 幂等/复用/状态机
-- 追溯: W227↔C209（六要素 0/6）、W218↔C200（PRD 草案非 TEXT JSON 列→TEXT）、W219↔C201（主键去默认函数）；需求依据 REQ-FC-02/03
-- 批准: 需求检视报告 §十四 14.1（2026-09-29）之 R-64=①（ecos_fc_* 落迁移脚本文件、不实跑库）、R-59=①（编排/生产分离：workspace 编排、cognitive 计算、data-engine 写通道）、R-17=①（确定性计算产物落业务域 APPLICATION/ecos_dw，ADR-14）；执行边界 §14.4 = 脚本文件落地
-- 状态: 仅落迁移脚本文件，**未对 ecos-postgres 实跑**（实跑属 §14.4 未授权项，需逐项再确认）
-- 纪律: DR01~DR08 / MC01~MC03 / ST03-A / ST06 / ST07 / ST09 / IR02(手动 psql) / IR03(只加不删)
-- 号段: 接续卷 07 V213，凭证 = 已批准 R-64 ①（V200~V213 已被卷 06/07 占用）
-- 归属: 业务域 `ecos_dw`（APPLICATION 层），写通道唯一 = data-engine（R-17① + R-59①，ADR-14）；
--   workspace/cognitive 不直写本表。SUCCEEDED 后只读（C210 应用层拒改，非 DDL 约束）。
-- 六要素（C209）: `id` 即 forecast_run_id（响应体 forecastRunId 唯一来源，不重复设列）；
--   caliber_version / as_of_time / snapshot_id / formula_version 落本表实列；
--   source_ref 为行级要素，落 ecos_fc_result_detail（V216）。

CREATE TABLE IF NOT EXISTS ecos_dw.ecos_fc_run (
    id                      VARCHAR(36) PRIMARY KEY,          -- MC01: 应用侧生成 UUID，DDL 无默认值；即六要素 forecast_run_id
    caliber_id              VARCHAR(36) NOT NULL,             -- 口径引用（指向 V214 视图 caliber_id；ST09 不建跨 schema FK）
    caliber_version         VARCHAR(20) NOT NULL,             -- 六要素之口径版本
    formula_version         VARCHAR(20) NOT NULL,             -- 六要素之公式版本
    as_of_time              TIMESTAMP NOT NULL,               -- 六要素之快照可见性时刻（§3.2 语义）
    snapshot_id             VARCHAR(36) NOT NULL,             -- 六要素之输入快照 ID（缺快照即 FAILED，C209 门禁）
    snapshot_checksum       VARCHAR(64),                      -- 快照 checksum（冻结后源变更不影响本 run，§03 事件 06）
    scope_hash              VARCHAR(64),                      -- SHA-256(排序后 projectId 集|departmentId 集|periodRange|stageSet)（§3.2）
    overrides_hash          VARCHAR(64),                      -- SHA-256(规范化 JSON(overrides))（§3.2）
    scenario_set_hash       VARCHAR(64),                      -- runKey 组成（§3.2）
    overrides_json          TEXT,                             -- MC02: JSON 一律 TEXT（PRD-09 草案非 TEXT JSON 形态已按 C200 更正）；键排序规范化后存
    caliber_snapshot_json   TEXT,                             -- run 创建时一次性冻结（§3.3），之后只读；历史 run 不受升版影响
    status                  VARCHAR(20) NOT NULL DEFAULT 'CREATED', -- CREATED→DATA_CHECK→SNAPSHOT_FROZEN→RUNNING→SUCCEEDED / FAILED（§3.1）
    reused_from_run_id      VARCHAR(36),                      -- 幂等复用来源（§3.2 reused=true 语义；force 新建时快照按 runKey 引用同一份）
    task_id                 VARCHAR(64),                      -- runtime-task 真值（C218：>50 项目或 >10 万行转异步，禁自造 jobId）
    error_code              VARCHAR(24),                      -- 失败原因码（FAILED 态填充）
    error_text              TEXT,
    -- DR06 六列 + DR07/DR08（卷 09 §6.1 每表统一）
    create_time             TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time             TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by               VARCHAR(100),
    update_by               VARCHAR(100),
    is_deleted              SMALLINT NOT NULL DEFAULT 0,      -- DR05
    version_no              VARCHAR(20) NOT NULL DEFAULT '1', -- DR07
    domain                  VARCHAR(50) NOT NULL DEFAULT 'default' -- DR08
);

COMMENT ON TABLE  ecos_dw.ecos_fc_run IS '预测运行主记录（业务域 APPLICATION，data-engine 唯一写通道，R-17①/R-59①/ADR-14）；SUCCEEDED 后只读（应用层约束 C210）';
COMMENT ON COLUMN ecos_dw.ecos_fc_run.as_of_time IS '六要素·快照可见性时刻：事实行按 create_time<=asOfTime AND (is_deleted=0 OR deleted_at>asOfTime) 裁剪（§3.2）';
COMMENT ON COLUMN ecos_dw.ecos_fc_run.task_id IS 'runtime-task 任务真值（禁自造）；异步阈值见 §3.1/C218';

-- 索引（普通 BTREE；幂等查 runKey 组合走 scope_hash/as_of_time 缩范围，runKey 非唯一——force 重跑允许同键多行）
CREATE INDEX IF NOT EXISTS idx_fc_run_caliber      ON ecos_dw.ecos_fc_run(caliber_id, caliber_version);
CREATE INDEX IF NOT EXISTS idx_fc_run_status       ON ecos_dw.ecos_fc_run(status);
CREATE INDEX IF NOT EXISTS idx_fc_run_scope_hash   ON ecos_dw.ecos_fc_run(scope_hash);
CREATE INDEX IF NOT EXISTS idx_fc_run_domain_time  ON ecos_dw.ecos_fc_run(domain, as_of_time);

-- ── DDL Lint Self-audit ──────────────────────────────────
-- [DR01] ecos_dw. 限定 ✓  [DR02] ecos_ 前缀 ✓  [DR04] _json 后缀（overrides_json/caliber_snapshot_json）✓
-- [DR05] is_deleted SMALLINT ✓  [DR06/07/08] 审计六列 + version_no + domain ✓
-- [MC01] VARCHAR(36) 应用侧 UUID、DDL 无默认函数 ✓  [MC02] JSON 全 TEXT、无非 TEXT JSON 列 ✓
-- [ST07] 业务域产物落 ecos_dw ✓  [ST09] caliber_id 无跨 schema FK ✓  [IR03] 只加不删 ✓
-- [R-64①] 仅脚本文件落地，未实跑 ✓
