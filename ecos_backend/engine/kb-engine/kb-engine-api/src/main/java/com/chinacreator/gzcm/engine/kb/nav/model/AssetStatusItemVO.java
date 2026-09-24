package com.chinacreator.gzcm.engine.kb.nav.model;

/**
 * 资产状态项 VO — 描述单个知识资产的图谱/向量双写状态。
 *
 * <p>用于 {@code GET /api/v1/knowledge/assets/status} 返回体
 * {@link AssetStatusVO} 的列表项（F10 双引擎批查）。
 *
 * @since PMO-D 2026-09-24
 */
public class AssetStatusItemVO {

    /** 知识资产 ID（knowledge_article.id） */
    private String articleId;
    /** 是否已入 Neo4j / graph_node 图 */
    private boolean graph;
    /** 是否已入 pgvector 向量索引（is_deleted=0 且 pgvector 可用时查） */
    private boolean vector;

    public AssetStatusItemVO() {
    }

    public String getArticleId() { return articleId; }
    public void setArticleId(String articleId) { this.articleId = articleId; }

    public boolean isGraph() { return graph; }
    public void setGraph(boolean graph) { this.graph = graph; }

    public boolean isVector() { return vector; }
    public void setVector(boolean vector) { this.vector = vector; }
}
