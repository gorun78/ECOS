package com.chinacreator.gzcm.common.event;

import java.util.LinkedHashMap;
import java.util.Map;

/** 对象创建事件 */
public class ObjectCreatedEvent extends ObjectRuntimeEvent {
    private final Map<String, Object> payload;

    public ObjectCreatedEvent(Object source, String objectId, String entityCode, Map<String, Object> payload) {
        super(source, "ObjectCreated", objectId, entityCode);
        this.payload = payload;
    }

    public Map<String, Object> getPayload() { return payload; }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("eventType", getEventType());
        m.put("objectId", getObjectId());
        m.put("entityCode", getEntityCode());
        m.put("timestamp", getEventTime().toString());
        m.put("payload", payload);
        return m;
    }
}
