package com.chinacreator.gzcm.engine.ontology.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

/**
 * B3-a 数据访问收口（R2-B3 / 架构铁律 §2.5 #1 / IR06）：{@code OntologyEngineHealthService}
 * （本体引擎健康探测 + 计数）的 JDBC 落点。SQL 原样搬运。
 */
@Repository
public class OntologyEngineHealthDao extends JdbcTemplateDaoSupport {

    /** 探测数据库连通性。成功返回 1，异常由调用方捕获。 */
    public Integer pingDb() {
        return jdbcTemplate.queryForObject("SELECT 1", Integer.class);
    }

    public long countOntologies() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ecos_ontology", Long.class);
    }

    public long countEntities() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ecos_ontology_entity", Long.class);
    }

    public long countProperties() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ecos_ontology_property", Long.class);
    }

    public long countRelationships() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ecos_ontology_relationship", Long.class);
    }
}
