package com.chinacreator.gzcm.sysman.config.dao;

import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.sysman.config.entity.SystemVariable;

/**
 * 系统变量DAO接口
 */
public interface SystemVariableDao {
    
    SystemVariable findById(String varId);
    
    SystemVariable findByCode(String varCode, String scopeId);
    
    List<SystemVariable> findAll();
    
    List<SystemVariable> findByCondition(Map<String, Object> condition);
    
    List<SystemVariable> findByScope(String scopeId);
    
    void insert(SystemVariable variable);
    
    void update(SystemVariable variable);
    
    void delete(String varId);
}

