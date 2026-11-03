package com.chinacreator.gzcm.engine.data.service;

import com.chinacreator.gzcm.runtime.access.connector.Connector;
import com.chinacreator.gzcm.runtime.access.connector.ConnectorFactory;
import com.chinacreator.gzcm.common.data.model.DataResource;
import com.chinacreator.gzcm.common.exception.NotFoundException;
import com.chinacreator.gzcm.engine.data.repository.DataResourceRepository;
import com.chinacreator.gzcm.engine.data.repository.DataSourceRepository;
import com.chinacreator.gzcm.engine.data.MetadataService;
import com.chinacreator.gzcm.engine.data.datasource.entity.DataSourceEntity;
import com.chinacreator.gzcm.runtime.core.cache.CacheJson;
import com.chinacreator.gzcm.runtime.core.cache.CacheKey;
import com.chinacreator.gzcm.runtime.core.cache.RuntimeCacheManager;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class MetadataCollectionService {

    private static final Logger log = LoggerFactory.getLogger(MetadataCollectionService.class);

    private final MetadataService metadataService;
    private final DataSourceRepository dsRepository;
    private final DataResourceRepository resourceRepository;
    private final ConnectorFactory connectorFactory;
    private final ObjectProvider<RuntimeCacheManager> cacheProvider;

    /** 14 类矩阵 #4「接入元数据」的缓存域（domain 单源；失效按本域收敛，不波及 data-engine 其他对象）。 */
    static final String CACHE_DOMAIN = "data-engine-metadata";
    static final String TYPE_RESOURCES = "metadata-resources";
    static final String TYPE_BULK = "metadata-bulk";
    static final String SCOPE_GLOBAL = "global";
    static final long CACHE_TTL_SECONDS = 300L;

    private static final TypeReference<List<DataResource>> RESOURCES_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<Map<String, Object>>> BULK_TYPE = new TypeReference<>() {
    };

    public MetadataCollectionService(MetadataService metadataService,
                                      DataSourceRepository dsRepository,
                                      DataResourceRepository resourceRepository,
                                      ConnectorFactory connectorFactory,
                                      ObjectProvider<RuntimeCacheManager> cacheProvider) {
        this.metadataService = metadataService;
        this.dsRepository = dsRepository;
        this.resourceRepository = resourceRepository;
        this.connectorFactory = connectorFactory;
        this.cacheProvider = cacheProvider;
    }

    public Map<String, Object> collect(String datasourceId) {
        DataSourceEntity ds = dsRepository.findById(datasourceId);
        if (ds == null) {
            throw NotFoundException.entity("数据源", datasourceId);
        }

        long start = System.currentTimeMillis();
        int count = metadataService.collectAll(datasourceId);
        long elapsed = System.currentTimeMillis() - start;

        resourcesEvict(datasourceId);
        bulkEvict();
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
                resourcesEvict(ds.getDatasourceId());
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

        resourcesCacheEvictAll();
        bulkEvict();

        long totalResources = results.stream()
                .filter(r -> Boolean.TRUE.equals(r.get("success")))
                .mapToInt(r -> (Integer) r.get("resourcesCollected"))
                .sum();
        log.info("Batch collection complete: {} datasources, {} total resources", allSources.size(), totalResources);
        return results;
    }

    public List<DataResource> getResources(String datasourceId) {
        CacheKey key = cacheKey(TYPE_RESOURCES, datasourceId);
        List<DataResource> cached = CacheJson.read(stringOf(key), RESOURCES_TYPE);
        if (cached != null) {
            return cached;
        }
        List<DataResource> loaded = resourceRepository.findByDatasource(datasourceId);
        if (loaded == null) {
            loaded = Collections.emptyList();
        }
        store(key, loaded);
        return loaded;
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
        CacheKey key = cacheKey(TYPE_BULK, "all");
        List<Map<String, Object>> cached = CacheJson.read(stringOf(key), BULK_TYPE);
        if (cached != null) {
            return cached;
        }
        List<Map<String, Object>> loaded = buildAllResources();
        store(key, loaded);
        return loaded;
    }

    private List<Map<String, Object>> buildAllResources() {
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
        resourcesEvict(datasourceId);
    }

    public void invalidateAllCaches() {
        resourcesCacheEvictAll();
        bulkEvict();
    }

    // ── Runtime 缓存接线（14 类矩阵 #4；#147AH）────────────────────────────
    // 权威源 = 业务 DB；缓存只是加速副本，关闭/未命中/任何异常都回源。

    private RuntimeCacheManager cache() {
        return cacheProvider == null ? null : cacheProvider.getIfAvailable();
    }

    private CacheKey cacheKey(String objectType, String objectId) {
        RuntimeCacheManager manager = cache();
        if (manager == null) {
            return null;
        }
        try {
            return manager.key(CACHE_DOMAIN, objectType, SCOPE_GLOBAL, objectId, "1");
        } catch (IllegalArgumentException e) {
            return null; // 键段非法（含冒号/空白）不缓存，直接回源
        }
    }

    private String stringOf(CacheKey key) {
        RuntimeCacheManager manager = cache();
        return key == null || manager == null ? null : manager.getString(key).orElse(null);
    }

    private void store(CacheKey key, Object value) {
        RuntimeCacheManager manager = cache();
        if (key == null || manager == null) {
            return;
        }
        manager.putString(key, CacheJson.write(value), CACHE_TTL_SECONDS);
    }

    private void resourcesEvict(String datasourceId) {
        RuntimeCacheManager manager = cache();
        if (manager != null && datasourceId != null) {
            manager.evictObject(CACHE_DOMAIN, TYPE_RESOURCES, datasourceId);
        }
    }

    private void bulkEvict() {
        RuntimeCacheManager manager = cache();
        if (manager != null) {
            manager.evictObject(CACHE_DOMAIN, TYPE_BULK, "all");
        }
    }

    private void resourcesCacheEvictAll() {
        RuntimeCacheManager manager = cache();
        if (manager != null) {
            manager.invalidateDomain(manager.env(), CACHE_DOMAIN);
        }
    }
}
