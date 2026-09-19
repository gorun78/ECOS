package com.chinacreator.gzcm.engine.kb.repository;

import com.chinacreator.gzcm.engine.kb.model.KnowledgeEmbedding;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

@Mapper
public interface KnowledgeEmbeddingMapper {

    @Select("SELECT id, document_id as articleId, content as chunkText, token_count, embedding_model as model, created_at as createdAt FROM ecos_knowledge.knowledge_embedding WHERE document_id = #{articleId}")
    List<KnowledgeEmbedding> findByArticleId(@Param("articleId") String articleId);

    @Select("SELECT id, document_id as articleId, content as chunkText, token_count, embedding_model as model, created_at as createdAt FROM ecos_knowledge.knowledge_embedding WHERE id = #{id}")
    KnowledgeEmbedding findById(@Param("id") String id);

    @Insert("INSERT INTO ecos_knowledge.knowledge_embedding (id, document_id, content, token_count, embedding_model, created_at) " +
            "VALUES (#{id}, #{articleId}, #{chunkText}, #{tokenCount}, #{model}, #{createdAt})")
    int insert(KnowledgeEmbedding embedding);

    @Select("SELECT COUNT(*) FROM ecos_knowledge.knowledge_embedding")
    long count();

    /**
     * pgvector 余弦相似度 Top-K 检索（B4：走 pgvector 向量列 embedding_vec + HNSW 索引）。
     * 使用 pgvector {@code <=>} 运算符 (cosine distance)，相似度 = 1 - distance。
     * 历史行 embedding_vec 为 NULL 时被 WHERE 排除（检索侧据此降级并告警）。
     */
    @Select("SELECT e.id, e.document_id AS articleId, e.content AS chunkText, " +
            "e.token_count AS tokenCount, e.embedding_model AS model, e.created_at AS createdAt, " +
            "1 - (e.embedding_vec <=> CAST(#{queryVector} AS vector)) AS score " +
            "FROM ecos_knowledge.knowledge_embedding e " +
            "WHERE e.embedding_vec IS NOT NULL " +
            "ORDER BY e.embedding_vec <=> CAST(#{queryVector} AS vector) " +
            "LIMIT #{limit}")
    List<Map<String, Object>> searchByVector(@Param("queryVector") String queryVector, @Param("limit") int limit);

    /**
     * 批量 upsert 向量行（B4/T3）：单条 SQL 多 VALUES，禁止循环单条写库。
     * 双写过渡（R1）：同时写 JSONB 原列 embedding 与 pgvector 新列 embedding_vec；
     * 按主键 id 幂等（ON CONFLICT DO UPDATE，重复 ingest 不产生重复行）。
     */
    @Insert("<script>" +
            "INSERT INTO ecos_knowledge.knowledge_embedding " +
            "(id, document_id, chunk_index, content, embedding, embedding_vec, embedding_model, token_count) VALUES " +
            "<foreach collection='items' item='it' separator=','>" +
            "(#{it.id}, #{it.articleId}, #{it.chunkIndex}, #{it.chunkText}, " +
            "CAST(#{it.embeddingJson} AS jsonb), CAST(#{it.embeddingVec} AS vector), #{it.model}, #{it.tokenCount})" +
            "</foreach> " +
            "ON CONFLICT (id) DO UPDATE SET " +
            "document_id = EXCLUDED.document_id, chunk_index = EXCLUDED.chunk_index, " +
            "content = EXCLUDED.content, embedding = EXCLUDED.embedding, " +
            "embedding_vec = EXCLUDED.embedding_vec, embedding_model = EXCLUDED.embedding_model, " +
            "token_count = EXCLUDED.token_count" +
            "</script>")
    int batchUpsertVectors(@Param("items") List<KnowledgeEmbedding> items);

    /**
     * 批量 upsert（仅 JSONB 列，无 embedding_vec）——pgvector 不可用时的降级写入，
     * 保证文本与 JSONB 向量不丢，待扩展就绪后重跑即可补齐 embedding_vec。
     */
    @Insert("<script>" +
            "INSERT INTO ecos_knowledge.knowledge_embedding " +
            "(id, document_id, chunk_index, content, embedding, embedding_model, token_count) VALUES " +
            "<foreach collection='items' item='it' separator=','>" +
            "(#{it.id}, #{it.articleId}, #{it.chunkIndex}, #{it.chunkText}, " +
            "CAST(#{it.embeddingJson} AS jsonb), #{it.model}, #{it.tokenCount})" +
            "</foreach> " +
            "ON CONFLICT (id) DO UPDATE SET " +
            "document_id = EXCLUDED.document_id, chunk_index = EXCLUDED.chunk_index, " +
            "content = EXCLUDED.content, embedding = EXCLUDED.embedding, " +
            "embedding_model = EXCLUDED.embedding_model, token_count = EXCLUDED.token_count" +
            "</script>")
    int batchUpsertJsonOnly(@Param("items") List<KnowledgeEmbedding> items);

    /**
     * 文本关键词回退检索 (ILIKE)。
     */
    @Select("SELECT id, document_id AS articleId, content AS chunkText, " +
            "token_count AS tokenCount, embedding_model AS model, created_at AS createdAt " +
            "FROM ecos_knowledge.knowledge_embedding " +
            "WHERE content ILIKE CONCAT('%', #{keyword}, '%') " +
            "ORDER BY created_at DESC " +
            "LIMIT #{limit}")
    List<KnowledgeEmbedding> searchByKeyword(@Param("keyword") String keyword, @Param("limit") int limit);
}