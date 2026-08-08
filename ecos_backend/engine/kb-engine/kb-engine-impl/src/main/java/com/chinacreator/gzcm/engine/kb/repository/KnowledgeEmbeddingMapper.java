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
     * pgvector 余弦相似度 Top-K 检索。
     * 使用 pgvector <=> 运算符 (cosine distance)，相似度 = 1 - distance。
     */
    @Select("SELECT e.id, e.document_id AS articleId, e.content AS chunkText, " +
            "e.token_count AS tokenCount, e.embedding_model AS model, e.created_at AS createdAt, " +
            "1 - (e.embedding <=> #{queryVector}::vector) AS score " +
            "FROM ecos_knowledge.knowledge_embedding e " +
            "WHERE e.embedding IS NOT NULL " +
            "ORDER BY e.embedding <=> #{queryVector}::vector " +
            "LIMIT #{limit}")
    List<Map<String, Object>> searchByVector(@Param("queryVector") String queryVector, @Param("limit") int limit);

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