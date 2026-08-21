package com.chinacreator.gzcm.engine.ai.service;

import com.chinacreator.gzcm.engine.ai.agent.mesh.entity.AgentRegistryEntity;
import com.chinacreator.gzcm.engine.ai.agent.mesh.repository.AgentRegistryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Agent 配置三层解析器 — L1(application.yml) → L2(Agent 模板) → L3(Agent 实例) → 运行时覆盖。
 *
 * <h3>解析模型</h3>
 * <pre>
 *   resolve(agentId, requestOverrides):
 *     L1: 从 application.yml 读取全局默认值
 *     L2: 查找 Agent 模板（role=builtin 的 AgentRegistryEntity.metadata）
 *         - 若 agentId 本身就是 builtin → 直接取其 metadata
 *         - 若 agentId 是用户实例 → 从其 metadata.templateId 找到 builtin 模板
 *     L3: 查找用户 Agent 实例（role != builtin 的 AgentRegistryEntity.metadata）
 *     requestOverrides: API 请求参数覆盖
 * </pre>
 *
 * <p>每层只覆盖非空字段，后层覆盖前层，requestOverrides 优先级最高。</p>
 *
 * <h3>注入点</h3>
 * <ul>
 *   <li>{@link AgentRegistryRepository} — 查 Agent 注册表（required=false，未就绪时仅 L1 生效）</li>
 * </ul>
 */
@Component
public class AgentConfigResolver {

    private static final Logger log = LoggerFactory.getLogger(AgentConfigResolver.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // ─── L1: application.yml 全局默认值 ────────────────────────────────

    @Value("${llm.default-provider:deepseek}")
    private String l1DefaultProvider;

    @Value("${llm.default-model:deepseek-chat}")
    private String l1DefaultModel;

    @Value("${llm.default-temperature:0.3}")
    private Double l1DefaultTemperature;

    @Value("${llm.max-context-tokens:8000}")
    private Integer l1MaxContextTokens;

    @Value("${llm.agent-timeout-ms:300000}")
    private Long l1AgentTimeoutMs;

    @Value("${agent.default-max-iterations:5}")
    private Integer l1MaxIterations;

    @Value("${agent.default-system-prompt:你是ECOS平台的AI助手。}")
    private String l1DefaultSystemPrompt;

    @Autowired(required = false)
    private AgentRegistryRepository agentRepo;

    // ─── 公开 API ──────────────────────────────────────────────────────

    /**
     * 执行三层解析，返回最终合并后的配置。
     *
     * @param agentId         Agent 标识（可为 "default"、builtin ID、或用户实例 ID）
     * @param requestOverrides API 请求参数（可为 null）
     * @return 三层合并后的 AgentLoopConfig
     */
    public AgentLoopConfig resolve(String agentId, Map<String, Object> requestOverrides) {
        // L1: 全局默认值
        AgentLoopConfig config = new AgentLoopConfig(
                l1DefaultProvider, l1DefaultModel, l1DefaultTemperature,
                null,           // maxTokens — L1 不设默认，由 L2/L3/请求决定
                l1MaxContextTokens,
                l1MaxIterations,
                l1AgentTimeoutMs,
                l1DefaultSystemPrompt
        );

        log.debug("[AgentConfigResolver] L1 defaults: provider={}, model={}, temp={}, maxCtx={}, maxIter={}, timeout={}ms",
                l1DefaultProvider, l1DefaultModel, l1DefaultTemperature,
                l1MaxContextTokens, l1MaxIterations, l1AgentTimeoutMs);

        // "default" 或空 → 跳过 L2/L3，仅用 L1 + 请求覆盖
        if (agentId == null || agentId.isBlank() || "default".equals(agentId)) {
            log.debug("[AgentConfigResolver] agentId is default/null, skipping L2/L3 lookups");
            if (requestOverrides != null && !requestOverrides.isEmpty()) {
                config.applyOverrides(requestOverrides);
            }
            return config;
        }

        // 查 AgentRegistry
        if (agentRepo == null) {
            log.debug("[AgentConfigResolver] AgentRegistryRepository not available, using L1 only");
            if (requestOverrides != null && !requestOverrides.isEmpty()) {
                config.applyOverrides(requestOverrides);
            }
            return config;
        }

        AgentRegistryEntity entity = agentRepo.findById(agentId);

        if (entity == null) {
            log.debug("[AgentConfigResolver] Agent '{}' not found in registry, using L1 only", agentId);
            if (requestOverrides != null && !requestOverrides.isEmpty()) {
                config.applyOverrides(requestOverrides);
            }
            return config;
        }

        // L2: 查找 builtin 模板
        AgentRegistryEntity builtin = null;
        if ("builtin".equals(entity.getRole())) {
            // agentId 本身就是 builtin 模板
            builtin = entity;
            log.debug("[AgentConfigResolver] L2: agent '{}' IS a builtin template", agentId);
        } else {
            // 用户实例 — 从 metadata 中提取 templateId 查找 builtin 模板
            String templateId = extractTemplateId(entity.getMetadata());
            if (templateId != null && !templateId.isBlank()) {
                builtin = agentRepo.findById(templateId);
                if (builtin != null && "builtin".equals(builtin.getRole())) {
                    log.debug("[AgentConfigResolver] L2: found builtin template '{}' for agent '{}'",
                            templateId, agentId);
                } else {
                    log.debug("[AgentConfigResolver] L2: templateId '{}' not found or not builtin", templateId);
                    builtin = null;
                }
            } else {
                log.debug("[AgentConfigResolver] L2: no templateId in agent '{}' metadata", agentId);
            }
        }

        // 应用 L2（builtin 模板）
        if (builtin != null) {
            Map<String, Object> builtinMeta = parseMetadata(builtin.getMetadata());
            if (builtinMeta != null && !builtinMeta.isEmpty()) {
                config.applyTemplate(builtinMeta);
                log.debug("[AgentConfigResolver] L2 applied: template '{}' (role={})",
                        builtin.getId(), builtin.getRole());
            }
        }

        // L3: 用户实例覆盖（仅当 entity 不是 builtin 时）
        if (!"builtin".equals(entity.getRole())) {
            config.applyInstance(entity);
            log.debug("[AgentConfigResolver] L3 applied: agent instance '{}' (role={})",
                    entity.getId(), entity.getRole());
        }

        // 运行时请求覆盖（最高优先级）
        if (requestOverrides != null && !requestOverrides.isEmpty()) {
            config.applyOverrides(requestOverrides);
            log.debug("[AgentConfigResolver] Runtime overrides applied: {} keys", requestOverrides.size());
        }

        log.info("[AgentConfigResolver] Resolved config for agent '{}': {}", agentId, config);
        return config;
    }

    /**
     * 便捷方法：从请求体解析 agentId 和运行时覆盖参数构建最终配置。
     *
     * @param body API 请求体（含 agentId, model, temperature 等字段）
     * @return 三层合并后的 AgentLoopConfig
     */
    @SuppressWarnings("unchecked")
    public AgentLoopConfig resolveFromRequest(Map<String, Object> body) {
        String agentId = (String) body.getOrDefault("agentId", "default");
        // requestOverrides 是 body 本身（包含所有运行时参数）
        return resolve(agentId, body);
    }

    // ─── 内部工具方法 ───────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseMetadata(String metadataJson) {
        if (metadataJson == null || metadataJson.isEmpty()) return null;
        try {
            return objectMapper.readValue(metadataJson, Map.class);
        } catch (Exception e) {
            log.debug("[AgentConfigResolver] Failed to parse metadata JSON: {}", e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private String extractTemplateId(String metadataJson) {
        Map<String, Object> meta = parseMetadata(metadataJson);
        if (meta == null) return null;
        Object tid = meta.get("templateId");
        return tid != null ? tid.toString() : null;
    }
}
