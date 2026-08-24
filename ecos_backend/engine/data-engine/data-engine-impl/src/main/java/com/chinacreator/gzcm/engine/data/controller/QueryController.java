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

    /** security-engine RLS apply 端点（架构规则 2.1：引擎间只调 REST） */
    private static final String RLS_APPLY_URL = "http://localhost:8080/api/security/rls/apply";

    public QueryController(QueryExecutionService queryExecutionService) {
        this.queryExecutionService = queryExecutionService;
        this.restTemplate = new RestTemplate();
    }

    /**
     * 执行 SQL 查询。
     * 架构规则 2.4：若 body 传 tableName，查询前调 security-engine RLS 注入行级过滤。
     * 默认 DENY：RLS 调用失败/返回空 condition → 403 拒绝，不降级放行。
     */
    @PostMapping("/execute")
    public ApiResponse<Map<String, Object>> execute(@RequestBody Map<String, Object> body,
                                                     @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            String datasourceId = (String) body.get("datasource_id");
            String sql = (String) body.get("sql");
            String tableName = (String) body.get("table_name");
            @SuppressWarnings("unchecked")
            Map<String, Object> params = (Map<String, Object>) body.getOrDefault("params", Map.of());
            int maxRows = body.containsKey("max_rows") ? ((Number) body.get("max_rows")).intValue() : 10000;
            int timeoutSeconds = body.containsKey("timeout_seconds") ? ((Number) body.get("timeout_seconds")).intValue() : 30;

            if (datasourceId == null || sql == null) {
                return ApiResponse.badRequest("数据源 ID 和 SQL 不能为空");
            }

            // ── 架构规则 2.4: RLS 行级过滤接入 ──
            // 若请求指定了 table_name，查询前调 security-engine RLS 注入行级过滤条件
            if (tableName != null && !tableName.isEmpty()) {
                String userId = UserContext.getCurrentUserId();
                if (userId == null) {
                    userId = "anonymous";
                }

                String rlsCondition = applyRls(tableName, userId, authHeader);
                if (rlsCondition == null || rlsCondition.isEmpty()) {
                    // 默认 DENY：RLS 不可用或返回空 → 拒绝，不降级放行
                    log.warn("RLS 行级过滤拒绝: tableName={}, userId={}, condition 为空或 RLS 不可用", tableName, userId);
                    return ApiResponse.forbidden("RLS 行级过滤不可用，查询被拒绝（默认 DENY）");
                }

                // 用子查询包裹原 SQL，注入 RLS WHERE 条件
                final String rlsSql = "SELECT * FROM (" + sql + ") AS _rls WHERE " + rlsCondition;
                sql = rlsSql;
                log.info("RLS 行级过滤已注入: tableName={}, userId={}, condition={}", tableName, userId, rlsCondition);
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
            return ApiResponse.internalError("查询执行失败: " + e.getMessage());
        }
    }

    /**
     * 调 security-engine RLS apply 端点获取行级过滤条件。
     * 架构规则 2.1：引擎间只调 REST，不调 Impl。
     * 架构规则 2.4 第 6 条：RLS 不可用 → 返回 null（调用方默认 DENY）。
     *
     * @param tableName 目标表名
     * @param userId 当前用户 ID
     * @return RLS 过滤条件字符串，null 表示 RLS 不可用（调用方应拒绝）
     */
    @SuppressWarnings("unchecked")
    private String applyRls(String tableName, String userId, String authHeader) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
            // 转发调用方的 JWT token（架构规则 2.1：引擎间 REST 调用需认证）
            if (authHeader != null && !authHeader.isEmpty()) {
                headers.set("Authorization", authHeader);
            }

            Map<String, Object> rlsRequest = Map.of("tableName", tableName, "userId", userId);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(rlsRequest, headers);

            Map<String, Object> response = restTemplate.postForObject(
                    RLS_APPLY_URL, entity, Map.class);

            if (response == null) {
                log.error("RLS apply 返回空响应: tableName={}, userId={}", tableName, userId);
                return null;
            }

            // ApiResponse 格式: {code:0, data:{condition:"...", ...}}
            Object code = response.get("code");
            if (code == null || !"0".equals(code.toString())) {
                log.error("RLS apply 业务失败: code={}, message={}", code, response.get("message"));
                return null;
            }

            Map<String, Object> data = (Map<String, Object>) response.get("data");
            if (data == null) {
                return null;
            }

            // 优先取 condition，兼容 rlsCondition
            Object condition = data.get("condition");
            if (condition == null) {
                condition = data.get("rlsCondition");
            }
            return condition != null ? condition.toString() : null;
        } catch (Exception e) {
            log.error("RLS apply REST 调用失败: tableName={}, userId={}, error={}", tableName, userId, e.getMessage());
            return null; // 默认 DENY
        }
    }

    /**
     * 获取 Schema 树结构。
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
            return ApiResponse.internalError("获取 Schema 失败: " + e.getMessage());
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
            return ApiResponse.internalError("保存模板失败: " + e.getMessage());
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
            return ApiResponse.internalError("获取模板列表失败: " + e.getMessage());
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
            return ApiResponse.internalError("获取模板失败: " + e.getMessage());
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
            return ApiResponse.internalError("删除模板失败: " + e.getMessage());
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
            return ApiResponse.internalError("获取查询历史失败: " + e.getMessage());
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
            return ApiResponse.internalError("取消查询失败: " + e.getMessage());
        }
    }
}
