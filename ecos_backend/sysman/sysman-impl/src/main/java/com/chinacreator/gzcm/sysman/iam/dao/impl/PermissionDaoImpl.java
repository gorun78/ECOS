package com.chinacreator.gzcm.sysman.iam.dao.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.chinacreator.gzcm.runtime.core.database.ISystemDatabaseAccess;
import com.chinacreator.gzcm.sysman.iam.dao.PermissionDao;
import com.chinacreator.gzcm.sysman.iam.entity.Permission;

/**
 * 权限DAO实现
 * 通过Runtime的系统数据库访问接口完成数据库操作
 */
@Repository
public class PermissionDaoImpl implements PermissionDao {

    private final ISystemDatabaseAccess databaseAccess;
    private static final String SQL_CONFIG_PATH = "com/chinacreator/gzcm/sysman/iam/dao/impl/Permission-sql.xml";

    @Autowired
    public PermissionDaoImpl(ISystemDatabaseAccess databaseAccess) {
        this.databaseAccess = databaseAccess;
    }

    @Override
    public void insert(Permission permission) throws Exception {
        try {
            databaseAccess.executeInsertFromConfig(SQL_CONFIG_PATH, "insertPermission", permission);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("插入权限失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void update(Permission permission) throws Exception {
        try {
            databaseAccess.executeUpdateFromConfigWithEntity(SQL_CONFIG_PATH, "updatePermission", permission);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("更新权限失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String permissionId) throws Exception {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("permissionId", permissionId);
            databaseAccess.executeUpdateFromConfig(SQL_CONFIG_PATH, "deletePermission", params);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("删除权限失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Permission findById(String permissionId) throws Exception {
        try {
            return databaseAccess.queryObjectFromConfig(SQL_CONFIG_PATH, "findById", Permission.class, permissionId);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("查询权限失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Permission findByResourceAction(String resource, String action) throws Exception {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("resource", resource);
            params.put("action", action);
            return databaseAccess.queryObjectFromConfig(SQL_CONFIG_PATH, "findByResourceAction", Permission.class, params);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("根据资源和动作查询权限失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Permission> listAll() throws Exception {
        try {
            return databaseAccess.queryListFromConfig(SQL_CONFIG_PATH, "listPermissions", Permission.class, null);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("查询所有权限失败: " + e.getMessage(), e);
        }
    }
}


