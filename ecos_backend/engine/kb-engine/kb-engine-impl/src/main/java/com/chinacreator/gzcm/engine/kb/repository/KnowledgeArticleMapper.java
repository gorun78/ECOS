package com.chinacreator.gzcm.engine.kb.repository;

import com.chinacreator.gzcm.engine.kb.model.KnowledgeArticle;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface KnowledgeArticleMapper {

    @Select("SELECT id, title, content, domain, category, source as sourceType, status, created_at as createdAt, updated_at as updatedAt FROM ecos_knowledge.knowledge_article WHERE id = #{id}")
    KnowledgeArticle findById(@Param("id") String id);

    @Select("SELECT id, title, content, domain, category, source as sourceType, status, created_at as createdAt, updated_at as updatedAt FROM ecos_knowledge.knowledge_article WHERE domain = #{domain}")
    List<KnowledgeArticle> findByDomain(@Param("domain") String domain);

    @Select("SELECT id, title, content, domain, category, source as sourceType, status, created_at as createdAt, updated_at as updatedAt FROM ecos_knowledge.knowledge_article WHERE title ILIKE CONCAT('%', #{query}, '%') OR content ILIKE CONCAT('%', #{query}, '%') LIMIT #{limit}")
    List<KnowledgeArticle> search(@Param("query") String query, @Param("limit") int limit);

    @Select("SELECT id, title, content, domain, category, source as sourceType, status, created_at as createdAt, updated_at as updatedAt FROM ecos_knowledge.knowledge_article")
    List<KnowledgeArticle> findAll();

    @Insert("INSERT INTO ecos_knowledge.knowledge_article (id, title, content, domain, category, source, status, created_at, updated_at) " +
            "VALUES (#{id}, #{title}, #{content}, #{domain, jdbcType=VARCHAR}, #{category, jdbcType=VARCHAR}, #{sourceType, jdbcType=VARCHAR}, #{status}, #{createdAt}, #{updatedAt})")
    int insert(KnowledgeArticle article);

    @Select("SELECT COUNT(*) FROM ecos_knowledge.knowledge_article")
    long count();
}