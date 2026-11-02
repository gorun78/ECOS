package com.chinacreator.gzcm.engine.ontology.audit;

import java.util.Optional;

/**
 * 发布审计的<b>读回口</b>（§2.9.4④ L95 对账读回；#147AD）。
 *
 * <p>为什么需要它：发布侧投递审计事件拿到的是「总线已受理」，而 {@code OntologyVersionService.auditPublish}
 * 只能把腿记成 {@code SUCCEEDED}+原因「待对账」——全仓**没有 reader** 去确认那行到底落没落库，
 * 「待对账」于是长悬。本接口就是那个 reader 的端口，实现只做一件事：
 * 按 {@code audit_event_id} 查审计落库行。</p>
 *
 * <p><b>契约硬约束</b>：① 行不存在返回 {@link Optional#empty()}（缺失 ≠ 失败）；
 * ② <b>查询异常必须原样抛出</b>，由调用方落 {@code UNKNOWN}——吞异常返回 empty 会把
 * 「读不到」误报成「没写过」，那是把不确定当否定的假证据。</p>
 *
 * @author ECOS Ontology #147AD
 */
public interface PublishAuditLookup {

    /** 审计落库行的治理态投影（只取对账需要的两列）。 */
    record AuditRow(String applyStatus, String verificationStatus) {
    }

    /**
     * 按事件标识回读审计落库行。
     *
     * @param auditEventId 发布事件标识（{@code ont-pub-<versionId>-<versionNo>}）
     * @return 命中返回该行治理态；未命中返回 empty；查询故障抛运行时异常
     */
    Optional<AuditRow> findByEventId(String auditEventId);
}
