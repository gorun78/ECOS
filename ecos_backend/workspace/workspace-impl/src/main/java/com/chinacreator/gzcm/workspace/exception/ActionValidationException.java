package com.chinacreator.gzcm.workspace.exception;

/**
 * 动作提案五必填缺失 / 非法（F07-10 / C-5）。HTTP 400，错误码 ACTION_FIELDS_MISSING。
 */
public class ActionValidationException extends WorkspaceException {

    public static final String CODE = "ACTION_FIELDS_MISSING";

    public ActionValidationException(String message) {
        super(400, 400, CODE, message);
    }
}
