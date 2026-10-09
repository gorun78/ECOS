package com.chinacreator.gzcm.workspace.scenario;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 动作提案入参（卷07 F07-10 N8；五必填中的审批人不从请求体取，改由主体解析）。 */
public final class ActionProposal {

    private String ownerUserId;
    private LocalDate dueDate;
    private BigDecimal expectedImpact;
    private String controlMetric;
    private String forecastRunId;
    private String description;

    public String getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(String ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public BigDecimal getExpectedImpact() {
        return expectedImpact;
    }

    public void setExpectedImpact(BigDecimal expectedImpact) {
        this.expectedImpact = expectedImpact;
    }

    public String getControlMetric() {
        return controlMetric;
    }

    public void setControlMetric(String controlMetric) {
        this.controlMetric = controlMetric;
    }

    public String getForecastRunId() {
        return forecastRunId;
    }

    public void setForecastRunId(String forecastRunId) {
        this.forecastRunId = forecastRunId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
