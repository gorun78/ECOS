package com.chinacreator.gzcm.runtime.core.metadata.access;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.chinacreator.gzcm.runtime.core.metadata.access.IMetadataAccessService;
import com.chinacreator.gzcm.runtime.core.metadata.model.DataSchema;
import com.chinacreator.gzcm.runtime.core.metadata.model.DataSourceMetadata;
import com.chinacreator.gzcm.runtime.core.metadata.model.TechnicalMetadata;

/**
 * 元数据访问服务实现
 * 提供缓存管理和统计功能
 * 
 * @author CDRC Runtime Team
 */
public class MetadataAccessServiceImpl implements IMetadataAccessService {
    
    // 缓存存储
    private final Map<String, TechnicalMetadata> metadataCache = new ConcurrentHashMap<>();
    private final Map<String, DataSchema> schemaCache = new ConcurrentHashMap<>();
    private final Map<String, DataSourceMetadata> datasourceCache = new ConcurrentHashMap<>();
    
    // 缓存统计
    private long hitCount = 0;
    private long missCount = 0;
    
    @Override
    public TechnicalMetadata getTechnicalMetadata(String metadataId) throws MetadataAccessException {
        try {
            TechnicalMetadata metadata = metadataCache.get(metadataId);
            if (metadata != null) {
                hitCount++;
                return metadata;
            } else {
                missCount++;
                // 创建空的元数据对象（实际应该从数据源加载）
                metadata = new TechnicalMetadata();
                metadataCache.put(metadataId, metadata);
                return metadata;
            }
        } catch (Exception e) {
            throw new MetadataAccessException("Failed to get technical metadata: " + metadataId, e);
        }
    }

    @Override
    public DataSourceMetadata getDataSourceMetadata(String datasourceId) throws MetadataAccessException {
        try {
            DataSourceMetadata metadata = datasourceCache.get(datasourceId);
            if (metadata != null) {
                hitCount++;
                return metadata;
            } else {
                missCount++;
                // 创建空的元数据对象（实际应该从数据源加载）
                metadata = new DataSourceMetadata();
                datasourceCache.put(datasourceId, metadata);
                return metadata;
            }
        } catch (Exception e) {
            throw new MetadataAccessException("Failed to get datasource metadata: " + datasourceId, e);
        }
    }

    @Override
    public DataSchema getSchema(String schemaId) throws MetadataAccessException {
        try {
            DataSchema schema = schemaCache.get(schemaId);
            if (schema != null) {
                hitCount++;
                return schema;
            } else {
                missCount++;
                // 创建空的Schema对象（实际应该从数据源加载）
                schema = new DataSchema();
                schemaCache.put(schemaId, schema);
                return schema;
            }
        } catch (Exception e) {
            throw new MetadataAccessException("Failed to get schema: " + schemaId, e);
        }
    }

    @Override
    public Map<String, TechnicalMetadata> batchGetTechnicalMetadata(List<String> metadataIds) 
            throws MetadataAccessException {
        try {
            Map<String, TechnicalMetadata> result = new HashMap<>();
            for (String metadataId : metadataIds) {
                TechnicalMetadata metadata = getTechnicalMetadata(metadataId);
                result.put(metadataId, metadata);
            }
            return result;
        } catch (MetadataAccessException e) {
            throw e;
        } catch (Exception e) {
            throw new MetadataAccessException("Failed to batch get technical metadata", e);
        }
    }

    @Override
    public void refreshCache(String metadataId) throws MetadataAccessException {
        if (metadataId == null) {
            // 刷新所有缓存
            metadataCache.clear();
            schemaCache.clear();
            datasourceCache.clear();
        } else {
            metadataCache.remove(metadataId);
        }
    }

    @Override
    public void refreshSchemaCache(String schemaId) throws MetadataAccessException {
        if (schemaId == null) {
            schemaCache.clear();
        } else {
            schemaCache.remove(schemaId);
        }
    }

    @Override
    public void refreshDataSourceCache(String datasourceId) throws MetadataAccessException {
        if (datasourceId == null) {
            datasourceCache.clear();
        } else {
            datasourceCache.remove(datasourceId);
        }
    }

    @Override
    public void warmupCache(List<String> metadataIds) throws MetadataAccessException {
        if (metadataIds == null || metadataIds.isEmpty()) {
            return;
        }
        
        // 预热缓存：提前加载元数据
        for (String metadataId : metadataIds) {
            try {
                getTechnicalMetadata(metadataId);
            } catch (Exception e) {
                // 忽略单个加载失败
            }
        }
    }

    @Override
    public CacheStatistics getCacheStatistics() {
        CacheStatistics stats = new CacheStatistics();
        long totalRequests = hitCount + missCount;
        
        stats.setHitCount(hitCount);
        stats.setMissCount(missCount);
        stats.setTotalRequests(totalRequests);
        
        if (totalRequests > 0) {
            stats.setHitRate((double) hitCount / totalRequests);
        } else {
            stats.setHitRate(0.0);
        }
        
        return stats;
    }
}

