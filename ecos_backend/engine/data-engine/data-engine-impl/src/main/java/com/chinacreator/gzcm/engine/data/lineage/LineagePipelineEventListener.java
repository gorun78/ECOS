package com.chinacreator.gzcm.engine.data.lineage;

import com.chinacreator.gzcm.common.event.PipelineEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * F02-11 血缘派生事件监听器 — 消费 {@link PipelineEvent} 中的"管道执行成功"事件，
 * 对 payload 携带（或携带于 {@code PipelineEvent.pipelineId}）的 definitionId
 * 触发 {@link LineageDerivationService#deriveFromDefinition}，把
 * source / transform / sink 三节点与边落入 {@code ecos_data.ecos_data_lineage_node/edge}。
 *
 * <p>事件类型 whitelist（与 DqPipelineEventListener 同口径）：</p>
 * <ul>
 *   <li>{@link PipelineEvent.EventType#TRANSFORM_COMPLETED}</li>
 *   <li>{@link PipelineEvent.EventType#CLEANSING_COMPLETED}</li>
 *   <li>{@link PipelineEvent.EventType#COLLECTION_COMPLETED}</li>
 * </ul>
 *
 * <p>设计备注：common-api {@link PipelineEvent.EventType} 无 PIPELINE_EXECUTION_SUCCEEDED
 * 常量（禁改 — API 只增不改）；transitive 完成事件即代表 DAG 全部节点执行成功，
 * 是"管道执行成功"的既有代表事件。</p>
 *
 * <p>异常隔离：单条事件失败仅 warn 不吞，不阻断主流程；异步路径由 {@code @Async} 走
 * gateway {@code @EnableAsync}（同一容器的既有约定，与 DqPipelineEventListener 同型）。</p>
 */
@Component
public class LineagePipelineEventListener {

    private static final Logger log = LoggerFactory.getLogger(LineagePipelineEventListener.class);

    private final LineageDerivationService derivationService;

    public LineagePipelineEventListener(LineageDerivationService derivationService) {
        this.derivationService = derivationService;
    }

    /**
     * 监听管道成功事件 — 异步触发行血缘派生。
     *
     * <p>producer 侧约定：{@code PipelineEvent.pipelineId} 字段携带
     * <b>definitionId</b>（F02-11 publisher 位置在 PipelineExecutionService 成功分支，
     * 由后续批次接通；本监听器不依赖具体发布点，可在任意来源的 PipelineEvent 上独立生效）。</p>
     */
    @Async
    @EventListener
    public void onPipelineEvent(PipelineEvent event) {
        if (event == null || event.getEventType() == null) {
            return;
        }
        PipelineEvent.EventType type = event.getEventType();
        if (type != PipelineEvent.EventType.TRANSFORM_COMPLETED
                && type != PipelineEvent.EventType.CLEANSING_COMPLETED
                && type != PipelineEvent.EventType.COLLECTION_COMPLETED) {
            return; // 非管道成功事件（含 PIPELINE_FAILED / WRITE_CHANNEL_WRITE 等）不派生
        }
        String definitionId = event.getPipelineId();
        if (definitionId == null || definitionId.isBlank()) {
            log.debug("LineagePipelineEventListener: PipelineEvent 未携带 pipelineId，跳过派生 eventType={}", type);
            return;
        }
        try {
            LineageDerivationService.DerivationSummary summary =
                    derivationService.deriveFromDefinition(definitionId);
            log.info("LineagePipelineEventListener: 派生完成 definitionId={} nodes={} edges={}",
                    definitionId, summary.nodeCount(), summary.edgeCount());
        } catch (Exception e) {
            log.error("LineagePipelineEventListener: 血缘派生失败 definitionId={}: {}",
                    definitionId, e.getMessage(), e);
        }
    }
}
