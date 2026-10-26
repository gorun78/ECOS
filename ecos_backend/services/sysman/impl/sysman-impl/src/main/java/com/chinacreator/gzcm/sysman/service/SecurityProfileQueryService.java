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
 * （密级五档链）与安全引擎都不再各自抄 SQL。分两批收口——「取用户最高角色档」见
 * #142/B4-3e-83（D-B4-99 d)），其余六组读面见 #142/B4-3e-84（D-B4-100）。
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

    public int update(String sql, Object... args) {
        return dao.update(sql, args);
    }
}
