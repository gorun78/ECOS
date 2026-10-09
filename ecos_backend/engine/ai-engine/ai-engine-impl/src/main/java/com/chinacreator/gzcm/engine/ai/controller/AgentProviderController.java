package com.chinacreator.gzcm.engine.ai.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.error.AiError;
import com.chinacreator.gzcm.common.error.AiErrorClass;
import com.chinacreator.gzcm.engine.ai.trace.AiTraceSupport;
import com.chinacreator.gzcm.runtime.llm.gateway.LLMGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent Provider 目录端点 — provider 真值单源 = llm-gateway。
 *
 * <h3>端点</h3>
 * <ul>
 *   <li>{@code GET /api/v1/agent/providers} — 列出网关侧 provider 目录及探测状态</li>
 * </ul>
 *
 * <p>F06-05 要点 1（X-22 拆除）：引擎侧不再自持 {@code LLMProvider} 抽象，本端点改委托
 * {@link LLMGateway#getProviderStatus()} 取真值。<b>目录无数据时返回 501 + {@code stub=true}</b>
 * （{@link AiErrorClass#STUB_CAPABILITY}），禁止"恒空 200"伪实现。</p>
 */
@RestController
@RequestMapping("/api/v1/agent")
public class AgentProviderController {

    private static final Logger log = LoggerFactory.getLogger(AgentProviderController.class);

    /** llm-gateway 未装配（独立引擎调试态）时目录不可得，走 501 分支 */
    private final LLMGateway llmGateway;

    public AgentProviderController(@Autowired(required = false) LLMGateway llmGateway) {
        this.llmGateway = llmGateway;
    }

    /**
     * 列出 llm-gateway 侧 provider 目录及其运行状态。
     *
     * @return 200 + 目录；目录不可得/为空 → 501 + {@code stub=true}
     */
    @GetMapping("/providers")
    public ResponseEntity<ApiResponse<Map<String, Object>>> listProviders() {
        Map<String, Object> catalog = llmGateway == null ? null : llmGateway.getProviderStatus();
        if (catalog == null || catalog.isEmpty()) {
            return stubResponse(catalog == null ? "llm-gateway 未装配" : "provider 目录为空");
        }

        List<Map<String, Object>> providerList = new ArrayList<>();
        int availableCount = 0;
        for (Map.Entry<String, Object> entry : catalog.entrySet()) {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("name", entry.getKey());
            Object value = entry.getValue();
            boolean available = false;
            if (value instanceof Map<?, ?> status) {
                Object state = status.get("status");
                info.put("status", state == null ? "unknown" : String.valueOf(state));
                info.put("baseUrl", status.get("baseUrl"));
                available = "available".equals(info.get("status"));
            } else {
                info.put("status", "unknown");
            }
            info.put("available", available);
            providerList.add(info);
            if (available) {
                availableCount++;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("providers", providerList);
        result.put("total", providerList.size());
        result.put("available", availableCount);
        result.put("stub", false);
        result.put("catalogSource", "runtime.llm.gateway.LLMGateway#getProviderStatus");
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    private ResponseEntity<ApiResponse<Map<String, Object>>> stubResponse(String reason) {
        String traceId = AiTraceSupport.currentTraceId();
        log.warn("[AgentProviderController] provider 目录不可得，返回 501 + stub=true: {} (traceId={})",
                reason, traceId);
        Map<String, Object> data = new LinkedHashMap<>(
                AiError.of(AiErrorClass.STUB_CAPABILITY, traceId, reason).toResponseBody());
        data.put("providers", List.of());
        data.put("total", 0);
        data.put("available", 0);
        data.put("stub", true);
        ApiResponse<Map<String, Object>> body = ApiResponse.error(
                HttpStatus.NOT_IMPLEMENTED.value(), AiErrorClass.STUB_CAPABILITY.errorCode(),
                "provider 目录不可得（" + reason + "），能力未上线");
        body.setData(data);
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(body);
    }
}
