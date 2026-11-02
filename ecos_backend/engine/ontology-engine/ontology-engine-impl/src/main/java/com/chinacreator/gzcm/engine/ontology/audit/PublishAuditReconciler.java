package com.chinacreator.gzcm.engine.ontology.audit;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 发布审计对账器（§2.9.4④ L95 读回；#147AD）。
 *
 * <p><b>它解决的问题</b>：发布侧投递审计只知「总线已受理」，据此只能把审计腿记
 * {@code SUCCEEDED}+「待对账」。本类把这条悬空记录收敛成四态之一，供读面回答
 * 「这次发布的审计到底落库没有」。</p>
 *
 * <p><b>四态与判据（fail-closed）</b>：</p>
 * <ul>
 *   <li>{@link Verdict#NOT_AVAILABLE} —— 读回口未装配。语义是「无法对账」，<b>不是</b>「没写」；</li>
 *   <li>{@link Verdict#MISSING} —— 查得到通道、查无此行（投递已受理但落库未发生/被丢）；</li>
 *   <li>{@link Verdict#PERSISTED} —— 查到行，附 {@code apply_status}/{@code verification_status}；</li>
 *   <li>{@link Verdict#UNKNOWN} —— 查询异常或缺 {@code eventId}。<b>查询失败绝不判 PERSISTED</b>，
 *       也不判 MISSING（那是把不确定当否定）。</li>
 * </ul>
 *
 * <p><b>边界</b>：本类只读，不改权威库、不改发布态；异步窗口永远存在（事件在途时必然是 MISSING），
 * 故它回答的是「此刻是否已落库」，不是「最终一定落库」。</p>
 *
 * @author ECOS Ontology #147AD
 */
@Component
public class PublishAuditReconciler {

    private static final Logger log = LoggerFactory.getLogger(PublishAuditReconciler.class);

    /** 对账结论四态。 */
    public enum Verdict {
        NOT_AVAILABLE,
        MISSING,
        PERSISTED,
        UNKNOWN
    }

    /**
     * 对账结果。
     *
     * @param verdict 四态之一
     * @param detail  人读原因（禁为空，读面据此解释为何不是 PERSISTED）
     */
    public record Reconciliation(Verdict verdict, String detail) {
    }

    private final ObjectProvider<PublishAuditLookup> lookupProvider;

    public PublishAuditReconciler(ObjectProvider<PublishAuditLookup> lookupProvider) {
        this.lookupProvider = lookupProvider;
    }

    /**
     * 对账单个发布事件。
     *
     * @param auditEventId 发布事件标识（{@code ont-pub-<versionId>-<versionNo>}）
     * @return 四态结论；任何不确定都落在 UNKNOWN/NOT_AVAILABLE，绝不冒充当已落库
     */
    public Reconciliation reconcile(String auditEventId) {
        if (auditEventId == null || auditEventId.isBlank()) {
            return new Reconciliation(Verdict.UNKNOWN, "缺 eventId，无法对账");
        }
        PublishAuditLookup lookup = lookupProvider == null ? null : lookupProvider.getIfAvailable();
        if (lookup == null) {
            return new Reconciliation(Verdict.NOT_AVAILABLE, "审计读回口未装配（禁按已审计理解）");
        }
        try {
            Optional<PublishAuditLookup.AuditRow> row = lookup.findByEventId(auditEventId.trim());
            if (row.isEmpty()) {
                return new Reconciliation(Verdict.MISSING,
                        "审计事件未落库（投递已受理但查无此行；异步窗口内也属此态）");
            }
            PublishAuditLookup.AuditRow audit = row.get();
            return new Reconciliation(Verdict.PERSISTED,
                    "apply=" + nullSafe(audit.applyStatus()) + ", verification=" + nullSafe(audit.verificationStatus()));
        } catch (RuntimeException e) {
            log.warn("审计对账读回失败(eventId={})，按 UNKNOWN 处理（不判已落库）: {}", auditEventId, e.getMessage());
            return new Reconciliation(Verdict.UNKNOWN, "审计读回失败: " + e.getMessage());
        }
    }

    private static String nullSafe(String value) {
        return value == null ? "-" : value;
    }
}
