package com.chinacreator.gzcm.engine.ontology.dao;

import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

/**
 * B3-a 数据访问收口（R2-B3 / 架构铁律 §2.5 #1 / IR06）：{@code AutoDiscoverService}
 * （本体自动发现写路径：entity/property/mapping 落库）的 JDBC 落点。
 *
 * <p>方法形如 {@code xxx(checkPart, insertPart)}：把原方法内的「先查重、再/后插入」
 * 这段原子 SQL 编排整体下沉，Service 侧仅保留 code 推导、id 生成、类型映射、
 * JSON 组装等业务逻辑。SQL 文本、参数序原样搬运（含 {@code ?::jsonb} 强转与
 * ON CONFLICT upsert）。</p>
 */
@Repository
public class AutoDiscoverDao extends JdbcTemplateDaoSupport {

    /**
     * 查询实体表映射（不分页，按 domain 可选过滤，按创建时间倒序）。
     */
    public List<Map<String, Object>> listMappings(String domainCode) {
        String sql;
        Object[] params;
        if (domainCode != null && !domainCode.isBlank()) {
            sql = "SELECT * FROM ecos_entity_table_mapping WHERE domain_code=? ORDER BY created_at DESC";
            params = new Object[]{domainCode};
        } else {
            sql = "SELECT * FROM ecos_entity_table_mapping ORDER BY created_at DESC";
            params = new Object[]{};
        }
        return jdbcTemplate.queryForList(sql, params);
    }

    /**
     * 实体查重 + 落库：存在则取回已有行并返回，否则 INSERT 并由 Service 组装返回行。
     *
     * @return 已存在时返回存量行（id/code/name），不存在返回 null
     */
    public Map<String, Object> createOntologyEntity(String entityCode, String domainCode,
                                                    String id, String ontologyId, String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ecos_ontology_entity WHERE code=? AND domain_id=(SELECT id FROM ecos_ontology_domain WHERE code=?)",
                Integer.class, entityCode, domainCode);
        if (count != null && count > 0) {
            return jdbcTemplate.queryForMap(
                    "SELECT id, code, name FROM ecos_ontology_entity WHERE code=? AND domain_id=(SELECT id FROM ecos_ontology_domain WHERE code=?)",
                    entityCode, domainCode);
        }

        jdbcTemplate.update(
                "INSERT INTO ecos_ontology_entity (id, ontology_id, code, name, entity_type, description, domain_id, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, 'MASTER', ?, (SELECT id FROM ecos_ontology_domain WHERE code=?), NOW(), NOW())",
                id, ontologyId, entityCode, tableName, "Auto-discovered from " + tableName, domainCode);
        return null;
    }

    /**
     * 按 domain code 查本体主记录，未命中返回 null（INSERT 由 Service 侧第二次编排）。
     */
    public String findOntologyId(String domainCode) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT o.id FROM ecos_ontology o WHERE o.code=?", domainCode.toLowerCase());
        if (!rows.isEmpty()) {
            return (String) rows.get(0).get("id");
        }
        return null;
    }

    /** 本体主记录落库（查无时）。 */
    public void insertOntology(String oid, String domainCode) {
        jdbcTemplate.update(
                "INSERT INTO ecos_ontology (id, code, name, version, status, description) " +
                "VALUES (?, ?, ?, '1.0', 'DRAFT', ?)",
                oid, domainCode.toLowerCase(), domainCode + " Ontology",
                "Auto-created for domain " + domainCode);
    }

    /**
     * 属性查重（同 code + entity 已存在时返回已有行数）。
     */
    public Integer countEntityProperty(String propCode, String entityCode, String domainCode) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ecos_ontology_property WHERE code=? AND entity_id=" +
                "(SELECT id FROM ecos_ontology_entity WHERE code=? AND domain_id=(SELECT id FROM ecos_ontology_domain WHERE code=?))",
                Integer.class, propCode, entityCode, domainCode);
    }

    /** 属性落库。 */
    public void insertEntityProperty(String propId, String propCode, String fieldName,
                                     String ontologyType, String entityCode, String domainCode) {
        jdbcTemplate.update(
                "INSERT INTO ecos_ontology_property (id, code, name, property_type, entity_id, searchable_flag, required_flag, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, (SELECT id FROM ecos_ontology_entity WHERE code=? AND domain_id=(SELECT id FROM ecos_ontology_domain WHERE code=?)), 1, 0, NOW(), NOW())",
                propId, propCode, fieldName, ontologyType, entityCode, domainCode);
    }

    /**
     * 映射落库（ON CONFLICT (entity_code, datasource_id, resource_name) upsert）。
     */
    public void upsertMapping(String id, String entityCode, String entityName, String domainCode,
                              String datasourceId, String resourceName, String schema,
                              String fieldMappingsJson) {
        jdbcTemplate.update(
                "INSERT INTO ecos_entity_table_mapping (id, entity_code, entity_name, domain_code, datasource_id, resource_name, table_schema, field_mappings, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, NOW(), NOW()) " +
                "ON CONFLICT (entity_code, datasource_id, resource_name) DO UPDATE SET field_mappings=?::jsonb, updated_at=NOW()",
                id, entityCode, entityName, domainCode, datasourceId, resourceName, schema, fieldMappingsJson, fieldMappingsJson);
    }

    /** 按主键读回落库后的映射行。 */
    public Map<String, Object> findMappingById(String id) {
        return jdbcTemplate.queryForMap("SELECT * FROM ecos_entity_table_mapping WHERE id=?", id);
    }
}
