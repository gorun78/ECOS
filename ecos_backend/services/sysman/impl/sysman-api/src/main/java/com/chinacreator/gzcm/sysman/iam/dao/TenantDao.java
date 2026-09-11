package com.chinacreator.gzcm.sysman.iam.dao;

import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.sysman.iam.entity.Tenant;

public interface TenantDao {
    void insert(Tenant tenant) throws Exception;
    void update(Tenant tenant) throws Exception;
    void softDelete(String tenantId) throws Exception;
    Tenant findById(String tenantId) throws Exception;
    Tenant findByCode(String tenantCode) throws Exception;
    List<Tenant> query(Map<String, Object> condition) throws Exception;
}


