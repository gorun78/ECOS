package com.chinacreator.gzcm.ai.wagent.narrative;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * 分册10 F10-24 · 叙述数字剥离守卫（E-WA-NARRATIVE-NUM）。
 *
 * <p>LLM 叙述里出现的<b>未经 CM 源锚定的金额/比率/百分比</b>属高危（F10-24）：
 * {@link #stripNumbersFromLlmText(String, Set)} 将数字序列替换为 {@code <NUM>}。
 * {@link #isNarrativeClean(String)} 判定：剥离后若文本"只剩占位"（全是数字）⇒ 危险。</p>
 */
public final class NarrativeGuard {

    public static final String NUM_PLACEHOLDER = "<NUM>";

    /** 数字序列（含整数/小数/百分比/千分位），保守匹配，避免误伤标识符。 */
    private static final Pattern NUMBER = Pattern.compile("[-+]?\\d+(?:[.,]\\d+)*(?:%|亿|万)?");

    private NarrativeGuard() {}

    /**
     * 剥离 LLM 文本里的数字（金额/比率/百分比）：数字序列 → {@code <NUM>}。
     * {@code knownCmKeys} 仅在语义上标记"这些 CM 键对应数字是锚定的"，本方法只剥离数字形态。
     */
    public static String stripNumbersFromLlmText(String text, Set<String> knownCmKeys) {
        if (text == null) return "";
        return NUMBER.matcher(text).replaceAll(NUM_PLACEHOLDER);
    }

    /**
     * 叙述是否"干净"：文本去掉占位符后<strong>无残留</strong>视为"全是数字"=危险 ⇒ false；
     * 空/纯空白文本同样危险（无实质信息）⇒ false；否则 true。
     */
    public static boolean isNarrativeClean(String text) {
        if (text == null || text.isBlank()) return false;
        String stripped = stripNumbersFromLlmText(text, Set.of()).replace(NUM_PLACEHOLDER, "");
        // 只剩非数字字符且含实质内容 = 干净。
        return !stripped.isBlank();
    }
}
