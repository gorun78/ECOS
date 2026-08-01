package com.chinacreator.gzcm.engine.kb.model;

/** 知识抽取来源记录 */
public class ExtractionSource {
    private String id;
    private String sourceType;    // MANUAL, KB_ARTICLE, DOCUMENT, KG_ENTITY, STRUCTURED
    private String sourceId;
    private String sourceTitle;
    private String sourceExcerpt;
    private long extractedAt;
    private String extractorVersion;

    public ExtractionSource() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getSourceId() { return sourceId; }
    public void setSourceId(String sourceId) { this.sourceId = sourceId; }
    public String getSourceTitle() { return sourceTitle; }
    public void setSourceTitle(String sourceTitle) { this.sourceTitle = sourceTitle; }
    public String getSourceExcerpt() { return sourceExcerpt; }
    public void setSourceExcerpt(String sourceExcerpt) { this.sourceExcerpt = sourceExcerpt; }
    public long getExtractedAt() { return extractedAt; }
    public void setExtractedAt(long extractedAt) { this.extractedAt = extractedAt; }
    public String getExtractorVersion() { return extractorVersion; }
    public void setExtractorVersion(String extractorVersion) { this.extractorVersion = extractorVersion; }
}
