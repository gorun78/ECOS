package com.chinacreator.gzcm.cli;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * table 输出不得丢数据。
 *
 * <p>缺陷背景（2026-11-11 实测）：{@code data} 为裸列表时 {@code toTable} 只打
 * {@code rows : N}，行内容全部丢失 —— 而 {@code --output table} 的唯一用途就是让人看内容。
 * 空列表应显式报 0 行（与「有行但没渲染」区分开）。</p>
 */
class TableOutputCompletenessTest {

    private static Map<String, Object> envelope(Object data) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("status", "SUCCESS");
        out.put("data", data);
        out.put("error", null);
        out.put("trace_id", "t-1");
        out.put("source_refs", List.of());
        return out;
    }

    @Test
    @DisplayName("裸列表：table 必须渲染每行内容，不能只给行数")
    void bareListRendersRows() {
        Map<String, Object> row1 = new LinkedHashMap<>();
        row1.put("modelId", "m1");
        row1.put("name", "GPT");
        Map<String, Object> row2 = new LinkedHashMap<>();
        row2.put("modelId", "m2");
        row2.put("name", "Claude");
        String text = OutputRenderer.renderToString(envelope(List.of(row1, row2)), "table");
        assertTrue(text.contains("rows   : 2"), "行数应保留: \n" + text);
        assertTrue(text.contains("m1") && text.contains("GPT"), "第 1 行内容丢失: \n" + text);
        assertTrue(text.contains("m2") && text.contains("Claude"), "第 2 行内容丢失: \n" + text);
    }

    @Test
    @DisplayName("空列表：如实报 0 行（不得伪装有数据，也不得因无内容而省略）")
    void emptyListReportsZero() {
        String text = OutputRenderer.renderToString(envelope(List.of()), "table");
        assertTrue(text.contains("rows   : 0"), "空列表应报 0 行: \n" + text);
        assertFalse(text.contains("  - "), "空列表不应有行项: \n" + text);
    }

    @Test
    @DisplayName("非 Map 行元素也要渲染（裸标量列表同样是数据）")
    void scalarRowsAreRendered() {
        String text = OutputRenderer.renderToString(envelope(List.of("alpha", "beta")), "table");
        assertTrue(text.contains("alpha") && text.contains("beta"), "标量行丢失: \n" + text);
    }
}
