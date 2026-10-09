package com.chinacreator.gzcm.engine.ai.service;

import com.chinacreator.gzcm.common.trace.AgentLoopTrace;
import com.chinacreator.gzcm.common.trace.EcosTraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Agent 追踪器的<b>本地调试视图</b> —— 不是 traceId 源（详细设计-06 F06-06 要点 1）。
 *
 * <p>traceId 唯一源 = MDC（入口过滤器写入，见 {@link EcosTraceContext}）。本类只做两件事：</p>
 * <ol>
 *   <li>{@link #newTrace()}：读 MDC（缺失时由 {@link EcosTraceContext#currentTraceId()}
 *       生成<b>完整 UUID</b> 并写回 MDC），并在内存登记一个调试视图条目。
 *       旧的 {@code substring(0,8)} 短串已废除 —— 它与其它进程/其它会话可碰撞，且重启即失；</li>
 *   <li>{@link #record}：按 {@link AgentLoopTrace.Op} 枚举累积轮次，供本机 IDE/日志面板速览。</li>
 * </ol>
 *
 * <p>淘汰策略：按 {@code insertSeq} 单调序号的 LRU（X-36 的
 * {@code traces.keys().nextElement()} 淘汰是 ConcurrentHashMap 迭代序，与插入序无关且存在竞态，
 * 可能淘汰掉正在写入的最新 trace）。</p>
 *
 * <p><b>运行回放（F06-08）一律不依赖本类</b>：回放只从结构化日志行 + 审计事件重建。</p>
 */
public class AgentTracer {

    private static final Logger log = LoggerFactory.getLogger(AgentTracer.class);

    /** 内存保留的 trace 上限（调试视图，非持久层）。 */
    static final int MAX_TRACES = 1000;

    /** traceId → 轮次记录；值内列表按步骤顺序追加。 */
    private static final ConcurrentHashMap<String, TraceBucket> traces = new ConcurrentHashMap<>();

    /** 单调递增登记序号 —— LRU 淘汰唯一依据。 */
    private static final AtomicLong insertSeqGen = new AtomicLong();

    /** 淘汰临界区锁（视图非热路径，串行即可保证判定确定）。 */
    private static final Object EVICT_LOCK = new Object();

    /**
     * 起始本次 Loop 的 trace —— 直接取 MDC traceId（完整 UUID），本类不另造 ID。
     *
     * @return 与 MDC 同源的 traceId
     */
    public static String newTrace() {
        String traceId = EcosTraceContext.currentTraceId();
        traces.computeIfAbsent(traceId, id -> new TraceBucket(insertSeqGen.incrementAndGet()));
        evictIfOverCapacity();
        log.debug("[AgentTracer] Debug view attached to trace {}", traceId);
        return traceId;
    }

    /**
     * 记录一步（plan / llm_call / tool_call / tool_result / final）。
     *
     * @param traceId          MDC 同源 traceId
     * @param step             步骤号（Loop 轮次）
     * @param op               步骤枚举（F06-06 要点 4，取代 think/act/observe）
     * @param latencyMs        耗时毫秒；未知传 {@link AgentLoopTrace#UNKNOWN}
     * @param promptTokens     输入 token；未知传 {@link AgentLoopTrace#UNKNOWN}（禁传 0 冒充实测）
     * @param completionTokens 输出 token；未知传 {@link AgentLoopTrace#UNKNOWN}
     * @param toolName         工具名（tool_call / tool_result 有效，其余传 null）
     * @param decision         裁决态，无裁决环节传 {@link AgentLoopTrace.Decision#NA}
     */
    public static void record(String traceId, int step, AgentLoopTrace.Op op,
                              long latencyMs, long promptTokens, long completionTokens,
                              String toolName, AgentLoopTrace.Decision decision) {
        if (traceId == null || op == null) {
            return;
        }
        TraceBucket bucket = traces.get(traceId);
        if (bucket == null) {
            bucket = traces.computeIfAbsent(traceId, id -> new TraceBucket(insertSeqGen.incrementAndGet()));
        }
        bucket.steps.add(new TurnRecord(traceId, step, op.wire(), latencyMs,
                promptTokens, completionTokens, toolName,
                decision == null ? AgentLoopTrace.Decision.NA.name() : decision.name()));
        log.debug("[AgentTracer] trace={} step={} op={} latencyMs={} promptTokens={} completionTokens={}",
                traceId, step, op.wire(), latencyMs, promptTokens, completionTokens);
    }

    /**
     * 获取指定 trace 的轮次记录（本地调试视图；进程重启即失）。
     */
    public static List<Map<String, Object>> getTrace(String traceId) {
        TraceBucket bucket = traces.get(traceId);
        if (bucket == null) {
            return Collections.emptyList();
        }
        List<TurnRecord> snapshot;
        synchronized (bucket.steps) {
            snapshot = new ArrayList<>(bucket.steps);
        }
        List<Map<String, Object>> result = new ArrayList<>(snapshot.size());
        for (TurnRecord r : snapshot) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("traceId", r.traceId());
            m.put("step", r.step());
            m.put("op", r.op());
            m.put("latencyMs", r.latencyMs());
            m.put("promptTokens", r.promptTokens());
            m.put("completionTokens", r.completionTokens());
            m.put("toolName", r.toolName());
            m.put("decision", r.decision());
            result.add(m);
        }
        return result;
    }

    /**
     * 清理 trace 调试视图数据。
     */
    public static void clear(String traceId) {
        traces.remove(traceId);
    }

    /** 当前视图内 trace 数（测试与诊断用）。 */
    static int trackedTraceCount() {
        return traces.size();
    }

    /** 清空整个视图（测试用，避免用例间串号）。 */
    static void clearAll() {
        traces.clear();
    }

    /** 按 insertSeq 淘汰最久未登记的条目，直到不超过 {@link #MAX_TRACES}。 */
    private static void evictIfOverCapacity() {
        if (traces.size() <= MAX_TRACES) {
            return;
        }
        synchronized (EVICT_LOCK) {
            while (traces.size() > MAX_TRACES) {
                String victim = null;
                long lowest = Long.MAX_VALUE;
                for (Map.Entry<String, TraceBucket> e : traces.entrySet()) {
                    long seq = e.getValue().firstSeq;
                    if (seq < lowest) {
                        lowest = seq;
                        victim = e.getKey();
                    }
                }
                if (victim == null) {
                    return;
                }
                traces.remove(victim);
                log.debug("[AgentTracer] Evicted debug view (LRU by insertSeq): {}", victim);
            }
        }
    }

    /** 一个 trace 的视图桶：首次登记序号 + 步骤列表。 */
    private static final class TraceBucket {
        private final long firstSeq;
        private final List<TurnRecord> steps = new ArrayList<>();

        TraceBucket(long firstSeq) {
            this.firstSeq = firstSeq;
        }
    }

    // ─── 轮次记录 POJO ────────────────────────────────────────

    /** 单步记录（字段名与结构化日志行 key 一致，便于回放对账）。 */
    public record TurnRecord(String traceId, int step, String op, long latencyMs,
                             long promptTokens, long completionTokens, String toolName,
                             String decision) {
    }
}
