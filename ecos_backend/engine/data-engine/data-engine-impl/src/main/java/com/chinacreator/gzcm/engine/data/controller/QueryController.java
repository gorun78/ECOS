package com.chinacreator.gzcm.engine.data.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.data.QueryExecutionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 统一 SQL 查询控制器。
 * 提供异构数据源的 SQL 执行、Schema 浏览、查询模板管理和历史记录。
 *
 * @author ECOS Data Engine Team
 * @since 2026-07-11
 */
@RestController
@RequestMapping("/api/v1/engine/data/query")
public class QueryController {

    private static final Logger log = LoggerFactory.getLogger(QueryController.class);

    private final QueryExecutionService queryExecutionService;

    public QueryController(QueryExecutionService queryExecutionService) {
        this.queryExecutionService = queryExecutionService;
    }

    /**
     * 执行 SQL 查询。
     */
    @PostMapping("/execute")
    public ApiResponse<Map<String, Object>> execute(@RequestBody Map<String, Object> body) {
        try {
            String datasourceId = (String) body.get("datasource_id");
            String sql = (String) body.get("sql");
            @SuppressWarnings("unchecked")
            Map<String, Object> params = (Map<String, Object>) body.getOrDefault("params", Map.of());
            int maxRows = body.containsKey("max_rows") ? ((Number) body.get("max_rows")).intValue() : 10000;
            int timeoutSeconds = body.containsKey("timeout_seconds") ? ((Number) body.get("timeout_seconds")).intValue() : 30;

            if (datasourceId == null || sql == null) {
                return ApiResponse.badRequest("数据源 ID 和 SQL 不能为空");
            }

            Map<String, Object> result = queryExecutionService.execute(
                    datasourceId, sql, params, maxRows, timeoutSeconds);
            return ApiResponse.success(result);
        } catch (IllegalArgumentException e) {
            return ApiResponse.badRequest(e.getMessage());
        } catch (Exception e) {
            log.error("Query execution failed", e);
            return ApiResponse.internalError("查询执行失败: " + e.getMessage());
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
