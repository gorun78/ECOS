package com.chinacreator.gzcm.cognitive.model;

import java.io.Serializable;

/**
 * 计划执行进度。
 */
public class PlanProgress implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 总步骤数 */
    private Integer totalSteps;
    /** 已完成步骤数 */
    private Integer completedSteps;
    /** 当前执行步骤 ID */
    private String currentStep;
    /** 完成百分比 */
    private Integer percentage;

    public Integer getTotalSteps() { return totalSteps; }
    public void setTotalSteps(Integer totalSteps) { this.totalSteps = totalSteps; }
    public Integer getCompletedSteps() { return completedSteps; }
    public void setCompletedSteps(Integer completedSteps) { this.completedSteps = completedSteps; }
    public String getCurrentStep() { return currentStep; }
    public void setCurrentStep(String currentStep) { this.currentStep = currentStep; }
    public Integer getPercentage() { return percentage; }
    public void setPercentage(Integer percentage) { this.percentage = percentage; }
}
