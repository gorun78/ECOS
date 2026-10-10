package com.chinacreator.gzcm.workspace.blueprint;

/** 蓝图结构化字段（gap/dependency/capability/test 子段）JSON 非法（HTTP 400，不伪装合法）。 */
public class BlueprintContentInvalidException extends BlueprintDomainException {

    public BlueprintContentInvalidException(String field, String reason) {
        super(400, "BLUEPRINT_CONTENT_INVALID",
                "蓝图字段 JSON 非法: " + field + "（" + reason + "），须为 JSON 数组");
    }
}
