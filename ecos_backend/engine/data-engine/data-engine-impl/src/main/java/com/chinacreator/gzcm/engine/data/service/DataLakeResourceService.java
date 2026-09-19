package com.chinacreator.gzcm.engine.data.service;

import com.chinacreator.gzcm.common.data.model.DataResource;
import com.chinacreator.gzcm.common.exception.ValidationException;
import com.chinacreator.gzcm.engine.data.dto.DataResourceRegisterDTO;
import com.chinacreator.gzcm.engine.data.dto.DataResourceVO;
import com.chinacreator.gzcm.engine.data.dto.UnstructuredRegisterDTO;
import com.chinacreator.gzcm.engine.data.model.DataLayer;
import com.chinacreator.gzcm.engine.data.repository.DataResourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Set;
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

    /** 近源层非结构化区标识（方案 §4 F1：原文按 docId 寻址） */
    private static final String ZONE_UNSTRUCTURED = "UNSTRUCTURED";

    /** 资源类型：数据湖对象（区别于元数据采集产出的 TABLE/VIEW） */
    private static final String TYPE_LAKE_OBJECT = "LAKE_OBJECT";

    /** 非结构化对象 key 前缀（数据湖存储分层规范 §三） */
    private static final String UNSTRUCTURED_KEY_PREFIX = "raw/unstructured/";

    /** 允许登记的资源类型（分层规范 §五） */
    private static final Set<String> ALLOWED_RESOURCE_TYPES =
            Set.of("TABLE", "VIEW", "API", "FILE", "LAKE_OBJECT");

    /** source_path 列长度上限（td_data_resource.source_path VARCHAR(512)） */
    private static final int MAX_SOURCE_PATH = 512;

    /** 默认资源状态 */
    private static final String STATUS_ACTIVE = "ACTIVE";

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

    // ═══════════════ B5-1：非结构化登记端点（D5 生产者侧） ═══════════════

    /**
     * 登记非结构化原文对象（方案 §4 F1 / §5.3 / SOP-2）。
     *
     * <p>按分层规范 §三 组装对象 key {@code raw/unstructured/{source}/{docId}/{originalFileName}}，
     * 并按 §五 登记 {@code td_data_resource}（{@code layer=RAW}、{@code zone=UNSTRUCTURED}、
     * {@code resource_type=LAKE_OBJECT}）。本方法只做元数据登记，不写 MinIO 二进制。
     *
     * <p>与管道内「尽力而为」的分层标记不同，登记端点自身失败必须上抛（登记即职责）。
     *
     * @param req 登记入参（source/docId/originalFileName 必填）
     * @return 登记结果（含是否新建）
     * @throws ValidationException 入参非法或违反分层合法性矩阵
     */
    public DataResourceVO registerUnstructured(UnstructuredRegisterDTO req) {
        if (req == null) {
            throw new ValidationException("body", "请求体不能为空");
        }
        String source = requireNonBlank("source", req.getSource(), 128);
        String docId = requireNonBlank("docId", req.getDocId(), 128);
        // 文件名去路径，防止越出 {docId}/ 目录（对象 key 末段只保留文件名）
        String fileName = requireNonBlank("originalFileName", req.getOriginalFileName(), 512)
                .replace('\\', '/');
        int slash = fileName.lastIndexOf('/');
        if (slash >= 0) {
            fileName = fileName.substring(slash + 1);
        }
        if (fileName.isBlank()) {
            throw new ValidationException("originalFileName", "文件名不能为空");
        }
        String objectKey = UNSTRUCTURED_KEY_PREFIX + source + "/" + docId + "/" + fileName;
        if (objectKey.length() > MAX_SOURCE_PATH) {
            throw new ValidationException("sourcePath", "组装的对象 key 超长(>" + MAX_SOURCE_PATH + "): " + objectKey);
        }
        // 合法性矩阵：RAW 才允许 UNSTRUCTURED
        validateLayerZone(DataLayer.RAW.name(), ZONE_UNSTRUCTURED);

        String resourceName = req.getResourceName() != null && !req.getResourceName().isBlank()
                ? req.getResourceName().trim() : fileName;
        return persist("lakeres-", resourceName, TYPE_LAKE_OBJECT, req.getDatasourceId(), objectKey,
                DataLayer.RAW.name(), ZONE_UNSTRUCTURED,
                "非结构化原文: " + source + "/" + docId, null, STATUS_ACTIVE);
    }

    /**
     * 通用数据资源登记（方案 §5.3「知识→数据 登记数据资源」）。
     *
     * <p>A3 过渡态下知识工作台据此把「解析文本」登记为 {@code layer=CURATED} 资源；
     * 亦可供其他工作台登记任意分层资源。强制校验 {@code layer} 为合法 DataLayer 枚举、
     * {@code zone} 符合 §五 合法性矩阵（非 RAW 层 zone 必须为空）。
     *
     * @param req 登记入参
     * @return 登记结果（含是否新建）
     * @throws ValidationException 入参非法或违反分层合法性矩阵
     */
    public DataResourceVO registerResource(DataResourceRegisterDTO req) {
        if (req == null) {
            throw new ValidationException("body", "请求体不能为空");
        }
        String resourceName = requireNonBlank("resourceName", req.getResourceName(), 256);
        String resourceType = requireNonBlank("resourceType", req.getResourceType(), 32).toUpperCase();
        if (!ALLOWED_RESOURCE_TYPES.contains(resourceType)) {
            throw new ValidationException("resourceType",
                    "非法资源类型: " + resourceType + "（合法值: TABLE/VIEW/API/FILE/LAKE_OBJECT）");
        }
        String sourcePath = requireNonBlank("sourcePath", req.getSourcePath(), MAX_SOURCE_PATH);
        String layer = requireNonBlank("layer", req.getLayer(), 32).toUpperCase();
        String zone = req.getZone();
        validateLayerZone(layer, zone);

        String status = req.getStatus() != null && !req.getStatus().isBlank()
                ? req.getStatus().trim() : STATUS_ACTIVE;
        return persist("res-", resourceName, resourceType, req.getDatasourceId(), sourcePath,
                layer, zone, req.getDescription(), req.getTags(), status);
    }

    // ═══════════════ 内部辅助 ═══════════════

    /**
     * 按 source_path 幂等登记：已存在则更新（不产生重复行），否则新增。
     */
    private DataResourceVO persist(String idPrefix, String resourceName, String resourceType,
                                   String datasourceId, String sourcePath, String layer, String zone,
                                   String description, String tags, String status) {
        DataResource existing = resourceRepository.findBySourcePath(sourcePath);
        if (existing != null) {
            existing.setResourceName(resourceName);
            existing.setResourceType(resourceType);
            existing.setStatus(status);
            existing.setLayer(layer);
            existing.setZone(zone);
            if (description != null) {
                existing.setDescription(description);
            }
            if (tags != null) {
                existing.setTags(tags);
            }
            resourceRepository.update(existing);
            if (zone == null) {
                // update 的 <if test="zone != null"> 无法清空旧 zone → 显式置空以严格符合合法性矩阵
                resourceRepository.updateLayerZone(existing.getResourceId(), layer, null);
            }
            log.info("数据资源登记（幂等更新）: resourceId={}, sourcePath={}, layer={}, zone={}",
                    existing.getResourceId(), sourcePath, layer, zone);
            return toVo(existing, false);
        }

        DataResource created = new DataResource();
        created.setResourceId(idPrefix + UUID.randomUUID().toString().replace("-", "").substring(0, 24));
        created.setResourceName(resourceName);
        created.setResourceType(resourceType);
        created.setDatasourceId(datasourceId != null && !datasourceId.isBlank() ? datasourceId : FALLBACK_DATASOURCE_ID);
        created.setSourcePath(sourcePath);
        created.setDescription(description);
        created.setTags(tags);
        created.setStatus(status);
        created.setFieldCount(0);
        created.setRecordCount(0L);
        created.setLayer(layer);
        created.setZone(zone);
        resourceRepository.insert(created);
        log.info("数据资源登记（新增）: resourceId={}, sourcePath={}, layer={}, zone={}",
                created.getResourceId(), sourcePath, layer, zone);
        return toVo(created, true);
    }

    /**
     * 分层合法性矩阵校验（分层规范 §五，强制）。
     * <p>{@code layer=RAW} ⟺ {@code zone ∈ {STRUCTURED, UNSTRUCTURED}}；其他层 {@code zone} 必须为空。
     */
    private void validateLayerZone(String layer, String zone) {
        DataLayer parsed;
        try {
            parsed = DataLayer.valueOf(layer);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("layer",
                    "非法分层名: " + layer + "（合法值: SOURCE/RAW/CURATED/SEMANTIC/APPLICATION）");
        }
        if (parsed == DataLayer.RAW) {
            if (zone == null || zone.isBlank()) {
                throw new ValidationException("zone", "layer=RAW 时 zone 必填（STRUCTURED/UNSTRUCTURED）");
            }
            if (!ZONE_STRUCTURED.equals(zone) && !ZONE_UNSTRUCTURED.equals(zone)) {
                throw new ValidationException("zone", "非法近源区: " + zone + "（合法值: STRUCTURED/UNSTRUCTURED）");
            }
            return;
        }
        if (zone != null && !zone.isBlank()) {
            throw new ValidationException("zone",
                    "layer=" + layer + " 时 zone 必须为空（合法性矩阵：仅 RAW 允许 zone）");
        }
    }

    /** 校验必填文本非空且不超长，返回 trim 后的值。 */
    private String requireNonBlank(String field, String value, int maxLen) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(field, "不能为空");
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLen) {
            throw new ValidationException(field, "长度超过上限 " + maxLen);
        }
        return trimmed;
    }

    /** 实体 → VO。 */
    private DataResourceVO toVo(DataResource entity, boolean created) {
        DataResourceVO vo = new DataResourceVO();
        vo.setResourceId(entity.getResourceId());
        vo.setResourceName(entity.getResourceName());
        vo.setResourceType(entity.getResourceType());
        vo.setDatasourceId(entity.getDatasourceId());
        vo.setSourcePath(entity.getSourcePath());
        vo.setStatus(entity.getStatus());
        vo.setLayer(entity.getLayer());
        vo.setZone(entity.getZone());
        vo.setCreated(created);
        return vo;
    }
}
