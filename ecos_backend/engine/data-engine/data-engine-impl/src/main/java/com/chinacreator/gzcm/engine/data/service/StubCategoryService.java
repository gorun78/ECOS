package com.chinacreator.gzcm.engine.data.service;

import com.chinacreator.gzcm.common.data.model.DataCategory;
import com.chinacreator.gzcm.engine.data.CategoryService;
import com.chinacreator.gzcm.engine.data.dao.StubCategoryDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * CategoryService 真实实现（PMO-B6）。
 * <p>
 * 通过 {@link StubCategoryDao} 操作 {@code ecos_data.td_data_category} 表，实现分类树的真实 CRUD。
 * 类名保持 {@code StubCategoryService} 以避免 Bean 名称冲突，但行为已从 stub 升级为真实实现。
 *
 * <h3>表结构</h3>
 * 目标表为 {@code ecos_data.td_data_category}（分类树），其建表脚本、索引与审计列
 * 由 db/migration 单一来源维护（V58 初始列 + V162 补审计列/domain/version_no），
 * 运行时不再内嵌建表语句。字段语义参见对应迁移脚本。
 *
 * @author DataBridge Datanet Team
 */
@Service
public class StubCategoryService implements CategoryService {

    private static final Logger log = LoggerFactory.getLogger(StubCategoryService.class);

    private final StubCategoryDao categoryDao;

    public StubCategoryService(StubCategoryDao categoryDao) {
        this.categoryDao = categoryDao;
    }

    // H8-T1: ecos_data.td_data_category 建表 DDL（含索引 idx_category_parent 与审计列）
    // 收编至 db/migration（V58 + V162），运行时不再内嵌 DDL。

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
        categoryDao.insert(category);
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
        int rows = categoryDao.update(category);
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
        return categoryDao.getById(categoryId);
    }

    @Override
    public List<DataCategory> getTree() {
        // 查全量后组装树结构
        List<DataCategory> all;
        try {
            all = categoryDao.getTree();
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
            return categoryDao.getChildren(parentId);
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
        int rows = categoryDao.delete(categoryId);
        log.info("Category removed: id={}, rows={}", categoryId, rows);
    }

    @Override
    public List<DataCategory> getCategoryStats() {
        // 统计每个 status 的数量，返回带 resourceCount 的分类列表
        List<Map<String, Object>> rows;
        try {
            rows = categoryDao.statusStats();
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
