package com.chinacreator.gzcm.engine.ai.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.runtime.llm.agent.spi.AgentRuntimeCapability;
import com.chinacreator.gzcm.runtime.llm.agent.spi.AgentRuntimeCapabilityUnavailableException;
import com.chinacreator.gzcm.runtime.llm.agent.spi.AgentRuntimeProvider;
import com.chinacreator.gzcm.runtime.llm.agent.spi.model.AgentExecutionPlan;
import com.chinacreator.gzcm.runtime.llm.agent.spi.model.AgentExecutionResult;
import com.chinacreator.gzcm.runtime.llm.agent.spi.model.AgentExecutionTask;
import com.chinacreator.gzcm.runtime.llm.agent.spi.model.AgentGoal;
import com.chinacreator.gzcm.runtime.llm.agent.spi.model.AgentMemoryContext;
import com.chinacreator.gzcm.runtime.llm.agent.spi.model.AgentMemoryLayer;
import com.chinacreator.gzcm.runtime.llm.agent.spi.model.AgentMemoryRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 对话入口 —— 记忆装配 / 计划 / 单任务执行 / 记忆回写。
 *
 * <p><b>详细设计-06 §八 接缝义务 1（M0 直改）</b>：本类原先直接
 * {@code import com.chinacreator.gzcm.services.agent.runtime.*}（铁律 §2.1 违例，
 * ARCH_SPEC C257）。现改为只依赖 {@link AgentRuntimeProvider} SPI（落
 * {@code runtime/llm-gateway}），provider 实现由 {@code services/agent-service} 提供、
 * 组装期注入。</p>
 *
 * <p>HTTP 路径 {@code POST /api/v1/agent/chat} 与响应体字段
 * （answer / thoughtChain / toolCalls / sources / sessionId）零变化（API 只增不改）。</p>
 *
 * <p><b>缺件口径</b>：当前承流切片没有 provider bean，或所需能力不可用时，
 * 返回体 code=501 + {@code data.stub=true} 显式回显，绝不返回 200 + 空数据。</p>
 *
 * @author ecos-factory
 * @since PMO-49（接缝重构：详细设计-06 §八 M0）
 */
@RestController
@RequestMapping("/api/v1/agent")
public class AgentChatController {

    private static final Logger log = LoggerFactory.getLogger(AgentChatController.class);

    /** 未装配错误码（增量字段，不改既有成功响应体）。 */
    private static final String ERROR_CODE_RUNTIME_ABSENT = "ECOS-AI-AGENT-RUNTIME-ABSENT";

    private static final int CODE_RUNTIME_NOT_ASSEMBLED = 501;

    private final ObjectProvider<AgentRuntimeProvider> runtimeProvider;

    public AgentChatController(ObjectProvider<AgentRuntimeProvider> runtimeProvider) {
        this.runtimeProvider = runtimeProvider;
    }

    @PostMapping("/chat")
    public ApiResponse<Map<String, Object>> chat(@RequestBody Map<String, Object> request) {
        AgentRuntimeProvider provider = runtimeProvider.getIfAvailable();
        if (provider == null) {
            return runtimeAbsent();
        }
        try {
            String agentId = (String) request.getOrDefault("agentId", "default");
            String message = (String) request.get("message");
            String sessionId = (String) request.getOrDefault("sessionId", UUID.randomUUID().toString());

            if (message == null || message.trim().isEmpty()) {
                return ApiResponse.badRequest("message 不能为空");
            }

            AgentMemoryContext ctx = provider.memory().buildContext(agentId, sessionId);
            log.debug("memory context assembled for agentId={} sessionId={} records={}",
                    agentId, sessionId, ctx == null ? 0 : ctx.totalRecords());

            AgentGoal goal = new AgentGoal();
            goal.setId(UUID.randomUUID().toString());
            goal.setDescription(message);
            AgentExecutionPlan plan = provider.planner().createPlan(goal);
            log.debug("plan created: planId={} tasks={}",
                    plan == null ? null : plan.getId(), plan == null ? 0 : plan.getTasks().size());

            AgentExecutionTask task = new AgentExecutionTask();
            task.setId(UUID.randomUUID().toString());
            task.setInstruction(message);
            task.setAgentId(agentId);
            task.setSessionId(sessionId);
            AgentExecutionResult result = provider.executor().execute(task);

            AgentMemoryRecord record = new AgentMemoryRecord();
            record.setAgentId(agentId);
            record.setSessionId(sessionId);
            record.setLayer(AgentMemoryLayer.WORKING);
            record.setContent(message + " -> " + result.getOutput());
            provider.memory().store(record);

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("answer", result.getOutput());
            resp.put("thoughtChain", List.of("Received: " + message, "Planned execution", "Executed"));
            resp.put("toolCalls", List.of());
            resp.put("sources", List.of());
            resp.put("sessionId", sessionId);

            return ApiResponse.success(resp);
        } catch (AgentRuntimeCapabilityUnavailableException e) {
            // F06-15：仅保留具体类型捕获；其余未捕获异常由 AiErrorAdvice 分类为 ECOS-AI-* 响应
            log.warn("Agent chat 依赖的运行时能力不可用: capability={} reason={}",
                    e.getCapability(), e.getReason());
            return capabilityUnavailable(e);
        }
    }

    /** provider bean 不在 classpath / 未被组件扫描 —— 显式 501 + stub。 */
    private ApiResponse<Map<String, Object>> runtimeAbsent() {
        ApiResponse<Map<String, Object>> resp = ApiResponse.error(
                CODE_RUNTIME_NOT_ASSEMBLED, ERROR_CODE_RUNTIME_ABSENT,
                "AgentRuntimeProvider 未在本承流形态中装配（agent-service 不在 classpath 或未被组件扫描）");
        resp.setData(stubBody(null, "no AgentRuntimeProvider bean"));
        return resp;
    }

    /** provider 在但能力不可用 —— 显式 501 + stub，禁止静默 no-op。 */
    private ApiResponse<Map<String, Object>> capabilityUnavailable(AgentRuntimeCapabilityUnavailableException e) {
        ApiResponse<Map<String, Object>> resp = ApiResponse.error(
                CODE_RUNTIME_NOT_ASSEMBLED, ERROR_CODE_RUNTIME_ABSENT,
                "Agent 运行时能力 " + e.getCapability() + " 不可用: " + e.getReason());
        resp.setData(stubBody(e.getCapability(), e.getReason()));
        return resp;
    }

    private static Map<String, Object> stubBody(AgentRuntimeCapability capability, String reason) {
        Map<String, Object> stub = new LinkedHashMap<>();
        stub.put("stub", true);
        stub.put("capability", capability == null ? null : capability.name());
        stub.put("reason", reason);
        return stub;
    }
}
