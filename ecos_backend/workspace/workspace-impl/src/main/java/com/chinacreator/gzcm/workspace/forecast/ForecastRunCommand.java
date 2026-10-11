package com.chinacreator.gzcm.workspace.forecast;

import com.chinacreator.gzcm.workspace.scenario.RunMode;

/**
 * 规范化预测 run 命令（校验后的入参落点，immutable）。
 *
 * <p>六要素：{@code caliberId@caliberVersion, asOfTime, snapshotId(输入快照), formulaVersion}；
 * 幂等：{@code idempotencyKey + contentHash}；正交列：{@link RunMode}（禁与 run_type 挤同列）。</p>
 */
public final class ForecastRunCommand {

    private final String scenarioId;
    private final String runType;
    private final String caliberId;
    private final String caliberVersion;
    private final String asOfTime;
    private final String snapshotId;
    private final String formulaVersion;
    private final String idempotencyKey;
    private final String contentHash;
    private final RunMode runMode;
    private final String baselineRunId;
    private final String reusedFromRunId;

    public ForecastRunCommand(String scenarioId, String runType, String caliberId, String caliberVersion,
                              String asOfTime, String snapshotId, String formulaVersion,
                              String idempotencyKey, String contentHash, RunMode runMode, String baselineRunId) {
        this(scenarioId, runType, caliberId, caliberVersion, asOfTime, snapshotId, formulaVersion,
                idempotencyKey, contentHash, runMode, baselineRunId, null);
    }

    public ForecastRunCommand(String scenarioId, String runType, String caliberId, String caliberVersion,
                              String asOfTime, String snapshotId, String formulaVersion,
                              String idempotencyKey, String contentHash, RunMode runMode,
                              String baselineRunId, String reusedFromRunId) {
        this.scenarioId = scenarioId;
        this.runType = runType;
        this.caliberId = caliberId;
        this.caliberVersion = caliberVersion;
        this.asOfTime = asOfTime;
        this.snapshotId = snapshotId;
        this.formulaVersion = formulaVersion;
        this.idempotencyKey = idempotencyKey;
        this.contentHash = contentHash;
        this.runMode = runMode;
        this.baselineRunId = baselineRunId;
        this.reusedFromRunId = reusedFromRunId;
    }

    public String getScenarioId() {
        return scenarioId;
    }

    public String getRunType() {
        return runType;
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

    public RunMode getRunMode() {
        return runMode;
    }

    public String getBaselineRunId() {
        return baselineRunId;
    }

    public String getReusedFromRunId() {
        return reusedFromRunId;
    }

    /**
     * 以指定源 run 为回链派生新命令（重试/复制面）：
     * 复用六要素与内容指纹，换新 idempotencyKey，并以 baselineRunId/reusedFromRunId 回链源 run。
     */
    public ForecastRunCommand deriveFrom(String newIdempotencyKey, String sourceRunId) {
        return new ForecastRunCommand(scenarioId, runType, caliberId, caliberVersion, asOfTime,
                snapshotId, formulaVersion, newIdempotencyKey, contentHash, runMode,
                sourceRunId, sourceRunId);
    }

    /** 内容指纹一致性判定：同 key 且同 hash 才允许复用（禁对不一致载荷静默复用）。 */
    public boolean sameContent(String otherContentHash) {
        return contentHash != null && contentHash.equals(otherContentHash);
    }
}
