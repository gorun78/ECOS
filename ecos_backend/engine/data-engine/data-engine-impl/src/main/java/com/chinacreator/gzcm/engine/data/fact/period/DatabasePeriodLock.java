package com.chinacreator.gzcm.engine.data.fact.period;

import com.chinacreator.gzcm.engine.data.fact.dao.PeriodStatusMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * DB 真身期间锁 — 详细设计-09 v1.2 §十一 H-3 交付①（V278 {@code ecos_data.ecos_data_period_status}）。
 *
 * <p><b>装配裁定（@Primary + 属性开关，二者取一 → 选后者组合）</b>：
 * {@code @ConditionalOnProperty(name="ecos.data.facts.period-lock", havingValue="database",
 * matchIfMissing=true)} + {@code @Primary}。理由：
 * <ol>
 *   <li>默认（缺省属性）即启用 DB 真身——H-3 的目标形态；</li>
 *   <li>显式 {@code period-lock=config} 一键回退 {@link OpenPeriodsPeriodLock}（配置期锁），
 *       即 V278 未实跑环境的既有形态，运维可灰度；</li>
 *   <li>比纯 {@code @Primary} 好在能把本 bean 整体摘除（表缺失环境不产生无谓查询与告警噪音），
 *       比纯条件好在 OpenPeriodsPeriodLock 恒在、降级委托有实体。</li>
 * </ol>
 *
 * <p><b>禁循环查库</b>（后端规范红线）：CLOSED 集合按 {@code refresh-seconds}（默认 60s）
 * 缓存；isClosed 命中缓存零查询；{@link #forceRefresh()} 供关账成功后即时失效。
 *
 * <p><b>降级口径</b>：DB 访问异常（表未迁移/连接失败）→ WARN 一次并回退
 * {@link OpenPeriodsPeriodLock} 的配置 CSV 判定（保持 H-3 前行为，不把平台迁移进度
 * 变成全量写入 500）。注意这与"关账=拒绝非降级"（{@link PeriodLock} javadoc）不冲突：
 * 拒绝语义在 DB 可达时严格执行；DB 不可达属基础设施故障面，按 C.8 降级矩阵处理。
 */
@Component
@Primary
@ConditionalOnProperty(name = "ecos.data.facts.period-lock", havingValue = "database", matchIfMissing = true)
public class DatabasePeriodLock implements PeriodLock {

    private static final Logger log = LoggerFactory.getLogger(DatabasePeriodLock.class);

    private final PeriodStatusMapper periodStatusMapper;
    private final OpenPeriodsPeriodLock configFallback;
    private final long refreshMillis;

    private volatile Set<String> closedCache = null;
    private volatile long lastRefreshAt = 0L;
    private volatile boolean degradedWarned = false;

    public DatabasePeriodLock(PeriodStatusMapper periodStatusMapper,
                              OpenPeriodsPeriodLock configFallback,
                              @Value("${ecos.data.facts.period-lock-refresh-seconds:60}") long refreshSeconds) {
        this.periodStatusMapper = periodStatusMapper;
        this.configFallback = configFallback;
        this.refreshMillis = Math.max(1L, refreshSeconds) * 1000L;
    }

    @Override
    public boolean isClosed(String period) {
        if (period == null || period.isBlank()) {
            return false;
        }
        Set<String> snapshot = currentSnapshot();
        if (snapshot == null) {
            // DB 不可达 → 配置期锁降级判定（WARN 一次，恢复后自动回到 DB 判定）
            return configFallback.isClosed(period);
        }
        return snapshot.contains(period);
    }

    /** 关账成功后强制失效缓存（下一次判定即刻反映 DB 态）。 */
    public void forceRefresh() {
        synchronized (this) {
            lastRefreshAt = 0L;
            closedCache = null;
        }
    }

    /**
     * @return CLOSED 期间集合；null = DB 不可达（调用侧走降级）。
     */
    private Set<String> currentSnapshot() {
        long now = System.currentTimeMillis();
        Set<String> cache = closedCache;
        if (cache != null && now - lastRefreshAt < refreshMillis) {
            return cache;
        }
        synchronized (this) {
            long sinceAttempt = System.currentTimeMillis() - lastRefreshAt;
            cache = closedCache;
            if (cache != null && sinceAttempt < refreshMillis) {
                return cache;
            }
            if (cache == null && lastRefreshAt != 0L && sinceAttempt < refreshMillis) {
                return null;   // 从未成功过：失败退避窗口内不再打库（禁循环查库/防空转）
            }
            try {
                List<String> closed = periodStatusMapper.selectClosedPeriods();
                cache = Collections.unmodifiableSet(new HashSet<>(closed));
                closedCache = cache;
                lastRefreshAt = System.currentTimeMillis();
                degradedWarned = false;
                return cache;
            } catch (Exception e) {
                if (!degradedWarned) {
                    log.warn("[PeriodLock] ecos_data.ecos_data_period_status 不可读，降级配置期锁"
                            + "（ecos.data.facts.closed-periods）: {}", e.getMessage());
                    degradedWarned = true;
                }
                lastRefreshAt = System.currentTimeMillis();   // 退避：异常也按刷新周期重试
                return closedCache;                            // 保留旧缓存（若有）；从未成功 → null 触发配置降级
            }
        }
    }

    /** 测试/运维态诊断用：当前缓存的 CLOSED 集合（未刷新成功时为空集）。 */
    public Set<String> cachedClosedPeriods() {
        Set<String> c = closedCache;
        return c == null ? Collections.emptySet() : c;
    }
}
