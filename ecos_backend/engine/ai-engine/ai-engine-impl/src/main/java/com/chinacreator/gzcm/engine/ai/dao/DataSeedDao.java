package com.chinacreator.gzcm.engine.ai.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

/**
 * 数据访问收口（R2-B4-ai / 架构铁律 §2.5 #1 / IR06）：启动种子数据（角色/权限映射）的 JDBC 落点。
 * <p>SQL 原样搬运自原 {@code config.DataInitializer#seedRoles}，只搬不改语义；
 * 种子数据的组装（5 roles × 22 perms × 21 映射）仍由 DataInitializer 提供。**
 */
@Repository
public class DataSeedDao extends JdbcTemplateDaoSupport {

    public long countRoles() {
        Long n = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM td_role", Long.class);
        return n != null ? n : 0L;
    }

    public int insertRole(String roleId, String roleName, String roleCode, String description, String now) {
        return jdbcTemplate.update(
            "INSERT INTO td_role (ROLE_ID, ROLE_NAME, ROLE_CODE, DESCRIPTION, STATUS, CREATED_TIME, CREATED_BY) " +
            "VALUES (?, ?, ?, ?, 'ACTIVE', ?::timestamp, 'system')",
            roleId, roleName, roleCode, description, now);
    }

    public int insertPermission(String permId, String permName, String permCode,
                                String resourceId, String action, String description, String now) {
        return jdbcTemplate.update(
            "INSERT INTO td_permission (PERMISSION_ID, PERMISSION_NAME, PERMISSION_CODE, RESOURCE_ID, ACTION, " +
            "DESCRIPTION, CREATED_TIME, CREATED_BY) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?::timestamp, 'system')",
            permId, permName, permCode, resourceId, action, description, now);
    }

    public int insertRolePermission(String roleId, String permId, String now) {
        return jdbcTemplate.update(
            "INSERT INTO td_role_permission (ROLE_ID, PERMISSION_ID, CREATED_TIME) VALUES (?, ?, ?::timestamp)",
            roleId, permId, now);
    }
}
