package com.chinacreator.gzcm.datanet.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.datanet.connector.Connector;
import com.chinacreator.gzcm.datanet.connector.ConnectorFactory;
import com.chinacreator.gzcm.datanet.dto.DataSourceDTO;
import com.chinacreator.gzcm.datanet.service.DataSourceService;
import com.chinacreator.gzcm.runtime.core.datasource.entity.DataSourceEntity;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 数据源管理 Controller。
 *
 * @author DataBridge Datanet Team
 */
@RestController
@RequestMapping("/datanet/datasource")
public class DataSourceController {

    private final DataSourceService dataSourceService;
    private final ConnectorFactory connectorFactory;

    /** 数据源列表缓存 — 5分钟TTL，注册/删除后失效 */
    private final Cache<String, List<DataSourceEntity>> dsListCache = Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .maximumSize(2)
            .build();
    private static final String CACHE_KEY = "ALL";

    public DataSourceController(DataSourceService dataSourceService,
                                 ConnectorFactory connectorFactory) {
        this.dataSourceService = dataSourceService;
        this.connectorFactory = connectorFactory;
    }

    @PostMapping
    public ApiResponse<DataSourceEntity> register(@RequestBody DataSourceDTO dto) {
        DataSourceEntity result = dataSourceService.register(dto);
        dsListCache.invalidate(CACHE_KEY);
        return ApiResponse.success(result);
    }

    @GetMapping
    public ApiResponse<List<DataSourceEntity>> list() {
        List<DataSourceEntity> list = dsListCache.get(CACHE_KEY,
                key -> dataSourceService.listAll());
        return ApiResponse.success(list);
    }

    @GetMapping("/{id}")
    public ApiResponse<DataSourceEntity> getById(@PathVariable String id) {
        DataSourceEntity entity = dataSourceService.getById(id);
        if (entity == null) {
            return ApiResponse.error(404, "数据源不存在: " + id);
        }
        return ApiResponse.success(entity);
    }

    @PostMapping("/{id}/test")
    public ApiResponse<Map<String, Object>> testConnection(@PathVariable String id) {
        boolean ok = dataSourceService.testConnection(id);
        return ApiResponse.success(Map.of("success", ok, "datasourceId", id));
    }

    /** 前置连接测试 — 不保存数据源，只用连接配置测试连通性 */
    @PostMapping("/test")
    public ApiResponse<Map<String, Object>> testConnectionRaw(@RequestBody DataSourceDTO dto) {
        if (dto.getDatasourceType() == null || dto.getDatasourceType().isBlank()) {
            return ApiResponse.error(400, "数据源类型不能为空");
        }
        if (dto.getConnectionConfig() == null || dto.getConnectionConfig().isBlank()) {
            return ApiResponse.error(400, "连接配置不能为空");
        }
        try {
            Connector connector = connectorFactory.getConnector(dto.getDatasourceType());
            boolean ok = connector.testConnection(dto.getConnectionConfig());
            return ApiResponse.success(Map.of(
                "success", ok,
                "message", ok ? "连接成功" : "连接失败"
            ));
        } catch (Exception e) {
            return ApiResponse.success(Map.of(
                "success", false,
                "message", "连接测试异常: " + e.getMessage()
            ));
        }
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> remove(@PathVariable String id) {
        dataSourceService.remove(id);
        dsListCache.invalidate(CACHE_KEY);
        return ApiResponse.success(null);
    }
}
