package com.chinacreator.gzcm.sysman.iam.dao.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.chinacreator.gzcm.runtime.core.database.ISystemDatabaseAccess;
import com.chinacreator.gzcm.sysman.iam.dao.UserRoleDao;
import com.chinacreator.gzcm.sysman.iam.entity.UserRole;

/**
 * 用户角色关联DAO实现
 * 通过Runtime的系统数据库访问接口完成数据库操作
 */
@Repository
public class UserRoleDaoImpl implements UserRoleDao {

    private final ISystemDatabaseAccess databaseAccess;
    private static final String SQL_CONFIG_PATH = "com/chinacreator/gzcm/sysman/iam/dao/impl/UserRole-sql.xml";

    @Autowired
    public UserRoleDaoImpl(ISystemDatabaseAccess databaseAccess) {
        this.databaseAccess = databaseAccess;
    }

    @Override
    public void insert(UserRole userRole) throws Exception {
        try {
            databaseAccess.executeInsertFromConfig(SQL_CONFIG_PATH, "insertUserRole", userRole);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("插入用户角色关联失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String userId, String roleId) throws Exception {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("userId", userId);
            params.put("roleId", roleId);
            databaseAccess.executeUpdateFromConfig(SQL_CONFIG_PATH, "deleteUserRole", params);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("删除用户角色关联失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<UserRole> listByUserId(String userId) throws Exception {
        try {
            return databaseAccess.queryListFromConfig(SQL_CONFIG_PATH, "listByUserId", UserRole.class, userId);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("查询用户角色列表失败: " + e.getMessage(), e);
        }
    }
}


