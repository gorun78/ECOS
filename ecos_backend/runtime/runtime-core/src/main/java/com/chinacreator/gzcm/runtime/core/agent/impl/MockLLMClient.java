package com.chinacreator.gzcm.runtime.core.agent.impl;

import com.chinacreator.gzcm.runtime.core.agent.llm.ChatRequest;
import com.chinacreator.gzcm.runtime.core.agent.llm.ChatResponse;
import com.chinacreator.gzcm.runtime.core.agent.llm.LLMClient;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class MockLLMClient implements LLMClient {

    private final AtomicInteger totalCalls = new AtomicInteger(0);

    @Override
    public ChatResponse chat(ChatRequest request) {
        int callId = totalCalls.incrementAndGet();
        ChatResponse resp = new ChatResponse();

        // Collect all message content to detect role
        StringBuilder allText = new StringBuilder();
        if (request.getMessages() != null) {
            for (var m : request.getMessages()) {
                if (m.getContent() != null) allText.append(m.getContent());
            }
        }
        String fullText = allText.toString();
        String lastMsg = "";
        if (request.getMessages() != null && !request.getMessages().isEmpty()) {
            lastMsg = request.getMessages().get(request.getMessages().size() - 1).getContent();
            if (lastMsg == null) lastMsg = "";
        }

        // ── Coordinator mode: return AGENT:xxx|yyy format ──
        if (fullText.contains("任务协调器") || fullText.contains("Coordinator")) {
            // Look at the mission description to decide which agents to dispatch
            String plan = buildCoordinatorPlan(lastMsg);
            resp.setContent(plan);
            resp.setFinishReason("stop");
            resp.setUsage(new ChatResponse.TokenUsage(50, plan.length(), 50 + plan.length()));
            return resp;
        }

        // ── Specialist mode: ReAct with tool calls ──
        if (request.getTools() != null && !request.getTools().isEmpty() && callId % 2 == 1) {
            // Send a tool call on odd calls
            String toolName = "knowledge_search";
            if (lastMsg.contains("object") || lastMsg.contains("数据") || lastMsg.contains("分析")) {
                toolName = "object_query";
            } else if (lastMsg.contains("ontology") || lastMsg.contains("本体")) {
                toolName = "ontology_explore";
            } else if (lastMsg.contains("workflow") || lastMsg.contains("工作流")) {
                toolName = "workflow_start";
            }

            ChatRequest.ToolCallRequest tc = new ChatRequest.ToolCallRequest(
                "mock-tc-" + callId, toolName,
                "{\"query\":\"" + lastMsg.substring(0, Math.min(30, lastMsg.length())) + "\"}");
            resp.setToolCalls(List.of(tc));
            resp.setContent("Calling tool: " + toolName);
        } else {
            // Final answer after tool execution
            resp.setContent("Analysis complete. Found relevant information matching query. " +
                "Summary: 3 key findings identified. Status: SUCCESS.");
            resp.setFinishReason("stop");
        }

        resp.setUsage(new ChatResponse.TokenUsage(10, 50, 60));
        return resp;
    }

    private String buildCoordinatorPlan(String missionDescription) {
        String desc = missionDescription != null ? missionDescription.toLowerCase() : "";
        StringBuilder plan = new StringBuilder();

        if (desc.contains("customer") || desc.contains("客户") || desc.contains("knowledge")) {
            plan.append("AGENT:ag-knowledge|Search knowledge graph for customer-related entities and relationships\n");
            plan.append("AGENT:ag-data|Extract customer data records from registered data sources\n");
        } else if (desc.contains("compliance") || desc.contains("合规") || desc.contains("audit")) {
            plan.append("AGENT:ag-knowledge|Retrieve compliance regulations and policies\n");
            plan.append("AGENT:ag-compliance|Review data records against compliance requirements\n");
        } else if (desc.contains("analysis") || desc.contains("分析") || desc.contains("report")) {
            plan.append("AGENT:ag-data|Extract and clean relevant data\n");
            plan.append("AGENT:ag-knowledge|Search for related knowledge and context\n");
        } else {
            plan.append("AGENT:ag-knowledge|Search knowledge graph for: " + 
                (missionDescription != null ? missionDescription.substring(0, Math.min(50, missionDescription.length())) : "general query") + "\n");
            plan.append("AGENT:ag-data|Query data sources for related records\n");
        }
        plan.append("AGENT:ag-knowledge|Summarize all findings into a structured report\n");

        return plan.toString();
    }

    @Override
    public ChatResponse chatStream(ChatRequest request, StreamCallback callback) {
        ChatResponse resp = chat(request);
        if (callback != null && resp.getContent() != null) {
            for (char c : resp.getContent().toCharArray()) {
                callback.onToken(String.valueOf(c));
            }
        }
        return resp;
    }

    @Override
    public List<String> getAvailableModels() {
        return List.of("mock-model");
    }

    @Override
    public Map<String, Object> getUsageStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalCalls", totalCalls.get());
        stats.put("model", "mock-model");
        return stats;
    }
}
