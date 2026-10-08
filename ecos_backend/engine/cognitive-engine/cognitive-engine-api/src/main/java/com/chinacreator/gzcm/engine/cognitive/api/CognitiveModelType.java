package com.chinacreator.gzcm.engine.cognitive.api;

import java.util.Optional;

/**
 * 认知模型类型冻结枚举（REQ-COG-07 规格 1，PRD-05 §6.2 / 附件第二册 :1056-1062）。
 *
 * <p><b>枚举单源</b>：CM-01~CM-07 字面量只许在本类声明（D.2 规则 3；
 * {@code CognitiveModelTypeSingleSourceTest} 以源码扫描为门禁；DDL 禁建 CHECK，V261 同纪律）。
 * 新增枚举值先改本契约再入码。</p>
 *
 * <p><b>与旧 {@code model_type} 的关系</b>（V261 列形态依据）：旧四值
 * FORECAST/CAUSAL/SIMULATION/DECISION 到 CM 的映射与 V261 存量补形段逐字一致
 * （FORECAST→CM-03 / CAUSAL→CM-04 / SIMULATION→CM-05 / DECISION→CM-01），
 * 其余 CM 值不可由旧值反推；映射失败返回 empty，由调用侧 fail-loud 拒绝（禁静默默认值）。</p>
 *
 * <p><b>CM-07 数值禁令</b>（REQ-COG-07 规格 2 / 详设-05 §8.1 硬边界）：金额、比率、概率只准
 * 来自 CM-01~CM-05 或 data-engine 事实；CM-07 只能产 hypothesis，
 * {@link #numericWritesForbidden()} 为源码守卫（{@code Cm07NumericGuardTest}）的契约锚点。</p>
 */
public enum CognitiveModelType {

    CM_01("CM-01", "规则推理", false),
    CM_02("CM-02", "统计推断", false),
    CM_03("CM-03", "预测", false),
    CM_04("CM-04", "因果", false),
    CM_05("CM-05", "情景·反事实", false),
    CM_06("CM-06", "案例推理", false),
    CM_07("CM-07", "LLM 假设·语义推理", true);

    /** 契约文档 §REQ-COG-07 规格 1 冻结出处。 */
    public static final String CONTRACT_DOC_SECTION =
            "docs/20-需求/PRD-05-cognitive-engine需求规格-2026-09-28.md/§6.2 REQ-COG-07";

    private final String code;
    private final String label;
    private final boolean numericWritesForbidden;

    CognitiveModelType(String code, String label, boolean numericWritesForbidden) {
        this.code = code;
        this.label = label;
        this.numericWritesForbidden = numericWritesForbidden;
    }

    public String code() {
        return code;
    }

    /** PRD-05 冻结中文名（展示名，禁作存储值）。 */
    public String label() {
        return label;
    }

    /** CM-07 = true：该类型执行路径禁写 probability / 金额 / 比率列（REQ-COG-07 规格 2）。 */
    public boolean numericWritesForbidden() {
        return numericWritesForbidden;
    }

    /** 按存储字面量解析（CM-01…CM-07，大小写敏感于 DDL 补形段的 upper 结果，容错 trim）。 */
    public static Optional<CognitiveModelType> fromCode(String raw) {
        if (raw == null) return Optional.empty();
        String v = raw.trim().toUpperCase();
        for (CognitiveModelType t : values()) {
            if (t.code.equals(v)) return Optional.of(t);
        }
        return Optional.empty();
    }

    public static boolean isCmCode(String raw) {
        return fromCode(raw).isPresent();
    }

    /**
     * 旧 {@code model_type} 四值 → CM 映射（与 V261 存量补形段逐字一致）。
     * 未映射值（含 null / 空串 / 未知词）返回 empty ⇒ 调用侧 fail-loud，禁静默默认。
     */
    public static Optional<CognitiveModelType> fromLegacyModelType(String legacy) {
        if (legacy == null) return Optional.empty();
        switch (legacy.trim().toUpperCase()) {
            case "FORECAST":   return Optional.of(CM_03);
            case "CAUSAL":     return Optional.of(CM_04);
            case "SIMULATION": return Optional.of(CM_05);
            case "DECISION":   return Optional.of(CM_01); // V261 补形段口径：七类中无 DECISION，按最近语义归"规则推理"
            default:           return Optional.empty();
        }
    }
}
