package com.chinacreator.gzcm.engine.kb.model;

public class KnowledgeEmbedding {

    private String id;
    private String articleId;
    private String chunkText;
    private int tokenCount;
    private String model;
    private long createdAt;
    /** 分片序号（chunk_index，B4 向量写入用） */
    private Integer chunkIndex;
    /** 双写过渡：JSONB 列 embedding 的字符串值（形如 [0.1,0.2,...]，B4 向量写入用） */
    private String embeddingJson;
    /** pgvector 列 embedding_vec 的字面量（形如 [0.1,0.2,...]，B4 向量写入用） */
    private String embeddingVec;

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
    public Integer getChunkIndex() { return chunkIndex; }
    public void setChunkIndex(Integer chunkIndex) { this.chunkIndex = chunkIndex; }
    public String getEmbeddingJson() { return embeddingJson; }
    public void setEmbeddingJson(String embeddingJson) { this.embeddingJson = embeddingJson; }
    public String getEmbeddingVec() { return embeddingVec; }
    public void setEmbeddingVec(String embeddingVec) { this.embeddingVec = embeddingVec; }
}