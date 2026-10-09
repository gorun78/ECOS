package com.chinacreator.gzcm.runtime.core.memory;

import com.chinacreator.gzcm.common.service.IMemorySweepable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 底座内存视图周期清扫任务（详细设计-06 F06-10 / W152）。
 *
 * <p><b>为什么在 runtime-task</b>：铁律 §2.5-4 规定长运行任务不得散落在各模块内自建
 * {@code @Scheduled}/线程池（X-25）。会话/轨迹这类进程内有界视图的属主只实现
 * {@link IMemorySweepable} 交出"被清扫"能力，唯一的周期驱动点就是本任务 ——
 * 与 {@code AuditRetryTask}、{@code GitArchiveRetryTask} 同一形态。</p>
 *
 * <p><b>为什么必须周期清扫</b>：懒淘汰只在读路径生效，"已超时且再不被读"的条目永远回收不到，
 * RSS 于是仍随创建量单调增长，F06-09 的内存门禁无从判定。</p>
 *
 * <p><b>故障面</b>：单个 sweepable 抛异常只 ERROR 留痕并继续下一个（清扫是可重入的幂等回收，
 * 下一周期还会再试），禁因一个视图件故障让整条内存治理链停摆。</p>
 */
@Component
public class SessionMemorySweepTask {

    /** 清扫周期：60s（TTL 最细粒度 = 1 分钟级误差，对 30 分钟空闲阈值足够） */
    public static final long INTERVAL_MS = 60_000L;

    private static final Logger log = LoggerFactory.getLogger(SessionMemorySweepTask.class);

    private final List<IMemorySweepable> sweepables;

    public SessionMemorySweepTask(List<IMemorySweepable> sweepables) {
        this.sweepables = sweepables;
        log.info("[F06-10] 底座内存清扫任务已挂载: sweepables={} intervalMs={}",
                sweepables.size(), INTERVAL_MS);
    }

    @Scheduled(fixedDelay = INTERVAL_MS, initialDelay = INTERVAL_MS)
    public void sweepAll() {
        for (IMemorySweepable sweepable : sweepables) {
            String owner = sweepable.getClass().getSimpleName();
            try {
                int reclaimed = sweepable.sweepIdleEntries();
                if (reclaimed > 0) {
                    log.info("[F06-10] 内存清扫回收: owner={} reclaimed={} resident={}",
                            owner, reclaimed, sweepable.residentEntryCount());
                } else if (log.isDebugEnabled()) {
                    log.debug("[F06-10] 内存清扫空转: owner={} resident={}",
                            owner, sweepable.residentEntryCount());
                }
            } catch (Exception e) {
                log.error("[F06-10] 内存清扫失败（下一周期重试，禁停摆整链）: owner={} err={}",
                        owner, e.getMessage(), e);
            }
        }
    }
}
