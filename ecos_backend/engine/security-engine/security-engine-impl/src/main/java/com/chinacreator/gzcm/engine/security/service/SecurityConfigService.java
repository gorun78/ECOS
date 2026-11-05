package com.chinacreator.gzcm.engine.security.service;

import com.chinacreator.gzcm.engine.security.dao.SecurityConfigDao;
import com.chinacreator.gzcm.sysman.model.SecurityProfile;
import com.chinacreator.gzcm.sysman.security.profile.service.ISecurityProfileQueryService;
import com.chinacreator.gzcm.sysman.security.clearance.model.ClearanceLevel;
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

    /**
     * 属主 api 门面（#142/B4-3e-83，收口 D-B4-99 d)）。本类原经 {@link SecurityConfigDao}
     * 自带一份 {@code JOIN td_user_role …} 逐字副本，与 sysman 属主侧同形语句必然漂移
     * （副本未排除空密级行，PG 的 {@code DESC} 默认 NULLS FIRST 会把空值档读成 L0）。
     * 现「取用户最高角色档」不再写在引擎侧，只走本门面（铁律 #5）。
     * <p><b>必装配</b>：四枚宿主（gateway、sysman、data-engine、security-engine-boot）都带
     * sysman-impl 且扫描 {@code com.chinacreator.gzcm.sysman}，缺席即 context 起不来——
     * 不做「拿不到就跳档」的折叠。
     */
    private final ISecurityProfileQueryService profileQueryService;

    public SecurityConfigService(SecurityConfigDao dao, ISecurityProfileQueryService profileQueryService) {
        this.dao = dao;
        this.profileQueryService = profileQueryService;
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
            return profileQueryService.findUser(userId, getCurrentTenantId());
        } catch (Exception e) {
            log.warn("查询用户安全配置异常: userId={}, {}", userId, e.getMessage());
            return null;
        }
    }

    public SecurityProfile queryRoleProfile(String roleId) {
        try {
            return profileQueryService.findRole(roleId, getCurrentTenantId());
        } catch (Exception e) {
            log.warn("查询角色安全配置异常: roleId={}, {}", roleId, e.getMessage());
            return null;
        }
    }

    public SecurityProfile queryHighestRoleProfileForUser(String userId) {
        try {
            String tenantId = getCurrentTenantId();
            return profileQueryService.findHighestRoleForUser(userId, tenantId);
        } catch (Exception e) {
            log.warn("查询用户最高角色安全配置异常: userId={}, {}", userId, e.getMessage());
            return null;
        }
    }

    public SecurityProfile queryGlobalDefaultProfile() {
        try {
            return profileQueryService.findGlobalDefault(getCurrentTenantId());
        } catch (Exception e) {
            log.warn("查询全局默认安全配置异常: {}", e.getMessage());
            return null;
        }
    }

    public List<SecurityProfile> queryAllUserProfiles() {
        try {
            return profileQueryService.listAllUser(getCurrentTenantId());
        } catch (Exception e) {
            log.warn("查询所有用户安全配置异常: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public List<SecurityProfile> queryAllRoleProfiles() {
        try {
            return profileQueryService.listAllRole(getCurrentTenantId());
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
            return profileQueryService.findUserByScope(scopeType, scopeId);
        } catch (Exception e) {
            log.warn("按作用域查询安全配置异常: scopeType={}, scopeId={}, {}", scopeType, scopeId, e.getMessage());
            return null;
        }
    }

    public List<SecurityProfile> queryProfilesByScope(String scopeType, String scopeId) {
        try {
            return profileQueryService.listProfilesByScope(scopeType, scopeId);
        } catch (Exception e) {
            log.warn("按作用域列出安全配置异常: scopeType={}, scopeId={}, {}", scopeType, scopeId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 安全配置视图（11 键；旧 Map 逐键同名同序）。
     *
     * <p>前 6 键恒在（缺省取 {@link ClearanceLevel} 单源常量）；后 5 键仅档案列非空时出现
     * ——属性级 NON_NULL 保持旧 {@code if (x != null) put} 语义。</p>
     */
    public record SecurityProfileView(
            @com.fasterxml.jackson.annotation.JsonProperty("clearanceLevel") Integer clearanceLevel,
            @com.fasterxml.jackson.annotation.JsonProperty("level") Integer level,
            @com.fasterxml.jackson.annotation.JsonProperty("levelName") String levelName,
            @com.fasterxml.jackson.annotation.JsonProperty("linkedWorkstation") String linkedWorkstation,
            @com.fasterxml.jackson.annotation.JsonProperty("auditMode") String auditMode,
            @com.fasterxml.jackson.annotation.JsonProperty("sandboxMandatory") boolean sandboxMandatory,
            @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
            @com.fasterxml.jackson.annotation.JsonProperty("userId") String userId,
            @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
            @com.fasterxml.jackson.annotation.JsonProperty("roleId") String roleId,
            @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
            @com.fasterxml.jackson.annotation.JsonProperty("scopeType") String scopeType,
            @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
            @com.fasterxml.jackson.annotation.JsonProperty("tenantId") String tenantId,
            @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
            @com.fasterxml.jackson.annotation.JsonProperty("orgId") String orgId) {
    }

    public SecurityProfileView populateProfileResult(SecurityProfile profile) {
        if (profile != null) {
            return new SecurityProfileView(
                    profile.getClearanceLevel() != null ? profile.getClearanceLevel() : ClearanceLevel.NO_PROFILE_FALLBACK,
                    profile.getLevel() != null ? profile.getLevel() : ClearanceLevel.NO_PROFILE_FALLBACK,
                    profile.getLevelName() != null ? profile.getLevelName() : ClearanceLevel.nameOf(ClearanceLevel.NO_PROFILE_FALLBACK),
                    profile.getLinkedWorkstation() != null ? profile.getLinkedWorkstation() : "",
                    profile.getAuditMode() != null ? profile.getAuditMode() : "basic",
                    Boolean.TRUE.equals(profile.getSandboxMandatory()),
                    profile.getUserId(), profile.getRoleId(), profile.getScopeType(),
                    profile.getTenantId(), profile.getOrgId());
        }
        return new SecurityProfileView(
                ClearanceLevel.NO_PROFILE_FALLBACK,
                ClearanceLevel.NO_PROFILE_FALLBACK,
                ClearanceLevel.nameOf(ClearanceLevel.NO_PROFILE_FALLBACK),
                "", "basic", false, null, null, null, null, null);
    }

    public void upsertUserProfile(String userId, Integer clearanceLevel, String linkedWorkstation,
                                  String auditMode, Boolean sandboxMandatory, boolean isDefault,
                                  String scopeType, String tenantId, String orgId) {
        profileQueryService.upsertUserProfile(userId, clearanceLevel, linkedWorkstation,
                auditMode, sandboxMandatory, isDefault, scopeType, tenantId, orgId);
    }

    public void upsertRoleProfile(String roleId, Integer clearanceLevel, String linkedWorkstation,
                                  String auditMode, Boolean sandboxMandatory,
                                  String scopeType, String tenantId, String orgId) {
        profileQueryService.upsertRoleProfile(roleId, clearanceLevel, linkedWorkstation,
                auditMode, sandboxMandatory, scopeType, tenantId, orgId);
    }

    /**
     * 通用 UPSERT 入口（#142/B4-3e-87，收口 D-B4-101）。
     *
     * <p><b>参数形态保持不变</b>（对外服务契约）：原 {@code targetTable} 与 {@code idColumn}
     * 不再下传属主侧拼标识符，本类先对目标档做 OPEN 集校验再映射到属主具名目标档
     * （{@code USER}/{@code ROLE}）——非白名单值在本腿抛 {@link IllegalArgumentException}
     * （原行为是 {@code String.format} 拼出非法语句后由 JDBC 报错，分态不变：调用方
     * catch→internalError）。</p>
     */
    public void upsertProfile(String targetTable, String idColumn, String idValue,
                              Integer clearanceLevel, String linkedWorkstation,
                              String auditMode, Boolean sandboxMandatory, boolean isDefault,
                              String scopeType, String tenantId, String orgId) {
        String profileTarget;
        if ("td_user_security_profile".equals(targetTable) && "user_id".equals(idColumn)) {
            profileTarget = "USER";
        } else if ("td_role_security_profile".equals(targetTable) && "role_id".equals(idColumn)) {
            profileTarget = "ROLE";
        } else {
            throw new IllegalArgumentException(
                    "未知档案目标: table=" + targetTable + ", column=" + idColumn
                            + "（只允许 td_user_security_profile/user_id 或 td_role_security_profile/role_id）");
        }
        profileQueryService.upsertCoalescing(profileTarget, idValue, clearanceLevel,
                linkedWorkstation, auditMode, sandboxMandatory, isDefault, scopeType, tenantId, orgId);
    }

    public void clearAllDefaults() {
        profileQueryService.clearAllDefaults();
    }

    public void activateProfile(String id) {
        profileQueryService.activateDefault(id);
    }

    public int deleteUserProfile(String id) {
        return profileQueryService.deleteUserProfile(id);
    }

    public int deleteRoleProfile(String id) {
        return profileQueryService.deleteRoleProfile(id);
    }

    public void updateProfileFields(String id, Integer clearanceLevel, String linkedWorkstation,
                                     String auditMode, Boolean sandboxMandatory, boolean isDefault) {
        profileQueryService.updateProfileFields(id, clearanceLevel, linkedWorkstation,
                auditMode, sandboxMandatory, isDefault);
    }

    public void cloneProfile(String sourceId, String newId) {
        SecurityProfile source = queryUserProfile(sourceId);
        if (source == null) {
            source = queryRoleProfile(sourceId);
        }
        if (source == null) {
            throw new IllegalArgumentException("源安全配置不存在: " + sourceId);
        }
        profileQueryService.cloneUserProfile(newId,
                source.getClearanceLevel(), source.getLinkedWorkstation(),
                source.getAuditMode(), source.getSandboxMandatory(),
                source.getScopeType(), source.getTenantId(), source.getOrgId());
    }

    public boolean ping() {
        return dao.ping();
    }
}
