package com.chinacreator.gzcm.engine.ai.capability;

import java.math.BigDecimal;

/**
 * 异步能力任务状态回执（《补充设计 v1.0》§10.4 {@code getStatus(String taskId)} 的返回形态，
 * 类名按 §10.3 原文 {@code CapabilityStatus} 冻结）。
 *
 * <p>语义 = 台账行（V269 {@code ecos_ai_capability_task}）的只读投影：
 * 状态、质量三字段、输出引用、延迟、计量与告警。
 * 该 VO 与 {@link CapabilityResult} 的区别：结果结构用于<b>同步执行输出</b>，
 * 本 VO 用于<b>台账查询回显</b>（终态前 outputRef/quality 可为空，禁伪造）。</p>
 */
public class CapabilityStatus {

    private String taskId;
    private String capabilityId;
    private String status;
    private String inputRef;
    private String outputRef;
    private String modelRef;
    private String modelVersion;
    private BigDecimal qualityScore;
    private String qualityKind;
    private Boolean calibrated;
    private Long latencyMs;
    private String costUnit;
    private Boolean degraded;
    private String warningsText;
    private String traceId;
    private String runId;

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getCapabilityId() {
        return capabilityId;
    }

    public void setCapabilityId(String capabilityId) {
        this.capabilityId = capabilityId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getInputRef() {
        return inputRef;
    }

    public void setInputRef(String inputRef) {
        this.inputRef = inputRef;
    }

    public String getOutputRef() {
        return outputRef;
    }

    public void setOutputRef(String outputRef) {
        this.outputRef = outputRef;
    }

    public String getModelRef() {
        return modelRef;
    }

    public void setModelRef(String modelRef) {
        this.modelRef = modelRef;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public void setModelVersion(String modelVersion) {
        this.modelVersion = modelVersion;
    }

    public BigDecimal getQualityScore() {
        return qualityScore;
    }

    public void setQualityScore(BigDecimal qualityScore) {
        this.qualityScore = qualityScore;
    }

    public String getQualityKind() {
        return qualityKind;
    }

    public void setQualityKind(String qualityKind) {
        this.qualityKind = qualityKind;
    }

    public Boolean getCalibrated() {
        return calibrated;
    }

    public void setCalibrated(Boolean calibrated) {
        this.calibrated = calibrated;
    }

    public Long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(Long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public String getCostUnit() {
        return costUnit;
    }

    public void setCostUnit(String costUnit) {
        this.costUnit = costUnit;
    }

    public Boolean getDegraded() {
        return degraded;
    }

    public void setDegraded(Boolean degraded) {
        this.degraded = degraded;
    }

    public String getWarningsText() {
        return warningsText;
    }

    public void setWarningsText(String warningsText) {
        this.warningsText = warningsText;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getRunId() {
        return runId;
    }

    public void setRunId(String runId) {
        this.runId = runId;
    }
}
