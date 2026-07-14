package com.chinacreator.gzcm.runtime.core.datasource.dao.impl;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.chinacreator.gzcm.runtime.core.database.ISystemDatabaseAccess;
import com.chinacreator.gzcm.runtime.core.datasource.entity.DataSourceEntity;

/**
 * 数据库版本的数据源DAO实现
 * 使用 ISystemDatabaseAccess 访问数据库持久化
 * 
 * @author CDRC Runtime Team
 */
public class DatabaseDataSourceDaoImpl {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseDataSourceDaoImpl.class);
    
    private static final String TABLE_NAME = "TD_DX_DATASOURCE";
    
    private final ISystemDatabaseAccess databaseAccess;
    
    public DatabaseDataSourceDaoImpl(ISystemDatabaseAccess databaseAccess) {
        this.databaseAccess = databaseAccess;
    }
    
    public String save(DataSourceEntity entity) throws ISystemDatabaseAccess.DatabaseAccessException {
        String id = entity.getDatasourceId();
        if (id == null || id.isEmpty()) {
            id = UUID.randomUUID().toString();
            entity.setDatasourceId(id);
        }
        
        Map<String, Object> data = convertToMap(entity);
        databaseAccess.insert(TABLE_NAME, data);
        logger.info("Saved datasource: id={}, name={}", id, entity.getDatasourceName());
        return id;
    }
    
    public void update(DataSourceEntity entity) throws ISystemDatabaseAccess.DatabaseAccessException {
        Map<String, Object> data = convertToMap(entity);
        data.put("UPDATE_TIME", new Timestamp(System.currentTimeMillis()));
        
        String sql = "UPDATE " + TABLE_NAME + " SET " +
            "DS_NAME = ?, DS_TYPE = ?, CONNECTION_CONFIG = ?, DESCRIPTION = ?, " +
            "STATUS = ?, IS_DEFAULT = ?, LAST_TEST_TIME = ?, LAST_TEST_RESULT = ?, " +
            "LAST_TEST_MESSAGE = ?, UPDATE_BY = ?, UPDATE_TIME = ?, TAGS = ?, REMARK = ? " +
            "WHERE DS_ID = ?";
        
        databaseAccess.executeUpdate(sql,
            entity.getDatasourceName(),
            entity.getDatasourceType(),
            entity.getConnectionConfig(),
            entity.getDescription(),
            entity.getStatus(),
            entity.getIsDefault(),
            entity.getLastTestTime(),
            entity.getLastTestResult(),
            entity.getLastTestMessage(),
            entity.getUpdateBy(),
            new Timestamp(System.currentTimeMillis()),
            entity.getTags(),
            entity.getRemark(),
            entity.getDatasourceId()
        );
        
        logger.info("Updated datasource: id={}", entity.getDatasourceId());
    }
    
    public void delete(String id) throws ISystemDatabaseAccess.DatabaseAccessException {
        String sql = "DELETE FROM " + TABLE_NAME + " WHERE DS_ID = ?";
        databaseAccess.executeUpdate(sql, id);
        logger.info("Deleted datasource: id={}", id);
    }
    
    public DataSourceEntity findById(String id) throws ISystemDatabaseAccess.DatabaseAccessException {
        String sql = "SELECT * FROM " + TABLE_NAME + " WHERE DS_ID = ?";
        List<Map<String, Object>> results = databaseAccess.executeQuery(sql, id);
        if (results.isEmpty()) {
            return null;
        }
        return convertToEntity(results.get(0));
    }
    
    public List<DataSourceEntity> findAll() throws ISystemDatabaseAccess.DatabaseAccessException {
        String sql = "SELECT * FROM " + TABLE_NAME + " ORDER BY DS_NAME";
        List<Map<String, Object>> results = databaseAccess.executeQuery(sql);
        List<DataSourceEntity> entities = new ArrayList<>();
        for (Map<String, Object> row : results) {
            entities.add(convertToEntity(row));
        }
        return entities;
    }
    
    public List<DataSourceEntity> findByType(String type) throws ISystemDatabaseAccess.DatabaseAccessException {
        String sql = "SELECT * FROM " + TABLE_NAME + " WHERE DS_TYPE = ? ORDER BY DS_NAME";
        List<Map<String, Object>> results = databaseAccess.executeQuery(sql, type);
        List<DataSourceEntity> entities = new ArrayList<>();
        for (Map<String, Object> row : results) {
            entities.add(convertToEntity(row));
        }
        return entities;
    }
    
    public List<DataSourceEntity> findByOrgId(String orgId) throws ISystemDatabaseAccess.DatabaseAccessException {
        String sql = "SELECT * FROM " + TABLE_NAME + " WHERE ORG_ID = ? ORDER BY DS_NAME";
        List<Map<String, Object>> results = databaseAccess.executeQuery(sql, orgId);
        List<DataSourceEntity> entities = new ArrayList<>();
        for (Map<String, Object> row : results) {
            entities.add(convertToEntity(row));
        }
        return entities;
    }
    
    public List<DataSourceEntity> findByNodeId(String nodeId) throws ISystemDatabaseAccess.DatabaseAccessException {
        String sql = "SELECT * FROM " + TABLE_NAME + " WHERE NODE_ID = ? ORDER BY DS_NAME";
        List<Map<String, Object>> results = databaseAccess.executeQuery(sql, nodeId);
        List<DataSourceEntity> entities = new ArrayList<>();
        for (Map<String, Object> row : results) {
            entities.add(convertToEntity(row));
        }
        return entities;
    }
    
    // ========== 转换方法 ==========
    
    private Map<String, Object> convertToMap(DataSourceEntity entity) {
        Map<String, Object> data = new HashMap<>();
        data.put("DS_ID", entity.getDatasourceId());
        data.put("DS_NAME", entity.getDatasourceName());
        data.put("DS_TYPE", entity.getDatasourceType());
        data.put("ORG_ID", entity.getOrgId());
        data.put("NODE_ID", entity.getNodeId());
        data.put("DESCRIPTION", entity.getDescription());
        data.put("CONNECTION_CONFIG", entity.getConnectionConfig());
        data.put("STATUS", entity.getStatus());
        data.put("IS_DEFAULT", entity.getIsDefault());
        data.put("LAST_TEST_TIME", entity.getLastTestTime());
        data.put("LAST_TEST_RESULT", entity.getLastTestResult());
        data.put("LAST_TEST_MESSAGE", entity.getLastTestMessage());
        data.put("CREATE_BY", entity.getCreateBy());
        data.put("CREATE_TIME", entity.getCreateTime());
        data.put("UPDATE_BY", entity.getUpdateBy());
        data.put("UPDATE_TIME", entity.getUpdateTime());
        data.put("TAGS", entity.getTags());
        data.put("REMARK", entity.getRemark());
        return data;
    }
    
    private DataSourceEntity convertToEntity(Map<String, Object> row) {
        DataSourceEntity entity = new DataSourceEntity();
        entity.setDatasourceId((String) row.get("DS_ID"));
        entity.setDatasourceName((String) row.get("DS_NAME"));
        entity.setDatasourceType((String) row.get("DS_TYPE"));
        entity.setOrgId((String) row.get("ORG_ID"));
        entity.setNodeId((String) row.get("NODE_ID"));
        entity.setDescription((String) row.get("DESCRIPTION"));
        entity.setConnectionConfig((String) row.get("CONNECTION_CONFIG"));
        entity.setStatus((String) row.get("STATUS"));
        entity.setIsDefault((String) row.get("IS_DEFAULT"));
        entity.setLastTestResult((String) row.get("LAST_TEST_RESULT"));
        entity.setLastTestMessage((String) row.get("LAST_TEST_MESSAGE"));
        entity.setCreateBy((String) row.get("CREATE_BY"));
        entity.setUpdateBy((String) row.get("UPDATE_BY"));
        entity.setTags((String) row.get("TAGS"));
        entity.setRemark((String) row.get("REMARK"));
        
        // 处理时间类型
        Object lastTestTimeObj = row.get("LAST_TEST_TIME");
        if (lastTestTimeObj instanceof Timestamp) {
            entity.setLastTestTime((Timestamp) lastTestTimeObj);
        } else if (lastTestTimeObj instanceof Date) {
            entity.setLastTestTime(new Timestamp(((Date) lastTestTimeObj).getTime()));
        }
        
        Object createTimeObj = row.get("CREATE_TIME");
        if (createTimeObj instanceof Timestamp) {
            entity.setCreateTime((Timestamp) createTimeObj);
        } else if (createTimeObj instanceof Date) {
            entity.setCreateTime(new Timestamp(((Date) createTimeObj).getTime()));
        }
        
        Object updateTimeObj = row.get("UPDATE_TIME");
        if (updateTimeObj instanceof Timestamp) {
            entity.setUpdateTime((Timestamp) updateTimeObj);
        } else if (updateTimeObj instanceof Date) {
            entity.setUpdateTime(new Timestamp(((Date) updateTimeObj).getTime()));
        }
        
        return entity;
    }
}
