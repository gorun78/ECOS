package com.chinacreator.gzcm.engine.kb.repository;

import com.chinacreator.gzcm.engine.kb.model.KnowledgeEmbedding;
import org.apache.ibatis.annotations.*;

import java.util.List;

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
}