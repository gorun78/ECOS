package com.chinacreator.gzcm.engine.data.dao;

import com.chinacreator.gzcm.common.data.model.DataCategory;
import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

/**
 * StubCategoryService 的 JDBC 数据访问层。
 * <p>表: ecos_data.td_data_category。原始 SQL 均在此执行。</p>
 */
@Repository
public class StubCategoryDao extends JdbcTemplateDaoSupport {

    private static final String TABLE = "ecos_data.td_data_category";

    /** 行映射器：将 ResultSet 映射为 DataCategory */
    private final RowMapper<DataCategory> rowMapper = (rs, rowNum) -> {
        DataCategory c = new DataCategory();
        c.setCategoryId(rs.getString("category_id"));
        c.setCategoryName(rs.getString("category_name"));
        c.setParentId(rs.getString("parent_id"));
        c.setPath(rs.getString("path"));
        c.setLevel(rs.getInt("level"));
        c.setSortOrder(rs.getInt("sort_order"));
        c.setDescription(rs.getString("description"));
        c.setStatus(rs.getString("status"));
        c.setCreateBy(rs.getString("create_by"));
        Timestamp ct = rs.getTimestamp("create_time");
        if (ct != null) c.setCreateTime(ct.toLocalDateTime());
        c.setUpdateBy(rs.getString("update_by"));
        Timestamp ut = rs.getTimestamp("update_time");
        if (ut != null) c.setUpdateTime(ut.toLocalDateTime());
        return c;
    };

    public void insert(DataCategory category) {
        jdbcTemplate.update("""
            INSERT INTO ecos_data.td_data_category
                (category_id, category_name, parent_id, path, level, sort_order, description, status, create_by, create_time, update_time)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
            """,
                category.getCategoryId(),
                category.getCategoryName(),
                category.getParentId(),
                category.getPath(),
                category.getLevel(),
                category.getSortOrder() == null ? 0 : category.getSortOrder(),
                category.getDescription(),
                category.getStatus(),
                category.getCreateBy());
    }

    public int update(DataCategory category) {
        return jdbcTemplate.update("""
            UPDATE ecos_data.td_data_category SET
                category_name = COALESCE(?, category_name),
                parent_id     = COALESCE(?, parent_id),
                path          = COALESCE(?, path),
                level         = COALESCE(?, level),
                sort_order    = COALESCE(?, sort_order),
                description   = COALESCE(?, description),
                status        = COALESCE(?, status),
                update_by     = COALESCE(?, update_by),
                update_time   = NOW()
            WHERE category_id = ?
            """,
                category.getCategoryName(),
                category.getParentId(),
                category.getPath(),
                category.getLevel(),
                category.getSortOrder(),
                category.getDescription(),
                category.getStatus(),
                category.getUpdateBy(),
                category.getCategoryId());
    }

    public DataCategory getById(String categoryId) {
        if (categoryId == null || categoryId.isBlank()) return null;
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT * FROM " + TABLE + " WHERE category_id = ?",
                    rowMapper, categoryId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public List<DataCategory> getTree() {
        return jdbcTemplate.query(
                "SELECT * FROM " + TABLE + " ORDER BY sort_order, category_name",
                rowMapper);
    }

    public List<DataCategory> getChildren(String parentId) {
        return jdbcTemplate.query(
                "SELECT * FROM " + TABLE + " WHERE parent_id = ? ORDER BY sort_order, category_name",
                rowMapper, parentId);
    }

    public int delete(String categoryId) {
        return jdbcTemplate.update(
                "DELETE FROM " + TABLE + " WHERE category_id = ?", categoryId);
    }

    public List<Map<String, Object>> statusStats() {
        return jdbcTemplate.queryForList("""
            SELECT status, COUNT(*) AS cnt
            FROM ecos_data.td_data_category
            GROUP BY status
            ORDER BY status
            """);
    }
}
