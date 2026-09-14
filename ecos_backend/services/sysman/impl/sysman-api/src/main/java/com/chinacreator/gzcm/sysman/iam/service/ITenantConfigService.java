package com.chinacreator.gzcm.sysman.iam.service;

import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.sysman.iam.entity.TenantConfig;

public interface ITenantConfigService {

    void setConfig(String tenantId, String key, String value) throws TenantConfigException;

    String getConfig(String tenantId, String key) throws TenantConfigException;

    List<TenantConfig> listConfigs(String tenantId) throws TenantConfigException;

    void deleteConfig(String tenantId, String key) throws TenantConfigException;

    Map<String, String> getAllAsMap(String tenantId) throws TenantConfigException;

    class TenantConfigException extends Exception {
        private static final long serialVersionUID = 1L;
        public TenantConfigException(String message) { super(message); }
        public TenantConfigException(String message, Throwable cause) { super(message, cause); }
    }
}


