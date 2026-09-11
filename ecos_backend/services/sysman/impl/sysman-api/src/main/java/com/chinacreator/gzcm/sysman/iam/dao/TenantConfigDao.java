package com.chinacreator.gzcm.sysman.iam.dao;

import java.util.List;

import com.chinacreator.gzcm.sysman.iam.entity.TenantConfig;

public interface TenantConfigDao {
    void insert(TenantConfig config) throws Exception;
    void update(TenantConfig config) throws Exception;
    void delete(String configId) throws Exception;
    List<TenantConfig> listByTenantId(String tenantId) throws Exception;
    TenantConfig findByTenantAndKey(String tenantId, String key) throws Exception;
}


