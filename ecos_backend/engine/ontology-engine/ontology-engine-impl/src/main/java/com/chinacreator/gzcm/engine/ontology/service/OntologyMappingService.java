package com.chinacreator.gzcm.engine.ontology.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.chinacreator.gzcm.engine.ontology.dto.OntologyMappingSaveDTO;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyMappingVO;

/**
 * 本体映射持久化服务 — 封装 ecos_entity_table_mapping 表的 SQL 操作。
 *
 * <p>由 {@link com.chinacreator.gzcm.engine.ontology.controller.OntologyMappingController}
 * 调用，Controller 层不再直接持有 {@link JdbcTemplate}。</p>
 */
@Service
public class OntologyMappingService {

    private final JdbcTemplate jdbc;

    public OntologyMappingService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 查询映射列表（可按 objectId / sourceType 过滤）。返回原始行。
     */
    public List<Map<String, Object>> listMappings(String objectId, String sourceType) {
        StringBuilder sql = new StringBuilder("SELECT * FROM ecos_entity_table_mapping WHERE 1=1");
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

        return jdbc.queryForList(sql.toString(), params.toArray());
    }

    /**
     * 强类型 VO 版（T16-2）：内层把 row 转成 {@link OntologyMappingVO}。
     * <p>旧 {@link #listMappings} 签名保留（C1 mock / 内部消费者兼容）。
     */
    public List<OntologyMappingVO> listMappingsVO(String objectId, String sourceType) {
        List<Map<String, Object>> rows = listMappings(objectId, sourceType);
        List<OntologyMappingVO> result = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            result.add(toVO(row));
        }
        return result;
    }

    /**
     * 查询单个映射详情，不存在返回 null。
     */
    public Map<String, Object> findMappingById(String id) {
        try {
            return jdbc.queryForMap(
                    "SELECT * FROM ecos_entity_table_mapping WHERE id=?", id);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    /**
     * 强类型 VO 版（T16-2）；不存在返回 null。
     */
    public OntologyMappingVO findMappingByIdVO(String id) {
        Map<String, Object> row = findMappingById(id);
        return row == null ? null : toVO(row);
    }

    /**
     * 插入新映射记录。
     */
    public void insertMapping(String id, String objectId, String sourceName,
                              String sourceType, String sourceUri, String fieldMappingsJson) {
        jdbc.update(
                "INSERT INTO ecos_entity_table_mapping (id, entity_code, entity_name, domain_code, datasource_id, resource_name, table_schema, field_mappings, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, NOW(), NOW())",
                id, objectId, sourceName, sourceType, "",
                sourceName, sourceUri, fieldMappingsJson);
    }

    /**
     * 强类型 VO 版（T16-2）。行为与 {@link #insertMapping} 一致，但消费
     * {@link OntologyMappingSaveDTO} 强类型入参，便于审计与可读性。
     */
    public void insertMappingVO(String id, OntologyMappingSaveDTO dto) {
        jdbc.update(
                "INSERT INTO ecos_entity_table_mapping (id, entity_code, entity_name, domain_code, datasource_id, resource_name, table_schema, field_mappings, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, NOW(), NOW())",
                id, dto.getObjectId(), dto.getSourceName(), dto.getSourceType(), "",
                dto.getSourceName(), dto.getSourceUri(), dto.getFieldMappingsJson());
    }

    /**
     * 更新映射记录（拼接 SQL），返回更新后的行，不存在返回 null。
     */
    public Map<String, Object> updateMapping(String id, StringBuilder sql, List<Object> params) {
        jdbc.update(sql.toString(), params.toArray());
        return jdbc.queryForMap(
                "SELECT * FROM ecos_entity_table_mapping WHERE id=?", id);
    }

    /**
     * 强类型 VO 版（T16-2）。
     */
    public OntologyMappingVO updateMappingVO(String id, StringBuilder sql, List<Object> params) {
        jdbc.update(sql.toString(), params.toArray());
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT * FROM ecos_entity_table_mapping WHERE id=?", id);
        return toVO(row);
    }

    /**
     * 删除映射记录。返回被删除的行（用于日志），不存在返回 null。
     */
    public Map<String, Object> deleteMapping(String id) {
        Map<String, Object> existing;
        try {
            existing = jdbc.queryForMap("SELECT * FROM ecos_entity_table_mapping WHERE id=?", id);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
        jdbc.update("DELETE FROM ecos_entity_table_mapping WHERE id=?", id);
        return existing;
    }

    /**
     * 行 → {@link OntologyMappingVO} 转换（T16-2）。
     *
     * <p>字段映射严格对齐 {@code OntologyMappingController.rowToApiMap}：
     * id / objectId / objectTypeId / datasetId / objectType / sourceType /
     * sourceName / sourceUri / fieldMappings / propertyMappings / description / status /
     * createdAt / updatedAt。
     * {@code field_mappings} 列的 JSONB 形态可能是 Map / String / PGobject，统一落到 VO 字段。
     *
     * <p>JSDoc 溯源 T16-2。
     */
    private OntologyMappingVO toVO(Map<String, Object> row) {
   OntologyMappingVO vo = new OntologyMappingVO();
        String rowId = String.valueOf(row.get("id"));
        String entityCode = String.valueOf(row.getOrDefault("entity_code", ""));
        String entityName = String.valueOf(row.getOrDefault("entity_name", ""));
        String domainCode = String.valueOf(row.getOrDefault("domain_code", ""));
        String resourceName = String.valueOf(row.getOrDefault("resource_name", ""));
        String tableSchema = String.valueOf(row.getOrDefault("table_schema", ""));

        Object fmObj = row.get("field_mappings");
        Map<String, Object> attrs = parseFieldMappings(fmObj);

        vo.setId(rowId);
        vo.setObjectId(entityCode);
        vo.setObjectTypeId(entityCode);
        vo.setDatasetId(attrs.getOrDefault("datasetId", domainCode));
        vo.setObjectType(String.valueOf(attrs.getOrDefault("objectType", "ENTITY")));
        vo.setSourceType(domainCode);
        vo.setSourceName(entityName.isEmpty() ? resourceName : entityName);
        vo.setSourceUri(tableSchema);
        vo.setFieldMappings(toFieldList(attrs.getOrDefault("fieldMappings", new ArrayList<>())));
        vo.setPropertyMappings(toPropMap(attrs.getOrDefault("propertyMappings", new LinkedHashMap<>())));
        vo.setDescription(String.valueOf(attrs.getOrDefault("description", "")));
        vo.setStatus(String.valueOf(attrs.getOrDefault("status", "ACTIVE")));
        vo.setCreatedAt(String.valueOf(row.getOrDefault("created_at", "")));
        vo.setUpdatedAt(String.valueOf(row.getOrDefault("updated_at", "")));
        return vo;
    }

    /** 解析 field_mappings JSONB（可能是 PGobject / String / Map），与 Controller 旧版行为一致。 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseFieldMappings(Object fmObj) {
        if (fmObj == null) {
            return new LinkedHashMap<>();
        }
        if (fmObj instanceof Map) {
            return new LinkedHashMap<>((Map<String, Object>) fmObj);
        }
        try {
            return MAPPER.readValue(String.valueOf(fmObj),
                new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    /** 把扩展属性里的 fieldMappings（List/Map/其他）转成 {@code Map<String,Object>} 列表。 */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> toFieldList(Object fieldMappings) {
        if (fieldMappings instanceof List<?> list) {
            List<Map<String, Object>> out = new ArrayList<>(list.size());
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    m.forEach((k, v) -> entry.put(String.valueOf(k), v));
                    out.add(entry);
                }
            }
            return out;
        }
        return new ArrayList<>();
    }

    /** 把扩展属性里的 propertyMappings（Map/其他）转成 {@code Map<String,Object>} 扁平态。 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> toPropMap(Object propertyMappings) {
        if (propertyMappings instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            m.forEach((k, v) -> out.put(String.valueOf(k), v));
            return out;
        }
        return new LinkedHashMap<>();
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER = new com.fasterxml.jackson.databind.ObjectMapper();
}
