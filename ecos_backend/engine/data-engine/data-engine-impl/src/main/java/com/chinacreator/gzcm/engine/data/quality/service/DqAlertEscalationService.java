package com.chinacreator.gzcm.engine.data.quality.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.chinacreator.gzcm.runtime.core.alert.IAlertService;

/**
 * F02-10 — DQ 告警升级时限状态机（升级<b>驱动</b>归 runtime-task，见分册 00 C.4）。
 *
 * <p>本类只承担<b>一台/per-scan 的升级决策与落库/推送</b>：由 {@code DqAlertEscalationTask}
 * 注册的 runtime-task 周期任务每 1min 触发一次 {@link #runEscalation()}。data-engine 内
 * <b>禁</b>自建 {@code @Scheduled}（ESCALATION 不得自造调度，验收 {@code EscalationSchedulerRegisteredInRuntimeTaskTest}）。
 *
 * <p>升级规则（未 ack 且超响应窗口）：
 * <ul>
 *   <li>{@code P2} 超 {@link #P2_TO_P1}（5min）未 ack → {@code P1}；</li>
 *   <li>{@code P1} 超 {@link #P1_TO_P0}（15min）未 ack → {@code P0}。</li>
 * </ul>
 * 每次升级经 {@link IAlertService#triggerAlert} 推送一次，并回写
 * {@code dq_alert_record.escalated_to}/{@code alert_level}/{@code status='ESCALATED'} 留痕。
 *
 * <p>注入 {@link Clock} 仅为测试可控时钟服务（生产走 {@link Clock#systemZone()}）。
 */
@Component
public class DqAlertEscalationService {

    /** 升级任务类型（runtime-task 注册用）。 */
    public static final String TASK_TYPE = "DQ_ALERT_ESCALATION";
    public static final String TASK_ID = "dq-alert-escalation";

    private static final Duration P2_TO_P1 = Duration.ofMinutes(5);   // P2 超 5min 未 ack → P1
    private static final Duration P1_TO_P0 = Duration.ofMinutes(15);  // P1 超 15min 未 ack → P0
    private static final String ALERT_TYPE = "DQ_ESCALATION";

    private final Logger log = LoggerFactory.getLogger(DqAlertEscalationService.class);
    private final JdbcTemplate jdbc;
    private final IAlertService alertService;
    private final Clock clock;

    @Autowired
    public DqAlertEscalationService(JdbcTemplate jdbc, IAlertService alertService) {
        this(jdbc, alertService, Clock.systemDefaultZone());
    }

    /** 测试/需要可控时钟的构造器。 */
    public DqAlertEscalationService(JdbcTemplate jdbc, IAlertService alertService, Clock clock) {
        this.jdbc = jdbc;
        this.alertService = alertService;
        this.clock = clock;
    }

    /**
     * 扫描未 ack 告警并升级超限者，返回本次升级条数。
     * <p>降级矩阵（分册 00）：runtime-monitor 不可用（IAlertService 抛异常）→ 仅落库 + 记 WARN，
     * 不回抛，保证升级留痕不受推送通道影响。
     */
    public int runEscalation() {
        List<Map<String, Object>> due;
        try {
            due = jdbc.queryForList(
                    "SELECT id, rule_id, alert_level, asset_id, rule_name, created_at "
                            + "FROM ecos_dq.dq_alert_record "
                            + "WHERE status IN ('PENDING','NOTIFIED','ESCALATED')");
        } catch (Exception e) {
            log.warn("DQ 告警升级扫描失败（表可能未迁移）: {}", e.getMessage());
            return 0;
        }

        int escalated = 0;
        for (Map<String, Object> row : due) {
            String fromLevel = str(row.get("alert_level"));
            Instant createdAt = parseInstant(row.get("created_at"));
            String target = decideEscalation(fromLevel, createdAge(createdAt));
            if (target == null) {
                continue;
            }
            applyEscalation(row, fromLevel, target);
            escalated++;
        }
        return escalated;
    }

    /**
     * 纯决策：给定当前级别与未 ack 时长，返回应升级到的目标级别；无升级返回 {@code null}。
     * 抽成纯函数便于可控时钟单测断言（{@code DqAlertEscalationTimerTest}）。
     */
    String decideEscalation(String fromLevel, Duration unAckAge) {
        if (fromLevel == null || unAckAge == null) {
            return null;
        }
        String lvl = fromLevel.trim().toUpperCase(Locale.ROOT);
        switch (lvl) {
            case "P2":
                return unAckAge.compareTo(P2_TO_P1) >= 0 ? "P1" : null;
            case "P1":
                return unAckAge.compareTo(P1_TO_P0) >= 0 ? "P0" : null;
            default:
                // P0 为最高级无再升级；P3 无自动升级规则
                return null;
        }
    }

    /** 相对当前可控时钟计算未 ack 时长。 */
    private Duration createdAge(Instant createdAt) {
        if (createdAt == null) {
            return null;
        }
        return Duration.between(createdAt, clock.instant());
    }

    private void applyEscalation(Map<String, Object> row, String fromLevel, String target) {
        String alertId = str(row.get("id"));
        try {
            jdbc.update(
                    "UPDATE ecos_dq.dq_alert_record "
                            + "SET alert_level = ?, escalated_to = ?, status = 'ESCALATED', last_notify_at = NOW() "
                            + "WHERE id = ?",
                    target, target, alertId);
            pushAlert(row, fromLevel, target);
            log.info("DQ 告警升级: id={} {} -> {} (未ack超时)", alertId, fromLevel, target);
        } catch (Exception e) {
            // 降级：落库失败或推送失败都不能中断整批扫描
            log.warn("DQ 告警升级落库/推送失败 id={} {} -> {}: {}", alertId, fromLevel, target, e.getMessage());
        }
    }

    /** 经 runtime IAlertService 推送升级告警（1 次调用即 1 次推送）。失败只记日志，不中断。 */
    private void pushAlert(Map<String, Object> row, String fromLevel, String target) {
        try {
            alertService.triggerAlert(
                    str(row.get("rule_id")),
                    ALERT_TYPE,
                    str(row.get("asset_id")),
                    null,
                    "DQ 告警升级 " + fromLevel + " -> " + target + "（未 ack 超响应窗口，rule="
                            + str(row.get("rule_name")) + "）");
        } catch (Exception e) {
            // 分册 00 降级矩阵：runtime-monitor 不可用 → 仅落库 + 补投
            log.warn("DQ 告警升级推送失败（降级仅落库）: {}", e.getMessage());
        }
    }

    /** 兼容 PG TIMESTAMP 的多种 JDBC 归物形态（OffsetDateTime/LocalDateTime/Instant/epoch millis）。 */
    static Instant parseInstant(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Instant) {
            return (Instant) v;
        }
        if (v instanceof OffsetDateTime) {
            return ((OffsetDateTime) v).toInstant();
        }
        if (v instanceof java.sql.Timestamp) {
            return ((java.sql.Timestamp) v).toInstant();
        }
        if (v instanceof java.util.Date) {
            return ((java.util.Date) v).toInstant();
        }
        if (v instanceof LocalDateTime) {
            return ((LocalDateTime) v).atZone(ZoneId.systemDefault()).toInstant();
        }
        if (v instanceof Number) {
            return Instant.ofEpochMilli(((Number) v).longValue());
        }
        try {
            return OffsetDateTime.parse(v.toString()).toInstant();
        } catch (Exception ignore) {
            return null;
        }
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    // 暴露升迁响应窗口，供运维/UI 明示降级口径复用（常量对齐规格响应时限）。
    public static Duration responseSlaFor(String level) {
        if (level == null) {
            return null;
        }
        switch (level.trim().toUpperCase(Locale.ROOT)) {
            case "P0": return Duration.of(5, ChronoUnit.MINUTES);
            case "P1": return Duration.of(15, ChronoUnit.MINUTES);
            case "P2": return Duration.ofHours(1);
            case "P3": return Duration.ofHours(24);
            default:  return null;
        }
    }
}
