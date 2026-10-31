package com.chinacreator.gzcm.engine.cognitive.api;

import java.util.Optional;

/**
 * R4 认知模型九类分类词表单源（详设 §8.4.2，{@code ecos_cognitive_model.model_category}，V296）。
 *
 * <p><b>枚举单源</b>：九值存储字面量（CAUSAL…BEHAVIORAL）只许在本类声明
 * （{@code V296R4FieldsGateTest} 源码扫描门禁）；DDL 禁建 CHECK（V261/V292 同纪律，防先加
 * 约束后改代码造成线上拒写）。</p>
 *
 * <p><b>与既有两词表的关系</b>（V261 三词表并存先例，各守其域互不合并改写）：
 * 旧 {@code model_type} 四值（V124 FORECAST/CAUSAL/SIMULATION/DECISION）与
 * {@code cognitive_model_type} CM-01~07（V261，单源 {@link CognitiveModelType}）均保留；
 * 本词表是新权威九类分类列 {@code model_category} 的存储面单一来源。</p>
 *
 * <p><b>三期分期口径</b>（详设 §8.4.2 phase 注释）：一期 CAUSAL/DIAGNOSTIC/HYPOTHESIS_TESTING，
 * 二期 PREDICTIVE/SIMULATION/RISK/RELATIONAL，三期 OPTIMIZATION/BEHAVIORAL——
 * 以 {@link #routePhase()} 为派生单源，库内 phase 列写入必须取此值（禁调用方私有派生）。</p>
 */
public enum CognitiveR4ModelCategory {

    CAUSAL("CAUSAL", "因果模型", 1),
    DIAGNOSTIC("DIAGNOSTIC", "诊断模型", 1),
    HYPOTHESIS_TESTING("HYPOTHESIS_TESTING", "假设检验", 1),
    PREDICTIVE("PREDICTIVE", "预测模型", 2),
    SIMULATION("SIMULATION", "仿真模型", 2),
    RISK("RISK", "风险模型", 2),
    RELATIONAL("RELATIONAL", "关系模型", 2),
    OPTIMIZATION("OPTIMIZATION", "优化模型", 3),
    BEHAVIORAL("BEHAVIORAL", "行为模型", 3);

    /** 契约出处（冻结口径）。 */
    public static final String CONTRACT_DOC_SECTION =
            "docs/30-设计/ECOS详细设计说明书-从零实施版-2026-10-12.md/§8.4.2";

    private final String code;
    private final String label;
    private final int routePhase;

    CognitiveR4ModelCategory(String code, String label, int routePhase) {
        this.code = code;
        this.label = label;
        this.routePhase = routePhase;
    }

    public String code() {
        return code;
    }

    /** 中文名（展示名，禁作存储值）。 */
    public String label() {
        return label;
    }

    /** 三期路线分期 1/2/3（phase 列派生单源）。 */
    public int routePhase() {
        return routePhase;
    }

    /** 按存储字面量解析（大小写容错 + trim）；未知值返回 empty ⇒ 调用侧 fail-loud，禁静默默认。 */
    public static Optional<CognitiveR4ModelCategory> fromCode(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String v = raw.trim();
        for (CognitiveR4ModelCategory c : values()) {
            if (c.code.equalsIgnoreCase(v)) {
                return Optional.of(c);
            }
        }
        return Optional.empty();
    }
}
