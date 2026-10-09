package com.chinacreator.gzcm.engine.ai.replay;

import com.chinacreator.gzcm.common.trace.AgentLoopTrace;
import com.chinacreator.gzcm.engine.ai.service.AgentTracer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 详细设计-06 F06-08 验收 —— 一次运行的全部步骤可按 traceId 从日志重建时序。
 *
 * <p>doc 冻结的三个标识：</p>
 * <ul>
 *   <li>{@code stepsReconstructedInOrderWithLatencyAndTokens} —— 时序 plan→tool→llm→final
 *       与耗时/token 逐步骤还原，含重试记录；</li>
 *   <li>{@code replaySurvivesProcessRestart} —— 重建输入只有磁盘上的结构化行，
 *       <b>断言 {@code AgentTracer} 内存为空时回放仍成立</b>（消灭 X-36 进程内 map 形态）；</li>
 *   <li>{@code replayResponseOmitsRawToolOutput} —— 响应面结构上不存在消息原文、
 *       工具入参与工具结果（禁回显脱敏面）。</li>
 * </ul>
 *
 * <p>本类不新建回放表（X-58 全库无 replay 字段，本册确认无需引入），
 * 测试用日志文件即 {@code ECOS_AGENT_LOOP} 文件 appender 的落地形态。</p>
 */
class RunReplayTest {

    private static final String TRACE = "tr-9f2c4b1a7d6e5f01";
    private static final String OTHER_TRACE = "tr-0000000000000000";
    private static final String RAW_TOOL_OUTPUT = "合同金额明细 1,234,567.89 元，客户电话 13800001111";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("F06-08 要点：按 traceId 从日志重建步骤时序，含耗时、token 与重试记录")
    void stepsReconstructedInOrderWithLatencyAndTokens(@TempDir Path logDir) throws IOException {
        writeChannelFile(logDir.resolve("agent-loop.log"), lines());

        RunReplayView view = service(logDir).replay(TRACE);

        assertEquals(RunReplayView.FOUND, view.status(), String.valueOf(view));
        assertEquals(TRACE, view.traceId());
        assertEquals("forecast-analyst", view.agentId());
        assertEquals(List.of("agent-loop-log"), view.sources());

        List<String> order = view.steps().stream().map(RunReplayView.Step::op).toList();
        assertEquals(List.of("plan", "llm_call", "tool_call", "tool_result", "final"), order,
                "步骤必须按时间序重建（同一步的多个 op 按写入序）");

        RunReplayView.Step plan = view.steps().get(0);
        assertEquals(0, plan.step());
        assertEquals(5L, plan.latencyMs());
        assertEquals("ALLOW", plan.decision());
        assertTrue(plan.retries().isEmpty(), "无重试时 retries 是空数组而非 null");

        RunReplayView.Step llm = view.steps().get(1);
        assertEquals(1, llm.step());
        assertEquals("deepseek-chat", llm.model());
        assertEquals(900L, llm.latencyMs());
        assertEquals(1200L, llm.promptTokens());
        assertEquals(300L, llm.completionTokens());
        assertEquals(1, llm.retries().size(), "同步骤第二次 llm_call 即格式错误重试（Loop 每尝试一行）");
        RunReplayView.Attempt retry = llm.retries().get(0);
        assertEquals(2, retry.attempt());
        assertEquals(850L, retry.latencyMs());
        assertNull(retry.promptTokens(), "重试行 token 为 -1 → 回放标缺失，禁渲染成 0");

        RunReplayView.Step tool = view.steps().get(2);
        assertEquals("query_db", tool.tool());
        assertEquals(40L, tool.latencyMs());

        RunReplayView.Step toolResult = view.steps().get(3);
        assertEquals("query_db", toolResult.tool());
        assertNull(toolResult.latencyMs(), "-1 必须还原成缺失态");

        RunReplayView.Totals totals = view.totals();
        assertEquals(1200L, totals.promptTokens(), "token 只累计 llm_call 行");
        assertEquals(300L, totals.completionTokens());
        assertEquals(2, totals.llmCalls(), "含重试共两次真实网关调用");
        assertEquals(2900L, totals.latencyMs(), "整次运行耗时取 final 行（禁步内耗时求和冒充实测总耗时）");
        assertEquals(5, view.stepCount());
    }

    @Test
    @DisplayName("F06-08 消灭 X-36：AgentTracer 内存为空时，新实例（等价进程重启）仍能重建")
    void replaySurvivesProcessRestart(@TempDir Path logDir) throws IOException {
        writeChannelFile(logDir.resolve("agent-loop.log"), lines());

        // 本"进程"从未跑过 Loop —— 内存视图必然为空；回放却必须成立
        assertTrue(AgentTracer.getTrace(TRACE).isEmpty(),
                "前提断言：内存调试视图无该 trace（若此处非空说明测试装置污染）");
        assertEquals(RunReplayView.FOUND, service(logDir).replay(TRACE).status());

        // 换一个完全新建的读取端 + 服务端实例（模拟进程重启后重新起 JVM）
        RunReplayView after = new RunReplayService(new AgentLoopLogSource(env(logDir))).replay(TRACE);

        assertEquals(RunReplayView.FOUND, after.status());
        assertEquals(5, after.steps().size());
        assertEquals(List.of("plan", "llm_call", "tool_call", "tool_result", "final"),
                after.steps().stream().map(RunReplayView.Step::op).toList());
        assertTrue(AgentTracer.getTrace(TRACE).isEmpty(),
                "回放不得回填 AgentTracer 内存（它只是本地调试视图）");
    }

    @Test
    @DisplayName("F06-08 脱敏：响应里既无工具结果原文也无消息/入参字段")
    void replayResponseOmitsRawToolOutput(@TempDir Path logDir) throws IOException {
        List<String> lines = new java.util.ArrayList<>(lines());
        // 原文只可能出现在业务日志面；结构化行本身不带，回放端也不得凭空补
        lines.add("2026-10-09T10:00:02.060 [main] INFO com.chinacreator.gzcm.engine.ai.service"
                + ".ToolExecutorService : tool result = " + RAW_TOOL_OUTPUT);
        writeChannelFile(logDir.resolve("agent-loop.log"), lines);

        RunReplayView view = service(logDir).replay(TRACE);
        String json = objectMapper.writeValueAsString(view);

        assertFalse(json.contains(RAW_TOOL_OUTPUT), "禁回显工具结果原文: " + json);
        assertFalse(json.contains("content"), "响应面不得出现消息/结果原文字段: " + json);
        assertFalse(json.contains("arguments"), "响应面不得出现工具入参: " + json);

        Set<String> stepFields = Arrays.stream(RunReplayView.Step.class.getRecordComponents())
                .map(c -> c.getName()).collect(Collectors.toSet());
        assertEquals(Set.of("op", "step", "tool", "model", "latencyMs",
                "promptTokens", "completionTokens", "decision", "retries"), stepFields,
                "步骤字段集必须等于本册接口契约（结构上无原文字段）");
    }

    @Test
    @DisplayName("F06-08 失败可见性：通道未就绪与 trace 不存在是两个态，禁折叠成空时间线")
    void unavailableChannelAndUnknownTraceAreDistinctStates(@TempDir Path logDir) throws IOException {
        Path emptyDir = logDir.resolve("no-appender");
        Files.createDirectories(emptyDir);

        RunReplayView noChannel = service(emptyDir).replay(TRACE);
        assertEquals(RunReplayView.NO_LOG_SOURCE, noChannel.status());
        assertEquals(0, noChannel.steps().size());

        writeChannelFile(logDir.resolve("agent-loop.log"), lines());
        RunReplayView unknown = service(logDir).replay("tr-not-exists-0000000000000000000000");
        assertEquals(RunReplayView.NOT_FOUND, unknown.status(), "通道就绪但无该 trace ⇒ NOT_FOUND");

        // 越界 traceId（超长）按参数非法处理，不去扫日志
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> service(logDir).replay("x".repeat(65)));
    }

    // ── 装置 ────────────────────────────────────────────────────────────────

    private RunReplayService service(Path logDir) {
        return new RunReplayService(new AgentLoopLogSource(env(logDir)));
    }

    private MockEnvironment env(Path logDir) {
        return new MockEnvironment().withProperty(AgentLoopLogSource.LOG_PATH_PROPERTY, logDir.toString());
    }

    /** 与 {@code ECOS_AGENT_LOOP} 文件 appender 同 pattern：{@code %d{ISO8601.millis} %msg%n}。 */
    private static List<String> lines() {
        return List.of(
                ts("10:00:00.100") + line("plan", 0, null, 5, AgentLoopTrace.Decision.ALLOW,
                        TRACE, -1, -1),
                ts("10:00:01.100") + line("llm_call", 1, null, 900, AgentLoopTrace.Decision.ALLOW,
                        TRACE, 1200, 300),
                ts("10:00:01.200") + line("llm_call", 1, null, 850, AgentLoopTrace.Decision.NA,
                        TRACE, -1, -1),
                ts("10:00:02.000") + line("tool_call", 1, "query_db", 40, AgentLoopTrace.Decision.ALLOW,
                        TRACE, -1, -1),
                ts("10:00:02.050") + line("tool_result", 1, "query_db", -1, AgentLoopTrace.Decision.ALLOW,
                        TRACE, -1, -1),
                ts("10:00:03.000") + line("final", 2, null, 2900, AgentLoopTrace.Decision.ALLOW,
                        TRACE, -1, -1),
                // 另一条链路 + 一行自由文本：必须被检索键与标记过滤掉
                ts("10:00:03.100") + line("llm_call", 1, null, 700, AgentLoopTrace.Decision.ALLOW,
                        OTHER_TRACE, 50, 10),
                "2026-10-09T10:00:03.200 [main] INFO com.example.Noise : 与回放无关的业务日志");
    }

    private static String ts(String time) {
        return "2026-10-09T" + time + " ";
    }

    /** 用行契约自己的构造器生成（禁在测试里手写行文本，否则字段序一改就假绿）。 */
    private static String line(String opWire, int step, String tool, long latencyMs,
                               AgentLoopTrace.Decision decision, String traceId,
                               long promptTokens, long completionTokens) {
        return AgentLoopTrace.line(AgentLoopTrace.Op.fromWire(opWire), step)
                .agent("forecast-analyst")
                .tool(tool)
                .model("deepseek-chat")
                .tokens(promptTokens, completionTokens)
                .latencyMs(latencyMs)
                .decision(decision)
                .trace(traceId)
                .render();
    }

    private static void writeChannelFile(Path file, List<String> lines) throws IOException {
        Files.write(file, lines, StandardCharsets.UTF_8);
    }
}
