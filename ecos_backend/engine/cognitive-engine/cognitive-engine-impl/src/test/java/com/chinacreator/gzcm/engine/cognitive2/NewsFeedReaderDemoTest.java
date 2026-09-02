package com.chinacreator.gzcm.engine.cognitive2;

import com.chinacreator.gzcm.engine.cognitive2.service.NewsFeedReader;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Wave-3.2 T7 — NewsFeedReader Demo 端点全链路第一环测试。
 *
 * <p>不用 LLM 的情况下就能跑通：Markdown → headers/mermaid/bullets。
 */
class NewsFeedReaderDemoTest {

    private final NewsFeedReader reader = new NewsFeedReader();

    private static final String MD = """
            # 销售收入下滑事故复盘

            ## 一、现象
            - 5 月营收同比下降 12%
            - 主力 SKU 在华东区域出货减少
            - 客户回款周期从 45 天延伸到 70 天

            ## 二、根因链路
            ```mermaid
            graph TD
            A[营收下降] --> B[SKU 出货减少]
            B --> C[渠道库存积压]
            B --> D[客户回款放缓]
            D --> E[资金缺口]
            ```

            ## 三、行动
            - 复核渠道库存
            - 拉通销售/财务例会
            """;

    @Test
    void parsesHeaderMermaidAndBullets() {
        NewsFeedReader.MarkdownParseResult r = reader.parseMarkdown(MD);

        // 标题
        assertNotNull(r.getHeaders());
        assertTrue(r.getHeaders().contains("销售收入下滑事故复盘"));
        assertTrue(r.getHeaders().size() >= 3);

        // Mermaid 块
        assertEquals(1, r.getMermaidLines().size());
        String block = r.getMermaidLines().get(0);
        assertTrue(block.contains("graph"));
        assertTrue(block.contains("A["));
        assertTrue(block.contains("-->"));
        assertEquals(1, ((Number) r.getExtractionMeta().get("mermaid_count")).intValue());

        // bullets
        List<String> keyPoints = r.getKeyPoints();
        assertTrue(keyPoints.size() >= 3);
        boolean foundDeviation = keyPoints.stream()
                .anyMatch(s -> s.contains("12%") || s.contains("下降"));
        boolean foundCausal = keyPoints.stream().anyMatch(s -> s.contains("回款"));
        assertTrue(foundDeviation, "应抽到下降/12% 要点");
        assertTrue(foundCausal, "应抽到回款要点");

        // 统计
        assertTrue(r.getCharCount() > 100);
        assertEquals("parsed", r.getExtractionMeta().get("status"));
    }

    @Test
    void emptyInputGraceful() {
        NewsFeedReader.MarkdownParseResult r = reader.parseMarkdown("");
        assertEquals(0, r.getCharCount());
        assertTrue(r.getHeaders().isEmpty());
        assertTrue(r.getMermaidLines().isEmpty());
    }

    @Test
    void parsesRootCauseCausalEdgesForDemo() {
        NewsFeedReader.MarkdownParseResult r = reader.parseMarkdown(MD);
        // 直接断言 mermaid 块内容覆盖关键因果词
        List<String> mermaidLines = r.getMermaidLines();
        assertEquals(1, mermaidLines.size(), "应抽到 1 个 mermaid 块");
        String block = mermaidLines.get(0);
        // 因果链 4 段：营收→SKU→渠道/回款→资金
        assertTrue(block.contains("营收"), "mermaid 不含营收");
        assertTrue(block.contains("SKU"), "mermaid 不含 SKU");
        assertTrue(block.contains("回款"), "mermaid 不含回款");
        assertTrue(block.contains("资金"), "mermaid 不含资金");
        // 边数（--> - 出现数）>= 3
        int arrowCount = 0;
        for (int i = 0; i + 3 <= block.length(); i++) {
            if (block.charAt(i) == '-' && block.charAt(i + 1) == '-' && block.charAt(i + 2) == '>') {
                arrowCount++;
                i += 2;
            }
        }
        assertTrue(arrowCount >= 3, "mermaid 边数应 >= 3, 实际=" + arrowCount);
    }
}
