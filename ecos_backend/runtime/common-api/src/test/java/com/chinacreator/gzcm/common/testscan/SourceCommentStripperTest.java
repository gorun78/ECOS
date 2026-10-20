package com.chinacreator.gzcm.common.testscan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link SourceCommentStripper} 的判据自证（差距 <b>D-B4-40</b>，#142/B4-3e-5）。
 *
 * <p>本类只回答一个问题：剥离器自己对"字面量里的 // 不是注释"这件事是否真的成立。
 * 门禁的"零违例"结论全部建立在这一条上 —— 剥离器一旦失明，消费集枚举与快照计数
 * 会一起变成不可信的绿。</p>
 */
@DisplayName("源码门禁剥离器：引号/文本块感知，且反面证据仍成立")
class SourceCommentStripperTest {

    /** D-B4-40 的原始复现行：evaluate 咽喉 URL 写在字符串字面量里。 */
    private static final String URL_CALL =
            "return restTemplate.postForObject(\"http://localhost:8080/api/v1/"
                    + "policy-engine/evaluate\", params, Object.class);";

    @Test
    @DisplayName("字面量内的 URL 不被吃掉（朴素口径会吃掉）")
    void urlLiteralSurvivesAwareStrip() {
        String stripped = SourceCommentStripper.strip(URL_CALL);
        assertTrue(stripped.contains("policy-engine/evaluate"),
                "引号感知剥离失灵 ⇒ 消费集枚举会漏掉整支消费方（D-B4-40 的成因）");
        assertTrue(stripped.contains("postForObject"), "代码本体必须留在剥离结果里");
    }

    @Test
    @DisplayName("反面证据：朴素口径确实吃掉同一行")
    void naiveStripStillBlindsTheUrlLiteral() {
        String naive = SourceCommentStripper.naiveStrip(URL_CALL);
        assertFalse(naive.contains("policy-engine/evaluate"),
                "反面证据失效：正则不再吃掉 URL ⇒ D-B4-40 的登记依据需要重写");
        // 同一条模式串必须仍是历史门禁沿用的那条，否则本类的对照物已换题
        assertEquals("//" + "[^" + "\\n" + "]*", SourceCommentStripper.naiveLinePattern(),
                "naiveLinePattern 不再是同族门禁沿用的形态");
        assertFalse(URL_CALL.replaceAll(SourceCommentStripper.naiveLinePattern(), " ")
                .contains("policy-engine/evaluate"), "以模式串单独复算也必须重现盲区");
        assertTrue(SourceCommentStripper.strip(URL_CALL)
                .contains("policy-engine/evaluate"), "同一行在引号感知口径下必须可见");
    }

    @Test
    @DisplayName("真注释（行注释与 Javadoc）仍被剥离，禁词不误报")
    void realCommentsAreStripped() {
        String src = "/** 现契约 data 段发 \"allow\" 键 */\n"
                + "// allow 只在注释出现\n"
                + "boolean ok = Contract.isExplicitAllow(d); // 行尾注释里的 allow\n";
        String stripped = SourceCommentStripper.strip(src);
        assertTrue(stripped.contains("Contract.isExplicitAllow"), "代码本体必须保留");
        assertFalse(stripped.contains("只在注释出现"), "行注释未被剥离");
        assertFalse(stripped.contains("现契约"), "Javadoc 未被剥离");
        assertFalse(stripped.contains("行尾注释里的 allow"), "行尾注释未被剥离");
    }

    @Test
    @DisplayName("换行数守恒：违例定位仍指向原行号")
    void newLineCountIsPreserved() {
        String src = "int a = 1; /* 块注释\n * 跨三行\n */ int b = 2;\n// 行注释\nint c = 3;\n";
        long before = src.chars().filter(ch -> ch == '\n').count();
        long after = SourceCommentStripper.strip(src).chars().filter(ch -> ch == '\n').count();
        assertEquals(before, after, "剥离器不得压缩换行，否则违例行号失真");
    }

    @Test
    @DisplayName("字符字面量里的引号不破坏状态机")
    void charLiteralWithEmbeddedQuoteKeepsFollowingCodeVisible() {
        String src = "char q = '\"';\nString u = \"http://x/policy-engine/evaluate\";\n";
        String stripped = SourceCommentStripper.strip(src);
        assertTrue(stripped.contains("policy-engine/evaluate"),
                "字符字面量后的字符串被错认 ⇒ 剥离器状态机不可信");
    }

    @Test
    @DisplayName("文本块整体保留，其后的真代码不被当字面量吞掉")
    void textBlockKeepsFollowingCodeVisible() {
        String src = "String sql = \"\"\"\n SELECT 1 -- 文本块里的 // 不是注释\n\"\"\";\n"
                + "String u = \"http://x/policy-engine/evaluate\";\n";
        String stripped = SourceCommentStripper.strip(src);
        assertTrue(stripped.contains("policy-engine/evaluate"),
                "三引号未单独识别 ⇒ 状态机跨块错认，后面的真代码会被当成字面量留下");
        assertTrue(stripped.contains("String sql"), "文本块起始声明必须保留");
    }

    @Test
    @DisplayName("注释定界符出现在字面量内时不得开启注释状态")
    void blockCommentMarkerInsideStringDoesNotStartComment() {
        String src = "String s = \"a/*b\";\nString needle = \"ready_to_sync\";\n";
        assertTrue(SourceCommentStripper.strip(src).contains("ready_to_sync"),
                "字面量里的 /* 被当块注释起点 ⇒ 后面整段代码被抹平（同型盲区）");
    }
}
