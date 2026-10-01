package com.chinacreator.gzcm.engine.ontology.dao;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Repository;

/**
 * B3-a 数据访问收口（R2-B3 / 架构铁律 §2.5 #1 / IR06）：{@code OntologyMappingService}
 * （本体映射表 ecos_entity_table_mapping 等）的 JDBC 落点。
 *
 * <p>SQL 逐个搬运，只搬不改语义（含 B0 V200 前修中已定型的 schema 限定形态）；
 * C4 校验业务逻辑、JSONB 解析、VO 转换保留在 Service 层。</p>
 */
@Repository
public class OntologyMappingDao extends JdbcTemplateDaoSupport {

    /** 映射表显式列清单（禁 {@code SELECT *}，架构铁律 §六 SQL 规范；含 Q2 新增列 materialized、W2 新增列 doc_anchor_json/doc_anchor_type）。 */
    static final String MAPPING_COLUMNS =
            "id, entity_code, entity_name, domain_code, datasource_id, resource_name, table_schema, "
                    + "field_mappings, materialized, doc_anchor_json, doc_anchor_type, created_at, updated_at";

    /** 映射表显式列清单（带 {@code m.} 别名，联表查询用）。 */
    static final String MAPPING_COLUMNS_ALIASED =
            "m.id, m.entity_code, m.entity_name, m.domain_code, m.datasource_id, m.resource_name, "
                    + "m.table_schema, m.field_mappings, m.materialized, m.doc_anchor_json, m.doc_anchor_type, "
                    + "m.created_at, m.updated_at";

    /**
     * 查询映射列表（可按 objectId / sourceType 过滤）。返回原始行。
     */
    public List<Map<String, Object>> listMappings(String objectId, String sourceType) {
        StringBuilder sql = new StringBuilder(
                "SELECT " + MAPPING_COLUMNS + " FROM ecos_entity_table_mapping WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (objectId != null && !objectId.isBlank()) {
            sql.append(" AND entity_code=?");
            params.add(objectId);
        }
        if (sourceType != null && !sourceType.isBlank()) {
            sql.append(" AND domain_code=?");
            params.add(sourceType);
        }
        sql.append(" ORDER BY created_at DESC");

        return jdbcTemplate.queryForList(sql.toString(), params.toArray());
    }

    /**
     * 查询单个映射详情，不存在返回 null。
     */
    public Map<String, Object> findMappingById(String id) {
        try {
            return jdbcTemplate.queryForMap(
                    "SELECT " + MAPPING_COLUMNS + " FROM ecos_entity_table_mapping WHERE id=?", id);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    /**
     * 插入新映射记录（W2 扩展：非结构化文档锚点）。
     *
     * @param materialized   是否参与图谱实例化（Q2 裁决）；{@code null} 时按默认 {@code true} 落库
     * @param docAnchorJson  非结构化文档锚点 JSON（{@code doc_anchor_json} 列，W2 新增；
     *                       {@code null} = 无锚点）
     * @param docAnchorType  锚点类型（{@code doc_anchor_type} 列，W2 新增；
     *                       TABLE/DOC_ONLY/MIXED，{@code null} = 未显式指定）
     */
    public void insertMapping(String id, String objectId, String sourceName,
                              String sourceType, String sourceUri, String fieldMappingsJson,
                              Boolean materialized, String docAnchorJson, String docAnchorType) {
        String normalizedType = (docAnchorType == null || docAnchorType.isBlank())
                ? null : docAnchorType.trim();
        jdbcTemplate.update(
                "INSERT INTO ecos_entity_table_mapping (id, entity_code, entity_name, domain_code, datasource_id, resource_name, table_schema, field_mappings, materialized, doc_anchor_json, doc_anchor_type, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?::jsonb, ?, NOW(), NOW())",
                id, objectId, sourceName, sourceType, "",
                sourceName, sourceUri, fieldMappingsJson,
                materialized == null ? Boolean.TRUE : materialized, docAnchorJson, normalizedType);
    }

    /**
     * 更新映射记录（拼接 SQL），返回更新后的行。
     */
    public Map<String, Object> updateMapping(String id, String sql, List<Object> params) {
        jdbcTemplate.update(sql, params.toArray());
        return jdbcTemplate.queryForMap(
                "SELECT " + MAPPING_COLUMNS + " FROM ecos_entity_table_mapping WHERE id=?", id);
    }

    /**
     * 删除映射记录。返回被删除的行（用于日志），不存在返回 null。
     */
    public Map<String, Object> deleteMapping(String id) {
        Map<String, Object> existing;
        try {
            existing = jdbcTemplate.queryForMap(
                    "SELECT " + MAPPING_COLUMNS + " FROM ecos_entity_table_mapping WHERE id=?", id);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
        jdbcTemplate.update("DELETE FROM ecos_entity_table_mapping WHERE id=?", id);
        return existing;
    }

    /**
     * 按本体 ID 查询映射契约全量（方案 §5.3「实例抽取入口」）原始行。
     */
    public List<Map<String, Object>> listMappingsByOntology(String ontologyId) {
        String sql = "SELECT " + MAPPING_COLUMNS_ALIASED + " FROM ecos_entity_table_mapping m "
                + "WHERE m.entity_code IN ("
                + "SELECT e.id FROM ecos_ontology.ecos_ontology_entity e WHERE e.ontology_id = ? AND e.is_deleted = 0 "
                + "UNION "
                + "SELECT e.code FROM ecos_ontology.ecos_ontology_entity e WHERE e.ontology_id = ? AND e.is_deleted = 0"
                + ") ORDER BY m.created_at DESC";
        return jdbcTemplate.queryForList(sql, ontologyId, ontologyId);
    }

    /**
     * 查本体属性类型（本引擎自有表 {@code ecos_ontology_property}）。
     *
     * <p>实体与属性均按「id 或 code」双匹配，兼容 {@code entity_code} 存 id 或 code 的历史差异。
     *
     * @return 属性类型（STRING/NUMBER/BOOLEAN/DATETIME）；查不到返回 null
     */
    public String resolveOntologyPropertyType(String entityRef, String propertyRef) {
        if (entityRef == null || entityRef.isBlank() || propertyRef == null || propertyRef.isBlank()) {
            return null;
        }
        try {
            List<String> types = jdbcTemplate.queryForList(
                    "SELECT p.property_type FROM ecos_ontology_property p "
                            + "JOIN ecos_ontology_entity e ON p.entity_id = e.id "
                            + "WHERE (e.id = ? OR e.code = ?) AND (p.id = ? OR p.code = ?) "
                            + "AND p.is_deleted = 0 LIMIT 1",
                    String.class, entityRef, entityRef, propertyRef, propertyRef);
            return types.isEmpty() ? null : types.get(0);
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(OntologyMappingDao.class)
                    .warn("查询本体属性类型失败 entity={} property={}: {}", entityRef, propertyRef, e.getMessage());
            return null;
        }
    }
}
