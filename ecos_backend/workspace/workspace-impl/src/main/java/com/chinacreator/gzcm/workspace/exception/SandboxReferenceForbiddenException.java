package com.chinacreator.gzcm.workspace.exception;

/**
 * FORMAL 运行引用了 SANDBOX 演练产物（F07-09 / C-5）。HTTP 400，错误码 SANDBOX_REF_FORBIDDEN。
 */
public class SandboxReferenceForbiddenException extends WorkspaceException {

    public static final String CODE = "SANDBOX_REF_FORBIDDEN";

    public SandboxReferenceForbiddenException(String message) {
        super(400, 400, CODE, message);
    }
}
