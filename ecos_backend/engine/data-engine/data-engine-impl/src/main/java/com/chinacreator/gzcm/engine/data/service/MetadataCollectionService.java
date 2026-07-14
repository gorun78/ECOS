package com.chinacreator.gzcm.engine.data.service;

import com.chinacreator.gzcm.datanet.connector.Connector;
import com.chinacreator.gzcm.datanet.connector.ConnectorFactory;
import com.chinacreator.gzcm.datanet.model.DataResource;
import com.chinacreator.gzcm.datanet.repository.DataResourceRepository;
import com.chinacreator.gzcm.datanet.repository.DataSourceRepository;
import com.chinacreator.gzcm.datanet.service.MetadataService;
import com.chinacreator.gzcm.runtime.core.datasource.entity.DataSourceEntity;
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
            throw new IllegalArgumentException("数据源不存在: " + datasourceId);
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
            throw new IllegalArgumentException("资源不存在: " + resourceId);
        }

        DataSourceEntity ds = dsRepository.findById(resource.getDatasourceId());
        if (ds == null) {
            throw new IllegalArgumentException("数据源不存在: " + resource.getDatasourceId());
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
