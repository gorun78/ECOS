package com.chinacreator.gzcm.runtime.core.security.policy.engine.impl;


import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.sysman.iam.entity.UserAccount;
import com.chinacreator.gzcm.sysman.iam.service.IOrganizationService;
import com.chinacreator.gzcm.sysman.iam.service.IUserService;
import com.chinacreator.gzcm.sysman.policy.engine.PIP;
import com.chinacreator.gzcm.sysman.iam.entity.Organization;

/**
 * Policy Information Point 实现
 * 提供策略评估所需的主体、资源、环境属性
 */
public class PIPImpl implements PIP {
    
    private final IUserService userService;
    private final IOrganizationService organizationService;
    
    public PIPImpl(IUserService userService, IOrganizationService organizationService) {
        this.userService = userService;
        this.organizationService = organizationService;
    }
    
    @Override
    public Map<String, Object> getSubjectAttributes(String subjectId) throws PolicyInformationException {
        try {
            Map<String, Object> attributes = new HashMap<>();
            
            if (subjectId == null) {
                return attributes;
            }
            
            // 获取用户信息
            UserAccount user = userService.getUserByUserId(subjectId);
            if (user != null) {
                attributes.put("userId", user.getUserId());
                attributes.put("username", user.getUsername());
                attributes.put("email", user.getEmail());
                attributes.put("phone", user.getPhone());
                attributes.put("status", user.getStatus());
                attributes.put("userRealname", user.getUserRealname());
                attributes.put("userType", user.getUserType());
                
                // 获取用户所属机构
                try {
                    List<com.chinacreator.gzcm.sysman.iam.entity.Organization> orgs = 
                        organizationService.getUserOrganizations(subjectId);
                    if (orgs != null && !orgs.isEmpty()) {
                        attributes.put("organizationId", orgs.get(0).getOrgId());
                        attributes.put("organizationName", orgs.get(0).getOrgName());
                        attributes.put("organizationCode", orgs.get(0).getOrgCode());
                        attributes.put("organizationType", orgs.get(0).getOrgType());
                    }
                } catch (Exception e) {
                    // 忽略机构查询错误
                }
            }
            
            return attributes;
        } catch (Exception e) {
            throw new PolicyInformationException("获取主体属性失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public Map<String, Object> getResourceAttributes(String resourceId) throws PolicyInformationException {
        try {
            Map<String, Object> attributes = new HashMap<>();
            
            if (resourceId == null) {
                return attributes;
            }
            
            // 基本资源属性
            attributes.put("resourceId", resourceId);
            attributes.put("resourceType", inferResourceType(resourceId));
            
            // 可以根据resourceId从资源服务获取更多属性
            // 这里简化处理，实际应该从资源管理服务获取
            
            return attributes;
        } catch (Exception e) {
            throw new PolicyInformationException("获取资源属性失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public Map<String, Object> getEnvironmentAttributes() throws PolicyInformationException {
        try {
            Map<String, Object> attributes = new HashMap<>();
            
            // 时间属性
            attributes.put("currentTime", new Date());
            attributes.put("currentTimeMillis", System.currentTimeMillis());
            
            // IP地址（需要从请求上下文获取，这里简化处理）
            // attributes.put("ipAddress", getCurrentIpAddress());
            
            // 地理位置（需要从IP服务获取，这里简化处理）
            // attributes.put("location", getLocation());
            
            return attributes;
        } catch (Exception e) {
            throw new PolicyInformationException("获取环境属性失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public Object getAttribute(String attributeName, Map<String, Object> context) throws PolicyInformationException {
        try {
            if (attributeName == null || attributeName.isEmpty()) {
                return null;
            }
            
            String[] parts = attributeName.split("\\.");
            if (parts.length < 2) {
                return null;
            }
            
            String namespace = parts[0];
            String key = parts[1];
            
            switch (namespace.toLowerCase()) {
                case "subject":
                    String subjectId = context != null ? (String) context.get("subjectId") : null;
                    if (subjectId != null) {
                        Map<String, Object> subjectAttrs = getSubjectAttributes(subjectId);
                        return subjectAttrs.get(key);
                    }
                    break;
                case "resource":
                    String resourceId = context != null ? (String) context.get("resourceId") : null;
                    if (resourceId != null) {
                        Map<String, Object> resourceAttrs = getResourceAttributes(resourceId);
                        return resourceAttrs.get(key);
                    }
                    break;
                case "environment":
                    Map<String, Object> envAttrs = getEnvironmentAttributes();
                    return envAttrs.get(key);
                default:
                    // 从上下文中直接获取
                    if (context != null) {
                        return context.get(attributeName);
                    }
            }
            
            return null;
        } catch (Exception e) {
            throw new PolicyInformationException("获取属性失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public Map<String, Object> getAttributes(List<String> attributeNames, Map<String, Object> context) throws PolicyInformationException {
        try {
            Map<String, Object> result = new HashMap<>();
            
            for (String attrName : attributeNames) {
                Object value = getAttribute(attrName, context);
                if (value != null) {
                    result.put(attrName, value);
                }
            }
            
            return result;
        } catch (Exception e) {
            throw new PolicyInformationException("批量获取属性失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 推断资源类型
     */
    private String inferResourceType(String resourceId) {
        if (resourceId == null) {
            return "UNKNOWN";
        }
        // 简化实现：根据资源ID的前缀或格式推断类型
        if (resourceId.startsWith("api:")) {
            return "API";
        } else if (resourceId.startsWith("data:")) {
            return "DATA";
        } else if (resourceId.startsWith("file:")) {
            return "FILE";
        }
        return "UNKNOWN";
    }
}

