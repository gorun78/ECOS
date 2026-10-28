package com.chinacreator.gzcm.engine.kb.dto;

/**
 * 混合召回请求（{@code POST /api/v1/knowledge/retrieval/search} 入参，档 B）。
 *
 * <p>与旧 {@code RagSearchRequest} 的差异仅在字段名：契约把检索词命名为 {@code question}、
 * 过滤维收进 {@code filters} 对象。两族请求体不共用一个 DTO，避免旧端点新增字段时
 * 悄悄扩大新端点的入参面。</p>
 */
public class KnowledgeRetrievalSearchRequest {

    /** 检索词（必填；空/空白由服务侧拒 400）。 */
    private String question;

    /** Top-K（可空 = 5；服务侧收敛到 [1,50]）。 */
    private Integer topK;

    /** 过滤维（可空 = 全量召回）。 */
    private KnowledgeRetrievalFilters filters;

    public String getQuestion() { return question; }

    public void setQuestion(String question) { this.question = question; }

    public Integer getTopK() { return topK; }

    public void setTopK(Integer topK) { this.topK = topK; }

    public KnowledgeRetrievalFilters getFilters() { return filters; }

    public void setFilters(KnowledgeRetrievalFilters filters) { this.filters = filters; }
}
