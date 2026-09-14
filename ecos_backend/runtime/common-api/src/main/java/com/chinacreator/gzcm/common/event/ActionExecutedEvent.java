package com.chinacreator.gzcm.common.event;

import java.util.LinkedHashMap;
import java.util.Map;

/** Action 执行事件 */
public class ActionExecutedEvent extends ObjectRuntimeEvent {
    private final String actionCode;
    private final String result;
    private final Map<String, Object> params;

    public ActionExecutedEvent(Object source, String objectId, String entityCode, String actionCode, String result, Map<String, Object> params) {
        super(source, "ActionExecuted", objectId, entityCode);
        this.actionCode = actionCode;
        this.result = result;
        this.params = params;
    }

    public String getActionCode() { return actionCode; }
    public String getResult() { return result; }
    public Map<String, Object> getParams() { return params; }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("eventType", getEventType());
        m.put("objectId", getObjectId());
        m.put("entityCode", getEntityCode());
        m.put("actionCode", actionCode);
        m.put("result", result);
        m.put("timestamp", getEventTime().toString());
        return m;
    }
}
