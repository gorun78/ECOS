package com.chinacreator.gzcm.engine.cognitive2.observability;

import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.common.exception.BusinessException;
import com.chinacreator.gzcm.engine.cognitive.api.observability.CognitiveEventLog;
import com.chinacreator.gzcm.engine.cognitive.api.observability.CognitiveEventLog.Op;
import com.chinacreator.gzcm.engine.cognitive.api.observability.CognitiveEventLog.Result;
import com.chinacreator.gzcm.engine.cognitive.api.CognitiveErrorCode;
import com.chinacreator.gzcm.engine.cognitive2.service.CognitiveAuditFallback;
import com.chinacreator.gzcm.engine.cognitive2.service.scenario.CognitiveGuardException;
import com.chinacreator.gzcm.runtime.core.monitor.metrics.IMetricRecorder;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 认知域可观测性出口（详细设计-05 C.7-2 事件日志 + C.7-3 审计 + C.7-4 指标）。
 *
 * <p>指标只经 runtime-monitor 的 {@link IMetricRecorder} 门面暴露（铁律 §2.5-5 禁引擎自建注册表）；
 * 宿主未装配实现时降级为 DEBUG 并保持计数语义可见，禁静默丢弃、禁在引擎内 new 一套 registry。</p>
 *
 * <p>审计三段式 fail-closed（铁律 §2.4#5）：主通道 runtime-event {@code ecos.audit} →
 * {@link CognitiveAuditFallback} 本地兜底 → 双通道均不可记录则抛 {@code ECOS-COG-504} 拒绝业务动作，
 * 禁 "log.warn + continue" 式假审计。</p>
 */
@Component
public class CognitiveObservability {

    private static final Logger log = LoggerFactory.getLogger(CognitiveObservability.class);

    private final ObjectProvider<IMetricRecorder> recorderProvider;
    private final ObjectProvider<EventBusService> eventBusProvider;
    private final CognitiveAuditFallback auditFallback;

    public CognitiveObservability(ObjectProvider<IMetricRecorder> recorderProvider,
                                 ObjectProvider<EventBusService> eventBusProvider,
                                 CognitiveAuditFallback auditFallback) {
        this.recorderProvider = recorderProvider;
        this.eventBusProvider = eventBusProvider;
        this.auditFallback = auditFallback;
    }

    /** 计时起点（纳秒），传入 {@link #event} 折算为毫秒。 */
    public long start() {
        return System.nanoTime();
    }

    /** 记录一条 C.7-2 事件行；{@code agent} 无数据源时传 null（编解码落 {@code -}，禁伪造）。 */
    public void event(Op op, String agent, String scenario, String mind, long startedAtNanos, Result result) {
        if (result == null) {
            return;
        }
        log.info(CognitiveEventLog.format(op, agent, scenario, mind, costMs(startedAtNanos), result));
    }

    /** guard 异常结局：仅 4xx 拒答与 DG-9 stub 进 EVT，故障码（500/501/502/504）由错误日志承载。 */
    public void eventGuard(Op op, String agent, String scenario, String mind, long startedAtNanos,
            CognitiveGuardException e) {
        event(op, agent, scenario, mind, startedAtNanos, CognitiveEventLog.forErrorCode(e.errorCode));
    }

    /** 计数 {@code delta} 次；{@code delta<=0} 不产生事件。 */
    public void counter(String name, int delta) {
        if (delta <= 0) {
            return;
        }
        IMetricRecorder recorder = recorderProvider.getIfAvailable();
        if (recorder == null) {
            log.debug("metric {} +{} 未计入：runtime-monitor IMetricRecorder 未装配（降级 DEBUG，禁引擎自建注册表）",
                    name, delta);
            return;
        }
        for (int i = 0; i < delta; i++) {
            recorder.increment(name);
        }
    }

    private static long costMs(long startedAtNanos) {
        return Math.max(0L, (System.nanoTime() - startedAtNanos) / 1_000_000L);
    }

    /**
     * C.7-3 审计留痕（fail-closed 三段式）：诊断/模拟/配置写各发一条 {@code ecos.audit}。
     *
     * @throws BusinessException 主通道与本地兜底均不可用（ECOS-COG-504），调用方动作随之拒绝
     */
    public void audit(String action, String resource, String detail) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("action", action);
        event.put("resource", resource);
        event.put("result", "success");
        event.put("detail", detail);
        event.put("userId", "system");
        event.put("timestamp", LocalDateTime.now().toString());
        if (tryPublish(event)) {
            return;
        }
        try {
            auditFallback.write(event);
        } catch (Exception fe) {
            log.error("认知审计兜底同样失败，按 fail-closed 拒绝业务动作: action={} err={}",
                    action, fe.getMessage());
            throw new BusinessException(500, CognitiveErrorCode.E504_INTERNAL
                    + ": 审计主通道与本地兜底均不可用，操作已拒绝（fail-closed）: action=" + action);
        }
    }

    private boolean tryPublish(Map<String, Object> event) {
        EventBusService bus = eventBusProvider.getIfAvailable();
        if (bus == null) {
            log.warn("认知审计主通道未装配（EventBusService 缺失），转本地兜底");
            return false;
        }
        try {
            bus.publish(KafkaTopics.AUDIT, event);
            return true;
        } catch (Exception e) {
            log.warn("认知审计主通道发布失败，转本地兜底: err={}", e.getMessage());
            return false;
        }
    }
}
