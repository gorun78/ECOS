-- V115: 知识图谱同步日志表 (ecos_knowledge.kg_sync_log)
--
-- 用途（PMO-50 T2/T4 作业台账）：
--   1. GET  /api/v1/knowledge/sync/status                 — 最近一次成功同步时间
--   2. GET  /api/v1/knowledge/sync/jobs                   — 任务列表（按 job_id 去重取最新）
--   3. GET  /api/v1/knowledge/sync/jobs/{jobId}/logs      — 单任务日志
--   4. POST /api/v1/knowledge/sync/jobs/{jobId}/rollback  — 按本表 nodes/edges 计数回滚本批 node/edge
--   5. EcosOntologyEventConsumer 失败兜底行（status=FAILED）
--
-- 恢复说明：2.1-alpha 手工合并时本脚本丢失（迁移序列 V114 → V116 断号），
-- 而 KgSyncServiceImpl javadoc 与 V116__kb_ontology_snapshot.sql 注释均引用本编号，故按代码实际列用法补齐。
--
-- 【#146h knownLegacy 注记 2026-10-27】本脚本 dev PG 从未实跑（to_regclass 归 NULL，表无 0 行历史）。
-- kg_sync_log 族（含本表）的 live 承接表 = V179 合规新表 ecos_knowledge.ecos_kg_sync_log
-- （VARCHAR(36) 应用侧 UUID PK；op→sync_op / nodes→node_count / edges→edge_count /
--  created_at→create_time / finished_at→finished_time / report→report_json TEXT）。
-- #146h 起全部生产 DAO 读/写端已切 ecos_kg_sync_log（映射台账 §D-7 裁决，
-- kb-engine 回潮门禁 KbExtractBareTableGateTest 钉位）。本脚本判 knownLegacy 停再跑，
-- 不 DROP（schema 只加不删，IR03）；本头注只登记不改 DDL 本体。

CREATE TABLE IF NOT EXISTS ecos_knowledge.kg_sync_log (
    id            BIGSERIAL     PRIMARY KEY,
    object_type   VARCHAR(64)   NOT NULL,
    op            VARCHAR(32)   NOT NULL,
    job_id        VARCHAR(128)  NOT NULL,
    status        VARCHAR(16)   NOT NULL,
    progress      INTEGER       NOT NULL DEFAULT 0,
    nodes         INTEGER       NOT NULL DEFAULT 0,
    edges         INTEGER       NOT NULL DEFAULT 0,
    error_message VARCHAR(1024),
    created_at    TIMESTAMP     NOT NULL DEFAULT NOW(),
    finished_at   TIMESTAMP
);

-- job_id：任务列表按 job_id 去重（DISTINCT ON）+ 单任务日志过滤
CREATE INDEX IF NOT EXISTS idx_kg_sync_log_job_id
    ON ecos_knowledge.kg_sync_log (job_id);

-- created_at：列表倒序 + 单任务取最新行
CREATE INDEX IF NOT EXISTS idx_kg_sync_log_created_at
    ON ecos_knowledge.kg_sync_log (created_at DESC);

COMMENT ON TABLE ecos_knowledge.kg_sync_log IS '知识图谱同步作业日志（PMO-50 T2/T4：同步状态/任务列表/回滚计数台账）';