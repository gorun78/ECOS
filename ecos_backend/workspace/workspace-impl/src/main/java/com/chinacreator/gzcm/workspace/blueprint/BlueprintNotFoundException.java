package com.chinacreator.gzcm.workspace.blueprint;

/** 蓝图不存在 / 已软删（HTTP 404，不给伪造空数据，§13.1）。 */
public class BlueprintNotFoundException extends BlueprintDomainException {

    public BlueprintNotFoundException(String blueprintId) {
        super(404, "BLUEPRINT_NOT_FOUND", "场景蓝图不存在或已删除: " + blueprintId);
        putDetail("blueprintId", blueprintId);
    }
}
