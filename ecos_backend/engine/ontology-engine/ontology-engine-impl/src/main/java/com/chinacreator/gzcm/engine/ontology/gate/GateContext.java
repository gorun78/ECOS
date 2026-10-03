package com.chinacreator.gzcm.engine.ontology.gate;

import java.util.ArrayList;
import java.util.List;

/**
 * 门禁评估上下文（F03-03）。由提案执行侧从 payload 解析构造，与具体存储解耦以便单测。
 *
 * <p>{@code executeProposal} 与 {@code approveAndPublish} 复用同一 {@link PublishGateService}，
 * 上下文对象即"两条发布路径同一套校验"的载体（吸取分册 02 孪生教训）。
 */
public class GateContext {

    /** 指标变更引用（V1/V2 判据来源）。 */
    public static class MetricRef {
        public String code;            // M_*
        public String caliberId;       // V1：发布态必填
        public String formulaVersion;  // V1：口径版本
        public String expression;      // V2：公式
        public String aggregation;     // V2：SUM/AVG/...
    }

    /** 血缘列校验引用（V3 判据来源，经 data-engine REST 校验，禁跨 schema 直查 ecos_dw）。 */
    public static class LineageRef {
        public String resourceId;      // 数据资源（资产）id，data-engine 主键
        public String physicalColumn;  // 引用的事实表列

        public LineageRef(String resourceId, String physicalColumn) {
            this.resourceId = resourceId;
            this.physicalColumn = physicalColumn;
        }
    }

    private List<MetricRef> metrics = new ArrayList<>();
    private List<LineageRef> lineageRefs = new ArrayList<>();

    public List<MetricRef> getMetrics() { return metrics; }
    public void setMetrics(List<MetricRef> metrics) { this.metrics = metrics != null ? metrics : new ArrayList<>(); }
    public List<LineageRef> getLineageRefs() { return lineageRefs; }
    public void setLineageRefs(List<LineageRef> lineageRefs) { this.lineageRefs = lineageRefs != null ? lineageRefs : new ArrayList<>(); }
}
