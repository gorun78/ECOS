package com.chinacreator.gzcm.sysman.abac.service.impl;

import com.chinacreator.gzcm.sysman.abac.model.AbacPolicy;
import com.chinacreator.gzcm.sysman.abac.service.IAbacPolicyService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class InMemoryAbacPolicyService implements IAbacPolicyService {

    private final Map<String, AbacPolicy> store = new ConcurrentHashMap<>();

    @Override
    public AbacPolicy createPolicy(AbacPolicy policy) throws AbacException {
        if (policy.getPolicyId() == null || policy.getPolicyId().isEmpty()) {
            policy.setPolicyId(UUID.randomUUID().toString().replace("-", ""));
        }
        if (policy.getEffect() == null) policy.setEffect("ALLOW");
        if (policy.getPriority() == null) policy.setPriority(100);
        policy.setCreatedTime(LocalDateTime.now());
        store.put(policy.getPolicyId(), policy);
        return policy;
    }

    @Override
    public AbacPolicy updatePolicy(AbacPolicy policy) throws AbacException {
        AbacPolicy existing = store.get(policy.getPolicyId());
        if (existing == null) throw new AbacException("策略不存在: " + policy.getPolicyId());
        if (policy.getPolicyName() != null) existing.setPolicyName(policy.getPolicyName());
        if (policy.getSubjectCondition() != null) existing.setSubjectCondition(policy.getSubjectCondition());
        if (policy.getResourceCondition() != null) existing.setResourceCondition(policy.getResourceCondition());
        if (policy.getActionCondition() != null) existing.setActionCondition(policy.getActionCondition());
        if (policy.getEnvironmentCondition() != null) existing.setEnvironmentCondition(policy.getEnvironmentCondition());
        if (policy.getEffect() != null) existing.setEffect(policy.getEffect());
        if (policy.getPriority() != null) existing.setPriority(policy.getPriority());
        if (policy.getScopeType() != null) existing.setScopeType(policy.getScopeType());
        if (policy.getScopeId() != null) existing.setScopeId(policy.getScopeId());
        return existing;
    }

    @Override
    public void deletePolicy(String policyId) throws AbacException {
        store.remove(policyId);
    }

    @Override
    public AbacPolicy getPolicy(String policyId) throws AbacException {
        return store.get(policyId);
    }

    @Override
    public List<AbacPolicy> listPolicies() throws AbacException {
        return new ArrayList<>(store.values());
    }
}
