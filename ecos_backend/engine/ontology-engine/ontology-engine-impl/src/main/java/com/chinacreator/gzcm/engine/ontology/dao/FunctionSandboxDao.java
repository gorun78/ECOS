package com.chinacreator.gzcm.engine.ontology.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

/**
 * B3c 数据访问收口（R2/B3c）：{@code FunctionSandboxEngine} 编译结果 SQL 的沙箱执行
 * （PreparedStatement + setQueryTimeout(5) + 参数化）的 JDBC 落点。
 *
 * <p>保留源代码的 {@link org.springframework.jdbc.core.PreparedStatementCreator} 用法，
 * 直接经继承的 {@code jdbcTemplate} 调用 {@code query(PreparedStatementCreator, RowMapper)}。
 * SQL 文本、超时、参数绑定原样搬运。</p>
 */
@Repository
public class FunctionSandboxDao extends JdbcTemplateDaoSupport {

    static final int QUERY_TIMEOUT_SECONDS = 5;

    /** 无参数沙箱查询（带 5s 超时），返回标量 value（无行返回 null）。 */
    public Object executeWithTimeout(String sql) {
        return jdbcTemplate.query((Connection conn) -> {
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            return ps;
        }, (ResultSet rs) -> {
            if (rs.next()) {
                return rs.getObject("result");
            }
            return null;
        });
    }

    /** 带参数沙箱查询（带 5s 超时），返回标量 value（无行返回 null）。 */
    public Object executeWithTimeout(String sql, List<Object> params) {
        return jdbcTemplate.query((Connection conn) -> {
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            return ps;
        }, (ResultSet rs) -> {
            if (rs.next()) {
                return rs.getObject("result");
            }
            return null;
        });
    }
}
