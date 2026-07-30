package com.chinacreator.gzcm.engine.ai.service;

import java.time.LocalDateTime;
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

    public CronJobServiceImpl(CronJobRepository cronJobRepository,
                              CronJobExecutionRepository executionRepository) {
        this.cronJobRepository = cronJobRepository;
        this.executionRepository = executionRepository;
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
        log.info("CronJob toggled: id={} enabled={}", id, enabled);
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
