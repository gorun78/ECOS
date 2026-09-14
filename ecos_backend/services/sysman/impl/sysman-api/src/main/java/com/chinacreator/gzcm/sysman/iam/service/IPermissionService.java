package com.chinacreator.gzcm.sysman.iam.service;

import java.util.List;

import com.chinacreator.gzcm.sysman.iam.entity.Permission;

public interface IPermissionService {
    Permission createPermission(Permission permission, String operator) throws PermissionException;
    Permission updatePermission(Permission permission, String operator) throws PermissionException;
    void deletePermission(String permissionId, String operator) throws PermissionException;
    Permission getPermission(String permissionId) throws PermissionException;
    List<Permission> listPermissions() throws PermissionException;

    class PermissionException extends Exception {
        private static final long serialVersionUID = 1L;
        public PermissionException(String message) { super(message); }
        public PermissionException(String message, Throwable cause) { super(message, cause); }
    }
}


