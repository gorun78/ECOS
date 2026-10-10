package com.chinacreator.gzcm.engine.data.governance.conf;

import java.util.Locale;

/**
 * 《ECOS 认知域与智能域补充详细设计 v1.0》§7.2 置信度规则 — 单源解析器。
 *
 * <p>设计要点（§7.2 置信度规则）：
 * <ul>
 *   <li>{@code confidence.value} 不得当作已校准概率，除非关联模型通过 calibration 门禁且明确声明
 *       校准方法/数据集/有效范围；</li>
 *   <li>LLM 自报「90% 确信」必须标为 {@link #UNCALIBRATED_MODEL_ESTIMATE}，
 *       不得与统计概率混用（{@code calibrated} 恒 FALSE）。</li>
 * </ul>
 *
 * <p>三种 kind 词表单源：{@link #STATISTICAL} / {@link #CALIBRATED_MODEL} /
 * {@link #UNCALIBRATED_MODEL_ESTIMATE}。这是<b>纯静态无副作用</b>解析器，
 * 供 Service 落库 + 单测直接断言（不依赖 Spring/JDBC）。</p>
 */
public final class GovernanceConfidenceResolver {

    /** 统计概率（来自确定性统计，如剖析空值率/基数），非模型估计。 */
    public static final String STATISTICAL = "STATISTICAL";

    /** 已校准模型估计：模型通过 calibration 门禁且声明校准方法/数据集/有效范围。 */
    public static final String CALIBRATED_MODEL = "CALIBRATED_MODEL";

    /** 未校准模型/LLM 自报估计：不得当已校准概率（LLM 自报一律归此）。 */
    public static final String UNCALIBRATED_MODEL_ESTIMATE = "UNCALIBRATED_MODEL_ESTIMATE";

    private GovernanceConfidenceResolver() {
    }

    /** 规范化后的置信度（kind + calibrated 标记 + 归一 value）。 */
    public record ResolvedConfidence(Double value, String kind, Boolean calibrated) {
        /** 无 value → 全空（候选未提供置信度）。 */
        public boolean isEmpty() {
            return value == null;
        }
    }

    /**
     * 解析置信度。
     *
     * <p>规范化规则（§7.2 置信度规则 + 任务口径单源）：
     * <ul>
     *   <li>kind 白名单 = {@link #STATISTICAL} / {@link #CALIBRATED_MODEL} /
     *       {@link #UNCALIBRATED_MODEL_ESTIMATE}；设计文档同义别名 {@code MODEL_ESTIMATE}
     *       与 {@code LLM} 归一为 {@link #UNCALIBRATED_MODEL_ESTIMATE}，
     *       {@code STATISTICAL_PROBABILITY} 归一为 {@link #STATISTICAL}；</li>
     *   <li>kind 非空白名且不在白名单/别名内 → 拒（IllegalArgumentException → 400）；</li>
     *   <li>{@code calibrated=true} 与 LLM 来源 / 未校准 kind 矛盾 → 拒（400），
     *       禁止未经 calibration 门禁自认已校准（SUP-MODEL-03 同族语义）；</li>
     *   <li>LLM 自报一律 {@link #UNCALIBRATED_MODEL_ESTIMATE} + calibrated=false。</li>
     * </ul>
     *
     * @param value              0..1 数值（可 null）
     * @param declaredKind       请求方声明的 kind（可 null 或别名/白名单词形）
     * @param declaredCalibrated 请求方是否声明已校准（可 null）
     * @param sourceLlm          该置信度是否来自 LLM 自报（true 则强制 UNCALIBRATED + calibrated=false）
     * @return 规范化置信度
     * @throws IllegalArgumentException kind 不在白名单（非别名）或 calibrated 与 kind 矛盾
     */
    public static ResolvedConfidence resolve(Double value, String declaredKind,
                                             Boolean declaredCalibrated, boolean sourceLlm) {
        if (value == null) {
            return new ResolvedConfidence(null, null, null);
        }
        double clamp = clamp01(value);
        String normalizedKind = normalizeKind(declaredKind);

        // 红线：LLM 自报一律 UNCALIBRATED_MODEL_ESTIMATE + calibrated=false（§7.2 末段）。
        if (sourceLlm) {
            if (Boolean.TRUE.equals(declaredCalibrated)) {
                throw new IllegalArgumentException(
                        "confidence 矛盾：LLM 自报置信度禁声明 calibrated=true（§7.2 置信度规则）");
            }
            return new ResolvedConfidence(clamp, UNCALIBRATED_MODEL_ESTIMATE, Boolean.FALSE);
        }

        // 统计概率：kind=STATISTICAL；校准标记不参与（统计不是「校准模型」），保留声明态为 null。
        if (STATISTICAL.equals(normalizedKind)) {
            return new ResolvedConfidence(clamp, STATISTICAL, null);
        }

        // calibrated 与归一后 kind 矛盾 → 400：
        // 未校准 kind 自认已校准 / 已校准 kind 自认未校准（显式 false）均拒绝，禁止口径漂移。
        if (Boolean.TRUE.equals(declaredCalibrated)
                && UNCALIBRATED_MODEL_ESTIMATE.equals(normalizedKind)) {
            throw new IllegalArgumentException(
                    "confidence 矛盾：kind=" + declaredKind
                            + " 未通过校准门禁，禁声明 calibrated=true（§7.2 置信度规则）");
        }
        if (Boolean.FALSE.equals(declaredCalibrated)
                && CALIBRATED_MODEL.equals(normalizedKind)) {
            throw new IllegalArgumentException(
                    "confidence 矛盾：kind=CALIBRATED_MODEL 与 calibrated=false 冲突（§7.2 置信度规则）");
        }

        // 模型估计：仅当显式声明已校准（走 calibration 门禁）时升格 CALIBRATED_MODEL，
        // 否则一律降级为 UNCALIBRATED_MODEL_ESTIMATE（宁可保守，不可误当已校准概率）。
        boolean calibrated = Boolean.TRUE.equals(declaredCalibrated);
        if (calibrated) {
            return new ResolvedConfidence(clamp, CALIBRATED_MODEL, Boolean.TRUE);
        }
        if (normalizedKind == null) {
            // 未声明 kind 的模型侧数值：保守归未校准（无 calibration 门禁声明不认已校准）
            return new ResolvedConfidence(clamp, UNCALIBRATED_MODEL_ESTIMATE, Boolean.FALSE);
        }
        return new ResolvedConfidence(clamp, normalizedKind, null);
    }

    /**
     * 归一 kind 词形；非白名单且非已知别名 → 拒（400）。
     *
     * @return 白名单词形；null = 未声明
     */
    static String normalizeKind(String declaredKind) {
        if (declaredKind == null || declaredKind.isBlank()) {
            return null;
        }
        String k = declaredKind.trim().toUpperCase(Locale.ROOT);
        switch (k) {
            case "STATISTICAL":
            case "STATISTICAL_PROBABILITY":
                return STATISTICAL;
            case "CALIBRATED_MODEL":
                return CALIBRATED_MODEL;
            case "UNCALIBRATED_MODEL_ESTIMATE":
            case "MODEL_ESTIMATE":
            case "LLM":
                return UNCALIBRATED_MODEL_ESTIMATE;
            default:
                throw new IllegalArgumentException(
                        "confidence.kind 不在白名单: " + declaredKind
                                + "（白名单 " + STATISTICAL + " / " + CALIBRATED_MODEL + " / "
                                + UNCALIBRATED_MODEL_ESTIMATE + "，设计文档别名 MODEL_ESTIMATE）");
        }
    }

    /**
     * 解析 LLM 自报文本里的百分比（如 "90% 确信" / "confidence: 0.78"）为数值并强制未校准标记。
     *
     * @param llmText LLM 原文片段
     * @return 规范化置信度（kind=UNCALIBRATED_MODEL_ESTIMATE，calibrated=false）；解析不到数值返回全空
     */
    public static ResolvedConfidence fromLlmText(String llmText) {
        if (llmText == null || llmText.isBlank()) {
            return new ResolvedConfidence(null, null, null);
        }
        Double value = extractPercent(llmText);
        return resolve(value, null, null, true);
    }

    /** 0..1 归一（越界收敛不抛错，保留候选语义）。 */
    private static double clamp01(double v) {
        if (v < 0) return 0;
        if (v > 1) return 1;
        return v;
    }

    /**
     * 从 LLM 文本抽取 0..1 数值：
     * <ul>
     *   <li>"90%" / "90 % " → 0.90</li>
     *   <li>"confidence: 0.78" / "confidence=0.78" → 0.78</li>
     *   <li>"0.78" → 0.78</li>
     * </ul>
     */
    private static Double extractPercent(String text) {
        String s = text.trim();
        // 1) 百分号形态
        int pct = s.indexOf('%');
        if (pct >= 0) {
            String head = s.substring(0, pct).trim();
            int ws = indexOfNonNumberFromLeft(head);
            String num = ws < 0 ? head : head.substring(ws);
            try {
                double d = Double.parseDouble(num);
                return d / 100.0;
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        // 2) 小数形态（0.xx / 1.0）
        for (String token : s.split("[\\s,;，；]+")) {
            String t = token.trim();
            if (t.matches("0?\\.\\d+")) {
                try {
                    return Double.parseDouble(t);
                } catch (NumberFormatException ignored) {
                    // next
                }
            }
            if (t.equals("0") || t.equals("1")) {
                try {
                    return Double.parseDouble(t);
                } catch (NumberFormatException ignored) {
                    // next
                }
            }
        }
        // 3) 兜底 conf 关键词后数字
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "(\\d+(?:\\.\\d+)?)\\s*%?").matcher(s);
        while (m.find()) {
            try {
                double d = Double.parseDouble(m.group(1));
                if (d >= 0 && d <= 1) return d;
                if (d <= 100) return d / 100.0;
            } catch (NumberFormatException ignored) {
                // next
            }
        }
        return null;
    }

    /** 从字符串左侧定位第一个数字字符（含小数点/负号）的下标。 */
    private static int indexOfNonNumberFromLeft(String head) {
        for (int i = 0; i < head.length(); i++) {
            char c = head.charAt(i);
            if (Character.isDigit(c) || c == '.' || c == '-') {
                return i;
            }
        }
        return -1;
    }
}
