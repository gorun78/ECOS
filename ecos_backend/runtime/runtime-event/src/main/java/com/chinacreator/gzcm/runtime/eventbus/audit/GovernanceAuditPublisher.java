package com.chinacreator.gzcm.runtime.eventbus.audit;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.chinacreator.gzcm.common.context.TenantContextHolder;
import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.common.security.header.ServiceContext;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;

/**
 * 治理审计统一发布口（§2.9.9 G5「其余发布域的 git_commit_sha」；#147BV）。
 *
 * <p><b>为什么放 runtime-event</b>：kb / workspace 各自在引擎内写了一份「发 {@code ecos.audit}
 * + 缺 {@code id} 被消费端丢弃」的发布器（#147BB 才补上 {@code id}），ai / cognitive 两个发布域
 * 至今零治理审计。同一形状再抄两遍即第三、第四份口径，故把「事件面」收口到本类，
 * 调用方只提供<b>内容</b>（动作/资源/结果/治理列）。</p>
 *
 * <p><b>事件形状（与 {@code AuditKafkaConsumer} 消费面逐键对齐）</b>：
 * <ul>
 *   <li>{@code id} —— 消费端主键判重键，<b>缺则整条丢弃</b>（#147BB 的教训）；毫秒×1000 + 随机后缀；</li>
 *   <li>治理列（{@code AuditEventContract#toColumns()} 的蛇形键：{@code git_repository} /
 *       {@code git_commit_sha} / {@code content_hash} / {@code object_type} …）先铺底；</li>
 *   <li>基础键后写：同名的 {@code action}/{@code resource}/{@code result}/{@code tenantId} 等
 *       以本类为准，<b>防调用方用治理列改写归类</b>（#147BB 定下的压键顺序）。</li>
 * </ul></p>
 *
 * <p><b>失败语义（有意与裁决审计区分）</b>：本类服务于「业务已成功之后补证据」（归档成功即发），
 * 故<b>不抛</b>：通道缺失/发布异常 ⇒ 返回 {@code false} + 结构化 WARN 兜底日志
 * （{@link #LOCAL_FALLBACK_MARKER}），由调用方决定是否告警。裁决类审计的 fail-closed 语义
 * （{@code GuardrailAuditPublisher#auditOrThrow}）不适用于此——把已成功的归档翻成失败
 * 只会制造「业务回滚但 Git 已有提交」的错账。</p>
 *
 * @author ECOS Runtime #147BV
 */
@Component
public class GovernanceAuditPublisher {

    private static final Logger log = LoggerFactory.getLogger(GovernanceAuditPublisher.class);

    /** 本地兜底日志标记（可 grep；"审计没发出去"必须可观测）。 */
    public static final String LOCAL_FALLBACK_MARKER = "[AUDIT-LOCAL-FALLBACK]";

    private final ObjectProvider<EventBusService> eventBusProvider;

    public GovernanceAuditPublisher(ObjectProvider<EventBusService> eventBusProvider) {
        this.eventBusProvider = eventBusProvider;
    }

    /**
     * 发一条治理审计事件。
     *
     * @param action            业务动作标识（如 {@code agent.archive}）
     * @param resource          资源域标识（如 {@code ai-agent}）
     * @param result            结果词（如 {@code SUCCESS}）
     * @param governanceColumns 治理列（{@code AuditEventContract#toColumns()}，可空）
     * @param attr              关键属性摘要（可空；禁放正文/凭据）
     * @return true＝已交事件总线；false＝通道缺失或发布失败（已写本地兜底日志）
     */
    public boolean publish(String action, String resource, String result,
                           Map<String, Object> governanceColumns, Object attr) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("id", String.valueOf(System.currentTimeMillis() * 1000L
                + ThreadLocalRandom.current().nextInt(1000)));
        if (governanceColumns != null && !governanceColumns.isEmpty()) {
            event.putAll(governanceColumns);
        }
        // 基础键后写：同名键以本类为准，禁调用方借治理列改写归类（#147BB 压键顺序）
        event.put("eventId", UUID.randomUUID().toString());
        event.put("timestamp", System.currentTimeMillis());
        event.put("userId", currentUserIdOrAnonymous());
        event.put("tenantId", currentTenantIdOrDefault());
        event.put("action", action);
        event.put("resource", resource);
        if (result != null) {
            event.put("result", result);
        }
        event.put("attr", attr == null ? Map.of() : attr);

        EventBusService bus = eventBusProvider == null ? null : eventBusProvider.getIfAvailable();
        if (bus == null) {
            localFallback(action, event, "EventBusService 未装配");
            return false;
        }
        boolean kafka = bus.isKafkaAvailable();
        try {
            bus.publish(KafkaTopics.AUDIT, event);
            if (!kafka) {
                log.debug("治理审计经内存总线分发（Kafka 不可用）: action={} eventId={}",
                        action, event.get("eventId"));
            }
            return true;
        } catch (Exception e) {
            localFallback(action, event, e.getMessage());
            return false;
        }
    }

    private void localFallback(String action, Map<String, Object> event, String reason) {
        log.warn("{} topic={} action={} reason={} event={}",
                LOCAL_FALLBACK_MARKER, KafkaTopics.AUDIT, action, reason, event);
    }

    private static String currentUserIdOrAnonymous() {
        try {
            ServiceContext ctx = ServiceContext.current();
            if (ctx != null && ctx.getUserId() != null && !ctx.getUserId().isBlank()) {
                return ctx.getUserId();
            }
        } catch (Exception ignored) {
            // 上下文解析失败按匿名记，不影响审计投递
        }
        return "anonymous";
    }

    private static String currentTenantIdOrDefault() {
        try {
            String tenant = TenantContextHolder.getTenantId();
            if (tenant != null && !tenant.isBlank()) {
                return tenant;
            }
        } catch (Exception ignored) {
            // 无租户上下文走 default
        }
        return "default";
    }
}
