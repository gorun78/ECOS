package com.chinacreator.gzcm.engine.ontology.service;

import com.chinacreator.gzcm.engine.ontology.client.DataNetResourceClient;
import com.chinacreator.gzcm.engine.ontology.dao.AutoDiscoverDao;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyAutoDiscoverMappingVO;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyAutoDiscoverResultVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AutoDiscoverService — 本体自动发现。
 *
 * <p>Wave B-2 · T13 (来源: 肖国荣 / 日期: 2026-09-12 / 责任人: fullstack-implementer)</p>
 *
 * <p>T13 改造: 原 {@code getResourceFields} 用 JdbcTemplate 直查 data-engine 的数据资源
 * / 数据字段两张元数据表 (跨引擎指定, 违反铁律 §0.3.1 微服务边界),
 * 改为 delegate 到 {@link DataNetResourceClient} 走 datanet service REST。
 * 本引擎自有表的读写经 {@link AutoDiscoverDao}（ecos_ontology_entity / ecos_domain /
 * ecos_ontology_property / ecos_entity_table_mapping），属本引擎合理范围。</p>
 *
 * <p>PMO-B7 T1 (D11) 加固: 取数源统一收敛为 DW(CURATED) 层资源清单
 * （{@link DataNetResourceClient#listResourcesByLayer(String)}）+
 * 列定义（{@link DataNetResourceClient#listMetadataFields(String)}）；
 * data-engine 不可达时按默认拒绝抛 {@link com.chinacreator.gzcm.common.exception.DataAccessException}
 * （架构铁律 §2.4-6 精神），**不再静默降级返回空集**（D1 教训）。</p>
 */
@Service
public class AutoDiscoverService {

    private static final Logger log = LoggerFactory.getLogger(AutoDiscoverService.class);

    /** 取数源分层：DW 层（本体工作台仅允许只读 DW 层，见 §0.5 边界铁律）。 */
    private static final String DW_LAYER = "CURATED";

    private final AutoDiscoverDao autoDiscoverDao;
    private final DataNetResourceClient dataNetClient;

    public AutoDiscoverService(AutoDiscoverDao autoDiscoverDao, DataNetResourceClient dataNetClient) {
        this.autoDiscoverDao = autoDiscoverDao;
        this.dataNetClient = dataNetClient;
    }

    public List<Map<String, Object>> autoDiscover(String domainCode, String datasourceId, List<String> resourceNames) {
        List<Map<String, Object>> results = new ArrayList<>();

        // DW 层资源清单一次拉取（避免循环调 REST）；data-engine 不可达时抛 DataAccessException（默认拒绝）
        List<Map<String, Object>> curatedResources = loadCuratedResources();
        // 列定义缓存：resource_id → 字段列表，避免同一资源重复请求（禁循环查库）
        Map<String, List<Map<String, Object>>> fieldsCache = new LinkedHashMap<>();

        for (String resourceName : resourceNames) {
            List<Map<String, Object>> fields =
                    resolveResourceFields(curatedResources, fieldsCache, datasourceId, resourceName);
            if (fields.isEmpty()) {
                log.warn("No fields found for resource: {}", resourceName);
                continue;
            }

            String entityCode = toEntityCode(resourceName);
            Map<String, Object> entity = createOntologyEntity(entityCode, resourceName, domainCode);

            int propCount = 0;
            for (Map<String, Object> field : fields) {
                String fieldName = (String) field.get("fieldName");
                String dataType = (String) field.get("dataType");
                if (fieldName == null) continue;
                createEntityProperty(entityCode, fieldName, dataType, domainCode);
                propCount++;
            }

            Map<String, Object> mapping = createMapping(entityCode, resourceName, domainCode, datasourceId,
                    (String) fieldValue(fields, "schema"), fields);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("entityCode", entityCode);
            result.put("entityName", resourceName);
            result.put("domainCode", domainCode);
            result.put("propertyCount", propCount);
            result.put("mapping", mapping);
            results.add(result);

            log.info("Auto-discovered: {} -> entity {} with {} properties", resourceName, entityCode, propCount);
        }

        return results;
    }

    // ──────────────────────────────────────────────────────────────
    // T16-3 (2026-09-13)：VO 重载 — Controller 强类型返回
    // 旧 Map 方法原样保留（C1 兼容，其他调用方可继续使用）
    // ──────────────────────────────────────────────────────────────

    /**
     * 自动发现（VO 版本） — T16-3。
     *
     * <p>复用 {@link #autoDiscover(String, String, List)} 的 Map 结果，
     * 逐条映射到 {@link OntologyAutoDiscoverResultVO}。
     */
    public List<OntologyAutoDiscoverResultVO> autoDiscoverVO(String domainCode,
                                                              String datasourceId,
                                                              List<String> resourceNames) {
        List<Map<String, Object>> raw = autoDiscover(domainCode, datasourceId, resourceNames);
        if (raw == null) {
            return new ArrayList<>();
        }
        return raw.stream().map(this::toResultVO).collect(Collectors.toList());
    }

    private OntologyAutoDiscoverResultVO toResultVO(Map<String, Object> row) {
        OntologyAutoDiscoverResultVO vo = new OntologyAutoDiscoverResultVO();
        vo.setEntityCode(objToString(row.get("entityCode")));
        vo.setEntityName(objToString(row.get("entityName")));
        vo.setDomainCode(objToString(row.get("domainCode")));
        vo.setPropertyCount(objToInt(row.get("propertyCount")));
        // mapping 是 createMapping 的 PG 行 Map，保留 raw Map 透传（动态 payload 豁免）
        vo.setMapping((Map<String, Object>) row.get("mapping"));
        return vo;
    }

    /**
     * 实体表映射列表（VO 版本） — T16-3。
     *
     * <p>复用 {@link #listMappings(String)} 的 raw Map 结果，
     * 逐条映射到 {@link OntologyAutoDiscoverMappingVO}。
     */
    public List<OntologyAutoDiscoverMappingVO> listMappingsVO(String domainCode) {
        List<Map<String, Object>> raw = listMappings(domainCode);
        if (raw == null) {
            return new ArrayList<>();
        }
        return raw.stream().map(this::toMappingVO).collect(Collectors.toList());
    }

    private OntologyAutoDiscoverMappingVO toMappingVO(Map<String, Object> row) {
        OntologyAutoDiscoverMappingVO vo = new OntologyAutoDiscoverMappingVO();
        vo.setId(row.get("id"));
        vo.setEntityCode(objToString(row.get("entity_code")));
        vo.setEntityName(objToString(row.get("entity_name")));
        vo.setDomainCode(objToString(row.get("domain_code")));
        vo.setDatasourceId(objToString(row.get("datasource_id")));
        vo.setResourceName(objToString(row.get("resource_name")));
        vo.setTableSchema(objToString(row.get("table_schema")));
        // field_mappings 是 JSONB 列，PG JDBC 直出 PGobject → 原始 Object 透传
        vo.setFieldMappings(row.get("field_mappings"));
        vo.setCreatedAt(objToString(row.get("created_at")));
        vo.setUpdatedAt(objToString(row.get("updated_at")));
        return vo;
    }

    private static String objToString(Object o) {
        return o == null ? null : o.toString();
    }

    private static Integer objToInt(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(o.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 查询实体表映射（不分页，按 domain 可选过滤，按创建时间倒序）。
     */
    public List<Map<String, Object>> listMappings(String domainCode) {
        return autoDiscoverDao.listMappings(domainCode);
    }

    /**
     * 拉取 DW（CURATED）层资源清单。
     *
     * <p>本体工作台对 DW 层**只读**（§0.5 边界铁律），取数一律经 data-engine REST，
     * 禁止直查 data-engine 元数据表。data-engine 不可达或响应非法时，
     * {@link DataNetResourceClient#listResourcesByLayer(String)} 抛
     * {@link com.chinacreator.gzcm.common.exception.DataAccessException}（默认拒绝）。
     *
     * @return CURATED 层资源行（含 {@code resource_id / resource_name / datasource_id}）
     */
    public List<Map<String, Object>> loadCuratedResources() {
        return dataNetClient.listResourcesByLayer(DW_LAYER);
    }

    /**
     * 从 DW 层资源清单定位资源并取列定义（供自动发现与预览共用，避免同类逻辑重复实现）。
     *
     * <p>「DW 层确无该资源」属正常业务语义 → 返回空列表（调用方按「无字段数据」处理）；
     * 「data-engine 不可达」→ 由 {@link DataNetResourceClient#listMetadataFields(String)} 抛
     * {@link com.chinacreator.gzcm.common.exception.DataAccessException}（默认拒绝，不静默降级）。
     *
     * @param curatedResources DW 层资源清单（调用方一次拉取）
     * @param fieldsCache      列定义缓存（resource_id → 字段列表），避免循环请求
     * @param datasourceId     数据源 ID（非空时参与资源匹配）
     * @param resourceName     资源名（表名）
     * @return 列定义列表；DW 层无该资源时为空列表
     */
    public List<Map<String, Object>> resolveResourceFields(List<Map<String, Object>> curatedResources,
                                                           Map<String, List<Map<String, Object>>> fieldsCache,
                                                           String datasourceId,
                                                           String resourceName) {
        String resourceId = null;
        for (Map<String, Object> row : curatedResources) {
            if (!resourceName.equals(objToString(row.get("resource_name")))) {
                continue;
            }
            String rowDatasourceId = objToString(row.get("datasource_id"));
            if (datasourceId != null && !datasourceId.isBlank()
                    && rowDatasourceId != null && !datasourceId.equals(rowDatasourceId)) {
                continue;
            }
            resourceId = objToString(row.get("resource_id"));
            if (resourceId != null && !resourceId.isBlank()) {
                break;
            }
        }
        if (resourceId == null || resourceId.isBlank()) {
            log.warn("DW(CURATED) 层未找到资源: datasourceId={}, resourceName={}", datasourceId, resourceName);
            return Collections.emptyList();
        }
        // data-engine 不可达时 listMetadataFields 抛 DataAccessException，向上传播（默认拒绝）
        return fieldsCache.computeIfAbsent(resourceId, dataNetClient::listMetadataFields);
    }

    private Map<String, Object> createOntologyEntity(String entityCode, String tableName, String domainCode) {
        String id = UUID.randomUUID().toString().replace("-", "").substring(0, 32);
        String ontologyId = findOrCreateOntology(domainCode);

        Map<String, Object> existing = autoDiscoverDao.createOntologyEntity(entityCode, domainCode, id, ontologyId, tableName);
        if (existing != null) {
            log.info("Entity {} already exists in domain {}", entityCode, domainCode);
            return existing;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("code", entityCode);
        result.put("name", tableName);
        return result;
    }

    private String findOrCreateOntology(String domainCode) {
        String existing = autoDiscoverDao.findOntologyId(domainCode);
        if (existing != null) {
            return existing;
        }

        String oid = UUID.randomUUID().toString().replace("-", "").substring(0, 32);
        autoDiscoverDao.insertOntology(oid, domainCode);
        return oid;
    }

    private void createEntityProperty(String entityCode, String fieldName, String dataType, String domainCode) {
        String propCode = fieldName;
        String ontologyType = mapSqlType(dataType);
        String propId = UUID.randomUUID().toString().replace("-", "").substring(0, 32);

        Integer count = autoDiscoverDao.countEntityProperty(propCode, entityCode, domainCode);
        if (count != null && count > 0) return;

        autoDiscoverDao.insertEntityProperty(propId, propCode, fieldName, ontologyType, entityCode, domainCode);
    }

    private Map<String, Object> createMapping(String entityCode, String resourceName, String domainCode,
                                               String datasourceId, String schema, List<Map<String, Object>> fields) {
        String id = UUID.randomUUID().toString().replace("-", "").substring(0, 32);
        String entityName = resourceName;

        List<Map<String, String>> fmList = new ArrayList<>();
        for (Map<String, Object> f : fields) {
            Map<String, String> fm = new LinkedHashMap<>();
            fm.put("field", (String) f.get("fieldName"));
            fm.put("type", (String) f.get("dataType"));
            fm.put("propertyCode", (String) f.get("fieldName"));
            fmList.add(fm);
        }

        String fieldMappingsJson = toJsonString(fmList);

        autoDiscoverDao.upsertMapping(id, entityCode, entityName, domainCode, datasourceId, resourceName, schema,
                fieldMappingsJson);

        return autoDiscoverDao.findMappingById(id);
    }

    private String toEntityCode(String tableName) {
        StringBuilder sb = new StringBuilder();
        boolean capitalize = true;
        for (char c : tableName.toCharArray()) {
            if (c == '_' || c == '-' || c == ' ') {
                capitalize = true;
            } else if (capitalize) {
                sb.append(Character.toUpperCase(c));
                capitalize = false;
            } else {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }

    /**
     * SQL 类型 → 本体属性类型映射（STRING / NUMBER / BOOLEAN / DATETIME）。
     *
     * <p>PMO-B2 T2：由 private 提升为包级 static，供
     * {@link OntologyMappingService} 的 C4 映射校验复用（禁止重复实现同类逻辑）。
     */
    static String mapSqlType(String sqlType) {
        if (sqlType == null) return "STRING";
        String t = sqlType.toUpperCase();
        if (t.contains("INT") || t.contains("BIGINT") || t.contains("SMALLINT") || t.contains("TINYINT") || t.contains("NUMERIC") || t.contains("DECIMAL") || t.contains("FLOAT") || t.contains("DOUBLE"))
            return "NUMBER";
        if (t.contains("BOOL"))
            return "BOOLEAN";
        if (t.contains("DATE") || t.contains("TIME") || t.contains("TIMESTAMP"))
            return "DATETIME";
        return "STRING";
    }

    private Object fieldValue(List<Map<String, Object>> fields, String key) {
        if (fields.isEmpty()) return null;
        return fields.get(0).get(key);
    }

    private String toJsonString(Object obj) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(obj);
        } catch (Exception e) {
            return "[]";
        }
    }
}
