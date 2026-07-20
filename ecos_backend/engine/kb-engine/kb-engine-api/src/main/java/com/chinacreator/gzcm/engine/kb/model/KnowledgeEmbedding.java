package com.chinacreator.gzcm.engine.kb.model;

public class KnowledgeEmbedding {

    private String id;
    private String articleId;
    private String chunkText;
    private int tokenCount;
    private String model;
    private long createdAt;

    public KnowledgeEmbedding() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getArticleId() { return articleId; }
    public void setArticleId(String articleId) { this.articleId = articleId; }
    public String getChunkText() { return chunkText; }
    public void setChunkText(String chunkText) { this.chunkText = chunkText; }
    public int getTokenCount() { return tokenCount; }
    public void setTokenCount(int tokenCount) { this.tokenCount = tokenCount; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}