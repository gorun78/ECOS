package com.chinacreator.gzcm.sysman.config.service;

import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.sysman.config.entity.SystemParam;

/**
 * 系统参数服务接口
 */
public interface ISystemParamService {
    
    SystemParam createParam(SystemParam param, String operator) throws SystemParamException;
    
    SystemParam updateParam(String paramId, SystemParam param, String operator) throws SystemParamException;
    
    void deleteParam(String paramId, String operator) throws SystemParamException;
    
    SystemParam getParam(String paramId) throws SystemParamException;
    
    SystemParam getParamByName(String paramName) throws SystemParamException;
    
    List<SystemParam> listParams(Map<String, Object> condition) throws SystemParamException;
    
    String getParamValue(String paramName) throws SystemParamException;
    
    class SystemParamException extends Exception {
        private static final long serialVersionUID = 1L;
        public SystemParamException(String message) { super(message); }
        public SystemParamException(String message, Throwable cause) { super(message, cause); }
    }
}

