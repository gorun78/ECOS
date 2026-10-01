package com.chinacreator.gzcm.engine.ontology.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyFunctionAuditVO;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyFunctionCompileVO;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyFunctionExecuteQuery;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyFunctionSaveDTO;
import com.chinacreator.gzcm.engine.ontology.engine.FunctionAuditRecord;
import com.chinacreator.gzcm.engine.ontology.engine.FunctionCacheManager;
import com.chinacreator.gzcm.engine.ontology.engine.FunctionResult;
import com.chinacreator.gzcm.engine.ontology.engine.FunctionSandboxEngine;
import com.chinacreator.gzcm.engine.ontology.engine.FunctionValidator;
import com.chinacreator.gzcm.engine.ontology.service.AuditContextGuard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * FunctionController — Function 沙箱执行引擎 REST API。
 *
 * <h3>端点：</h3>
 * <ul>
 *   <li>POST /api/v1/ontology/functions/test — 测试 Function（沙箱执行+返回结果+生成的SQL）</li>
 *   <li>POST /api/v1/ontology/functions/compile — 仅编译（返回生成的SQL，不执行）</li>
 *   <li>GET  /api/v1/ontology/functions/{propertyId}/execute — 执行已存储的 Function 属性</li>
 *   <li>GET  /api/v1/ontology/functions/audit — 审计日志分页查询</li>
 *   <li>GET  /api/v1/ontology/functions/whitelist — 返回白名单函数列表</li>
 * </ul>
 *
 * <p>W80/W81/M0（F03-06）改造：</p>
 * <ul>
 *   <li><b>审计上下文守卫</b>：强制从 {@link AuditContextGuard} 解析操作人 + traceId，
 *       缺失即 {@code 400 ECOS-ONTO-060} 拒收（旧实现的 {@code "anonymous"} 匿名兜底已删除）。</li>
 *   <li><b>审计双失败</b>：{@link FunctionCacheManager#writeAudit(FunctionAuditRecord)} 抛
 *       {@link FunctionCacheManager.AuditWriteException} → {@code 500 ECOS-ONTO-061} 拒收。</li>
 *   <li><b>/execute callerId 修正</b>：旧实现 {@code callerId = "api_execute_" + propertyId}
 *       匿名伪造已废弃；callerId 一律取 UserContext，propertyId 只作 function_id。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/ontology/functions")
public class FunctionController {

    private static final Logger log = LoggerFactory.getLogger(FunctionController.class);

    /** 审计上下文缺失 → 400。 */
    private static final String CODE_NO_AUDIT_CTX = "ECOS-ONTO-060";
    /** 审计双失败（Kafka + 本地）→ 500。 */
    private static final String CODE_AUDIT_DUAL_FAIL = "ECOS-ONTO-061";

    private final FunctionValidator validator;
    private final FunctionSandboxEngine engine;
    private final FunctionCacheManager cacheManager;
    private final AuditContextGuard auditContextGuard;

    public FunctionController(FunctionValidator validator,
                              FunctionSandboxEngine engine,
                              FunctionCacheManager cacheManager,
                              AuditContextGuard auditContextGuard) {
        this.validator = validator;
        this.engine = engine;
        this.cacheManager = cacheManager;
        this.auditContextGuard = auditContextGuard;
    }

    // ═══════════════ POST /test ═══════════════════

    /**
     * 测试 Function——沙箱执行+返回结果+生成的 SQL。
     */
    @PostMapping("/test")
    public ApiResponse<FunctionResult> test(@RequestBody OntologyFunctionSaveDTO req) {
        // 0. 审计上下文守卫（W81/M0）：缺操作人/traceId → 400 ECOS-ONTO-060 拒收
        try {
            auditContextGuard.require();
        } catch (AuditContextGuard.AuditContextMissingException e) {
            return ApiResponse.badRequest(CODE_NO_AUDIT_CTX + ": " + e.getMessage());
        }
        String callerId = auditContextGuard.resolveCallerId();
        String traceId = auditContextGuard.resolveTraceId();

        String expression = req.getExpression();
        String entityName = req.getEntityName();

        // 1. 安全扫描
        String forbidden = validator.quickScan(expression);
        if (forbidden != null) {
            return auditAndRespondNullResult(expression, entityName, callerId, traceId,
                    "FORBIDDEN", forbidden, () -> ApiResponse.badRequest(forbidden));
        }

        // 2. 白名单验证
        Map<String, Object> validation = validator.validate(expression);
        if (!(boolean) validation.getOrDefault("valid", false)) {
            @SuppressWarnings("unchecked")
            List<String> errors = (List<String>) validation.getOrDefault("errors", List.of());
            String errMsg = String.join("; ", errors);
            return auditAndRespondNullResult(expression, entityName, callerId, traceId,
                    "FORBIDDEN", errMsg, () -> ApiResponse.badRequest(errMsg));
        }

        // 3. 缓存检查
        FunctionResult cached = cacheManager.get(expression, entityName);
        if (cached != null) {
            log.info("Function test: cache hit for expression={} entity={}", expression, entityName);
            return ApiResponse.success(cached);
        }

        // 4. 执行
        try {
            FunctionResult result = engine.test(expression, entityName, callerId);
            cacheManager.put(expression, entityName, result);
            String resultStr = result.getValue() != null ? result.getValue().toString() : null;
            return auditAndRespond(
                    FunctionAuditRecord.builder()
                            .functionName("function-test")
                            .expression(expression)
                            .objectType(entityName)
                            .resultSummary(resultStr)
                            .durationMs(result.getExecutionTimeMs())
                            .cacheHit(result.isFromCache())
                            .callerId(callerId)
                            .traceId(traceId)
                            .status("SUCCESS")
                            .build(),
                    () -> ApiResponse.success(result));
        } catch (IllegalArgumentException e) {
            return auditAndRespondNullResult(expression, entityName, callerId, traceId,
                    "ERROR", e.getMessage(), () -> ApiResponse.badRequest(e.getMessage()));
        } catch (Exception e) {
            String errMsg = e.getMessage() != null ? e.getMessage() : "unknown";
            log.error("Function test failed: expression={} entity={}", expression, entityName, e);
            return auditAndRespondNullResult(expression, entityName, callerId, traceId,
                    "ERROR", errMsg, () -> ApiResponse.internalError("function test failed: " + errMsg));
        }
    }

    // ═══════════════ POST /compile ═══════════════════

    /**
     * 仅编译（返回生成的 SQL，不执行）。
     *
     * <p>不写审计：未实际执行函数，也不算失败。保持原行为。</p>
     */
    @PostMapping("/compile")
    public ApiResponse<OntologyFunctionCompileVO> compile(@RequestBody OntologyFunctionExecuteQuery query) {
        String expression = query.getExpression();
        String entityName = query.getEntityName();

        String forbidden = validator.quickScan(expression);
        if (forbidden != null) {
            return ApiResponse.badRequest(forbidden);
        }

        Map<String, Object> validation = validator.validate(expression);
        if (!(boolean) validation.getOrDefault("valid", false)) {
            @SuppressWarnings("unchecked")
            List<String> errors = (List<String>) validation.getOrDefault("errors", List.of());
            return ApiResponse.badRequest(String.join("; ", errors));
        }

        try {
            Map<String, Object> compiled = engine.compile(expression);
            if (compiled.containsKey("error")) {
                return ApiResponse.badRequest((String) compiled.get("error"));
            }
            OntologyFunctionCompileVO vo = new OntologyFunctionCompileVO();
            vo.setSql((String) compiled.get("sql"));
            vo.setParams(compiled.get("params"));
            vo.setEntityName(entityName != null ? entityName : (String) compiled.get("entityName"));
            return ApiResponse.success(vo);
        } catch (Exception e) {
            log.error("Function compile failed: expression={}", expression, e);
            return ApiResponse.internalError("compile failed");
        }
    }

    // ═══════════════ GET /{propertyId}/execute ═══════════════════

    /**
     * 执行已存储的 Function 属性。
     *
     * <p>W80 修正：callerId 取值改走 {@link AuditContextGuard}（不再 {@code "api_execute_" + propertyId} 匿名）。
     * {@code propertyId} 只作 {@code function_id} 写入审计，不冒充操作人。</p>
     */
    @GetMapping("/{propertyId}/execute")
    public ApiResponse<FunctionResult> execute(
            @PathVariable String propertyId,
            @RequestParam(value = "expression", required = true) String expression,
            @RequestParam(value = "entityName", required = false) String entityName) {
        // 0. 审计上下文守卫
        try {
            auditContextGuard.require();
        } catch (AuditContextGuard.AuditContextMissingException e) {
            return ApiResponse.badRequest(CODE_NO_AUDIT_CTX + ": " + e.getMessage());
        }
        String callerId = auditContextGuard.resolveCallerId();
        String traceId = auditContextGuard.resolveTraceId();

        // 1. 安全扫描
        String forbidden = validator.quickScan(expression);
        if (forbidden != null) {
            return auditAndRespond(FunctionAuditRecord.builder()
                            .functionId(propertyId)
                            .functionName("function-" + propertyId)
                            .expression(expression)
                            .objectType(entityName)
                            .callerId(callerId)
                            .traceId(traceId)
                            .status("FORBIDDEN")
                            .errorMessage(forbidden)
                            .build(),
                    () -> ApiResponse.badRequest(forbidden));
        }

        // 2. 验证
        Map<String, Object> validation = validator.validate(expression);
        if (!(boolean) validation.getOrDefault("valid", false)) {
            @SuppressWarnings("unchecked")
            List<String> errors = (List<String>) validation.getOrDefault("errors", List.of());
            String errMsg = String.join("; ", errors);
            return auditAndRespond(FunctionAuditRecord.builder()
                            .functionId(propertyId)
                            .functionName("function-" + propertyId)
                            .expression(expression)
                            .objectType(entityName)
                            .callerId(callerId)
                            .traceId(traceId)
                            .status("FORBIDDEN")
                            .errorMessage(errMsg)
                            .build(),
                    () -> ApiResponse.badRequest(errMsg));
        }

        // 3. 缓存检查
        FunctionResult cached = cacheManager.get(expression, entityName);
        if (cached != null) {
            return ApiResponse.success(cached);
        }

        // 4. 执行
        try {
            FunctionResult result = engine.test(expression, entityName, callerId);
            cacheManager.put(expression, entityName, result);
            String resultStr = result.getValue() != null ? result.getValue().toString() : null;
            return auditAndRespond(
                    FunctionAuditRecord.builder()
                            .functionId(propertyId)
                            .functionName("function-" + propertyId)
                            .expression(expression)
                            .objectType(entityName)
                            .resultSummary(resultStr)
                            .durationMs(result.getExecutionTimeMs())
                            .cacheHit(result.isFromCache())
                            .callerId(callerId)
                            .traceId(traceId)
                            .status("SUCCESS")
                            .build(),
                    () -> ApiResponse.success(result));
        } catch (Exception e) {
            String errMsg = e.getMessage() != null ? e.getMessage() : "unknown";
            log.error("Function execute failed: propertyId={}", propertyId, e);
            return auditAndRespond(FunctionAuditRecord.builder()
                            .functionId(propertyId)
                            .functionName("function-" + propertyId)
                            .expression(expression)
                            .objectType(entityName)
                            .callerId(callerId)
                            .traceId(traceId)
                            .status("ERROR")
                            .errorMessage(errMsg)
                            .build(),
                    () -> ApiResponse.internalError("function execute failed: " + errMsg));
        }
    }

    // ═══════════════ GET /audit ═══════════════════

    /**
     * 审计日志分页查询（W82 起底层读 V172 新表 {@code ecos_ontology.ecos_function_audit}）。
     * 返回结构保持 {@link OntologyFunctionAuditVO}（items 为 JDBC 行 Map，动态豁免）。
     */
    @GetMapping("/audit")
    public ApiResponse<OntologyFunctionAuditVO> audit(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "callerId", required = false) String callerId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        try {
            Map<String, Object> raw = cacheManager.queryAudit(status, callerId, page, pageSize);
            OntologyFunctionAuditVO vo = new OntologyFunctionAuditVO();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) raw.get("items");
            vo.setItems(items);
            vo.setTotal(objToInt(raw.get("total")));
            vo.setPage(objToInt(raw.get("page")));
            vo.setPageSize(objToInt(raw.get("pageSize")));
            return ApiResponse.success(vo);
        } catch (Exception e) {
            log.error("Function audit query failed", e);
            return ApiResponse.internalError("audit query failed");
        }
    }

    // ═══════════════ GET /whitelist ═══════════════════

    @GetMapping("/whitelist")
    public ApiResponse<List<String>> whitelist() {
        List<String> functions = new ArrayList<>(FunctionValidator.WHITELISTED_FUNCTIONS);
        functions.sort(String::compareTo);
        return ApiResponse.success(functions);
    }

    // ═══════════════ private helpers ═══════════════════

    /**
     * 写入审计 + 返回响应。若 writeAudit 双失败 → {@code 500 ECOS-ONTO-061} 拒收。
     * 用于"响应 body 依赖 FunctionResult 结果"的路径（成功/失败执行）。
     */
    private ApiResponse<FunctionResult> auditAndRespond(FunctionAuditRecord rec,
                                                        java.util.function.Supplier<ApiResponse<FunctionResult>> res) {
        try {
            cacheManager.writeAudit(rec);
        } catch (FunctionCacheManager.AuditWriteException ae) {
            return ApiResponse.internalError(CODE_AUDIT_DUAL_FAIL + ": audit dual-fail — function rejected: " + ae.getMessage());
        }
        return res.get();
    }

    /**
     * 便于 "/test" 路径的 FORBIDDEN/ERROR 分支（result 为 null，函数名固定 "function-test"）。
     */
    private ApiResponse<FunctionResult> auditAndRespondNullResult(String expression,
                                                                  String entityName,
                                                                  String callerId,
                                                                  String traceId,
                                                                  String status,
                                                                  String errorMessage,
                                                                  java.util.function.Supplier<ApiResponse<FunctionResult>> res) {
        return auditAndRespond(
                FunctionAuditRecord.builder()
                        .functionName("function-test")
                        .expression(expression)
                        .objectType(entityName)
                        .callerId(callerId)
                        .traceId(traceId)
                        .status(status)
                        .errorMessage(errorMessage)
                        .build(),
                res);
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
}
