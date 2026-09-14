package com.chinacreator.gzcm.common.event;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.context.ApplicationEvent;

/**
 * Object Runtime 事件基类 — 通过 Spring ApplicationEventPublisher 发布。
 */
public abstract class ObjectRuntimeEvent extends ApplicationEvent {

    private final String eventType;
    private final String objectId;
    private final String entityCode;
    private final Instant timestamp;

    protected ObjectRuntimeEvent(Object source, String eventType, String objectId, String entityCode) {
        super(source);
        this.eventType = eventType;
        this.objectId = objectId;
        this.entityCode = entityCode;
        this.timestamp = Instant.now();
    }

    public String getEventType() { return eventType; }
    public String getObjectId() { return objectId; }
    public String getEntityCode() { return entityCode; }
    public Instant getEventTime() { return timestamp; }

    public abstract Map<String, Object> toMap();
}
