package com.chinacreator.gzcm.sysman.iam.dao.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.chinacreator.gzcm.runtime.core.database.ISystemDatabaseAccess;
import com.chinacreator.gzcm.sysman.iam.dao.RolePermissionDao;
import com.chinacreator.gzcm.sysman.iam.entity.RolePermission;

/**
 * 角色权限关联DAO实现
 * 通过Runtime的系统数据库访问接口完成数据库操作
 */
@Repository
public class RolePermissionDaoImpl implements RolePermissionDao {

    private final ISystemDatabaseAccess databaseAccess;
    private static final String SQL_CONFIG_PATH = "com/chinacreator/gzcm/sysman/iam/dao/impl/RolePermission-sql.xml";

    @Autowired
    public RolePermissionDaoImpl(ISystemDatabaseAccess databaseAccess) {
        this.databaseAccess = databaseAccess;
    }

    @Override
    public void insert(RolePermission rp) throws Exception {
        try {
            databaseAccess.executeInsertFromConfig(SQL_CONFIG_PATH, "insertRolePermission", rp);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("插入角色权限关联失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String roleId, String permissionId) throws Exception {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("roleId", roleId);
            params.put("permissionId", permissionId);
            databaseAccess.executeUpdateFromConfig(SQL_CONFIG_PATH, "deleteRolePermission", params);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("删除角色权限关联失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<RolePermission> listByRoleId(String roleId) throws Exception {
        try {
            return databaseAccess.queryListFromConfig(SQL_CONFIG_PATH, "listRolePermissions", RolePermission.class, roleId);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("查询角色权限列表失败: " + e.getMessage(), e);
        }
    }
}


