package com.chinacreator.gzcm.engine.data.quality.scoring;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * DQ 6 维评估枚举（PMO-48-B T8 评分引擎 SPI 维度类型）。
 *
 * <p>6 维取值与默认权重（方案 §4.1）：</p>
 * <ul>
 *   <li>{@link #COMPLETENESS}   — 失空率，默认权重 30</li>
 *   <li>{@link #ACCURACY}       — 格式/范围/正则校验通过率，默认权重 25</li>
 *   <li>{@link #CONSISTENCY}    — 跨表/跨源一致性，默认权重 15</li>
 *   <li>{@link #UNIQUENESS}     — 主键/业务键无重复，默认权重 15</li>
 *   <li>{@link #VALIDITY}       — 枚举/合规命中通过率，默认权重 10</li>
 *   <li>{@link #FRESHNESS}      — 数据新鲜度（executed_at 距 now），默认权重 5</li>
 * </ul>
 *
 * <p>每个维度携带它映射的 rule_type 列表（{@link #getRuleTypes()})
 * 与默认权重（{@link #getDefaultWeight()})。权重用于
 * {@code Σ(维度分×权重) / Σ(权重)} 加权汇总，计算入
 * {@code ecos_dq.dq_score_asset.overall_score}。</p>
 *
 * <p>JSON 序列化走枚举 name（前/后端一致），参见
 * {@code Phase 1 T3 DqGovernanceController#dimension-registry}。</p>
 *
 * <p>V112 落表字段：{@code dimension VARCHAR(16)}</p>
 *
 * @author PMO-48-B T8
 */
public enum DqDimension {

    /** 完整性（规则类型 NOT_NULL / PRESENCE / OWN 类） */
    COMPLETENESS(30, List.of("NOT_NULL", "PRESENCE"), "字段非空率 / 必填覆盖度"),

    /** 准确性（规则类型 FORMAT / RANGE / REGEX） */
    ACCURACY(25, List.of("FORMAT", "RANGE", "REGEX"), "格式/范围/正则校验与参考值匹配度"),

    /** 一致性（规则类型 CONSISTENCY / STATISTICAL） */
    CONSISTENCY(15, List.of("CONSISTENCY", "STATISTICAL"), "跨表/跨源一致性与分布漂移"),

    /** 唯一性（规则类型 UNIQUE） */
    UNIQUENESS(15, List.of("UNIQUE"), "主键/业务键无重复"),

    /** 合规有效性（规则类型 VALIDITY / ENUM） */
    VALIDITY(10, List.of("VALIDITY", "ENUM"), "枚举/合规取值有效"),

    /** 及时性（规则类型 FRESHNESS / TIMELINESS） */
    FRESHNESS(5, List.of("FRESHNESS", "TIMELINESS"), "数据新鲜度 / 到达时效");

    /** 默认权重 0-100（综合评分） */
    private final int defaultWeight;

    /** 该维度映射的 rule_type 列表（小写不敏感匹配即可） */
    private final List<String> ruleTypes;

    /** 维度简短描述（中文，仅展示用，不用于序列化） */
    private final String description;

    DqDimension(int weight, List<String> types, String desc) {
        this.defaultWeight = weight;
        this.ruleTypes = types;
        this.description = desc;
    }

    public int getDefaultWeight() {
        return defaultWeight;
    }

    public List<String> getRuleTypes() {
        return ruleTypes;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 业务阈值等级（A≥95 / B≥85 / C≥70 / D≥50 / F&lt;50，对应 0.95/0.85/0.70/0.50）。
     *
     * <p>判定规则（边界含边界，输入已是 0.0-1.0 规范化分）：</p>
     * <pre>
     *   [0.95, 1.00]  A
     *   [0.85, 0.95)  B
     *   [0.70, 0.85)  C
     *   [0.50, 0.70)  D
     *   [0.00, 0.50)  F
     * </pre>
     *
     * @param score 0.0 - 1.0 规范化分
     * @return 单字符等级 A/B/C/D/F
     */
    public String gradeOf(double score) {
        if (score >= 0.95D) {
            return "A";
        }
        if (score >= 0.85D) {
            return "B";
        }
        if (score >= 0.70D) {
            return "C";
        }
        if (score >= 0.50D) {
            return "D";
        }
        return "F";
    }

    /** 反查：给定 ruleType（小写不敏感）是否归属当前维度。 */
    public boolean containsRuleType(String ruleType) {
        if (ruleType == null || ruleType.isBlank()) {
            return false;
        }
        String lower = ruleType.toLowerCase();
        for (String type : ruleTypes) {
            if (type.equalsIgnoreCase(lower)) {
                return true;
            }
        }
        return false;
    }

    /** 反查：给定 ruleType 应归属哪个维度（找不到时默认 {@link #ACCURACY}）。 */
    public static DqDimension ofRuleType(String ruleType) {
        if (ruleType == null || ruleType.isBlank()) {
            return ACCURACY;
        }
        for (DqDimension d : values()) {
            if (d.containsRuleType(ruleType)) {
                return d;
            }
        }
        // 兜底: 未识别的 rule_type 计入 ACCURACY（避免维度丢失）
        return ACCURACY;
    }

    /** 全部 6 个维度（固定顺序：COMPLETENESS/ACCURACY/CONSISTENCY/UNIQUENESS/VALIDITY/FRESHNESS）— 雷达图渲染用。 */
    public static List<DqDimension> all() {
        return List.of(COMPLETENESS, ACCURACY, CONSISTENCY, UNIQUENESS, VALIDITY, FRESHNESS);
    }

    /** JSON 序列化: 枚举 name 大写（前后端一致，避免 Jackson 默认 toString）。 */
    @JsonValue
    public String toJson() {
        return name();
    }
}
