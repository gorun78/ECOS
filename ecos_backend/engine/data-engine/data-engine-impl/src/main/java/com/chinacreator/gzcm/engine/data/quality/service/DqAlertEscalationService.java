package com.chinacreator.gzcm.engine.data.quality.service;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.chinacreator.gzcm.engine.data.quality.dao.DqAlertEscalationDao;
import com.chinacreator.gzcm.runtime.core.alert.IAlertService;

/**
 * DQ 告警升级时限状态机（F02-10，P1）。
 *
 * <p>由 runtime-task 统一注册的周期任务 {@code dq-alert-escalation}（每 1 min cron）
 * 每次调 {@link #executeEscalationScan(Instant)}，本方法内部走完
 * P2→P1（5 min 未 acknowledge） / P1→P0（15 min 未 acknowledge）两条升级，
 * 仅落库 {@code escalated_to} 留痕 + runtime IAlertService 统一推送，
 * 本身不注册 @Scheduled（铁律 §2.5 #4：data-engine 禁自调度）。</p>
 *
 * <p>升降级矩阵：</p>
 * <pre>
 *   P2 未 acknowledge 超 P2_ACK_SLA_MINUTES（5 min）→ P1
 *   P1 未 acknowledge 超 P1_ACK_SLA_MINUTES（15 min）→ P0
 *   P0 / P3 不进一步升级
 * </pre>
 *
 * <p>幂等安全：</p>
 * <ul>
 *   <li>UPDATE 带 {@code status IN ('PENDING','NOTIFIED')} + {@code id=?} 乐观条件，
 *       与被确认 / 解决的并发更新互不影响；</li>
 *   <li>alert_level 已升级到目标级别时不再参与"下轮"挑选（WHERE alert_level = fromLevel
 *       已经天然过滤），不会死循环；</li>
 *   <li>runtime push 失败仍写 escalated_to 留痕，只记 WARN，不阻断主流程。</li>
 * </ul>
 *
 * <p>Bean 名 {@code ecosDqAlertEscalationService}（铁律 1.3 防多 Bean，不 implements 既有接口）。</p>
 *
 * @author ECOS Data Engine Team / F02-10
 */
@Service("ecosDqAlertEscalationService")
public class DqAlertEscalationService {

    private static final Logger log = LoggerFactory.getLogger(DqAlertEscalationService.class);

    /** P2 未 acknowledge 超 5 分钟升级为 P1（F02-10 D.10-2 判据）。 */
    public static final long P2_ACK_SLA_MINUTES = 5L;
    /** P1 未 acknowledge 超 15 分钟升级为 P0（F02-10 D.10-3 判据）。 */
    public static final long P1_ACK_SLA_MINUTES = 15L;

    private final ObjectProvider<IAlertService> alertServiceProvider;
    private final ObjectProvider<DqSecurityService> securityService;
    private final DqAlertEscalationDao alertEscalationDao;

    @Autowired
    public DqAlertEscalationService(ObjectProvider<IAlertService> alertServiceProvider,
                                    ObjectProvider<DqSecurityService> securityService,
                                    DqAlertEscalationDao alertEscalationDao) {
        this.alertServiceProvider = alertServiceProvider;
        this.securityService = securityService;
        this.alertEscalationDao = alertEscalationDao;
    }

    /**
     * runtime-task 入口 — Instant 时钟 + 单批次扫描。cron 由 runtime-task 触发，
     * 本方法幂等可重复调用；同一批告警不会二次升级（见类注释幂等安全）。
     *
     * @return 本批次升级的告警总数（0 = 无候选 / DB 不可用 / 无 SLA 突破）
     */
    public int executeEscalationScan(Instant now) {
        return executeEscalationScan(LocalDateTime.ofInstant(now, ZoneId.systemDefault()));
    }

    /**
     * 核心扫描（可注入 LocalDateTime 便于单测推进时钟）。见 {@link #executeEscalationScan(Instant)}。
     */
    public int executeEscalationScan(LocalDateTime now) {
        int p2Escalated = escalateLevel(now, "P2", "P1", P2_ACK_SLA_MINUTES);
        int p1Escalated = escalateLevel(now, "P1", "P0", P1_ACK_SLA_MINUTES);
        return p2Escalated + p1Escalated;
    }

    /**
     * 单级升级：从 {@code fromLevel} 未 acknowledge 超 SLA 的告警升级到 {@code toLevel}。
     *
     * <p>SQL 关键点：{@code ack_at IS NULL} 表示未 acknowledge；
     * {@code created_at < deadline} 用参数化比较，避开字符串拼接（IR05/MC03）。</p>
     *
     * @return 本级别成功升级的告警数
     */
    int escalateLevel(LocalDateTime now,
                      String fromLevel, String toLevel, long slaMinutes) {
        LocalDateTime deadline = now.minusMinutes(slaMinutes);
        Timestamp deadlineTs = Timestamp.valueOf(deadline);
        List<Map<String, Object>> rows;
        try {
            rows = alertEscalationDao.findUnacknowledged(fromLevel, deadlineTs);
        } catch (RuntimeException e) {
            log.error("DqAlertEscalation fetch {} candidates failed: {}", fromLevel, e.getMessage(), e);
            return 0;
        }
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        int escalated = 0;
        for (Map<String, Object> row : rows) {
            try {
                if (escalateOne(row, fromLevel, toLevel)) {
                    escalated++;
                }
            } catch (RuntimeException e) {
                log.error("DqAlertEscalation single escalation failed: level={}, alertId={}, err={}",
                        fromLevel, row.get("id"), e.getMessage(), e);
                // 单条失败不中断本批次扫描
            }
        }
        return escalated;
    }

    /**
     * 单告警升级：UPDATE 乐观锁写 escalated_to + status='ESCALATED' → runtime 推送 → 审计。
     * 全失败（n=0 / DB 异常）返回 false 不计数。
     */
    private boolean escalateOne(Map<String, Object> row,
                                String fromLevel, String toLevel) {
        String alertId = asString(row.get("id"));
        String ruleId = asString(row.get("ruleId"));
        String assetId = asString(row.get("assetId"));
        String ruleName = asString(row.get("ruleName"));
        String message = asString(row.get("message"));

        if (alertId == null || alertId.isBlank()) {
            log.warn("DqAlertEscalation 缺 alertId，跳过: ruleId={}, level={}", ruleId, fromLevel);
            return false;
        }
        String upgradeMsg = "[DQ Alert Escalation] "
                + (ruleName == null ? ruleId : ruleName)
                + " " + fromLevel + " -> " + toLevel
                + "（未 acknowledge 超出 SLA 升降级）"
                + (assetId == null || assetId.isBlank() ? "" : " asset=" + assetId)
                + (message == null || message.isBlank() ? "" : "；摘要: " + message);

        int rows;
        try {
            rows = alertEscalationDao.markEscalated(toLevel, alertId);
            if (rows == 0) {
                log.warn("DqAlertEscalation update no-op（并发已 ack/resolve）: alertId={}", alertId);
                return false;
            }
        } catch (RuntimeException e) {
            log.error("DqAlertEscalation update failed: alertId={}, err={}", alertId, e.getMessage(), e);
            return false;
        }

        boolean pushed = pushEscalation(alertId, ruleId, upgradeMsg, toLevel);

        DqSecurityService sec = securityService.getIfAvailable();
        if (sec != null) {
            sec.auditWrite("DQ_ALERT_ESCALATION", alertId,
                    (fromLevel + "->" + toLevel) + (pushed ? ", pushed" : ", push-failed"));
        }
        return true;
    }

    /**
     * runtime IAlertService 推送（决策 #6/#7：不直连 IM/Kafka）；
     * Bean 缺失或子类异常均降级为仅落库 + WARN，升级动作本体已成功，
     * 后续 runtime-monitor 补投策略由 F02-10 分册 00 降级矩阵吸收。
     */
    private boolean pushEscalation(String alertId, String ruleId, String message, String toLevel) {
        IAlertService svc = alertServiceProvider.getIfAvailable();
        if (svc == null) {
            log.warn("DqAlertEscalation runtime IAlertService 不可用（降级仅落库）: alertId={}", alertId);
            return false;
        }
        try {
            // runtime 5 参签名: (ruleId, alertType, nodeId, taskId, message)
            svc.triggerAlert(ruleId, "DQ_ALERT_ESCALATION",
                    alertId, alertId, message);
            return true;
        } catch (IAlertService.AlertException e) {
            log.warn("DqAlertEscalation push failed (degrade): alertId={}, err={}", alertId, e.getMessage());
            return false;
        } catch (RuntimeException e) {
            log.warn("DqAlertEscalation push unexpected: alertId={}, err={}", alertId, e.getMessage());
            return false;
        }
    }

    /** 便于验收/monitor 出口：SLA 矩阵当前值。 */
    public static Map<String, Long> currentSlaMatrix() {
        return Map.of(
                "P2_ACK_SLA_MINUTES", P2_ACK_SLA_MINUTES,
                "P1_ACK_SLA_MINUTES", P1_ACK_SLA_MINUTES);
    }

    private static String asString(Object o) {
        return o == null ? null : o.toString();
    }
}
