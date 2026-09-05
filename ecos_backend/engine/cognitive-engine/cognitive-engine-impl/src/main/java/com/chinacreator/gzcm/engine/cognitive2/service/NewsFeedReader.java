package com.chinacreator.gzcm.engine.cognitive2.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 新闻/文档解析器（Wave-3.2 T7 Demo 用）— 把 Markdown 解析为结构化实体/要点/mermaid 图节点。
 *
 * <p>不做的事：不直调 LLM/MCP（保持 Demo 端点可离线跑）；
 * 真正复杂解析由 kb-engine 走 MinerU + extraction REST，本组件仅作为 Demo 输入侧。</p>
 *
 * <p>对齐 05 文档 §四 抽取流程的"输入侧"。</p>
 *
 * @author ECOS Cognitive Engine Team
 * @since 2026-09-02 (Wave-3.2)
 */
@Component
public class NewsFeedReader {

    private static final Logger log = LoggerFactory.getLogger(NewsFeedReader.class);

    /** Markdown 标题 */
    private static final Pattern HEADER_PATTERN = Pattern.compile("^(#{1,3})\\s+(.+)$");
    /** Mermaid 图起始行识别 */
    private static final Pattern MERMAID_BLOCK = Pattern.compile("```mermaid\\r?\\n([\\s\\S]+?)```", Pattern.MULTILINE);

    /**
     * 实体抽取结果。
     */
    public record ExtractedEntity(String name, String type, double confidence, String evidenceText) {
        public ExtractedEntity(String name, String type) {
            this(name, type, 0.7, "");
        }
    }

    /**
     * Markdown 解析结果。
     */
    public static class MarkdownParseResult {
        private final List<String> headers = new ArrayList<>();
        private final List<String> mermaidLines = new ArrayList<>();
        private final List<String> keyPoints = new ArrayList<>();
        private final Map<String, Object> extractionMeta = new LinkedHashMap<>();
        private int charCount;

        public List<String> getHeaders() { return headers; }
        public List<String> getMermaidLines() { return mermaidLines; }
        public List<String> getKeyPoints() { return keyPoints; }
        public Map<String, Object> getExtractionMeta() { return extractionMeta; }
        public int getCharCount() { return charCount; }
        public void setCharCount(int v) { this.charCount = v; }
    }

    /**
     * 解析 Markdown 文本。
     *
     * @param markdown 原始文本
     * @return 结构化结果
     */
    public MarkdownParseResult parseMarkdown(String markdown) {
        MarkdownParseResult r = new MarkdownParseResult();
        if (markdown == null || markdown.isEmpty()) {
            r.setCharCount(0);
            r.getExtractionMeta().put("status", "empty");
            return r;
        }
        r.setCharCount(markdown.length());
        r.getExtractionMeta().put("status", "parsed");
        r.getExtractionMeta().put("mermaid_count", 0);

        String[] lines = markdown.split("\\r?\\n");

        // 1. 标题
        for (String line : lines) {
            if (line == null) continue;
            Matcher mh = HEADER_PATTERN.matcher(line);
            if (mh.find()) {
                String text = mh.group(2).trim();
                if (!text.isEmpty() && r.getHeaders().size() < 20) {
                    r.getHeaders().add(text);
                }
            }
        }

        // 2. Mermaid 代码块抽取 graph 边
        Matcher mm = MERMAID_BLOCK.matcher(markdown);
        while (mm.find()) {
            String block = mm.group(1);
            r.getMermaidLines().add(block);
        }
        r.getExtractionMeta().put("mermaid_count", r.getMermaidLines().size());

        // 3. bullet 列表抽取要点
        for (String line : lines) {
            if (line == null) continue;
            String t = line.trim();
            if ((t.startsWith("- ") || t.startsWith("* ")) && t.length() <= 80) {
                String text = t.substring(2).trim();
                if (!text.isEmpty() && r.getKeyPoints().size() < 30) {
                    r.getKeyPoints().add(text);
                }
            }
        }

        log.debug("parseMarkdown: headers={}, mermaid={}, keyPoints={}",
                r.getHeaders().size(), r.getMermaidLines().size(), r.getKeyPoints().size());
        return r;
    }
}
