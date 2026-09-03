package com.chinacreator.gzcm.engine.data.service;

import com.chinacreator.gzcm.runtime.access.connector.Connector;
import com.chinacreator.gzcm.runtime.access.connector.ConnectorFactory;
import com.chinacreator.gzcm.common.data.model.DataResource;
import com.chinacreator.gzcm.common.exception.NotFoundException;
import com.chinacreator.gzcm.engine.data.repository.DataResourceRepository;
import com.chinacreator.gzcm.engine.data.repository.DataSourceRepository;
import com.chinacreator.gzcm.engine.data.MetadataService;
import com.chinacreator.gzcm.engine.data.datasource.entity.DataSourceEntity;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class MetadataCollectionService {

    private static final Logger log = LoggerFactory.getLogger(MetadataCollectionService.class);

    private final MetadataService metadataService;
    private final DataSourceRepository dsRepository;
    private final DataResourceRepository resourceRepository;
    private final ConnectorFactory connectorFactory;

    private final Cache<String, List<DataResource>> resourcesCache = Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .maximumSize(50)
            .build();

    private final Cache<String, List<Map<String, Object>>> bulkResourcesCache = Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .maximumSize(2)
            .build();

    public MetadataCollectionService(MetadataService metadataService,
                                      DataSourceRepository dsRepository,
                                      DataResourceRepository resourceRepository,
                                      ConnectorFactory connectorFactory) {
        this.metadataService = metadataService;
        this.dsRepository = dsRepository;
        this.resourceRepository = resourceRepository;
        this.connectorFactory = connectorFactory;
    }

    public Map<String, Object> collect(String datasourceId) {
        DataSourceEntity ds = dsRepository.findById(datasourceId);
        if (ds == null) {
            throw NotFoundException.entity("数据源", datasourceId);
        }

        long start = System.currentTimeMillis();
        int count = metadataService.collectAll(datasourceId);
        long elapsed = System.currentTimeMillis() - start;

        resourcesCache.invalidate(datasourceId);
        bulkResourcesCache.invalidate("ALL");
        log.info("Cache invalidated for datasource={} after collection", datasourceId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("datasourceId", datasourceId);
        result.put("datasourceName", ds.getDatasourceName());
        result.put("resourcesCollected", count);
        result.put("elapsedMs", elapsed);
        log.info("Metadata collection for '{}': {} resources in {}ms", ds.getDatasourceName(), count, elapsed);
        return result;
    }

    public List<Map<String, Object>> collectAll() {
        List<DataSourceEntity> allSources = dsRepository.findAll();
        if (allSources.isEmpty()) {
            return Collections.emptyList();
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

        resourcesCache.invalidateAll();
        bulkResourcesCache.invalidateAll();

        long totalResources = results.stream()
                .filter(r -> Boolean.TRUE.equals(r.get("success")))
                .mapToInt(r -> (Integer) r.get("resourcesCollected"))
                .sum();
        log.info("Batch collection complete: {} datasources, {} total resources", allSources.size(), totalResources);
        return results;
    }

    public List<DataResource> getResources(String datasourceId) {
        return resourcesCache.get(datasourceId, id -> resourceRepository.findByDatasource(id));
    }

    /**
     * PMO-37 数据表目录分页查询（元数据缓存分页，DB 不分页）：
     * 返回 [items(当前页), total(总条数)]。列含 record_count 三态语义。
     *
     * @param pageNum  从 1 起
     * @param pageSize 每页条数（上限 100）
     */
    public List<Object> getResourcePages(String datasourceId, int pageNum, int pageSize) {
        int pn = Math.max(1, pageNum);
        int ps = Math.max(1, Math.min(pageSize, 100));
        List<DataResource> all = getResources(datasourceId);
        int total = all == null ? 0 : all.size();
        int from = Math.min((pn - 1) * ps, total);
        int to = Math.min(from + ps, total);

        List<Map<String, Object>> page = new ArrayList<>();
        if (all != null) {
            for (DataResource r : all.subList(from, to)) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("resourceId", r.getResourceId());
                item.put("resourceName", r.getResourceName());
                item.put("resourceType", r.getResourceType());
                item.put("sourcePath", r.getSourcePath());
                item.put("description", r.getDescription());
                item.put("fieldCount", r.getFieldCount());
                // 行数三态：-1 未采集 / 0 空表 / >=0 真实或估算
                Long rc = r.getRecordCount();
                item.put("recordCount", rc == null ? -1L : rc);
                item.put("lastSyncTime", r.getLastSyncTime());
                page.add(item);
            }
        }
        List<Object> out = new ArrayList<>(2);
        out.add(page);
        out.add(total);
        return out;
    }

    public List<Map<String, Object>> getAllResources() {
        return bulkResourcesCache.get("ALL", key -> {
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
            log.info("Bulk resources cache built: {} resources from {} datasources", result.size(), sources.size());
            return result;
        });
    }

    public Map<String, Object> preview(String resourceId, int limit) {
        DataResource resource = resourceRepository.findById(resourceId);
        if (resource == null) {
            throw NotFoundException.entity("资源", resourceId);
        }

        DataSourceEntity ds = dsRepository.findById(resource.getDatasourceId());
        if (ds == null) {
            throw NotFoundException.entity("数据源", resource.getDatasourceId());
        }

        Connector connector = connectorFactory.getConnector(ds.getDatasourceType());
        List<Map<String, Object>> rows = connector.queryPreview(
                ds.getConnectionConfig(),
                resource.getResourceName(),
                Math.min(limit, 200)
        );

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("resourceId", resourceId);
        result.put("resourceName", resource.getResourceName());
        result.put("rows", rows);
        result.put("rowCount", rows.size());
        result.put("columns", rows.isEmpty() ? 0 : rows.get(0).size());
        return result;
    }

    public void invalidateCache(String datasourceId) {
        resourcesCache.invalidate(datasourceId);
    }

    public void invalidateAllCaches() {
        resourcesCache.invalidateAll();
        bulkResourcesCache.invalidateAll();
    }
}
