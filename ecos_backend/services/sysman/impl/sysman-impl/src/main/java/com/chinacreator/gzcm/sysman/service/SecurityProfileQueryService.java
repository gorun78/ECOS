package com.chinacreator.gzcm.sysman.service;

import com.chinacreator.gzcm.sysman.dao.SecurityProfileQueryDao;
import com.chinacreator.gzcm.sysman.model.SecurityProfile;
import com.chinacreator.gzcm.sysman.security.profile.service.ISecurityProfileQueryService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * SecurityProfileQueryService — SecurityProfile 查询与写面服务（PMO-E2: 从 SecurityProfileController 下沉的 JdbcTemplate 访问层）。
 * <p>铁律 §2.5：数据访问下沉到 SQL 属主 DAO（{@link SecurityProfileQueryDao}）。
 * <p>本类同时是属主 api 门面 {@link ISecurityProfileQueryService} 的实现：sysman 内消费方
 * （密级五档链）与安全引擎都不再各自抄 SQL。读面分四批收口——「取用户最高角色档」见
 * #142/B4-3e-83（D-B4-99 d)），其余六组读面见 #142/B4-3e-84（D-B4-100），
 * 角色码判读 {@link #hasRoleCode} 见 #142/B4-3e-86（D-B4-103），
 * 写面七腿（upsert×3／clearAllDefaults／activate／delete×2／updateFields／clone）
 * 见 #142/B4-3e-87（D-B4-101）。写面各腿本类只做纯委派，语句与语义全在 DAO 单源。
 */
@Service
public class SecurityProfileQueryService implements ISecurityProfileQueryService {

    private final SecurityProfileQueryDao dao;

    public SecurityProfileQueryService(SecurityProfileQueryDao dao) {
        this.dao = dao;
    }

    @Override
    public SecurityProfile findUser(String userId, String tenantId) {
        return dao.findUser(userId, tenantId);
    }

    @Override
    public SecurityProfile findRole(String roleId, String tenantId) {
        return dao.findRole(roleId, tenantId);
    }

    @Override
    public SecurityProfile findHighestRoleForUser(String userId, String tenantId) {
        return dao.findHighestRoleForUser(userId, tenantId);
    }

    @Override
    public SecurityProfile findGlobalDefault(String tenantId) {
        return dao.findGlobalDefault(tenantId);
    }

    @Override
    public List<SecurityProfile> listAllUser(String tenantId) {
        return dao.listAllUser(tenantId);
    }

    @Override
    public List<SecurityProfile> listAllRole(String tenantId) {
        return dao.listAllRole(tenantId);
    }

    @Override
    public SecurityProfile findUserByScope(String scopeType, String scopeId) {
        return dao.findUserByScope(scopeType, scopeId);
    }

    @Override
    public List<SecurityProfile> listProfilesByScope(String scopeType, String scopeId) {
        return dao.listProfilesByScope(scopeType, scopeId);
    }

    @Override
    public boolean hasRoleCode(String userId, String roleCode) {
        return dao.hasRoleCode(userId, roleCode);
    }

    // ── 写面（#142/B4-3e-87，收口 D-B4-101）—— 纯委派，语句单源在 DAO ──

    @Override
    public int upsertUserProfile(String userId, Integer clearanceLevel, String linkedWorkstation,
                                 String auditMode, Boolean sandboxMandatory, boolean isDefault,
                                 String scopeType, String tenantId, String orgId) {
        return dao.upsertUserProfile(userId, clearanceLevel, linkedWorkstation,
                auditMode, sandboxMandatory, isDefault, scopeType, tenantId, orgId);
    }

    @Override
    public int upsertRoleProfile(String roleId, Integer clearanceLevel, String linkedWorkstation,
                                 String auditMode, Boolean sandboxMandatory,
                                 String scopeType, String tenantId, String orgId) {
        return dao.upsertRoleProfile(roleId, clearanceLevel, linkedWorkstation,
                auditMode, sandboxMandatory, scopeType, tenantId, orgId);
    }

    @Override
    public int upsertCoalescing(String profileTarget, String idValue, Integer clearanceLevel,
                                String linkedWorkstation, String auditMode, Boolean sandboxMandatory,
                                boolean isDefault, String scopeType, String tenantId, String orgId) {
        return dao.upsertCoalescing(profileTarget, idValue, clearanceLevel, linkedWorkstation,
                auditMode, sandboxMandatory, isDefault, scopeType, tenantId, orgId);
    }

    @Override
    public int clearAllDefaults() {
        return dao.clearAllDefaults();
    }

    @Override
    public int activateDefault(String userId) {
        return dao.activateDefault(userId);
    }

    @Override
    public int deleteUserProfile(String userId) {
        return dao.deleteUserProfile(userId);
    }

    @Override
    public int deleteRoleProfile(String roleId) {
        return dao.deleteRoleProfile(roleId);
    }

    @Override
    public int updateProfileFields(String userId, Integer clearanceLevel, String linkedWorkstation,
                                   String auditMode, Boolean sandboxMandatory, boolean isDefault) {
        return dao.updateProfileFields(userId, clearanceLevel, linkedWorkstation,
                auditMode, sandboxMandatory, isDefault);
    }

    @Override
    public int cloneUserProfile(String newUserId, Integer clearanceLevel, String linkedWorkstation,
                                String auditMode, Boolean sandboxMandatory,
                                String scopeType, String tenantId, String orgId) {
        return dao.cloneUserProfile(newUserId, clearanceLevel, linkedWorkstation,
                auditMode, sandboxMandatory, scopeType, tenantId, orgId);
    }

    public int update(String sql, Object... args) {
        return dao.update(sql, args);
    }
}
