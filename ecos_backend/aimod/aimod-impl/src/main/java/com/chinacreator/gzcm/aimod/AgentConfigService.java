package com.chinacreator.gzcm.aimod;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.chinacreator.gzcm.runtime.hermes.HermesEngine;
import com.chinacreator.gzcm.runtime.hermes.model.ProfileConfig;
import com.chinacreator.gzcm.runtime.hermes.repository.ProfileConfigRepository;
import com.chinacreator.gzcm.runtime.hermes.scheduler.AgentResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Agent 配置业务服务 — 将 Entity 转为 Map 返回给 Controller
 * <p>
 * 遵循 WorkflowService / OntologyService 模式，提供 Agent 配置 CRUD 业务逻辑。
 * </p>
 */
@Service
public class AgentConfigService {

    private static final Logger log = LoggerFactory.getLogger(AgentConfigService.class);
    private static final AtomicInteger ID_SEQ = new AtomicInteger(100);

    private static final String TEST_SUBSYSTEM = "sysman";
    private static final String TEST_PROFILE_PREFIX = "agent-test-";

    private final AgentRepository repository;
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private HermesEngine hermesEngine;

    @Autowired(required = false)
    private ProfileConfigRepository profileConfigRepository;

    public AgentConfigService(AgentRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    private String nextId() {
        return "agent-" + ID_SEQ.incrementAndGet();
    }

    /**
     * 列出所有 Agent（默认 100 条）
     */
    public List<Map<String, Object>> listAgents(int pageSize) {
        return repository.findAll(pageSize).stream()
            .map(this::toMap)
            .toList();
    }

    public long totalCount() {
        return repository.count();
    }

    /**
     * 按 ID 获取 Agent 详情
     */
    public Optional<Map<String, Object>> getAgent(String id) {
        return repository.findById(id).map(this::toMap);
    }

    /**
     * 创建 Agent
     */
    public Map<String, Object> createAgent(Map<String, Object> body) {
        AgentEntity entity = new AgentEntity();
        String id = nextId();
        entity.setId(id);
        entity.setName(String.valueOf(body.getOrDefault("name", "新 Agent")));
        entity.setModelProvider(String.valueOf(body.getOrDefault("modelProvider", "deepseek")));
        entity.setModelName(String.valueOf(body.getOrDefault("modelName", "deepseek-v4-flash")));
        entity.setSystemPrompt(String.valueOf(body.getOrDefault("systemPrompt", "")));
        entity.setTools(toJsonString(body.getOrDefault("tools", List.of())));
        entity.setKnowledge(toJsonString(body.getOrDefault("knowledge", List.of())));
        entity.setStatus("draft");
        repository.insert(entity);
        log.info("Agent created: {} [{}]", id, entity.getName());
        return toMap(entity);
    }

    /**
     * 更新 Agent
     */
    public Optional<Map<String, Object>> updateAgent(String id, Map<String, Object> body) {
        Optional<AgentEntity> existing = repository.findById(id);
        if (existing.isEmpty()) return Optional.empty();

        String name = body.containsKey("name") ? String.valueOf(body.get("name")) : null;
        String modelProvider = body.containsKey("modelProvider") ? String.valueOf(body.get("modelProvider")) : null;
        String modelName = body.containsKey("modelName") ? String.valueOf(body.get("modelName")) : null;
        String systemPrompt = body.containsKey("systemPrompt") ? String.valueOf(body.get("systemPrompt")) : null;
        String tools = body.containsKey("tools") ? toJsonString(body.get("tools")) : null;
        String knowledge = body.containsKey("knowledge") ? toJsonString(body.get("knowledge")) : null;
        String status = body.containsKey("status") ? String.valueOf(body.get("status")) : null;

        repository.update(id, name, modelProvider, modelName, systemPrompt, tools, knowledge, status);
        return repository.findById(id).map(this::toMap);
    }

    /**
     * 绑定工具列表
     */
    public Optional<Map<String, Object>> bindTools(String id, Object toolsObj) {
        Optional<AgentEntity> existing = repository.findById(id);
        if (existing.isEmpty()) return Optional.empty();
        String toolsJson = toJsonString(toolsObj);
        repository.updateTools(id, toolsJson);
        return repository.findById(id).map(this::toMap);
    }

    /**
     * 绑定知识库
     */
    public Optional<Map<String, Object>> bindKnowledge(String id, Object knowledgeObj) {
        Optional<AgentEntity> existing = repository.findById(id);
        if (existing.isEmpty()) return Optional.empty();
        String knowledgeJson = toJsonString(knowledgeObj);
        repository.updateKnowledge(id, knowledgeJson);
        return repository.findById(id).map(this::toMap);
    }

    /**
     * 发布 Agent
     */
    public Optional<Map<String, Object>> publishAgent(String id) {
        Optional<AgentEntity> existing = repository.findById(id);
        if (existing.isEmpty()) return Optional.empty();
        repository.publish(id);
        log.info("Agent published: {}", id);
        return repository.findById(id).map(this::toMap);
    }

    /**
     * 删除 Agent
     */
    public boolean deleteAgent(String id) {
        return repository.deleteById(id) > 0;
    }

    /**
     * 测试 Agent — 通过 HermesEngine 执行真实对话
     * <p>
     * 1. 根据 Agent 配置创建临时 ProfileConfig
     * 2. 持久化到 DB 并刷新引擎缓存
     * 3. 通过 HermesEngine 执行用户消息
     * 4. 清理临时 Profile
     * </p>
     */
    public Map<String, Object> testAgent(String id, Map<String, Object> body) {
        // 1. 获取 Agent 实体
        AgentEntity agent = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Agent " + id + " 不存在"));

        String message = (String) body.getOrDefault("message", "你好");
        String tempProfileName = TEST_PROFILE_PREFIX + UUID.randomUUID().toString().substring(0, 8);

        // 2. 如果 HermesEngine 不可用，返回降级信息
        if (hermesEngine == null || profileConfigRepository == null) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("agentId", id);
            result.put("status", "unavailable");
            result.put("message", message);
            result.put("response", "Hermes 引擎未就绪，无法执行测试。请确认 hermes-engine 服务已正确配置。");
            return result;
        }

        try {
            // 3. 构建临时 ProfileConfig
            ProfileConfig profile = buildTestProfile(agent, tempProfileName);

            // 4. 持久化到 DB
            profileConfigRepository.insert(profile);

            // 5. 刷新引擎缓存
            hermesEngine.refreshProfileCache(TEST_SUBSYSTEM);

            // 6. 通过 HermesEngine 执行
            AgentResult result = hermesEngine.execute(TEST_SUBSYSTEM, tempProfileName, message);

            // 7. 映射返回值
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("agentId", id);
            response.put("sessionId", result.getSessionId());
            response.put("status", result.isSuccess() ? "completed" : "failed");
            response.put("success", result.isSuccess());
            response.put("message", message);
            response.put("response", result.getContent());
            response.put("tokensInput", result.getTokensInput());
            response.put("tokensOutput", result.getTokensOutput());
            response.put("durationMs", result.getDurationMs());
            if (!result.isSuccess()) {
                response.put("errorMsg", result.getErrorMsg());
            }

            log.info("Agent test execution completed: id={}, success={}, duration={}ms",
                    id, result.isSuccess(), result.getDurationMs());
            return response;

        } catch (Exception e) {
            log.error("Agent test execution failed: id={}", id, e);
            Map<String, Object> errorResult = new LinkedHashMap<>();
            errorResult.put("agentId", id);
            errorResult.put("status", "error");
            errorResult.put("message", message);
            errorResult.put("response", "Agent 测试执行失败: " + e.getMessage());
            errorResult.put("durationMs", 0);
            return errorResult;
        } finally {
            // 8. 清理临时 Profile
            try {
                profileConfigRepository.deleteById(tempProfileName);
                hermesEngine.refreshProfileCache(TEST_SUBSYSTEM);
            } catch (Exception e) {
                log.warn("Failed to clean up test profile: {}", tempProfileName, e);
            }
        }
    }

    /**
     * 根据 Agent 实体构建临时测试用的 ProfileConfig
     */
    private ProfileConfig buildTestProfile(AgentEntity agent, String profileName) {
        return ProfileConfig.builder()
                .id(profileName)
                .profileName(profileName)
                .subsystem(TEST_SUBSYSTEM)
                .enabled(true)
                .description("Temporary profile for Agent [" + agent.getId() + "] test")
                .provider(agent.getModelProvider() != null ? agent.getModelProvider() : "deepseek")
                .model(agent.getModelName() != null ? agent.getModelName() : "deepseek-v4-flash")
                .systemPrompt(agent.getSystemPrompt() != null ? agent.getSystemPrompt() : "")
                .allowedTools(agent.getTools() != null ? agent.getTools() : "[]")
                .toolsEnabled(true)
                .temperature(0.7)
                .maxTokens(4096)
                .sessionTimeoutSec(300)
                .build();
    }

    /**
     * 获取 Prompt 版本列表 — 模拟数据
     */
    public List<Map<String, Object>> listPromptVersions(String agentId) {
        List<Map<String, Object>> versions = new ArrayList<>();
        Map<String, Object> v1 = new LinkedHashMap<>();
        v1.put("version", "v1.0");
        v1.put("content", "当前使用的系统提示词");
        v1.put("createdAt", LocalDateTime.now().toString());
        versions.add(v1);
        return versions;
    }

    private Map<String, Object> toMap(AgentEntity entity) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", entity.getId());
        map.put("name", entity.getName());
        map.put("modelProvider", entity.getModelProvider());
        map.put("modelName", entity.getModelName());
        map.put("systemPrompt", entity.getSystemPrompt());
        map.put("tools", parseJsonArray(entity.getTools()));
        map.put("knowledge", parseJsonArray(entity.getKnowledge()));
        map.put("status", entity.getStatus());
        map.put("createdAt", entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null);
        map.put("updatedAt", entity.getUpdatedAt() != null ? entity.getUpdatedAt().toString() : null);
        return map;
    }

    @SuppressWarnings("unchecked")
    private List<String> parseJsonArray(String json) {
        if (json == null || json.isBlank() || json.equals("[]")) return List.of();
        try {
            return objectMapper.readValue(json, List.class);
        } catch (Exception e) {
            log.warn("Failed to parse JSON array: {}", json, e);
            return List.of();
        }
    }

    private String toJsonString(Object obj) {
        if (obj instanceof String s) {
            // If it's already a JSON string, use as-is
            if (s.trim().startsWith("[")) return s;
            return "[\"" + s + "\"]";
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize to JSON: {}", obj, e);
            return "[]";
        }
    }
}
