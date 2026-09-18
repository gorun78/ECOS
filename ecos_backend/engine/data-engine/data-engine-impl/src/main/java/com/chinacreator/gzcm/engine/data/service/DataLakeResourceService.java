package com.chinacreator.gzcm.engine.data.service;

import com.chinacreator.gzcm.common.data.model.DataResource;
import com.chinacreator.gzcm.engine.data.model.DataLayer;
import com.chinacreator.gzcm.engine.data.repository.DataResourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 数据湖分层标记服务 — 把管道产出登记 / 标记到 td_data_resource 的 layer + zone。
 * <p>
 * 分层口径（详见 .trae/rules/数据湖存储分层规范.md）：
 * <ul>
 *   <li>{@code layer=RAW} + {@code zone=STRUCTURED}：MinIO 近源层结构化对象</li>
 *   <li>{@code layer=CURATED}：DW 层库表</li>
 * </ul>
 * <p>
 * 设计约束：
 * <ul>
 *   <li>MinIO 对象不参与 PG 元数据采集，故近源对象需显式 insert 登记，否则分层视图恒为空。</li>
 *   <li>标记失败仅告警，不使管道执行失败（与旧链 PipelineExecutionEngine.updateResourceLayer 同策略）。</li>
 * </ul>
 *
 * @author DataBridge Datanet Team
 */
@Service
public class DataLakeResourceService {

    private static final Logger log = LoggerFactory.getLogger(DataLakeResourceService.class);

    /** 近源层结构化区标识（与 V133 DDL 注释一致） */
    private static final String ZONE_STRUCTURED = "STRUCTURED";

    /** 资源类型：数据湖对象（区别于元数据采集产出的 TABLE/VIEW） */
    private static final String TYPE_LAKE_OBJECT = "LAKE_OBJECT";

    /** 近源对象缺失上游数据源时的兜底 datasource_id（td_data_resource.datasource_id 为 NOT NULL） */
    private static final String FALLBACK_DATASOURCE_ID = "data-lake";

    private final DataResourceRepository resourceRepository;

    public DataLakeResourceService(DataResourceRepository resourceRepository) {
        this.resourceRepository = resourceRepository;
    }

    /**
     * 标记近源层结构化对象（layer=RAW, zone=STRUCTURED）。
     * <p>已登记则更新，未登记则新增（MinIO 对象不参与元数据采集，必须显式登记）。
     *
     * @param table        表名（资源名）
     * @param objectName   MinIO 对象 key（作为 source_path 定位）
     * @param datasourceId 上游数据源 ID，可为空（空则落兜底值）
     * @param columnCount  列数，可为 null
     * @param rowCount     本次写入行数，可为 null
     */
    public void markNearSourceStructured(String table, String objectName, String datasourceId,
                                          Integer columnCount, Long rowCount) {
        if (objectName == null || objectName.isEmpty()) {
            return;
        }
        try {
            DataResource existing = resourceRepository.findBySourcePath(objectName);
            if (existing != null) {
                existing.setLayer(DataLayer.RAW.name());
                existing.setZone(ZONE_STRUCTURED);
                existing.setFieldCount(columnCount != null ? columnCount : existing.getFieldCount());
                existing.setRecordCount(rowCount != null ? rowCount : existing.getRecordCount());
                resourceRepository.update(existing);
                log.debug("近源层对象标记更新: object={}, layer=RAW, zone={}", objectName, ZONE_STRUCTURED);
                return;
            }

            DataResource created = new DataResource();
            created.setResourceId("lakeres-" + UUID.randomUUID().toString().replace("-", "").substring(0, 24));
            created.setResourceName(table != null && !table.isEmpty() ? table : objectName);
            created.setResourceType(TYPE_LAKE_OBJECT);
            created.setDatasourceId(datasourceId != null && !datasourceId.isEmpty()
                    ? datasourceId : FALLBACK_DATASOURCE_ID);
            created.setSourcePath(objectName);
            created.setStatus("ACTIVE");
            created.setFieldCount(columnCount != null ? columnCount : 0);
            created.setRecordCount(rowCount != null ? rowCount : 0L);
            created.setLayer(DataLayer.RAW.name());
            created.setZone(ZONE_STRUCTURED);
            resourceRepository.insert(created);
            log.info("近源层对象登记: object={}, layer=RAW, zone={}", objectName, ZONE_STRUCTURED);
        } catch (Exception e) {
            // 分层标记失败不影响管道结果
            log.warn("近源层对象标记失败（忽略）: object={}, err={}", objectName, e.getMessage());
        }
    }

    /**
     * 标记 DW 层库表（layer=CURATED, zone=NULL）。
     * <p>仅更新已登记资源；未登记则跳过（DW 层表由元数据采集负责登记）。
     *
     * @param tableName 表名（与 source_path 一致）
     */
    public void markCurated(String tableName) {
        if (tableName == null || tableName.isEmpty()) {
            return;
        }
        try {
            DataResource existing = resourceRepository.findBySourcePath(tableName);
            if (existing == null) {
                log.debug("DW 层标记跳过（资源未登记）: table={}", tableName);
                return;
            }
            existing.setLayer(DataLayer.CURATED.name());
            resourceRepository.update(existing);
            log.debug("DW 层标记更新: table={}, layer=CURATED", tableName);
        } catch (Exception e) {
            // 分层标记失败不影响管道结果
            log.warn("DW 层标记失败（忽略）: table={}, err={}", tableName, e.getMessage());
        }
    }
}
