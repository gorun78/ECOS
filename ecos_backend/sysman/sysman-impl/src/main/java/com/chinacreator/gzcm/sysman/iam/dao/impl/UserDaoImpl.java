package com.chinacreator.gzcm.sysman.iam.dao.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.chinacreator.gzcm.runtime.core.database.ISystemDatabaseAccess;
import com.chinacreator.gzcm.sysman.iam.dao.UserDao;
import com.chinacreator.gzcm.sysman.iam.entity.UserAccount;

/**
 * 用户DAO实现
 * 通过Runtime的系统数据库访问接口完成数据库操作
 */
@Repository
public class UserDaoImpl implements UserDao {

    private final ISystemDatabaseAccess databaseAccess;
    private static final String SQL_CONFIG_PATH = "com/chinacreator/gzcm/sysman/iam/dao/impl/User-sql.xml";

    @Autowired
    public UserDaoImpl(ISystemDatabaseAccess databaseAccess) {
        this.databaseAccess = databaseAccess;
    }

    @Override
    public void insert(UserAccount user) throws Exception {
        try {
            databaseAccess.executeInsertFromConfig(SQL_CONFIG_PATH, "insertUser", user);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("插入用户失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void update(UserAccount user) throws Exception {
        try {
            databaseAccess.executeUpdateFromConfigWithEntity(SQL_CONFIG_PATH, "updateUser", user);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("更新用户失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String userId) throws Exception {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("userId", userId);
            databaseAccess.executeUpdateFromConfig(SQL_CONFIG_PATH, "deleteUser", params);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("删除用户失败: " + e.getMessage(), e);
        }
    }

    @Override
    public UserAccount findById(String userId) throws Exception {
        try {
            return databaseAccess.queryObjectFromConfig(SQL_CONFIG_PATH, "findById", UserAccount.class, userId);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("查询用户失败: " + e.getMessage(), e);
        }
    }

    @Override
    public UserAccount findByUsername(String username) throws Exception {
        try {
            return databaseAccess.queryObjectFromConfig(SQL_CONFIG_PATH, "findByUsername", UserAccount.class, username);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("根据用户名查询用户失败: " + e.getMessage(), e);
        }
    }

    @Override
    public UserAccount findByEmail(String email) throws Exception {
        try {
            return databaseAccess.queryObjectFromConfig(SQL_CONFIG_PATH, "findByEmail", UserAccount.class, email);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("根据邮箱查询用户失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<UserAccount> query(Map<String, Object> condition) throws Exception {
        try {
            // Always pass condition map — the SQL has #if guards for null values
            return databaseAccess.queryListFromConfig(SQL_CONFIG_PATH, "queryUsers", UserAccount.class, condition);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("查询用户列表失败: " + e.getMessage(), e);
        }
    }
}


