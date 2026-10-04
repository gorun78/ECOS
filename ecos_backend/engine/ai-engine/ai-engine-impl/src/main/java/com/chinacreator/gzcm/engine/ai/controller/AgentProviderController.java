package com.chinacreator.gzcm.engine.ai.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.runtime.llm.gateway.LLMGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Agent Provider 管理端点 — 列出已注册的 LLM Provider 及其状态。
 *
 * <p>F06-05 要点 1（X-22）：引擎侧已删除 {@code LLMProvider} 抽象（0 实现，预防性拆除），
 * provider 目录合法性 <b>唯一</b>来自 llm-gateway 的 {@link LLMGateway} 兜路口径。
 * 本端点 API 只增不改（原 {@code GET /api/v1/agent/providers}），改为委托 llm-gateway
 * 取真值；若目录数据不可用 / 为空 → 返回 <b>501 + {@code stub=true}</b>（禁"恒空 200"
 * 伪实现，与分册 05 §0.2-⑦ 同裁定）。</p>
 *
 * <h3>端点</h3>
 * <ul>
 *   <li>{@code GET /api/v1/agent/providers} — 列出 llm-gateway 识别的 provider 及其状态</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/agent")
public class AgentProviderController {

    private static final Logger log = LoggerFactory.getLogger(AgentProviderController.class);

    private final LLMGateway llmGateway;

    public AgentProviderController(@Autowired(required = false) LLMGateway llmGateway) {
        this.llmGateway = llmGateway;
        if (llmGateway == null) {
            log.warn("[AgentProviderController] LLMGateway 未注入（stub=true 恒 501）");
        } else {
            log.info("[AgentProviderController] 委派 LLMGateway.getProviderStatus 取 provider 目录（F06-05）");
        }
    }

    /**
     * 列出所有 llm-gateway 识别的 Provider 及其状态（{@link LLMGateway#getProviderStatus()}
     * 真值代理）。目录不可用 / 空 → 501 + {@code stub=true}。
     */
    @GetMapping("/providers")
    public ApiResponse<Map<String, Object>> listProviders() {
        Map<String, Object> status;
        try {
            status = llmGateway == null ? Map.of() : llmGateway.getProviderStatus();
        } catch (Exception e) {
            log.warn("[AgentProviderController] getProviderStatus 异常（回 501 + stub=true）: {}", e.getMessage());
            status = Map.of();
        }

        if (status == null || status.isEmpty()) {
            // F06-05 要点 1（X-22）：无目录数据 → 501 + stub=true，禁"恒空 200" 伪实现。
            // ApiResponse 仅支持 error(int, String) — code=501 + 消息内 tagged "stub=true"
            // 携带 stub 语义（无 data 字段可挂载；与分册 05 §0.2 同裁定：501 明示不可用）
            log.info("[AgentProviderController] provider 目录不可用/为空 → 501 + stub=true");
            return ApiResponse.error(501, "llm-gateway provider 目录不可用或为空（stub=true 模式）");
        }

        List<Map<String, Object>> providerList = new ArrayList<>();
        Set<String> availableSet = new LinkedHashSet<>();
        for (Map.Entry<String, Object> entry : status.entrySet()) {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("name", entry.getKey());
            info.put("status", entry.getValue());
            providerList.add(info);
            if (entry.getValue() instanceof Map<?, ?> m
                    && "available".equals(String.valueOf(m.get("status")))) {
                availableSet.add(String.valueOf(entry.getKey()));
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("stub", false);
        result.put("providers", providerList);
        result.put("total", providerList.size());
        result.put("available", availableSet.size());
        return ApiResponse.success(result);
    }
}
