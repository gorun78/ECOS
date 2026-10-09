package com.chinacreator.gzcm.engine.ai.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.runtime.llm.agent.spi.AgentEvolutionCapability;
import com.chinacreator.gzcm.runtime.llm.agent.spi.AgentRuntimeCapability;
import com.chinacreator.gzcm.runtime.llm.agent.spi.AgentRuntimeCapabilityUnavailableException;
import com.chinacreator.gzcm.runtime.llm.agent.spi.AgentRuntimeProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 进化编排端点 —— 只经 {@link AgentEvolutionCapability} SPI 出口。
 *
 * <p>详细设计-06 §八 接缝义务 1（M0 直改，ARCH_SPEC C257）：本类原先直接
 * import {@code services.agent.runtime.evolution.EvolutionOrchestrator}，
 * 使 {@code ai-engine-impl} 反向 compile-time 依赖部署态服务；现改为运行期
 * 从 {@link AgentRuntimeProvider} 取能力，provider 缺席即 501 + {@code stub=true}，
 * 禁止 200 + 空数据伪装成功。</p>
 *
 * <p>路径与签名零变化（API 只增不改）：{@code POST /api/v1/evolution/trigger}、
 * {@code GET /api/v1/evolution/log/{missionId}}。</p>
 */
@RestController
@RequestMapping("/api/v1/evolution")
public class EvolutionController {

    private static final Logger log = LoggerFactory.getLogger(EvolutionController.class);

    private static final String ERROR_CODE_RUNTIME_ABSENT = "ECOS-AI-AGENT-RUNTIME-ABSENT";
    private static final int CODE_RUNTIME_NOT_ASSEMBLED = 501;

    private final ObjectProvider<AgentRuntimeProvider> runtimeProvider;

    public EvolutionController(ObjectProvider<AgentRuntimeProvider> runtimeProvider) {
        this.runtimeProvider = runtimeProvider;
    }

    @PostMapping("/trigger")
    public ApiResponse<Map<String, Object>> trigger(@RequestBody Map<String, Object> request) {
        AgentEvolutionCapability evolution;
        try {
            evolution = resolveEvolution();
        } catch (AgentRuntimeCapabilityUnavailableException e) {
            return unavailable(e);
        }
        try {
            String trigger = (String) request.getOrDefault("trigger", "MANUAL");
            @SuppressWarnings("unchecked")
            Map<String, Object> context = (Map<String, Object>) request.getOrDefault("context", Map.of());
            String missionId = evolution.triggerEvolution(trigger, context);
            log.info("Evolution triggered: missionId={}", missionId);
            return ApiResponse.success(Map.of("missionId", missionId, "status", "STARTED"));
        } catch (Exception e) {
            log.error("Evolution trigger failed", e);
            return ApiResponse.internalError("Evolution trigger failed");
        }
    }

    @GetMapping("/log/{missionId}")
    public ApiResponse<List<Map<String, Object>>> getLog(@PathVariable String missionId) {
        try {
            List<Map<String, Object>> logEntries = resolveEvolution().getEvolutionLog(missionId);
            return ApiResponse.success(logEntries);
        } catch (AgentRuntimeCapabilityUnavailableException e) {
            log.warn("evolution 依赖的运行时能力不可用: capability={} reason={}",
                    e.getCapability(), e.getReason());
            return ApiResponse.error(CODE_RUNTIME_NOT_ASSEMBLED, ERROR_CODE_RUNTIME_ABSENT,
                    "Agent 运行时能力 " + e.getCapability() + " 不可用: " + e.getReason());
        } catch (Exception e) {
            log.error("Get evolution log failed: missionId={}", missionId, e);
            return ApiResponse.internalError("Get evolution log failed");
        }
    }

    private AgentEvolutionCapability resolveEvolution() {
        AgentRuntimeProvider provider = runtimeProvider.getIfAvailable();
        if (provider == null) {
            throw new AgentRuntimeCapabilityUnavailableException(AgentRuntimeCapability.EVOLUTION,
                    "AgentRuntimeProvider 未在本承流形态中装配（agent-service 不在 classpath 或未被组件扫描）");
        }
        return provider.evolution();
    }

    private ApiResponse<Map<String, Object>> unavailable(AgentRuntimeCapabilityUnavailableException e) {
        log.warn("evolution 依赖的运行时能力不可用: capability={} reason={}", e.getCapability(), e.getReason());
        ApiResponse<Map<String, Object>> resp = ApiResponse.error(
                CODE_RUNTIME_NOT_ASSEMBLED, ERROR_CODE_RUNTIME_ABSENT,
                "Agent 运行时能力 " + e.getCapability() + " 不可用: " + e.getReason());
        Map<String, Object> stub = new LinkedHashMap<>();
        stub.put("stub", true);
        stub.put("capability", e.getCapability() == null ? null : e.getCapability().name());
        stub.put("provider", "AgentRuntimeProvider");
        stub.put("reason", e.getReason());
        resp.setData(stub);
        return resp;
    }
}
