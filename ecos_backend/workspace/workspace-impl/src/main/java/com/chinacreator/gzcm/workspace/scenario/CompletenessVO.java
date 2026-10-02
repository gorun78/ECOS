package com.chinacreator.gzcm.workspace.scenario;

import java.util.List;

/**
 * 完整度端点 N1 出参 VO（详细设计-07 D-2 N1 / F07-02）。
 * 字段名与 PRD-08 §2.3 的 coverage/requiredEdges/presentEdges/missingEdges/islands 对齐。
 */
public class CompletenessVO {

    private String scenarioId;
    private double coverage;
    private boolean coverageNull;      // coverage=null 时前端显示 "—"（禁绿环）
    private String verdict;            // OK | PARTIAL | ISOLATED | EMPTY_SCENARIO | NOT_APPLICABLE
    private List<RequiredEdgeVO> requiredEdges;
    private List<PresentEdgeVO> presentEdges;
    private List<MissingEdgeVO> missingEdges;
    private List<Island> islands;
    private List<String> probeErrors;

    public CompletenessVO(String scenarioId, Result r) {
        this.scenarioId = scenarioId;
        this.coverage = r.coverage() == null ? 0 : r.coverage();
        this.coverageNull = r.coverage() == null;
        this.verdict = r.verdict();
        this.requiredEdges = r.requiredEdges();
        this.presentEdges = r.presentEdges();
        this.missingEdges = r.missingEdges();
        this.islands = r.islands();
        this.probeErrors = r.probeErrors();
    }

    public String getScenarioId() { return scenarioId; }
    public void setScenarioId(String v) { this.scenarioId = v; }
    /** coverage 或 null（null = EMPTY_SCENARIO / NOT_APPLICABLE，前端显示 "—"）。 */
    public Double getCoverage() { return coverageNull ? null : coverage; }
    public void setCoverage(double v) { this.coverage = v; this.coverageNull = false; }
    public String getVerdict() { return verdict; }
    public void setVerdict(String v) { this.verdict = v; }
    public List<RequiredEdgeVO> getRequiredEdges() { return requiredEdges; }
    public void setRequiredEdges(List<RequiredEdgeVO> v) { this.requiredEdges = v; }
    public List<PresentEdgeVO> getPresentEdges() { return presentEdges; }
    public void setPresentEdges(List<PresentEdgeVO> v) { this.presentEdges = v; }
    public List<MissingEdgeVO> getMissingEdges() { return missingEdges; }
    public void setMissingEdges(List<MissingEdgeVO> v) { this.missingEdges = v; }
    public List<Island> getIslands() { return islands; }
    public void setIslands(List<Island> v) { this.islands = v; }
    public List<String> getProbeErrors() { return probeErrors; }
    public void setProbeErrors(List<String> v) { this.probeErrors = v; }

    // ── 嵌套结构 ─────────────────────────────────────────────

    /** 计算核心结果（record，供 service 与单测复用）。coverage 可空（null = EMPTY_SCENARIO / NOT_APPLICABLE）。 */
    public record Result(Double coverage, String verdict,
                         List<RequiredEdgeVO> requiredEdges, List<PresentEdgeVO> presentEdges,
                         List<MissingEdgeVO> missingEdges, List<Island> islands, List<String> probeErrors) {
        public static Result emptyScenario(List<RequiredEdgeVO> required, List<Island> islands) {
            return new Result(null, "EMPTY_SCENARIO", required, List.of(), List.of(), islands, List.of());
        }
        public static Result notApplicable(List<PresentEdgeVO> p, List<MissingEdgeVO> m, List<Island> islands) {
            return new Result(null, "NOT_APPLICABLE", List.of(), p, m, islands, List.of());
        }
    }

    public static class RequiredEdgeVO {
        private String type;
        public RequiredEdgeVO() {}
        public RequiredEdgeVO(String type) { this.type = type; }
        public String getType() { return type; }
        public void setType(String t) { this.type = t; }
    }

    public static class PresentEdgeVO {
        private String type;
        private String sourceId;
        private String targetId;
        private int contractCount;
        private String lastVerifiedAt;
        public PresentEdgeVO() {}
        public PresentEdgeVO(String type, String sourceId, String targetId, int contractCount, String lastVerifiedAt) {
            this.type = type; this.sourceId = sourceId; this.targetId = targetId;
            this.contractCount = contractCount; this.lastVerifiedAt = lastVerifiedAt;
        }
        public String getType() { return type; }
        public void setType(String v) { this.type = v; }
        public String getSourceId() { return sourceId; }
        public void setSourceId(String v) { this.sourceId = v; }
        public String getTargetId() { return targetId; }
        public void setTargetId(String v) { this.targetId = v; }
        public int getContractCount() { return contractCount; }
        public void setContractCount(int v) { this.contractCount = v; }
        public String getLastVerifiedAt() { return lastVerifiedAt; }
        public void setLastVerifiedAt(String v) { this.lastVerifiedAt = v; }
    }

    public static class MissingEdgeVO {
        private String type;
        private String labelKey;
        private String reason;
        public MissingEdgeVO() {}
        public MissingEdgeVO(String type, String labelKey, String reason) {
            this.type = type; this.labelKey = labelKey; this.reason = reason;
        }
        public String getType() { return type; }
        public void setType(String v) { this.type = v; }
        public String getLabelKey() { return labelKey; }
        public void setLabelKey(String v) { this.labelKey = v; }
        public String getReason() { return reason; }
        public void setReason(String v) { this.reason = v; }
    }

    /** 孤岛条目（C-5：bindingId / name / bindingType / reason）。 */
    public static class Island {
        private String bindingId;
        private String name;
        private String bindingType;
        private String reason;
        public Island() {}
        public Island(String bindingId, String name, String bindingType, String reason) {
            this.bindingId = bindingId; this.name = name; this.bindingType = bindingType; this.reason = reason;
        }
        public String getBindingId() { return bindingId; }
        public void setBindingId(String v) { this.bindingId = v; }
        public String getName() { return name; }
        public void setName(String v) { this.name = v; }
        public String getBindingType() { return bindingType; }
        public void setBindingType(String v) { this.bindingType = v; }
        public String getReason() { return reason; }
        public void setReason(String v) { this.reason = v; }
    }
}
