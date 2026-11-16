package com.chinacreator.gzcm.buszhi.workflow;

import com.chinacreator.gzcm.common.event.ObjectCreatedEvent;
import com.chinacreator.gzcm.common.event.ObjectUpdatedEvent;
import com.chinacreator.gzcm.common.event.StatusChangedEvent;
import com.chinacreator.gzcm.common.event.ActionExecutedEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Object Runtime 事件监听器 — 对象事件的可观测性出口（<b>不再写 timeline</b>）。
 *
 * <p><b>D94（2026-11-14 验证，用户裁决：保留 controller 直写）</b>：本类原先在四个
 * {@code @EventListener} 里各调一次 {@code timelineRepo.record(...)}，而
 * {@code ObjectController}/{@code ObjectActionController}/{@code StateMachineEngine}
 * 已经在同一业务动作里直写过同一条时间线 ⇒ 每个事件落库两遍（实测同一 id 的
 * {@code ObjectCreated}/{@code ObjectUpdated} 各成对出现，created_at 相差约 12ms，
 * summary 与 details 逐字相同）。</p>
 *
 * <p>裁决保留直写侧（语义更全：带真实 summary/actor，且同步写入无延迟），
 * 因此本类只保留<b>观测</b>职责：把事件记到日志，供排障与审计串接查看。
 * 若将来要恢复事件驱动写入，必须先删 controller 侧直写并同步本类的 D94 门禁。</p>
 */
@Component
public class ObjectRuntimeEventListener {

    private static final Logger log = LoggerFactory.getLogger(ObjectRuntimeEventListener.class);

    /**
     * 监听对象创建事件。
     */
    @EventListener
    public void onObjectCreated(ObjectCreatedEvent event) {
        log.debug("Event received: ObjectCreated for {}/{}", event.getEntityCode(), event.getObjectId());
    }

    /**
     * 监听对象更新事件。
     */
    @EventListener
    public void onObjectUpdated(ObjectUpdatedEvent event) {
        log.debug("Event received: ObjectUpdated for {}/{} fields={}",
                event.getEntityCode(), event.getObjectId(), event.getChangedFields());
    }

    /**
     * 监听状态变更事件。
     */
    @EventListener
    public void onStatusChanged(StatusChangedEvent event) {
        log.debug("Event received: StatusChanged for {}/{}: {}→{}",
                event.getEntityCode(), event.getObjectId(), event.getFromStatus(), event.getToStatus());
    }

    /**
     * 监听 Action 执行事件。
     */
    @EventListener
    public void onActionExecuted(ActionExecutedEvent event) {
        log.debug("Event received: ActionExecuted for {}/{}: {} result={}",
                event.getEntityCode(), event.getObjectId(), event.getActionCode(), event.getResult());
    }
}
