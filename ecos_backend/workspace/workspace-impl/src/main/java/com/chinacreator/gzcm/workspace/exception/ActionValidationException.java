package com.chinacreator.gzcm.workspace.exception;

import java.util.List;

/** 动作/运行请求体必填字段缺失（F07-10 五必填 / F07-14 run body，HTTP 400 / ACTION_FIELDS_MISSING）。 */
public class ActionValidationException extends WorkspaceDomainException {

    private final List<String> missingFields;

    public ActionValidationException(List<String> missingFields) {
        super(400, "ACTION_FIELDS_MISSING", "必填字段缺失: " + missingFields);
        this.missingFields = List.copyOf(missingFields == null ? List.of() : missingFields);
        putDetail("missingFields", this.missingFields);
    }

    public List<String> getMissingFields() {
        return missingFields;
    }
}
