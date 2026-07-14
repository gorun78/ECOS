package com.chinacreator.gzcm.runtime.core.quality;

import java.util.Collections;
import java.util.Map;

/**
 * 数据质量规则 — 描述一条质量校验规则。
 *
 * <p>规则由上层 BUS-ZHI 模块定义和管理，内核只负责执行。
 * 每条规则包含：
 * <ul>
 *   <li>ruleId — 唯一标识</li>
 *   <li>ruleType — 规则类型：NOT_NULL / RANGE / REGEX / UNIQUE / CUSTOM</li>
 *   <li>target — 作用对象："dataset.field" 或 "dataset.*"</li>
 *   <li>parameters — 规则参数，如 {"min": "0", "max": "200"}</li>
 *   <li>severity — 严重级别：ERROR(阻断) / WARN(记录) / INFO(统计)</li>
 * </ul>
 */
public class QualityRule {

    private final String ruleId;
    private final String ruleType;
    private final String target;
    private final Map<String, String> parameters;
    private final Severity severity;

    public QualityRule(String ruleId, String ruleType, String target,
                       Map<String, String> parameters, Severity severity) {
        this.ruleId = ruleId;
        this.ruleType = ruleType;
        this.target = target;
        this.parameters = parameters != null
            ? Collections.unmodifiableMap(parameters)
            : Collections.emptyMap();
        this.severity = severity != null ? severity : Severity.WARN;
    }

    public String getRuleId() {
        return ruleId;
    }

    public String getRuleType() {
        return ruleType;
    }

    public String getTarget() {
        return target;
    }

    public Map<String, String> getParameters() {
        return parameters;
    }

    public Severity getSeverity() {
        return severity;
    }

    /** 质量违规严重级别 */
    public enum Severity {
        /** 阻断：数据不合规则拒绝入库 */
        ERROR,
        /** 警告：数据入库但记录质量问题 */
        WARN,
        /** 信息：仅统计，不阻断不告警 */
        INFO
    }

    @Override
    public String toString() {
        return "QualityRule{" + ruleId + ":" + ruleType + " on " + target + " [" + severity + "]}";
    }
}
