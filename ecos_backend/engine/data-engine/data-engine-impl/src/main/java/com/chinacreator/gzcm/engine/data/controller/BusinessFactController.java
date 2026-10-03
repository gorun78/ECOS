package com.chinacreator.gzcm.engine.data.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.context.TraceContext;
import com.chinacreator.gzcm.engine.data.service.BusinessFactService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * BusinessFactController — F02-06 业务事实导入/读取（详细设计-02 §D.2）。
 *
 * <p>端点：</p>
 * <ul>
 *   <li>{@code GET  /api/v1/datanet/facts/{factType}/template} — 模板下载</li>
 *   <li>{@code POST /api/v1/datanet/facts/{factType}/import} — 导入（≤100 行/批）</li>
 *   <li>{@code GET  /api/v1/datanet/facts/{factType}} — 分页列表</li>
 *   <li>{@code GET  /api/v1/datanet/facts/batches/{batchId}} — 批次状态</li>
 *   <li>{@code POST /api/v1/datanet/facts/{factType}/publish} — 发布批次（PASSED→PUBLISHED）</li>
 *   <li>{@code POST /api/v1/datanet/facts/action-outcomes} — W→D 反馈链</li>
 * </ul>
 *
 * <p>三滤波器：</p>
 * <ol>
 *   <li>VersionPrefixRewriteFilter — 无 /api/v1/datanet/facts → 裸前缀改写（§C.2.2 明令"数据域新裸前缀不加双向映射"）</li>
 *   <li>SecurityConfig — 默认 DENY（不加匿名豁免，JWT 必需）</li>
 *   <li>ClearanceInterceptor — 数据域 L2 准入（facts）；走既有必认证路径</li>
 * </ol>
 *
 * @author ECOS-BE (F02-06)
 */
@RestController
@RequestMapping("/api/v1/datanet/facts")
public class BusinessFactController {

    private static final Logger log = LoggerFactory.getLogger(BusinessFactController.class);

    private final BusinessFactService factService;

    public BusinessFactController(BusinessFactService factService) {
        this.factService = factService;
    }

    @GetMapping("/{factType}/template")
    public ApiResponse<Map<String, Object>> template(@PathVariable String factType) {
        return ApiResponse.success(factService.getTemplate(factType));
    }

    public record ImportRequest(String batchName, String currency,
                                List<Map<String, Object>> rows) {}

    @PostMapping("/{factType}/import")
    public ApiResponse<Map<String, Object>> importFacts(@PathVariable String factType,
                                                        @RequestBody ImportRequest req) {
        BusinessFactService.ImportResult r = factService.importBusinessFacts(
                factType, req.batchName(), req.rows());
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("batchId", r.batchId());
        body.put("accepted", r.accepted());
        body.put("rejected", r.rejected());
        body.put("traceId", TraceContext.current());
        return ApiResponse.success(body);
    }

    @GetMapping("/{factType}")
    public ApiResponse<Map<String, Object>> listFacts(@PathVariable String factType,
                                                       @RequestParam(required = false) String batchId,
                                                       @RequestParam(defaultValue = "1") int page,
                                                       @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(factService.listFacts(factType, batchId, page, size));
    }

    @GetMapping("/batches/{batchId}")
    public ApiResponse<Map<String, Object>> getBatch(@PathVariable String batchId,
                                                      @RequestParam String factType) {
        return ApiResponse.success(factService.getBatch(factType, batchId));
    }

    public record PublishRequest(String batchId) {}

    @PostMapping("/{factType}/publish")
    public ApiResponse<Map<String, Object>> publishBatch(@PathVariable String factType,
                                                          @RequestBody PublishRequest req) {
        int n = factService.publishBatch(factType, req.batchId());
        return ApiResponse.success("发布完成", Map.of("batchId", req.batchId(), "published", n));
    }

    public record ActionOutcomeRequest(String factType, String batchId, String evidenceRef) {}

    @PostMapping("/action-outcomes")
    public ApiResponse<Map<String, Object>> writeActionOutcome(@RequestBody ActionOutcomeRequest req) {
        int n = factService.writeActionOutcome(req.factType(), req.batchId(), req.evidenceRef());
        return ApiResponse.success("行动结果已写入", Map.of("batchId", req.batchId(),
                "updated", n));
    }
}
