package com.chinacreator.gzcm.sysman.abac.dao;

import java.util.List;

import com.chinacreator.gzcm.sysman.abac.model.AbacPolicy;

public interface AbacPolicyDao {
    void insert(AbacPolicy policy) throws Exception;
    void update(AbacPolicy policy) throws Exception;
    void delete(String policyId) throws Exception;
    AbacPolicy findById(String policyId) throws Exception;
    List<AbacPolicy> findAll() throws Exception;
}


