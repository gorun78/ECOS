package com.chinacreator.gzcm.engine.ai.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Token 估算器 — 不调用分词器，用启发式规则估算文本和消息的 token 数。
 * <p>
 * 规则：
 * <ul>
 *   <li>中文字符（含中文标点）≈ 1.5 token/字</li>
 *   <li>英文单词 ≈ 1.3 token/词</li>
 *   <li>数字/符号 ≈ 1.0 token/字符</li>
 *   <li>role 字符串（system/user/assistant/tool）≈ 4 token 固定开销</li>
 * </ul>
 * 这些系数基于 GPT/DeepSeek 系列 tokenizer 的经验值，不精确但够用。
 * </p>
 */
public class TokenEstimator {

    /** 中文 Unicode 范围正则（含常用中文字符和中文标点） */
    private static final Pattern CHINESE_PATTERN = Pattern.compile(
            "[\\u4e00-\\u9fff\\u3000-\\u303f\\uff00-\\uffef]");
    /** 英文/数字/符号单词正则 */
    private static final Pattern WORD_PATTERN = Pattern.compile("[a-zA-Z0-9]+");

    private static final double CN_TOKEN_RATIO = 1.5;
    private static final double EN_TOKEN_RATIO = 1.3;
    private static final double OTHER_CHAR_RATIO = 1.0;

    /** role 字段固定开销 */;
    private static final int ROLE_OVERHEAD = 4;

    /**
     * 估算纯文本的 token 数。
     *
     * @param text 待估算文本（可为 null）
     * @return 估算 token 数（null / 空字符串返回 0）
     */
    public static int estimate(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        double tokens = 0.0;

        // 先统计中文字符
        Matcher cnMatcher = CHINESE_PATTERN.matcher(text);
        int cnCount = 0;
        while (cnMatcher.find()) {
            cnCount++;
        }
        tokens += cnCount * CN_TOKEN_RATIO;

        // 去掉中文字符后，统计英文单词
        String nonCn = cnMatcher.replaceAll(" ");
        Matcher wordMatcher = WORD_PATTERN.matcher(nonCn);
        int wordCount = 0;
        while (wordMatcher.find()) {
            wordCount++;
        }
        tokens += wordCount * EN_TOKEN_RATIO;

        // 剩余非中非英数字的字符（标点、空格等）
        String stripped = WORD_PATTERN.matcher(nonCn).replaceAll("");
        int otherCount = stripped.length();
        tokens += otherCount * OTHER_CHAR_RATIO;

        return (int) Math.ceil(tokens);
    }

    /**
     * 估算一条 Message 的总 token 数。
     * <p>
     * 计算方式：role 开销 + content 文本估算。对 assistant 消息携带 tool_calls，
     * 额外按 JSON 序列化后的文本长度估算。
     * </p>
     *
     * @param msg 消息对象（可为 null）
     * @return 估算 token 数
     */
    public static int estimate(Message msg) {
        if (msg == null) {
            return 0;
        }

        double tokens = ROLE_OVERHEAD;

        // content
        if (msg.getContent() != null) {
            tokens += estimate(msg.getContent());
        }

        // toolCalls — 将其 JSON 序列化后估算
        if (msg.hasToolCalls() && msg.getToolCalls() != null) {
            for (ToolCall tc : msg.getToolCalls()) {
                // 每个 tool_call 大约: {"id":"...","type":"function","function":{"name":"...","arguments":{...}}}
                // 粗略按 name + arguments JSON 序列化长度估算
                StringBuilder sb = new StringBuilder();
                sb.append(tc.getName());
                if (tc.getArguments() != null) {
                    sb.append(tc.getArguments().toString());
                }
                tokens += estimate(sb.toString());
            }
        }

        return (int) Math.ceil(tokens);
    }
}
