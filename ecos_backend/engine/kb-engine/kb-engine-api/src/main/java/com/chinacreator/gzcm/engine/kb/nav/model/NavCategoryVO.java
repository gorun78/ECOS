package com.chinacreator.gzcm.engine.kb.nav.model;

import java.time.Instant;

/**
 * 知识导航·目录节点 VO — 用于 {@code GET /api/v1/knowledge/nav/categories} 子树返回。
 *
 * <p>字段口径（与 {@code ecos_knowledge.kb_nav_category} 表对齐）：
 * <ul>
 *   <li>{@code id} — UUID 主键</li>
 *   <li>{@code parentId} / {@code parentName} — 父目录 ID 与名称（根目录为 null）</li>
 *   <li>{@code name} / {@code path} — 名称 + 从根拼接的路径（冗余，避免 JOIN）</li>
 *   <li>{@code level} — 1/2/3（1 = 域下一级目录，3 = 叶子极限）</li>
 *   <li>{@code childCount} — 直接子目录数（一级目录粒度）</li>
 *   <li>{@code articleCount} — 该节点（含子树）下挂文章数</li>
 * </ul>
 * </p>
 */
public class NavCategoryVO {

    private String id;
    private String domain;
    private String parentId;
    private String parentName;
    private String name;
    private String path;
    private Integer level;
    private Integer sortOrder;
    private Integer childCount;
    private Integer articleCount;
    private Instant createdAt;
    private Instant updatedAt;

    public NavCategoryVO() {
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }

    public String getParentId() { return parentId; }
    public void setParentId(String parentId) { this.parentId = parentId; }

    public String getParentName() { return parentName; }
    public void setParentName(String parentName) { this.parentName = parentName; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public Integer getLevel() { return level; }
    public void setLevel(Integer level) { this.level = level; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public Integer getChildCount() { return childCount; }
    public void setChildCount(Integer childCount) { this.childCount = childCount; }

    public Integer getArticleCount() { return articleCount; }
    public void setArticleCount(Integer articleCount) { this.articleCount = articleCount; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
