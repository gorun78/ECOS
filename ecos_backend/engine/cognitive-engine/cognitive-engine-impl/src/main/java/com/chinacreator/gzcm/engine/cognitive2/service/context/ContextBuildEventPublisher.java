package com.chinacreator.gzcm.engine.cognitive2.service.context;

import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.engine.cognitive.api.context.ContextBuildRequest;
import com.chinacreator.gzcm.engine.cognitive.api.context.ContextBuildResult;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Context 构建完成事件发布器 —— 走既有 {@link EventBusService} 单通道
 *（runtime-event 公共底座；Kafka 未启用时自动降级内存总线，铁律 §2.5 禁自建 KafkaTemplate）。
 *
 * <p>topic = {@link KafkaTopics#COGNITIVE}（ecos.cognitive，聚合名 "cognition"
 * 经 {@link KafkaTopics#topicForAggregate} 路由，<b>不新增 topic</b>，容器/主题基线不变）。</p>
 *
 * <p>载荷纪律（§5.2 / §12.2）：只带 contextDigest / scenarioId / blueprintId+version /
 * 来源引用 ID 串与 traceId；<b>禁带完整上下文正文或敏感数据</b>。
 * publish 异常吞掉 WARN 不阻塞主流程（业务先于事件，最终一致；同 MentalEventPublisher 口径）。</p>
 */
@Component
public class ContextBuildEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ContextBuildEventPublisher.class);

    /** 事件类型字面量（§13 认知 Cognition 事件族 additive 注册：请求态上下文构建完成）。 */
    public static final String EVENT_TYPE_CONTEXT_BUILT = "CognitiveContextBuilt";

    private static final String SOURCE = "cognitive-engine";

    private final EventBusService eventBusService;

    public ContextBuildEventPublisher(EventBusService eventBusService) {
        this.eventBusService = eventBusService;
    }

    /** 发布构建完成事件（成功路径；失败路径抛 ContextBuildException 不经过本通道）。 */
    public void publishBuilt(ContextBuildResult result) {
        if (eventBusService == null) {
            log.warn("Context 事件无法发送: EventBusService 未装配, eventType={}", EVENT_TYPE_CONTEXT_BUILT);
            return;
        }
        try {
            Map<String, Object> payload = envelope();
            payload.put("scenarioId", result.getScenarioId());
            payload.put("requestId", result.getRequestId());
            payload.put("contextDigest", result.getContextDigest());
            payload.put("ready", result.isReady());
            payload.put("truncated", result.isTruncated());
            ContextBuildRequest.BlueprintRef bp = result.getBlueprintRef();
            if (bp != null) {
                payload.put("blueprintId", bp.getBlueprintId());
                payload.put("blueprintVersion", bp.getVersion());
            }
            ContextBuildRequest.Scope scope = result.getScopeRef();
            if (scope != null) {
                payload.put("tenantId", scope.getTenantId());
            }
            if (result.getPack() != null) {
                // 只带来源引用 ID 串（引用回溯锚点），禁带正文
                payload.put("sourceRefs", result.getPack().getSources().stream()
                        .map(s -> s.getSourceType() + ":" + s.getSourceId())
                        .toList());
            }
            eventBusService.publish(KafkaTopics.COGNITIVE, payload);
            log.info("Context 构建事件已发布: digest={} scenarioId={}",
                    result.getContextDigest(), result.getScenarioId());
        } catch (Exception e) {
            log.warn("Context 构建事件发布失败 (ignored, 不阻塞主流程): err={}", e.getMessage());
        }
    }

    private Map<String, Object> envelope() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventType", EVENT_TYPE_CONTEXT_BUILT);
        payload.put("eventId", "ctxevt-" + java.util.UUID.randomUUID());
        payload.put("timestamp", Instant.now().toString());
        payload.put("source", SOURCE);
        return payload;
    }
}
