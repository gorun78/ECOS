package com.chinacreator.gzcm.sysman.config.dao;

import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.sysman.config.entity.SystemParam;

/**
 * 系统参数DAO接口
 */
public interface SystemParamDao {
    
    SystemParam findById(String paramId);
    
    SystemParam findByName(String paramName);
    
    List<SystemParam> findAll();
    
    List<SystemParam> findByCondition(Map<String, Object> condition);
    
    void insert(SystemParam param);
    
    void update(SystemParam param);
    
    void delete(String paramId);
}

