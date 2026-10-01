package com.chinacreator.gzcm.sysman.dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Portal 全局搜索 DAO — 承接 PortalSearchQueryService 中的 JdbcTemplate 操作（委托）。
 */
@Repository
public class PortalSearchQueryDao extends com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport {

    public List<Map<String, Object>> queryForList(String sql, Object... args) {
        return jdbcTemplate.queryForList(sql, args);
    }
}
