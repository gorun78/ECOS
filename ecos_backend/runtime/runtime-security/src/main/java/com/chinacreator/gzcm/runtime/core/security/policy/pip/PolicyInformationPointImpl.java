package com.chinacreator.gzcm.runtime.core.security.policy.pip;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.sysman.iam.entity.UserAccount;
import com.chinacreator.gzcm.sysman.iam.entity.UserRole;
import com.chinacreator.gzcm.sysman.iam.dao.UserRoleDao;
import com.chinacreator.gzcm.sysman.iam.service.IUserService;
import com.chinacreator.gzcm.sysman.iam.service.IOrganizationService;
import com.chinacreator.gzcm.sysman.iam.service.ITenantService;
import com.chinacreator.gzcm.sysman.policy.pip.PolicyInformationPoint;

/**
 * 策略信息点实现：从各种服务获取属性信息
 */
public class PolicyInformationPointImpl implements PolicyInformationPoint {

    private final IUserService userService;
    private final UserRoleDao userRoleDao;
    private final IOrganizationService organizationService;
    private final ITenantService tenantService;

    public PolicyInformationPointImpl(IUserService userService,
                                      UserRoleDao userRoleDao,
                                      IOrganizationService organizationService,
                                      ITenantService tenantService) {
        this.userService = userService;
        this.userRoleDao = userRoleDao;
        this.organizationService = organizationService;
        this.tenantService = tenantService;
    }

    @Override
    public Map<String, Object> getUserAttributes(String userId) {
        Map<String, Object> attributes = new HashMap<>();
        try {
            UserAccount user = userService.getUser(userId);
            if (user != null) {
                attributes.put("userId", user.getUserId());
                attributes.put("username", user.getUsername());
                attributes.put("email", user.getEmail());
                attributes.put("phone", user.getPhone());
                attributes.put("status", user.getStatus());
                // 获取用户角色
                try {
                    List<UserRole> userRoles = userRoleDao.listByUserId(userId);
                    attributes.put("roles", userRoles.stream().map(UserRole::getRoleId).toList());
                } catch (Exception e) {
                    // 忽略角色获取失败
                }
                // 获取用户机构
                try {
                    var orgs = organizationService.getUserOrganizations(userId);
                    attributes.put("organizations", orgs.stream().map(o -> o.getOrgId()).toList());
                } catch (Exception e) {
                    // 忽略机构获取失败
                }
            }
        } catch (Exception e) {
            // 返回空属性
        }
        return attributes;
    }

    @Override
    public Map<String, Object> getResourceAttributes(String resourceId) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("resourceId", resourceId);
        // TODO: 从资源服务获取更多属性（如数据分类、敏感级别等）
        return attributes;
    }

    @Override
    public Map<String, Object> getEnvironmentAttributes() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("timestamp", System.currentTimeMillis());
        // TODO: 从请求上下文获取IP、设备等信息
        return attributes;
    }

    @Override
    public Map<String, Object> getTenantAttributes(String tenantId) {
        Map<String, Object> attributes = new HashMap<>();
        try {
            var tenant = tenantService.getTenant(tenantId);
            if (tenant != null) {
                attributes.put("tenantId", tenant.getTenantId());
                attributes.put("tenantName", tenant.getTenantName());
                attributes.put("status", tenant.getStatus());
            }
        } catch (Exception e) {
            // 返回空属性
        }
        return attributes;
    }
}

