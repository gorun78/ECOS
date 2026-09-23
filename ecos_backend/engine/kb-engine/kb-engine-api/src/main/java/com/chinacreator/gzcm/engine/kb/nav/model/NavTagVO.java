package com.chinacreator.gzcm.engine.kb.nav.model;

import java.time.Instant;

/**
 * 知识导航·标签 VO（{@code GET /api/v1/knowledge/nav/tags} 行）。
 *
 * <p>字段口径与 {@code ecos_knowledge.kb_nav_tag} 对齐；{@code useCount} 由
 * {@code kb_nav_article_rel} 按 node_id=tag.id &amp; scope='tag' 计数。</p>
 */
public class NavTagVO {

    /** 标签 ID（UUID 36） */
    private String id;

    /** 多租户预留，默认 "default" */
    private String domain;

    /** 标签名（同 domain 下唯一） */
    private String tagName;

    /** 该标签当前被多少文章引用（0 = 未被使用） */
    private Integer useCount;

    private Instant createdAt;
    private Instant updatedAt;

    public NavTagVO() {
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }

    public String getTagName() { return tagName; }
    public void setTagName(String tagName) { this.tagName = tagName; }

    public Integer getUseCount() { return useCount; }
    public void setUseCount(Integer useCount) { this.useCount = useCount; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
