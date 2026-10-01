package com.chinacreator.gzcm.engine.ontology.dao;

import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

/**
 * B3-a 数据访问收口（R2-B3 / 架构铁律 §2.5 #1 / IR06）：{@code OntologyKgSyncService}
 * （Ontology → KG 同步前的实体/关系摘要读取）的 JDBC 落点。SQL 原样搬运。
 */
@Repository
public class OntologyKgSyncDao extends JdbcTemplateDaoSupport {

    /** 读实体 code 摘要（本体自有表）。 */
    public List<Map<String, Object>> findEntityCodes() {
        return jdbcTemplate.queryForList("SELECT code FROM ecos_ontology_entity");
    }

    /** 读关系 id 摘要。 */
    public List<Map<String, Object>> findRelationshipIds() {
        return jdbcTemplate.queryForList("SELECT r.id FROM ecos_ontology_relationship r");
    }
}
