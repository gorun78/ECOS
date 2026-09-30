package com.chinacreator.gzcm.engine.data.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.data.QueryExecutionService;
import com.chinacreator.gzcm.sysman.iam.context.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 统一 SQL 查询控制器。
 * 提供异构数据源的 SQL 执行、Schema 浏览、查询模板管理和历史记录。
 *
 * 架构规则 2.4：查询前调 security-engine RLS 注入行级过滤，不得绕过。
 *
 * @author ECOS Data Engine Team
 * @since 2026-07-11
 */
@RestController
@RequestMapping("/api/v1/engine/data/query")
public class QueryController {

    private static final Logger log = LoggerFactory.getLogger(QueryController.class);

    private final QueryExecutionService queryExecutionService;
    private final RestTemplate restTemplate;
    /** 详细设计-01 W32：两态寻址唯一出口，可选注入（缺失时 RLS 默认 DENY）。 */
    private final com.chinacreator.gzcm.runtime.access.endpoint.ServiceEndpointResolver endpointResolver;

    public QueryController(QueryExecutionService queryExecutionService,
                           @org.springframework.beans.factory.annotation.Autowired(required = false)
                           com.chinacreator.gzcm.runtime.access.endpoint.ServiceEndpointResolver endpointResolver) {
        this.queryExecutionService = queryExecutionService;
        this.restTemplate = new RestTemplate();
        this.endpointResolver = endpointResolver;
    }

    /**
     * 执行 SQL 查询。
     * 架构规则 2.4：若 body 传 tableName，查询前调 security-engine RLS 注入行级过滤。
     * 默认 DENY：RLS 调用失败/返回空 condition → 403 拒绝，不降级放行。
     *
     * <p>详细设计-01 W31（P0）：不再把 security 返回的<b>裸 RLS 条件字符串</b>直接
     * 拼进 SQL（旧的「包裹用户 SQL + 追加 WHERE 裸条件」形态已删除，见门禁
     * {@code RlsInjectionGuardArchTest}）。改为消费 security-engine 的<b>参数化谓词</b>
     * （C.2.2）——{@code predicateTemplate} 已在策略写入期经 {@code RlsPredicateValidator}
     * 白名单校验（禁 SQL 关键字/拼接语义），其 {@code :name} 占位符并入 {@code params}，
     * 由执行层统一参数化解析；只把 <code>用户 intent SQL</code> 包进子查询，不拼接任何
     * 客户端/策略原始条件串。</p>
     */
    @PostMapping("/execute")
    public ApiResponse<Map<String, Object>> execute(@RequestBody Map<String, Object> body,
                                                     @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            String datasourceId = (String) body.get("datasource_id");
            String sql = (String) body.get("sql");
            String tableName = (String) body.get("table_name");
            @SuppressWarnings("unchecked")
            Map<String, Object> params = new LinkedHashMap<>((Map<String, Object>) body.getOrDefault("params", Map.of()));
            int maxRows = body.containsKey("max_rows") ? ((Number) body.get("max_rows")).intValue() : 10000;
            int timeoutSeconds = body.containsKey("timeout_seconds") ? ((Number) body.get("timeout_seconds")).intValue() : 30;

            if (datasourceId == null || sql == null) {
                return ApiResponse.badRequest("数据源 ID 和 SQL 不能为空");
            }

            // ── 架构规则 2.4: RLS 行级过滤接入（参数化谓词，W30/W31）──
            if (tableName != null && !tableName.isEmpty()) {
                String userId = UserContext.getCurrentUserId();
                if (userId == null) {
                    userId = "anonymous";
                }

                Map<String, Object> rls = applyRls(tableName, userId, authHeader);
                if (rls == null) {
                    // 默认 DENY：RLS 不可用 → 拒绝，不降级放行
                    log.warn("RLS 不可用，默认 DENY: tableName={}, userId={}", tableName, userId);
                    return ApiResponse.forbidden("RLS 行级过滤不可用，查询被拒绝（默认 DENY）");
                }

                @SuppressWarnings("unchecked")
                Map<String, Object> pred = (Map<String, Object>) rls.get("predicate");
                if (pred == null) {
                    return ApiResponse.forbidden("RLS 未返回参数化谓词，查询被拒绝（默认 DENY）");
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> predParams = (Map<String, Object>) rls.get("params");

                String template = (String) pred.get("predicateTemplate");
                boolean denyIfEmpty = !Boolean.FALSE.equals(pred.get("denyIfEmpty"));
                if (template == null || template.isBlank() || "1=1".equals(template.trim())) {
                    // 无有效谓词：denyIfEmpty（默认）→ DENY，不泄露存在性。
                    log.warn("RLS 无有效谓词, denyIfEmpty={}, 默认 DENY: tableName={}, userId={}",
                            denyIfEmpty, tableName, userId);
                    return ApiResponse.forbidden("无匹配的行级安全策略，访问被拒绝（默认 DENY）");
                }

                // 谓词占位符并入 params（:name → 值），执行层统一参数化解析；不拼接裸条件串。
                if (predParams != null) {
                    params.putAll(predParams);
                }
                // 仅把「用户 intent SQL」包进子查询；WHERE 段 = 写期经 RlsPredicateValidator
                // 白名单校验的谓词模板（只含列名/占位符，无客户端值），绑定值经 params 参数化。
                sql = wrapWithRlsPredicate(sql, template);
                log.info("RLS 参数化谓词已注入: tableName={}, userId={}, template={}", tableName, userId, template);
            }

            // 大表查询保护：ExecutorService 超时保护 (30秒)
            final String finalSql = sql;
            ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                Future<Map<String, Object>> future = executor.submit(() ->
                        queryExecutionService.execute(datasourceId, finalSql, params, maxRows, timeoutSeconds));
                Map<String, Object> result = future.get(30, TimeUnit.SECONDS);
                return ApiResponse.success(result);
            } catch (TimeoutException e) {
                log.warn("Query execution timeout after 30s for datasource={}", datasourceId);
                Map<String, Object> timeoutResult = new LinkedHashMap<>();
                timeoutResult.put("timeout", true);
                timeoutResult.put("message", "查询超时（30秒）");
                return ApiResponse.success(timeoutResult);
            } finally {
                executor.shutdownNow();
            }
        } catch (IllegalArgumentException e) {
            return ApiResponse.badRequest(e.getMessage());
        } catch (Exception e) {
            log.error("Query execution failed", e);
            return ApiResponse.internalError("查询执行失败");
        }
    }

    /**
     * 调 security-engine RLS apply 端点获取<b>参数化谓词</b>。
     * 架构规则 2.1：引擎间只调 REST。2.4 第 6 条：RLS 不可用 → 返回 null（调用方默认 DENY）。
     *
     * @return RLS {@code data} 载荷（含 {@code predicate}/{@code params}），null 表示不可用（DENY）。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> applyRls(String tableName, String userId, String authHeader) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
            if (authHeader != null && !authHeader.isEmpty()) {
                headers.set("Authorization", authHeader);
            }

            String rlsUrl = resolveRlsApplyUrl();
            if (rlsUrl == null) {
                log.error("RLS 端点寻址失败（默认 DENY）: tableName={}, userId={}", tableName, userId);
                return null;
            }
            Map<String, Object> rlsRequest = Map.of("tableName", tableName, "userId", userId);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(rlsRequest, headers);

            Map<String, Object> response = restTemplate.postForObject(rlsUrl, entity, Map.class);
            if (response == null) {
                log.error("RLS apply 返回空响应: tableName={}, userId={}", tableName, userId);
                return null;
            }
            Object code = response.get("code");
            if (code == null || !"0".equals(code.toString())) {
                log.error("RLS apply 业务失败: code={}, message={}", code, response.get("message"));
                return null;
            }
            Object data = response.get("data");
            return data instanceof Map ? (Map<String, Object>) data : null;
        } catch (Exception e) {
            log.error("RLS apply REST 调用失败: tableName={}, userId={}, error={}", tableName, userId, e.getMessage());
            return null; // 默认 DENY
        }
    }

    /**
     * 详细设计-01 W32：security-engine 端点经 {@link ServiceEndpointResolver}（单源
     * route-manifest.json，{@code /api/v1/security} → owner sysman）两态寻址，
     * <b>禁止</b>引擎内硬编码本机单体端点常量。
     * resolver 不可用 → 返回 null（RLS 不可用，调用方默认 DENY，不降级放行）。
     */
    private String resolveRlsApplyUrl() {
        if (endpointResolver == null) {
            log.warn("ServiceEndpointResolver 不可用，RLS 端点无法寻址（默认 DENY）");
            return null;
        }
        return endpointResolver.resolve(
                        "sysman", com.chinacreator.gzcm.runtime.access.endpoint.ServiceEndpointResolver.RouteMode.MONOLITH)
                .map(base -> base + "/api/security/rls/apply")
                .orElseGet(() -> {
                    log.warn("route-manifest 未登记 sysman，RLS 端点无法寻址（默认 DENY）");
                    return null;
                });
    }

    /**
     * 详细设计-01 W31：只把「用户 intent SQL」包进子查询套上参数化谓词 WHERE 段。
     * <code>template</code> 是写期经 {@code RlsPredicateValidator} 白名单校验的谓词模板
     * （仅含列名与 {@code :name} 占位符，无客户端值），绑定值经 params 参数化，
     * 不拼接任何裸条件串。
     */
    private static String wrapWithRlsPredicate(String intentSql, String predicateTemplate) {
        return String.format("SELECT * FROM (%s) AS _rls WHERE %s", intentSql, predicateTemplate);
    }

    /**
     * 获取数据源 Schema 树。
     */
    @GetMapping("/schema/{dsId}")
    public ApiResponse<Map<String, Object>> getSchemaTree(@PathVariable String dsId) {
        try {
            Map<String, Object> result = queryExecutionService.getSchemaTree(dsId);
            return ApiResponse.success(result);
        } catch (IllegalArgumentException e) {
            return ApiResponse.badRequest(e.getMessage());
        } catch (Exception e) {
            log.error("Failed to get schema tree for dsId={}", dsId, e);
            return ApiResponse.internalError("获取 Schema 失败");
        }
    }

    /**
     * 保存查询模板（新增或更新）。
     */
    @PostMapping("/template")
    public ApiResponse<Map<String, Object>> saveTemplate(@RequestBody Map<String, Object> body) {
        try {
            Map<String, Object> result = queryExecutionService.saveTemplate(body);
            return ApiResponse.success(result);
        } catch (IllegalArgumentException e) {
            return ApiResponse.badRequest(e.getMessage());
        } catch (Exception e) {
            log.error("Failed to save template", e);
            return ApiResponse.internalError("保存模板失败");
        }
    }

    /**
     * 查询模板列表（分页，可选按数据源过滤）。
     */
    @GetMapping("/templates")
    public ApiResponse<Map<String, Object>> listTemplates(
            @RequestParam(required = false) String datasourceId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        try {
            Map<String, Object> result = queryExecutionService.listTemplates(datasourceId, page, pageSize);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("Failed to list templates", e);
            return ApiResponse.internalError("获取模板列表失败");
        }
    }

    /**
     * 获取模板详情。
     */
    @GetMapping("/templates/{id}")
    public ApiResponse<Map<String, Object>> getTemplate(@PathVariable String id) {
        try {
            Map<String, Object> result = queryExecutionService.getTemplate(id);
            return ApiResponse.success(result);
        } catch (IllegalArgumentException e) {
            return ApiResponse.notFound(e.getMessage());
        } catch (Exception e) {
            log.error("Failed to get template id={}", id, e);
            return ApiResponse.internalError("获取模板失败");
        }
    }

    /**
     * 删除模板。
     */
    @DeleteMapping("/templates/{id}")
    public ApiResponse<Void> deleteTemplate(@PathVariable String id) {
        try {
            queryExecutionService.deleteTemplate(id);
            return ApiResponse.success(null);
        } catch (IllegalArgumentException e) {
            return ApiResponse.notFound(e.getMessage());
        } catch (Exception e) {
            log.error("Failed to delete template id={}", id, e);
            return ApiResponse.internalError("删除模板失败");
        }
    }

    /**
     * 查询执行历史（分页）。
     */
    @GetMapping("/history")
    public ApiResponse<Map<String, Object>> getQueryHistory(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        try {
            Map<String, Object> result = queryExecutionService.getQueryHistory(page, pageSize);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("Failed to get query history", e);
            return ApiResponse.internalError("获取查询历史失败");
        }
    }

    /**
     * 取消正在执行的查询。
     */
    @PostMapping("/cancel/{historyId}")
    public ApiResponse<Void> cancelQuery(@PathVariable String historyId) {
        try {
            queryExecutionService.cancelQuery(historyId);
            return ApiResponse.success(null);
        } catch (IllegalArgumentException e) {
            return ApiResponse.notFound(e.getMessage());
        } catch (Exception e) {
            log.error("Failed to cancel query historyId={}", historyId, e);
            return ApiResponse.internalError("取消查询失败");
        }
    }
}
