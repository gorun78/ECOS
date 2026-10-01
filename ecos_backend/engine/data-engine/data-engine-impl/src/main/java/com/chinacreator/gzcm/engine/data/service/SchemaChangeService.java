package com.chinacreator.gzcm.engine.data.service;

import com.chinacreator.gzcm.engine.data.dao.SchemaChangeDao;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Schema 变更检测 Service 层。
 * <p>
 * 查询 schema_changes 表，支持按 acknowledged 过滤和确认操作。
 * 实际 JDBC 访问由 {@link SchemaChangeDao} 承担。
 * </p>
 */
@Service
public class SchemaChangeService {

    private final SchemaChangeDao schemaChangeDao;

    public SchemaChangeService(SchemaChangeDao schemaChangeDao) {
        this.schemaChangeDao = schemaChangeDao;
    }

    /**
     * 查询 schema 变更列表。默认只返回未确认的变更。
     *
     * @param acknowledged 是否已确认
     */
    public List<Map<String, Object>> listChanges(boolean acknowledged) {
        return schemaChangeDao.listChanges(acknowledged);
    }

    /**
     * 确认一条 schema 变更记录。
     *
     * @param id 变更记录 ID
     * @return 受影响行数
     */
    public int acknowledge(Long id) {
        return schemaChangeDao.acknowledge(id);
    }
}
