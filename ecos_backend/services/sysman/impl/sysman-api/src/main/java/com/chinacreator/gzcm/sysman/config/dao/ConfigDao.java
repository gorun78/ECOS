package com.chinacreator.gzcm.sysman.config.dao;

import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.sysman.config.entity.Config;

/**
 * 配置DAO接口
 * 
 * @author CDRC Design Team
 */
public interface ConfigDao {
    
    void insert(Config config) throws Exception;
    
    void update(Config config) throws Exception;
    
    void delete(String configId) throws Exception;
    
    Config findById(String configId) throws Exception;
    
    Config findByTypeNameEnv(String configType, String configName, String environment) throws Exception;
    
    List<Config> query(Map<String, Object> condition) throws Exception;
    
    List<Config> listAll() throws Exception;
}


