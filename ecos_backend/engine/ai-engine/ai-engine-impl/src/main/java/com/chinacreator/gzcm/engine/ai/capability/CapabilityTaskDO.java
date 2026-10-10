package com.chinacreator.gzcm.engine.ai.capability;

import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentBaseDO;

import java.math.BigDecimal;

/**
 * 能力调用台账 DO（V269 {@code ecos_ai.ecos_ai_capability_task}）。
 *
 * <p>复用 {@link WAgentBaseDO} 通用基线列（tenant_id/org_id/domain/version_no/时间戳/is_deleted，DR05~DR08）；
 * 只存<b>引用与元数据</b>，禁复制大文件或完整敏感输入（§10.5 末段红线）。
 * 布尔语义一律 SMALLINT 0/1（DR05）：{@code calibrated}/{@code degraded} 为 {@link Integer}。</p>
 */
public class CapabilityTaskDO extends WAgentBaseDO {

    /** 主键 = taskId（MC01 应用侧 UUID；即 ModelCapabilityExecutor.submit 回显键）。 */
    private String id;
    /** SYNC / ASYNC（§10.4：同步直执 / 异步经 runtime-task）。 */
    private String taskKind;
    /** 能力 ID（§10.2 目录；词表单源 AiCapabilityId）。 */
    private String capabilityId;
    /** 触发的 Tool Contract 引用（机校字段单源 V270）。 */
    private String toolContractRef;
    /** 输入引用（文件/片段/向量引用 ID）；NOT NULL，禁存正文。 */
    private String inputRef;
    /** 选中模型（Model Router 决策产物，§10.3）。 */
    private String modelRef;
    /** 选中模型版本（SUP-MODEL-02 可追踪维度）。 */
    private String modelVersion;
    /** 结果引用（OCR 结果等落 MinIO 通道，业务库只存引用）。 */
    private String outputRef;
    /** PENDING/SUBMITTED/RUNNING/SUCCEEDED/DEGRADED/FAILED/CANCELED（七态词表）。 */
    private String status;
    /** 可选质量分（0..1，NUMERIC(6,5) 精度 MC02）。 */
    private BigDecimal qualityScore;
    /** MODEL_ESTIMATE 等置信类型（§8.3 同词表纪律，禁冒充统计概率）。 */
    private String qualityKind;
    /** NULL=未声明；0/1。UNCALIBRATED 断言不得用于权威裁决（§10.2 末段）。 */
    private Integer calibrated;
    /** 计量口径（TOKEN/CALL/INPUT_SCALE/...，§10.6 非 Token 计量）。 */
    private String costUnit;
    /** 调用延迟毫秒（bigint，SUP-MODEL-02）。 */
    private Long latencyMs;
    /** 0/1，DEGRADED 状态必须置 1（DR05 禁布尔）。 */
    private Integer degraded;
    /** JSON 数组（TEXT，MC04）：降级原因/质量不满足/备用模型未达门槛等。 */
    private String warningsText;
    /** 路由决策留痕（§10.3：requestedCapability/候选/选中/未选原因/预算/策略引用）。 */
    private String selectedModelReason;
    /** 全链 traceId（入口 RequestContextFilter 生成/透传，§12.2）。 */
    private String traceId;
    /** 关联运行 ID（§12.2 统一 trace 维度）。 */
    private String runId;
    /** 提交者。 */
    private String submitter;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTaskKind() { return taskKind; }
    public void setTaskKind(String taskKind) { this.taskKind = taskKind; }

    public String getCapabilityId() { return capabilityId; }
    public void setCapabilityId(String capabilityId) { this.capabilityId = capabilityId; }

    public String getToolContractRef() { return toolContractRef; }
    public void setToolContractRef(String toolContractRef) { this.toolContractRef = toolContractRef; }

    public String getInputRef() { return inputRef; }
    public void setInputRef(String inputRef) { this.inputRef = inputRef; }

    public String getModelRef() { return modelRef; }
    public void setModelRef(String modelRef) { this.modelRef = modelRef; }

    public String getModelVersion() { return modelVersion; }
    public void setModelVersion(String modelVersion) { this.modelVersion = modelVersion; }

    public String getOutputRef() { return outputRef; }
    public void setOutputRef(String outputRef) { this.outputRef = outputRef; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public BigDecimal getQualityScore() { return qualityScore; }
    public void setQualityScore(BigDecimal qualityScore) { this.qualityScore = qualityScore; }

    public String getQualityKind() { return qualityKind; }
    public void setQualityKind(String qualityKind) { this.qualityKind = qualityKind; }

    public Integer getCalibrated() { return calibrated; }
    public void setCalibrated(Integer calibrated) { this.calibrated = calibrated; }

    public String getCostUnit() { return costUnit; }
    public void setCostUnit(String costUnit) { this.costUnit = costUnit; }

    public Long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(Long latencyMs) { this.latencyMs = latencyMs; }

    public Integer getDegraded() { return degraded; }
    public void setDegraded(Integer degraded) { this.degraded = degraded; }

    public String getWarningsText() { return warningsText; }
    public void setWarningsText(String warningsText) { this.warningsText = warningsText; }

    public String getSelectedModelReason() { return selectedModelReason; }
    public void setSelectedModelReason(String selectedModelReason) { this.selectedModelReason = selectedModelReason; }

    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }

    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }

    public String getSubmitter() { return submitter; }
    public void setSubmitter(String submitter) { this.submitter = submitter; }
}
