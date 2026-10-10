package com.chinacreator.gzcm.engine.ai.wagent.dataobject;

import java.time.LocalDateTime;

/**
 * V234 {@code ecos_ai.ecos_wagent_run_step} 运行步骤 DO（step×attempt 粒度执行留痕，REQ-WAG-11/12）。
 * <p>词表单源：{@code step_type} = {@link com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.StepType}
 * （六类，§4.2 核定集合）；{@code policy_effect} = {@link
 * com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.PolicyEffect}（四值，security-engine PEP 裁决）；
 * {@code automation_level} 档位 = {@link com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.AutomationLevel}
 * （应用层断言 ≤ run.automation_ceiling）。</p>
 * <p>{@code error_code} 为 §5.3/§5.4 线协议码（VARCHAR(24)，单源 = {@link
 * com.chinacreator.gzcm.engine.ai.wagent.WAgentErrorCodes}）；{@code task_id} 是 runtime-task 真值外部键，禁自造。</p>
 * <p>UNIQUE(run_id, step_key, attempt, is_deleted)；输入输出引用型（*_ref_text + *_hash，§6.3 不存正文）。</p>
 */
public class WAgentRunStepDO extends WAgentBaseDO {

    private String id;
    private String runId;
    private String stepKey;
    private Integer attempt;
    private String stepType;
    private String toolName;
    private String toolVersion;
    private String promptId;
    private String promptVersion;
    private Integer automationLevel;
    private String status;
    private String inputRefText;
    private String inputHash;
    private String outputRefText;
    private String outputHash;
    private String taskId;
    private String policyEffect;
    private String policyRuleId;
    private String metricsJson;
    private String errorCode;
    private String errorText;
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }

    public String getStepKey() { return stepKey; }
    public void setStepKey(String stepKey) { this.stepKey = stepKey; }

    public Integer getAttempt() { return attempt; }
    public void setAttempt(Integer attempt) { this.attempt = attempt; }

    public String getStepType() { return stepType; }
    public void setStepType(String stepType) { this.stepType = stepType; }

    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }

    public String getToolVersion() { return toolVersion; }
    public void setToolVersion(String toolVersion) { this.toolVersion = toolVersion; }

    public String getPromptId() { return promptId; }
    public void setPromptId(String promptId) { this.promptId = promptId; }

    public String getPromptVersion() { return promptVersion; }
    public void setPromptVersion(String promptVersion) { this.promptVersion = promptVersion; }

    public Integer getAutomationLevel() { return automationLevel; }
    public void setAutomationLevel(Integer automationLevel) { this.automationLevel = automationLevel; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getInputRefText() { return inputRefText; }
    public void setInputRefText(String inputRefText) { this.inputRefText = inputRefText; }

    public String getInputHash() { return inputHash; }
    public void setInputHash(String inputHash) { this.inputHash = inputHash; }

    public String getOutputRefText() { return outputRefText; }
    public void setOutputRefText(String outputRefText) { this.outputRefText = outputRefText; }

    public String getOutputHash() { return outputHash; }
    public void setOutputHash(String outputHash) { this.outputHash = outputHash; }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    public String getPolicyEffect() { return policyEffect; }
    public void setPolicyEffect(String policyEffect) { this.policyEffect = policyEffect; }

    public String getPolicyRuleId() { return policyRuleId; }
    public void setPolicyRuleId(String policyRuleId) { this.policyRuleId = policyRuleId; }

    public String getMetricsJson() { return metricsJson; }
    public void setMetricsJson(String metricsJson) { this.metricsJson = metricsJson; }

    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }

    public String getErrorText() { return errorText; }
    public void setErrorText(String errorText) { this.errorText = errorText; }

    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
}
