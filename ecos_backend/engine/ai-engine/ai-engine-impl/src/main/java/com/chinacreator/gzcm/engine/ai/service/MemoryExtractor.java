package com.chinacreator.gzcm.engine.ai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 记忆抽取器 — 基于规则引擎从对话历史中提取用户偏好/习惯事实。
 * <p>
 * 纯规则匹配，不依赖 LLM 调用。用于在 System Prompt 中注入跨会话记忆段，
 * 使 Agent 在后续对话中保持用户偏好上下文。
 * </p>
 *
 * <h3>提取条件</h3>
 * <ol>
 *   <li>消息内容超过 50 字</li>
 *   <li>包含至少一个偏好关键词（偏好/习惯/总是/不要/记住/喜欢）</li>
 *   <li>最多返回 5 条事实</li>
 * </ol>
 *
 * <h3>使用方式</h3>
 * <pre>
 *   List&lt;String&gt; facts = memoryExtractor.extractFacts(messages);
 *   // → ["用户偏好：使用表格展示数据", "用户习惯：每天早上查看报表"]
 * </pre>
 */
@Component
public class MemoryExtractor {

    private static final Logger log = LoggerFactory.getLogger(MemoryExtractor.class);

    /** 偏好关键词 — 命中任一即视为候选事实 */
    private static final List<String> PREFERENCE_KEYWORDS = Arrays.asList(
            "偏好", "习惯", "总是", "不要", "记住", "喜欢"
    );

    /** 内容最小长度阈值（字符数） */
    private static final int MIN_CONTENT_LENGTH = 50;

    /** 最多返回的事实条数 */
    private static final int MAX_FACTS = 5;

    /**
     * 从消息列表中抽取用户偏好事实。
     *
     * @param messages 对话消息列表（通常来自会话历史）
     * @return 事实列表，每条格式为 "用户{关键词}：{摘要}"，最多 5 条
     */
    public List<String> extractFacts(List<AgentSessionService.AgentMessage> messages) {
        List<String> facts = new ArrayList<>();

        if (messages == null || messages.isEmpty()) {
            log.debug("[MemoryExtractor] No messages to extract from");
            return facts;
        }

        for (AgentSessionService.AgentMessage msg : messages) {
            if (facts.size() >= MAX_FACTS) {
                break;
            }

            String content = msg.getContent();
            if (content == null || content.length() < MIN_CONTENT_LENGTH) {
                continue;
            }

            // 仅提取 user 角色的消息
            if (!"user".equalsIgnoreCase(msg.getRole())) {
                continue;
            }

            for (String keyword : PREFERENCE_KEYWORDS) {
                if (content.contains(keyword)) {
                    String fact = buildFact(content, keyword);
                    facts.add(fact);
                    log.debug("[MemoryExtractor] Extracted fact with keyword '{}': {}", keyword, fact);
                    break; // 一条消息只生成一条事实
                }
            }
        }

        log.info("[MemoryExtractor] Extracted {} facts from {} messages", facts.size(),
                messages != null ? messages.size() : 0);
        return facts;
    }

    /**
     * 从消息内容中构建一条事实摘要。
     * <p>
     * 策略：在关键词前后截取上下文，格式化输出。
     * </p>
     */
    private String buildFact(String content, String keyword) {
        int idx = content.indexOf(keyword);

        // 取关键词前最多 20 字 + 关键词 + 关键词后最多 60 字
        int start = Math.max(0, idx - 20);
        int end = Math.min(content.length(), idx + keyword.length() + 60);

        String snippet = content.substring(start, end).replace("\n", " ").trim();
        if (start > 0) {
            snippet = "…" + snippet;
        }
        if (end < content.length()) {
            snippet = snippet + "…";
        }

        return "用户" + keyword + "：" + snippet;
    }
}
