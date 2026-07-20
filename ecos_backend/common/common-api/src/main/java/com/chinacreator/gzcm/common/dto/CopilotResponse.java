package com.chinacreator.gzcm.common.dto;

import java.util.List;
import java.util.Map;

public class CopilotResponse {
    private String answer;
    private List<String> thoughtChain;
    private List<Map<String, Object>> toolCalls;
    private List<Map<String, Object>> sources;
    private String sessionId;

    public CopilotResponse() {}

    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }
    public List<String> getThoughtChain() { return thoughtChain; }
    public void setThoughtChain(List<String> thoughtChain) { this.thoughtChain = thoughtChain; }
    public List<Map<String, Object>> getToolCalls() { return toolCalls; }
    public void setToolCalls(List<Map<String, Object>> toolCalls) { this.toolCalls = toolCalls; }
    public List<Map<String, Object>> getSources() { return sources; }
    public void setSources(List<Map<String, Object>> sources) { this.sources = sources; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
}
