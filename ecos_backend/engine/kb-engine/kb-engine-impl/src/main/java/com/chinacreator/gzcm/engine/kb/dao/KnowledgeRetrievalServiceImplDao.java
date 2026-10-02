package com.chinacreator.gzcm.engine.kb.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * <p>V200/R3-B1: schema 由 `${kb.control-schema:ecos_knowledge}` 注入 (MC06 全面落地)。</p>
 *
 * <p>数据访问收口（R2 / B2-kb / 架构铁律 §2.5 / IR06）：
 * {@code service.KnowledgeRetrievalServiceImpl} 的知识导航目录树 / 标签过滤 JDBC 落点。
 *
 * <p>SQL 原样搬运自原 {@code KnowledgeRetrievalServiceImpl#resolveCategorySubtree /
 * matchesNavFilter / matchesTagFilter}（表 {@code " + schema + ".kb_nav_category /
 * kb_nav_article_rel / kb_nav_tag}，<b>带 schema 前缀</b>，verbatim 保留；
 * OR 占位符拼装逻辑一并下沉，业务语义不变）。</p>
 */
@Repository
public class KnowledgeRetrievalServiceImplDao extends JdbcTemplateDaoSupport {

    private final String schema;

    public KnowledgeRetrievalServiceImplDao(
            @Value("${kb.control-schema:ecos_knowledge}") String schema) {
        this.schema = schema;
    }

    /**
     * 展开 categoryIds → 导航目录 path 行（id + path）。NULL/空入参不进入本方法（调用方前置判空）。
     */
    public List<Map<String, Object>> selectCategoryPaths(List<String> categoryIds) {
        List<Object> args = new ArrayList<>(categoryIds.size());
        StringBuilder inClause = new StringBuilder("(");
        for (int i = 0; i < categoryIds.size(); i++) {
            if (i > 0) {
                inClause.append(" OR ");
            }
            inClause.append("id = ?");
            args.add(categoryIds.get(i));
        }
        inClause.append(")");
        String sql = "SELECT id, path FROM " + schema + ".kb_nav_category "
                + "WHERE is_deleted = 0 AND (" + inClause + ")";
        return jdbcTemplate.queryForList(sql, args.toArray());
    }

    /** 判断 articleId 在给定子树 path 前缀下的关联计数（&gt;0 即命中）。 */
    public Integer countCategoryPathMatch(String articleId, String pathPrefix) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + schema + ".kb_nav_article_rel r "
                        + "JOIN " + schema + ".kb_nav_category c ON c.id = r.node_id AND c.is_deleted = 0 "
                        + "WHERE r.is_deleted = 0 AND r.scope = 'category' AND r.article_id = ? "
                        + "AND c.path LIKE ?",
                Integer.class, articleId, pathPrefix);
    }

    /**
     * 判断 articleId 是否命中给定标签名集合（OR 匹配，&gt;0 即命中）；无关联行返回 0。
     */
    public long countTagMatch(String articleId, List<String> tags) {
        List<Object> args = new ArrayList<>(tags.size() + 1);
        args.add(articleId);
        StringBuilder inClause = new StringBuilder("(");
        for (int i = 0; i < tags.size(); i++) {
            if (i > 0) {
                inClause.append(" OR ");
            }
            inClause.append("t.tag_name = ?");
            args.add(tags.get(i));
        }
        inClause.append(")");
        String sql = "SELECT COUNT(*) AS cnt FROM " + schema + ".kb_nav_article_rel r "
                + "JOIN " + schema + ".kb_nav_tag t ON t.id = r.node_id AND t.is_deleted = 0 "
                + "WHERE r.is_deleted = 0 AND r.scope = 'tag' AND r.article_id = ? "
                + "AND (" + inClause + ")";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, args.toArray());
        if (rows.isEmpty()) {
            return 0L;
        }
        Object cnt = rows.get(0).get("cnt");
        return (cnt instanceof Number num) ? num.longValue() : 0L;
    }
}
