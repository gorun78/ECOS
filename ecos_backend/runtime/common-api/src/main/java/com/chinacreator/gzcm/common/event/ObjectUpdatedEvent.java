package com.chinacreator.gzcm.common.event;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 对象更新事件 */
public class ObjectUpdatedEvent extends ObjectRuntimeEvent {
    private final List<String> changedFields;

    public ObjectUpdatedEvent(Object source, String objectId, String entityCode, List<String> changedFields) {
        super(source, "ObjectUpdated", objectId, entityCode);
        this.changedFields = changedFields;
    }

    public List<String> getChangedFields() { return changedFields; }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("eventType", getEventType());
        m.put("objectId", getObjectId());
        m.put("entityCode", getEntityCode());
        m.put("changedFields", changedFields);
        m.put("timestamp", getEventTime().toString());
        return m;
    }
}
