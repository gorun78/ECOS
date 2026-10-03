package com.chinacreator.gzcm.engine.ontology.dto;

import com.chinacreator.gzcm.engine.ontology.model.MetricDefinition;

/** 指标定义视图（D.1 listMetricDefinitions，带 caliberId/formulaVersion）。 */
public class MetricDefinitionVO {
    private String id;
    private String code;
    private String name;
    private String expression;
    private String aggregation;
    private String entityCode;
    private String caliberId;
    private String formulaVersion;
    private String status;
    private String snapshotFrozen;   // 是否已冻结口径快照（发布时刻一次性只读）
    private String createTime;

    public static MetricDefinitionVO from(MetricDefinition m) {
        MetricDefinitionVO v = new MetricDefinitionVO();
        v.id = m.getId();
        v.code = m.getCode();
        v.name = m.getName();
        v.expression = m.getExpression();
        v.aggregation = m.getAggregation();
        v.entityCode = m.getEntityCode();
        v.caliberId = m.getCaliberId();
        v.formulaVersion = m.getFormulaVersion();
        v.status = m.getStatus();
        v.snapshotFrozen = m.getCaliberSnapshot() != null && !m.getCaliberSnapshot().isBlank() ? "true" : "false";
        v.createTime = m.getCreateTime() != null ? m.getCreateTime().toString() : null;
        return v;
    }

    public String getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getExpression() { return expression; }
    public String getAggregation() { return aggregation; }
    public String getEntityCode() { return entityCode; }
    public String getCaliberId() { return caliberId; }
    public String getFormulaVersion() { return formulaVersion; }
    public String getStatus() { return status; }
    public String getSnapshotFrozen() { return snapshotFrozen; }
    public String getCreateTime() { return createTime; }
}
