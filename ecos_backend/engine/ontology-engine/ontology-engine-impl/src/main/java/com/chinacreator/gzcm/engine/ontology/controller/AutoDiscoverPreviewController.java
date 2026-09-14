package com.chinacreator.gzcm.engine.ontology.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 本体自动发现预览 — PMO-40 批次3 T4b。
 *
 * <p>与现有 {@link AutoDiscoverController#autoDiscover}
 * （{@code POST /api/v1/ecos/domains/{domainCode}/auto-discover}）不同，
 * 本端点**仅预览**将要创建的实体列表，**不写数据库**。
 *
 * <h3>端点</h3>
 * <ul>
 *   <li>POST /api/v1/ecos/domains/{domainCode}/auto-discover/preview
 *       — 预览可发现的实体（dryRun=true，不落库）</li>
 * </ul>
 *
 * @author PMO-40 Batch3
 */
@RestController
@RequestMapping({"/api/v1/ecos/domains", "/api/ecos/domains"})
public class AutoDiscoverPreviewController {

    private static final Logger log = LoggerFactory.getLogger(AutoDiscoverPreviewController.class);

    private final JdbcTemplate jdbc;

    public AutoDiscoverPreviewController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 只读校验数据源存在性 + 资源归属（跨引擎只读校验，不操作 data-engine 任何表，
     * 仅 SELECT 用于归属确认，§0.3 跨引擎数据访问判定为只读合规）。
     */
    private boolean datasourceExists(String datasourceId) {
        try {
            Integer cnt = jdbc.queryForObject(
                "SELECT COUNT(*) FROM td_datasource WHERE datasource_id = ?",
                Integer.class, datasourceId);
            return cnt != null && cnt > 0;
        } catch (Exception e) {
            log.warn("Preview: datasource existence check failed: {}", e.getMessage());
            return true; // 宽松降级：允许预览继续
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  POST /api/v1/ecos/domains/{domainCode}/auto-discover/preview
    // ═══════════════════════════════════════════════════════════════

    /**
     * 预览自动发现 — 仅返回将要创建的 entities 列表，不写 DB。
     *
     * <p>请求体与 {@code /auto-discover} 一致：
     * <pre>
     * { "datasourceId": "...", "resourceNames": ["table1", "table2"] }
     * </pre>
     *
     * <p>返回每个资源的实体码、字段数、数据类型分布（dryRun 预览）。
     *
     * @param domainCode  业务域代码
     * @param body        包含 datasourceId + resourceNames
     */
    @PostMapping("/{domainCode}/auto-discover/preview")
    public ApiResponse<Map<String, Object>> preview(
            @PathVariable String domainCode,
            @RequestBody Map<String, Object> body) {
        try {
            String datasourceId = (String) body.get("datasourceId");
            @SuppressWarnings("unchecked")
            List<String> resourceNames = (List<String>) body.get("resourceNames");

            if (datasourceId == null || datasourceId.isBlank()) {
                return ApiResponse.badRequest("datasourceId is required");
            }
            if (resourceNames == null || resourceNames.isEmpty()) {
                return ApiResponse.badRequest("resourceNames is required");
            }
            if (!datasourceExists(datasourceId)) {
                throw new com.chinacreator.gzcm.common.exception.NotFoundException(
                    "数据源不存在: " + datasourceId);
            }

            List<Map<String, Object>> entityPreviews = new ArrayList<>();
            int totalFields = 0;

            for (String resourceName : resourceNames) {
                // 1) 查字段（只读，不写库）
                List<Map<String, Object>> fields;
                try {
                    fields = jdbc.queryForList(
                        "SELECT f.field_name AS \"fieldName\", " +
                        "       f.field_type AS \"dataType\", " +
                        "       COALESCE(f.description, '') AS \"comment\", " +
                        "       f.nullable    AS \"nullable\" " +
                        "FROM td_data_field f " +
                        "INNER JOIN td_data_resource r " +
                        "  ON f.resource_id = r.resource_id " +
                        "WHERE r.resource_name = ? AND r.datasource_id = ? " +
                        "ORDER BY f.field_order LIMIT 200",
                        resourceName, datasourceId);
                } catch (Exception e) {
                    log.warn("Preview: cannot query fields for resource={}: {}", resourceName, e.getMessage());
                    fields = Collections.emptyList();
                }

                if (fields.isEmpty()) {
                    Map<String, Object> skip = new LinkedHashMap<>();
                    skip.put("resourceName", resourceName);
                    skip.put("entityCode", toEntityCode(resourceName));
                    skip.put("discovered", false);
                    skip.put("reason", "无字段数据");
                    entityPreviews.add(skip);
                    continue;
                }

                // 2) 统计字段数 + 类型分布
                int fieldCount = fields.size();
                totalFields += fieldCount;
                Map<String, Integer> typeCounts = new LinkedHashMap<>();
                for (Map<String, Object> f : fields) {
                    Object dt = f.get("dataType");
                    String mapped = dt != null ? mapSqlType(dt.toString()) : "STRING";
                    typeCounts.merge(mapped, 1, Integer::sum);
                }

                // 3) 检查实体是否已存在（只读查询）
                boolean alreadyExists;
                try {
                    Integer cnt = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM ecos_ontology_entity " +
                        "WHERE code = ? AND domain_id = " +
                        "(SELECT id FROM ecos_domain WHERE code = ?)",
                        Integer.class, toEntityCode(resourceName), domainCode);
                    alreadyExists = cnt != null && cnt > 0;
                } catch (Exception e) {
                    alreadyExists = false;
                }

                Map<String, Object> preview = new LinkedHashMap<>();
                preview.put("resourceName", resourceName);
                preview.put("entityCode", toEntityCode(resourceName));
                preview.put("entityName", resourceName);
                preview.put("domainCode", domainCode);
                preview.put("discovered", true);
                preview.put("fieldCount", fieldCount);
                preview.put("typeDistribution", typeCounts);
                preview.put("alreadyExists", alreadyExists);
                entityPreviews.add(preview);
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("domainCode", domainCode);
            data.put("datasourceId", datasourceId);
            data.put("dryRun", true);
            data.put("entities", entityPreviews);
            data.put("totalResources", entityPreviews.size());
            data.put("totalFields", totalFields);
            data.put("note", "预览模式（dryRun=true），未写入数据库");

            return ApiResponse.success(data);
        } catch (Exception e) {
            log.error("Preview failed for domain={}: {}", domainCode, e.getMessage(), e);
            return ApiResponse.internalError("预览失败: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  私有工具方法（与 AutoDiscoverService 保持一致）
    // ═══════════════════════════════════════════════════════════════

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

    private String mapSqlType(String sqlType) {
        if (sqlType == null) return "STRING";
        String t = sqlType.toUpperCase();
        if (t.contains("INT") || t.contains("BIGINT") || t.contains("SMALLINT")
                || t.contains("TINYINT") || t.contains("NUMERIC") || t.contains("DECIMAL")
                || t.contains("FLOAT") || t.contains("DOUBLE")) {
            return "NUMBER";
        }
        if (t.contains("BOOL")) {
            return "BOOLEAN";
        }
        if (t.contains("DATE") || t.contains("TIME") || t.contains("TIMESTAMP")) {
            return "DATETIME";
        }
        return "STRING";
    }
}
