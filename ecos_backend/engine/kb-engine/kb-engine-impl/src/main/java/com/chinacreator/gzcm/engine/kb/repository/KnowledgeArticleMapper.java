package com.chinacreator.gzcm.engine.kb.repository;

import com.chinacreator.gzcm.engine.kb.model.KnowledgeArticle;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface KnowledgeArticleMapper {

    /**
     * 列清单单源（#147H：七分类 + 规则语义根引用）。
     *
     * <p><b>时间列的类型对齐（#147U 实测修复）</b>：实体 {@code createdAt/updatedAt} 是
     * {@code long}（epoch 毫秒），而 DDL 两列是 {@code timestamp without time zone}。
     * 原写法直接把 timestamp 读进 long ⇒ 存量行读取 500（{@code 不良的类型值 long : 2026-10-17 …}），
     * 写入则以 bigint 打 timestamp 列 ⇒ {@code createArticle} 500。此处统一按 epoch 毫秒转换读，
     * 写入侧改用 {@code CURRENT_TIMESTAMP}（见 {@code insert}）。</p>
     */
    String COLS = "id, title, content, domain, category, knowledge_category as knowledgeCategory, "
            + "rule_ref as ruleRef, source as sourceType, status, "
            + "CAST(extract(epoch from created_at) * 1000 AS BIGINT) as createdAt, "
            + "CAST(extract(epoch from updated_at) * 1000 AS BIGINT) as updatedAt";

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

    /**
     * 改分类（#147U 编辑腿）：分类与 {@code rule_ref} 同批写——非 Rule 类必须写 NULL，
     * 否则会留下降级前的影子规则引用。返回值 0 表示条目不存在（由服务层转 404）。
     */
    @Update("UPDATE ecos_knowledge.knowledge_article SET knowledge_category = #{category}, "
            + "rule_ref = #{ruleRef}, updated_at = CURRENT_TIMESTAMP WHERE id = #{id}")
    int updateCategory(@Param("id") String id, @Param("category") String category,
                       @Param("ruleRef") String ruleRef);

    /**
     * 插入条目。时间列用 {@code CURRENT_TIMESTAMP} 落库（实体侧 epoch 毫秒仅用于出参，
     * 不参与写入），避免 {@code bigint} 打 {@code timestamp} 列的类型错误（#147U 实测修复）。
     */
    @Insert("INSERT INTO ecos_knowledge.knowledge_article "
            + "(id, title, content, domain, category, knowledge_category, rule_ref, source, status, created_at, updated_at) "
            + "VALUES (#{id}, #{title}, #{content}, #{domain, jdbcType=VARCHAR}, #{category, jdbcType=VARCHAR}, "
            + "#{knowledgeCategory, jdbcType=VARCHAR}, #{ruleRef, jdbcType=VARCHAR}, "
            + "#{sourceType, jdbcType=VARCHAR}, #{status}, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")
    int insert(KnowledgeArticle article);

    @Select("SELECT COUNT(*) FROM ecos_knowledge.knowledge_article")
    long count();
}
