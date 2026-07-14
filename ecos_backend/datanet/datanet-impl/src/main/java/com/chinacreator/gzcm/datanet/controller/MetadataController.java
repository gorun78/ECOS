package com.chinacreator.gzcm.datanet.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.datanet.connector.Connector;
import com.chinacreator.gzcm.datanet.connector.ConnectorFactory;
import com.chinacreator.gzcm.datanet.model.CatalogItem;
import com.chinacreator.gzcm.datanet.model.DataField;
import com.chinacreator.gzcm.datanet.model.DataResource;
import com.chinacreator.gzcm.datanet.repository.DataResourceRepository;
import com.chinacreator.gzcm.datanet.repository.DataSourceRepository;
import com.chinacreator.gzcm.datanet.service.CatalogService;
import com.chinacreator.gzcm.datanet.service.MetadataService;
import com.chinacreator.gzcm.runtime.core.datasource.entity.DataSourceEntity;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 元数据采集 Controller — 触发采集、查询字段和资源。
 *
 * @author DataBridge Datanet Team
 */
@RestController
@RequestMapping("/datanet/metadata")
public class MetadataController {

    private static final Logger log = LoggerFactory.getLogger(MetadataController.class);

    private final MetadataService metadataService;
    private final DataSourceRepository dsRepository;
    private final CatalogService catalogService;
    private final DataResourceRepository resourceRepository;
    private final ConnectorFactory connectorFactory;

    /** 数据目录资源缓存 — 5分钟TTL，采集后自动失效 */
    private final Cache<String, List<DataResource>> resourcesCache = Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .maximumSize(50)
            .build();

    /** 批量资源缓存（全量 JOIN） */
    private final Cache<String, List<Map<String, Object>>> bulkResourcesCache = Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .maximumSize(2)
            .build();

    public MetadataController(MetadataService metadataService,
                               DataSourceRepository dsRepository,
                               CatalogService catalogService,
                               DataResourceRepository resourceRepository,
                               ConnectorFactory connectorFactory) {
        this.metadataService = metadataService;
        this.dsRepository = dsRepository;
        this.catalogService = catalogService;
        this.resourceRepository = resourceRepository;
        this.connectorFactory = connectorFactory;
    }

    /**
     * 采集指定数据源的所有元数据（表/视图 + 字段）。
     */
    @PostMapping("/collect/{datasourceId}")
    public ApiResponse<Map<String, Object>> collect(@PathVariable String datasourceId) {
        DataSourceEntity ds = dsRepository.findById(datasourceId);
        if (ds == null) {
            return ApiResponse.error(404, "数据源不存在: " + datasourceId);
        }

        long start = System.currentTimeMillis();
        int count;
        try {
            count = metadataService.collectAll(datasourceId);
        } catch (Exception e) {
            log.error("Metadata collection failed: {}", e.getMessage(), e);
            return ApiResponse.error(500, "采集失败: " + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
        long elapsed = System.currentTimeMillis() - start;

        // 采集后清缓存，下次查询走DB拿最新数据
        resourcesCache.invalidate(datasourceId);
        bulkResourcesCache.invalidate("ALL");
        log.info("Cache invalidated for datasource={} after collection", datasourceId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("datasourceId", datasourceId);
        result.put("datasourceName", ds.getDatasourceName());
        result.put("resourcesCollected", count);
        result.put("elapsedMs", elapsed);

        log.info("Metadata collection for '{}': {} resources in {}ms",
                ds.getDatasourceName(), count, elapsed);
        return ApiResponse.success(result);
    }

    /**
     * 采集所有数据源的元数据。
     */
    @PostMapping("/collect")
    public ApiResponse<List<Map<String, Object>>> collectAll() {
        List<DataSourceEntity> allSources = dsRepository.findAll();
        if (allSources.isEmpty()) {
            return ApiResponse.success(Collections.emptyList());
        }

        List<Map<String, Object>> results = new ArrayList<>();
        for (DataSourceEntity ds : allSources) {
            long start = System.currentTimeMillis();
            try {
                int count = metadataService.collectAll(ds.getDatasourceId());
                long elapsed = System.currentTimeMillis() - start;
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("datasourceId", ds.getDatasourceId());
                r.put("datasourceName", ds.getDatasourceName());
                r.put("resourcesCollected", count);
                r.put("elapsedMs", elapsed);
                r.put("success", true);
                results.add(r);
                resourcesCache.invalidate(ds.getDatasourceId());
                log.info("Collected '{}': {} resources in {}ms", ds.getDatasourceName(), count, elapsed);
            } catch (Exception e) {
                log.error("Failed to collect '{}': {}", ds.getDatasourceName(), e.getMessage());
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("datasourceId", ds.getDatasourceId());
                r.put("datasourceName", ds.getDatasourceName());
                r.put("error", e.getMessage());
                r.put("success", false);
                results.add(r);
            }
        }

        // 全量采集后清全部缓存
        resourcesCache.invalidateAll();
        bulkResourcesCache.invalidateAll();

        long totalResources = results.stream()
                .filter(r -> Boolean.TRUE.equals(r.get("success")))
                .mapToInt(r -> (Integer) r.get("resourcesCollected"))
                .sum();
        log.info("Batch collection complete: {} datasources, {} total resources",
                allSources.size(), totalResources);

        return ApiResponse.success(results);
    }

    /**
     * 获取某个资源的所有字段。
     */
    @GetMapping("/fields/{resourceId}")
    public ApiResponse<List<DataField>> getFields(@PathVariable String resourceId) {
        List<DataField> fields = metadataService.getFields(resourceId);
        return ApiResponse.success(fields);
    }

    /**
     * 获取某个数据源下已采集的所有资源（带 Caffeine 缓存）。
     */
    @GetMapping("/resources/{datasourceId}")
    public ApiResponse<List<DataResource>> getResources(@PathVariable String datasourceId) {
        List<DataResource> resources = resourcesCache.get(datasourceId,
                id -> resourceRepository.findByDatasource(id));
        return ApiResponse.success(resources);
    }

    /**
     * 批量获取所有资源的列表（含数据源名称、一次 JOIN 返回）。
     * <p>
     * 替代前端多次调用 /resources/{datasourceId} 的 N+1 模式。
     * 返回结构: [{resourceId, resourceName, resourceType, sourcePath, fieldCount,
     *            datasourceId, datasourceName, datasourceType}, ...]
     * </p>
     */
    @GetMapping("/resources/all")
    public ApiResponse<List<Map<String, Object>>> getAllResources() {
        List<Map<String, Object>> all = bulkResourcesCache.get("ALL", key -> {
            List<DataSourceEntity> sources = dsRepository.findAll();
            List<Map<String, Object>> result = new ArrayList<>();
            for (DataSourceEntity ds : sources) {
                List<DataResource> resources = resourceRepository.findByDatasource(ds.getDatasourceId());
                for (DataResource r : resources) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("resourceId", r.getResourceId());
                    item.put("resourceName", r.getResourceName());
                    item.put("resourceType", r.getResourceType());
                    item.put("sourcePath", r.getSourcePath());
                    item.put("fieldCount", r.getFieldCount());
                    item.put("datasourceId", ds.getDatasourceId());
                    item.put("datasourceName", ds.getDatasourceName());
                    item.put("datasourceType", ds.getDatasourceType());
                    result.add(item);
                }
            }
            log.info("Bulk resources cache built: {} resources from {} datasources",
                    result.size(), sources.size());
            return result;
        });
        return ApiResponse.success(all);
    }

    /**
     * 数据预览 — 查询资源（表/视图）的实际数据行。
     * GET /datanet/metadata/preview/{resourceId}?limit=50
     */
    @GetMapping("/preview/{resourceId}")
    public ApiResponse<Map<String, Object>> preview(@PathVariable String resourceId,
                                                     @RequestParam(defaultValue = "50") int limit) {
        // 1. 查资源 → 获取表名
        DataResource resource = resourceRepository.findById(resourceId);
        if (resource == null) {
            return ApiResponse.error(404, "资源不存在: " + resourceId);
        }

        // 2. 查数据源 → 获取连接配置
        DataSourceEntity ds = dsRepository.findById(resource.getDatasourceId());
        if (ds == null) {
            return ApiResponse.error(404, "数据源不存在: " + resource.getDatasourceId());
        }

        // 3. 通过 Connector 查询实际数据
        try {
            Connector connector = connectorFactory.getConnector(ds.getDatasourceType());
            List<Map<String, Object>> rows = connector.queryPreview(
                    ds.getConnectionConfig(),
                    resource.getResourceName(),  // 表名
                    Math.min(limit, 200)         // 最多200行
            );

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("resourceId", resourceId);
            result.put("resourceName", resource.getResourceName());
            result.put("rows", rows);
            result.put("rowCount", rows.size());
            result.put("columns", rows.isEmpty() ? 0 : rows.get(0).size());
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("Preview failed for {}: {}", resourceId, e.getMessage());
            return ApiResponse.error(500, "数据查询失败: " + e.getMessage());
        }
    }
}
