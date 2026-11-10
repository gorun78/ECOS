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
import com.chinacreator.gzcm.common.error.AiErrorClass;
import com.chinacreator.gzcm.engine.ai.AgentMeshService;
import com.chinacreator.gzcm.engine.ai.agent.mesh.entity.AgentRegistryEntity;
import com.chinacreator.gzcm.engine.ai.agent.mesh.entity.MissionEntity;
import com.chinacreator.gzcm.engine.ai.agent.mesh.entity.MissionTaskEntity;
import com.chinacreator.gzcm.engine.ai.agent.mesh.repository.AgentRegistryRepository;
import com.chinacreator.gzcm.engine.ai.agent.mesh.repository.MissionRepository;
import com.chinacreator.gzcm.engine.ai.agent.mesh.repository.MissionTaskRepository;
import com.chinacreator.gzcm.engine.ai.agent.mesh.MissionExecutionEngine;
import com.fasterxml.jackson.core.JsonProcessingException;
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

    /** P1-Copilot：内置智能体目录（保存后失效缓存，使工作台修改即时生效）。 */
    @Autowired(required = false)
    private com.chinacreator.gzcm.engine.ai.copilot.CopilotAgentCatalog copilotAgentCatalog;

    private void invalidateCopilotCatalog() {
        if (copilotAgentCatalog != null) {
            copilotAgentCatalog.invalidate();
        }
    }

    /** P1-8：MCP 工具发现（经 SPI 接缝，不直依赖任何 provider 类型） */
    @Autowired(required = false)
    private com.chinacreator.gzcm.runtime.llm.agent.spi.AgentRuntimeProvider runtimeProvider;

    // ═══════════════ MCP 工具发现（P1-8） ═══════════════

    /** GET /api/agent-mesh/mcp/tools — MCP tools/list（provider 不可用时如实 501） */
    @GetMapping("/mcp/tools")
    public ApiResponse<Object> listMcpTools() {
        if (runtimeProvider == null) {
            return ApiResponse.error(501, AiErrorClass.ERROR_CODE_RUNTIME_ABSENT,
                    "MCP provider 未装配（AgentRuntimeProvider 不在本承流形态）");
        }
        try {
            com.chinacreator.gzcm.runtime.llm.agent.spi.AgentMcpCapability mcp = runtimeProvider.mcp();
            if (!mcp.isAvailable()) {
                return ApiResponse.error(501, AiErrorClass.ERROR_CODE_RUNTIME_ABSENT,
                        "MCP 通道未连接");
            }
            return ApiResponse.success(mcp.listTools());
        } catch (com.chinacreator.gzcm.runtime.llm.agent.spi.AgentRuntimeCapabilityUnavailableException e) {
            return ApiResponse.error(501, AiErrorClass.ERROR_CODE_RUNTIME_ABSENT,
                    "MCP provider 不可用: " + e.getReason());
        }
    }

    // ═══════════════ Agent CRUD ═══════════════════

    /** 1. GET /api/agent-mesh/agents — 列出所有Agent */
    @GetMapping("/agents")
    public ApiResponse<List<AgentRegistryEntity>> listAgents() {
        if (agentRegistryRepo == null) return AiResponseSupport.dependencyUnavailable("AgentRegistryRepository");
        return ApiResponse.success(agentRegistryRepo.findAll());
    }

    /** 2. GET /api/agent-mesh/agents/{id} — 查询单个Agent */
    @GetMapping("/agents/{id}")
    public ApiResponse<AgentRegistryEntity> getAgent(@PathVariable String id) {
        if (agentRegistryRepo == null) return AiResponseSupport.dependencyUnavailable("AgentRegistryRepository");
        AgentRegistryEntity agent = agentRegistryRepo.findById(id);
        if (agent == null) return ApiResponse.notFound("Agent " + id + " 不存在");
        return ApiResponse.success(agent);
    }

    /** 3. POST /api/agent-mesh/agents — 注册Agent */
    @PostMapping("/agents")
    public ApiResponse<AgentRegistryEntity> createAgent(@RequestBody AgentRegistryEntity agent) {
        if (agentRegistryRepo == null) return AiResponseSupport.dependencyUnavailable("AgentRegistryRepository");
        // Wave-7 T-29 (R5): 防御空 body / null 字段, 避免 PG NOT NULL 违表 5xx
        if (agent == null) return ApiResponse.badRequest("请求体不能为空");
        if (agent.getName() == null || agent.getName().trim().isEmpty()) {
            return ApiResponse.badRequest("name 必填");
        }
        if (agent.getId() == null || agent.getId().trim().isEmpty()) {
            agent.setId(UUID.randomUUID().toString().replace("-", ""));
        }
        if (agent.getStatus() == null) agent.setStatus("ACTIVE");
        // ecos_agent_registry NOT NULL 列 (endpoint/metadata/capability/created_at/updated_at)
        if (agent.getEndpoint() == null) agent.setEndpoint("");
        if (agent.getCapability() == null) agent.setCapability("{}");
        if (agent.getMetadata() == null) agent.setMetadata("{}");
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        if (agent.getCreatedAt() == null) agent.setCreatedAt(now);
        if (agent.getUpdatedAt() == null) agent.setUpdatedAt(now);
        try {
            agentRegistryRepo.insert(agent);
        } catch (org.springframework.dao.DataAccessException ex) {
            log.error("Agent insert failed: id={}, name={}", agent.getId(), agent.getName(), ex);
            return ApiResponse.badRequest("Agent 注册失败：提交内容与现有记录冲突或字段不合法");
        }
        log.info("Agent registered: {} [{}]", agent.getId(), agent.getName());
        invalidateCopilotCatalog();
        return ApiResponse.success(agent);
    }

    /** 4. PUT /api/agent-mesh/agents/{id} — 更新Agent（P1-Copilot：与既有记录合并，禁整实体覆盖） */
    @PutMapping("/agents/{id}")
    public ApiResponse<AgentRegistryEntity> updateAgent(@PathVariable String id,
                                                         @RequestBody AgentRegistryEntity agent) {
        if (agentRegistryRepo == null) return AiResponseSupport.dependencyUnavailable("AgentRegistryRepository");
        if (agent == null) return ApiResponse.badRequest("请求体不能为空");
        AgentRegistryEntity existing = agentRegistryRepo.findById(id);
        if (existing == null) return ApiResponse.notFound("Agent " + id + " 不存在");
        AgentRegistryEntity merged = mergeForUpdate(existing, agent);
        try {
            agentRegistryRepo.update(merged);
        } catch (org.springframework.dao.DataAccessException ex) {
            log.error("Agent update failed: id={}", id, ex);
            return ApiResponse.badRequest("Agent 更新失败：提交内容与现有记录冲突或字段不合法");
        }
        invalidateCopilotCatalog();
        return ApiResponse.success(merged);
    }

    /** 5. DELETE /api/agent-mesh/agents/{id} — 删除Agent（内置智能体禁删，与 AIP 对齐） */
    @DeleteMapping("/agents/{id}")
    public ApiResponse<String> deleteAgent(@PathVariable String id) {
        if (agentRegistryRepo == null) return AiResponseSupport.dependencyUnavailable("AgentRegistryRepository");
        AgentRegistryEntity existing = agentRegistryRepo.findById(id);
        if (existing == null) return ApiResponse.notFound("Agent " + id + " 不存在");
        if ("builtin".equalsIgnoreCase(existing.getRole())) {
            return ApiResponse.forbidden("内置智能体不允许删除（可停用或编辑配置）");
        }
        int rows = agentRegistryRepo.delete(id);
        invalidateCopilotCatalog();
        return rows > 0 ? ApiResponse.success("Agent " + id + " 已删除")
                        : ApiResponse.notFound("Agent " + id + " 不存在");
    }

    /**
     * 更新合并：未传字段沿用旧值；metadata/capability 按 JSON 浅合并（空对象不覆盖）。
     *
     * <p>请求体经 {@code AgentRegistryEntity} 的派生 setter 已把 systemPrompt/model/description
     * 写入 incoming.metadata；此处再与 existing.metadata 合并，避免工作台一次保存抹掉
     * routes/rag/toolWhitelist 等键。</p>
     */
    private AgentRegistryEntity mergeForUpdate(AgentRegistryEntity existing, AgentRegistryEntity incoming) {
        AgentRegistryEntity merged = new AgentRegistryEntity();
        merged.setId(existing.getId());
        merged.setName(firstNonNull(incoming.getName(), existing.getName()));
        merged.setRole(firstNonNull(incoming.getRole(), existing.getRole()));
        merged.setStatus(firstNonNull(incoming.getStatus(), existing.getStatus()));
        merged.setEndpoint(firstNonNull(incoming.getEndpoint(), existing.getEndpoint()));
        merged.setCapability(mergeJson(existing.getCapability(), incoming.getCapability()));
        merged.setMetadata(mergeJson(existing.getMetadata(), incoming.getMetadata()));
        merged.setCreatedAt(existing.getCreatedAt());
        merged.setUpdatedAt(java.time.LocalDateTime.now());
        return merged;
    }

    private static String firstNonNull(String incoming, String existing) {
        return incoming != null && !incoming.isBlank() ? incoming : existing;
    }

    /** JSON 对象浅合并：incoming 为空/{} ⇒ 保留 existing；同名键 incoming 覆盖（异常上抛由 AiErrorAdvice 分类渲染）。 */
    private static String mergeJson(String existing, String incoming) {
        if (incoming == null || incoming.isBlank() || "{}".equals(incoming.trim())) {
            return existing;
        }
        if (existing == null || existing.isBlank() || "{}".equals(existing.trim())) {
            return incoming;
        }
        com.fasterxml.jackson.databind.JsonNode oldNode;
        com.fasterxml.jackson.databind.JsonNode newNode;
        try {
            oldNode = mapper.readTree(existing);
            newNode = mapper.readTree(incoming);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Agent metadata/capability 非法 JSON: " + e.getMessage(), e);
        }
        if (!(oldNode instanceof com.fasterxml.jackson.databind.node.ObjectNode oldObj)
                || !(newNode instanceof com.fasterxml.jackson.databind.node.ObjectNode newObj)) {
            return incoming;
        }
        oldObj.setAll(newObj);
        return oldObj.toString();
    }

    // ═══════════════ Mission CRUD + Execute ═════════

    /** 6. GET /api/agent-mesh/missions — 列出Mission */
    @GetMapping("/missions")
    public ApiResponse<List<MissionEntity>> listMissions(
            @RequestParam(defaultValue = "20") int limit) {
        if (missionRepo == null) return AiResponseSupport.dependencyUnavailable("MissionRepository");
        return ApiResponse.success(missionRepo.findRecent(Math.min(limit, 100)));
    }

    /** 7. GET /api/agent-mesh/missions/{id} — 查询Mission详情 */
    @GetMapping("/missions/{id}")
    public ApiResponse<Map<String, Object>> getMission(@PathVariable String id) {
        if (missionRepo == null) return AiResponseSupport.dependencyUnavailable("MissionRepository");
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
    public ApiResponse<MissionEntity> createMission(@RequestBody Map<String, Object> body)
            throws JsonProcessingException {
        if (missionRepo == null) return AiResponseSupport.dependencyUnavailable("MissionRepository");

        MissionEntity mission = new MissionEntity();
        mission.setId(UUID.randomUUID().toString().replace("-", ""));
        mission.setTitle((String) body.getOrDefault("title", "Untitled Mission"));
        mission.setDescription((String) body.getOrDefault("description", ""));
        mission.setMode((String) body.getOrDefault("mode", "SUPERVISOR"));
        mission.setStatus("PENDING");

        // F06-15：序列化失败不再落「{}」掩蔽，直接上抛经 AiErrorAdvice 分类
        Object params = body.get("inputParams");
        mission.setInputParams(params != null ? mapper.writeValueAsString(params) : "{}");
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

    /** 9. POST /api/agent-mesh/missions/{id}/execute — 执行Mission（执行期异常由 {@link AiErrorAdvice} 分类为 ECOS-AI-* 响应） */
    @PostMapping("/missions/{id}/execute")
    public ApiResponse<MissionEntity> executeMission(@PathVariable String id) throws Exception {
        if (missionEngine == null) return AiResponseSupport.dependencyUnavailable("MissionExecutionEngine");
        MissionEntity mission = missionRepo.findById(id);
        if (mission == null) return ApiResponse.notFound("Mission " + id + " 不存在");
        if (!"PENDING".equals(mission.getStatus())) {
            return ApiResponse.badRequest("Mission 状态为 " + mission.getStatus() + "，无法执行");
        }
        MissionEntity result = missionEngine.execute(mission);
        return ApiResponse.success(result);
    }

    /** 10. GET /api/agent-mesh/missions/{id}/tasks — 查询Mission子任务 */
    @GetMapping("/missions/{id}/tasks")
    public ApiResponse<List<MissionTaskEntity>> getMissionTasks(@PathVariable String id) {
        if (taskRepo == null) return AiResponseSupport.dependencyUnavailable("MissionTaskRepository");
        return ApiResponse.success(taskRepo.findByMissionId(id));
    }

    /** 11. POST /api/agent-mesh/route-intent — 意图路由 */
    @PostMapping("/route-intent")
    public ApiResponse<Map<String, Object>> routeIntent(@RequestBody Map<String, Object> req) {
        Map<String, Object> result = agentMeshService.routeIntent(req);
        return ApiResponse.success(result);
    }
}
