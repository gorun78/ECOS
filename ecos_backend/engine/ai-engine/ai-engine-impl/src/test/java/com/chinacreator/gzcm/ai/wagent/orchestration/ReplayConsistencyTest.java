package com.chinacreator.gzcm.ai.wagent.orchestration;

import com.chinacreator.gzcm.ai.wagent.orchestrator.ReplayConsistency;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 分册10 F10-18 重放一致性离线单测：同 {@code input_hash} 两次 output_hash 一致放行并回传该 hash，
 * 不一致 ⇒ E-WA-STATE（非确定性步违反可重放）。
 */
class ReplayConsistencyTest {

    /** F10-18：同输入哈希两次产出相同输出哈希 ⇒ 验证通过并回传该 hash。 */
    @Test
    void sameInputHashYieldsSameOutputHash() {
        String inputHash = "input-hash-A";
        String out = "output-hash-same";
        // 同一 (input, tool, prompt) 组合跨 (tool, prompt_ver) 仍由 input_hash 主导校验。
        assertEquals(out, ReplayConsistency.verify(inputHash, out, out),
                "同 input_hash 两次同 output_hash ⇒ 回传该 hash");

        // 不同 input_hash 与同 output 组合：verify 语义只校验 output 两侧一致（同 input 内），
        // 因此不同输入也应自洽通过（各自的两次 output 相同）。
        assertEquals("out-B", ReplayConsistency.verify("input-hash-B", "out-B", "out-B"));
    }

    /** F10-18：同 input_hash 两次 output 分歧 ⇒ E-WA-STATE（非确定性步）。 */
    @Test
    void sameInputHashDifferentOutputRejected() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> ReplayConsistency.verify("input-hash-A", "out-1", "out-2"));
        assertTrue(ex.getMessage().contains("E-WA-STATE"),
                "输出分歧必须抛 E-WA-STATE，实际：" + ex.getMessage());
    }
}
