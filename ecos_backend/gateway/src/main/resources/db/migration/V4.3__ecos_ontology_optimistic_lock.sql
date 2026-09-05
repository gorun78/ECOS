-- PMO-29 §4.2 乐观锁版本管理
-- 为 ecos_ontology_proposals 加 optimistic_lock_version 列 + 索引
-- 版本号 INTEGER NOT NULL DEFAULT 0，UPDATE 时 WHERE optimistic_lock_version = ? AND status = ?
-- 并发更新失败返回 0 行 → OPTIMISTIC_LOCK_CONFLICT 错误码
ALTER TABLE ecos_ontology_proposals
    ADD COLUMN IF NOT EXISTS optimistic_lock_version INTEGER NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_proposals_olock ON ecos_ontology_proposals(optimistic_lock_version);

-- 已有数据回填 0 (DEFAULT 自动)
