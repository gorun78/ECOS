package com.chinacreator.gzcm.common.token;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Token 启发式估算单源（详细设计-06 F06-07 要点 1 / X-88）。
 *
 * <p>公共底座件：llm-gateway 出口计量（provider 未上报 usage 时的估算面）与各引擎的
 * 上下文预算计算共用同一套系数，避免"两处估算口径不一致"。估算值必须与实测值严格区分
 * （上报时标 {@code estimated=true}），禁把估算当实测。</p>
 *
 * <p>系数（GPT/DeepSeek 系 tokenizer 经验值，非精确）：
 * 中文字符 ≈ 1.5 token/字；英文/数字单词 ≈ 1.3 token/词；其余字符 ≈ 1.0 token/字符；
 * 每条消息 role 段固定开销 ≈ {@link #ROLE_OVERHEAD_TOKENS} token。</p>
 */
public final class TokenEstimates {

    /** 中文（含中文标点）字符 */
    private static final Pattern CHINESE_PATTERN =
            Pattern.compile("[\\u4e00-\\u9fff\\u3000-\\u303f\\uff00-\\uffef]");
    /** 英文/数字单词 */
    private static final Pattern WORD_PATTERN = Pattern.compile("[a-zA-Z0-9]+");

    private static final double CN_TOKEN_RATIO = 1.5;
    private static final double EN_TOKEN_RATIO = 1.3;
    private static final double OTHER_CHAR_RATIO = 1.0;

    /** 单条消息 role 段的固定 token 开销 */
    public static final int ROLE_OVERHEAD_TOKENS = 4;

    private TokenEstimates() {}

    /**
     * 估算纯文本 token 数。
     *
     * @param text 待估算文本（null / 空 → 0）
     * @return 估算 token 数（向上取整）
     */
    public static int estimateText(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        double tokens = 0.0;

        Matcher cnMatcher = CHINESE_PATTERN.matcher(text);
        int cnCount = 0;
        while (cnMatcher.find()) {
            cnCount++;
        }
        tokens += cnCount * CN_TOKEN_RATIO;

        String nonCn = cnMatcher.replaceAll(" ");
        Matcher wordMatcher = WORD_PATTERN.matcher(nonCn);
        int wordCount = 0;
        while (wordMatcher.find()) {
            wordCount++;
        }
        tokens += wordCount * EN_TOKEN_RATIO;

        String stripped = WORD_PATTERN.matcher(nonCn).replaceAll("");
        tokens += stripped.length() * OTHER_CHAR_RATIO;

        return (int) Math.ceil(tokens);
    }

    /**
     * 估算一条消息的 token 数（role 固定开销 + content 文本估算）。
     *
     * @param content 消息正文（可为 null）
     * @return 估算 token 数
     */
    public static int estimateMessage(String content) {
        return ROLE_OVERHEAD_TOKENS + estimateText(content);
    }
}
