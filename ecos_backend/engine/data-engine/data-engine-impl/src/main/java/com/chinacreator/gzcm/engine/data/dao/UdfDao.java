package com.chinacreator.gzcm.engine.data.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * UdfServiceImpl 的 JDBC 数据访问层。
 * <p>表: ecos_pipeline_udf。原始 SQL 均在此执行。</p>
 */
@Repository
public class UdfDao extends JdbcTemplateDaoSupport {

    public void insert(String id, Map<String, Object> body) {
        jdbcTemplate.update(
            "INSERT INTO ecos_pipeline_udf (id, name, category, language, signature, source_code, author, is_shared, description) " +
            "VALUES (?, ?, ?, ?, ?::text, ?, ?, ?, ?)",
            id,
            body.get("name"),
            body.getOrDefault("category", "transform"),
            body.getOrDefault("language", "python"),
            body.getOrDefault("signature", "[]"),
            body.get("source_code"),
            body.getOrDefault("author", "system"),
            body.getOrDefault("is_shared", false),
            body.getOrDefault("description", ""));
    }

    public void update(String id, Map<String, Object> body) {
        jdbcTemplate.update(
            "UPDATE ecos_pipeline_udf SET name = COALESCE(?, name), category = COALESCE(?, category), " +
            "language = COALESCE(?, language), signature = COALESCE(?::text, signature), " +
            "source_code = COALESCE(?, source_code), description = COALESCE(?, description), " +
            "is_shared = COALESCE(?, is_shared), version = version + 1, updated_at = NOW() WHERE id = ?",
            body.get("name"), body.get("category"), body.get("language"),
            body.get("signature"), body.get("source_code"), body.get("description"),
            body.get("is_shared"), id);
    }

    public void delete(String id) {
        jdbcTemplate.update("DELETE FROM ecos_pipeline_udf WHERE id = ?", id);
    }

    /** @return 命中行；不存在返回 {@code null}。 */
    public Map<String, Object> getByIdRow(String id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT * FROM ecos_pipeline_udf WHERE id = ?", id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public Map<String, Object> list(int page, int pageSize, String category, String language) {
        StringBuilder sql = new StringBuilder("SELECT * FROM ecos_pipeline_udf WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (category != null && !category.isEmpty()) {
            sql.append(" AND category = ?");
            params.add(category);
        }
        if (language != null && !language.isEmpty()) {
            sql.append(" AND language = ?");
            params.add(language);
        }

        int total = jdbcTemplate.queryForObject("SELECT COUNT(*)" + sql.substring(sql.indexOf("FROM") - 1), Integer.class, params.toArray());
        int offset = Math.max(0, page - 1) * pageSize;

        sql.append(" ORDER BY updated_at DESC LIMIT ? OFFSET ?");
        params.add(pageSize);
        params.add(offset);

        List<Map<String, Object>> list = jdbcTemplate.queryForList(sql.toString(), params.toArray());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("page", page);
        result.put("pageSize", pageSize);
        result.put("list", list);
        return result;
    }
}
