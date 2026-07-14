package com.chinacreator.gzcm.runtime.core.security.abac.service.impl;


import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.chinacreator.gzcm.sysman.abac.cache.AbacPolicyCacheService;
import com.chinacreator.gzcm.sysman.abac.dao.AbacPolicyDao;
import com.chinacreator.gzcm.sysman.abac.model.AbacPolicy;
import com.chinacreator.gzcm.sysman.abac.service.IAbacPolicyService;

public class AbacPolicyServiceImpl implements IAbacPolicyService {

    private final AbacPolicyDao policyDao;
    private final AbacPolicyCacheService cacheService;

    public AbacPolicyServiceImpl(AbacPolicyDao policyDao, AbacPolicyCacheService cacheService) {
        this.policyDao = policyDao;
        this.cacheService = cacheService;
    }

    @Override
    public AbacPolicy createPolicy(AbacPolicy policy) throws AbacException {
        try {
            policy.setPolicyId(policy.getPolicyId() == null ? UUID.randomUUID().toString() : policy.getPolicyId());
            if (policy.getEffect() == null) {
                policy.setEffect("ALLOW");
            }
            if (policy.getPriority() == null) {
                policy.setPriority(0);
            }
            policy.setCreatedTime(LocalDateTime.now());
            policyDao.insert(policy);
            // 策略变更后刷新缓存
            refreshCacheQuietly();
            return policyDao.findById(policy.getPolicyId());
        } catch (Exception e) {
            throw new AbacException("创建策略失败: " + e.getMessage(), e);
        }
    }

    @Override
    public AbacPolicy updatePolicy(AbacPolicy policy) throws AbacException {
        try {
            AbacPolicy existing = policyDao.findById(policy.getPolicyId());
            if (existing == null) {
                throw new AbacException("策略不存在: " + policy.getPolicyId());
            }
            if (policy.getPolicyName() != null) existing.setPolicyName(policy.getPolicyName());
            if (policy.getSubjectCondition() != null) existing.setSubjectCondition(policy.getSubjectCondition());
            if (policy.getResourceCondition() != null) existing.setResourceCondition(policy.getResourceCondition());
            if (policy.getActionCondition() != null) existing.setActionCondition(policy.getActionCondition());
            if (policy.getEnvironmentCondition() != null) existing.setEnvironmentCondition(policy.getEnvironmentCondition());
            if (policy.getEffect() != null) existing.setEffect(policy.getEffect());
            if (policy.getPriority() != null) existing.setPriority(policy.getPriority());
            policyDao.update(existing);
            // 策略变更后刷新缓存
            refreshCacheQuietly();
            return policyDao.findById(existing.getPolicyId());
        } catch (AbacException e) {
            throw e;
        } catch (Exception e) {
            throw new AbacException("更新策略失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void deletePolicy(String policyId) throws AbacException {
        try {
            policyDao.delete(policyId);
            // 策略变更后刷新缓存
            refreshCacheQuietly();
        } catch (Exception e) {
            throw new AbacException("删除策略失败: " + e.getMessage(), e);
        }
    }

    @Override
    public AbacPolicy getPolicy(String policyId) throws AbacException {
        try {
            return policyDao.findById(policyId);
        } catch (Exception e) {
            throw new AbacException("获取策略失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<AbacPolicy> listPolicies() throws AbacException {
        try {
            return policyDao.findAll();
        } catch (Exception e) {
            throw new AbacException("查询策略列表失败: " + e.getMessage(), e);
        }
    }

    private void refreshCacheQuietly() {
        if (cacheService == null) {
            return;
        }
        try {
            List<AbacPolicy> all = policyDao.findAll();
            cacheService.refreshAll(all);
        } catch (Exception e) {
            // 刷新缓存失败不影响主流程，简单忽略或打印日志（此处先忽略）
        }
    }
}


