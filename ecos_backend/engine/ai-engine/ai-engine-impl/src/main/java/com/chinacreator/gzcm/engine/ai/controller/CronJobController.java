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
 * Cron Job Controller — CRUD operations backed by an external MCP provider's cronjob_* tools.
 *
 * <p>Endpoint: /api/v1/cron-jobs（路径与签名零变化，API 只增不改）</p>
 *
 * <p><b>详细设计-06 §八 接缝义务 1 + 2（M0 直改）</b>：本类原先 compile-time 依赖
 * {@code com.chinacreator.gzcm.services.agent.runtime.mcp.HermesMCPClient}。
 * 现只依赖 {@link AgentMcpCapability} SPI —— Hermes 降级为「可选外部 provider」，
 * 引擎侧不认识任何 Hermes 类型（铁律 §2.1，ARCH_SPEC C238/C257）。</p>
 *
 * <p><b>口径修正（仓库既有裁决：禁 200-with-empty-body 假成功）</b>：
 * provider/MCP 通道不可用时，旧实现返回 {@code code=0 + "[]"}（对前端伪装成「没有任务」）；
 * 现改为 {@code code=501 + data.stub=true} 显式回显未装配，成功路径的数据体（JSON 文本）不变。</p>
 *
 * <p><b>接缝义务 3（C237）提示</b>：Skill/Cron 的任务定义落 {@code ecos_ai.ecos_wagent_*}，
 * 调度投递归 runtime-task；本类只做外部 provider 通道代理，不内建 {@code @Scheduled}。</p>
 *
 * @author ecos-factory
 * @since 详细设计-06 §八 M0
 */
@RestController
@RequestMapping("/api/v1/cron-jobs")
public class CronJobController {

    private static final Logger log = LoggerFactory.getLogger(CronJobController.class);

    private static final String ERROR_CODE_RUNTIME_ABSENT = "ECOS-AI-AGENT-RUNTIME-ABSENT";
    private static final int CODE_RUNTIME_NOT_ASSEMBLED = 501;

    private final ObjectProvider<AgentRuntimeProvider> runtimeProvider;

    public CronJobController(ObjectProvider<AgentRuntimeProvider> runtimeProvider) {
        this.runtimeProvider = runtimeProvider;
    }

    /** List all cron jobs */
    @GetMapping
    public ApiResponse<Object> list() {
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
        try {
            String result = mcp.callTool("cronjob_list", null);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("cronjob_list failed: {}", e.getMessage());
            return ApiResponse.badRequest("Cron job list failed: " + e.getMessage());
        }
    }

    /** Create a new cron job */
    @PostMapping
    public ApiResponse<Object> create(@RequestBody Map<String, String> body) {
        String name = body.get("name");
        String schedule = body.get("schedule");
        String prompt = body.get("prompt");
        if (name == null || schedule == null || prompt == null) {
            return ApiResponse.badRequest("name, schedule, prompt required");
        }
        AgentMcpCapability mcp;
        try {
            mcp = resolveMcp();
        } catch (AgentRuntimeCapabilityUnavailableException e) {
            return unavailable(e);
        }
        try {
            Map<String, Object> args = new HashMap<>();
            args.put("name", name);
            args.put("schedule", schedule);
            args.put("prompt", prompt);
            String result = mcp.callTool("cronjob_create", args);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("cronjob_create failed: {}", e.getMessage());
            return ApiResponse.badRequest("Create failed: " + e.getMessage());
        }
    }

    /** Pause a cron job */
    @PostMapping("/{jobId}/pause")
    public ApiResponse<Object> pause(@PathVariable String jobId) {
        AgentMcpCapability mcp;
        try {
            mcp = resolveMcp();
        } catch (AgentRuntimeCapabilityUnavailableException e) {
            return unavailable(e);
        }
        try {
            Map<String, Object> args = new HashMap<>();
            args.put("job_id", jobId);
            String result = mcp.callTool("cronjob_pause", args);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("cronjob_pause failed: {}", e.getMessage());
            return ApiResponse.badRequest("Pause failed: " + e.getMessage());
        }
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
        log.warn("cron-jobs 依赖的运行时能力不可用: capability={} reason={}", e.getCapability(), e.getReason());
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
