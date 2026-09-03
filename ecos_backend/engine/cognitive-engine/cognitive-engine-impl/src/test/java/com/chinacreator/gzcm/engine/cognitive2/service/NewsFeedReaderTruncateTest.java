package com.chinacreator.gzcm.engine.cognitive2.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Wave-5.1 T-08 — NewsFeedReader 截断行为测试 (任务 5)。
 *
 * <p>既有的 NewsFeedReaderDemoTest 覆盖 happy path (header/mermaid/bullets 抽取)。
 * 本类**补充** truncation 边界:
 * <ol>
 *   <li>headers 上限 20 — 超过不防 ring buffer</li>
 *   <li>keyPoints 上限 30 — bullet>80 字 + 30 条时正确截</li>
 *   <li>extractEntity has confidence=0.7 默认值 (type only 构造)</li>
 * </ol>
 *
 * @author ECOS Cognitive Engine Team
 * @since 2026-09-02 (Wave-5.1)
 */
class NewsFeedReaderTruncateTest {

    // ── headers 上限 ──

    @Test
    @DisplayName("T-08-5-1: parseMarkdown headers 超过 20 时 session 仍取前 20")
    void headersCappedAtTwenty() {
        StringBuilder md = new StringBuilder();
        for (int i = 1; i <= 25; i++) {
            md.append("# Header-").append(i).append("\n\n");
        }
        NewsFeedReader.MarkdownParseResult r = new NewsFeedReader().parseMarkdown(md.toString());

        assertEquals(20, r.getHeaders().size(), "headers 上限 20, 不应超");
        assertEquals("Header-1", r.getHeaders().get(0));
        assertEquals("Header-20", r.getHeaders().get(19));
    }

    // ── keyPoints 上限 ──

    @Test
    @DisplayName("T-08-5-2: parseMarkdown keyPoints 超过 30 时只取前 30")
    void keyPointsCappedAtThirty() {
        StringBuilder md = new StringBuilder();
        for (int i = 1; i <= 35; i++) {
            md.append("- point-").append(i).append(" it is a short bullet\n");
        }
        NewsFeedReader.MarkdownParseResult r = new NewsFeedReader().parseMarkdown(md.toString());

        assertEquals(30, r.getKeyPoints().size(), "keyPoints 上限 30");
        assertEquals("point-1 it is a short bullet", r.getKeyPoints().get(0));
    }

    // ── bullet 截断: >80 字跳过 ──

    @Test
    @DisplayName("T-08-5-3: bullet 长度 > 80 字 → 不抽 keyPoints")
    void longBulletsSkipped() {
        String md = "- " + "x".repeat(120) + "\n";
        NewsFeedReader.MarkdownParseResult r = new NewsFeedReader().parseMarkdown(md);

        assertTrue(r.getKeyPoints().isEmpty(), ">80 字 bullet 应跳过");
    }

    // ── extractEntity 默认 confidence ──

    @Test
    @DisplayName("T-08-5-4: ExtractedEntity type-only 构造 → confidence=0.7, evidenceText 空")
    void extractedEntityDefaultConfidenceAndEvidence() {
        NewsFeedReader.ExtractedEntity e = new NewsFeedReader.ExtractedEntity("客户A", "CUSTOMER");

        assertEquals("客户A", e.name());
        assertEquals("CUSTOMER", e.type());
        assertEquals(0.7, e.confidence(), 0.001);
        assertEquals("", e.evidenceText());
    }

    // ── 多 mermaid 块累加 ──

    @Test
    @DisplayName("T-08-5-5: 多个 mermaid 代码块 → mermaidLines 累加 (no truncate)")
    void multipleMermaidBlocksAccumulate() {
        String md = """
                # T
                ```mermaid
                graph TD
                A --> B
                ```
                ```mermaid
                graph LR
                X --> Y
                ```
                """;
        NewsFeedReader.MarkdownParseResult r = new NewsFeedReader().parseMarkdown(md);
        assertEquals(2, r.getMermaidLines().size());
        assertEquals(2, ((Number) r.getExtractionMeta().get("mermaid_count")).intValue());
    }
}
