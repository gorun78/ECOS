package com.chinacreator.gzcm.common.service;

import java.util.List;
import java.util.Map;

public interface ICopilotAgentService {
    Map<String, Object> chat(String agentId, String userMessage, String sessionId);
    List<Map<String, Object>> getQuickQuestions(String agentId);
}
