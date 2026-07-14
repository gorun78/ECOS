package com.chinacreator.gzcm.datanet.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 数据分类 — 树形分类目录。
 * <p>
 * 用于组织数据目录的分类树，支持多级层级结构。
 * 每个分类可关联数据资源（通过 CatalogItem.categoryPath）。
 *
 * @author DataBridge Datanet Team
 */
public class DataCategory {

    /** 分类 ID */
    private String categoryId;

    /** 分类名称 */
    private String categoryName;

    /** 父分类 ID（根分类为 0） */
    private String parentId;

    /** 层级路径（如 /01/0101/010101） */
    private String path;

    /** 层级深度（根=1） */
    private Integer level;

    /** 排序号 */
    private Integer sortOrder;

    /** 描述 */
    private String description;

    /** 子分类列表（树形展现用） */
    private List<DataCategory> children;

    /** 关联资源数（统计用） */
    private Integer resourceCount;

    /** 状态：ACTIVE/INACTIVE */
    private String status;

    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;

    // ===== Getters/Setters =====

    public String getCategoryId() { return categoryId; }
    public void setCategoryId(String categoryId) { this.categoryId = categoryId; }

    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

    public String getParentId() { return parentId; }
    public void setParentId(String parentId) { this.parentId = parentId; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public Integer getLevel() { return level; }
    public void setLevel(Integer level) { this.level = level; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public List<DataCategory> getChildren() { return children; }
    public void setChildren(List<DataCategory> children) { this.children = children; }

    public Integer getResourceCount() { return resourceCount; }
    public void setResourceCount(Integer resourceCount) { this.resourceCount = resourceCount; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCreateBy() { return createBy; }
    public void setCreateBy(String createBy) { this.createBy = createBy; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public String getUpdateBy() { return updateBy; }
    public void setUpdateBy(String updateBy) { this.updateBy = updateBy; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
