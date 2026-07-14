package com.chinacreator.gzcm.sysman.config.service;

import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.sysman.config.entity.SystemVariable;

/**
 * 系统变量服务接口
 */
public interface ISystemVariableService {
    
    SystemVariable createVariable(SystemVariable variable, String operator) throws SystemVariableException;
    
    SystemVariable updateVariable(String varId, SystemVariable variable, String operator) throws SystemVariableException;
    
    void deleteVariable(String varId, String operator) throws SystemVariableException;
    
    SystemVariable getVariable(String varId) throws SystemVariableException;
    
    SystemVariable getVariableByCode(String varCode, String scopeId) throws SystemVariableException;
    
    List<SystemVariable> listVariables(Map<String, Object> condition) throws SystemVariableException;
    
    List<SystemVariable> listVariablesByScope(String scopeId) throws SystemVariableException;
    
    void enableVariable(String varId, String operator) throws SystemVariableException;
    
    void disableVariable(String varId, String operator) throws SystemVariableException;
    
    String getVariableValue(String varCode, String scopeId) throws SystemVariableException;
    
    class SystemVariableException extends Exception {
        private static final long serialVersionUID = 1L;
        public SystemVariableException(String message) { super(message); }
        public SystemVariableException(String message, Throwable cause) { super(message, cause); }
    }
}

