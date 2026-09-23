package com.chinacreator.gzcm.engine.kb.model.dto;

import java.util.List;

/**
 * RAG 检索请求 DTO（PMO-B T1 引入）— {@code POST /api/v1/knowledge/rag}。
 *
 * <p>兼容旧客户端：所有字段均为可选（缺省沿用默认值：topK=5, threshold=0.7）；
 * 新增 {@code categoryIds / tags}（知识导航，可空 = 全量检索，回归保证）。</p>
 *
 * <p>手写 getter/setter — api 模块保持与 lombok 无关，避免契约层引入传递依赖。</p>
 */
public class RagSearchRequest {

    /** 检索词（缺省 = ""） */
    private String query;

    /** Top-K 上限（缺省 5, 上限 50） */
    private Integer topK;

    /** 余弦相似度阈值（缺省 0.7 — 未用于硬筛，保留给前端展示） */
    private Double threshold;

    /** 知识导航目录 ID 集合（可空 / 空 = 全量检索，回归保证） */
    private List<String> categoryIds;

    /** 知识导航标签名集合（仅占位，本期不做下行 WHERE） */
    private List<String> tags;

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }

    public Integer getTopK() { return topK; }
    public void setTopK(Integer topK) { this.topK = topK; }

    public Double getThreshold() { return threshold; }
    public void setThreshold(Double threshold) { this.threshold = threshold; }

    public List<String> getCategoryIds() { return categoryIds; }
    public void setCategoryIds(List<String> categoryIds) { this.categoryIds = categoryIds; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
}
