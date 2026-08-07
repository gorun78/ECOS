package com.chinacreator.gzcm.engine.ai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent 追踪器 — 为每次 AgentLoop 推理创建 trace，记录每轮 think/act/observe 的耗时和结果。
 */
public class AgentTracer {

    private static final Logger log = LoggerFactory.getLogger(AgentTracer.class);

    /** traceId → 轮次记录列表，内存保留最近 1000 条 trace */
    private static final ConcurrentHashMap<String, List<TurnRecord>> traces = new ConcurrentHashMap<>();
    private static final int MAX_TRACES = 1000;

    /**
     * 创建新 trace，返回短 traceId（UUID 前 8 位）。
     */
    public static String newTrace() {
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        traces.put(traceId, new ArrayList<>());
        // 内存保护：超出上限时清理最早的 trace
        if (traces.size() > MAX_TRACES) {
            String oldestKey = traces.keys().nextElement();
            traces.remove(oldestKey);
        }
        log.debug("[AgentTracer] Created trace {}", traceId);
        return traceId;
    }

    /**
     * 记录一轮操作（think / act / observe）。
     *
     * @param traceId   追踪 ID
     * @param turn      轮次号
     * @param action    操作类型（think / act / observe）
     * @param elapsedMs 耗时（毫秒）
     * @param tokens    当轮消耗 token 数
     * @param toolName  工具名（act 时有效，其他传 null）
     * @param success   是否成功
     */
    public static void record(String traceId, int turn, String action,
                               long elapsedMs, int tokens, String toolName, boolean success) {
        List<TurnRecord> list = traces.get(traceId);
        if (list == null) {
            list = new ArrayList<>();
            traces.put(traceId, list);
        }
        TurnRecord rec = new TurnRecord(traceId, turn, action, elapsedMs, tokens, toolName, success);
        list.add(rec);
        log.debug("[AgentTracer] trace={} turn={} action={} elapsedMs={} tokens={}",
                traceId, turn, action, elapsedMs, tokens);
    }

    /**
     * 获取指定 trace 的所有轮次记录。
     */
    public static List<Map<String, Object>> getTrace(String traceId) {
        List<TurnRecord> list = traces.getOrDefault(traceId, Collections.emptyList());
        List<Map<String, Object>> result = new ArrayList<>();
        for (TurnRecord r : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("traceId", r.traceId);
            m.put("turn", r.turn);
            m.put("action", r.action);
            m.put("elapsedMs", r.elapsedMs);
            m.put("tokens", r.tokens);
            m.put("toolName", r.toolName);
            m.put("success", r.success);
            result.add(m);
        }
        return result;
    }

    /**
     * 清理 trace 数据。
     */
    public static void clear(String traceId) {
        traces.remove(traceId);
    }

    // ─── 轮次记录 POJO ────────────────────────────────────────

    public static class TurnRecord {
        public final String traceId;
        public final int turn;
        public final String action;
        public final long elapsedMs;
        public final int tokens;
        public final String toolName;
        public final boolean success;

        TurnRecord(String traceId, int turn, String action,
                    long elapsedMs, int tokens, String toolName, boolean success) {
            this.traceId = traceId;
            this.turn = turn;
            this.action = action;
            this.elapsedMs = elapsedMs;
            this.tokens = tokens;
            this.toolName = toolName;
            this.success = success;
        }
    }
}
