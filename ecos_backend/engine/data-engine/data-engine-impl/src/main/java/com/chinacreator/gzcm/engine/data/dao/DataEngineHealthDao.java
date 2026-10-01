package com.chinacreator.gzcm.engine.data.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

/**
 * DataEngineHealthService 的 JDBC 数据访问层。
 * <p>健康探测与计数查询走本 DAO，service 层不再持有 JdbcTemplate。</p>
 */
@Repository
public class DataEngineHealthDao extends JdbcTemplateDaoSupport {

    /** 探测数据库连通性。成功返回 1，异常由调用方捕获。 */
    public Integer pingDb() {
        return jdbcTemplate.queryForObject("SELECT 1", Integer.class);
    }

    public long countDatasources() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM td_datasource", Long.class);
    }

    public long countResources() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM td_data_resource", Long.class);
    }

    public long countCatalogItems() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM td_catalog_item", Long.class);
    }

    public long countFields() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM td_data_field", Long.class);
    }
}
