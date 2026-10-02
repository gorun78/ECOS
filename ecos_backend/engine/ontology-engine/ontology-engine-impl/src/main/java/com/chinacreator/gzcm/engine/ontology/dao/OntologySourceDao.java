package com.chinacreator.gzcm.engine.ontology.dao;

import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

/**
 * B3-a 数据访问收口（R2-B3 / 架构铁律 §2.5 #1 / IR06）：{@code OntologySourceService}
 * （本体对象来源三表 JOIN 聚合查询）的 JDBC 落点。SQL 原样搬运，VO 映射保留在 Service 层。
 */
@Repository
public class OntologySourceDao extends JdbcTemplateDaoSupport {

    /**
     * 三表 JOIN（ecos_entity_table_mapping × ecos_ontology_entity × ecos_ontology_domain），
     * 可选 domainCode 过滤，LIMIT 兜底由调用方控制。
     */
    public List<Map<String, Object>> listSourcesRaw(String domainCode, int limit) {
        String sql = "SELECT "
            + "  e.id          AS \"ontologyId\", "
            + "  d.code        AS \"sourceDomain\", "
            + "  m.resource_name AS \"originManifest\", "
            + "  e.created_at  AS \"createdAt\", "
            + "FROM ecos_entity_table_mapping m "
            + "INNER JOIN ecos_ontology_entity e ON e.code = m.entity_code "
            + "INNER JOIN ecos_ontology_domain d ON d.id = e.domain_id AND d.code = m.domain_code";
        if (domainCode != null && !domainCode.isBlank()) {
            sql += " WHERE m.domain_code = ?";
        }
        sql += " ORDER BY m.created_at DESC LIMIT ?";

        boolean hasFilter = domainCode != null && !domainCode.isBlank();
        return hasFilter
            ? jdbcTemplate.queryForList(sql, domainCode, limit)
            : jdbcTemplate.queryForList(sql, limit);
    }
}
