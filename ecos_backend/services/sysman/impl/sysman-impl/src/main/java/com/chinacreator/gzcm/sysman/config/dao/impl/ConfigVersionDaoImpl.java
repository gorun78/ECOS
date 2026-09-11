package com.chinacreator.gzcm.sysman.config.dao.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.chinacreator.gzcm.runtime.core.database.ISystemDatabaseAccess;
import com.chinacreator.gzcm.sysman.config.dao.ConfigVersionDao;
import com.chinacreator.gzcm.sysman.config.entity.ConfigVersion;

/**
 * 配置版本DAO实现
 * 通过Runtime的系统数据库访问接口完成数据库操作
 * 
 * @author CDRC Design Team
 */
@Repository
public class ConfigVersionDaoImpl implements ConfigVersionDao {
    
    private final ISystemDatabaseAccess databaseAccess;
    private static final String SQL_CONFIG_PATH = "com/chinacreator/gzcm/sysman/config/dao/impl/ConfigVersion-sql.xml";
    
    @Autowired
    public ConfigVersionDaoImpl(ISystemDatabaseAccess databaseAccess) {
        this.databaseAccess = databaseAccess;
    }
    
    @Override
    public void insert(ConfigVersion version) throws Exception {
        try {
            databaseAccess.executeInsertFromConfig(SQL_CONFIG_PATH, "ConfigVersion.insert", version);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("插入配置版本失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public ConfigVersion findById(String versionId) throws Exception {
        try {
            return databaseAccess.queryObjectFromConfig(SQL_CONFIG_PATH, "ConfigVersion.findById", ConfigVersion.class, versionId);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("查询配置版本失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public ConfigVersion findByConfigIdAndVersion(String configId, String version) throws Exception {
        try {
            Map<String, Object> condition = new HashMap<>();
            condition.put("configId", configId);
            condition.put("version", version);
            return databaseAccess.queryObjectFromConfig(SQL_CONFIG_PATH, "ConfigVersion.findByConfigIdAndVersion", ConfigVersion.class, condition);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("根据配置ID和版本查询配置版本失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public List<ConfigVersion> listByConfigId(String configId) throws Exception {
        try {
            return databaseAccess.queryListFromConfig(SQL_CONFIG_PATH, "ConfigVersion.listByConfigId", ConfigVersion.class, configId);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("查询配置版本列表失败: " + e.getMessage(), e);
        }
    }
}



