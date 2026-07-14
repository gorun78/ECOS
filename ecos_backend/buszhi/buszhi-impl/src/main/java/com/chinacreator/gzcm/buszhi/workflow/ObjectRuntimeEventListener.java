package com.chinacreator.gzcm.buszhi.workflow;

import java.util.*;

import com.chinacreator.gzcm.common.event.ObjectCreatedEvent;
import com.chinacreator.gzcm.common.event.ObjectUpdatedEvent;
import com.chinacreator.gzcm.common.event.StatusChangedEvent;
import com.chinacreator.gzcm.common.event.ActionExecutedEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Object Runtime 事件监听器 — 自动监听所有对象事件并记录 Timeline。
 *
 * <p>监听事件类型：
 * <ul>
 *   <li>ObjectCreatedEvent — 记录「对象创建」</li>
 *   <li>ObjectUpdatedEvent — 记录「对象更新」</li>
 *   <li>StatusChangedEvent — 记录「状态变更」</li>
 *   <li>ActionExecutedEvent — 记录「Action 执行」</li>
 * </ul>
 */
@Component
public class ObjectRuntimeEventListener {

    private static final Logger log = LoggerFactory.getLogger(ObjectRuntimeEventListener.class);

    private final TimelineRepository timelineRepo;

    public ObjectRuntimeEventListener(TimelineRepository timelineRepo) {
        this.timelineRepo = timelineRepo;
    }

    /**
     * 监听对象创建事件 → 记录 Timeline。
     */
    @EventListener
    public void onObjectCreated(ObjectCreatedEvent event) {
        log.debug("Event received: ObjectCreated for {}/{}", event.getEntityCode(), event.getObjectId());

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("eventType", event.getEventType());
        if (event.getPayload() != null) {
            // 只记录关键字段，避免 payload 过大
            if (event.getPayload().containsKey("name")) {
                details.put("name", event.getPayload().get("name"));
            }
            if (event.getPayload().containsKey("status")) {
                details.put("status", event.getPayload().get("status"));
            }
        }

        timelineRepo.record(event.getObjectId(), event.getEntityCode(),
            "ObjectCreated", "对象创建", "system", details);
    }

    /**
     * 监听对象更新事件 → 记录 Timeline。
     */
    @EventListener
    public void onObjectUpdated(ObjectUpdatedEvent event) {
        log.debug("Event received: ObjectUpdated for {}/{}", event.getEntityCode(), event.getObjectId());

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("eventType", event.getEventType());
        details.put("changedFields", event.getChangedFields());

        String summary = "更新字段: " + String.join(", ", event.getChangedFields());

        timelineRepo.record(event.getObjectId(), event.getEntityCode(),
            "ObjectUpdated", summary, "system", details);
    }

    /**
     * 监听状态变更事件 → 记录 Timeline。
     */
    @EventListener
    public void onStatusChanged(StatusChangedEvent event) {
        log.debug("Event received: StatusChanged for {}/{}: {}→{}",
            event.getEntityCode(), event.getObjectId(), event.getFromStatus(), event.getToStatus());

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("eventType", event.getEventType());
        details.put("fromStatus", event.getFromStatus());
        details.put("toStatus", event.getToStatus());

        String summary = "状态变更: " + event.getFromStatus() + " → " + event.getToStatus();

        timelineRepo.record(event.getObjectId(), event.getEntityCode(),
            "StatusChanged", summary, "system", details);
    }

    /**
     * 监听 Action 执行事件 → 记录 Timeline。
     */
    @EventListener
    public void onActionExecuted(ActionExecutedEvent event) {
        log.debug("Event received: ActionExecuted for {}/{}: {}",
            event.getEntityCode(), event.getObjectId(), event.getActionCode());

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("eventType", event.getEventType());
        details.put("actionCode", event.getActionCode());
        details.put("result", event.getResult());
        if (event.getParams() != null) {
            details.put("params", event.getParams());
        }

        String summary = event.getActionCode() + " 执行" +
            ("success".equals(event.getResult()) ? "成功" : " — " + event.getResult());

        timelineRepo.record(event.getObjectId(), event.getEntityCode(),
            "ActionExecuted", summary, "system", details);
    }
}
