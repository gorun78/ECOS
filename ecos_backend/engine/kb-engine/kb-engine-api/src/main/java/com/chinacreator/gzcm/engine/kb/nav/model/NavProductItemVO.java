package com.chinacreator.gzcm.engine.kb.nav.model;

import java.time.Instant;
import java.util.List;

/**
 * 知识导航·资产列表项 VO（{@code GET /api/v1/knowledge/nav/products} 行）。
 *
 * <p>轻量摘要（不含 {@code content} 大字段）；与 {@code knowledge_article} 列对齐，
 * 并冗余关联维度：{@code matchedCategoryIds / matchedTags}。</p>
 */
public class NavProductItemVO {

    private String id;
    private String title;
    private String source;
    private String domain;
    private String category;
    private String status;
    private Instant updatedAt;
    /** 命中的目录 ID 列表（已按子树展开） */
    private List<String> matchedCategoryIds;
    /** 命中的标签名列表（按 tags IN 匹配） */
    private List<String> matchedTags;

    public NavProductItemVO() {
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public List<String> getMatchedCategoryIds() { return matchedCategoryIds; }
    public void setMatchedCategoryIds(List<String> matchedCategoryIds) { this.matchedCategoryIds = matchedCategoryIds; }

    public List<String> getMatchedTags() { return matchedTags; }
    public void setMatchedTags(List<String> matchedTags) { this.matchedTags = matchedTags; }
}
