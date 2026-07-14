package com.chinacreator.gzcm.runtime.core.datapermission.service.impl;


import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.chinacreator.gzcm.sysman.datapermission.dao.DataPermissionPolicyDao;
import com.chinacreator.gzcm.sysman.datapermission.entity.DataPermissionPolicy;
import com.chinacreator.gzcm.sysman.datapermission.service.IDataPermissionPolicyService;

public class DataPermissionPolicyServiceImpl implements IDataPermissionPolicyService {

    private final DataPermissionPolicyDao policyDao;

    public DataPermissionPolicyServiceImpl(DataPermissionPolicyDao policyDao) {
        this.policyDao = policyDao;
    }

    @Override
    public DataPermissionPolicy createPolicy(DataPermissionPolicy policy, String operator) throws DataPermissionPolicyException {
        try {
            policy.setPolicyId(policy.getPolicyId() == null ? UUID.randomUUID().toString() : policy.getPolicyId());
            policy.setCreatedTime(new Date());
            policy.setCreatedBy(operator);
            policyDao.insert(policy);
            return policyDao.findById(policy.getPolicyId());
        } catch (Exception e) {
            throw new DataPermissionPolicyException("创建数据权限策略失败: " + e.getMessage(), e);
        }
    }

    @Override
    public DataPermissionPolicy updatePolicy(DataPermissionPolicy policy, String operator) throws DataPermissionPolicyException {
        try {
            DataPermissionPolicy existing = policyDao.findById(policy.getPolicyId());
            if (existing == null) {
                throw new DataPermissionPolicyException("策略不存在: " + policy.getPolicyId());
            }
            if (policy.getPolicyName() != null) existing.setPolicyName(policy.getPolicyName());
            if (policy.getPolicyType() != null) existing.setPolicyType(policy.getPolicyType());
            if (policy.getResource() != null) existing.setResource(policy.getResource());
            if (policy.getPolicyCondition() != null) existing.setPolicyCondition(policy.getPolicyCondition());
            policyDao.update(existing);
            return policyDao.findById(existing.getPolicyId());
        } catch (DataPermissionPolicyException e) {
            throw e;
        } catch (Exception e) {
            throw new DataPermissionPolicyException("更新数据权限策略失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void deletePolicy(String policyId) throws DataPermissionPolicyException {
        try {
            policyDao.delete(policyId);
        } catch (Exception e) {
            throw new DataPermissionPolicyException("删除数据权限策略失败: " + e.getMessage(), e);
        }
    }

    @Override
    public DataPermissionPolicy getPolicy(String policyId) throws DataPermissionPolicyException {
        try {
            return policyDao.findById(policyId);
        } catch (Exception e) {
            throw new DataPermissionPolicyException("获取数据权限策略失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<DataPermissionPolicy> listPolicies(Map<String, Object> condition) throws DataPermissionPolicyException {
        try {
            return policyDao.query(condition);
        } catch (Exception e) {
            throw new DataPermissionPolicyException("查询数据权限策略失败: " + e.getMessage(), e);
        }
    }
}


