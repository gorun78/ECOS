package com.chinacreator.gzcm.engine.ai.replay;

import com.chinacreator.gzcm.common.trace.AgentLoopTrace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 运行回放重建服务（详细设计-06 F06-08）。
 *
 * <p>时序重建完全来自 {@link AgentLoopLogSource}（日志面），
 * <b>不读 {@code AgentTracer} 内存视图</b> —— 后者只是本地调试辅助，进程重启即失（X-36），
 * 而本服务的输入是磁盘上的结构化行，故 {@code replaySurvivesProcessRestart} 成立。</p>
 *
 * <p>重试识别：F06-06 固定字段序不允许新增"第几次尝试"字段，因此约定
 * <b>同一步骤 + 同一 op + 同一工具</b>的多条行即多次尝试，首行为该步骤本体，
 * 其余按出现序进 {@code retries[]}（Loop 的格式错误重试路径正是每尝试一行 llm_call）。</p>
 */
@Service
public class RunReplayService {

    private static final Logger log = LoggerFactory.getLogger(RunReplayService.class);

    /** traceId 长度上限（UUID 36，留余量给外部系统串）；只用作等值比对，不入 SQL/路径。 */
    static final int MAX_TRACE_ID_LENGTH = 64;

    private final AgentLoopLogSource logSource;

    public RunReplayService(AgentLoopLogSource logSource) {
        this.logSource = logSource;
    }

    /**
     * 按 traceId 重建一次运行。
     *
     * @param traceId 诊断码（MDC {@code traceId} / {@code X-Request-Id} 同源值）
     * @return FOUND / NOT_FOUND / NO_LOG_SOURCE 三态视图，永不返回 null
     */
    public RunReplayView replay(String traceId) {
        if (traceId == null || traceId.isBlank() || traceId.length() > MAX_TRACE_ID_LENGTH) {
            throw new IllegalArgumentException("traceId 非法（必填且长度 ≤" + MAX_TRACE_ID_LENGTH + "）");
        }
        AgentLoopLogSource.ScanResult scan = logSource.scan();
        if (!scan.sourceAvailable()) {
            log.warn("[F06-08] 回放通道未就绪，返回 NO_LOG_SOURCE: trace={}（前端须显式提示而非空时间线）", traceId);
            return RunReplayView.noLogSource(traceId);
        }
        List<AgentLoopTrace.Line> matched = new ArrayList<>();
        for (AgentLoopTrace.Line line : scan.lines()) {
            if (traceId.equals(line.traceId())) {
                matched.add(line);
            }
        }
        if (matched.isEmpty()) {
            return RunReplayView.notFound(traceId, RunReplayView.SOURCE_AGENT_LOOP_LOG);
        }
        matched.sort(RunReplayService::compareChronologically);
        List<RunReplayView.Step> steps = stepsOf(matched);
        return new RunReplayView(RunReplayView.FOUND, traceId, agentIdOf(matched),
                steps.size(), Boolean.valueOf(scan.truncated()), RunReplayView.SOURCE_AGENT_LOOP_LOG,
                totalsOf(matched), steps);
    }

    /** 时间戳可用则按时间戳，否则退回读取序（同进程内即写入序）。 */
    private static int compareChronologically(AgentLoopTrace.Line a, AgentLoopTrace.Line b) {
        if (a.timestampMillis() != AgentLoopTrace.UNKNOWN
                && b.timestampMillis() != AgentLoopTrace.UNKNOWN) {
            int byTime = Long.compare(a.timestampMillis(), b.timestampMillis());
            if (byTime != 0) {
                return byTime;
            }
        }
        return Long.compare(a.seq(), b.seq());
    }

    private static String agentIdOf(List<AgentLoopTrace.Line> lines) {
        for (AgentLoopTrace.Line line : lines) {
            if (line.agent() != null) {
                return line.agent();
            }
        }
        return null;
    }

    private static List<RunReplayView.Step> stepsOf(List<AgentLoopTrace.Line> lines) {
        Map<String, List<AgentLoopTrace.Line>> grouped = new LinkedHashMap<>();
        for (AgentLoopTrace.Line line : lines) {
            grouped.computeIfAbsent(groupKey(line), k -> new ArrayList<>()).add(line);
        }
        List<RunReplayView.Step> steps = new ArrayList<>(grouped.size());
        for (List<AgentLoopTrace.Line> attempts : grouped.values()) {
            AgentLoopTrace.Line first = attempts.get(0);
            List<RunReplayView.Attempt> retries = new ArrayList<>(attempts.size() - 1);
            for (int i = 1; i < attempts.size(); i++) {
                AgentLoopTrace.Line retry = attempts.get(i);
                retries.add(new RunReplayView.Attempt(i + 1, retry.model(),
                        value(retry.latencyMs()), value(retry.promptTokens()),
                        value(retry.completionTokens()), name(retry.decision())));
            }
            steps.add(new RunReplayView.Step(first.op() == null ? null : first.op().wire(),
                    first.step(), first.tool(), first.model(), value(first.latencyMs()),
                    value(first.promptTokens()), value(first.completionTokens()),
                    name(first.decision()), List.copyOf(retries)));
        }
        return List.copyOf(steps);
    }

    /** 步骤身份 = 步骤号 + op + 工具（同一步内不同工具不合并）。 */
    private static String groupKey(AgentLoopTrace.Line line) {
        return line.step() + "|" + (line.op() == null ? "-" : line.op().wire())
                + "|" + (line.tool() == null ? "-" : line.tool());
    }

    /**
     * 累计值：token 只累计 {@code llm_call} 行（工具/规划行本身无 token），
     * 耗时取 {@code final} 行（即整次运行墙钟），无 final 行为 null（禁以步内耗时求和冒充实测总耗时）。
     */
    private static RunReplayView.Totals totalsOf(List<AgentLoopTrace.Line> lines) {
        long prompt = 0;
        long completion = 0;
        boolean hasPrompt = false;
        boolean hasCompletion = false;
        int llmCalls = 0;
        Long runLatency = null;
        for (AgentLoopTrace.Line line : lines) {
            if (line.op() == AgentLoopTrace.Op.LLM_CALL) {
                llmCalls++;
                if (line.promptTokens() != AgentLoopTrace.UNKNOWN) {
                    prompt += line.promptTokens();
                    hasPrompt = true;
                }
                if (line.completionTokens() != AgentLoopTrace.UNKNOWN) {
                    completion += line.completionTokens();
                    hasCompletion = true;
                }
            }
            if (line.op() == AgentLoopTrace.Op.FINAL && runLatency == null
                    && line.latencyMs() != AgentLoopTrace.UNKNOWN) {
                runLatency = line.latencyMs();
            }
        }
        return new RunReplayView.Totals(hasPrompt ? prompt : null, hasCompletion ? completion : null,
                runLatency, llmCalls);
    }

    /** -1（缺失）→ null：回放响应面把"未上报"与"实测 0"分开。 */
    private static Long value(long v) {
        return v == AgentLoopTrace.UNKNOWN ? null : v;
    }

    private static String name(AgentLoopTrace.Decision decision) {
        return decision == null ? AgentLoopTrace.Decision.NA.name() : decision.name();
    }
}
