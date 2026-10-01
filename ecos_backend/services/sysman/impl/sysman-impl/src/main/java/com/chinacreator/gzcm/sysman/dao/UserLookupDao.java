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
            "SELECT \"TENANT_ID\" FROM TD_USER WHERE \"USERNAME\" = ?", username);
    }

    // ── 用户-组织关联（td_user_organization）──

    public void deleteUserOrg(String userId) {
        jdbcTemplate.update("DELETE FROM td_user_organization WHERE \"USER_ID\" = ?", userId);
    }

    public void insertUserOrg(String userId, String orgId, String createdById) {
        jdbcTemplate.update(
            "INSERT INTO td_user_organization (\"USER_ID\", \"ORG_ID\", \"IS_PRIMARY\", \"CREATED_TIME\", \"CREATED_BY\") VALUES (?, ?, '1', now(), ?)",
            userId, orgId, createdById);
    }
}
