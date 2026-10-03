package com.chinacreator.gzcm.runtime.audit;

/**
 * 审计事件兜底投递入口（C.5.4 审计兜底 SLA / 消除 G5-7）。
 * <p>
 * 供现有落 audit 的调用方复用：publish 到 {@code KafkaTopics.AUDIT} 成功 → {@code true}；
 * 同步调用方投递失败 → 本地 {@code ecos_runtime_audit_retry} 落行（不阻塞业务、不回滚业务写），
 * 返回 {@code false}，之后由 {@code AuditRetryTask} 指数退避重放，3 次仍失败转
 * critical 告警（ack 必须人工）。
 * <p>
 * 红线（C.5.4）：兜底失败<b>不回滚业务写</b>（可用性）；SLA：失败到落库告警 <=15min。
 */
public interface AuditSink {

    /**
     * 尝试向 {@code ecos.audit} topic 投递一条审计事件 JSON。
     *
     * @param eventType  事件类型（如 MONITOR_ALERT_ACK / DQ_ALERT_DISPATCH）
     * @param payloadJson 审计事件 JSON 串（须为合法 JSON）
     * @return true=publish 成功；false=投递失败已转兜底重试（业务方不应因此中断或回滚）
     */
    boolean tryPublish(String eventType, String payloadJson);
}
