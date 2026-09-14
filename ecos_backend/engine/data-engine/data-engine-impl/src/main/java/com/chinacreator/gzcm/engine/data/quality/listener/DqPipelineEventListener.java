package com.chinacreator.gzcm.engine.data.quality.listener;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.chinacreator.gzcm.common.event.PipelineEvent;
import com.chinacreator.gzcm.common.event.PipelineEvent.EventType;
import com.chinacreator.gzcm.engine.data.quality.DqScheduleService;
import com.chinacreator.gzcm.engine.data.quality.mapper.DqScheduleMapper;
import com.chinacreator.gzcm.engine.data.quality.model.DqScheduleVO;

/**
 * DQ Pipeline 事件监听器（PMO-48-C T11）— 事件触发源。
 *
 * <p>监听 common-api {@link PipelineEvent} 的"管道执行成功"类事件
 * （{@link EventType#TRANSFORM_COMPLETED} / {@link EventType#COLLECTION_COMPLETED}
 * / {@link EventType#CLEANSING_COMPLETED}），对 {@code trigger_type='EVENT'} 且
 * {@code event_type} 匹配、enabled 的调度计划触发 {@code runRuleBatch}。</p>
 *
 * <p>设计说明：</p>
 * <ul>
 *   <li>{@link PipelineEvent.EventType} 枚举当前无 PIPELINE_EXECUTION_SUCCEEDED 常量（不改
 *       common-api 契约 — 铁律 API 只增不改）；以 TRANSFORM_COMPLETED 作为"管道执行成功"代表
 *       事件（管道 采集→清洗→转换 全程完成的最后一步语义）</li>
 *   <li>{@code @Async} 异步执行（Gateway 已 {@code @EnableAsync}）— 防 Pipeline 发布事件线程
 *       被 runRuleBatch 阻塞主流程；单计划失败 catch 隔离不中断其它计划</li>
 *   <li>安全卡：审计由 {@code DqScheduleServiceImpl.runRuleBatch} 统一走
 *       {@code DqSecurityService.auditWrite}（铁律 2.4 #5）</li>
 * </ul>
 *
 * @author PMO-48-C T11
 */
@Component
public class DqPipelineEventListener {

    private static final Logger log = LoggerFactory.getLogger(DqPipelineEventListener.class);

    /** 视为"管道执行成功"的事件类型集合（管道 采集/清洗/转换 完成节点）。 */
    private static final List<EventType> PIPELINE_SUCCESS_EVENTS = List.of(
            EventType.TRANSFORM_COMPLETED,
            EventType.CLEANSING_COMPLETED,
            EventType.COLLECTION_COMPLETED);

    /** 事件类型 → 调度计划 event_type 常量映射（与 dq_schedule.event_type 字面量对齐）。 */
    private static final List<String> PIPELINE_SUCCESS_EVENT_NAMES = List.of(
            "PIPELINE_EXECUTION_SUCCEEDED",
            "TRANSFORM_COMPLETED",
            "CLEANSING_COMPLETED",
            "COLLECTION_COMPLETED");

    private final DqScheduleMapper scheduleMapper;
    private final DqScheduleService scheduleService;

    public DqPipelineEventListener(DqScheduleMapper scheduleMapper,
                                   DqScheduleService scheduleService) {
        this.scheduleMapper = scheduleMapper;
        this.scheduleService = scheduleService;
    }

    /**
     * 监听管道成功事件 — 异步触发 EVENT 型调度计划。
     *
     * <p>流程：判断事件类型命中 → 取 enabled 且 trigger_type='EVENT' 且 event_type 匹配计划
     * → 逐计划 runRuleBatch（per-plan try/catch 隔离）。</p>
     *
     * @param event PipelineEvent（common-api 契约）
     */
    @Async
    @EventListener
    public void onPipelineEvent(PipelineEvent event) {
        if (event == null || event.getEventType() == null) {
            return;
        }
        EventType type = event.getEventType();
        if (!PIPELINE_SUCCESS_EVENTS.contains(type)) {
            return; // 非管道成功事件（如 PIPELINE_FAILED / DATASOURCE_STATUS_CHANGED）不触发
        }

        List<DqScheduleVO> plans = scheduleMapper.listEnabled().stream()
                .filter(p -> "EVENT".equalsIgnoreCase(p.getTriggerType()))
                .filter(p -> p.getEventType() != null
                        && PIPELINE_SUCCESS_EVENT_NAMES.stream().anyMatch(n -> n.equalsIgnoreCase(p.getEventType())))
                .toList();

        if (plans.isEmpty()) {
            log.debug("DqPipelineEventListener: 无 EVENT 型调度计划命中 eventType={}", type);
            return;
        }

        log.info("DqPipelineEventListener: 事件 {} 命中 {} 个 EVENT 调度计划",
                type, plans.size());
        for (DqScheduleVO plan : plans) {
            try {
                int executed = scheduleService.runRuleBatch(plan.getId());
                log.info("DqPipelineEventListener: 计划 {} 触发完成, 评估 {} 条规则",
                        plan.getId(), executed);
            } catch (RuntimeException e) {
                // per-plan 故障隔离：单计划失败不中断其它计划
                log.error("DqPipelineEventListener: 计划 {} 触发失败: {}",
                        plan.getId(), e.getMessage(), e);
            }
        }
    }
}
