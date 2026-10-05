package com.chinacreator.gzcm.ai.wagent.readiness;

import com.chinacreator.gzcm.ai.wagent.narrative.NarrativeGuard;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 分册10 F10-24 叙述数字剥离离线单测：带「万/元/份」尾的数字序列被剥为 {@code <NUM>}，
 * 无数字文本恒等通过（clarify）。
 */
class NarrativeGuardTest {

    /** F10-24：「销售额 12 万元」中的 12 被剥离，结果不含 "12"。 */
    @Test
    void numberInLlmDescriptionStripped() {
        String out = NarrativeGuard.stripNumbersFromLlmText("销售额 12 万元", Set.of());
        assertFalse(out.contains("12"), "结果不得残留数字 12，实际：" + out);
        assertTrue(out.contains(NarrativeGuard.NUM_PLACEHOLDER), "数字序列应被替换为 <NUM> 占位");
    }

    /** F10-24：无任何数字序列的文本恒等返回（含中文/字母/标点）。 */
    @Test
    void clarifyNotStripped() {
        String clarify = "请确认是否继续执行本项目";
        assertEquals(clarify, NarrativeGuard.stripNumbersFromLlmText(clarify, Set.of()));

        String english = "continuing with the revision";
        assertEquals(english, NarrativeGuard.stripNumbersFromLlmText(english, Set.of()));

        // null 安全。
        assertEquals("", NarrativeGuard.stripNumbersFromLlmText(null, Set.of()));
    }
}
