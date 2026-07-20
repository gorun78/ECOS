package com.chinacreator.gzcm.engine.ai;

import java.util.Map;

public interface AgentMeshService {

    Map<String, Object> routeIntent(Map<String, Object> req);

    Map<String, Object> routeIntentByLLM(Map<String, Object> req);

    Map<String, Object> getMissionStatus(String missionId);
}
