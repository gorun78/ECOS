package com.chinacreator.gzcm.ai.wagent.web;

import com.chinacreator.gzcm.ai.wagent.WAgentApiPaths;
import com.chinacreator.gzcm.ai.wagent.tool.*;
import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.exception.BusinessException;
import io.swagger.v3.oas.annotations.Operation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.springframework.http.HttpStatus.OK;

/**
 * 分册10 §5.2 #14/#15/#16 · Tool 编排 Controller（R-59① 仅协调）。
 * <p>#14 走 {@link ToolSearchService#search} 四重过滤（ACTIVE + 权限 + 天花板 + 数据分类）；
 * #15 单工具描述（不在目录 → 404）；#16 走 {@link ToolContractRegistry#validateFiveGates}
 * 五关：schema_valid / example_call / timeout_and_error / idempotent / policy_denial
 * （每关 one boolean + detail）。无 double。</p>
 */
@RestController
public class WAgentToolController {

    private static final Logger log = LoggerFactory.getLogger(WAgentToolController.class);
    private static final List<String> GATE_NAMES = List.of(
            "schema_valid", "example_call", "timeout_and_error", "idempotent", "policy_denial");

    private final ToolContractRegistry registry;
    private final ToolSearchService search;
    private final CircuitBreakerView breaker;

    public WAgentToolController(ToolContractRegistry registry, ToolSearchService search,
                                 CircuitBreakerView breaker) {
        this.registry = registry; this.search = search; this.breaker = breaker;
    }

    // #14 GET /tools → 200 list（权限过滤后，渐进披露入口）
    @Operation(operationId = "searchTools")
    @GetMapping(WAgentApiPaths.SEARCH_TOOLS)
    @ResponseStatus(OK)
    public ApiResponse<Map<String, Object>> searchTools(
            @RequestParam(value = "toolset", required = false) String toolset,
            @RequestHeader(value = "X-User-Id", required = false, defaultValue = "") String uid,
            @RequestHeader(value = "X-Tenant-Id", required = false, defaultValue = "") String tid,
            @RequestParam(value = "query", required = false) String query) {
        log.debug("wagent searchTools toolset={} query={} uid={} tid={}", toolset, query, uid, tid);
        log.debug("wagent searchTools routing=GET {}", WAgentApiPaths.SEARCH_TOOLS);
        log.debug("wagent searchTools delegate -> ToolSearchService.search 四重过滤");
        ToolSearchService.CallerContext ctx = new ToolSearchService.CallerContext(
                uid == null || uid.isBlank() ? "anonymous" : uid,
                tid == null || tid.isBlank() ? "default" : tid,
                Set.of(), 3);
        List<String> names = search.search(query, toolset, ctx).stream().map(ToolContract::name).toList();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("tools", names);
        out.put("count", names.size());   // integer-only
        out.put("progressiveDisclosure", true);
        return ApiResponse.success(out);
    }

    // #15 GET /tools/{name} → 200 或 404
    @Operation(operationId = "describeTool")
    @GetMapping(WAgentApiPaths.DESCRIBE_TOOL)
    public ApiResponse<Map<String, Object>> describeTool(@PathVariable("name") String name) {
        log.debug("wagent describeTool name={}", name);
        log.debug("wagent describeTool routing=GET {}", WAgentApiPaths.DESCRIBE_TOOL);
        log.debug("wagent describeTool check BusinessToolCatalog.byName + ToolContractRegistry.isActiveTool");
        ToolContract seed = BusinessToolCatalog.byName(name);
        if (seed == null) throw new BusinessException(404, "E_NOT_FOUND tool " + name);
        ToolContract tc = registry.isActiveTool(name) != null ? registry.isActiveTool(name) : seed;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("name", name);
        out.put("version", tc.version());
        out.put("engine", tc.engine());
        out.put("category", String.valueOf(tc.category()));
        out.put("status", String.valueOf(tc.status()));
        out.put("summary", tc.summary());                        // String JSON 语义
        out.put("dataClassification", tc.dataClassificationText());
        out.put("endpointKind", tc.endpointKind());
        out.put("endpointMethod", tc.endpointMethod());
        return ApiResponse.success(out);
    }

    // #16 POST /tools/{name}/validate → 200（五关，每关 one boolean + detail）
    @Operation(operationId = "validateToolContract")
    @PostMapping(WAgentApiPaths.VALIDATE_TOOL_CONTRACT)
    @ResponseStatus(OK)
    public ApiResponse<Map<String, Object>> validateToolContract(
            @PathVariable("name") String name,
            @RequestBody(required = false) Map<String, Object> body) {
        log.debug("wagent validateToolContract name={} body.keys={}", name,
                body == null ? 0 : body.keySet().size());
        log.debug("wagent validateToolContract routing=POST {}", WAgentApiPaths.VALIDATE_TOOL_CONTRACT);
        log.debug("wagent validateToolContract delegate -> ToolContractRegistry.validateFiveGates (static)");
        ToolContract tc = registry.isActiveTool(name);
        if (tc == null) throw new BusinessException(404, "E_NOT_FOUND tool " + name);
        List<String> fails = ToolContractRegistry.validateFiveGates(tc);
        boolean circuitOk = breaker.allow();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("name", name);
        // 五关逐关 one-bool + detail（顺序固定 GATE_NAMES）
        for (String g : GATE_NAMES) {
            boolean p = !fails.contains(g);
            out.put(g, p);
            out.put("detail_" + g, g + "=" + (p ? "pass" : "fail (gate declared failed)"));
        }
        out.put("circuit", circuitOk);
        out.put("ok", fails.isEmpty() && circuitOk);
        return ApiResponse.success(out);
    }
}
