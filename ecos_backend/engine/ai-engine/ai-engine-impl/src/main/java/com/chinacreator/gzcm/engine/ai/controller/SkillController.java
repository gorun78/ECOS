package com.chinacreator.gzcm.engine.ai.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.runtime.llm.agent.spi.AgentMcpCapability;
import com.chinacreator.gzcm.runtime.llm.agent.spi.AgentRuntimeCapability;
import com.chinacreator.gzcm.runtime.llm.agent.spi.AgentRuntimeCapabilityUnavailableException;
import com.chinacreator.gzcm.runtime.llm.agent.spi.AgentRuntimeProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Skill Controller — list/enable/disable external MCP provider skills via the SPI.
 *
 * <p>Endpoint: /api/v1/skills（路径与签名零变化，API 只增不改）</p>
 *
 * <p><b>详细设计-06 §八 接缝义务 1 + 2（M0 直改）</b>：旧实现 compile-time 依赖
 * {@code com.chinacreator.gzcm.services.agent.runtime.mcp.HermesMCPClient}；
 * 现只依赖 {@link AgentMcpCapability}，Hermes 是可选外部 provider 而非引擎内建概念
 * （铁律 §2.1 / ARCH_SPEC C238/C257）。</p>
 *
 * <p><b>口径修正（禁 200-with-empty-body 假成功）</b>：通道不可用时旧实现返回
 * {@code code=0 + "Skills service not available"}（把失败伪装成成功）；
 * 现改为 {@code code=501 + data.stub=true}。{@code GET /health} 仍如实回
 * {@code {available: false}}（健康探测语义，不是业务成功）。</p>
 *
 * @author ecos-factory
 * @since 详细设计-06 §八 M0
 */
@RestController
@RequestMapping("/api/v1/skills")
public class SkillController {

    private static final Logger log = LoggerFactory.getLogger(SkillController.class);

    private static final String ERROR_CODE_RUNTIME_ABSENT = "ECOS-AI-AGENT-RUNTIME-ABSENT";
    private static final int CODE_RUNTIME_NOT_ASSEMBLED = 501;

    private final ObjectProvider<AgentRuntimeProvider> runtimeProvider;

    public SkillController(ObjectProvider<AgentRuntimeProvider> runtimeProvider) {
        this.runtimeProvider = runtimeProvider;
    }

    /** List all available skills, optionally filtered by category */
    @GetMapping
    public ApiResponse<Object> list(@RequestParam(required = false) String category) {
        AgentMcpCapability mcp;
        try {
            mcp = resolveMcp();
        } catch (AgentRuntimeCapabilityUnavailableException e) {
            return unavailable(e);
        }
        if (!mcp.isAvailable()) {
            return unavailable(new AgentRuntimeCapabilityUnavailableException(
                    AgentRuntimeCapability.MCP, "MCP channel not connected"));
        }
        Map<String, Object> args = new HashMap<>();
        if (category != null) {
            args.put("category", category);
        }
        String result = mcp.callTool("skills_list", args);
        return ApiResponse.success(result);
    }

    /** Health check */
    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> status = new LinkedHashMap<>();
        boolean available = false;
        try {
            available = resolveMcp().isAvailable();
        } catch (AgentRuntimeCapabilityUnavailableException e) {
            log.debug("skills health: MCP capability unavailable: {}", e.getReason());
        }
        status.put("available", available);
        return ApiResponse.success(status);
    }

    private AgentMcpCapability resolveMcp() {
        AgentRuntimeProvider provider = runtimeProvider.getIfAvailable();
        if (provider == null) {
            throw new AgentRuntimeCapabilityUnavailableException(AgentRuntimeCapability.MCP,
                    "AgentRuntimeProvider 未在本承流形态中装配（agent-service 不在 classpath 或未被组件扫描）");
        }
        return provider.mcp();
    }

    private ApiResponse<Object> unavailable(AgentRuntimeCapabilityUnavailableException e) {
        log.warn("skills 依赖的运行时能力不可用: capability={} reason={}", e.getCapability(), e.getReason());
        ApiResponse<Object> resp = ApiResponse.error(
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
