package com.chinacreator.gzcm.engine.data.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * DataLayerService 的 JDBC 数据访问层。
 * <p>td_data_resource 按层计数与查询走本 DAO。</p>
 */
@Repository
public class DataLayerDao extends JdbcTemplateDaoSupport {

    public Integer countResourcesByLayer(String layer) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM td_data_resource WHERE layer = ?", Integer.class, layer);
    }

    public List<Map<String, Object>> getResourcesByLayer(String layer) {
        return jdbcTemplate.queryForList(
            "SELECT * FROM td_data_resource WHERE layer = ?", layer);
    }
}
