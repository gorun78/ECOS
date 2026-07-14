package com.chinacreator.gzcm.sysman.config.service;

import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.sysman.config.entity.IPAccess;

/**
 * IP访问控制服务接口
 */
public interface IIPAccessService {
    
    IPAccess createIPAccess(IPAccess ipAccess, String operator) throws IPAccessException;
    
    IPAccess updateIPAccess(String accessId, IPAccess ipAccess, String operator) throws IPAccessException;
    
    void deleteIPAccess(String accessId, String operator) throws IPAccessException;
    
    IPAccess getIPAccess(String accessId) throws IPAccessException;
    
    List<IPAccess> listIPAccess(Map<String, Object> condition) throws IPAccessException;
    
    boolean isIPAllowed(String ipAddress) throws IPAccessException;
    
    boolean isIPDenied(String ipAddress) throws IPAccessException;
    
    class IPAccessException extends Exception {
        private static final long serialVersionUID = 1L;
        public IPAccessException(String message) { super(message); }
        public IPAccessException(String message, Throwable cause) { super(message, cause); }
    }
}

