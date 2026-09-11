package com.chinacreator.gzcm.sysman.security.policy.service.impl;

import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.chinacreator.gzcm.sysman.security.policy.dao.DataSecurityPolicyDao;
import com.chinacreator.gzcm.sysman.security.policy.entity.DataSecurityPolicy;
import com.chinacreator.gzcm.sysman.security.policy.service.IDataSecurityPolicyService;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 数据安全策略服务实现
 */
public class DataSecurityPolicyServiceImpl implements IDataSecurityPolicyService {
    
    private final DataSecurityPolicyDao policyDao;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    public DataSecurityPolicyServiceImpl(DataSecurityPolicyDao policyDao) {
        this.policyDao = policyDao;
    }
    
    @Override
    public DataSecurityPolicy createPolicy(DataSecurityPolicy policy, String operator) throws DataSecurityPolicyException {
        try {
            // 验证策略配置
            ValidationResult validation = validatePolicy(policy.getPolicyType(), policy.getPolicyContent());
            if (!validation.isValid()) {
                throw new DataSecurityPolicyException("策略配置验证失败: " + validation.getErrorMessage());
            }
            
            // 设置默认值
            if (policy.getPolicyId() == null) {
                policy.setPolicyId(UUID.randomUUID().toString());
            }
            if (policy.getStatus() == null) {
                policy.setStatus("DRAFT");
            }
            if (policy.getEnabled() == null) {
                policy.setEnabled(false);
            }
            if (policy.getPriority() == null) {
                policy.setPriority(0);
            }
            policy.setCreatedTime(new Date());
            policy.setCreatedBy(operator);
            
            policyDao.insert(policy);
            return policy;
        } catch (Exception e) {
            throw new DataSecurityPolicyException("创建数据安全策略失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public DataSecurityPolicy updatePolicy(DataSecurityPolicy policy, String operator) throws DataSecurityPolicyException {
        try {
            // 验证策略配置
            ValidationResult validation = validatePolicy(policy.getPolicyType(), policy.getPolicyContent());
            if (!validation.isValid()) {
                throw new DataSecurityPolicyException("策略配置验证失败: " + validation.getErrorMessage());
            }
            
            policy.setUpdatedTime(new Date());
            policy.setUpdatedBy(operator);
            
            policyDao.update(policy);
            return policy;
        } catch (Exception e) {
            throw new DataSecurityPolicyException("更新数据安全策略失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void deletePolicy(String policyId, String operator) throws DataSecurityPolicyException {
        try {
            policyDao.delete(policyId);
        } catch (Exception e) {
            throw new DataSecurityPolicyException("删除数据安全策略失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public DataSecurityPolicy getPolicy(String policyId) throws DataSecurityPolicyException {
        try {
            return policyDao.findById(policyId);
        } catch (Exception e) {
            throw new DataSecurityPolicyException("获取数据安全策略失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public List<DataSecurityPolicy> listPolicies(String policyType, String scope, String scopeId, Boolean enabled) throws DataSecurityPolicyException {
        try {
            return policyDao.listByCondition(policyType, scope, scopeId, enabled);
        } catch (Exception e) {
            throw new DataSecurityPolicyException("查询数据安全策略失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public List<DataSecurityPolicy> getApplicablePolicies(String policyType, String scopeId) throws DataSecurityPolicyException {
        try {
            List<DataSecurityPolicy> policies = policyDao.findApplicablePolicies(policyType, scopeId);
            
            // 过滤出启用的策略，并按优先级排序
            return policies.stream()
                    .filter(p -> p.getEnabled() != null && p.getEnabled())
                    .filter(p -> "ACTIVE".equals(p.getStatus()))
                    .sorted(Comparator.comparing(DataSecurityPolicy::getPriority, Comparator.nullsLast(Comparator.reverseOrder())))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new DataSecurityPolicyException("获取适用的数据安全策略失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void enablePolicy(String policyId, String operator) throws DataSecurityPolicyException {
        try {
            DataSecurityPolicy policy = getPolicy(policyId);
            if (policy != null) {
                policy.setEnabled(true);
                policy.setStatus("ACTIVE");
                updatePolicy(policy, operator);
            }
        } catch (Exception e) {
            throw new DataSecurityPolicyException("启用策略失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void disablePolicy(String policyId, String operator) throws DataSecurityPolicyException {
        try {
            DataSecurityPolicy policy = getPolicy(policyId);
            if (policy != null) {
                policy.setEnabled(false);
                policy.setStatus("INACTIVE");
                updatePolicy(policy, operator);
            }
        } catch (Exception e) {
            throw new DataSecurityPolicyException("禁用策略失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public ValidationResult validatePolicy(String policyType, String policyContent) throws DataSecurityPolicyException {
        ValidationResult result = new ValidationResult(false);
        
        try {
            if (policyContent == null || policyContent.trim().isEmpty()) {
                result.setErrorMessage("策略内容不能为空");
                return result;
            }
            
            // 验证JSON格式
            try {
                objectMapper.readTree(policyContent);
            } catch (Exception e) {
                result.setErrorMessage("策略内容不是有效的JSON格式: " + e.getMessage());
                return result;
            }
            
            // 根据策略类型进行特定验证
            Map<String, Object> details = new HashMap<>();
            
            switch (policyType) {
                case "DATA_CLASSIFICATION":
                    // 验证数据分级分类策略
                    try {
                        objectMapper.readValue(policyContent, 
                            com.chinacreator.gzcm.sysman.security.policy.model.DataClassificationPolicy.class);
                        details.put("valid", true);
                    } catch (Exception e) {
                        result.setErrorMessage("数据分级分类策略格式错误: " + e.getMessage());
                        return result;
                    }
                    break;
                    
                case "DLP":
                    // 验证DLP策略
                    try {
                        objectMapper.readValue(policyContent, 
                            com.chinacreator.gzcm.sysman.security.policy.model.DLPPolicy.class);
                        details.put("valid", true);
                    } catch (Exception e) {
                        result.setErrorMessage("DLP策略格式错误: " + e.getMessage());
                        return result;
                    }
                    break;
                    
                case "DATA_INTEGRITY":
                    // 验证数据完整性保护策略
                    try {
                        objectMapper.readValue(policyContent, 
                            com.chinacreator.gzcm.sysman.security.policy.model.DataIntegrityPolicy.class);
                        details.put("valid", true);
                    } catch (Exception e) {
                        result.setErrorMessage("数据完整性保护策略格式错误: " + e.getMessage());
                        return result;
                    }
                    break;
                    
                case "ENCRYPTION":
                    // 验证加密策略
                    try {
                        objectMapper.readValue(policyContent, 
                            com.chinacreator.gzcm.sysman.security.policy.model.EncryptionPolicy.class);
                        details.put("valid", true);
                    } catch (Exception e) {
                        result.setErrorMessage("加密策略格式错误: " + e.getMessage());
                        return result;
                    }
                    break;
                    
                default:
                    result.setErrorMessage("未知的策略类型: " + policyType);
                    return result;
            }
            
            result.setValid(true);
            result.setDetails(details);
            return result;
            
        } catch (Exception e) {
            throw new DataSecurityPolicyException("验证策略配置失败: " + e.getMessage(), e);
        }
    }
}

