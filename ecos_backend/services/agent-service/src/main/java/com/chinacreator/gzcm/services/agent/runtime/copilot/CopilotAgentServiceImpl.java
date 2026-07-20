package com.chinacreator.gzcm.services.agent.runtime.copilot;

import com.chinacreator.gzcm.common.service.ICopilotAgentService;
import com.chinacreator.gzcm.services.agent.runtime.executor.ExecutorService;
import com.chinacreator.gzcm.services.agent.runtime.memory.MemoryService;
import com.chinacreator.gzcm.services.agent.runtime.model.*;
import com.chinacreator.gzcm.services.agent.runtime.planner.PlannerService;
import com.chinacreator.gzcm.services.agent.runtime.toolrouter.ToolRouterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CopilotAgentServiceImpl implements ICopilotAgentService {

    private static final Logger log = LoggerFactory.getLogger(CopilotAgentServiceImpl.class);

    private final ToolRouterService toolRouterService;
    private final PlannerService plannerService;
    private final ExecutorService executorService;
    private final MemoryService memoryService;
    private final JdbcTemplate jdbcTemplate;

    public CopilotAgentServiceImpl(ToolRouterService toolRouterService,
                                    PlannerService plannerService,
                                    ExecutorService executorService,
                                    MemoryService memoryService,
                                    JdbcTemplate jdbcTemplate) {
        this.toolRouterService = toolRouterService;
        this.plannerService = plannerService;
        this.executorService = executorService;
        this.memoryService = memoryService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Map<String, Object> chat(String agentId, String userMessage, String sessionId) {
        log.info("Copilot chat: agentId={}, sessionId={}, message={}", agentId, sessionId, userMessage);

        MemoryContext ctx = memoryService.buildContext(agentId, sessionId);

        Goal goal = new Goal();
        goal.setId(UUID.randomUUID().toString());
        goal.setDescription(userMessage);
        plannerService.createPlan(goal);

        ExecutionTask task = new ExecutionTask();
        task.setId(UUID.randomUUID().toString());
        task.setInstruction(userMessage);
        task.setAgentId(agentId);
        ExecutionResult result = executorService.execute(task);

        MemoryRecord record = new MemoryRecord();
        record.setId(UUID.randomUUID().toString());
        record.setAgentId(agentId);
        record.setSessionId(sessionId);
        record.setLayer(MemoryLayer.WORKING);
        record.setContent(userMessage);
        memoryService.store(record);

        return Map.of(
            "answer", result.getOutput() != null ? result.getOutput() : "",
            "thoughtChain", List.of("Received: " + userMessage, "Planned", "Executed"),
            "toolCalls", List.of(),
            "sources", List.of(),
            "sessionId", sessionId
        );
    }

    @Override
    public List<Map<String, Object>> getQuickQuestions(String agentId) {
        Map<String, List<String>> presets = Map.of(
            "agent-data", List.of("有哪些数据源？", "XX表结构是什么？", "数据质量报告", "跑一下同步管道"),
            "agent-ontology", List.of("有哪些对象类型？", "供应商对象有哪些属性？", "合同和供应商什么关系？"),
            "agent-knowledge", List.of("搜索供应商风险", "查找因果链", "知识图谱路径"),
            "agent-security", List.of("谁能看财务数据？", "最近有没有越权访问？", "审计日志"),
            "agent-scenario", List.of("如果换供应商影响多大？", "仿真推演", "风险分析")
        );
        List<String> questions = presets.getOrDefault(agentId, List.of("你好，有什么可以帮你？"));
        return questions.stream()
            .map(q -> { Map<String, Object> m = new java.util.HashMap<>(); m.put("question", q); m.put("agentId", agentId); return m; })
            .collect(Collectors.toList());
    }
}
