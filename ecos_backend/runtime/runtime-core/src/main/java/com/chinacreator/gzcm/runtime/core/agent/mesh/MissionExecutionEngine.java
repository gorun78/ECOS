package com.chinacreator.gzcm.runtime.core.agent.mesh;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.chinacreator.gzcm.runtime.core.agent.AgentResult;
import com.chinacreator.gzcm.runtime.core.agent.AgentRuntime;
import com.chinacreator.gzcm.runtime.core.agent.mesh.entity.AgentRegistryEntity;
import com.chinacreator.gzcm.runtime.core.agent.mesh.entity.MissionEntity;
import com.chinacreator.gzcm.runtime.core.agent.mesh.entity.MissionTaskEntity;
import com.chinacreator.gzcm.runtime.core.agent.mesh.repository.AgentRegistryRepository;
import com.chinacreator.gzcm.runtime.core.agent.mesh.repository.MissionRepository;
import com.chinacreator.gzcm.runtime.core.agent.mesh.repository.MissionTaskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Mission 执行引擎 — 多 Agent 协作核心。
 *
 * <p>支持两种模式：
 * <ul>
 *   <li><b>SUPERVISOR</b>（默认）：Coordinator Agent 拆解 Mission → 分派各 Specialist → 汇总结果</li>
 *   <li><b>PIPELINE</b>：Agent 按序执行，上游输出作为下游输入，最终 Agent 产出结果</li>
 * </ul>
 * </p>
 *
 * @author CDRC Design Team
 */
@Service
public class MissionExecutionEngine {

    private static final Logger log = LoggerFactory.getLogger(MissionExecutionEngine.class);
    private static final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @Autowired(required = false)
    private AgentRuntime agentRuntime;

    @Autowired(required = false)
    private AgentRegistryRepository agentRegistryRepo;

    @Autowired(required = false)
    private MissionRepository missionRepo;

    @Autowired(required = false)
    private MissionTaskRepository taskRepo;

    @Autowired(required = false)
    private AgentMessageBus messageBus;

    // ── SUPERVISOR 模式 ───────────────────────────

    /**
     * 执行 Mission（异步，由调用方决定是否等待）
     */
    public MissionEntity execute(MissionEntity mission) throws Exception {
        long startMs = System.currentTimeMillis();

        // 1. 标记开始
        mission.setStatus("RUNNING");
        mission.setStartedAt(LocalDateTime.now());
        missionRepo.updateStatus(mission.getId(), "RUNNING");

        try {
            if ("PIPELINE".equalsIgnoreCase(mission.getMode())) {
                executePipeline(mission);
            } else {
                executeSupervisor(mission);
            }

            // 2. 收集结果
            List<MissionTaskEntity> tasks = taskRepo.findByMissionId(mission.getId());
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("missionId", mission.getId());
            output.put("tasks", tasks);

            mission.setStatus("COMPLETED");
            mission.setOutputResult(mapper.writeValueAsString(output));
        } catch (Exception e) {
            log.error("Mission {} failed: {}", mission.getId(), e.getMessage());
            mission.setStatus("FAILED");
            mission.setErrorMessage(e.getMessage());
        }

        long duration = System.currentTimeMillis() - startMs;
        mission.setCompletedAt(LocalDateTime.now());
        mission.setDurationMs(duration);
        missionRepo.updateCompleted(mission.getId(), mission.getStatus(),
                mission.getResult(), mission.getCompletedAt());

        return mission;
    }

    // ── SUPERVISOR 模式 ───────────────────────────

    private void executeSupervisor(MissionEntity mission) throws Exception {
        log.info("Executing SUPERVISOR mission: {}", mission.getId());

        // 获取所有活跃 Agent
        List<AgentRegistryEntity> agents = agentRegistryRepo.findActive();
        if (agents.isEmpty()) {
            throw new IllegalStateException("无可用 Agent，请先注册 Agent");
        }

        // Coordinator 拆解任务
        String planPrompt = buildCoordinatorPrompt(mission, agents);
        String sessionId = agentRuntime.createSession("Coordinator", planPrompt).getId();
        AgentResult planResult = agentRuntime.run(sessionId, mission.getDescription());

        if (!planResult.isSuccess() || planResult.getFinalAnswer() == null) {
            throw new RuntimeException("Coordinator 任务拆解失败: " +
                    (planResult.getErrorMessage() != null ? planResult.getErrorMessage() : "无输出"));
        }
        agentRuntime.closeSession(sessionId);

        // 解析 Coordinator 输出 → Agent 分派
        // 格式: AGENT:ag-data|指令内容
        String[] lines = planResult.getFinalAnswer().split("\n");
        int seq = 0;
        for (String line : lines) {
            line = line.trim();
            if (!line.startsWith("AGENT:") && !line.contains("|")) continue;

            String[] parts = line.substring(line.indexOf(":") + 1).split("\\|", 2);
            String agentId = parts[0].trim();
            String instruction = parts.length > 1 ? parts[1].trim() : line;

            AgentRegistryEntity agent = agentRegistryRepo.findById(agentId);
            if (agent == null) {
                log.warn("Coordinator 指定了未知 Agent: {}, 跳过", agentId);
                continue;
            }

            MissionTaskEntity task = new MissionTaskEntity();
            task.setId(UUID.randomUUID().toString().replace("-", ""));
            task.setMissionId(mission.getId());
            task.setSeq(seq++);
            task.setAgentId(agentId);
            task.setAgentName(agent.getName());
            task.setInstruction(instruction);
            task.setStatus("PENDING");
            taskRepo.insert(task);

            // 执行 Specialist
            executeSpecialistTask(task, agent);
        }
    }

    // ── PIPELINE 模式 ──────────────────────────────

    private void executePipeline(MissionEntity mission) throws Exception {
        log.info("Executing PIPELINE mission: {}", mission.getId());

        // PIPELINE 模式下，task 已在 Controller 层预先创建并指定 agent 和 seq
        List<MissionTaskEntity> tasks = taskRepo.findByMissionId(mission.getId());
        if (tasks.isEmpty()) {
            throw new IllegalStateException("PIPELINE 模式下必须预创建子任务");
        }

        String previousOutput = mission.getDescription();

        for (MissionTaskEntity task : tasks) {
            AgentRegistryEntity agent = agentRegistryRepo.findById(task.getAgentId());
            if (agent == null) {
                task.setStatus("FAILED");
                task.setErrorMessage("Agent " + task.getAgentId() + " 未注册");
                continue;
            }

            // 将上游输出注入当前任务指令
            String enrichedInstruction = task.getInstruction();
            if (previousOutput != null && !previousOutput.isEmpty()) {
                enrichedInstruction = task.getInstruction() + "\n\n【上游Agent输出】\n" + previousOutput;
            }

            task.setInstruction(enrichedInstruction);
            executeSpecialistTask(task, agent);

            if ("COMPLETED".equals(task.getStatus()) && task.getResultSummary() != null) {
                previousOutput = task.getResultSummary();
            }
        }
    }

    // ── Specialist 执行 ────────────────────────────

    private void executeSpecialistTask(MissionTaskEntity task, AgentRegistryEntity agent) {
        long taskStart = System.currentTimeMillis();
        task.setStatus("RUNNING");
        taskRepo.updateStatus(task.getId(), "RUNNING");

        try {
            String sessionId = agentRuntime.createSession(
                    agent.getName() + " - " + task.getId(),
                    agent.getSystemPrompt()).getId();

            AgentResult result = agentRuntime.run(sessionId, task.getInstruction());

            if (result.isSuccess()) {
                task.setStatus("COMPLETED");
                task.setResultSummary(result.getFinalAnswer());
                Map<String, Object> detail = new LinkedHashMap<>();
                detail.put("totalTokens", result.getTotalTokens());
                task.setResultDetail(mapper.writeValueAsString(detail));
            } else {
                task.setStatus("FAILED");
                task.setErrorMessage(result.getErrorMessage());
            }

            agentRuntime.closeSession(sessionId);
        } catch (Exception e) {
            log.error("Task {} agent={} failed", task.getId(), agent.getId(), e);
            task.setStatus("FAILED");
            task.setErrorMessage(e.getClass().getSimpleName() + ": " + e.getMessage());
        }

        task.setDurationMs(System.currentTimeMillis() - taskStart);
        task.setFinishedAt(LocalDateTime.now());
        taskRepo.updateCompleted(task.getId(), task.getStatus(),
                task.getResult(), task.getFinishedAt());
    }

    // ── Coordinator Prompt ─────────────────────────

    private String buildCoordinatorPrompt(MissionEntity mission, List<AgentRegistryEntity> agents) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个任务协调器(Coordinator)。你的职责是将用户的Mission拆解为多个子任务，分配给合适的专家Agent。\n\n");
        sb.append("## 可用的专家Agent\n");
        for (AgentRegistryEntity a : agents) {
            sb.append(String.format("- **%s** [%s] %s: %s\n",
                    a.getId(), a.getRole(), a.getName(), a.getDescription()));
        }
        sb.append("\n## 输出格式\n");
        sb.append("每行一个子任务，格式: AGENT:<agentId>|<指令内容>\n");
        sb.append("示例:\n");
        sb.append("AGENT:ag-data|从数据库提取所有供应商记录，检查数据完整性\n");
        sb.append("AGENT:ag-knowledge|检索供应商合规政策法规和历史案例\n");
        sb.append("AGENT:ag-compliance|根据上述分析结果，逐项审查合规性并给出风险等级\n\n");
        sb.append("请拆解以下Mission:\n");
        return sb.toString();
    }

    public AgentMessageBus getMessageBus() { return messageBus; }
}
