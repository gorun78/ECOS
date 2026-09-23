package com.chinacreator.gzcm.engine.kb.nav.model;

import java.util.List;

/**
 * 知识导航·子树统计 VO（{@code GET /api/v1/knowledge/nav/categories/{id}/stats}）。
 *
 * <p>统计口径：以 scope=category 关联（含子树展开）的文章数与同 scope 下命中的标签数。
 * {@code tagNames} 列举子树命中文章使用过的标签（去重，限量 50）。</p>
 */
public class NavNodeStatsVO {

    private String nodeId;
    /** 固定 "category"（节点是目录） */
    private String scope;
    /** 子树文章数（直接 + 间接关联） */
    private Integer articleCount;
    /** 子树下被挂过的标签数 */
    private Integer tagCount;
    /** 子树下命中标签名（去重，限量 50） */
    private List<String> tagNames;

    public NavNodeStatsVO() {
    }

    public String getNodeId() { return nodeId; }
    public void setNodeId(String nodeId) { this.nodeId = nodeId; }

    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }

    public Integer getArticleCount() { return articleCount; }
    public void setArticleCount(Integer articleCount) { this.articleCount = articleCount; }

    public Integer getTagCount() { return tagCount; }
    public void setTagCount(Integer tagCount) { this.tagCount = tagCount; }

    public List<String> getTagNames() { return tagNames; }
    public void setTagNames(List<String> tagNames) { this.tagNames = tagNames; }
}
