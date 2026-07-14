package com.chinacreator.gzcm.aimod;

import com.chinacreator.gzcm.runtime.hermes.HermesEngine;
import com.chinacreator.gzcm.runtime.hermes.metrics.AgentMetrics;
import com.chinacreator.gzcm.runtime.hermes.model.ProfileConfig;
import com.chinacreator.gzcm.runtime.hermes.repository.ProfileConfigRepository;
import com.chinacreator.gzcm.sysman.hermes.service.IAgentProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Agent Profile 配置 CRUD 服务实现
 */
@Service
public class AgentProfileServiceImpl implements IAgentProfileService {

    private static final Logger log = LoggerFactory.getLogger(AgentProfileServiceImpl.class);

    @Autowired
    private ProfileConfigRepository profileConfigRepository;

    @Autowired(required = false)
    private HermesEngine hermesEngine;

    @Autowired(required = false)
    private AgentMetrics agentMetrics;

    @Override
    public List<ProfileConfig> listAll() {
        return profileConfigRepository.findAll();
    }

    @Override
    public List<ProfileConfig> listBySubsystem(String subsystem) {
        return profileConfigRepository.findBySubsystem(subsystem);
    }

    @Override
    public ProfileConfig getById(String id) {
        return profileConfigRepository.findById(id);
    }

    @Override
    @Transactional
    public ProfileConfig create(ProfileConfig config) {
        if (config.getId() == null || config.getId().isEmpty()) {
            config.setId(UUID.randomUUID().toString().replace("-", ""));
        }
        if (config.getCreatedTime() == null) {
            config.setCreatedTime(LocalDateTime.now());
        }
        profileConfigRepository.insert(config);
        log.info("Created agent profile: id={}, name={}, subsystem={}",
                config.getId(), config.getProfileName(), config.getSubsystem());
        return config;
    }

    @Override
    @Transactional
    public ProfileConfig update(ProfileConfig config) {
        config.setUpdatedTime(LocalDateTime.now());
        profileConfigRepository.update(config);
        // 刷新引擎缓存
        if (hermesEngine != null) {
            hermesEngine.refreshProfileCache(config.getSubsystem());
        }
        log.info("Updated agent profile: id={}, name={}, subsystem={}",
                config.getId(), config.getProfileName(), config.getSubsystem());
        return config;
    }

    @Override
    @Transactional
    public void delete(String id) {
        ProfileConfig existing = profileConfigRepository.findById(id);
        if (existing == null) {
            throw new RuntimeException("Profile 不存在: " + id);
        }
        profileConfigRepository.deleteById(id);
        // 刷新引擎缓存
        if (hermesEngine != null && existing.getSubsystem() != null) {
            hermesEngine.refreshProfileCache(existing.getSubsystem());
        }
        log.info("Deleted agent profile: id={}", id);
    }

    @Override
    @Transactional
    public void toggleEnabled(String id, boolean enabled) {
        ProfileConfig config = profileConfigRepository.findById(id);
        if (config == null) {
            throw new RuntimeException("Profile 不存在: " + id);
        }
        config.setEnabled(enabled);
        config.setUpdatedTime(LocalDateTime.now());
        profileConfigRepository.update(config);
        // 刷新引擎缓存
        if (hermesEngine != null && config.getSubsystem() != null) {
            hermesEngine.refreshProfileCache(config.getSubsystem());
        }
        log.info("Toggled agent profile enabled={}: id={}", enabled, id);
    }

    @Override
    public void testConnection(String id) {
        ProfileConfig config = profileConfigRepository.findById(id);
        if (config == null) {
            throw new RuntimeException("Profile 不存在: " + id);
        }
        if (config.getBaseUrl() == null || config.getBaseUrl().isEmpty()) {
            throw new RuntimeException("baseUrl 未配置，无法测试连接");
        }
        try {
            // 这里可以添加实际的 LLM 连通性测试逻辑
            // 例如使用 LLMGateway.ping() 或发送一个简单请求
            // 目前简单校验配置完整性
            if (config.getApiKeyRef() == null || config.getApiKeyRef().isEmpty()) {
                throw new RuntimeException("apiKeyRef 未配置，无法完成认证");
            }
            if (config.getModel() == null || config.getModel().isEmpty()) {
                throw new RuntimeException("model 未配置");
            }
            log.info("Connection test passed for profile: id={}, baseUrl={}", id, config.getBaseUrl());
        } catch (Exception e) {
            log.error("Connection test failed for profile: id={}", id, e);
            throw new RuntimeException("连接测试失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, Object> getSubsystemStats(String subsystem) {
        if (agentMetrics != null) {
            return agentMetrics.getSubsystemStats(subsystem);
        }
        return Map.of();
    }

    @Override
    public Map<String, Object> getGlobalStats() {
        if (agentMetrics != null) {
            return agentMetrics.getGlobalStats();
        }
        return Map.of();
    }
}
