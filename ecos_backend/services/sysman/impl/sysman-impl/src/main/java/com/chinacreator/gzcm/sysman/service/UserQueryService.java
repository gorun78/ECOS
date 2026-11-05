package com.chinacreator.gzcm.sysman.service;

import com.chinacreator.gzcm.sysman.dao.UserQueryDao;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * UserQueryService — User 查询服务（PMO-E2: 从 UserController 下沉的 JdbcTemplate 访问层）。
 * <p>铁律 §2.5：数据访问下沉到 DAO（{@link UserQueryDao}）。
 */
@Service
public class UserQueryService {

    private final UserQueryDao dao;

    public UserQueryService(UserQueryDao dao) {
        this.dao = dao;
    }

    public List<Map<String, Object>> queryForList(String sql, Object... args) {
        return dao.queryForList(sql, args);
    }
    public List<Map<String, Object>> queryForList(String sql) {
        return dao.queryForList(sql);
    }
    public <T> T queryForObject(String sql, Class<T> type, Object... args) {
        return dao.queryForObject(sql, type, args);
    }
    public int update(String sql, Object... args) {
        return dao.update(sql, args);
    }

    /**
     * 用户列表页（#147CI：SQL 从 Controller 收回，行面改 {@link UserListRow} record）。
     *
     * <p>行键＝旧 SQL 的列别名（camelCase 11 键）；时间列保留 {@link java.sql.Timestamp}
     * ——旧实现返回原始 JDBC 对象，由同一 ObjectMapper 渲染，改字符串会漂格式。</p>
     */
    public UserPage listUsers(String keyword, String status, int page, int pageSize) {
        StringBuilder sql = new StringBuilder(
                "SELECT u.user_id as \"userId\", u.username as username, u.real_name as \"realName\", " +
                "u.email as email, u.mobile_tel1 as phone, u.status as status, " +
                "u.locked as locked, u.last_login_time as \"lastLoginTime\", " +
                "u.created_time as \"createdTime\", " +
                "o.org_id as \"orgId\", o.org_name as \"orgName\" " +
                "FROM TD_USER u " +
                "LEFT JOIN td_user_organization uo ON u.user_id = uo.user_id AND uo.is_primary = '1' " +
                "LEFT JOIN td_organization o ON uo.org_id = o.org_id " +
                "WHERE 1=1 ");
        List<Object> params = new ArrayList<>();
        if (keyword != null && !keyword.isEmpty()) {
            sql.append("AND (u.username ILIKE ? OR u.email ILIKE ?) ");
            String kw = "%" + keyword + "%";
            params.add(kw);
            params.add(kw);
        }
        if (status != null && !status.isEmpty()) {
            sql.append("AND u.status = ? ");
            params.add(status);
        } else {
            sql.append("AND u.status != 'DELETED' ");
        }
        sql.append("ORDER BY u.created_time DESC ");

        int total = dao.queryForObject("SELECT COUNT(*) FROM (" + sql + ") t", Integer.class,
                params.toArray());
        int offset = (page - 1) * pageSize;
        sql.append("LIMIT ? OFFSET ?");
        params.add(pageSize);
        params.add(offset);

        List<UserListRow> data = new ArrayList<>();
        for (Map<String, Object> row : dao.queryForList(sql.toString(), params.toArray())) {
            data.add(new UserListRow(asString(row.get("userId")), asString(row.get("username")),
                    asString(row.get("realName")), asString(row.get("email")),
                    asString(row.get("phone")), asString(row.get("status")),
                    asString(row.get("locked")), asTimestamp(row.get("lastLoginTime")),
                    asTimestamp(row.get("createdTime")), asString(row.get("orgId")),
                    asString(row.get("orgName"))));
        }
        return new UserPage(page, pageSize, total, data);
    }

    /** 用户角色（#147CI：3 键行面 record）。 */
    public List<UserRoleRow> listUserRoles(String userId) {
        String sql = "SELECT r.role_id as \"roleId\", r.role_name as \"roleName\", " +
                "r.role_code as \"roleCode\" FROM TD_ROLE r " +
                "INNER JOIN TD_USER_ROLE ur ON r.role_id = ur.role_id WHERE ur.user_id = ?";
        List<UserRoleRow> roles = new ArrayList<>();
        for (Map<String, Object> row : dao.queryForList(sql, userId)) {
            roles.add(new UserRoleRow(asString(row.get("roleId")), asString(row.get("roleName")),
                    asString(row.get("roleCode"))));
        }
        return roles;
    }

    /** 用户列表行（11 键，键序＝旧列别名顺序）。 */
    public record UserListRow(String userId, String username, String realName, String email,
                              String phone, String status, String locked,
                              java.sql.Timestamp lastLoginTime, java.sql.Timestamp createdTime,
                              String orgId, String orgName) {
    }

    /** 用户角色行（3 键）。 */
    public record UserRoleRow(String roleId, String roleName, String roleCode) {
    }

    /** 用户分页（4 键 page/pageSize/total/data）。 */
    public record UserPage(int page, int pageSize, int total, List<UserListRow> data) {
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static java.sql.Timestamp asTimestamp(Object value) {
        return value instanceof java.sql.Timestamp ts ? ts : null;
    }
}
