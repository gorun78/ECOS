package com.chinacreator.gzcm.workspace.scenario;

import java.util.List;

/** 向导范围预填出参（卷07 F07-14 N3）。 */
public final class ForecastScopeVO {

    private final String scenarioId;
    private final List<String> periods;
    private final List<String> projectIds;
    private final List<String> departmentIds;
    private final String caliberId;
    private final Double coverageRatio;
    private final long requiredFactCells;

    public ForecastScopeVO(String scenarioId, List<String> periods, List<String> projectIds,
                           List<String> departmentIds, String caliberId, Double coverageRatio, long requiredFactCells) {
        this.scenarioId = scenarioId;
        this.periods = periods;
        this.projectIds = projectIds;
        this.departmentIds = departmentIds;
        this.caliberId = caliberId;
        this.coverageRatio = coverageRatio;
        this.requiredFactCells = requiredFactCells;
    }

    public String getScenarioId() {
        return scenarioId;
    }

    public List<String> getPeriods() {
        return periods;
    }

    public List<String> getProjectIds() {
        return projectIds;
    }

    public List<String> getDepartmentIds() {
        return departmentIds;
    }

    public String getCaliberId() {
        return caliberId;
    }

    /** 完整度口径（消费 F07-02 单源，禁前端自算）。 */
    public Double getCoverageRatio() {
        return coverageRatio;
    }

    /** 项目×部门×月×环节 的应有数口径（属主 PRD-02，workspace 只给应有数）。 */
    public long getRequiredFactCells() {
        return requiredFactCells;
    }
}
