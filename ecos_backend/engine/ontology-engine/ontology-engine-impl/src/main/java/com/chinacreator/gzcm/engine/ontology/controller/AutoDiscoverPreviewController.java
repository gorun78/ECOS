package com.chinacreator.gzcm.engine.ontology.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.error.SystemErrorCode;
import com.chinacreator.gzcm.common.exception.DataBridgeException;
import com.chinacreator.gzcm.engine.ontology.service.AutoDiscoverPreviewQueryService;
import com.chinacreator.gzcm.engine.ontology.service.AutoDiscoverService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * <p>PMO-B7 T1 (D11)：列定义取数由「直查 data-engine 数据资源 / 数据字段元数据表」
 * 改为经 data-engine REST
 * （复用 {@link AutoDiscoverService#loadCuratedResources()} /
 * {@link AutoDiscoverService#resolveResourceFields}），
 * 符合 §0.5 边界铁律「本体工作台仅只读 DW 层」。
 *
 * @author PMO-40 Batch3
 */
@RestController
@RequestMapping({"/api/v1/ecos/domains", "/api/ecos/domains"})
public class AutoDiscoverPreviewController {

    private static final Logger log = LoggerFactory.getLogger(AutoDiscoverPreviewController.class);

    private final AutoDiscoverPreviewQueryService previewQueryService;

    /** DW 层取数源（经 data-engine REST，禁直查 td_data_*）。 */
    private final AutoDiscoverService autoDiscoverService;

    public AutoDiscoverPreviewController(AutoDiscoverPreviewQueryService previewQueryService,
                                         AutoDiscoverService autoDiscoverService) {
        this.previewQueryService = previewQueryService;
        this.autoDiscoverService = autoDiscoverService;
    }

    /**
     * 只读校验数据源存在性 + 资源归属（跨引擎只读校验，不操作 data-engine 任何表，
     * 仅 SELECT 用于归属确认，§0.3 跨引擎数据访问判定为只读合规）。
     *
     * <p><b>#142/B4-3e-58（差距 D-B4-88）</b>：收口前本方法在 {@code catch (Exception)} 顶层
     * {@code return true}，注释自证「宽松降级：允许预览继续」——把「查不了」洗成「查得到且通过」，
     * 违详设 §4.7.2「不可用 ≠ 无权限 ≠ 空结果」，且直接绕过调用方的 {@code NotFoundException} 分支
     * （数据源根本不存在时也照样放行去拉 DW 资源清单）。现改判为<b>显式不可用承载</b>：抛携带
     * 503 状态行 + {@code ECOS-SYS-503} 机读码的 {@link DataBridgeException}，由
     * {@link #preview} 的 {@code catch (DataBridgeException) → throw e} 保护腿透出，
     * 禁被外层 {@code catch (Exception) → internalError(500)} 反向折叠。</p>
     */
    private boolean datasourceExists(String datasourceId) {
        try {
            Integer cnt = previewQueryService.countDatasourceById(datasourceId);
            return cnt != null && cnt > 0;
        } catch (Exception e) {
            log.error("Preview: datasource existence check unavailable, datasourceId={}: {}",
                    datasourceId, e.getMessage(), e);
            throw new DataBridgeException(503, 503,
                    SystemErrorCode.ECOS_SYS_503 + ": 数据源存在性判定通道不可用，预览无法继续");
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

            // DW(CURATED) 层资源清单一次拉取（data-engine 不可达时抛 DataAccessException → 默认拒绝）
            List<Map<String, Object>> curatedResources = autoDiscoverService.loadCuratedResources();
            // 列定义缓存（resource_id → 字段列表），避免循环请求
            Map<String, List<Map<String, Object>>> fieldsCache = new LinkedHashMap<>();

            for (String resourceName : resourceNames) {
                // 1) 列定义（只读，不写库）—— 走 data-engine REST，禁直查 td_data_*
                List<Map<String, Object>> fields = autoDiscoverService.resolveResourceFields(
                        curatedResources, fieldsCache, datasourceId, resourceName);

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
                    Integer cnt = previewQueryService.countEntityByCodeAndDomain(
                            toEntityCode(resourceName), domainCode);
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
        } catch (DataBridgeException e) {
            // #142/B4-3e-58（D-B4-88）：承载型不可用（判定通道故障＝503）显式上抛，
            // 由 gateway GlobalExceptionHandler 按 getHttpStatus() 透出状态行，
            // 禁在此折成 internalError(500)——那会把「查不了」再次洗成「服务器错误」的无码面。
            throw e;
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
