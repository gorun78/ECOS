package com.chinacreator.gzcm.services.datanet.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * R2-B5 datanet 数据访问收口（架构铁律 §2.5 #1 / IR06）：DQ 看板表 JDBC 落点。
 *
 * <p>承接原 {@link com.chinacreator.gzcm.services.datanet.service.DqDashboardService}，
 * 把 raw-SQL JdbcTemplate 访问从服务层下沉到本 DAO（extends
 * {@code com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport}）；
 * 方法为通用查询/更新委托，SQL / 参数由调用方（Controller）传入，原样搬运，只搬不改。</p>
 */
@Repository
public class DqDashboardDao extends JdbcTemplateDaoSupport {

    public DqDashboardDao(JdbcTemplate jdbc) {
        this.jdbcTemplate = jdbc;
    }

    public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
        return jdbcTemplate.query(sql, rowMapper, args);
    }

    public <T> List<T> query(String sql, RowMapper<T> rowMapper) {
        return jdbcTemplate.query(sql, rowMapper);
    }

    public <T> T queryForObject(String sql, Class<T> type, Object... args) {
        return jdbcTemplate.queryForObject(sql, type, args);
    }

    public int update(String sql, Object... args) {
        return jdbcTemplate.update(sql, args);
    }
}
