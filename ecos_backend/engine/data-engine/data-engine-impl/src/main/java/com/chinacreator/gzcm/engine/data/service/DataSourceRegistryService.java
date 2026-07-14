package com.chinacreator.gzcm.engine.data.service;

import com.chinacreator.gzcm.datanet.connector.Connector;
import com.chinacreator.gzcm.datanet.connector.ConnectorFactory;
import com.chinacreator.gzcm.datanet.dto.DataSourceDTO;
import com.chinacreator.gzcm.datanet.service.DataSourceService;
import com.chinacreator.gzcm.runtime.core.datasource.entity.DataSourceEntity;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class DataSourceRegistryService {

    private static final String CACHE_KEY = "ALL";

    private final DataSourceService dataSourceService;
    private final ConnectorFactory connectorFactory;

    private final Cache<String, List<DataSourceEntity>> dsListCache = Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .maximumSize(2)
            .build();

    public DataSourceRegistryService(DataSourceService dataSourceService,
                                      ConnectorFactory connectorFactory) {
        this.dataSourceService = dataSourceService;
        this.connectorFactory = connectorFactory;
    }

    public DataSourceEntity register(DataSourceDTO dto) {
        DataSourceEntity result = dataSourceService.register(dto);
        dsListCache.invalidate(CACHE_KEY);
        return result;
    }

    public List<DataSourceEntity> listAll() {
        return dsListCache.get(CACHE_KEY, key -> dataSourceService.listAll());
    }

    public DataSourceEntity getById(String id) {
        return dataSourceService.getById(id);
    }

    public boolean testConnection(String datasourceId) {
        return dataSourceService.testConnection(datasourceId);
    }

    public Map<String, Object> testConnectionRaw(DataSourceDTO dto) {
        if (dto.getDatasourceType() == null || dto.getDatasourceType().isBlank()) {
            throw new IllegalArgumentException("数据源类型不能为空");
        }
        if (dto.getConnectionConfig() == null || dto.getConnectionConfig().isBlank()) {
            throw new IllegalArgumentException("连接配置不能为空");
        }
        try {
            Connector connector = connectorFactory.getConnector(dto.getDatasourceType());
            boolean ok = connector.testConnection(dto.getConnectionConfig());
            return Map.of("success", ok, "message", ok ? "连接成功" : "连接失败");
        } catch (Exception e) {
            return Map.of("success", false, "message", "连接测试异常: " + e.getMessage());
        }
    }

    public void remove(String id) {
        dataSourceService.remove(id);
        dsListCache.invalidate(CACHE_KEY);
    }
}
