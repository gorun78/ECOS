package com.chinacreator.gzcm.sysman.config.dao;

import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.sysman.config.entity.IPAccess;

/**
 * IP访问控制DAO接口
 */
public interface IPAccessDao {
    
    IPAccess findById(String accessId);
    
    List<IPAccess> findAll();
    
    List<IPAccess> findByCondition(Map<String, Object> condition);
    
    List<IPAccess> findByIpAddress(String ipAddress);
    
    void insert(IPAccess ipAccess);
    
    void update(IPAccess ipAccess);
    
    void delete(String accessId);
}

