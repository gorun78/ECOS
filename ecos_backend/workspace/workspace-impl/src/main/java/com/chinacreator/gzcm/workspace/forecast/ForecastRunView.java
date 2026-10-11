package com.chinacreator.gzcm.workspace.forecast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 预测 run 视图（编排面对外返回体，final-class + 构造器 + getter，无 Lombok，与 {@code ForecastScopeVO} 同风）。
 *
 * <p>承载六要素落位结果与状态/幂等回链字段，值均由上游真值映射而来，编排面不合成数值。</p>
 */
public final class ForecastRunView {

    private final String forecastRunId;
    private final String scenarioId;
    private final String runType;
    private final ForecastRunStatus status;
    private final String caliberId;
    private final String caliberVersion;
    private final String asOfTime;
    private final String snapshotId;
    private final String formulaVersion;
    private final String idempotencyKey;
    private final String contentHash;
    private final String baselineRunId;
    private final String reusedFromRunId;
    private final boolean degraded;
    private final List<String> evidenceRefs;
    private final QuantileTriple quantile;
    /** H-6 异步派发回链：runtime-task 真实 taskId（submitTask 返回；同步路径为 null，V277 run 表 task_id 列落位）。 */
    private final String taskId;

    private ForecastRunView(Builder b) {
        this.forecastRunId = b.forecastRunId;
        this.scenarioId = b.scenarioId;
        this.runType = b.runType;
        this.status = b.status;
        this.caliberId = b.caliberId;
        this.caliberVersion = b.caliberVersion;
        this.asOfTime = b.asOfTime;
        this.snapshotId = b.snapshotId;
        this.formulaVersion = b.formulaVersion;
        this.idempotencyKey = b.idempotencyKey;
        this.contentHash = b.contentHash;
        this.baselineRunId = b.baselineRunId;
        this.reusedFromRunId = b.reusedFromRunId;
        this.degraded = b.degraded;
        this.evidenceRefs = b.evidenceRefs == null
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(b.evidenceRefs));
        this.quantile = b.quantile;
        this.taskId = b.taskId;
    }

    public static Builder builder(String forecastRunId) {
        return new Builder(forecastRunId);
    }

    /** 幂等复用回链副本：仅改写 reusedFromRunId，其余要素原样透传（禁重算）。 */
    public ForecastRunView withReusedFromRunId(String reusedFromRunId) {
        return ForecastRunView.builder(forecastRunId)
                .scenarioId(scenarioId).runType(runType).status(status)
                .caliberId(caliberId).caliberVersion(caliberVersion).asOfTime(asOfTime)
                .snapshotId(snapshotId).formulaVersion(formulaVersion)
                .idempotencyKey(idempotencyKey).contentHash(contentHash)
                .baselineRunId(baselineRunId).reusedFromRunId(reusedFromRunId)
                .degraded(degraded).evidenceRefs(evidenceRefs).quantile(quantile)
                .taskId(taskId)
                .build();
    }

    /** H-6 异步回链副本：仅改写 taskId（真实 taskId 来自 ITaskManagementService.submitTask，禁自造），其余原样透传。 */
    public ForecastRunView withTaskId(String taskId) {
        return ForecastRunView.builder(forecastRunId)
                .scenarioId(scenarioId).runType(runType).status(status)
                .caliberId(caliberId).caliberVersion(caliberVersion).asOfTime(asOfTime)
                .snapshotId(snapshotId).formulaVersion(formulaVersion)
                .idempotencyKey(idempotencyKey).contentHash(contentHash)
                .baselineRunId(baselineRunId).reusedFromRunId(reusedFromRunId)
                .degraded(degraded).evidenceRefs(evidenceRefs).quantile(quantile)
                .taskId(taskId)
                .build();
    }

    public String getForecastRunId() {
        return forecastRunId;
    }

    public String getScenarioId() {
        return scenarioId;
    }

    public String getRunType() {
        return runType;
    }

    public ForecastRunStatus getStatus() {
        return status;
    }

    public String getCaliberId() {
        return caliberId;
    }

    public String getCaliberVersion() {
        return caliberVersion;
    }

    public String getAsOfTime() {
        return asOfTime;
    }

    public String getSnapshotId() {
        return snapshotId;
    }

    public String getFormulaVersion() {
        return formulaVersion;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getContentHash() {
        return contentHash;
    }

    public String getBaselineRunId() {
        return baselineRunId;
    }

    public String getReusedFromRunId() {
        return reusedFromRunId;
    }

    public boolean isDegraded() {
        return degraded;
    }

    public List<String> getEvidenceRefs() {
        return evidenceRefs;
    }

    public QuantileTriple getQuantile() {
        return quantile;
    }

    public String getTaskId() {
        return taskId;
    }

    /** 链式构造器。 */
    public static final class Builder {
        private final String forecastRunId;
        private String scenarioId;
        private String runType;
        private ForecastRunStatus status;
        private String caliberId;
        private String caliberVersion;
        private String asOfTime;
        private String snapshotId;
        private String formulaVersion;
        private String idempotencyKey;
        private String contentHash;
        private String baselineRunId;
        private String reusedFromRunId;
        private boolean degraded;
        private List<String> evidenceRefs;
        private QuantileTriple quantile;
        private String taskId;

        private Builder(String forecastRunId) {
            this.forecastRunId = forecastRunId;
        }

        public Builder scenarioId(String v) { this.scenarioId = v; return this; }
        public Builder runType(String v) { this.runType = v; return this; }
        public Builder status(ForecastRunStatus v) { this.status = v; return this; }
        public Builder caliberId(String v) { this.caliberId = v; return this; }
        public Builder caliberVersion(String v) { this.caliberVersion = v; return this; }
        public Builder asOfTime(String v) { this.asOfTime = v; return this; }
        public Builder snapshotId(String v) { this.snapshotId = v; return this; }
        public Builder formulaVersion(String v) { this.formulaVersion = v; return this; }
        public Builder idempotencyKey(String v) { this.idempotencyKey = v; return this; }
        public Builder contentHash(String v) { this.contentHash = v; return this; }
        public Builder baselineRunId(String v) { this.baselineRunId = v; return this; }
        public Builder reusedFromRunId(String v) { this.reusedFromRunId = v; return this; }
        public Builder degraded(boolean v) { this.degraded = v; return this; }
        public Builder evidenceRefs(List<String> v) { this.evidenceRefs = v; return this; }
        public Builder quantile(QuantileTriple v) { this.quantile = v; return this; }
        public Builder taskId(String v) { this.taskId = v; return this; }

        public ForecastRunView build() {
            return new ForecastRunView(this);
        }
    }
}
