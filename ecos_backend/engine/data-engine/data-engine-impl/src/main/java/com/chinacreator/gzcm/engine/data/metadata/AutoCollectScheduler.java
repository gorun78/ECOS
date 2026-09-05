package com.chinacreator.gzcm.engine.data.metadata;

import com.chinacreator.gzcm.engine.data.datasource.entity.DataSourceEntity;
import com.chinacreator.gzcm.engine.data.service.MetadataRowCountService;
import com.chinacreator.gzcm.engine.data.repository.DataSourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PMO-37 定时自动采集 —— 每 60s 扫描 strategy=ON_SCHEDULE 的数据源，
 * 到点（cron 相对 last_collect_time 的下次触发 &lt;= now）提交采集任务。
 * <p>
 * 连续失败 3 次的数据源自动静默（下一周期重置计数），避免刷屏。
 *
 * @author DataBridge Datanet Team
 */
@Component
public class AutoCollectScheduler {

    private static final Logger log = LoggerFactory.getLogger(AutoCollectScheduler.class);
    private static final int MAX_CONSECUTIVE_FAILS = 3;

    private final DataSourceRepository dsRepository;
    private final MetadataAsyncTrigger trigger;
    private final MetadataRowCountService rowCountService;

    /** datasourceId -> 连续失败次数（触发即 +1，完成回调 markSuccess 清零） */
    private final Map<String, Integer> fails = new ConcurrentHashMap<>();

    public AutoCollectScheduler(DataSourceRepository dsRepository,
                                MetadataAsyncTrigger trigger,
                                MetadataRowCountService rowCountService) {
        this.dsRepository = dsRepository;
        this.trigger = trigger;
        this.rowCountService = rowCountService;
    }

    @Scheduled(fixedDelay = 60_000L, initialDelay = 30_000L)
    public void scanDueSources() {
        Iterable<DataSourceEntity> all;
        try {
            all = dsRepository.findAll();
        } catch (Exception e) {
            log.warn("AutoCollectScheduler 扫描失败: {}", e.getMessage());
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (DataSourceEntity ds : all) {
            try {
                MetadataStrategyConfig cfg = MetadataStrategyConfig.fromJson(ds.getMetadataConfig());
                if (!MetadataAsyncTrigger.isOnSchedule(cfg)) {
                    continue;
                }
                java.sql.Timestamp last = rowCountService.getLastCollectTime(ds.getDatasourceId());
                LocalDateTime lastT = last == null ? null : last.toLocalDateTime();
                // 从未采集过 → 直接触发一次基线采集
                if (lastT == null) {
                    log.info("基线采集触发（从未采集）: datasource={}, cron={}",
                            ds.getDatasourceId(), cfg.getScheduleCron());
                    trigger.triggerManualAsync(ds.getDatasourceId());
                    fails.merge(ds.getDatasourceId(), 1, Integer::sum);
                } else {
                    LocalDateTime next = MetadataAsyncTrigger.nextFireTime(cfg.getScheduleCron(), lastT);
                    if (next != null && !next.isAfter(now)) {
                        int failStreak = fails.getOrDefault(ds.getDatasourceId(), 0);
                        if (failStreak >= MAX_CONSECUTIVE_FAILS) {
                            log.debug("定时采集静默中（连续失败 {} 次）: datasource={}",
                                    failStreak, ds.getDatasourceId());
                            continue;
                        }
                        log.info("定时采集触发: datasource={}, cron={}",
                                ds.getDatasourceId(), cfg.getScheduleCron());
                        trigger.triggerManualAsync(ds.getDatasourceId());
                        fails.merge(ds.getDatasourceId(), 1, Integer::sum);
                    }
                }
            } catch (Exception e) {
                log.debug("AutoCollectScheduler 处理 {} 失败: {}", ds.getDatasourceId(), e.getMessage());
            }
        }
    }

    /** 采集完成后由 Controller/Service 回调，重置失败计数 */
    public void markSuccess(String datasourceId) {
        if (datasourceId != null) {
            fails.remove(datasourceId);
        }
    }

    public void markFailure(String datasourceId) {
        if (datasourceId != null) {
            fails.merge(datasourceId, 1, Integer::sum);
        }
    }
}
