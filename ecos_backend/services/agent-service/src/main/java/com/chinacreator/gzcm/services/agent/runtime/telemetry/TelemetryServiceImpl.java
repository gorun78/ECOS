package com.chinacreator.gzcm.services.agent.runtime.telemetry;

import com.chinacreator.gzcm.services.agent.runtime.model.AgentMetricsSummary;
import com.chinacreator.gzcm.services.agent.runtime.model.PromptRecord;
import com.chinacreator.gzcm.services.agent.runtime.model.ToolCallRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;

@Service
public class TelemetryServiceImpl implements TelemetryService {
    private static final Logger log = LoggerFactory.getLogger(TelemetryServiceImpl.class);
    private final ConcurrentHashMap<String, AgentMetrics> metricsMap = new ConcurrentHashMap<>();

    @Override
    public void recordPrompt(PromptRecord record) {
        log.debug("Recording prompt for agent: {}", record.getAgentId());
        metricsMap.computeIfAbsent(record.getAgentId(), k -> new AgentMetrics())
            .addPrompt(record.getTokenCount(), record.getLatencyMs());
    }

    @Override
    public void recordToolCall(ToolCallRecord record) {
        log.debug("Recording tool call for agent: {}", record.getAgentId());
        metricsMap.computeIfAbsent(record.getAgentId(), k -> new AgentMetrics())
            .addToolCall(record.getLatencyMs());
    }

    @Override
    public AgentMetricsSummary getMetrics(String agentId) {
        AgentMetrics m = metricsMap.get(agentId);
        AgentMetricsSummary s = new AgentMetricsSummary();
        s.setAgentId(agentId);
        if (m != null) {
            s.setTotalPrompts(m.prompts.longValue());
            s.setTotalToolCalls(m.toolCalls.longValue());
            s.setAvgLatencyMs(m.prompts.longValue() > 0 ? m.totalLatency.doubleValue() / m.prompts.longValue() : 0);
            s.setTotalTokens(m.totalTokens.longValue());
        }
        return s;
    }

    private static class AgentMetrics {
        AtomicLong prompts = new AtomicLong();
        AtomicLong toolCalls = new AtomicLong();
        AtomicLong totalTokens = new AtomicLong();
        DoubleAdder totalLatency = new DoubleAdder();

        void addPrompt(int tokens, long latency) {
            prompts.incrementAndGet();
            totalTokens.addAndGet(tokens);
            totalLatency.add(latency);
        }

        void addToolCall(long latency) {
            toolCalls.incrementAndGet();
            totalLatency.add(latency);
        }
    }
}
