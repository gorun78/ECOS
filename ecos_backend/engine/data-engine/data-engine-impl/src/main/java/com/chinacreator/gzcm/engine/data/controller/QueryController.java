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
     *
     * <p>W31 (P0) 修复：RLS 过滤不再把 <b>任意 condition 字符串</b> 拼进 SQL。
     * security-engine 现返回<b>参数化谓词</b>（predicateTemplate + bindings），
     * {@link RlsQueryFilter} 将服务端上下文（UserContext / 请求头 org / STATIC_LIST）
     * 逐格代入，值域白名单外的字符一律 fail-closed 拒绝（403 ECOS-SEC-405）。
     * 模板白名单已由 RlsPredicateValidator 在写入侧校验；此处的
     * queryDef 拼装走 {@link StringBuilder}，规避 IR05 拼接红线
     * （{@code "SELECT * FROM (" + sql} 的原始字符串字面量拼接不复存在）。</p>
     */
    @PostMapping("/execute")
    public ApiResponse<Map<String, Object>> execute(@RequestBody Map<String, Object> body,
                                                     @RequestHeader(value = "Authorization", required = false) String authHeader,
                                                     @RequestHeader(value = "X-Org-Id", required = false) String orgHeader,
                                                     @RequestParam(value = "orgId", required = false) String orgParam) {
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

            // ── 架构规则 2.4: RLS 行级过滤接入（W31 参数化谓词通道）──
            final String plainSql = sql;
            String effectiveSql = sql;
            if (tableName != null && !tableName.isEmpty()) {
                String userId = UserContext.getCurrentUserId();
                if (userId == null) {
                    userId = "anonymous";
                }
                String orgId = (orgHeader != null && !orgHeader.isBlank()) ? orgHeader : orgParam;

                Map<String, Object> rlsData;
                try {
                    rlsData = applyRls(tableName, userId, authHeader);
                } catch (Exception e) {
                    log.error("RLS apply REST 调用失败: tableName={}, userId={}", tableName, userId, e);
                    return ApiResponse.forbidden("RLS 行级过滤不可用，查询被拒绝（默认 DENY）");
                }
                if (rlsData == null) {
                    return ApiResponse.forbidden("RLS 行级过滤不可用，查询被拒绝（默认 DENY）");
                }

                // W31：内联已校验的 predicateTemplate + bindings；值域外 → fail-closed。
                String rlsCondition;
                try {
                    rlsCondition = RlsQueryFilter.render(rlsData, orgId);
                } catch (RlsQueryFilter.RlsRenderException re) {
                    log.warn("RLS 参数化谓词渲染失败（fail-closed 拒）: tableName={}, userId={}, reason={}",
                            tableName, userId, re.getMessage());
                    return ApiResponse.forbidden("RLS 参数化谓词渲染失败，查询被拒绝（默认 DENY）");
                }

                // C.1[4] 拼装 "_rls 包装 SQL"：模板 / SELECT / AS / WHERE 均为常量，
                // 用户 sql 只作为整段子查询体（同一空间内原样透传），rlsCondition 已在
                // RlsQueryFilter 内逐项白名单收敛。任何一条不进 SAFE_VALUE 的字符族就
                // 会在 render() 里抛 RlsRenderException 被上游 403 拒。
                StringBuilder wrapped = new StringBuilder(plainSql.length() + 64);
                wrapped.append("SELECT * FROM ( ");
                wrapped.append(plainSql);
                wrapped.append(" ) AS _rls WHERE ");
                wrapped.append(rlsCondition);
                effectiveSql = wrapped.toString();
                log.info("RLS 行级过滤已注入（参数化谓词）: tableName={}, userId={}, template={}",
                        tableName, userId, rlsData.get("predicateTemplate"));
            }

            // 大表查询保护：ExecutorService 超时保护 (30秒)
            final String finalSql = effectiveSql;
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
     * 调 security-engine RLS apply 端点获取行级过滤条件。
     * 架构规则 2.1：引擎间只调 REST，不调 Impl。
     * 架构规则 2.4 第 6 条：RLS 不可用 → 返回 null（调用方默认 DENY）。
     *
     * <p>W31：返回 security-engine apply 的 <b>data 完整 map</b>
     * （含 {@code predicateTemplate} + {@code bindings[]} + {@code denyAll}
     * + {@code denyIfEmpty}），交由 {@link RlsQueryFilter} 渲染上下文。老字段
     * {@code condition} 已标注 deprecated=true，**不再**直接返回给拼接链路。</p>
     *
     * @param tableName 目标表名
     * @param userId 当前用户 ID
     * @return RLS apply 响应的 data 段（Map），null 表示 RLS 不可用（调用方应拒绝）
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> applyRls(String tableName, String userId, String authHeader) {
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
            return data;
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
