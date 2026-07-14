package com.chinacreator.gzcm.common.event;

import java.util.LinkedHashMap;
import java.util.Map;

/** 状态变更事件 */
public class StatusChangedEvent extends ObjectRuntimeEvent {
    private final String fromStatus;
    private final String toStatus;

    public StatusChangedEvent(Object source, String objectId, String entityCode, String fromStatus, String toStatus) {
        super(source, "StatusChanged", objectId, entityCode);
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
    }

    public String getFromStatus() { return fromStatus; }
    public String getToStatus() { return toStatus; }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("eventType", getEventType());
        m.put("objectId", getObjectId());
        m.put("entityCode", getEntityCode());
        m.put("fromStatus", fromStatus);
        m.put("toStatus", toStatus);
        m.put("timestamp", getEventTime().toString());
        return m;
    }
}
