package com.chinacreator.gzcm.workspace.scenario;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 完整度计算唯一出参（卷07 F07-02 N1 契约，后端单源，前端只渲染不重算）。
 * <p>{@code coverage = |E_present ∩ E_req| / |E_req|}；空场景 → coverage=null + EMPTY_SCENARIO；
 * 无必需边 → coverage=null + NOT_APPLICABLE。</p>
 */
public final class CompletenessReport {

    /** 判定枚举。 */
    public enum Verdict {
        OK, PARTIAL, EMPTY_SCENARIO, NOT_APPLICABLE
    }

    private final String scenarioId;
    private final Double coverage;
    private final Verdict verdict;
    private final List<RequiredEdgeView> requiredEdges = new ArrayList<>();
    private final List<PresentEdgeView> presentEdges = new ArrayList<>();
    private final List<MissingEdgeView> missingEdges = new ArrayList<>();
    private final List<IslandView> islands = new ArrayList<>();
    private final List<String> probeErrors = new ArrayList<>();

    public CompletenessReport(String scenarioId, Double coverage, Verdict verdict) {
        this.scenarioId = scenarioId;
        this.coverage = coverage;
        this.verdict = verdict;
    }

    public String getScenarioId() {
        return scenarioId;
    }

    /** 覆盖率；null 表示不适用/空场景（前端显示 "—" 或 0%，禁显示 100%）。 */
    public Double getCoverage() {
        return coverage;
    }

    public Verdict getVerdict() {
        return verdict;
    }

    public List<RequiredEdgeView> getRequiredEdges() {
        return requiredEdges;
    }

    public List<PresentEdgeView> getPresentEdges() {
        return presentEdges;
    }

    public List<MissingEdgeView> getMissingEdges() {
        return missingEdges;
    }

    public List<IslandView> getIslands() {
        return islands;
    }

    public List<String> getProbeErrors() {
        return probeErrors;
    }

    // ── 嵌套视图 ────────────────────────────────────────

    /** 必需边声明视图。 */
    public static final class RequiredEdgeView {
        private final String type;
        private final String sourceType;
        private final String targetType;

        public RequiredEdgeView(String type, String sourceType, String targetType) {
            this.type = type;
            this.sourceType = sourceType;
            this.targetType = targetType;
        }

        public String getType() {
            return type;
        }

        public String getSourceType() {
            return sourceType;
        }

        public String getTargetType() {
            return targetType;
        }
    }

    /** 已具备边视图（含 hover 契约来源）。 */
    public static final class PresentEdgeView {
        private final String type;
        private final String sourceId;
        private final String targetId;
        private final long contractCount;
        private final Instant lastVerifiedAt;

        public PresentEdgeView(String type, String sourceId, String targetId, long contractCount, Instant lastVerifiedAt) {
            this.type = type;
            this.sourceId = sourceId;
            this.targetId = targetId;
            this.contractCount = contractCount;
            this.lastVerifiedAt = lastVerifiedAt;
        }

        public String getType() {
            return type;
        }

        public String getSourceId() {
            return sourceId;
        }

        public String getTargetId() {
            return targetId;
        }

        public long getContractCount() {
            return contractCount;
        }

        public Instant getLastVerifiedAt() {
            return lastVerifiedAt;
        }
    }

    /** 缺失边视图。 */
    public static final class MissingEdgeView {
        private final String type;
        private final String labelKey;
        private final String reason;

        public MissingEdgeView(String type, String labelKey, String reason) {
            this.type = type;
            this.labelKey = labelKey;
            this.reason = reason;
        }

        public String getType() {
            return type;
        }

        public String getLabelKey() {
            return labelKey;
        }

        public String getReason() {
            return reason;
        }
    }

    /** 孤岛绑定视图。 */
    public static final class IslandView {
        private final String bindingId;
        private final String name;
        private final String bindingType;
        private final String reason;

        public IslandView(String bindingId, String name, String bindingType, String reason) {
            this.bindingId = bindingId;
            this.name = name;
            this.bindingType = bindingType;
            this.reason = reason;
        }

        public String getBindingId() {
            return bindingId;
        }

        public String getName() {
            return name;
        }

        public String getBindingType() {
            return bindingType;
        }

        public String getReason() {
            return reason;
        }
    }
}
