package com.chinacreator.gzcm.engine.ontology.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.chinacreator.gzcm.common.exception.DataBridgeException;
import com.chinacreator.gzcm.engine.ontology.client.DataNetResourceClient;
import com.chinacreator.gzcm.engine.ontology.dto.FieldMappingItemDTO;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyMappingIssueVO;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyMappingSaveDTO;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyMappingValidateDTO;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyMappingValidationVO;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyMappingVO;

/**
 * 本体映射持久化服务 — 封装 ecos_entity_table_mapping 表的 SQL 操作。
 *
 * <p>由 {@link com.chinacreator.gzcm.engine.ontology.controller.OntologyMappingController}
 * 调用，Controller 层不再直接持有 {@link JdbcTemplate}。</p>
 *
 * <p>PMO-B2 T2：新增 {@link #validateMappings(OntologyMappingValidateDTO)} 实现方案 §2.3
 * 「C4 映射有效性」——DW 表/列存在性与类型兼容校验，DW 元数据一律经
 * {@link DataNetResourceClient} 走 data-engine REST（架构铁律 §2.1 引擎间只调 API、
 * §3.3 不跨引擎操作对方表）。</p>
 */
@Service
public class OntologyMappingService {

    private static final Logger log = LoggerFactory.getLogger(OntologyMappingService.class);

    /** C4 校验的 DW 层（数据湖分层规范 §一：CURATED = DW 层）。 */
    private static final String DW_LAYER = "CURATED";

    /** C4 失败拒绝码（方案 §2.3：拒绝该实体实例抽取）。 */
    private static final String REJECT_CODE_INVALID_MAPPING = "INVALID_MAPPING";
    /** 问题码：目标 DW 表不存在于 CURATED 层。 */
    private static final String CODE_TABLE_NOT_FOUND = "TABLE_NOT_FOUND";
    /** 问题码：映射列不存在于 DW 表。 */
    private static final String CODE_COLUMN_NOT_FOUND = "COLUMN_NOT_FOUND";
    /** 问题码：DW 列类型与本体属性类型不兼容。 */
    private static final String CODE_TYPE_INCOMPATIBLE = "TYPE_INCOMPATIBLE";
    /** 问题码：data-engine 元数据不可用或未采集（默认拒绝，不降级放行）。 */
    private static final String CODE_METADATA_UNAVAILABLE = "METADATA_UNAVAILABLE";
    /** 问题码：指定的映射主键不存在。 */
    private static final String CODE_MAPPING_NOT_FOUND = "MAPPING_NOT_FOUND";

    private final JdbcTemplate jdbc;

    /** data-engine REST 客户端（DW 层资源 + 列定义，禁止直查 td_data_* 跨引擎表）。 */
    private final DataNetResourceClient dataNetClient;

    public OntologyMappingService(JdbcTemplate jdbc, DataNetResourceClient dataNetClient) {
        this.jdbc = jdbc;
        this.dataNetClient = dataNetClient;
    }

    /**
     * 查询映射列表（可按 objectId / sourceType 过滤）。返回原始行。
     */
    public List<Map<String, Object>> listMappings(String objectId, String sourceType) {
        StringBuilder sql = new StringBuilder("SELECT * FROM ecos_entity_table_mapping WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (objectId != null && !objectId.isBlank()) {
            sql.append(" AND entity_code=?");
            params.add(objectId);
        }
        if (sourceType != null && !sourceType.isBlank()) {
            sql.append(" AND domain_code=?");
            params.add(sourceType);
        }
        sql.append(" ORDER BY created_at DESC");

        return jdbc.queryForList(sql.toString(), params.toArray());
    }

    /**
     * 强类型 VO 版（T16-2）：内层把 row 转成 {@link OntologyMappingVO}。
     * <p>旧 {@link #listMappings} 签名保留（C1 mock / 内部消费者兼容）。
     */
    public List<OntologyMappingVO> listMappingsVO(String objectId, String sourceType) {
        List<Map<String, Object>> rows = listMappings(objectId, sourceType);
        List<OntologyMappingVO> result = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            result.add(toVO(row));
        }
        return result;
    }

    /**
     * 查询单个映射详情，不存在返回 null。
     */
    public Map<String, Object> findMappingById(String id) {
        try {
            return jdbc.queryForMap(
                    "SELECT * FROM ecos_entity_table_mapping WHERE id=?", id);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    /**
     * 强类型 VO 版（T16-2）；不存在返回 null。
     */
    public OntologyMappingVO findMappingByIdVO(String id) {
        Map<String, Object> row = findMappingById(id);
        return row == null ? null : toVO(row);
    }

    /**
     * 插入新映射记录。
     */
    public void insertMapping(String id, String objectId, String sourceName,
                              String sourceType, String sourceUri, String fieldMappingsJson) {
        jdbc.update(
                "INSERT INTO ecos_entity_table_mapping (id, entity_code, entity_name, domain_code, datasource_id, resource_name, table_schema, field_mappings, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, NOW(), NOW())",
                id, objectId, sourceName, sourceType, "",
                sourceName, sourceUri, fieldMappingsJson);
    }

    /**
     * 强类型 VO 版（T16-2）。行为与 {@link #insertMapping} 一致，但消费
     * {@link OntologyMappingSaveDTO} 强类型入参，便于审计与可读性。
     */
    public void insertMappingVO(String id, OntologyMappingSaveDTO dto) {
        jdbc.update(
                "INSERT INTO ecos_entity_table_mapping (id, entity_code, entity_name, domain_code, datasource_id, resource_name, table_schema, field_mappings, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, NOW(), NOW())",
                id, dto.getObjectId(), dto.getSourceName(), dto.getSourceType(), "",
                dto.getSourceName(), dto.getSourceUri(), dto.getFieldMappingsJson());
    }

    /**
     * 更新映射记录（拼接 SQL），返回更新后的行，不存在返回 null。
     */
    public Map<String, Object> updateMapping(String id, StringBuilder sql, List<Object> params) {
        jdbc.update(sql.toString(), params.toArray());
        return jdbc.queryForMap(
                "SELECT * FROM ecos_entity_table_mapping WHERE id=?", id);
    }

    /**
     * 强类型 VO 版（T16-2）。
     */
    public OntologyMappingVO updateMappingVO(String id, StringBuilder sql, List<Object> params) {
        jdbc.update(sql.toString(), params.toArray());
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT * FROM ecos_entity_table_mapping WHERE id=?", id);
        return toVO(row);
    }

    /**
     * 删除映射记录。返回被删除的行（用于日志），不存在返回 null。
     */
    public Map<String, Object> deleteMapping(String id) {
        Map<String, Object> existing;
        try {
            existing = jdbc.queryForMap("SELECT * FROM ecos_entity_table_mapping WHERE id=?", id);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
        jdbc.update("DELETE FROM ecos_entity_table_mapping WHERE id=?", id);
        return existing;
    }

    // ═══════════════ C4 映射一致性校验（PMO-B2 T2） ═══════════════

    /**
     * 映射一致性校验（方案 §2.3 C4 映射有效性）。
     *
     * <p>校验映射指向的 DW 表 / 列是否存在、类型是否兼容。DW 元数据经
     * {@link DataNetResourceClient} 走 data-engine REST 获取（不直查 {@code td_data_*}）。
     *
     * <p>失败处置：不抛 500，返回 {@code valid=false} + {@code issues[]} +
     * {@code rejectCode=INVALID_MAPPING}，供 kb 侧拒绝该实体实例抽取；
     * data-engine 不可用时按「默认拒绝」处理（{@code METADATA_UNAVAILABLE}），不降级放行。
     *
     * @param dto 校验范围（mappingId / 内联 datasetId+fieldMappings / entityCode / 全量）
     * @return 结构化校验报告
     */
    public OntologyMappingValidationVO validateMappings(OntologyMappingValidateDTO dto) {
        OntologyMappingValidationVO report = new OntologyMappingValidationVO();

        // 1. 归一化校验目标（解析范围 + 形态统一）
        List<MappingTarget> targets = resolveTargets(dto, report);
        if (!report.getIssues().isEmpty()) {
            report.setValid(false);
            report.setRejectCode(REJECT_CODE_INVALID_MAPPING);
            return report;
        }
        report.setCheckedCount(targets.size());
        if (targets.isEmpty()) {
            report.setValid(true);
            return report;
        }

        // 2. DW 层资源清单：一次拉取，按 resource_id / resource_name 建索引
        Map<String, Map<String, Object>> resourceById = new LinkedHashMap<>();
        Map<String, Map<String, Object>> resourceByName = new LinkedHashMap<>();
        List<Map<String, Object>> curatedResources;
        try {
            curatedResources = dataNetClient.listResourcesByLayer(DW_LAYER);
        } catch (DataBridgeException e) {
            log.error("C4 校验中止：data-engine CURATED 资源清单不可用", e);
            report.getIssues().add(new OntologyMappingIssueVO(null, null, null, CODE_METADATA_UNAVAILABLE,
                    "data-engine 元数据不可用，无法校验 DW 表/列存在性: " + e.getMessage()));
            report.setValid(false);
            report.setRejectCode(REJECT_CODE_INVALID_MAPPING);
            return report;
        }
        for (Map<String, Object> row : curatedResources) {
            String resourceId = asText(row.get("resource_id"));
            String resourceName = asText(row.get("resource_name"));
            if (!resourceId.isEmpty()) {
                resourceById.putIfAbsent(resourceId, row);
            }
            if (!resourceName.isEmpty()) {
                resourceByName.putIfAbsent(resourceName.toLowerCase(Locale.ROOT), row);
            }
        }

        // 3. 逐条校验（列定义按 resourceId 缓存，避免 N+1）
        Map<String, List<Map<String, Object>>> fieldsCache = new LinkedHashMap<>();
        for (MappingTarget target : targets) {
            validateOneMapping(target, resourceById, resourceByName, fieldsCache, report);
        }

        report.setValid(report.getIssues().isEmpty());
        if (!report.isValid()) {
            report.setRejectCode(REJECT_CODE_INVALID_MAPPING);
        }
        log.info("C4 映射校验完成: checked={} issues={} valid={}",
                report.getCheckedCount(), report.getIssues().size(), report.isValid());
        return report;
    }

    /**
     * 解析校验目标（归一化已存映射行与内联入参为 {@link MappingTarget}）。
     *
     * @param dto    入参
     * @param report 报告（映射不存在时写入 MAPPING_NOT_FOUND）
     * @return 待校验目标列表
     */
    private List<MappingTarget> resolveTargets(OntologyMappingValidateDTO dto,
                                               OntologyMappingValidationVO report) {
        List<MappingTarget> targets = new ArrayList<>();
        if (dto == null) {
            return targets;
        }

        // ① 按 mappingId 精确校验
        if (!isBlank(dto.getMappingId())) {
            Map<String, Object> row = findMappingById(dto.getMappingId());
            if (row == null) {
                report.getIssues().add(new OntologyMappingIssueVO(null, null, null, CODE_MAPPING_NOT_FOUND,
                        "映射 " + dto.getMappingId() + " 不存在"));
                return targets;
            }
            targets.add(toTarget(row));
            return targets;
        }

        // ② 保存前内联校验（datasetId + fieldMappings）
        if (dto.getFieldMappings() != null && !dto.getFieldMappings().isEmpty()) {
            targets.add(new MappingTarget(dto.getEntityCode(), dto.getDatasetId(), null, dto.getFieldMappings()));
            return targets;
        }

        // ③ 已存映射批量校验（entityCode 非空时按 entity_code 过滤，否则全量）
        for (Map<String, Object> row : listMappings(dto.getEntityCode(), null)) {
            targets.add(toTarget(row));
        }
        return targets;
    }

    /**
     * 已存映射行 → {@link MappingTarget}。
     *
     * <p>{@code field_mappings} JSONB 内取 {@code datasetId}（DW 资源 id）与字段映射；
     * 字段映射缺失时回退 {@code propertyMappings}（{@code 属性: 列} 扁平形态）。
     */
    private MappingTarget toTarget(Map<String, Object> row) {
        Map<String, Object> attrs = parseFieldMappings(row.get("field_mappings"));
        String tableName = asText(row.get("resource_name"));
        if (tableName.isEmpty()) {
            tableName = asText(row.get("table_schema"));
        }
        List<FieldMappingItemDTO> items = toFieldItems(attrs.get("fieldMappings"));
        if (items.isEmpty()) {
            Object propertyMappings = attrs.get("propertyMappings");
            if (propertyMappings instanceof Map<?, ?> pm) {
                pm.forEach((k, v) -> {
                    FieldMappingItemDTO item = new FieldMappingItemDTO();
                    item.setSource(asText(k));
                    item.setTarget(asText(v));
                    items.add(item);
                });
            }
        }
        return new MappingTarget(asText(row.get("entity_code")), asText(attrs.get("datasetId")), tableName, items);
    }

    /**
     * 单条映射校验：DW 表存在性 → 列存在性 → 类型兼容。
     *
     * @param target          校验目标
     * @param resourceById    CURATED 资源索引（resource_id）
     * @param resourceByName  CURATED 资源索引（resource_name，小写）
     * @param fieldsCache     列定义缓存（resource_id → 字段列表）
     * @param report          报告（追加 issues）
     */
    private void validateOneMapping(MappingTarget target,
                                    Map<String, Map<String, Object>> resourceById,
                                    Map<String, Map<String, Object>> resourceByName,
                                    Map<String, List<Map<String, Object>>> fieldsCache,
                                    OntologyMappingValidationVO report) {
        // ① 定位 DW 资源：优先 datasetId（resource_id），回退表名
        Map<String, Object> resource = null;
        if (!isBlank(target.datasetId)) {
            resource = resourceById.get(target.datasetId);
        }
        if (resource == null && !isBlank(target.tableName)) {
            resource = resourceByName.get(target.tableName.toLowerCase(Locale.ROOT));
        }
        String tableName = target.tableName;
        if (resource != null) {
            String resolvedName = asText(resource.get("resource_name"));
            if (!resolvedName.isEmpty()) {
                tableName = resolvedName;
            }
        }
        if (resource == null) {
            report.getIssues().add(new OntologyMappingIssueVO(target.entityCode, tableName, null,
                    CODE_TABLE_NOT_FOUND,
                    "映射目标 DW 表不在 CURATED 层: datasetId=" + target.datasetId + " table=" + tableName));
            return;
        }

        // ② 列定义（按 resource_id 缓存）
        String resourceId = asText(resource.get("resource_id"));
        List<Map<String, Object>> fields;
        try {
            fields = fieldsCache.computeIfAbsent(resourceId, dataNetClient::listMetadataFields);
        } catch (DataBridgeException e) {
            log.error("C4 校验失败：字段元数据不可用 resourceId={}", resourceId, e);
            report.getIssues().add(new OntologyMappingIssueVO(target.entityCode, tableName, null,
                    CODE_METADATA_UNAVAILABLE, "字段元数据不可用: " + e.getMessage()));
            return;
        }
        if (fields.isEmpty()) {
            report.getIssues().add(new OntologyMappingIssueVO(target.entityCode, tableName, null,
                    CODE_METADATA_UNAVAILABLE, "DW 资源 " + resourceId + " 无字段元数据（未采集）"));
            return;
        }
        Map<String, Map<String, Object>> columnByName = new LinkedHashMap<>();
        for (Map<String, Object> field : fields) {
            String fieldName = asText(field.get("fieldName"));
            if (!fieldName.isEmpty()) {
                columnByName.putIfAbsent(fieldName.toLowerCase(Locale.ROOT), field);
            }
        }

        // ③ 逐条字段映射：列存在性 + 类型兼容
        for (FieldMappingItemDTO item : target.fieldMappings) {
            String left = firstNonBlank(item.getSource(), item.getField());
            String right = firstNonBlank(item.getTarget(), item.getPropertyCode());
            // 历史数据书写方向不固定，按 DW 列定义判定哪一侧是物理列
            String columnName = null;
            String propertyRef = null;
            if (columnByName.containsKey(left.toLowerCase(Locale.ROOT))) {
                columnName = left;
                propertyRef = right;
            } else if (columnByName.containsKey(right.toLowerCase(Locale.ROOT))) {
                columnName = right;
                propertyRef = left;
            }
            if (columnName == null) {
                String missing = left.isEmpty() ? right : left;
                report.getIssues().add(new OntologyMappingIssueVO(target.entityCode, tableName, missing,
                        CODE_COLUMN_NOT_FOUND, "映射列在 DW 表 " + tableName + " 中不存在: " + missing));
                continue;
            }
            String ontologyType = resolveOntologyPropertyType(target.entityCode, propertyRef);
            if (ontologyType == null) {
                // 属性未落库不属 C4 范畴（列已确认存在），跳过类型比对
                continue;
            }
            Map<String, Object> column = columnByName.get(columnName.toLowerCase(Locale.ROOT));
            String dwDataType = asText(column.get("dataType"));
            if (!AutoDiscoverService.mapSqlType(dwDataType).equals(ontologyType)) {
                report.getIssues().add(new OntologyMappingIssueVO(target.entityCode, tableName, columnName,
                        CODE_TYPE_INCOMPATIBLE,
                        "列 " + columnName + " 类型 " + dwDataType + " 与本体属性类型 " + ontologyType + " 不兼容"));
            }
        }
    }

    /**
     * 查本体属性类型（本引擎自有表 {@code ecos_ontology_property}）。
     *
     * <p>实体与属性均按「id 或 code」双匹配，兼容 {@code entity_code} 存 id 或 code 的历史差异。
     *
     * @param entityRef   本体实体 id/code
     * @param propertyRef 本体属性 id/code
     * @return 属性类型（STRING/NUMBER/BOOLEAN/DATETIME）；查不到返回 null
     */
    private String resolveOntologyPropertyType(String entityRef, String propertyRef) {
        if (isBlank(entityRef) || isBlank(propertyRef)) {
            return null;
        }
        try {
            List<String> types = jdbc.queryForList(
                    "SELECT p.property_type FROM ecos_ontology_property p "
                            + "JOIN ecos_ontology_entity e ON p.entity_id = e.id "
                            + "WHERE (e.id = ? OR e.code = ?) AND (p.id = ? OR p.code = ?) "
                            + "AND p.is_deleted = 0 LIMIT 1",
                    String.class, entityRef, entityRef, propertyRef, propertyRef);
            return types.isEmpty() ? null : types.get(0);
        } catch (Exception e) {
            log.warn("查询本体属性类型失败 entity={} property={}: {}", entityRef, propertyRef, e.getMessage());
            return null;
        }
    }

    /** 字段映射原始列表（List&lt;Map&gt;）→ 强类型 {@link FieldMappingItemDTO} 列表。 */
    private List<FieldMappingItemDTO> toFieldItems(Object fieldMappings) {
        List<FieldMappingItemDTO> items = new ArrayList<>();
        if (fieldMappings instanceof List<?> list) {
            for (Object element : list) {
                if (element instanceof Map<?, ?> map) {
                    FieldMappingItemDTO item = new FieldMappingItemDTO();
                    item.setSource(asText(map.get("source")));
                    item.setTarget(asText(map.get("target")));
                    item.setField(asText(map.get("field")));
                    item.setPropertyCode(asText(map.get("propertyCode")));
                    items.add(item);
                }
            }
        }
        return items;
    }

    /** null 安全转字符串（null → ""，去首尾空白）。 */
    private static String asText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    /** 取首个非空白值（均空白返回 ""）。 */
    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        return second == null ? "" : second.trim();
    }

    /** 字符串是否空白。 */
    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** 校验目标 — 已存映射行 / 内联入参归一化后的统一形态。 */
    private static final class MappingTarget {

        /** 本体实体 id/code（entity_code） */
        private final String entityCode;
        /** 目标 DW 数据资源 id（可为空，回退按表名匹配） */
        private final String datasetId;
        /** 目标 DW 表名（可为空，datasetId 命中时以资源名为准） */
        private final String tableName;
        /** 字段映射列表 */
        private final List<FieldMappingItemDTO> fieldMappings;

        private MappingTarget(String entityCode, String datasetId, String tableName,
                              List<FieldMappingItemDTO> fieldMappings) {
            this.entityCode = entityCode;
            this.datasetId = datasetId;
            this.tableName = tableName;
            this.fieldMappings = fieldMappings == null ? new ArrayList<>() : fieldMappings;
        }
    }

    /**
     * 行 → {@link OntologyMappingVO} 转换（T16-2）。
     *
     * <p>字段映射严格对齐 {@code OntologyMappingController.rowToApiMap}：
     * id / objectId / objectTypeId / datasetId / objectType / sourceType /
     * sourceName / sourceUri / fieldMappings / propertyMappings / description / status /
     * createdAt / updatedAt。
     * {@code field_mappings} 列的 JSONB 形态可能是 Map / String / PGobject，统一落到 VO 字段。
     *
     * <p>JSDoc 溯源 T16-2。
     */
    private OntologyMappingVO toVO(Map<String, Object> row) {
   OntologyMappingVO vo = new OntologyMappingVO();
        String rowId = String.valueOf(row.get("id"));
        String entityCode = String.valueOf(row.getOrDefault("entity_code", ""));
        String entityName = String.valueOf(row.getOrDefault("entity_name", ""));
        String domainCode = String.valueOf(row.getOrDefault("domain_code", ""));
        String resourceName = String.valueOf(row.getOrDefault("resource_name", ""));
        String tableSchema = String.valueOf(row.getOrDefault("table_schema", ""));

        Object fmObj = row.get("field_mappings");
        Map<String, Object> attrs = parseFieldMappings(fmObj);

        vo.setId(rowId);
        vo.setObjectId(entityCode);
        vo.setObjectTypeId(entityCode);
        vo.setDatasetId(attrs.getOrDefault("datasetId", domainCode));
        vo.setObjectType(String.valueOf(attrs.getOrDefault("objectType", "ENTITY")));
        vo.setSourceType(domainCode);
        vo.setSourceName(entityName.isEmpty() ? resourceName : entityName);
        vo.setSourceUri(tableSchema);
        vo.setFieldMappings(toFieldList(attrs.getOrDefault("fieldMappings", new ArrayList<>())));
        vo.setPropertyMappings(toPropMap(attrs.getOrDefault("propertyMappings", new LinkedHashMap<>())));
        vo.setDescription(String.valueOf(attrs.getOrDefault("description", "")));
        vo.setStatus(String.valueOf(attrs.getOrDefault("status", "ACTIVE")));
        vo.setCreatedAt(String.valueOf(row.getOrDefault("created_at", "")));
        vo.setUpdatedAt(String.valueOf(row.getOrDefault("updated_at", "")));
        return vo;
    }

    /** 解析 field_mappings JSONB（可能是 PGobject / String / Map），与 Controller 旧版行为一致。 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseFieldMappings(Object fmObj) {
        if (fmObj == null) {
            return new LinkedHashMap<>();
        }
        if (fmObj instanceof Map) {
            return new LinkedHashMap<>((Map<String, Object>) fmObj);
        }
        try {
            return MAPPER.readValue(String.valueOf(fmObj),
                new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    /** 把扩展属性里的 fieldMappings（List/Map/其他）转成 {@code Map<String,Object>} 列表。 */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> toFieldList(Object fieldMappings) {
        if (fieldMappings instanceof List<?> list) {
            List<Map<String, Object>> out = new ArrayList<>(list.size());
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    m.forEach((k, v) -> entry.put(String.valueOf(k), v));
                    out.add(entry);
                }
            }
            return out;
        }
        return new ArrayList<>();
    }

    /** 把扩展属性里的 propertyMappings（Map/其他）转成 {@code Map<String,Object>} 扁平态。 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> toPropMap(Object propertyMappings) {
        if (propertyMappings instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            m.forEach((k, v) -> out.put(String.valueOf(k), v));
            return out;
        }
        return new LinkedHashMap<>();
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER = new com.fasterxml.jackson.databind.ObjectMapper();
}
