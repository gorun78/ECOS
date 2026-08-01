package com.chinacreator.gzcm.engine.kb.model;

import java.util.List;

/** 规则版本快照 */
public class RuleVersion {
    private String id;
    private String ruleId;
    private int versionNumber;
    private String snapshot;      // JSON快照
    private String changedBy;
    private long changedAt;
    private String changeNote;

    public RuleVersion() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getRuleId() { return ruleId; }
    public void setRuleId(String ruleId) { this.ruleId = ruleId; }
    public int getVersionNumber() { return versionNumber; }
    public void setVersionNumber(int versionNumber) { this.versionNumber = versionNumber; }
    public String getSnapshot() { return snapshot; }
    public void setSnapshot(String snapshot) { this.snapshot = snapshot; }
    public String getChangedBy() { return changedBy; }
    public void setChangedBy(String changedBy) { this.changedBy = changedBy; }
    public long getChangedAt() { return changedAt; }
    public void setChangedAt(long changedAt) { this.changedAt = changedAt; }
    public String getChangeNote() { return changeNote; }
    public void setChangeNote(String changeNote) { this.changeNote = changeNote; }
}
