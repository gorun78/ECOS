package com.chinacreator.gzcm.workspace.scenario;

import java.util.List;

/** 动作五必填 / run body 校验失败（卷07 F07-10/F07-14，HTTP 400，code=ACTION_FIELDS_MISSING）。 */
public class ActionValidationException extends ScenarioDomainException {

    public ActionValidationException(List<String> missingFields) {
        super(400, "ACTION_FIELDS_MISSING", "必填字段缺失: " + missingFields);
        putDetail("missingFields", missingFields);
    }
}
