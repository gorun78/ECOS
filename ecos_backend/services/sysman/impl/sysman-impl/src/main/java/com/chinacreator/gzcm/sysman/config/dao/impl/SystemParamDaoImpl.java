package com.chinacreator.gzcm.sysman.config.dao.impl;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.chinacreator.gzcm.runtime.core.database.ISystemDatabaseAccess;
import com.chinacreator.gzcm.sysman.config.dao.SystemParamDao;
import com.chinacreator.gzcm.sysman.config.entity.SystemParam;

/**
 * 系统参数DAO实现
 * 通过Runtime的系统数据库访问接口完成数据库操作
 */
@Repository
public class SystemParamDaoImpl implements SystemParamDao {
    
    private final ISystemDatabaseAccess databaseAccess;
    private static final String TABLE_NAME = "td_system_param";
    
    @Autowired
    public SystemParamDaoImpl(ISystemDatabaseAccess databaseAccess) {
        this.databaseAccess = databaseAccess;
    }
    
    @Override
    public SystemParam findById(String paramId) {
        try {
            return databaseAccess.findById(TABLE_NAME, SystemParam.class, paramId);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new RuntimeException("查询系统参数失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public SystemParam findByName(String paramName) {
        try {
            Map<String, Object> condition = new HashMap<>();
            condition.put("param_name", paramName);
            return databaseAccess.findOne(TABLE_NAME, SystemParam.class, condition);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new RuntimeException("根据名称查询系统参数失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public List<SystemParam> findAll() {
        try {
            return databaseAccess.query(TABLE_NAME, SystemParam.class, new HashMap<>());
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new RuntimeException("查询所有系统参数失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public List<SystemParam> findByCondition(Map<String, Object> condition) {
        try {
            return databaseAccess.query(TABLE_NAME, SystemParam.class, condition != null ? condition : new HashMap<>());
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new RuntimeException("根据条件查询系统参数失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void insert(SystemParam param) {
        try {
            if (param.getCreatedTime() == null) {
                param.setCreatedTime(new Date());
            }
            databaseAccess.insert(TABLE_NAME, param);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new RuntimeException("插入系统参数失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void update(SystemParam param) {
        try {
            param.setUpdatedTime(new Date());
            databaseAccess.update(TABLE_NAME, param);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new RuntimeException("更新系统参数失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void delete(String paramId) {
        try {
            databaseAccess.delete(TABLE_NAME, paramId);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new RuntimeException("删除系统参数失败: " + e.getMessage(), e);
        }
    }
}

