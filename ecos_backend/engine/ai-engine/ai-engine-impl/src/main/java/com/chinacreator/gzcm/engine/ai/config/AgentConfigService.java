package com.chinacreator.gzcm.engine.ai.config;

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

import com.chinacreator.gzcm.engine.ai.dao.AgentDao;
import com.chinacreator.gzcm.runtime.llm.LLMGatewayService;
import com.chinacreator.gzcm.runtime.llm.model.ProfileConfig;
import com.chinacreator.gzcm.runtime.llm.repository.ProfileConfigRepository;
import com.chinacreator.gzcm.runtime.llm.scheduler.AgentResult;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Agent 配置业务服务 — 将 Entity 转为强类型行/回执返回给 Controller。
 *
 * <p><b>#147DA 行面强类型</b>：列表行 10 键、Prompt 版本行 3 键、测试回执（三种分态并集，
 * 条件键由属性级 NON_NULL 保持）改 record。请求体仍为自由 Map——创建/更新字段按
 * {@code containsKey}/{@code getOrDefault} 语义透传，属调用方定义面。</p>
 */
@Service
public class AgentConfigService {

    private static final Logger log = LoggerFactory.getLogger(AgentConfigService.class);
    private static final AtomicInteger ID_SEQ = new AtomicInteger(100);

    private static final String TEST_SUBSYSTEM = "sysman";
    private static final String TEST_PROFILE_PREFIX = "agent-test-";

    private final AgentDao repository;
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private LLMGatewayService llmGatewayService;

    @Autowired(required = false)
    private ProfileConfigRepository profileConfigRepository;

    public AgentConfigService(AgentDao repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /** Agent 行（10 键；tools/knowledge 为 JSON 数组还原后的字符串列表）。 */
    public record AgentRow(@JsonProperty("id") String id,
                           @JsonProperty("name") String name,
                           @JsonProperty("modelProvider") String modelProvider,
                           @JsonProperty("modelName") String modelName,
                           @JsonProperty("systemPrompt") String systemPrompt,
                           @JsonProperty("tools") List<String> tools,
                           @JsonProperty("knowledge") List<String> knowledge,
                           @JsonProperty("status") String status,
                           @JsonProperty("createdAt") String createdAt,
                           @JsonProperty("updatedAt") String updatedAt) {
    }

    /** Prompt 版本行（3 键）。 */
    public record PromptVersionRow(@JsonProperty("version") String version,
                                   @JsonProperty("content") String content,
                                   @JsonProperty("createdAt") String createdAt) {
    }

    /**
     * Agent 测试回执（三态并集：成功 9~10 键 / 依赖不可用 4 键 / 执行失败 5 键；
     * 条件键由属性级 NON_NULL 省略，键序与旧 Map 各分态一致）。
     */
    public record AgentTestResponse(@JsonProperty("agentId") String agentId,
                                    @JsonInclude(JsonInclude.Include.NON_NULL)
                                    @JsonProperty("sessionId") String sessionId,
                                    @JsonProperty("status") String status,
                                    @JsonInclude(JsonInclude.Include.NON_NULL)
                                    @JsonProperty("success") Boolean success,
                                    @JsonProperty("message") String message,
                                    @JsonProperty("response") String response,
                                    @JsonInclude(JsonInclude.Include.NON_NULL)
                                    @JsonProperty("tokensInput") Integer tokensInput,
                                    @JsonInclude(JsonInclude.Include.NON_NULL)
                                    @JsonProperty("tokensOutput") Integer tokensOutput,
                                    @JsonInclude(JsonInclude.Include.NON_NULL)
                                    @JsonProperty("durationMs") Long durationMs,
                                    @JsonInclude(JsonInclude.Include.NON_NULL)
                                    @JsonProperty("errorMsg") String errorMsg) {
    }

    private String nextId() {
        return "agent-" + ID_SEQ.incrementAndGet();
    }

    /**
     * 列出所有 Agent（默认 100 条）
     */
    public List<AgentRow> listAgents(int pageSize) {
        return repository.findAll(pageSize).stream()
            .map(this::toRow)
            .toList();
    }

    public long totalCount() {
        return repository.count();
    }

    /**
     * 按 ID 获取 Agent 详情
     */
    public Optional<AgentRow> getAgent(String id) {
        return repository.findById(id).map(this::toRow);
    }

    /**
     * 创建 Agent
     */
    public AgentRow createAgent(Map<String, Object> body) {
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
        return toRow(entity);
    }

    /**
     * 更新 Agent
     */
    public Optional<AgentRow> updateAgent(String id, Map<String, Object> body) {
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
        return repository.findById(id).map(this::toRow);
    }

    /**
     * 绑定工具列表
     */
    public Optional<AgentRow> bindTools(String id, Object toolsObj) {
        Optional<AgentEntity> existing = repository.findById(id);
        if (existing.isEmpty()) return Optional.empty();
        String toolsJson = toJsonString(toolsObj);
        repository.updateTools(id, toolsJson);
        return repository.findById(id).map(this::toRow);
    }

    /**
     * 绑定知识库
     */
    public Optional<AgentRow> bindKnowledge(String id, Object knowledgeObj) {
        Optional<AgentEntity> existing = repository.findById(id);
        if (existing.isEmpty()) return Optional.empty();
        String knowledgeJson = toJsonString(knowledgeObj);
        repository.updateKnowledge(id, knowledgeJson);
        return repository.findById(id).map(this::toRow);
    }

    /**
     * 发布 Agent
     */
    public Optional<AgentRow> publishAgent(String id) {
        Optional<AgentEntity> existing = repository.findById(id);
        if (existing.isEmpty()) return Optional.empty();
        repository.publish(id);
        log.info("Agent published: {}", id);
        return repository.findById(id).map(this::toRow);
    }

    /**
     * 删除 Agent
     */
    public boolean deleteAgent(String id) {
        return repository.deleteById(id) > 0;
    }

    /**
     * 测试 Agent — 通过 LLMGatewayService 执行真实对话
     * <p>
     * 1. 根据 Agent 配置创建临时 ProfileConfig
     * 2. 持久化到 DB 并刷新引擎缓存
     * 3. 通过 LLMGatewayService 执行用户消息
     * 4. 清理临时 Profile
     * </p>
     */
    public AgentTestResponse testAgent(String id, Map<String, Object> body) {
        // 1. 获取 Agent 实体
        AgentEntity agent = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Agent " + id + " 不存在"));

        String message = (String) body.getOrDefault("message", "你好");
        String tempProfileName = TEST_PROFILE_PREFIX + UUID.randomUUID().toString().substring(0, 8);

        // 2. 如果 LLMGatewayService 不可用，返回降级信息
        if (llmGatewayService == null || profileConfigRepository == null) {
            return new AgentTestResponse(id, null, "unavailable", null, message,
                    "Hermes 引擎未就绪，无法执行测试。请确认 hermes-engine 服务已正确配置。",
                    null, null, null, null);
        }

        try {
            // 3. 构建临时 ProfileConfig
            ProfileConfig profile = buildTestProfile(agent, tempProfileName);

            // 4. 持久化到 DB
            profileConfigRepository.insert(profile);

            // 5. 刷新引擎缓存
            llmGatewayService.refreshProfileCache(TEST_SUBSYSTEM);

            // 6. 通过 LLMGatewayService 执行
            AgentResult result = llmGatewayService.execute(TEST_SUBSYSTEM, tempProfileName, message);

            // 7. 映射返回值
            AgentTestResponse response = new AgentTestResponse(id, result.getSessionId(),
                    result.isSuccess() ? "completed" : "failed", result.isSuccess(), message,
                    result.getContent(), result.getTokensInput(), result.getTokensOutput(),
                    result.getDurationMs(), result.isSuccess() ? null : result.getErrorMsg());

            log.info("Agent test execution completed: id={}, success={}, duration={}ms",
                    id, result.isSuccess(), result.getDurationMs());
            return response;

        } catch (Exception e) {
            log.error("Agent test execution failed: id={}", id, e);
            return new AgentTestResponse(id, null, "error", null, message,
                    "Agent 测试执行失败: " + e.getMessage(), null, null, 0L, null);
        } finally {
            // 8. 清理临时 Profile
            try {
                profileConfigRepository.deleteById(tempProfileName);
                llmGatewayService.refreshProfileCache(TEST_SUBSYSTEM);
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
    public List<PromptVersionRow> listPromptVersions(String agentId) {
        List<PromptVersionRow> versions = new ArrayList<>();
        versions.add(new PromptVersionRow("v1.0", "当前使用的系统提示词", LocalDateTime.now().toString()));
        return versions;
    }

    private AgentRow toRow(AgentEntity entity) {
        return new AgentRow(entity.getId(), entity.getName(), entity.getModelProvider(),
                entity.getModelName(), entity.getSystemPrompt(),
                parseJsonArray(entity.getTools()), parseJsonArray(entity.getKnowledge()),
                entity.getStatus(),
                entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null,
                entity.getUpdatedAt() != null ? entity.getUpdatedAt().toString() : null);
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
