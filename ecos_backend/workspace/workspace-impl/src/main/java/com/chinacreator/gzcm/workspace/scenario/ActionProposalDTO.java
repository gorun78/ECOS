package com.chinacreator.gzcm.workspace.scenario;

/**
 * 动作提案入参（详细设计-07 F07-10 / REQ-FC-04 写侧校验骨架）。
 *
 * <p>五个必填字段（负责人 / 截止日 / 审批人 / 预期影响 / 控制指标）由
 * {@link ActionProposalValidator} 在服务端强校验（前端只做即时提示，禁当结论，§0.6.4-3）；
 * {@code forecastRunId} 为必填锚点，须指向一条 FORMAL 预测运行（与 F07-09 共用
 * {@link BaselineReferenceGuard} 单源判定，禁两处各写一份）。</p>
 */
public class ActionProposalDTO {

    /** 场景 id（路径已有时体里冗余需一致性校验，见 F07-10）。 */
    private String scenarioId;

    /** 动作标签（描述性，非必填）。 */
    private String label;

    /** 负责人 —— 必填。 */
    private String assignee;

    /** 截止日（ISO-8601 日期串）—— 必填。 */
    private String deadline;

    /** 审批人 —— 必填（身份须来自登录态，禁前端自造 userId，F07-07-3 同源）。 */
    private String approver;

    /** 预期影响 —— 必填。 */
    private String expectedImpact;

    /** 控制指标 —— 必填。 */
    private String controlMetric;

    /** 锚定的预测运行 id —— 必填，须 FORMAL。 */
    private String forecastRunId;

    public String getScenarioId() { return scenarioId; }
    public void setScenarioId(String scenarioId) { this.scenarioId = scenarioId; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getAssignee() { return assignee; }
    public void setAssignee(String assignee) { this.assignee = assignee; }

    public String getDeadline() { return deadline; }
    public void setDeadline(String deadline) { this.deadline = deadline; }

    public String getApprover() { return approver; }
    public void setApprover(String approver) { this.approver = approver; }

    public String getExpectedImpact() { return expectedImpact; }
    public void setExpectedImpact(String expectedImpact) { this.expectedImpact = expectedImpact; }

    public String getControlMetric() { return controlMetric; }
    public void setControlMetric(String controlMetric) { this.controlMetric = controlMetric; }

    public String getForecastRunId() { return forecastRunId; }
    public void setForecastRunId(String forecastRunId) { this.forecastRunId = forecastRunId; }
}
