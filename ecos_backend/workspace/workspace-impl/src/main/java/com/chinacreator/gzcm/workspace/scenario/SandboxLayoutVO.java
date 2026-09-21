package com.chinacreator.gzcm.workspace.scenario;

/**
 * 场景沙盘布局视图 VO — GET 布局出参（PMO-60 v2.0 P1）。
 * <p>对应表 {@code ecos_scenario_sandbox_layout}。</p>
 */
public class SandboxLayoutVO {

    /** 所属场景 id */
    private String scenarioId;
    /** 布局 JSONB（原始 Map：{nodes, edges, viewport}） */
    private Object layout;
    /** 版本号（乐观锁） */
    private Integer layoutVersion;
    /** 更新时间 ISO-8601 */
    private String updateTime;

    public String getScenarioId() { return scenarioId; }
    public void setScenarioId(String scenarioId) { this.scenarioId = scenarioId; }
    public Object getLayout() { return layout; }
    public void setLayout(Object layout) { this.layout = layout; }
    public Integer getLayoutVersion() { return layoutVersion; }
    public void setLayoutVersion(Integer layoutVersion) { this.layoutVersion = layoutVersion; }
    public String getUpdateTime() { return updateTime; }
    public void setUpdateTime(String updateTime) { this.updateTime = updateTime; }
}
