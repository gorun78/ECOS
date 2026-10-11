package com.chinacreator.gzcm.engine.data.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 预测运行只读快照响应（GET /api/v1/datanet/forecast-runs/{runId}/snapshot — 详细设计-09 v1.2 §十一 H-3 交付③）。
 *
 * <p><b>跨引擎契约（ST08）</b>：认知引擎禁直连 {@code ecos_dw}，本 VO 即 cognitive 侧
 * {@code ForecastRunSnapshotReader} REST 适配的唯一 wire 形态。JSON 键名与认知侧
 * record 组件<b>逐字对齐</b>（只增不改；消费方忽略未知键即可前向兼容）：
 * <pre>
 * 本 VO                          ←→ cognitive ForecastRunSnapshotReader
 * ------------------------------------------------------------------
 * forecastRunId                  RunSnapshot.forecastRunId
 * caliberId                      RunSnapshot.caliberId
 * caliberVersion                 RunSnapshot.caliberVersion
 * asOfTime                       RunSnapshot.asOfTime（String，ISO_LOCAL_DATE_TIME）
 * baselineProfit                 RunSnapshot.baselineProfit（BigDecimal，可空=无落库真值，禁伪造）
 * scenarioProfit                 RunSnapshot.scenarioProfit（同上）
 * cells[]                        RunSnapshot.cells → ScenarioBalanceChecker.Cell
 *   cells[].projectId            Cell.projectId(String)
 *   cells[].period               Cell.period(int)
 *   cells[].baseline             Cell.baseline(BigDecimal)
 *   cells[].scenario             Cell.scenario(BigDecimal)
 * edges[]                        RunSnapshot.edges → ContributionEdgeSource
 *   edges[].chainId              ContributionEdgeSource.chainId
 *   edges[].path[].node/.type    CognitiveContractSchema.ChainNodeVO(node,type)
 *   edges[].baseline/.scenario   ContributionEdgeSource.baseline/.scenario
 *   edges[].evidenceRefs[]       ContributionEdgeSource.evidenceRefs(List&lt;String&gt;)
 *   edges[].statSupport.method   CognitiveContractSchema.StatSupportVO.method
 *   edges[].statSupport.pValue   CognitiveContractSchema.StatSupportVO.pValue（键名以 @JsonProperty 钉死）
 * </pre>
 *
 * <p>六要素（定位快照所需）：{@code forecastRunId, caliberId, caliberVersion, asOfTime,
 * snapshotId, formulaVersion}——后两者为 V277 增列，历史行缺值时为 null（禁伪造回填）。
 *
 * <p>空集合语义：{@code cells}/{@code edges} 为空列表 = 运行结果载荷非结构化 JSON
 * （如 DeterministicForecastCalculator 的 canonicalBytes 文本形态）⇒ 认知侧按"无快照明细"
 * 处理并 fail-loud，<b>不是</b>数据不存在（行存在性由 HTTP 200 本身担保；runId 不存在回 404）。
 */
public class ForecastRunSnapshotVO {

    // ── 六要素 ──
    private String forecastRunId;
    private String caliberId;
    private String caliberVersion;
    /** 快照截至时点，ISO_LOCAL_DATE_TIME 字符串（RunSnapshot.asOfTime 为 String，同形直落）。 */
    private String asOfTime;
    private String snapshotId;
    private String formulaVersion;

    // ── 运行上下文（附加只读信息，认知侧可忽略）──
    private String scenarioId;
    private String runType;
    private String status;
    private String baselineRunId;

    // ── 金额真值（缺载荷时为 null，禁伪造）──
    private BigDecimal baselineProfit;
    private BigDecimal scenarioProfit;

    // ── 明细 ──
    private List<CellVO> cells = new ArrayList<>();
    private List<EdgeVO> edges = new ArrayList<>();

    /** 金额 cell（与 ScenarioBalanceChecker.Cell 四组件同键名）。 */
    public static class CellVO {
        private String projectId;
        /** 期间整型形态（Cell.period 为 int，与 YYYY-MM 字符串期间不同源，按载荷原样给出）。 */
        private Integer period;
        private BigDecimal baseline;
        private BigDecimal scenario;

        public String getProjectId() { return projectId; }
        public void setProjectId(String projectId) { this.projectId = projectId; }
        public Integer getPeriod() { return period; }
        public void setPeriod(Integer period) { this.period = period; }
        public BigDecimal getBaseline() { return baseline; }
        public void setBaseline(BigDecimal baseline) { this.baseline = baseline; }
        public BigDecimal getScenario() { return scenario; }
        public void setScenario(BigDecimal scenario) { this.scenario = scenario; }
    }

    /** 因果边（与 ContributionEdgeSource 六组件同键名）。 */
    public static class EdgeVO {
        private String chainId;
        private List<PathNodeVO> path = new ArrayList<>();
        private BigDecimal baseline;
        private BigDecimal scenario;
        private List<String> evidenceRefs = new ArrayList<>();
        private StatSupportVO statSupport;

        public String getChainId() { return chainId; }
        public void setChainId(String chainId) { this.chainId = chainId; }
        public List<PathNodeVO> getPath() { return path; }
        public void setPath(List<PathNodeVO> path) { this.path = path == null ? new ArrayList<>() : path; }
        public BigDecimal getBaseline() { return baseline; }
        public void setBaseline(BigDecimal baseline) { this.baseline = baseline; }
        public BigDecimal getScenario() { return scenario; }
        public void setScenario(BigDecimal scenario) { this.scenario = scenario; }
        public List<String> getEvidenceRefs() { return evidenceRefs; }
        public void setEvidenceRefs(List<String> evidenceRefs) {
            this.evidenceRefs = evidenceRefs == null ? new ArrayList<>() : evidenceRefs;
        }
        public StatSupportVO getStatSupport() { return statSupport; }
        public void setStatSupport(StatSupportVO statSupport) { this.statSupport = statSupport; }
    }

    /** 链节点（与 CognitiveContractSchema.ChainNodeVO 同键名 node/type）。 */
    public static class PathNodeVO {
        private String node;
        private String type;

        public String getNode() { return node; }
        public void setNode(String node) { this.node = node; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
    }

    /** 统计支撑（与 CognitiveContractSchema.StatSupportVO 同键名；pValue 键名钉死防 bean 命名漂移）。 */
    public static class StatSupportVO {
        private String method;
        @JsonProperty("pValue")
        private Double pValue;

        public String getMethod() { return method; }
        public void setMethod(String method) { this.method = method; }
        @JsonProperty("pValue")
        public Double getpValue() { return pValue; }
        @JsonProperty("pValue")
        public void setpValue(Double pValue) { this.pValue = pValue; }
    }

    public String getForecastRunId() { return forecastRunId; }
    public void setForecastRunId(String forecastRunId) { this.forecastRunId = forecastRunId; }
    public String getCaliberId() { return caliberId; }
    public void setCaliberId(String caliberId) { this.caliberId = caliberId; }
    public String getCaliberVersion() { return caliberVersion; }
    public void setCaliberVersion(String caliberVersion) { this.caliberVersion = caliberVersion; }
    public String getAsOfTime() { return asOfTime; }
    public void setAsOfTime(String asOfTime) { this.asOfTime = asOfTime; }
    public String getSnapshotId() { return snapshotId; }
    public void setSnapshotId(String snapshotId) { this.snapshotId = snapshotId; }
    public String getFormulaVersion() { return formulaVersion; }
    public void setFormulaVersion(String formulaVersion) { this.formulaVersion = formulaVersion; }
    public String getScenarioId() { return scenarioId; }
    public void setScenarioId(String scenarioId) { this.scenarioId = scenarioId; }
    public String getRunType() { return runType; }
    public void setRunType(String runType) { this.runType = runType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getBaselineRunId() { return baselineRunId; }
    public void setBaselineRunId(String baselineRunId) { this.baselineRunId = baselineRunId; }
    public BigDecimal getBaselineProfit() { return baselineProfit; }
    public void setBaselineProfit(BigDecimal baselineProfit) { this.baselineProfit = baselineProfit; }
    public BigDecimal getScenarioProfit() { return scenarioProfit; }
    public void setScenarioProfit(BigDecimal scenarioProfit) { this.scenarioProfit = scenarioProfit; }
    public List<CellVO> getCells() { return cells; }
    public void setCells(List<CellVO> cells) { this.cells = cells == null ? new ArrayList<>() : cells; }
    public List<EdgeVO> getEdges() { return edges; }
    public void setEdges(List<EdgeVO> edges) { this.edges = edges == null ? new ArrayList<>() : edges; }
}
