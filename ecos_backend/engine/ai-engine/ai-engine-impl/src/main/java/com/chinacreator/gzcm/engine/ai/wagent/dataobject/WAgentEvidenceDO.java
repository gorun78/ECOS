package com.chinacreator.gzcm.engine.ai.wagent.dataobject;

/**
 * V240 {@code ecos_ai.ecos_wagent_evidence} 运行证据 DO（引用型，无内容字段——§7.1/§6.3）。
 * <p>只存引用与哈希（{@code snapshot_ref_json}/{@code locator_text}/{@code content_hash}），
 * 禁业务明细副本或正文。{@code source_engine} 值域见
 * {@link com.chinacreator.gzcm.engine.ai.wagent.WAgentEngineCatalog#contractEngineCodesForDdlCheck()}
 * （data/ontology/kb/cognitive）。</p>
 * <p>与认知域 {@code ecos_cognitive.ecos_cognitive_evidence}（V127）互不互写（ST09）。
 * {@code classify_level} 密级列由 security-engine CLS 过滤联动，本层不写策略。
 * 写方唯一 = ai-engine.wagent，全链只读消费。</p>
 */
public class WAgentEvidenceDO extends WAgentBaseDO {

    private String id;
    private String evidenceType;
    private String sourceEngine;
    private String sourceObjectType;
    private String sourceObjectId;
    private String sourceVersion;
    private String snapshotRefJson;
    private String locatorText;
    private String contentHash;
    private String classifyLevel;
    private Integer isValid;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getEvidenceType() { return evidenceType; }
    public void setEvidenceType(String evidenceType) { this.evidenceType = evidenceType; }

    public String getSourceEngine() { return sourceEngine; }
    public void setSourceEngine(String sourceEngine) { this.sourceEngine = sourceEngine; }

    public String getSourceObjectType() { return sourceObjectType; }
    public void setSourceObjectType(String sourceObjectType) { this.sourceObjectType = sourceObjectType; }

    public String getSourceObjectId() { return sourceObjectId; }
    public void setSourceObjectId(String sourceObjectId) { this.sourceObjectId = sourceObjectId; }

    public String getSourceVersion() { return sourceVersion; }
    public void setSourceVersion(String sourceVersion) { this.sourceVersion = sourceVersion; }

    public String getSnapshotRefJson() { return snapshotRefJson; }
    public void setSnapshotRefJson(String snapshotRefJson) { this.snapshotRefJson = snapshotRefJson; }

    public String getLocatorText() { return locatorText; }
    public void setLocatorText(String locatorText) { this.locatorText = locatorText; }

    public String getContentHash() { return contentHash; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }

    public String getClassifyLevel() { return classifyLevel; }
    public void setClassifyLevel(String classifyLevel) { this.classifyLevel = classifyLevel; }

    public Integer getIsValid() { return isValid; }
    public void setIsValid(Integer isValid) { this.isValid = isValid; }
}
