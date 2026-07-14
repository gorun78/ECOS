package com.chinacreator.gzcm.sysman.iam.dao;

import java.util.List;

import com.chinacreator.gzcm.sysman.iam.entity.RolePermission;

public interface RolePermissionDao {
    void insert(RolePermission rp) throws Exception;
    void delete(String roleId, String permissionId) throws Exception;
    List<RolePermission> listByRoleId(String roleId) throws Exception;
}


