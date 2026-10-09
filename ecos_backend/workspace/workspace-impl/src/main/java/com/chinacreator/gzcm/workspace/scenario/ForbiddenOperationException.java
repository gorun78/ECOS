package com.chinacreator.gzcm.workspace.scenario;

import java.util.Map;

/** 权限拒绝 / 安全引擎默认 DENY（卷07 F07-07/F07-13/F07-18，HTTP 403，code=PERMISSION_DENIED）。 */
public class ForbiddenOperationException extends ScenarioDomainException {

    public ForbiddenOperationException(String permissionPoint) {
        super(403, "PERMISSION_DENIED", "无权限执行该操作: " + permissionPoint);
        putDetail("permission", permissionPoint);
    }

    public ForbiddenOperationException(String permissionPoint, Map<String, Object> attrs) {
        super(403, "PERMISSION_DENIED", "无权限执行该操作: " + permissionPoint);
        putDetail("permission", permissionPoint);
        if (attrs != null) {
            attrs.forEach(this::putDetail);
        }
    }
}
