package com.chinacreator.gzcm.engine.data.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * CatalogDashboardService 的 JDBC 数据访问层。
 * <p>大盘计数的系统 PG 查询走本 DAO。</p>
 */
@Repository
public class CatalogDashboardDao extends JdbcTemplateDaoSupport {

    public Long countResources() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM td_data_resource", Long.class);
    }

    public Long countFields() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM td_data_field", Long.class);
    }

    public Map<String, Long> countByType() {
        Map<String, Long> typeCounts = new LinkedHashMap<>();
        jdbcTemplate.query("SELECT resource_type, COUNT(*) FROM td_data_resource GROUP BY resource_type",
                (org.springframework.jdbc.core.RowCallbackHandler) rs -> typeCounts.put(rs.getString(1), rs.getLong(2)));
        return typeCounts;
    }
}
