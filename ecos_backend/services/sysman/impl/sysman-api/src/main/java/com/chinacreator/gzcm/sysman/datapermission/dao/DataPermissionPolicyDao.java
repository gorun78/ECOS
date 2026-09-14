package com.chinacreator.gzcm.sysman.datapermission.dao;

import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.sysman.datapermission.entity.DataPermissionPolicy;

public interface DataPermissionPolicyDao {

    void insert(DataPermissionPolicy policy) throws Exception;

    void update(DataPermissionPolicy policy) throws Exception;

    void delete(String policyId) throws Exception;

    DataPermissionPolicy findById(String policyId) throws Exception;

    List<DataPermissionPolicy> query(Map<String, Object> condition) throws Exception;
}


