-- B4 向量链路修复（D3）：knowledge_embedding 新增 pgvector 向量列 + HNSW 索引
--
-- 背景：V51 建表时 `embedding` 为 JSONB，无向量类型/索引 → RAG 退化为 ILIKE 关键词检索。
-- 本迁移只加不删（R1 缓解）：新增 `embedding_vec vector(1536)`，原 `embedding` JSONB 列保留，
-- 过渡期由写入侧双写（JSONB + vector），检索侧优先走 `embedding_vec` + HNSW。
--
-- 维度依据：RAG 嵌入模型默认 `text-embedding-3-small`（services/dccheng/application.yml:70
-- `ecos.rag.embedding-model`；llm-gateway LLMGatewayProperties.embeddingModel 同值，
-- 且 buildEmbeddingRequestBody 未传 dimensions 参数 → 取模型原生维度 1536）。
-- 若部署侧改用他维度模型（如 bge-small-zh-v1.5=512），需另起迁移新增对应维度列，不得改本列。
--
-- 前置：pgvector 扩展（本环境已装 0.6.2；若镜像未内置，需换用 pgvector/pgvector:pg16 镜像）。
-- Flyway 已禁用 → 本文件为口径留档，需手工执行到 PG。

CREATE EXTENSION IF NOT EXISTS vector;

-- 向量列（只加不删；可空，历史行 embedding_vec 为 NULL → 检索侧降级并告警）
ALTER TABLE ecos_knowledge.knowledge_embedding
    ADD COLUMN IF NOT EXISTS embedding_vec vector(1536);

COMMENT ON COLUMN ecos_knowledge.knowledge_embedding.embedding_vec
    IS 'B4: pgvector 向量列（1536 维，text-embedding-3-small）；原 embedding(JSONB) 保留双写过渡';

-- HNSW 余弦距离索引（pgvector >= 0.5）
CREATE INDEX IF NOT EXISTS idx_knowledge_embedding_vec_hnsw
    ON ecos_knowledge.knowledge_embedding
    USING hnsw (embedding_vec vector_cosine_ops);
