package com.chinacreator.gzcm.engine.ai;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.chinacreator.gzcm.engine.ai.entity.CronJobEntity;
import com.chinacreator.gzcm.engine.ai.entity.CronJobExecutionEntity;

/**
 * CronJob 定时任务 Service 接口
 */
public interface CronJobService {

    /** 列出所有 CronJob，支持分页和条件过滤 */
    List<CronJobEntity> listCronJobs(String status, Boolean enabled);

    /** 根据 ID 查询 */
    Optional<CronJobEntity> getCronJob(Long id);

    /** 创建新 CronJob */
    CronJobEntity createCronJob(Map<String, Object> body);

    /** 更新 CronJob */
    Optional<CronJobEntity> updateCronJob(Long id, Map<String, Object> body);

    /** 删除 CronJob */
    boolean deleteCronJob(Long id);

    /** 启用/禁用 */
    Optional<CronJobEntity> toggleCronJob(Long id, boolean enabled);

    /** 执行历史 */
    List<CronJobExecutionEntity> listExecutionHistory(Long cronJobId, int limit);

    /** 立即执行 CronJob */
    Optional<CronJobEntity> executeNow(Long id);

    /** 总数量 */
    long totalCount();
}
