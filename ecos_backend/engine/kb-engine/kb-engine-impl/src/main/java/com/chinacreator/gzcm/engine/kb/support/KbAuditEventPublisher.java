package com.chinacreator.gzcm.engine.kb.support;

import com.chinacreator.gzcm.common.context.TenantContextHolder;
import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.common.security.header.ServiceContext;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * kb 域真实审计发布器 — F04-15「审计与事件收口 runtime-event」共享底座
 * （2026-10-12 复盘收口：GraphSyncController / KnowledgeIngestController / KnowledgeDocIngestService /
 * KbEntityInstanceExtractionService / KgMapperService / KgSyncServiceImpl 六处 log-only 伪审计统一改走本类）。
 *
 * <p><b>复刻先例</b> {@code KnowledgeNavSecurityEngineClient#audit}（模块内唯一真实 EventBus 审计发布点，
 * KbAuditChainTest 验收）：
 * <ol>
 *   <li>正常路径：{@link EventBusService#publish} 发 {@link KafkaTopics#AUDIT}，
 *       event 携带 eventId/timestamp/userId/tenantId/action/resource/attr 六字段；</li>
 *   <li>发布失败路径按既有先例处理：eventBus 未装配或 publish 抛异常 → 先本地结构化 log 兜底
 *       （证据留应用日志），再抛 {@link KbErrorCodes#EVENT_BASE_UNAVAILABLE}（ECOS-KB-022），
 *       <b>不静默吞审计</b>；</li>
 *   <li>{@code isKafkaAvailable()==false} 但内存总线有效 → 仍走 publish（内存降级符合
 *       EventBusService 契约，F05-06 缺陷族不新造语义）。</li>
 * </ol>
 *
 * <p>调用方（写已落库的位点）捕获 {@link KbErrorCodes.DomainException} 后按先例调用方
 * （{@code NavTaxonServiceImpl}）处理：降级 log.error、不阻塞业务主链——本类只负责"发不出必抛"。</p>
 */
@Component
public class KbAuditEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KbAuditEventPublisher.class);

    /** 可选 — 无 runtime-event 环境（纯单测/未装配）保持 null，audit fail-closed。 */
    private final EventBusService eventBus;

    public KbAuditEventPublisher(@Autowired(required = false) EventBusService eventBus) {
        this.eventBus = eventBus;
    }

    /**
     * 审计事件 — 经 {@link EventBusService} 发 {@link KafkaTopics#AUDIT}（复刻先例语义）。
     *
     * @param action   业务动作标识（如 {@code kg_sync_full_submit}）
     * @param attr     关键属性（字符串明细或 Map，可空）
     * @param resource 资源域标识（如 {@code kb-graph-sync}）
     * @throws KbErrorCodes.DomainException eventBus 缺失或 publish 抛异常时（ECOS-KB-022）
     */
    public void audit(String action, Object attr, String resource) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", UUID.randomUUID().toString());
        event.put("timestamp", System.currentTimeMillis());
        event.put("userId", currentUserIdOrAnonymous());
        event.put("tenantId", currentTenantIdOrDefault());
        event.put("action", action);
        event.put("resource", resource);
        event.put("attr", attr == null ? Map.of() : attr);

        if (eventBus == null) {
            // eventBus 未装配 — 本地 log 兜底 + fail-closed（先例同款）
            localFallbackWrite(action, event);
            throw KbErrorCodes.EVENT_BASE_UNAVAILABLE.exception(
                    "audit 总线不可用: eventBus=null action=" + action);
        }
        boolean kafka = eventBus.isKafkaAvailable();
        try {
            eventBus.publish(KafkaTopics.AUDIT, event);
        } catch (Exception ex) {
            localFallbackWrite(action, event);
            throw KbErrorCodes.EVENT_BASE_UNAVAILABLE.exception(
                    "audit publish failed (eventBus.publish threw): action=" + action
                            + " kafkaAvailable=" + kafka
                            + " reason=" + ex.getMessage());
        }
        if (!kafka) {
            // 内存总线降级 — log 提示便于运维感知（不阻塞），符合 EventBusService 内存 fallback 契约
            log.debug("KbAuditEventPublisher.audit via memory bus: action={} eventId={}",
                    action, event.get("eventId"));
        }
    }

    /** 本地兜底 — 结构化 log 写（审计证据落地；复刻先例 localFallbackWrite）。 */
    private boolean localFallbackWrite(String action, Map<String, Object> event) {
        try {
            log.warn("[AUDIT-LOCAL-FALLBACK] topic={} action={} event={}",
                    KafkaTopics.AUDIT, action, event);
            return true;
        } catch (Exception ex) {
            log.warn("KbAuditEventPublisher local fallback log failed: action={} reason={}",
                    action, ex.getMessage());
            return false;
        }
    }

    // ── helpers（复刻 KnowledgeNavSecurityEngineClient 真实身份取值链） ──────────

    /** 从 {@link ServiceContext} 取真实 userId；缺失时回退 "anonymous"。 */
    private String currentUserIdOrAnonymous() {
        try {
            ServiceContext ctx = ServiceContext.current();
            if (ctx != null && ctx.getUserId() != null && !ctx.getUserId().isBlank()) {
                return ctx.getUserId();
            }
            org.springframework.security.core.Authentication auth =
                    org.springframework.security.core.context.SecurityContextHolder.getContext()
                            .getAuthentication();
            if (auth != null && auth.isAuthenticated() && auth.getName() != null
                    && !auth.getName().isBlank()) {
                return auth.getName();
            }
        } catch (Exception ignored) {
            // 客户端解析失败返回 anonymous，不抛
        }
        return "anonymous";
    }

    /** 从 {@link TenantContextHolder} 取真实 tenantId；缺失时回退 "default"。 */
    private String currentTenantIdOrDefault() {
        try {
            String t = TenantContextHolder.getTenantId();
            if (t != null && !t.isBlank()) {
                return t;
            }
        } catch (Exception ignored) {
            // 无租户上下文走 default
        }
        return "default";
    }
}
