package com.chinacreator.gzcm.engine.kb.dto;

import java.util.List;

/**
 * 混合召回过滤面（详设 §7.3.2 附录 A 第 14 行 {@code filters} 载荷）。
 *
 * <p>两维过滤与旧 {@code POST /api/v1/knowledge/rag} 同源（{@code RagSearchRequest}）：
 * {@code categoryIds} 走知识导航目录子树（{@code kb_nav_category.path} 前缀），
 * {@code tags} 走标签名集合（{@code kb_nav_tag.tag_name}）。缺省/空 = 不加过滤（全量召回）。</p>
 */
public class KnowledgeRetrievalFilters {

    /** 知识导航目录 ID 集合（可空 = 全量）。 */
    private List<String> categoryIds;

    /** 知识导航标签名集合（可空 = 全量）。 */
    private List<String> tags;

    public List<String> getCategoryIds() { return categoryIds; }

    public void setCategoryIds(List<String> categoryIds) { this.categoryIds = categoryIds; }

    public List<String> getTags() { return tags; }

    public void setTags(List<String> tags) { this.tags = tags; }
}
