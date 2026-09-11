package com.chinacreator.gzcm.sysman.config.dao;

import java.util.List;

import com.chinacreator.gzcm.sysman.config.entity.ConfigVersion;

/**
 * 配置版本DAO接口
 * 
 * @author CDRC Design Team
 */
public interface ConfigVersionDao {
    
    void insert(ConfigVersion version) throws Exception;
    
    ConfigVersion findById(String versionId) throws Exception;
    
    ConfigVersion findByConfigIdAndVersion(String configId, String version) throws Exception;
    
    List<ConfigVersion> listByConfigId(String configId) throws Exception;
}


