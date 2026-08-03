package com.chinacreator.gzcm.engine.ai.service;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import com.chinacreator.gzcm.runtime.core.agent.mesh.entity.AgentRegistryEntity;
import com.chinacreator.gzcm.runtime.core.agent.mesh.repository.AgentRegistryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Agent 模板管理服务 — 从 resources/agent-templates/*.json 加载内置模板，
 * 提供模板列表查询和基于模板的 Agent 实例化能力。
 *
 * <h3>核心方法：</h3>
 * <ul>
 *   <li>{@link #getTemplates()} — 返回所有内置模板</li>
 *   <li>{@link #instantiate(String, String, Map)} — 基于模板创建用户级 Agent</li>
 * </ul>
 */
@Component
public class AgentTemplateService {

    private static final Logger log = LoggerFactory.getLogger(AgentTemplateService.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final String TEMPLATE_PATH = "classpath:agent-templates/*.json";

    private final List<Map<String, Object>> templates = new ArrayList<>();

    @Autowired(required = false)
    private AgentRegistryRepository agentRepo;

    public AgentTemplateService() {
        loadTemplates();
    }

    /**
     * 从 classpath 加载所有 agent-templates/*.json 模板文件。
     */
    @SuppressWarnings("unchecked")
    private void loadTemplates() {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources(TEMPLATE_PATH);

            for (Resource resource : resources) {
                try (InputStream is = resource.getInputStream()) {
                    Map<String, Object> template = mapper.readValue(is, LinkedHashMap.class);
                    templates.add(template);
                    log.info("Loaded agent template: {} (id={})",
                            template.get("name"), template.get("id"));
                } catch (Exception e) {
                    log.warn("Failed to load agent template from {}: {}",
                            resource.getFilename(), e.getMessage());
                }
            }
            log.info("AgentTemplateService initialized with {} templates", templates.size());
        } catch (Exception e) {
            log.error("Failed to scan agent-templates directory: {}", e.getMessage());
        }
    }

    /**
     * 返回所有内置模板列表。
     */
    public List<Map<String, Object>> getTemplates() {
        return new ArrayList<>(templates);
    }

    /**
     * 根据模板ID查找模板。
     *
     * @param templateId 模板ID（如 "builtin-data-agent"）
     * @return 模板 Map，未找到返回 null
     */
    public Map<String, Object> getTemplateById(String templateId) {
        for (Map<String, Object> t : templates) {
            if (templateId.equals(t.get("id"))) {
                return t;
            }
        }
        return null;
    }

    /**
     * 基于模板实例化一个新的 Agent（写入 agentRepo）。
     *
     * @param templateId     模板ID（如 "builtin-data-agent"）
     * @param userId         创建用户标识
     * @param overrideParams 可覆盖的参数字典（如 name, description, systemPrompt 等），可为 null
     * @return 新创建的 AgentRegistryEntity
     * @throws IllegalArgumentException 模板不存在时抛出
     * @throws IllegalStateException    agentRepo 未就绪时抛出
     */
    @SuppressWarnings("unchecked")
    public AgentRegistryEntity instantiate(String templateId, String userId,
                                           Map<String, Object> overrideParams) {
        Map<String, Object> template = getTemplateById(templateId);
        if (template == null) {
            throw new IllegalArgumentException("Agent template not found: " + templateId);
        }
        if (agentRepo == null) {
            throw new IllegalStateException("AgentRegistryRepository 未就绪");
        }

        // 生成新 Agent ID
        String newId = "aip_agent_" + UUID.randomUUID().toString().substring(0, 8);

        // 合并参数：模板默认值 + 覆盖参数
        String name = getOverride(overrideParams, "name",
                String.valueOf(template.getOrDefault("name", "")));
        String description = getOverride(overrideParams, "description",
                String.valueOf(template.getOrDefault("description", "")));
        String systemPrompt = getOverride(overrideParams, "systemPrompt",
                String.valueOf(template.getOrDefault("systemPrompt", "")));
        String model = getOverride(overrideParams, "model",
                String.valueOf(template.getOrDefault("model", "deepseek-chat")));
        Integer maxIterations = getOverrideInt(overrideParams, "maxIterations",
                template.containsKey("maxIterations")
                        ? ((Number) template.get("maxIterations")).intValue() : 5);
        Double temperature = getOverrideDouble(overrideParams, "temperature",
                template.containsKey("temperature")
                        ? ((Number) template.get("temperature")).doubleValue() : 0.1);

        // 工具白名单合并
        List<String> tools;
        if (overrideParams != null && overrideParams.containsKey("toolWhitelist")) {
            tools = (List<String>) overrideParams.get("toolWhitelist");
        } else {
            tools = (List<String>) template.getOrDefault("toolWhitelist", new ArrayList<>());
        }

        // 构建 entity
        AgentRegistryEntity entity = new AgentRegistryEntity();
        entity.setId(newId);
        entity.setName(name);
        entity.setRole("user");  // 从模板实例化的 Agent 角色为 user，不再受 builtin 删除保护
        entity.setStatus("draft");

        // metadata JSON 存储扩展字段
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("description", description);
        meta.put("systemPrompt", systemPrompt);
        meta.put("model", model);
        meta.put("maxIterations", maxIterations);
        meta.put("temperature", temperature);
        meta.put("templateId", templateId);
        meta.put("createdBy", userId);
        meta.put("icon", String.valueOf(template.getOrDefault("icon", "")));
        try {
            entity.setMetadata(mapper.writeValueAsString(meta));
        } catch (Exception e) {
            entity.setMetadata("{}");
        }

        // capability JSON 存储工具白名单
        Map<String, Object> capability = new LinkedHashMap<>();
        capability.put("tools", tools);
        try {
            entity.setCapability(mapper.writeValueAsString(capability));
        } catch (Exception e) {
            entity.setCapability("{}");
        }

        agentRepo.insert(entity);
        log.info("Agent instantiated from template {}: {} [{}] by {}",
                templateId, newId, name, userId);
        return entity;
    }

    // ────── 辅助方法 ──────

    private String getOverride(Map<String, Object> overrides, String key, String defaultValue) {
        if (overrides != null && overrides.containsKey(key)) {
            return String.valueOf(overrides.get(key));
        }
        return defaultValue;
    }

    private Integer getOverrideInt(Map<String, Object> overrides, String key, Integer defaultValue) {
        if (overrides != null && overrides.containsKey(key)) {
            Object val = overrides.get(key);
            if (val instanceof Number) return ((Number) val).intValue();
            try { return Integer.parseInt(String.valueOf(val)); } catch (Exception e) { /* fallthrough */ }
        }
        return defaultValue;
    }

    private Double getOverrideDouble(Map<String, Object> overrides, String key, Double defaultValue) {
        if (overrides != null && overrides.containsKey(key)) {
            Object val = overrides.get(key);
            if (val instanceof Number) return ((Number) val).doubleValue();
            try { return Double.parseDouble(String.valueOf(val)); } catch (Exception e) { /* fallthrough */ }
        }
        return defaultValue;
    }
}
