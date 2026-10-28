package com.chinacreator.gzcm.engine.kb.dto;

import java.util.List;

/**
 * 单条召回命中（附录 A 第 14 行 {@code hits[]}：{@code {id,score,sourceId,chunk,citations}}）。
 *
 * <p>字段值只搬取数腿的真实读数（{@code KnowledgeEmbedding} 命中行），不补默认值：
 * 文章 {@code kb_nav_article} 侧零命中时 {@code sourceId} 保持空串，而不是伪造文章号。</p>
 */
public class KnowledgeRetrievalHitVO {

    /** 命中 id（= 向量块 id {@code knowledge_embedding.id}）。 */
    private String id;

    /** 相似度读数（向量腿为余弦读数、关键词回退为基线 0.5；原样透传数值类型）。 */
    private Object score;

    /** 来源文章 id（{@code knowledge_embedding.document_id}）。 */
    private String sourceId;

    /** 命中正文块。 */
    private String chunk;

    /**
     * 引用锚（契约列）。本引擎<b>无引用结构载体</b>（零 {@code forecastRunId/caliberId/version/asOfTime}
     * 四要素来源，那是 AI 域的引用块契约），故恒为空表；消费方须先读
     * {@link KnowledgeRetrievalSearchResultVO#isCitationsApplicable()}，不得把空表读成"无引用需求"。
     */
    private List<String> citations;

    public String getId() { return id; }

    public void setId(String id) { this.id = id; }

    public Object getScore() { return score; }

    public void setScore(Object score) { this.score = score; }

    public String getSourceId() { return sourceId; }

    public void setSourceId(String sourceId) { this.sourceId = sourceId; }

    public String getChunk() { return chunk; }

    public void setChunk(String chunk) { this.chunk = chunk; }

    public List<String> getCitations() { return citations; }

    public void setCitations(List<String> citations) { this.citations = citations; }
}
