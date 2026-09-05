package com.chinacreator.gzcm.engine.data.service;

import com.chinacreator.gzcm.common.data.model.DataResource;
import com.chinacreator.gzcm.engine.data.repository.DataResourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * PMO-37 表清单同步服务 —— 数据源元数据采集落库的唯一入口。
 * <p>
 * Upsert 策略：按 (datasource_id, source_path) 唯一定位既有行，存在则 UPDATE
 * (名称/类型/行数/last_sync_time)，不存在则 INSERT。幂等可重跑。
 *
 * @author DataBridge Datanet Team
 */
@Service
public class ResourceSyncService {

    private static final Logger log = LoggerFactory.getLogger(ResourceSyncService.class);

    private final DataResourceRepository resourceRepository;

    public ResourceSyncService(DataResourceRepository resourceRepository) {
        this.resourceRepository = resourceRepository;
    }

    /**
     * 同步单表元数据。
     *
     * @param datasourceId 数据源 ID
     * @param resource     Connector 发现的资源
     * @param rowCnt       行数：null=OFF 不统计(保持 -1)；>=0 真实/估算；-1=未采集
     * @return 落库后的资源 ID
     */
    public String syncResource(String datasourceId, DataResource resource, Long rowCnt) {
        // 以 datasource + sourcePath 定位既有行
        String key = resource.getSourcePath() != null && !resource.getSourcePath().isBlank()
                ? resource.getSourcePath() : resource.getResourceName();

        DataResource existing = findExisting(datasourceId, key);
        LocalDateTime now = LocalDateTime.now();

        if (existing == null) {
            if (resource.getResourceId() == null || resource.getResourceId().isBlank()) {
                resource.setResourceId(UUID.randomUUID().toString().replace("-", ""));
            }
            resource.setDatasourceId(datasourceId);
            if (resource.getResourceType() == null) {
                resource.setResourceType("TABLE");
            }
            if (resource.getStatus() == null) {
                resource.setStatus("ACTIVE");
            }
            resource.setRecordCount(rowCnt == null ? -1L : rowCnt);
            resource.setLastSyncTime(now);
            if (resource.getCreateTime() == null) {
                resource.setCreateTime(now);
            }
            resource.setUpdateTime(now);
            resourceRepository.insert(resource);
        } else {
            existing.setResourceName(resource.getResourceName());
            if (resource.getResourceType() != null) {
                existing.setResourceType(resource.getResourceType());
            }
            if (resource.getSourcePath() != null) {
                existing.setSourcePath(resource.getSourcePath());
            }
            if (resource.getDescription() != null) {
                existing.setDescription(resource.getDescription());
            }
            if (rowCnt != null) {
                existing.setRecordCount(rowCnt);
            }
            existing.setLastSyncTime(now);
            existing.setUpdateTime(now);
            resourceRepository.update(existing);
        }
        return resource.getResourceId();
    }

    /**
     * 清掉数据源下不属于本次采集的孤儿行（可选，采集器自传全量表名集合）。
     */
    public void removeStale(String datasourceId, java.util.Set<String> currentSourcePaths) {
        List<DataResource> current = resourceRepository.findByDatasource(datasourceId);
        for (DataResource r : current) {
            String key = r.getSourcePath() != null && !r.getSourcePath().isBlank()
                    ? r.getSourcePath() : r.getResourceName();
            if (!currentSourcePaths.contains(key)) {
                resourceRepository.deleteById(r.getResourceId());
                log.info("同步: 删除孤儿资源 {} (datasource={})", key, datasourceId);
            }
        }
    }

    private DataResource findExisting(String datasourceId, String sourcePathKey) {
        List<DataResource> rows = resourceRepository.findByDatasource(datasourceId);
        for (DataResource r : rows) {
            String k = r.getSourcePath() != null && !r.getSourcePath().isBlank()
                    ? r.getSourcePath() : r.getResourceName();
            if (k.equalsIgnoreCase(sourcePathKey)) {
                return r;
            }
        }
        return null;
    }
}
