package com.chinacreator.gzcm.runtime.hermes.metrics;

import com.chinacreator.gzcm.runtime.hermes.model.AgentCallLog;
import com.chinacreator.gzcm.runtime.hermes.repository.AgentCallLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * AgentMetrics 实现 — 基于 AgentCallLogRepository 的数据持久化 + 内存聚合
 * <p>
 * recordCall 写入 DB，统计查询通过聚合 DB 数据或内存累积数据实现。
 * </p>
 */
@Primary
@Service
public class AgentMetricsImpl implements AgentMetrics {

    private static final Logger log = LoggerFactory.getLogger(AgentMetricsImpl.class);

    @Autowired
    private AgentCallLogRepository repository;

    /** 内存聚合 — subsystem → 统计计数器 */
    private final ConcurrentHashMap<String, SubsystemStats> statsMap = new ConcurrentHashMap<>();

    @Override
    public void recordCall(String subsystem, String profileName,
                           int tokensInput, int tokensOutput, long durationMs, boolean success) {
        // 持久化写入 DB
        AgentCallLog logRecord = AgentCallLog.builder()
                .id(UUID.randomUUID().toString().replace("-", ""))
                .subsystem(subsystem)
                .profileName(profileName)
                .tokensInput(tokensInput)
                .tokensOutput(tokensOutput)
                .durationMs((int) durationMs)
                .status(success ? "success" : "failed")
                .createdTime(LocalDateTime.now())
                .build();

        try {
            repository.insert(logRecord);
        } catch (Exception e) {
            log.error("Failed to persist AgentCallLog: {}", e.getMessage());
        }

        // 内存聚合
        statsMap.computeIfAbsent(subsystem, k -> new SubsystemStats())
                .record(tokensInput, tokensOutput, durationMs, success);
    }

    @Override
    public Map<String, Object> getSubsystemStats(String subsystem) {
        SubsystemStats stats = statsMap.get(subsystem);
        if (stats == null) {
            return Map.of(
                    "subsystem", subsystem,
                    "totalCalls", 0,
                    "successCount", 0,
                    "failCount", 0,
                    "totalTokensInput", 0,
                    "totalTokensOutput", 0,
                    "totalTokens", 0,
                    "avgDurationMs", 0.0,
                    "activeSessions", 0
            );
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("subsystem", subsystem);
        result.put("totalCalls", stats.totalCalls.get());
        result.put("successCount", stats.successCount.get());
        result.put("failCount", stats.failCount.get());
        result.put("totalTokensInput", stats.totalTokensInput.get());
        result.put("totalTokensOutput", stats.totalTokensOutput.get());
        result.put("totalTokens", stats.totalTokensInput.get() + stats.totalTokensOutput.get());

        long totalCalls = stats.totalCalls.get();
        double avgDuration = totalCalls > 0
                ? (double) stats.totalDurationMs.get() / totalCalls
                : 0.0;
        result.put("avgDurationMs", avgDuration);

        // 注意: 活跃会话数由 SessionManager 维护，这里只返回 0 占位
        result.put("activeSessions", 0);

        return result;
    }

    @Override
    public Map<String, Object> getGlobalStats() {
        Map<String, Object> result = new LinkedHashMap<>();
        int totalCalls = 0;
        int totalSuccess = 0;
        int totalFail = 0;
        long totalTokensInput = 0;
        long totalTokensOutput = 0;
        long totalDurationMs = 0;

        for (Map.Entry<String, SubsystemStats> entry : statsMap.entrySet()) {
            SubsystemStats stats = entry.getValue();
            totalCalls += stats.totalCalls.get();
            totalSuccess += stats.successCount.get();
            totalFail += stats.failCount.get();
            totalTokensInput += stats.totalTokensInput.get();
            totalTokensOutput += stats.totalTokensOutput.get();
            totalDurationMs += stats.totalDurationMs.get();
        }

        result.put("totalCalls", totalCalls);
        result.put("successCount", totalSuccess);
        result.put("failCount", totalFail);
        result.put("totalTokensInput", totalTokensInput);
        result.put("totalTokensOutput", totalTokensOutput);
        result.put("totalTokens", totalTokensInput + totalTokensOutput);

        double avgDuration = totalCalls > 0 ? (double) totalDurationMs / totalCalls : 0.0;
        result.put("avgDurationMs", avgDuration);
        result.put("subsystemCount", statsMap.size());

        return result;
    }

    @Override
    public List<AgentCallLog> getRecentCalls(String subsystem, int limit) {
        try {
            return repository.findBySubsystem(subsystem, limit);
        } catch (Exception e) {
            log.error("Failed to query recent calls for subsystem [{}]: {}", subsystem, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 子系统级别的内存统计计数器
     */
    private static class SubsystemStats {
        private final AtomicInteger totalCalls = new AtomicInteger(0);
        private final AtomicInteger successCount = new AtomicInteger(0);
        private final AtomicInteger failCount = new AtomicInteger(0);
        private final AtomicLong totalTokensInput = new AtomicLong(0);
        private final AtomicLong totalTokensOutput = new AtomicLong(0);
        private final AtomicLong totalDurationMs = new AtomicLong(0);

        void record(int tokensIn, int tokensOut, long durationMs, boolean success) {
            totalCalls.incrementAndGet();
            if (success) {
                successCount.incrementAndGet();
            } else {
                failCount.incrementAndGet();
            }
            totalTokensInput.addAndGet(tokensIn);
            totalTokensOutput.addAndGet(tokensOut);
            totalDurationMs.addAndGet(durationMs);
        }
    }
}
