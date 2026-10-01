package com.chinacreator.gzcm.engine.security.service;

import com.chinacreator.gzcm.engine.security.dao.SecurityConfigDao;
import com.chinacreator.gzcm.sysman.model.SecurityProfile;
import com.chinacreator.gzcm.sysman.iam.context.TenantContext;
import com.chinacreator.gzcm.sysman.iam.context.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class SecurityConfigService {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfigService.class);

    private final SecurityConfigDao dao;

    public SecurityConfigService(SecurityConfigDao dao) {
        this.dao = dao;
    }

    public String getCurrentTenantId() {
        try {
            String tid = UserContext.getCurrentTenantId();
            if (tid != null && !tid.isBlank()) {
                return tid;
            }
        } catch (Exception e) {
            log.debug("从 UserContext 获取 tenantId 失败: {}", e.getMessage());
        }
        try {
            return TenantContext.getTenantId();
        } catch (Exception e) {
            log.debug("从 TenantContext 获取 tenantId 失败: {}", e.getMessage());
            return null;
        }
    }

    public SecurityProfile queryUserProfile(String userId) {
        try {
            String tenantId = getCurrentTenantId();
            List<SecurityProfile> list = dao.queryUserProfile(userId, tenantId);
            return list.isEmpty() ? null : list.get(0);
        } catch (Exception e) {
            log.warn("查询用户安全配置异常: userId={}, {}", userId, e.getMessage());
            return null;
        }
    }

    public SecurityProfile queryRoleProfile(String roleId) {
        try {
            String tenantId = getCurrentTenantId();
            List<SecurityProfile> list = dao.queryRoleProfile(roleId, tenantId);
            return list.isEmpty() ? null : list.get(0);
        } catch (Exception e) {
            log.warn("查询角色安全配置异常: roleId={}, {}", roleId, e.getMessage());
            return null;
        }
    }

    public SecurityProfile queryHighestRoleProfileForUser(String userId) {
        try {
            String tenantId = getCurrentTenantId();
            List<SecurityProfile> list = dao.queryHighestRoleProfileForUser(userId, tenantId);
            return list.isEmpty() ? null : list.get(0);
        } catch (Exception e) {
            log.warn("查询用户最高角色安全配置异常: userId={}, {}", userId, e.getMessage());
            return null;
        }
    }

    public SecurityProfile queryGlobalDefaultProfile() {
        try {
            String tenantId = getCurrentTenantId();
            List<SecurityProfile> list = dao.queryGlobalDefaultProfile(tenantId);
            return list.isEmpty() ? null : list.get(0);
        } catch (Exception e) {
            log.warn("查询全局默认安全配置异常: {}", e.getMessage());
            return null;
        }
    }

    public List<SecurityProfile> queryAllUserProfiles() {
        try {
            String tenantId = getCurrentTenantId();
            return dao.queryAllUserProfiles(tenantId);
        } catch (Exception e) {
            log.warn("查询所有用户安全配置异常: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public List<SecurityProfile> queryAllRoleProfiles() {
        try {
            String tenantId = getCurrentTenantId();
            return dao.queryAllRoleProfiles(tenantId);
        } catch (Exception e) {
            log.warn("查询所有角色安全配置异常: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public SecurityProfile queryProfileByScope(String scopeType, String scopeId) {
        try {
            if (!"TENANT".equals(scopeType) && !"ORG".equals(scopeType)) {
                log.debug("未知作用域类型: {}", scopeType);
                return null;
            }
            List<SecurityProfile> list = dao.queryProfileByScope(scopeType, scopeId);
            return list.isEmpty() ? null : list.get(0);
        } catch (Exception e) {
            log.warn("按作用域查询安全配置异常: scopeType={}, scopeId={}, {}", scopeType, scopeId, e.getMessage());
            return null;
        }
    }

    public List<SecurityProfile> queryProfilesByScope(String scopeType, String scopeId) {
        try {
            return dao.queryProfilesByScope(scopeType, scopeId);
        } catch (Exception e) {
            log.warn("按作用域列出安全配置异常: scopeType={}, scopeId={}, {}", scopeType, scopeId, e.getMessage());
            return Collections.emptyList();
        }
    }

    public Map<String, Object> populateProfileResult(SecurityProfile profile) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (profile != null) {
            result.put("clearanceLevel", profile.getClearanceLevel() != null ? profile.getClearanceLevel() : 1);
            result.put("level", profile.getLevel() != null ? profile.getLevel() : 1);
            result.put("levelName", profile.getLevelName() != null ? profile.getLevelName() : "L1内部");
            result.put("linkedWorkstation", profile.getLinkedWorkstation() != null ? profile.getLinkedWorkstation() : "");
            result.put("auditMode", profile.getAuditMode() != null ? profile.getAuditMode() : "basic");
            result.put("sandboxMandatory", Boolean.TRUE.equals(profile.getSandboxMandatory()));
            if (profile.getUserId() != null) result.put("userId", profile.getUserId());
            if (profile.getRoleId() != null) result.put("roleId", profile.getRoleId());
            if (profile.getScopeType() != null) result.put("scopeType", profile.getScopeType());
            if (profile.getTenantId() != null) result.put("tenantId", profile.getTenantId());
            if (profile.getOrgId() != null) result.put("orgId", profile.getOrgId());
        } else {
            result.put("clearanceLevel", 1);
            result.put("level", 1);
            result.put("levelName", "L1内部");
            result.put("linkedWorkstation", "");
            result.put("auditMode", "basic");
            result.put("sandboxMandatory", false);
        }
        return result;
    }

    public void upsertUserProfile(String userId, Integer clearanceLevel, String linkedWorkstation,
                                  String auditMode, Boolean sandboxMandatory, boolean isDefault,
                                  String scopeType, String tenantId, String orgId) {
        dao.upsertUserProfile(userId, clearanceLevel, linkedWorkstation, auditMode, sandboxMandatory,
                isDefault, scopeType, tenantId, orgId);
    }

    public void upsertRoleProfile(String roleId, Integer clearanceLevel, String linkedWorkstation,
                                  String auditMode, Boolean sandboxMandatory,
                                  String scopeType, String tenantId, String orgId) {
        dao.upsertRoleProfile(roleId, clearanceLevel, linkedWorkstation, auditMode, sandboxMandatory,
                scopeType, tenantId, orgId);
    }

    public void upsertProfile(String targetTable, String idColumn, String idValue,
                              Integer clearanceLevel, String linkedWorkstation,
                              String auditMode, Boolean sandboxMandatory, boolean isDefault,
                              String scopeType, String tenantId, String orgId) {
        dao.upsertProfile(targetTable, idColumn, idValue, clearanceLevel, linkedWorkstation,
                auditMode, sandboxMandatory, isDefault, scopeType, tenantId, orgId);
    }

    public void clearAllDefaults() {
        dao.clearAllDefaults();
    }

    public void activateProfile(String id) {
        dao.activateProfile(id);
    }

    public int deleteUserProfile(String id) {
        return dao.deleteUserProfile(id);
    }

    public int deleteRoleProfile(String id) {
        return dao.deleteRoleProfile(id);
    }

    public void updateProfileFields(String id, Integer clearanceLevel, String linkedWorkstation,
                                     String auditMode, Boolean sandboxMandatory, boolean isDefault) {
        dao.updateProfileFields(id, clearanceLevel, linkedWorkstation, auditMode, sandboxMandatory, isDefault);
    }

    public void cloneProfile(String sourceId, String newId) {
        SecurityProfile source = queryUserProfile(sourceId);
        if (source == null) {
            source = queryRoleProfile(sourceId);
        }
        if (source == null) {
            throw new IllegalArgumentException("源安全配置不存在: " + sourceId);
        }
        dao.cloneProfile(newId,
                source.getClearanceLevel(), source.getLinkedWorkstation(),
                source.getAuditMode(), source.getSandboxMandatory(),
                source.getScopeType(), source.getTenantId(), source.getOrgId());
    }

    public boolean ping() {
        return dao.ping();
    }
}
