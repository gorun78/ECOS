package com.chinacreator.gzcm.sysman.service;

import com.chinacreator.gzcm.sysman.dao.SecurityProfileQueryDao;
import com.chinacreator.gzcm.sysman.model.SecurityProfile;
import com.chinacreator.gzcm.sysman.security.profile.service.ISecurityProfileQueryService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * SecurityProfileQueryService — SecurityProfile 查询服务（PMO-E2: 从 SecurityProfileController 下沉的 JdbcTemplate 访问层）。
 * <p>铁律 §2.5：数据访问下沉到 SQL 属主 DAO（{@link SecurityProfileQueryDao}）。
 * <p>「取用户最高角色档」同时是属主 api 门面 {@link ISecurityProfileQueryService} 的实现
 * （#142/B4-3e-83，收口 D-B4-99 d)）：sysman 内消费方（密级五档链）与安全引擎都不再各自抄 SQL。
 */
@Service
public class SecurityProfileQueryService implements ISecurityProfileQueryService {

    private final SecurityProfileQueryDao dao;

    public SecurityProfileQueryService(SecurityProfileQueryDao dao) {
        this.dao = dao;
    }

    public SecurityProfile findUser(String userId, String tenantId) {
        return dao.findUser(userId, tenantId);
    }

    public SecurityProfile findRole(String roleId, String tenantId) {
        return dao.findRole(roleId, tenantId);
    }

    @Override
    public SecurityProfile findHighestRoleForUser(String userId, String tenantId) {
        return dao.findHighestRoleForUser(userId, tenantId);
    }

    public SecurityProfile findGlobalDefault(String tenantId) {
        return dao.findGlobalDefault(tenantId);
    }

    public List<SecurityProfile> listAllUser(String tenantId) {
        return dao.listAllUser(tenantId);
    }

    public List<SecurityProfile> listAllRole(String tenantId) {
        return dao.listAllRole(tenantId);
    }

    public SecurityProfile findUserByScope(String scopeType, String scopeId) {
        return dao.findUserByScope(scopeType, scopeId);
    }

    public int update(String sql, Object... args) {
        return dao.update(sql, args);
    }
}
