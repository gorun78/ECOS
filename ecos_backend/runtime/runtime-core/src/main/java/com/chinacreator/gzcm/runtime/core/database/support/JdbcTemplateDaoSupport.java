package com.chinacreator.gzcm.runtime.core.database.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.BeanPropertySqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.util.List;

public abstract class JdbcTemplateDaoSupport {

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    protected NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    protected NamedParameterJdbcTemplate getNamedParameterJdbcTemplate() {
        if (namedParameterJdbcTemplate == null) {
            namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
        }
        return namedParameterJdbcTemplate;
    }

    protected <T> List<T> queryList(String sql, Class<T> clazz, Object... args) {
        RowMapper<T> rowMapper = new BeanPropertyRowMapper<>(clazz);
        return jdbcTemplate.query(sql, rowMapper, args);
    }

    protected <T> List<T> queryListByBean(String sql, Class<T> clazz, Object bean) {
        SqlParameterSource paramSource = new BeanPropertySqlParameterSource(bean);
        RowMapper<T> rowMapper = new BeanPropertyRowMapper<>(clazz);
        return getNamedParameterJdbcTemplate().query(sql, paramSource, rowMapper);
    }

    protected <T> T queryObject(String sql, Class<T> clazz, Object... args) {
        RowMapper<T> rowMapper = new BeanPropertyRowMapper<>(clazz);
        return jdbcTemplate.queryForObject(sql, rowMapper, args);
    }

    protected <T> T queryObjectByBean(String sql, Class<T> clazz, Object bean) {
        SqlParameterSource paramSource = new BeanPropertySqlParameterSource(bean);
        RowMapper<T> rowMapper = new BeanPropertyRowMapper<>(clazz);
        return getNamedParameterJdbcTemplate().queryForObject(sql, paramSource, rowMapper);
    }

    protected String queryField(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, String.class, args);
    }

    protected Integer queryInt(String sql, Object... args) {
        Integer result = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return result != null ? result : 0;
    }

    protected Long queryLong(String sql, Object... args) {
        Long result = jdbcTemplate.queryForObject(sql, Long.class, args);
        return result != null ? result : 0L;
    }

    protected int insert(String sql, Object... args) {
        return jdbcTemplate.update(sql, args);
    }

    protected int insertByBean(String sql, Object bean) {
        SqlParameterSource paramSource = new BeanPropertySqlParameterSource(bean);
        return getNamedParameterJdbcTemplate().update(sql, paramSource);
    }

    protected Number insertAndReturnKey(String sql, Object bean, String keyColumn) {
        SqlParameterSource paramSource = new BeanPropertySqlParameterSource(bean);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        getNamedParameterJdbcTemplate().update(sql, paramSource, keyHolder, new String[]{keyColumn});
        return keyHolder.getKey();
    }

    protected int update(String sql, Object... args) {
        return jdbcTemplate.update(sql, args);
    }

    protected int updateByBean(String sql, Object bean) {
        SqlParameterSource paramSource = new BeanPropertySqlParameterSource(bean);
        return getNamedParameterJdbcTemplate().update(sql, paramSource);
    }

    protected int delete(String sql, Object... args) {
        return jdbcTemplate.update(sql, args);
    }

    protected int deleteByBean(String sql, Object bean) {
        SqlParameterSource paramSource = new BeanPropertySqlParameterSource(bean);
        return getNamedParameterJdbcTemplate().update(sql, paramSource);
    }

    protected int batchUpdate(String sql, List<Object[]> batchArgs) {
        int[] results = jdbcTemplate.batchUpdate(sql, batchArgs);
        int total = 0;
        for (int result : results) {
            total += result;
        }
        return total;
    }

    protected <T> int batchUpdateByBean(String sql, List<T> beans) {
        SqlParameterSource[] batchArgs = new SqlParameterSource[beans.size()];
        for (int i = 0; i < beans.size(); i++) {
            batchArgs[i] = new BeanPropertySqlParameterSource(beans.get(i));
        }
        int[] results = getNamedParameterJdbcTemplate().batchUpdate(sql, batchArgs);
        int total = 0;
        for (int result : results) {
            total += result;
        }
        return total;
    }

    protected <T> PageResult<T> queryPage(String sql, Class<T> clazz, int offset, int pageSize, Object... args) {
        String countSql = "SELECT COUNT(*) FROM (" + sql + ") AS _count_table";
        Long total = queryLong(countSql, args);
        
        String pageSql = sql + " LIMIT " + pageSize + " OFFSET " + offset;
        List<T> data = queryList(pageSql, clazz, args);
        
        return new PageResult<>(data, total, offset, pageSize);
    }

    protected <T> PageResult<T> queryPageByBean(String sql, Class<T> clazz, int offset, int pageSize, Object bean) {
        String countSql = "SELECT COUNT(*) FROM (" + sql + ") AS _count_table";
        SqlParameterSource paramSource = new BeanPropertySqlParameterSource(bean);
        Long total = getNamedParameterJdbcTemplate().queryForObject(countSql, paramSource, Long.class);
        
        String pageSql = sql + " LIMIT " + pageSize + " OFFSET " + offset;
        List<T> data = queryListByBean(pageSql, clazz, bean);
        
        return new PageResult<>(data, total != null ? total : 0L, offset, pageSize);
    }
}
