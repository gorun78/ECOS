package com.chinacreator.gzcm.engine.ai.replay;

import java.util.List;

/**
 * 运行回放视图（详细设计-06 F06-08；响应 shape 见本册 §接口契约的
 * {@code {op,step,tool,model,latencyMs,promptTokens,completionTokens,decision,retries[]}}）。
 *
 * <p>脱敏纪律：本视图<b>结构上不存在</b>消息原文、工具入参与工具结果字段 ——
 * 回放只依赖 {@code EVT=AgentLoop} 结构化行，行文本本身不含原文（F06-06 要点 3），
 * 因此"禁在响应里回显工具结果原文"由字段集保证而非运行时裁剪。</p>
 *
 * <p>缺失语义：{@code Long} 为 {@code null} 即"该项未上报"（日志行里的 {@code -1}），
 * <b>禁渲染成 0</b>（X-38/X-68 同纪律），前端据此显示"未上报"。</p>
 */
public record RunReplayView(String status,
                            String traceId,
                            String agentId,
                            Integer stepCount,
                            Boolean partial,
                            List<String> sources,
                            Totals totals,
                            List<Step> steps) {

    /** 运行成功重建。 */
    public static final String FOUND = "FOUND";

    /** 通道可用但该 traceId 无行（日志滚动清除、traceId 拼错、跨进程未共享日志面）。 */
    public static final String NOT_FOUND = "NOT_FOUND";

    /** 日志通道未就绪（未配文件 appender 或目录不可读）—— 显式对外表达，禁伪 200 空数组。 */
    public static final String NO_LOG_SOURCE = "NO_LOG_SOURCE";

    /** 回放数据来源（逻辑通道名，禁外泄服务器绝对路径）。 */
    public static final List<String> SOURCE_AGENT_LOOP_LOG = List.of("agent-loop-log");

    /** 整次运行的累计值；全无可信值时为 null（不当 0 冒充实测）。 */
    public record Totals(Long promptTokens, Long completionTokens, Long latencyMs, Integer llmCalls) {
    }

    /**
     * 一个步骤。
     *
     * @param retries 同一步骤/同一 op/同一工具的<b>后续尝试</b>（首次尝试即本对象自身），
     *                无重试时为空数组而非 null（F06-08 要点"含失败重试记录"）
     */
    public record Step(String op,
                       Integer step,
                       String tool,
                       String model,
                       Long latencyMs,
                       Long promptTokens,
                       Long completionTokens,
                       String decision,
                       List<Attempt> retries) {
    }

    /** 一次重试尝试（attempt 从 2 起：1 即 {@link Step} 自身）。 */
    public record Attempt(Integer attempt,
                          String model,
                          Long latencyMs,
                          Long promptTokens,
                          Long completionTokens,
                          String decision) {
    }

    /** 通道不可用态（无步骤、无累计值）。 */
    public static RunReplayView noLogSource(String traceId) {
        return new RunReplayView(NO_LOG_SOURCE, traceId, null, 0, Boolean.TRUE,
                List.of(), null, List.of());
    }

    /** 通道就绪但该 trace 无行。 */
    public static RunReplayView notFound(String traceId, List<String> sources) {
        return new RunReplayView(NOT_FOUND, traceId, null, 0, Boolean.FALSE,
                sources, null, List.of());
    }
}
