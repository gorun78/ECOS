package com.chinacreator.gzcm.engine.data.service;

import com.chinacreator.gzcm.common.data.model.DataCategory;
import com.chinacreator.gzcm.engine.data.CategoryService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * CategoryService 真实实现（PMO-B6）。
 * <p>
 * 基于 {@link JdbcTemplate} 操作 {@code ecos_data.td_data_category} 表，实现分类树的真实 CRUD。
 * 类名保持 {@code StubCategoryService} 以避免 Bean 名称冲突，但行为已从 stub 升级为真实实现。
 *
 * <h3>表结构</h3>
 * <pre>
 * CREATE TABLE IF NOT EXISTS ecos_data.td_data_category (
 *     category_id   VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *     category_name VARCHAR(200) NOT NULL,
 *     parent_id     VARCHAR(36),
 *     path          VARCHAR(500),
 *     level         INT DEFAULT 1,
 *     sort_order    INT DEFAULT 0,
 *     description   TEXT,
 *     status        VARCHAR(20) DEFAULT 'ACTIVE',
 *     tenant_id     VARCHAR(64),
 *     create_by     VARCHAR(64),
 *     create_time   TIMESTAMP DEFAULT NOW(),
 *     update_by     VARCHAR(64),
 *     update_time   TIMESTAMP DEFAULT NOW()
 * );
 * </pre>
 *
 * @author DataBridge Datanet Team
 */
@Service
public class StubCategoryService implements CategoryService {

    private static final Logger log = LoggerFactory.getLogger(StubCategoryService.class);

    private static final String TABLE = "ecos_data.td_data_category";

    private final JdbcTemplate jdbc;

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

    public StubCategoryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    public void init() {
        ensureSchema();
    }

    /** 幂等建表（列与已有迁移 V58 保持一致，只加不删） */
    private void ensureSchema() {
        try {
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS ecos_data.td_data_category (
                    category_id   VARCHAR(36) PRIMARY KEY,
                    category_name VARCHAR(200) NOT NULL,
                    parent_id     VARCHAR(36),
                    path          VARCHAR(500),
                    level         INT DEFAULT 1,
                    sort_order    INT DEFAULT 0,
                    description   TEXT,
                    status        VARCHAR(20) DEFAULT 'ACTIVE',
                    tenant_id     VARCHAR(64),
                    create_by     VARCHAR(64),
                    create_time   TIMESTAMP DEFAULT NOW(),
                    update_by     VARCHAR(64),
                    update_time   TIMESTAMP DEFAULT NOW()
                )
                """);
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_category_parent ON ecos_data.td_data_category(parent_id)");
            log.info("CategoryService table ready: {}", TABLE);
        } catch (Exception e) {
            log.warn("CategoryService table init warning: {}", e.getMessage());
        }
    }

    // ===== CRUD =====

    @Override
    public DataCategory create(DataCategory category) {
        String id = category.getCategoryId();
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
            category.setCategoryId(id);
        }
        // 计算层级与路径
        int level = computeLevel(category.getParentId());
        category.setLevel(level);
        String path = computePath(category.getParentId(), id);
        category.setPath(path);
        if (category.getStatus() == null || category.getStatus().isBlank()) {
            category.setStatus("ACTIVE");
        }
        String sql = """
            INSERT INTO ecos_data.td_data_category
                (category_id, category_name, parent_id, path, level, sort_order, description, status, create_by, create_time, update_time)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
            """;
        jdbc.update(sql,
                id,
                category.getCategoryName(),
                category.getParentId(),
                path,
                level,
                category.getSortOrder() == null ? 0 : category.getSortOrder(),
                category.getDescription(),
                category.getStatus(),
                category.getCreateBy());
        log.info("Category created: id={}, name={}", id, category.getCategoryName());
        return getById(id);
    }

    @Override
    public DataCategory update(DataCategory category) {
        String id = category.getCategoryId();
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("categoryId is required for update");
        }
        // 重新计算层级与路径（父分类可能变更）
        int level = computeLevel(category.getParentId());
        category.setLevel(level);
        String path = computePath(category.getParentId(), id);
        category.setPath(path);
        String sql = """
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
            """;
        int rows = jdbc.update(sql,
                category.getCategoryName(),
                category.getParentId(),
                path,
                level,
                category.getSortOrder(),
                category.getDescription(),
                category.getStatus(),
                category.getUpdateBy(),
                id);
        if (rows == 0) {
            log.warn("Category update affected 0 rows: id={}", id);
            return null;
        }
        log.info("Category updated: id={}", id);
        return getById(id);
    }

    @Override
    public DataCategory getById(String categoryId) {
        if (categoryId == null || categoryId.isBlank()) return null;
        try {
            return jdbc.queryForObject(
                    "SELECT * FROM ecos_data.td_data_category WHERE category_id = ?",
                    rowMapper, categoryId);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return null;
        }
    }

    @Override
    public List<DataCategory> getTree() {
        // 查全量后组装树结构
        List<DataCategory> all;
        try {
            all = jdbc.query(
                    "SELECT * FROM ecos_data.td_data_category ORDER BY sort_order, category_name",
                    rowMapper);
        } catch (Exception e) {
            log.warn("getTree query failed: {}", e.getMessage());
            return List.of();
        }
        return buildTree(all);
    }

    @Override
    public List<DataCategory> getChildren(String parentId) {
        if (parentId == null || parentId.isBlank()) return List.of();
        try {
            return jdbc.query(
                    "SELECT * FROM ecos_data.td_data_category WHERE parent_id = ? ORDER BY sort_order, category_name",
                    rowMapper, parentId);
        } catch (Exception e) {
            log.warn("getChildren query failed: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public void remove(String categoryId) {
        if (categoryId == null || categoryId.isBlank()) return;
        // 递归删除子分类
        List<DataCategory> children = getChildren(categoryId);
        for (DataCategory child : children) {
            remove(child.getCategoryId());
        }
        int rows = jdbc.update(
                "DELETE FROM ecos_data.td_data_category WHERE category_id = ?", categoryId);
        log.info("Category removed: id={}, rows={}", categoryId, rows);
    }

    @Override
    public List<DataCategory> getCategoryStats() {
        // 统计每个 status 的数量，返回带 resourceCount 的分类列表
        String sql = """
            SELECT status, COUNT(*) AS cnt
            FROM ecos_data.td_data_category
            GROUP BY status
            ORDER BY status
            """;
        List<Map<String, Object>> rows;
        try {
            rows = jdbc.queryForList(sql);
        } catch (Exception e) {
            log.warn("getCategoryStats query failed: {}", e.getMessage());
            return List.of();
        }
        List<DataCategory> stats = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            DataCategory c = new DataCategory();
            c.setStatus((String) row.get("status"));
            Number cnt = (Number) row.get("cnt");
            c.setResourceCount(cnt == null ? 0 : cnt.intValue());
            stats.add(c);
        }
        return stats;
    }

    // ===== 辅助方法 =====

    /** 根据父分类 ID 计算层级（根=1） */
    private int computeLevel(String parentId) {
        if (parentId == null || parentId.isBlank()) return 1;
        DataCategory parent = getById(parentId);
        if (parent == null || parent.getLevel() == null) return 1;
        return parent.getLevel() + 1;
    }

    /** 根据父分类 ID 与自身 ID 计算层级路径 */
    private String computePath(String parentId, String selfId) {
        if (parentId == null || parentId.isBlank()) return "/" + selfId;
        DataCategory parent = getById(parentId);
        if (parent == null || parent.getPath() == null) return "/" + selfId;
        return parent.getPath() + "/" + selfId;
    }

    /** 将扁平列表组装为树结构（parent_id 关联） */
    private List<DataCategory> buildTree(List<DataCategory> all) {
        Map<String, DataCategory> idMap = new LinkedHashMap<>();
        for (DataCategory c : all) {
            idMap.put(c.getCategoryId(), c);
        }
        List<DataCategory> roots = new ArrayList<>();
        for (DataCategory c : all) {
            String pid = c.getParentId();
            if (pid == null || pid.isBlank() || pid.equals("0") || !idMap.containsKey(pid)) {
                roots.add(c);
            } else {
                DataCategory parent = idMap.get(pid);
                if (parent.getChildren() == null) {
                    parent.setChildren(new ArrayList<>());
                }
                parent.getChildren().add(c);
            }
        }
        return roots;
    }
}
