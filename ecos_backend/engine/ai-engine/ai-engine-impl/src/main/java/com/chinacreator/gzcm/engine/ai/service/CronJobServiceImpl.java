package com.chinacreator.gzcm.engine.ai.service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.chinacreator.gzcm.engine.ai.CronJobService;
import com.chinacreator.gzcm.engine.ai.entity.CronJobEntity;
import com.chinacreator.gzcm.engine.ai.entity.CronJobExecutionEntity;
import com.chinacreator.gzcm.engine.ai.repository.CronJobExecutionRepository;
import com.chinacreator.gzcm.engine.ai.repository.CronJobRepository;

@Service
public class CronJobServiceImpl implements CronJobService {

    private static final Logger log = LoggerFactory.getLogger(CronJobServiceImpl.class);

    private final CronJobRepository cronJobRepository;
    private final CronJobExecutionRepository executionRepository;
    private final AgentCronJobBridge agentCronJobBridge;

    public CronJobServiceImpl(CronJobRepository cronJobRepository,
                              CronJobExecutionRepository executionRepository,
                              AgentCronJobBridge agentCronJobBridge) {
        this.cronJobRepository = cronJobRepository;
        this.executionRepository = executionRepository;
        this.agentCronJobBridge = agentCronJobBridge;
    }

    @Override
    public List<CronJobEntity> listCronJobs(String status, Boolean enabled) {
        return cronJobRepository.findAll(status, enabled);
    }

    @Override
    public Optional<CronJobEntity> getCronJob(Long id) {
        return cronJobRepository.findById(id);
    }

    @Override
    public CronJobEntity createCronJob(Map<String, Object> body) {
        CronJobEntity entity = new CronJobEntity();
        entity.setName(getString(body, "name"));
        entity.setCronExpression(getString(body, "cronExpression"));
        entity.setDescription(getString(body, "description"));
        entity.setEnabled(body.containsKey("enabled") ? getBoolean(body, "enabled") : true);
        entity.setStatus("IDLE");
        entity.setCreatedBy(getString(body, "createdBy"));
        entity.setLastRunAt(null);
        entity.setNextRunAt(null);

        cronJobRepository.insert(entity);
        log.info("CronJob created: id={} name={}", entity.getId(), entity.getName());

        // ── 注册到全局任务中心 ──
        try {
            String prompt = getString(body, "prompt");
            String agentId = getString(body, "agentId");
            String userId = getString(body, "userId");
            if (userId == null) {
                userId = entity.getCreatedBy();
            }

            if (Boolean.TRUE.equals(entity.getEnabled())) {
                agentCronJobBridge.register(entity, prompt, agentId, userId);
            }
        } catch (Exception e) {
            log.warn("Failed to register CronJob to task center: id={}, {}", entity.getId(), e.getMessage());
        }

        return entity;
    }

    @Override
    public Optional<CronJobEntity> updateCronJob(Long id, Map<String, Object> body) {
        Optional<CronJobEntity> existing = cronJobRepository.findById(id);
        if (existing.isEmpty()) return Optional.empty();

        CronJobEntity entity = existing.get();
        if (body.containsKey("name")) entity.setName(getString(body, "name"));
        if (body.containsKey("cronExpression")) entity.setCronExpression(getString(body, "cronExpression"));
        if (body.containsKey("description")) entity.setDescription(getString(body, "description"));
        if (body.containsKey("enabled")) entity.setEnabled(getBoolean(body, "enabled"));
        if (body.containsKey("status")) entity.setStatus(getString(body, "status"));
        if (body.containsKey("createdBy")) entity.setCreatedBy(getString(body, "createdBy"));

        cronJobRepository.update(entity);
        log.info("CronJob updated: id={}", id);
        return Optional.of(entity);
    }

    @Override
    public boolean deleteCronJob(Long id) {
        // ── 先从全局任务中心取消调度 ──
        try {
            agentCronJobBridge.cancel(id);
        } catch (Exception e) {
            log.warn("Failed to cancel CronJob in task center before delete: id={}, {}", id, e.getMessage());
        }

        int affected = cronJobRepository.deleteById(id);
        if (affected > 0) {
            log.info("CronJob deleted: id={}", id);
            return true;
        }
        return false;
    }

    @Override
    public Optional<CronJobEntity> toggleCronJob(Long id, boolean enabled) {
        Optional<CronJobEntity> existing = cronJobRepository.findById(id);
        if (existing.isEmpty()) return Optional.empty();

        cronJobRepository.updateEnabled(id, enabled);
        CronJobEntity entity = existing.get();
        entity.setEnabled(enabled);

        // ── 同步到全局任务中心 ──
        try {
            if (enabled) {
                // 重新启用：用缓存的参数重新注册
                String prompt = agentCronJobBridge.getCachedPrompt(id);
                String agentId = agentCronJobBridge.getCachedAgentId(id);
                String userId = agentCronJobBridge.getCachedUserId(id);
                agentCronJobBridge.reschedule(entity, prompt, agentId, userId);
            } else {
                // 禁用：取消调度
                agentCronJobBridge.cancel(id);
            }
        } catch (Exception e) {
            log.warn("Failed to sync CronJob toggle to task center: id={}, enabled={}, {}", id, enabled, e.getMessage());
        }

        log.info("CronJob toggled: id={} enabled={}", id, enabled);
        return Optional.of(entity);
    }

    @Override
    public Optional<CronJobEntity> executeNow(Long id) {
        Optional<CronJobEntity> existing = cronJobRepository.findById(id);
        if (existing.isEmpty()) return Optional.empty();

        CronJobEntity entity = existing.get();

        // ── 通过全局任务中心立即调度 ──
        try {
            String prompt = agentCronJobBridge.getCachedPrompt(id);
            String agentId = agentCronJobBridge.getCachedAgentId(id);
            String userId = agentCronJobBridge.getCachedUserId(id);

            // 如果缓存中没有参数，使用实体上的信息
            if (prompt == null && entity.getDescription() != null) {
                prompt = entity.getDescription();
            }
            if (userId == null) {
                userId = entity.getCreatedBy();
            }

            agentCronJobBridge.executeNow(entity, prompt, agentId, userId);
            log.info("CronJob executed now: id={}", id);
        } catch (Exception e) {
            log.warn("Failed to execute CronJob now via task center: id={}, {}", id, e.getMessage());
        }

        return Optional.of(entity);
    }

    @Override
    public List<CronJobExecutionEntity> listExecutionHistory(Long cronJobId, int limit) {
        return executionRepository.findByCronJobId(cronJobId, limit);
    }

    @Override
    public long totalCount() {
        return cronJobRepository.count();
    }

    // ── 工具方法 ──

    private String getString(Map<String, Object> body, String key) {
        Object val = body.get(key);
        return val != null ? val.toString() : null;
    }

    private Boolean getBoolean(Map<String, Object> body, String key) {
        Object val = body.get(key);
        if (val instanceof Boolean b) return b;
        if (val != null) return Boolean.valueOf(val.toString());
        return false;
    }
}
