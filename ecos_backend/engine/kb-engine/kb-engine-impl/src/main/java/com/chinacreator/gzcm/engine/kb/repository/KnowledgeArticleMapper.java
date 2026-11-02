package com.chinacreator.gzcm.engine.kb.repository;

import com.chinacreator.gzcm.engine.kb.model.KnowledgeArticle;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface KnowledgeArticleMapper {

    /** 列清单单源（#147H：七分类 + 规则语义根引用）。 */
    String COLS = "id, title, content, domain, category, knowledge_category as knowledgeCategory, "
            + "rule_ref as ruleRef, source as sourceType, status, "
            + "created_at as createdAt, updated_at as updatedAt";

    @Select("SELECT " + COLS + " FROM ecos_knowledge.knowledge_article WHERE id = #{id}")
    KnowledgeArticle findById(@Param("id") String id);

    @Select("SELECT " + COLS + " FROM ecos_knowledge.knowledge_article WHERE domain = #{domain}")
    List<KnowledgeArticle> findByDomain(@Param("domain") String domain);

    /**
     * 按七分类过滤（#147U）：分类闭集由 {@code KnowledgeCategory} 在服务层校验，
     * SQL 只做等值匹配；{@code limit} 已由调用方夹紧，避免无界列表。
     */
    @Select("SELECT " + COLS + " FROM ecos_knowledge.knowledge_article "
            + "WHERE knowledge_category = #{category} ORDER BY created_at DESC LIMIT #{limit}")
    List<KnowledgeArticle> findByCategory(@Param("category") String category, @Param("limit") int limit);

    @Select("SELECT " + COLS + " FROM ecos_knowledge.knowledge_article WHERE title ILIKE CONCAT('%', #{query}, '%') OR content ILIKE CONCAT('%', #{query}, '%') LIMIT #{limit}")
    List<KnowledgeArticle> search(@Param("query") String query, @Param("limit") int limit);

    @Select("SELECT " + COLS + " FROM ecos_knowledge.knowledge_article")
    List<KnowledgeArticle> findAll();

    @Insert("INSERT INTO ecos_knowledge.knowledge_article "
            + "(id, title, content, domain, category, knowledge_category, rule_ref, source, status, created_at, updated_at) "
            + "VALUES (#{id}, #{title}, #{content}, #{domain, jdbcType=VARCHAR}, #{category, jdbcType=VARCHAR}, "
            + "#{knowledgeCategory, jdbcType=VARCHAR}, #{ruleRef, jdbcType=VARCHAR}, "
            + "#{sourceType, jdbcType=VARCHAR}, #{status}, #{createdAt}, #{updatedAt})")
    int insert(KnowledgeArticle article);

    @Select("SELECT COUNT(*) FROM ecos_knowledge.knowledge_article")
    long count();
}
