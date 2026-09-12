package com.chinacreator.gzcm.engine.ontology.service;

import com.chinacreator.gzcm.engine.ontology.client.DataNetResourceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * AutoDiscoverService — 本体自动发现。
 *
 * <p>Wave B-2 · T13 (来源: 肖国荣 / 日期: 2026-09-12 / 责任人: fullstack-implementer)</p>
 *
 * <p>T13 改造: 原 {@code getResourceFields} 用 JdbcTemplate 直查 datanet 引擎表
 * {@code td_data_field} / {@code td_data_resource} (跨引擎指定, 违反铁律 §0.3.1 微服务边界),
 * 改为 delegate 到 {@link DataNetResourceClient} 走 datanet service REST。
 * 本类仍保留 JdbcTemplate 用于查询本体自己的表 (ecos_ontology_entity / ecos_domain /
 * ecos_ontology_property / ecos_entity_table_mapping), 属本引擎合理范围。</p>
 */
@Service
public class AutoDiscoverService {

    private static final Logger log = LoggerFactory.getLogger(AutoDiscoverService.class);

    private final JdbcTemplate jdbc;
    private final DataNetResourceClient dataNetClient;

    public AutoDiscoverService(JdbcTemplate jdbc, DataNetResourceClient dataNetClient) {
        this.jdbc = jdbc;
        this.dataNetClient = dataNetClient;
    }

    public List<Map<String, Object>> autoDiscover(String domainCode, String datasourceId, List<String> resourceNames) {
        List<Map<String, Object>> results = new ArrayList<>();

        for (String resourceName : resourceNames) {
            List<Map<String, Object>> fields = getResourceFields(datasourceId, resourceName);
            if (fields.isEmpty()) {
                log.warn("No fields found for resource: {}", resourceName);
                continue;
            }

            String entityCode = toEntityCode(resourceName);
            Map<String, Object> entity = createOntologyEntity(entityCode, resourceName, domainCode);

            int propCount = 0;
            for (Map<String, Object> field : fields) {
                String fieldName = (String) field.get("fieldName");
                String dataType = (String) field.get("dataType");
                if (fieldName == null) continue;
                createEntityProperty(entityCode, fieldName, dataType, domainCode);
                propCount++;
            }

            Map<String, Object> mapping = createMapping(entityCode, resourceName, domainCode, datasourceId,
                    (String) fieldValue(fields, "schema"), fields);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("entityCode", entityCode);
            result.put("entityName", resourceName);
            result.put("domainCode", domainCode);
            result.put("propertyCount", propCount);
            result.put("mapping", mapping);
            results.add(result);

            log.info("Auto-discovered: {} -> entity {} with {} properties", resourceName, entityCode, propCount);
        }

        return results;
    }

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
        return jdbc.queryForList(sql, params);
    }

    /**
     * 拿数据资源下的候选字段(用于本体自动发现预览)。
     *
     * <p>Wave B-2 T13: 从 JdbcTemplate 直查 datanet 引擎表 (td_data_field / td_data_resource)
     * 改为 delegate 到 {@link DataNetResourceClient} 走 datanet service REST。
     * endpoint `GET /api/v1/datanet/resources/{resourceId}/fields` 尚未落地
     * (TODO PMO-06 T19 补 endpoint 合同), client 在端点不可达时返回空列表,
     * 不阻断 autoDiscover 主流程, 自动发现候选将为空但不 500。</p>
     */
    private List<Map<String, Object>> getResourceFields(String datasourceId, String resourceName) {
        try {
            // T13: 调 datanet service REST 拿字段候选 (不直查 td_* 跨引擎表)
            return dataNetClient.listResourceFields(resourceName);
        } catch (Exception e) {
            log.warn("Cannot fetch fields via datanet REST for {}: {}", resourceName, e.getMessage());
            return Collections.emptyList();
        }
    }

    private Map<String, Object> createOntologyEntity(String entityCode, String tableName, String domainCode) {
        String id = UUID.randomUUID().toString().replace("-", "").substring(0, 32);
        String ontologyId = findOrCreateOntology(domainCode);

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ecos_ontology_entity WHERE code=? AND domain_id=(SELECT id FROM ecos_domain WHERE code=?)",
                Integer.class, entityCode, domainCode);
        if (count != null && count > 0) {
            log.info("Entity {} already exists in domain {}", entityCode, domainCode);
            return jdbc.queryForMap(
                    "SELECT id, code, name FROM ecos_ontology_entity WHERE code=? AND domain_id=(SELECT id FROM ecos_domain WHERE code=?)",
                    entityCode, domainCode);
        }

        jdbc.update(
                "INSERT INTO ecos_ontology_entity (id, ontology_id, code, name, entity_type, description, domain_id, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, 'MASTER', ?, (SELECT id FROM ecos_domain WHERE code=?), NOW(), NOW())",
                id, ontologyId, entityCode, tableName, "Auto-discovered from " + tableName, domainCode);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("code", entityCode);
        result.put("name", tableName);
        return result;
    }

    private String findOrCreateOntology(String domainCode) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT o.id FROM ecos_ontology o WHERE o.code=?", domainCode.toLowerCase());
        if (!rows.isEmpty()) {
            return (String) rows.get(0).get("id");
        }

        String oid = UUID.randomUUID().toString().replace("-", "").substring(0, 32);
        jdbc.update(
                "INSERT INTO ecos_ontology (id, code, name, version, status, description) " +
                "VALUES (?, ?, ?, '1.0', 'DRAFT', ?)",
                oid, domainCode.toLowerCase(), domainCode + " Ontology",
                "Auto-created for domain " + domainCode);
        return oid;
    }

    private void createEntityProperty(String entityCode, String fieldName, String dataType, String domainCode) {
        String propCode = fieldName;
        String ontologyType = mapSqlType(dataType);
        String propId = UUID.randomUUID().toString().replace("-", "").substring(0, 32);

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ecos_ontology_property WHERE code=? AND entity_id=" +
                "(SELECT id FROM ecos_ontology_entity WHERE code=? AND domain_id=(SELECT id FROM ecos_domain WHERE code=?))",
                Integer.class, propCode, entityCode, domainCode);
        if (count != null && count > 0) return;

        jdbc.update(
                "INSERT INTO ecos_ontology_property (id, code, name, property_type, entity_id, searchable_flag, required_flag, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, (SELECT id FROM ecos_ontology_entity WHERE code=? AND domain_id=(SELECT id FROM ecos_domain WHERE code=?)), 1, 0, NOW(), NOW())",
                propId, propCode, fieldName, ontologyType, entityCode, domainCode);
    }

    private Map<String, Object> createMapping(String entityCode, String resourceName, String domainCode,
                                               String datasourceId, String schema, List<Map<String, Object>> fields) {
        String id = UUID.randomUUID().toString().replace("-", "").substring(0, 32);
        String entityName = resourceName;

        List<Map<String, String>> fmList = new ArrayList<>();
        for (Map<String, Object> f : fields) {
            Map<String, String> fm = new LinkedHashMap<>();
            fm.put("field", (String) f.get("fieldName"));
            fm.put("type", (String) f.get("dataType"));
            fm.put("propertyCode", (String) f.get("fieldName"));
            fmList.add(fm);
        }

        String fieldMappingsJson = toJsonString(fmList);

        jdbc.update(
                "INSERT INTO ecos_entity_table_mapping (id, entity_code, entity_name, domain_code, datasource_id, resource_name, table_schema, field_mappings, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, NOW(), NOW()) " +
                "ON CONFLICT (entity_code, datasource_id, resource_name) DO UPDATE SET field_mappings=?::jsonb, updated_at=NOW()",
                id, entityCode, entityName, domainCode, datasourceId, resourceName, schema, fieldMappingsJson, fieldMappingsJson);

        return jdbc.queryForMap("SELECT * FROM ecos_entity_table_mapping WHERE id=?", id);
    }

    private String toEntityCode(String tableName) {
        StringBuilder sb = new StringBuilder();
        boolean capitalize = true;
        for (char c : tableName.toCharArray()) {
            if (c == '_' || c == '-' || c == ' ') {
                capitalize = true;
            } else if (capitalize) {
                sb.append(Character.toUpperCase(c));
                capitalize = false;
            } else {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }

    private String mapSqlType(String sqlType) {
        if (sqlType == null) return "STRING";
        String t = sqlType.toUpperCase();
        if (t.contains("INT") || t.contains("BIGINT") || t.contains("SMALLINT") || t.contains("TINYINT") || t.contains("NUMERIC") || t.contains("DECIMAL") || t.contains("FLOAT") || t.contains("DOUBLE"))
            return "NUMBER";
        if (t.contains("BOOL"))
            return "BOOLEAN";
        if (t.contains("DATE") || t.contains("TIME") || t.contains("TIMESTAMP"))
            return "DATETIME";
        return "STRING";
    }

    private Object fieldValue(List<Map<String, Object>> fields, String key) {
        if (fields.isEmpty()) return null;
        return fields.get(0).get(key);
    }

    private String toJsonString(Object obj) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(obj);
        } catch (Exception e) {
            return "[]";
        }
    }
}
