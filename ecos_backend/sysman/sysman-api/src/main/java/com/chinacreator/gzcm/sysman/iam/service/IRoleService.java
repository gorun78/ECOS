package com.chinacreator.gzcm.sysman.iam.service;

import java.util.List;
import java.util.Set;

import com.chinacreator.gzcm.sysman.iam.entity.Permission;
import com.chinacreator.gzcm.sysman.iam.entity.Role;

public interface IRoleService {
    Role createRole(Role role, String operator) throws RoleException;
    Role updateRole(Role role, String operator) throws RoleException;
    void deleteRole(String roleId, String operator) throws RoleException;
    Role getRole(String roleId) throws RoleException;
    List<Role> listRoles(String keyword, String tenantId) throws RoleException;
    void assignPermission(String roleId, String permissionId, String operator) throws RoleException;
    void revokePermission(String roleId, String permissionId, String operator) throws RoleException;
    List<Permission> getRolePermissions(String roleId) throws RoleException;
    Set<Permission> getMergedPermissions(String roleId) throws RoleException;

    class RoleException extends Exception {
        private static final long serialVersionUID = 1L;
        public RoleException(String message) { super(message); }
        public RoleException(String message, Throwable cause) { super(message, cause); }
    }
}


