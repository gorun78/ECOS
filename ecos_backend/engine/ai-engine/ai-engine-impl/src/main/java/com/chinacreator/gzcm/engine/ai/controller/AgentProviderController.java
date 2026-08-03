package com.chinacreator.gzcm.engine.ai.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.ai.service.LLMProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent Provider 管理端点 — 列出已注册的 LLM Provider 及其状态。
 *
 * <h3>端点</h3>
 * <ul>
 *   <li>{@code GET /api/v1/agent/providers} — 列出所有 Provider（按 priority 排序）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/agent")
public class AgentProviderController {

    private static final Logger log = LoggerFactory.getLogger(AgentProviderController.class);

    private final List<LLMProvider> providers;

    public AgentProviderController(@Autowired(required = false) List<LLMProvider> providers) {
        this.providers = (providers != null) ? providers : List.of();
        log.info("[AgentProviderController] {} LLM providers registered", this.providers.size());
        for (LLMProvider p : this.providers) {
            log.info("[AgentProviderController]   - {} (priority={}, functionCalling={}, available={})",
                    p.getName(), p.priority(), p.supportsFunctionCalling(), p.isAvailable());
        }
    }

    /**
     * 列出所有已注册的 LLM Provider 及其运行状态。
     *
     * @return Provider 列表，每项含 name / priority / supportsFunctionCalling / available
     */
    @GetMapping("/providers")
    public ApiResponse<Map<String, Object>> listProviders() {
        List<Map<String, Object>> providerList = new ArrayList<>();

        // 按 priority 升序排列
        List<LLMProvider> sorted = new ArrayList<>(providers);
        sorted.sort(Comparator.comparingInt(LLMProvider::priority));

        int availableCount = 0;
        for (LLMProvider p : sorted) {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("name", p.getName());
            info.put("priority", p.priority());
            info.put("supportsFunctionCalling", p.supportsFunctionCalling());
            info.put("available", p.isAvailable());
            providerList.add(info);
            if (p.isAvailable()) {
                availableCount++;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("providers", providerList);
        result.put("total", providerList.size());
        result.put("available", availableCount);

        return ApiResponse.success(result);
    }
}
