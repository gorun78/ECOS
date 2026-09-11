package com.chinacreator.gzcm.sysman.iam.service.impl;

public class PermissionCheckException extends Exception {
    private static final long serialVersionUID = 1L;

    public PermissionCheckException(String message) {
        super(message);
    }

    public PermissionCheckException(String message, Throwable cause) {
        super(message, cause);
    }
}


