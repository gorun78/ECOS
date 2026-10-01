package com.chinacreator.gzcm.engine.ai.dao;

import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

/**
 * 数据访问收口（R2-B4-ai / 架构铁律 §2.5 #1 / IR06）：NLQ 查询的 JDBC 落点。
 * <p>SQL 原样搬运自原 {@code service.NLQService}，只搬不改语义；
 * 执行 ObjectQL 解析后 SQL 的 SQL 文本由调用方传入。</p>
 */
@Repository
public class NLQDao extends JdbcTemplateDaoSupport {

    public List<Map<String, Object>> queryForList(String sql, Object[] params) {
        if (params == null || params.length == 0) {
            return jdbcTemplate.queryForList(sql);
        }
        return jdbcTemplate.queryForList(sql, params);
    }

    public List<Map<String, Object>> queryForList(String sql) {
        return jdbcTemplate.queryForList(sql);
    }
}
