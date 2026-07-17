package com.chinacreator.gzcm.services.agent.runtime.telemetry;

import com.chinacreator.gzcm.services.agent.runtime.model.AgentMetricsSummary;
import com.chinacreator.gzcm.services.agent.runtime.model.PromptRecord;
import com.chinacreator.gzcm.services.agent.runtime.model.ToolCallRecord;

public interface TelemetryService {
    void recordPrompt(PromptRecord record);
    void recordToolCall(ToolCallRecord record);
    AgentMetricsSummary getMetrics(String agentId);
}
