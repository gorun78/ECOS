package com.chinacreator.gzcm.sysman.config.dao.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.chinacreator.gzcm.runtime.core.database.ISystemDatabaseAccess;
import com.chinacreator.gzcm.sysman.config.dao.ConfigDao;
import com.chinacreator.gzcm.sysman.config.entity.Config;

/**
 * 配置DAO实现
 * 通过Runtime的系统数据库访问接口完成数据库操作
 * 
 * @author CDRC Design Team
 */
@Repository("sysManConfigDao")
public class ConfigDaoImpl implements ConfigDao {
    
    private final ISystemDatabaseAccess databaseAccess;
    private static final String SQL_CONFIG_PATH = "com/chinacreator/gzcm/sysman/config/dao/impl/Config-sql.xml";
    
    @Autowired
    public ConfigDaoImpl(ISystemDatabaseAccess databaseAccess) {
        this.databaseAccess = databaseAccess;
    }
    
    @Override
    public void insert(Config config) throws Exception {
        try {
            databaseAccess.executeInsertFromConfig(SQL_CONFIG_PATH, "Config.insert", config);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("插入配置失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void update(Config config) throws Exception {
        try {
            databaseAccess.executeUpdateFromConfigWithEntity(SQL_CONFIG_PATH, "Config.update", config);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("更新配置失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void delete(String configId) throws Exception {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("configId", configId);
            databaseAccess.executeUpdateFromConfig(SQL_CONFIG_PATH, "Config.delete", params);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("删除配置失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public Config findById(String configId) throws Exception {
        try {
            return databaseAccess.queryObjectFromConfig(SQL_CONFIG_PATH, "Config.findById", Config.class, configId);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("查询配置失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public Config findByTypeNameEnv(String configType, String configName, String environment) throws Exception {
        try {
            Map<String, Object> condition = new HashMap<>();
            condition.put("configType", configType);
            condition.put("configName", configName);
            condition.put("environment", environment);
            return databaseAccess.queryObjectFromConfig(SQL_CONFIG_PATH, "Config.findByTypeNameEnv", Config.class, condition);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("根据类型、名称和环境查询配置失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public List<Config> query(Map<String, Object> condition) throws Exception {
        try {
            return databaseAccess.queryListFromConfig(SQL_CONFIG_PATH, "Config.query", Config.class, condition);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("查询配置列表失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public List<Config> listAll() throws Exception {
        try {
            return databaseAccess.queryListFromConfig(SQL_CONFIG_PATH, "Config.listAll", Config.class, null);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("查询所有配置失败: " + e.getMessage(), e);
        }
    }
}



