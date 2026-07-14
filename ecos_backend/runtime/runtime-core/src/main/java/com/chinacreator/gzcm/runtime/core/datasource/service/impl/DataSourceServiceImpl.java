package com.chinacreator.gzcm.runtime.core.datasource.service.impl;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.chinacreator.gzcm.runtime.core.dataaccess.storage.IStorageAdapter;
import com.chinacreator.gzcm.runtime.core.dataaccess.storage.adapter.jdbc.JdbcAdapterFactory;
import com.chinacreator.gzcm.runtime.core.dataaccess.storage.enums.StorageType;
import com.chinacreator.gzcm.runtime.core.dataaccess.storage.model.StorageConfig;
import com.chinacreator.gzcm.runtime.core.datasource.entity.DataSourceEntity;
import com.chinacreator.gzcm.runtime.core.datasource.IDataSourceService;
import com.chinacreator.gzcm.runtime.core.datasource.dao.impl.DataSourceDaoImpl;
import com.chinacreator.gzcm.runtime.core.i18n.I18nUtils;
import com.chinacreator.gzcm.runtime.core.i18n.LocaleResolver;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 数据源服务实现类
 * 提供数据源的CRUD、连接测试、推送等功能
 * 
 * @author CDRC Runtime Team
 */
public class DataSourceServiceImpl implements IDataSourceService {
    
    private static final Logger logger = LoggerFactory.getLogger(DataSourceServiceImpl.class);
    
    private final DataSourceDaoImpl dao = new DataSourceDaoImpl();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JdbcAdapterFactory adapterFactory = new JdbcAdapterFactory();
    
    @Override
    public String createDataSource(DataSourceEntity datasource) throws DataSourceException {
        try {
            // 验证数据源信息
            validateDataSource(datasource);
            
            // 设置创建时间
            datasource.setCreateTime(new Timestamp(System.currentTimeMillis()));
            datasource.setStatus("ACTIVE");
            
            // 保存数据源
            String id = dao.save(datasource);
            
            logger.info(I18nUtils.getMessage("datasource.create.success", 
                    LocaleResolver.getDefaultLocaleCode(), datasource.getDatasourceName()));
            
            return id;
            
        } catch (Exception e) {
            String errorMsg = I18nUtils.getErrorMessage("operation.failed", 
                    LocaleResolver.getDefaultLocaleCode());
            logger.error(I18nUtils.getMessage("datasource.create.failed", 
                    LocaleResolver.getDefaultLocaleCode()), e);
            throw new DataSourceException(errorMsg, e);
        }
    }
    
    @Override
    public void updateDataSource(DataSourceEntity datasource) throws DataSourceException {
        try {
            // 验证数据源是否存在
            DataSourceEntity existing = dao.findById(datasource.getDatasourceId());
            if (existing == null) {
                String errorMsg = I18nUtils.getErrorMessage("data.not.found", 
                        LocaleResolver.getDefaultLocaleCode());
                throw new DataSourceException(errorMsg);
            }
            
            // 验证数据源信息
            validateDataSource(datasource);
            
            // 设置更新时间
            datasource.setUpdateTime(new Timestamp(System.currentTimeMillis()));
            
            // 更新数据源
            dao.update(datasource);
            
            logger.info(I18nUtils.getMessage("datasource.update.success", 
                    LocaleResolver.getDefaultLocaleCode(), datasource.getDatasourceName()));
            
        } catch (DataSourceException e) {
            throw e;
        } catch (Exception e) {
            String errorMsg = I18nUtils.getErrorMessage("operation.failed", 
                    LocaleResolver.getDefaultLocaleCode());
            logger.error(I18nUtils.getMessage("datasource.update.failed", 
                    LocaleResolver.getDefaultLocaleCode()), e);
            throw new DataSourceException(errorMsg, e);
        }
    }
    
    @Override
    public void deleteDataSource(String datasourceId) throws DataSourceException {
        try {
            DataSourceEntity existing = dao.findById(datasourceId);
            if (existing == null) {
                String errorMsg = I18nUtils.getErrorMessage("data.not.found", 
                        LocaleResolver.getDefaultLocaleCode());
                throw new DataSourceException(errorMsg);
            }
            
            dao.delete(datasourceId);
            
            logger.info(I18nUtils.getMessage("datasource.delete.success", 
                    LocaleResolver.getDefaultLocaleCode(), datasourceId));
            
        } catch (DataSourceException e) {
            throw e;
        } catch (Exception e) {
            String errorMsg = I18nUtils.getErrorMessage("operation.failed", 
                    LocaleResolver.getDefaultLocaleCode());
            logger.error(I18nUtils.getMessage("datasource.delete.failed", 
                    LocaleResolver.getDefaultLocaleCode()), e);
            throw new DataSourceException(errorMsg, e);
        }
    }
    
    @Override
    public DataSourceEntity getDataSourceById(String datasourceId) throws DataSourceException {
        DataSourceEntity entity = dao.findById(datasourceId);
        if (entity == null) {
            String errorMsg = I18nUtils.getErrorMessage("data.not.found", 
                    LocaleResolver.getDefaultLocaleCode());
            throw new DataSourceException(errorMsg);
        }
        return entity;
    }
    
    @Override
    public List<DataSourceEntity> queryDataSources(String orgId, String nodeId, 
            String datasourceType, String status) throws DataSourceException {
        try {
            return dao.findAll().stream()
                    .filter(ds -> orgId == null || orgId.equals(ds.getOrgId()))
                    .filter(ds -> nodeId == null || nodeId.equals(ds.getNodeId()))
                    .filter(ds -> datasourceType == null || datasourceType.equals(ds.getDatasourceType()))
                    .filter(ds -> status == null || status.equals(ds.getStatus()))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            String errorMsg = I18nUtils.getErrorMessage("query.failed", 
                    LocaleResolver.getDefaultLocaleCode());
            logger.error(errorMsg, e);
            throw new DataSourceException(errorMsg, e);
        }
    }
    
    @Override
    public boolean testDataSource(String datasourceId) throws DataSourceException {
        DataSourceEntity datasource = getDataSourceById(datasourceId);
        
        // 更新测试状态
        datasource.setStatus("TESTING");
        datasource.setLastTestTime(new Timestamp(System.currentTimeMillis()));
        dao.update(datasource);
        
        IStorageAdapter adapter = null;
        try {
            // 构建StorageConfig
            StorageConfig config = buildStorageConfig(datasource);
            
            // 创建适配器并测试连接
            StorageType storageType = StorageType.fromString(datasource.getDatasourceType());
            if (storageType == null) {
                throw new DataSourceException(I18nUtils.getErrorMessage("invalid.config", 
                        LocaleResolver.getDefaultLocaleCode()));
            }
            
            adapter = adapterFactory.createAdapter(storageType, config);
            boolean success = adapter.testConnection();
            
            // 更新测试结果
            datasource.setStatus(success ? "ACTIVE" : "ERROR");
            datasource.setLastTestResult(success ? "SUCCESS" : "FAILED");
            datasource.setLastTestMessage(success ? 
                    I18nUtils.getMessage("datasource.test.success", 
                            LocaleResolver.getDefaultLocaleCode()) :
                    I18nUtils.getErrorMessage("connection.failed", 
                            LocaleResolver.getDefaultLocaleCode()));
            datasource.setLastTestTime(new Timestamp(System.currentTimeMillis()));
            dao.update(datasource);
            
            logger.info(I18nUtils.getMessage("datasource.test.success", 
                    LocaleResolver.getDefaultLocaleCode(), datasource.getDatasourceName()));
            
            return success;
            
        } catch (Exception e) {
            // 更新测试结果
            datasource.setStatus("ERROR");
            datasource.setLastTestResult("FAILED");
            datasource.setLastTestMessage(e.getMessage());
            datasource.setLastTestTime(new Timestamp(System.currentTimeMillis()));
            dao.update(datasource);
            
            logger.error(I18nUtils.getMessage("datasource.test.failed", 
                    LocaleResolver.getDefaultLocaleCode()), e);
            
            throw new DataSourceException(I18nUtils.getErrorMessage("connection.failed", 
                    LocaleResolver.getDefaultLocaleCode()), e);
            
        } finally {
            // 关闭适配器连接
            if (adapter != null) {
                try {
                    adapter.disconnect();
                } catch (Exception e) {
                    logger.warn("Failed to disconnect adapter", e);
                }
            }
        }
    }
    
    @Override
    public void pushDataSourceToNode(String datasourceId, String nodeId) throws DataSourceException {
        // TODO: 实现数据源推送到节点的逻辑
        // 这需要与节点通信模块集成
        logger.debug("Pushing datasource {} to node {}", datasourceId, nodeId);
    }
    
    @Override
    public Map<String, Object> batchPushDataSourcesToNode(List<String> datasourceIds, String nodeId)
            throws DataSourceException {
        // TODO: 实现批量推送逻辑
        Map<String, Object> result = new HashMap<>();
        result.put("success", 0);
        result.put("failed", 0);
        return result;
    }
    
    @Override
    public Map<String, Object> pushDataSourceToOrgNodes(String datasourceId, String orgId)
            throws DataSourceException {
        // TODO: 实现按机构推送逻辑
        Map<String, Object> result = new HashMap<>();
        result.put("success", 0);
        result.put("failed", 0);
        return result;
    }
    
    @Override
    public List<DataSourceEntity> getNodeDataSources(String nodeId) throws DataSourceException {
        return queryDataSources(null, nodeId, null, null);
    }
    
    @Override
    public void setDefaultDataSource(String datasourceId, String nodeId) throws DataSourceException {
        try {
            DataSourceEntity datasource = getDataSourceById(datasourceId);
            
            // 取消其他默认数据源
            List<DataSourceEntity> nodeDataSources = getNodeDataSources(nodeId);
            for (DataSourceEntity ds : nodeDataSources) {
                if ("Y".equals(ds.getIsDefault())) {
                    ds.setIsDefault("N");
                    dao.update(ds);
                }
            }
            
            // 设置新的默认数据源
            datasource.setIsDefault("Y");
            dao.update(datasource);
            
        } catch (Exception e) {
            String errorMsg = I18nUtils.getErrorMessage("operation.failed", 
                    LocaleResolver.getDefaultLocaleCode());
            logger.error(errorMsg, e);
            throw new DataSourceException(errorMsg, e);
        }
    }
    
    // ========== 辅助方法 ==========
    
    /**
     * 验证数据源信息
     */
    private void validateDataSource(DataSourceEntity datasource) throws DataSourceException {
        if (datasource.getDatasourceName() == null || datasource.getDatasourceName().trim().isEmpty()) {
            String errorMsg = I18nUtils.getErrorMessage("validation.failed", 
                    LocaleResolver.getDefaultLocaleCode());
            throw new DataSourceException(errorMsg);
        }
        
        if (datasource.getDatasourceType() == null || datasource.getDatasourceType().trim().isEmpty()) {
            String errorMsg = I18nUtils.getErrorMessage("invalid.config", 
                    LocaleResolver.getDefaultLocaleCode());
            throw new DataSourceException(errorMsg);
        }
        
        if (datasource.getConnectionConfig() == null || datasource.getConnectionConfig().trim().isEmpty()) {
            String errorMsg = I18nUtils.getErrorMessage("invalid.config", 
                    LocaleResolver.getDefaultLocaleCode());
            throw new DataSourceException(errorMsg);
        }
    }
    
    /**
     * 从DataSourceEntity构建StorageConfig
     */
    private StorageConfig buildStorageConfig(DataSourceEntity datasource) throws Exception {
        StorageConfig config = new StorageConfig();
        
        try {
            // 解析连接配置JSON
            Map<String, Object> connConfig = objectMapper.readValue(
                    datasource.getConnectionConfig(), 
                    Map.class
            );
            
            // 设置基本属性
            config.setStorageType(datasource.getDatasourceType());
            config.setHost((String) connConfig.get("host"));
            config.setPort(connConfig.get("port") != null ? 
                    Integer.valueOf(connConfig.get("port").toString()) : null);
            config.setDatabase((String) connConfig.get("database"));
            config.setUsername((String) connConfig.get("username"));
            config.setPassword((String) connConfig.get("password"));
            
            // 设置连接字符串（如果存在）
            if (connConfig.containsKey("url")) {
                config.setConnectionString((String) connConfig.get("url"));
            }
            
            // 设置扩展属性
            Map<String, Object> properties = new HashMap<>();
            for (Map.Entry<String, Object> entry : connConfig.entrySet()) {
                String key = entry.getKey();
                if (!key.equals("host") && !key.equals("port") && 
                    !key.equals("database") && !key.equals("username") && 
                    !key.equals("password") && !key.equals("url")) {
                    properties.put(key, entry.getValue());
                }
            }
            if (!properties.isEmpty()) {
                config.setProperties(properties);
            }
            
        } catch (Exception e) {
            String errorMsg = I18nUtils.getErrorMessage("invalid.config", 
                    LocaleResolver.getDefaultLocaleCode());
            throw new DataSourceException(errorMsg, e);
        }
        
        return config;
    }
}
