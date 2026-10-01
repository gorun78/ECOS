package com.chinacreator.gzcm.engine.security.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

/**
 * 安全沙箱 DAO（R2-B3b 数据访问收口；只搬不改语义）。
 *
 * <p>表：{@code td_user_security_profile} + {@code td_role_security_profile}
 * （JOIN {@code td_user_role} 角色，SEC-P0-5 超级管理员角色判断）。
 * 供 {@code SecuritySandboxService} 使用；异常 → 0（fail-closed 判 false 由 service 决定）。</p>
 */
@Repository
public class SecuritySandboxDao extends JdbcTemplateDaoSupport {

    /** 用户是否绑定 ROLE_SUPER_ADMIN 角色（queryForList 避免空结果异常）。 */
    public int countSuperAdminRole(String userId) {
        var rows = jdbcTemplate.queryForList(
            "SELECT COUNT(*) FROM td_user_security_profile usp " +
            "JOIN td_role_security_profile rsp ON usp.profile_id = rsp.profile_id " +
            "WHERE usp.user_id = ? AND rsp.role_name = 'ROLE_SUPER_ADMIN'",
            Long.class, userId);
        if (rows.isEmpty()) return 0;
        return ((Number) rows.get(0)).intValue();
    }
}
