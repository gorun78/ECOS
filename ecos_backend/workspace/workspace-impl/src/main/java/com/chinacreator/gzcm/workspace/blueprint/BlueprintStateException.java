package com.chinacreator.gzcm.workspace.blueprint;

/** 持久化状态值不在词表单源（数据完整性异常，HTTP 409，禁按默认值伪装合法态）。 */
public class BlueprintStateException extends BlueprintDomainException {

    public BlueprintStateException(String code, String message) {
        super(409, code, message);
    }
}
