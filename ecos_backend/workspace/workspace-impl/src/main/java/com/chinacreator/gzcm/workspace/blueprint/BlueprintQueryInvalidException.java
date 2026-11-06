package com.chinacreator.gzcm.workspace.blueprint;

/** 蓝图只读查询参数非法（HTTP 400；未知 severity 等，禁静默降级）。 */
public class BlueprintQueryInvalidException extends BlueprintDomainException {

    public BlueprintQueryInvalidException(String field, String value) {
        super(400, "BLUEPRINT_QUERY_INVALID", "蓝图查询参数非法: " + field + "=" + value);
    }
}
