package com.chinacreator.gzcm.ai.wagent.orchestrator;

/**
 * 分册10 F10-18 · 重放一致性：同 {@code input_hash} 必产同 {@code output_hash}（确定性步）。
 *
 * <p>用于 Step 重放幂等对账：同一输入被两次执行（如恢复续跑、并发重复）时，
 * 输出哈希必须恒等；否则说明步骤非确定 ⇒ 违反 E-WA-STATE（状态不可重放）。
 * 禁引入时间/随机进哈希；LLM 型步的确定性由 prompt+seed 冻结保证（llm-gateway 侧）。</p>
 */
public final class ReplayConsistency {

    private ReplayConsistency() {}

    /**
     * 校验同一 input_hash 的两次 output_hash 是否一致。
     *
     * @param inputHash   输入哈希（两次执行必须同一）
     * @param outputHash1 第 1 次输出哈希
     * @param outputHash2 第 2 次输出哈希
     * @return 一致时返回该 output_hash
     * @throws IllegalStateException E-WA-STATE：input 相同而 output 分歧
     */
    public static String verify(String inputHash, String outputHash1, String outputHash2) {
        if (inputHash == null) throw new NullPointerException("inputHash null");
        if (outputHash1 == null || outputHash2 == null) {
            throw new IllegalStateException("E-WA-STATE: output hash null for input " + inputHash);
        }
        if (!outputHash1.equals(outputHash2)) {
            throw new IllegalStateException(
                    "E-WA-STATE: non-deterministic step — same input_hash " + inputHash
                            + " produced divergent output (" + outputHash1 + " vs " + outputHash2 + ")");
        }
        return outputHash1;
    }
}
