package com.chinacreator.gzcm.engine.data.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.security.rls.RlsExecutionContract;
import com.chinacreator.gzcm.engine.data.QueryExecutionService;
import com.chinacreator.gzcm.engine.data.security.RlsPredicateBinder;
import com.chinacreator.gzcm.sysman.iam.context.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
     * 默认 DENY：RLS 调用失败/裁决拒绝 → 拒绝，不降级放行。
     *
     * <p><b>#142/B4-3d-5 收口（差距 D-B4-24／§4.5.4 分态 D-B4-26）</b>：本通道此前读的是
     * {@code /rls/apply} 应答的<b>表达面</b> {@code predicate.predicateTemplate} + {@code :name}
     * 占位符，而另三通道（页面／本体／AI）读<b>执行契约面</b> {@code whereClause} + {@code ?}
     * ——同源不同口径。危害不是写法差异，而是<b>绕过了渲染器的拒绝归一</b>：上下文未解析时
     * 表达面的 {@code params} 带 {@code null}，本通道会把它当绑定值下推（{@code col = NULL}
     * 语义为「未知」，却表现为「该用户没有数据」，把 D-B4-23 伪装成正常空集）；恒真策略
     * （{@code 1=1}＝明确不限制行）则被本通道判成「无有效谓词」直接 403。</p>
     *
     * <p>现在读数一律经 {@link RlsExecutionContract#read} 单源，并按其四态分流：
     * {@code ALLOW_ALL}＝放行且不拼过滤；{@code FILTERED}＝片段经 {@link RlsPredicateBinder}
     * 改写为本引擎的命名参数形态，值只走 {@code PreparedStatement} 通道（IR05，
     * 门禁见 {@code QueryExecutorBindingTest}）；{@code DENIED}＝403 且不泄露存在性；
     * {@code UNAVAILABLE}＝503 {@code ECOS-SEC-501}，与「无权限」分态（§4.5.4 降级矩阵
     * 「security-engine 不可用 → 503 DENY」，与 {@code DecisionController} 的 fail-closed
     * 出口同形）。裸 RLS 条件字符串拼接的旧形态仍禁止（{@code RlsInjectionGuardArchTest}）。</p>
     *
     * <p><b>#142/B4-3e-18 收口（差距 D-B4-59 的 HTTP 保真腿）</b>：本方法此前返回裸
     * {@code ApiResponse}，{@code ApiResponse} 只描述 body、没有任何 HTTP 状态能力
     * ⇒ 三条安全拒绝出口（不可用 503／策略 DENY 403／绑定失败 403）在传输层一律是
     * <b>HTTP 200</b>：§4.5.4「不可用 ≠ 无权限」的分态只在信封里成立，状态行上
     * 403 与 503 同形，且都被 {@code res.ok} 读成成功（与
     * {@code DataBridgeHttpStatusFidelityTest} 钉住的 gateway 域桥缺陷同根）。
     * 现在拒绝态经 {@link ResponseEntity} 把携带值落成<b>真状态行</b>，
     * 信封体逐字段一字未动（{@code code}/{@code errorCode}/{@code message} 原形保留），
     * 因此既有按 {@code code} 分派的消费者不受影响；成功与非安全类失败出口
     * 有意保持 HTTP 200（不扩面，余面由 {@code SecurityDenialHttpStatusRatchetTest} 量化在册）。</p>
     */
    @PostMapping("/execute")
    public ResponseEntity<ApiResponse<Map<String, Object>>> execute(
            @RequestBody Map<String, Object> body,
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
                return ResponseEntity.ok(ApiResponse.badRequest("数据源 ID 和 SQL 不能为空"));
            }

            // ── 架构规则 2.4: RLS 行级过滤接入（执行契约单源 + 四态分流，D-B4-24）──
            if (tableName != null && !tableName.isEmpty()) {
                String userId = UserContext.getCurrentUserId();
                if (userId == null) {
                    userId = "anonymous";
                }

                RlsExecutionContract.Execution rls = applyRls(tableName, userId, authHeader);
                if (rls.unavailable()) {
                    // §4.5.4：不可用与「无权限」分态——503 可告警、可重试，禁冒充 403「没权限」。
                    // 状态行与信封同源（RlsExecutionContract.SEC_UNAVAILABLE_HTTP），禁两处各写一遍
                    log.warn("RLS 服务不可用，默认 DENY（503 分态）: tableName={}, userId={}, reason={}",
                            tableName, userId, rls.reason());
                    return ResponseEntity.status(RlsExecutionContract.SEC_UNAVAILABLE_HTTP)
                            .body(ApiResponse.error(RlsExecutionContract.SEC_UNAVAILABLE_HTTP,
                                    ERROR_SEC_UNAVAILABLE,
                                    "行级安全服务不可用，查询被拒绝（默认 DENY，非「无权限」）"));
                }
                if (rls.denied()) {
                    log.warn("RLS 裁决拒绝，默认 DENY: tableName={}, userId={}, reason={}, noPolicyMatched={}",
                            tableName, userId, rls.reason(), rls.noPolicyMatched());
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(ApiResponse.forbidden("无匹配的行级安全策略，访问被拒绝（默认 DENY）"));
                }
                if (rls.allowAll()) {
                    // 恒真＝裁决明确「不限制行」，与「应答没给谓词」是两个态（后者已在上两分支拒绝）
                    log.info("RLS 裁决恒真（不限制行），本查询不追加行过滤: tableName={}, userId={}",
                            tableName, userId);
                } else {
                    RlsPredicateBinder.Bound bound =
                            RlsPredicateBinder.toNamedForm(rls.clause(), rls.params(), params);
                    if (!bound.ok()) {
                        log.warn("RLS 片段不可安全绑定，默认 DENY: tableName={}, userId={}, reason={}",
                                tableName, userId, bound.reason());
                        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                .body(ApiResponse.forbidden("行级安全谓词无法安全绑定，查询被拒绝（默认 DENY）"));
                    }
                    params.putAll(bound.namedParams());
                    sql = wrapWithRlsPredicate(sql, bound.template());
                    log.info("RLS 参数化谓词已注入: tableName={}, userId={}, bindings={}",
                            tableName, userId, bound.namedParams().size());
                }
            }

            // 大表查询保护：ExecutorService 超时保护 (30秒)
            final String finalSql = sql;
            ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                Future<Map<String, Object>> future = executor.submit(() ->
                        queryExecutionService.execute(datasourceId, finalSql, params, maxRows, timeoutSeconds));
                Map<String, Object> result = future.get(30, TimeUnit.SECONDS);
                return ResponseEntity.ok(ApiResponse.success(result));
            } catch (TimeoutException e) {
                log.warn("Query execution timeout after 30s for datasource={}", datasourceId);
                Map<String, Object> timeoutResult = new LinkedHashMap<>();
                timeoutResult.put("timeout", true);
                timeoutResult.put("message", "查询超时（30秒）");
                return ResponseEntity.ok(ApiResponse.success(timeoutResult));
            } finally {
                executor.shutdownNow();
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.ok(ApiResponse.badRequest(e.getMessage()));
        } catch (Exception e) {
            log.error("Query execution failed", e);
            return ResponseEntity.ok(ApiResponse.internalError("查询执行失败"));
        }
    }

    /**
     * 调 security-engine RLS apply 端点，经 {@link RlsExecutionContract} 单源读回执行契约。
     * 架构规则 2.1：引擎间只调 REST。2.4 第 6 条：不可用 → {@link RlsExecutionContract.State#UNAVAILABLE} 态
     * （由调用方转 503 DENY），禁再返回 {@code null} 让调用方把「连不上」猜成某一种拒绝。
     *
     * @return 永不为 {@code null} 的 {@link RlsExecutionContract.Execution}
     */
    private RlsExecutionContract.Execution applyRls(String tableName, String userId, String authHeader) {
        String rlsUrl = resolveRlsApplyUrl();
        if (rlsUrl == null) {
            // 寻址失败属「安全服务不可达」，不是策略结论（§4.5.4 分态）
            return RlsExecutionContract.Execution.unavailable("端点寻址失败（resolver 缺失或 route-manifest 未登记 sysman）");
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
            if (authHeader != null && !authHeader.isEmpty()) {
                headers.set("Authorization", authHeader);
            }

            Map<String, Object> rlsRequest = Map.of("tableName", tableName, "userId", userId);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(rlsRequest, headers);

            Map<String, Object> response = restTemplate.postForObject(rlsUrl, entity, Map.class);
            if (response == null) {
                return RlsExecutionContract.Execution.unavailable("apply 返回空响应");
            }
            Object code = response.get("code");
            if (code == null || !"0".equals(code.toString())) {
                // security 侧业务失败（含 RlsController 兜底的 internalError）：不是策略结论，
                // 按不可用态处理，禁把「安全服务报错」读成「用户无权限」
                return RlsExecutionContract.Execution.unavailable(
                        "apply 业务失败 code=" + code + ", message=" + response.get("message"));
            }
            return RlsExecutionContract.read(response.get("data"), "table=" + tableName);
        } catch (Exception e) {
            log.error("RLS apply REST 调用失败: tableName={}, userId={}, error={}", tableName, userId, e.getMessage());
            return RlsExecutionContract.Execution.unavailable("REST 调用失败: " + e.getMessage());
        }
    }

    /** 不可用态的对外错误码（单源 {@link RlsExecutionContract#SEC_UNAVAILABLE_CODE}，禁再抄字面量）。 */
    static final String ERROR_SEC_UNAVAILABLE = RlsExecutionContract.SEC_UNAVAILABLE_CODE;

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
