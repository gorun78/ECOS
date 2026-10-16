package com.chinacreator.gzcm.sysman.dao;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * IAM 用户只读查询 DAO — 承接 AuthServiceImpl（用户租户）与
 * UserServiceImpl（用户-组织关联）中的 JdbcTemplate 操作。
 */
@Repository
public class UserLookupDao extends com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport {

    /** 按用户名回查租户ID（AuthServiceImpl.login）。 */
    public List<Map<String, Object>> findTenantByUsername(String username) {
        return jdbcTemplate.queryForList(
            "SELECT tenant_id FROM TD_USER WHERE username = ?", username);
    }

    // ── 用户-组织关联（td_user_organization）──

    public void deleteUserOrg(String userId) {
        jdbcTemplate.update("DELETE FROM td_user_organization WHERE user_id = ?", userId);
    }

    public void insertUserOrg(String userId, String orgId, String createdById) {
        jdbcTemplate.update(
            "INSERT INTO td_user_organization (user_id, org_id, is_primary, created_time, created_by) VALUES (?, ?, '1', now(), ?)",
            userId, orgId, createdById);
    }
}
