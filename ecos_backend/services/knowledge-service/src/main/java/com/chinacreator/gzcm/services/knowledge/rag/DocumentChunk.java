package com.chinacreator.gzcm.services.knowledge.rag;

public class DocumentChunk {
    private String id;
    private String documentId;
    private String content;
    private double score;
    private int chunkIndex;

    public DocumentChunk() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getDocumentId() { return documentId; }
    public void setDocumentId(String documentId) { this.documentId = documentId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public double getScore() { return score; }
    public void setScore(double score) { this.score = score; }
    public int getChunkIndex() { return chunkIndex; }
    public void setChunkIndex(int chunkIndex) { this.chunkIndex = chunkIndex; }
}
