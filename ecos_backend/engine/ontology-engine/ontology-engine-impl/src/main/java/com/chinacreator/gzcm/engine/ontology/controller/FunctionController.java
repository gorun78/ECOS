package com.chinacreator.gzcm.engine.ontology.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyFunctionAuditVO;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyFunctionCompileVO;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyFunctionExecuteQuery;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyFunctionSaveDTO;
import com.chinacreator.gzcm.engine.ontology.engine.FunctionCacheManager;
import com.chinacreator.gzcm.engine.ontology.engine.FunctionResult;
import com.chinacreator.gzcm.engine.ontology.engine.FunctionSandboxEngine;
import com.chinacreator.gzcm.engine.ontology.engine.FunctionValidator;

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
 * <p>T16-3 (2026-09-13) 改造：
 * <ul>
 *   <li>POST /test 入参 Map → {@link OntologyFunctionSaveDTO}；
 *       返回 {@link FunctionResult}（已有强类型 POJO，归位保持）</li>
 *   <li>POST /compile 入参 Map → {@link OntologyFunctionExecuteQuery}；
 *       返回 Map → {@link OntologyFunctionCompileVO}
 *       （{@code params} 字段动态豁免：SQL 占位参数 list，函数沙箱运行时 payload）</li>
 *   <li>GET /audit 返回 Map → {@link OntologyFunctionAuditVO}
 *       （{@code items} 字段动态豁免：PG 表 SELECT * 直出 row，JDBC 行 Map）</li>
 *   <li>GET /{propertyId}/execute — 查询参数直传，无入参改造；返回 {@link FunctionResult} 已强类型</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/ontology/functions")
public class FunctionController {

    private static final Logger log = LoggerFactory.getLogger(FunctionController.class);

    private final FunctionValidator validator;
    private final FunctionSandboxEngine engine;
    private final FunctionCacheManager cacheManager;

    public FunctionController(FunctionValidator validator,
                              FunctionSandboxEngine engine,
                              FunctionCacheManager cacheManager) {
        this.validator = validator;
        this.engine = engine;
        this.cacheManager = cacheManager;
    }

    // ═══════════════ POST /test ═══════════════════

    /**
     * 测试 Function——沙箱执行+返回结果+生成的 SQL。
     *
     * <p>T16-3：入参 Map → {@link OntologyFunctionSaveDTO}。
     * 返回 {@link FunctionResult}（已有强类型 POJO，归位保持）。
     */
    @PostMapping("/test")
    public ApiResponse<FunctionResult> test(@RequestBody OntologyFunctionSaveDTO req) {
        String expression = req.getExpression();
        String entityName = req.getEntityName();
        String callerId = req.getCallerId() != null ? req.getCallerId() : "anonymous";

        // 1. 安全扫描
        String forbidden = validator.quickScan(expression);
        if (forbidden != null) {
            cacheManager.writeAudit(null, expression, entityName, null,
                0, callerId, "FORBIDDEN", forbidden);
            return ApiResponse.badRequest(forbidden);
        }

        // 2. 白名单验证
        Map<String, Object> validation = validator.validate(expression);
        if (!(boolean) validation.getOrDefault("valid", false)) {
            @SuppressWarnings("unchecked")
            List<String> errors = (List<String>) validation.getOrDefault("errors", List.of());
            String errMsg = String.join("; ", errors);
            cacheManager.writeAudit(null, expression, entityName, null,
                0, callerId, "FORBIDDEN", errMsg);
            return ApiResponse.badRequest(errMsg);
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

            // 5. 写缓存+审计
            cacheManager.put(expression, entityName, result);
            String resultStr = result.getValue() != null ? result.getValue().toString() : null;
            cacheManager.writeAudit(null, expression, entityName, resultStr,
                result.getExecutionTimeMs(), callerId, "SUCCESS", null);

            return ApiResponse.success(result);
        } catch (IllegalArgumentException e) {
            cacheManager.writeAudit(null, expression, entityName, null,
                0, callerId, "ERROR", e.getMessage());
            return ApiResponse.badRequest(e.getMessage());
        } catch (Exception e) {
            String errMsg = e.getMessage() != null ? e.getMessage() : "未知错误";
            cacheManager.writeAudit(null, expression, entityName, null,
                0, callerId, "ERROR", errMsg);
            log.error("Function test failed: expression={} entity={}", expression, entityName, e);
            return ApiResponse.internalError("Function执行失败: " + errMsg);
        }
    }

    // ═══════════════ POST /compile ═══════════════════

    /**
     * 仅编译（返回生成的 SQL，不执行）。
     *
     * <p>T16-3：入参 Map → {@link OntologyFunctionExecuteQuery}；
     * 返回 Map → {@link OntologyFunctionCompileVO}。
     * {@code params} 字段动态豁免（SQL 占位参数 list，函数沙箱运行时 payload）。
     */
    @PostMapping("/compile")
    public ApiResponse<OntologyFunctionCompileVO> compile(@RequestBody OntologyFunctionExecuteQuery query) {
        String expression = query.getExpression();
        String entityName = query.getEntityName();

        // 1. 安全扫描
        String forbidden = validator.quickScan(expression);
        if (forbidden != null) {
            return ApiResponse.badRequest(forbidden);
        }

        // 2. 白名单验证
        Map<String, Object> validation = validator.validate(expression);
        if (!(boolean) validation.getOrDefault("valid", false)) {
            @SuppressWarnings("unchecked")
            List<String> errors = (List<String>) validation.getOrDefault("errors", List.of());
            return ApiResponse.badRequest(String.join("; ", errors));
        }

        // 3. 编译
        try {
            // T16-3: 函数沙箱运行时 payload 动态结构豁免 Map（engine.compile 返回 Map 含 params List<Object>）
            Map<String, Object> compiled = engine.compile(expression);
            if (compiled.containsKey("error")) {
                return ApiResponse.badRequest((String) compiled.get("error"));
            }
            OntologyFunctionCompileVO vo = new OntologyFunctionCompileVO();
            vo.setSql((String) compiled.get("sql"));
            vo.setParams(compiled.get("params")); // 动态 list，Object 承载（豁免）
            vo.setEntityName(entityName != null ? entityName : (String) compiled.get("entityName"));
            return ApiResponse.success(vo);
        } catch (Exception e) {
            log.error("Function compile failed: expression={}", expression, e);
            return ApiResponse.internalError("编译失败: " + e.getMessage());
        }
    }

    // ═══════════════ GET /{propertyId}/execute ═══════════════════

    /**
     * 执行已存储的 Function 属性。
     *
     * <p>路径参数 propertyId 为 Function 属性 ID。
     * 查询参数 expression / entityName 直传（查询参数天然强类型，无需改造）。
     * 返回 {@link FunctionResult}（已有强类型 POJO，归位保持）。
     */
    @GetMapping("/{propertyId}/execute")
    public ApiResponse<FunctionResult> execute(
            @PathVariable String propertyId,
            @RequestParam(value = "expression", required = true) String expression,
            @RequestParam(value = "entityName", required = false) String entityName) {
        String callerId = "api_execute_" + propertyId;

        // 1. 安全扫描
        String forbidden = validator.quickScan(expression);
        if (forbidden != null) {
            cacheManager.writeAudit(propertyId, expression, entityName, null,
                0, callerId, "FORBIDDEN", forbidden);
            return ApiResponse.badRequest(forbidden);
        }

        // 2. 验证
        Map<String, Object> validation = validator.validate(expression);
        if (!(boolean) validation.getOrDefault("valid", false)) {
            @SuppressWarnings("unchecked")
            List<String> errors = (List<String>) validation.getOrDefault("errors", List.of());
            String errMsg = String.join("; ", errors);
            cacheManager.writeAudit(propertyId, expression, entityName, null,
                0, callerId, "FORBIDDEN", errMsg);
            return ApiResponse.badRequest(errMsg);
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
            cacheManager.writeAudit(propertyId, expression, entityName, resultStr,
                result.getExecutionTimeMs(), callerId, "SUCCESS", null);
            return ApiResponse.success(result);
        } catch (Exception e) {
            String errMsg = e.getMessage() != null ? e.getMessage() : "未知错误";
            cacheManager.writeAudit(propertyId, expression, entityName, null,
                0, callerId, "ERROR", errMsg);
            log.error("Function execute failed: propertyId={}", propertyId, e);
            return ApiResponse.internalError("Function执行失败: " + errMsg);
        }
    }

    // ═══════════════ GET /audit ═══════════════════

    /**
     * 审计日志分页查询。
     *
     * <p>T16-3：返回 Map → {@link OntologyFunctionAuditVO}。
     * {@code items} 是 PG 表 SELECT * 直出 row（JDBC 行 Map），动态豁免。
     */
    @GetMapping("/audit")
    public ApiResponse<OntologyFunctionAuditVO> audit(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "callerId", required = false) String callerId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        try {
            // T16-3: 函数沙箱运行时 payload 动态结构豁免 Map（queryAudit 返回 items 是 JDBC 行 Map）
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
            return ApiResponse.internalError("审计查询失败: " + e.getMessage());
        }
    }

    // ═══════════════ GET /whitelist ═══════════════════

    /**
     * 返回白名单函数列表（String 列表已强类型，保持）。
     */
    @GetMapping("/whitelist")
    public ApiResponse<List<String>> whitelist() {
        List<String> functions = new ArrayList<>(FunctionValidator.WHITELISTED_FUNCTIONS);
        functions.sort(String::compareTo);
        return ApiResponse.success(functions);
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
