package com.chinacreator.gzcm.engine.ai.controller;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.ai.AgentMeshService;
import com.chinacreator.gzcm.runtime.core.agent.mesh.entity.AgentRegistryEntity;
import com.chinacreator.gzcm.runtime.core.agent.mesh.entity.MissionEntity;
import com.chinacreator.gzcm.runtime.core.agent.mesh.entity.MissionTaskEntity;
import com.chinacreator.gzcm.runtime.core.agent.mesh.repository.AgentRegistryRepository;
import com.chinacreator.gzcm.runtime.core.agent.mesh.repository.MissionRepository;
import com.chinacreator.gzcm.runtime.core.agent.mesh.repository.MissionTaskRepository;
import com.chinacreator.gzcm.runtime.core.agent.mesh.MissionExecutionEngine;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Agent Mesh 控制器 — 多Agent协作平台 REST API。
 *
 * <h3>10 个端点：</h3>
 * <ol>
 *   <li>GET    /api/agent-mesh/agents           — 列出所有Agent</li>
 *   <li>GET    /api/agent-mesh/agents/{id}       — 查询单个Agent</li>
 *   <li>POST   /api/agent-mesh/agents            — 注册Agent</li>
 *   <li>PUT    /api/agent-mesh/agents/{id}       — 更新Agent</li>
 *   <li>DELETE /api/agent-mesh/agents/{id}       — 删除Agent</li>
 *   <li>GET    /api/agent-mesh/missions          — 列出Mission</li>
 *   <li>GET    /api/agent-mesh/missions/{id}     — 查询Mission详情(含子任务)</li>
 *   <li>POST   /api/agent-mesh/missions          — 创建Mission</li>
 *   <li>POST   /api/agent-mesh/missions/{id}/execute — 执行Mission</li>
 *   <li>GET    /api/agent-mesh/missions/{id}/tasks   — 查询Mission子任务</li>
 * </ol>
 *
 * @author CDRC Design Team
 */
@RestController
@RequestMapping("/api/agent-mesh")
public class AgentMeshController {

    private static final Logger log = LoggerFactory.getLogger(AgentMeshController.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    @Autowired(required = false)
    private AgentRegistryRepository agentRegistryRepo;

    @Autowired(required = false)
    private MissionRepository missionRepo;

    @Autowired(required = false)
    private MissionTaskRepository taskRepo;

    @Autowired(required = false)
    private MissionExecutionEngine missionEngine;

    @Autowired
    private AgentMeshService agentMeshService;

    // ═══════════════ Agent CRUD ═══════════════════

    /** 1. GET /api/agent-mesh/agents — 列出所有Agent */
    @GetMapping("/agents")
    public ApiResponse<List<AgentRegistryEntity>> listAgents() {
        if (agentRegistryRepo == null) return ApiResponse.internalError("AgentRegistryRepository 未就绪");
        return ApiResponse.success(agentRegistryRepo.findAll());
    }

    /** 2. GET /api/agent-mesh/agents/{id} — 查询单个Agent */
    @GetMapping("/agents/{id}")
    public ApiResponse<AgentRegistryEntity> getAgent(@PathVariable String id) {
        if (agentRegistryRepo == null) return ApiResponse.internalError("AgentRegistryRepository 未就绪");
        AgentRegistryEntity agent = agentRegistryRepo.findById(id);
        if (agent == null) return ApiResponse.notFound("Agent " + id + " 不存在");
        return ApiResponse.success(agent);
    }

    /** 3. POST /api/agent-mesh/agents — 注册Agent */
    @PostMapping("/agents")
    public ApiResponse<AgentRegistryEntity> createAgent(@RequestBody AgentRegistryEntity agent) {
        if (agentRegistryRepo == null) return ApiResponse.internalError("AgentRegistryRepository 未就绪");
        if (agent.getId() == null || agent.getId().trim().isEmpty()) {
            agent.setId(UUID.randomUUID().toString().replace("-", ""));
        }
        if (agent.getStatus() == null) agent.setStatus("ACTIVE");
        agentRegistryRepo.insert(agent);
        log.info("Agent registered: {} [{}]", agent.getId(), agent.getName());
        return ApiResponse.success(agent);
    }

    /** 4. PUT /api/agent-mesh/agents/{id} — 更新Agent */
    @PutMapping("/agents/{id}")
    public ApiResponse<AgentRegistryEntity> updateAgent(@PathVariable String id,
                                                         @RequestBody AgentRegistryEntity agent) {
        if (agentRegistryRepo == null) return ApiResponse.internalError("AgentRegistryRepository 未就绪");
        AgentRegistryEntity existing = agentRegistryRepo.findById(id);
        if (existing == null) return ApiResponse.notFound("Agent " + id + " 不存在");
        agent.setId(id);
        agentRegistryRepo.update(agent);
        return ApiResponse.success(agent);
    }

    /** 5. DELETE /api/agent-mesh/agents/{id} — 删除Agent */
    @DeleteMapping("/agents/{id}")
    public ApiResponse<String> deleteAgent(@PathVariable String id) {
        if (agentRegistryRepo == null) return ApiResponse.internalError("AgentRegistryRepository 未就绪");
        int rows = agentRegistryRepo.delete(id);
        return rows > 0 ? ApiResponse.success("Agent " + id + " 已删除")
                        : ApiResponse.notFound("Agent " + id + " 不存在");
    }

    // ═══════════════ Mission CRUD + Execute ═════════

    /** 6. GET /api/agent-mesh/missions — 列出Mission */
    @GetMapping("/missions")
    public ApiResponse<List<MissionEntity>> listMissions(
            @RequestParam(defaultValue = "20") int limit) {
        if (missionRepo == null) return ApiResponse.internalError("MissionRepository 未就绪");
        return ApiResponse.success(missionRepo.findRecent(Math.min(limit, 100)));
    }

    /** 7. GET /api/agent-mesh/missions/{id} — 查询Mission详情 */
    @GetMapping("/missions/{id}")
    public ApiResponse<Map<String, Object>> getMission(@PathVariable String id) {
        if (missionRepo == null) return ApiResponse.internalError("MissionRepository 未就绪");
        MissionEntity mission = missionRepo.findById(id);
        if (mission == null) return ApiResponse.notFound("Mission " + id + " 不存在");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mission", mission);
        if (taskRepo != null) {
            result.put("tasks", taskRepo.findByMissionId(id));
        }
        return ApiResponse.success(result);
    }

    /** 8. POST /api/agent-mesh/missions — 创建Mission */
    @PostMapping("/missions")
    public ApiResponse<MissionEntity> createMission(@RequestBody Map<String, Object> body) {
        if (missionRepo == null) return ApiResponse.internalError("MissionRepository 未就绪");

        MissionEntity mission = new MissionEntity();
        mission.setId(UUID.randomUUID().toString().replace("-", ""));
        mission.setTitle((String) body.getOrDefault("title", "Untitled Mission"));
        mission.setDescription((String) body.getOrDefault("description", ""));
        mission.setMode((String) body.getOrDefault("mode", "SUPERVISOR"));
        mission.setStatus("PENDING");

        try {
            Object params = body.get("inputParams");
            mission.setInputParams(params != null ? mapper.writeValueAsString(params) : "{}");
        } catch (Exception e) {
            mission.setInputParams("{}");
        }
        missionRepo.insert(mission);

        // PIPELINE 模式：解析 tasks 数组预创建子任务
        if ("PIPELINE".equalsIgnoreCase(mission.getMode())) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> tasks = (List<Map<String, Object>>) body.get("tasks");
            if (tasks != null && taskRepo != null) {
                int seq = 0;
                for (Map<String, Object> t : tasks) {
                    MissionTaskEntity task = new MissionTaskEntity();
                    task.setId(UUID.randomUUID().toString().replace("-", ""));
                    task.setMissionId(mission.getId());
                    task.setSeq(seq++);
                    task.setAgentId((String) t.get("agentId"));
                    task.setAgentName((String) t.get("agentName"));
                    task.setInstruction((String) t.get("instruction"));
                    task.setStatus("PENDING");
                    taskRepo.insert(task);
                }
            }
        }

        log.info("Mission created: {} [{}]", mission.getId(), mission.getMode());
        return ApiResponse.success(mission);
    }

    /** 9. POST /api/agent-mesh/missions/{id}/execute — 执行Mission */
    @PostMapping("/missions/{id}/execute")
    public ApiResponse<MissionEntity> executeMission(@PathVariable String id) {
        if (missionEngine == null) return ApiResponse.internalError("MissionExecutionEngine 未就绪");
        MissionEntity mission = missionRepo.findById(id);
        if (mission == null) return ApiResponse.notFound("Mission " + id + " 不存在");
        if (!"PENDING".equals(mission.getStatus())) {
            return ApiResponse.badRequest("Mission 状态为 " + mission.getStatus() + "，无法执行");
        }
        try {
            MissionEntity result = missionEngine.execute(mission);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("Mission {} 执行失败", id, e);
            return ApiResponse.internalError("执行失败: " + e.getMessage());
        }
    }

    /** 10. GET /api/agent-mesh/missions/{id}/tasks — 查询Mission子任务 */
    @GetMapping("/missions/{id}/tasks")
    public ApiResponse<List<MissionTaskEntity>> getMissionTasks(@PathVariable String id) {
        if (taskRepo == null) return ApiResponse.internalError("MissionTaskRepository 未就绪");
        return ApiResponse.success(taskRepo.findByMissionId(id));
    }

    /** 11. POST /api/agent-mesh/route-intent — 意图路由 */
    @PostMapping("/route-intent")
    public ApiResponse<Map<String, Object>> routeIntent(@RequestBody Map<String, Object> req) {
        Map<String, Object> result = agentMeshService.routeIntent(req);
        return ApiResponse.success(result);
    }
}
