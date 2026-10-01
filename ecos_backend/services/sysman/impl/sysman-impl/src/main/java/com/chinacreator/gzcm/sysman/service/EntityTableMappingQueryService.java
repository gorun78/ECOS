package com.chinacreator.gzcm.sysman.service;

import com.chinacreator.gzcm.sysman.dao.EntityTableMappingQueryDao;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 实体-表映射查询服务 — 从 EntityTableMappingController 下沉的 JdbcTemplate 访问。
 * <p>铁律 §2.5：SQL 与数据访问下沉到 DAO（{@link EntityTableMappingQueryDao}），本类保留非 DB 逻辑（JSONB 序列化）。
 */
@Service
public class EntityTableMappingQueryService {

    private final EntityTableMappingQueryDao dao;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public EntityTableMappingQueryService(EntityTableMappingQueryDao dao) {
        this.dao = dao;
    }

    /** 列出指定实体的所有映射，或列出全部映射 */
    public List<Map<String, Object>> listMappings(String entityCode) {
        return dao.listMappings(entityCode);
    }

    /** 检查映射是否已存在：返回满足条件的行数 */
    public Integer countExistingMapping(String entityCode, String datasourceId, String resourceName) {
        return dao.countExistingMapping(entityCode, datasourceId, resourceName);
    }

    /** 序列化 fieldMappings 为 JSON 字符串 */
    public String serializeFieldMappings(Object fieldMappingsRaw) throws Exception {
        if (fieldMappingsRaw == null) {
            return null;
        }
        if (fieldMappingsRaw instanceof String) {
            return (String) fieldMappingsRaw;
        }
        return objectMapper.writeValueAsString(fieldMappingsRaw);
    }

    /** 插入新的实体-表映射 */
    public void insertMapping(String id, String entityCode, String entityName, String domainCode,
                              String datasourceId, String resourceName, String tableSchema,
                              String fieldMappingsJson) {
        dao.insertMapping(id, entityCode, entityName, domainCode, datasourceId, resourceName,
                tableSchema, fieldMappingsJson);
    }

    /** 查询刚插入的映射行 */
    public Map<String, Object> getMappingById(String id) {
        return dao.getMappingById(id);
    }

    /** 检查映射是否存在：返回行数 */
    public Integer countMappingById(String id) {
        return dao.countMappingById(id);
    }

    /** 删除指定ID的映射 */
    public void deleteMappingById(String id) {
        dao.deleteMappingById(id);
    }

    /** 查询所有 ACTIVE 数据资源（含数据源名称） */
    public List<Map<String, Object>> queryAllActiveDataResources() {
        return dao.queryAllActiveDataResources();
    }

    /** 查询指定实体已映射的资源 */
    public List<Map<String, Object>> queryMappedResources(String entityCode) {
        return dao.queryMappedResources(entityCode);
    }

    // ── PMO-E2 通用委托方法（供 Controller 调用）──
    public List<Map<String, Object>> queryForList(String sql, Object... args) {
        return dao.queryForList(sql, args);
    }
    public Map<String, Object> queryForMap(String sql, Object... args) {
        return dao.queryForMap(sql, args);
    }
    public <T> T queryForObject(String sql, Class<T> type, Object... args) {
        return dao.queryForObject(sql, type, args);
    }
    public int update(String sql, Object... args) {
        return dao.update(sql, args);
    }
}
