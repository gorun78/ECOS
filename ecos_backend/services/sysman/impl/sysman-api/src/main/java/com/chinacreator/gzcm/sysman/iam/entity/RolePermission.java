package com.chinacreator.gzcm.sysman.iam.entity;

/**
 * 角色权限关联实体
 * 支持权限继承和覆盖机制
 */
public class RolePermission {
    private String roleId;
    private String permissionId;
    private String effect; // ALLOW/DENY，用于权限覆盖（子角色可以显式拒绝父角色的权限）
    private Integer priority; // 优先级，数字越大优先级越高，用于权限覆盖时的决策

    public String getRoleId() {
        return roleId;
    }

    public void setRoleId(String roleId) {
        this.roleId = roleId;
    }

    public String getPermissionId() {
        return permissionId;
    }

    public void setPermissionId(String permissionId) {
        this.permissionId = permissionId;
    }

    public String getEffect() {
        return effect;
    }

    public void setEffect(String effect) {
        this.effect = effect;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }
}


