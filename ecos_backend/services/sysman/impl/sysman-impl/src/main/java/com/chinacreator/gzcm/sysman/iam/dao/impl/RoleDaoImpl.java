package com.chinacreator.gzcm.sysman.iam.dao.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.chinacreator.gzcm.runtime.core.database.ISystemDatabaseAccess;
import com.chinacreator.gzcm.sysman.iam.dao.RoleDao;
import com.chinacreator.gzcm.sysman.iam.entity.Role;

/**
 * 角色DAO实现
 * 通过Runtime的系统数据库访问接口完成数据库操作
 */
@Repository
public class RoleDaoImpl implements RoleDao {

    private final ISystemDatabaseAccess databaseAccess;
    private static final String SQL_CONFIG_PATH = "com/chinacreator/gzcm/sysman/iam/dao/impl/Role-sql.xml";

    @Autowired
    public RoleDaoImpl(ISystemDatabaseAccess databaseAccess) {
        this.databaseAccess = databaseAccess;
    }

    @Override
    public void insert(Role role) throws Exception {
        try {
            databaseAccess.executeInsertFromConfig(SQL_CONFIG_PATH, "insertRole", role);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("插入角色失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void update(Role role) throws Exception {
        try {
            databaseAccess.executeUpdateFromConfigWithEntity(SQL_CONFIG_PATH, "updateRole", role);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("更新角色失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String roleId) throws Exception {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("roleId", roleId);
            databaseAccess.executeUpdateFromConfig(SQL_CONFIG_PATH, "deleteRole", params);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("删除角色失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Role findById(String roleId) throws Exception {
        try {
            return databaseAccess.queryObjectFromConfig(SQL_CONFIG_PATH, "findById", Role.class, roleId);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("查询角色失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Role findByCode(String roleCode) throws Exception {
        try {
            return databaseAccess.queryObjectFromConfig(SQL_CONFIG_PATH, "findByCode", Role.class, roleCode);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("根据编码查询角色失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Role> query(Map<String, Object> condition) throws Exception {
        try {
            // 条件全为空时传空数组，让 extractParamsForSql 返回空参数
            Object params = hasAnyValue(condition) ? condition : new Object[0];
            return databaseAccess.queryListFromConfig(SQL_CONFIG_PATH, "queryRoles", Role.class, params);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("查询角色列表失败: " + e.getMessage(), e);
        }
    }
    
    private boolean hasAnyValue(Map<String, Object> map) {
        if (map == null) return false;
        for (Object v : map.values()) {
            if (v != null && !String.valueOf(v).isEmpty()) return true;
        }
        return false;
    }
}


