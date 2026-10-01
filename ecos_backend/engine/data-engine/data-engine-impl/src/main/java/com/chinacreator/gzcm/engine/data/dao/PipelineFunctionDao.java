package com.chinacreator.gzcm.engine.data.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * PB 函数注册表数据访问（ecos_pipeline_function）。
 *
 * <p>架构铁律 §2.5#1 / IR06：Service 不持有 JdbcTemplate，一律经 runtime 数据访问基类落库。</p>
 */
@Repository
public class PipelineFunctionDao extends JdbcTemplateDaoSupport {

    /** 内置函数已存在条数（种子幂等守门）。 */
    public int countBuiltin() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ecos_pipeline_function WHERE is_builtin = true", Integer.class);
    }

    /** 单条内置函数 upsert（ON CONFLICT (name)）。 */
    public void upsertBuiltinFunction(Object id, Object name, Object category, Object signature,
                                      Object returnType, Object description, Object example) {
        jdbcTemplate.update(
                "INSERT INTO ecos_pipeline_function (id, name, category, signature, return_type, description, example) "
                        + "VALUES (?, ?, ?, ?::text, ?, ?, ?) ON CONFLICT (name) DO UPDATE SET "
                        + "category = EXCLUDED.category, signature = EXCLUDED.signature, example = EXCLUDED.example",
                id, name, category, signature, returnType, description, example);
    }

    /** COUNT（配合 list 做分页 total）；sql 已带 WHERE/ORDER BY，不含 LIMIT/OFFSET。 */
    public int queryCount(String countSql, Object[] params) {
        return jdbcTemplate.queryForObject(countSql, Integer.class, params);
    }

    /** 分页/条件查询函数行。 */
    public List<Map<String, Object>> list(String sql, Object[] params) {
        return jdbcTemplate.queryForList(sql, params);
    }

    public List<Map<String, Object>> byCategory(String category) {
        return jdbcTemplate.queryForList(
                "SELECT * FROM ecos_pipeline_function WHERE category = ? ORDER BY name", category);
    }

    public List<Map<String, Object>> search(String like) {
        return jdbcTemplate.queryForList(
                "SELECT * FROM ecos_pipeline_function WHERE name ILIKE ? OR description ILIKE ? ORDER BY category, name",
                like, like);
    }

    public List<Map<String, Object>> byId(String id) {
        return jdbcTemplate.queryForList("SELECT * FROM ecos_pipeline_function WHERE id = ?", id);
    }

    public List<Map<String, Object>> categories() {
        return jdbcTemplate.queryForList(
                "SELECT category, COUNT(*) as count FROM ecos_pipeline_function GROUP BY category ORDER BY category");
    }

    public void insertCustom(String id, Object name, Object category, Object signature,
                             Object returnType, Object description, Object example) {
        jdbcTemplate.update(
                "INSERT INTO ecos_pipeline_function (id, name, category, signature, return_type, description, example, is_builtin) "
                        + "VALUES (?, ?, ?, ?::text, ?, ?, ?, false)",
                id, name, category, signature, returnType, description, example);
    }

    public void patch(String id, Object name, Object category, Object signature,
                      Object returnType, Object description, Object example) {
        jdbcTemplate.update(
                "UPDATE ecos_pipeline_function SET name = COALESCE(?, name), category = COALESCE(?, category), "
                        + "signature = COALESCE(?::text, signature), return_type = COALESCE(?, return_type), "
                        + "description = COALESCE(?, description), example = COALESCE(?, example) WHERE id = ?",
                name, category, signature, returnType, description, example, id);
    }

    public void remove(String id) {
        jdbcTemplate.update("DELETE FROM ecos_pipeline_function WHERE id = ?", id);
    }
}
