package com.chinacreator.gzcm.engine.ontology.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

/**
 * B3-a 数据访问收口（R2-B3 / 架构铁律 §2.5 #1 / IR06）：{@code AutoDiscoverPreviewQueryService}
 * （自动发现预览链路只读计数）的 JDBC 落点。SQL 原样搬运。
 */
@Repository
public class AutoDiscoverPreviewQueryDao extends JdbcTemplateDaoSupport {

    /**
     * 按主键统计数据源行数（存在性判定）。
     *
     * @param datasourceId 数据源 ID
     * @return 匹配行数（0 或 1）
     */
    public Integer countDatasourceById(String datasourceId) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM td_datasource WHERE datasource_id = ?",
            Integer.class, datasourceId);
    }

    /**
     * 按实体码 + 业务域统计数据模型行数（已发现判定）。
     */
    public Integer countEntityByCodeAndDomain(String entityCode, String domainCode) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM ecos_ontology_entity " +
            "WHERE code = ? AND domain_id = " +
            "(SELECT id FROM ecos_ontology_domain WHERE code = ?)",
            Integer.class, entityCode, domainCode);
    }
}
