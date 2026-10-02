package com.chinacreator.gzcm.engine.kb.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * <p>V200/R3-B1: schema 由 `${kb.control-schema:ecos_knowledge}` 注入 (MC06 全面落地)。</p>
 */
@Repository
public class KnowledgeGraphDao extends JdbcTemplateDaoSupport {

    private final String schema;

    public KnowledgeGraphDao(
            @Value("${kb.control-schema:ecos_knowledge}") String schema) {
        this.schema = schema;
    }

    /**
     * PMO-C T3: 把 categoryIds 映射为可访问节点白名单（kb_nav_article_rel scope=category 的 node_id）。
     *
     * <p>参数化占位（IR05 红线：禁字符串拼接业务 id）。读取失败原样上浮，由调用方降级。</p>
     *
     * @param cleaned 清洗后的 category id 列表（非空）
     * @return node_id 原始行列表
     */
    public List<Map<String, Object>> resolveCategoryNodeIds(List<String> cleaned) {
        List<Object> args = new ArrayList<>(cleaned.size());
        StringBuilder inClause = new StringBuilder("(");
        for (int i = 0; i < cleaned.size(); i++) {
            if (i > 0) {
                inClause.append(" OR ");
            }
            inClause.append("c.id = ?");
            args.add(cleaned.get(i));
        }
        inClause.append(")");
        String sql = "SELECT DISTINCT r.node_id FROM " + schema + ".kb_nav_article_rel r "
                + "JOIN " + schema + ".kb_nav_category c ON c.id = r.node_id AND c.is_deleted = 0 "
                + "WHERE r.is_deleted = 0 AND r.scope = 'category' AND (" + inClause + ")";
        return jdbcTemplate.queryForList(sql, args.toArray());
    }
}
