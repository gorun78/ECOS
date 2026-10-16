package com.chinacreator.gzcm.common.security.decision;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 三通道裁决入口「通道词表单源」用例（#142/B4-3d-2，差距 D-B4-27）。
 *
 * <p><b>缺陷本体</b>：{@code DecisionController#parsePurpose} 此前把<b>任何</b>认不出的
 * {@code purpose} 字符串静默映射成 {@code Purpose.PAGE}。于是"导出/AI 通道"的调用一旦词面失配
 * （拼错、被改名、多了个非 ASCII 空格），拿到的是 <b>200 + 页面通道语义的裁决束</b>：
 * 护栏按页面档评估、导出水印义务不适用，而调用方以为自己走的是导出。这属 fail-open 中
 * 最难发现的一类——伪装成成功（与 D-B4-14「OPA 从未被咨询」同一形态族）。</p>
 *
 * <p>本类把"认不出"钉成<b>可区分的两种结果</b>：</p>
 * <ul>
 *   <li>{@code null}/空白 ⇒ {@code null}（入口按既约定默认页面通道，API 只增不改）；</li>
 *   <li>非空白但未知名 ⇒ {@code null} 且入口必须转 400（由 {@code BundleColumnSurfaceTest}
 *       在入口侧钉住"未知名不回落页面"）。</li>
 * </ul>
 *
 * <p>因此"缺失"与"未识别"在本契约里返回值相同（都是 {@code null}），<b>区分点</b>是
 * {@link DecisionChannelContract#normalize} 的入参是否空白——用例把这条口径显式钉住，
 * 防止后来者把两者折成一支（那会让 400 面消失）。</p>
 */
@DisplayName("裁决通道词表：三词单源 + 未识别不回落页面 + 缺失才默认 PAGE")
class DecisionChannelContractTest {

    // ═══════════════ 1. 词表本体 ═══════════════

    @Test
    @DisplayName("词表恰为 page/export/ai 三词，字面量与常量一致（改名唯一对照点）")
    void vocabularyIsExactlyTheThreeChannels() {
        List<String> known = DecisionChannelContract.known();

        assertEquals(3, known.size(), "通道数不得增删而不登记（详设-01 C.1 三通道是判定表的分维）");
        assertEquals(List.of("page", "export", "ai"), known);
        assertEquals(DecisionChannelContract.PURPOSE_PAGE, known.get(0));
        assertEquals(DecisionChannelContract.PURPOSE_EXPORT, known.get(1));
        assertEquals(DecisionChannelContract.PURPOSE_AI, known.get(2));
    }

    @Test
    @DisplayName("词表不可变（消费侧排序/篡改会制造第二套真值）")
    void vocabularyIsUnmodifiable() {
        List<String> known = DecisionChannelContract.known();
        assertThrows(UnsupportedOperationException.class, () -> known.set(0, "whatever"),
                "known() 可被就地改写＝词表存在第二份可变真值");
    }

    // ═══════════════ 2. 归一化：大小写/空白容忍，但不容忍未知名 ═══════════════

    @Test
    @DisplayName("大写/带空白的合法词归一为规范小写词（入口对既调用方保持兼容）")
    void normalizeAcceptsCaseAndPaddingVariants() {
        assertEquals("page", DecisionChannelContract.normalize("PAGE"));
        assertEquals("export", DecisionChannelContract.normalize(" Export "));
        assertEquals("ai", DecisionChannelContract.normalize("AI"));
        for (String word : DecisionChannelContract.known()) {
            assertTrue(DecisionChannelContract.isKnown(DecisionChannelContract.normalize(word)));
        }
    }

    @Test
    @DisplayName("null 与各种空白串都归为「未提供」（同返回值，与「未识别」在入口侧分态）")
    void absentOrBlankIsNotUnrecognized() {
        assertNull(DecisionChannelContract.normalize(null));
        assertNull(DecisionChannelContract.normalize(""));
        assertNull(DecisionChannelContract.normalize("   "));
        assertNull(DecisionChannelContract.normalize("\t \n"));
    }

    @Test
    @DisplayName("未识别的通道词返回 null——调用方必须拒绝，禁在此处回落 page")
    void unknownChannelDoesNotSilentlyBecomePage() {
        // 「exprt」= 拼错；「download」= 改名；「pages」= 复数漂移；三者是现实中最常见的失配形态。
        // 注意不能把带空白的合法词放进来——normalize 的 trim 是有意兼容（见上一用例）。
        for (String raw : List.of("exprt", "download", "pages", "export2", "page/export", "purpose")) {
            String normalized = DecisionChannelContract.normalize(raw);
            assertNull(normalized, "未识别词 " + raw + " 被归一成功＝静默降级回潮");
            assertFalse(DecisionChannelContract.PURPOSE_PAGE.equals(normalized),
                    "未识别词 " + raw + " 绝不可等价于 page（那正是 D-B4-27 的 fail-open）");
        }
        // 正向对照：证明上面不是「normalize 恒返回 null」造成的假绿
        assertEquals("page", DecisionChannelContract.normalize("page"));
    }

    @Test
    @DisplayName("normalize 幂等且 isKnown 与之自洽（门禁可用任一支判定规范形态）")
    void normalizeIsIdempotentAndConsistentWithIsKnown() {
        for (String raw : List.of("  PAGE", "export", "AI")) {
            String once = DecisionChannelContract.normalize(raw);
            assertNotNull(once);
            assertEquals(once, DecisionChannelContract.normalize(once));
            assertTrue(DecisionChannelContract.isKnown(once));
        }
        assertFalse(DecisionChannelContract.isKnown(null));
        assertFalse(DecisionChannelContract.isKnown("Page")); // 规范形态是小写，大小写脏值不算已知
    }

    // ═══════════════ 3. 400 理由串：可机读 + 自带词表 ═══════════════

    @Test
    @DisplayName("未识别理由带机读前缀、回显实际收到的词并列出合法词表")
    void unrecognizedReasonIsMachineReadableAndActionable() {
        String reason = DecisionChannelContract.unrecognizedReason("  exprt ");

        assertTrue(reason.startsWith(DecisionChannelContract.REASON_UNRECOGNIZED),
                "理由必须带机读前缀，消费方日志要能区分「我传错词」与「security 内部故障」，实际=" + reason);
        assertTrue(reason.contains("exprt"), "必须回显实际收到的词，静默拒绝等于让调用方猜拼写");
        for (String word : DecisionChannelContract.known()) {
            assertTrue(reason.contains(word), "错误 message 必须列出合法词表，实际=" + reason);
        }
    }

    @Test
    @DisplayName("null 入参不抛异常（理由串构造面不得成为新崩溃点）")
    void unrecognizedReasonToleratesNull() {
        String reason = DecisionChannelContract.unrecognizedReason(null);
        assertTrue(reason.startsWith(DecisionChannelContract.REASON_UNRECOGNIZED));
    }
}
