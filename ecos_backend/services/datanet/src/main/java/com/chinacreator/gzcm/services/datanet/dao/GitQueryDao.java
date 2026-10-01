package com.chinacreator.gzcm.services.datanet.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * R2-B5 datanet 数据访问收口（架构铁律 §2.5 #1 / IR06）：Git 仓库元数据表 JDBC 落点。
 *
 * <p>承接原 {@link com.chinacreator.gzcm.services.datanet.service.GitQueryService}，
 * 把 raw-SQL JdbcTemplate 访问从服务层下沉到本 DAO（extends
 * {@code com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport}）；
 * 方法为通用查询委托，SQL / 参数由调用方（Controller）传入，原样搬运，只搬不改。</p>
 */
@Repository
public class GitQueryDao extends JdbcTemplateDaoSupport {

    public GitQueryDao(JdbcTemplate jdbc) {
        this.jdbcTemplate = jdbc;
    }

    public List<Map<String, Object>> queryForList(String sql, Object... args) {
        return jdbcTemplate.queryForList(sql, args);
    }

    public List<Map<String, Object>> queryForList(String sql) {
        return jdbcTemplate.queryForList(sql);
    }

    public <T> T queryForObject(String sql, Class<T> type, Object... args) {
        return jdbcTemplate.queryForObject(sql, type, args);
    }
}
