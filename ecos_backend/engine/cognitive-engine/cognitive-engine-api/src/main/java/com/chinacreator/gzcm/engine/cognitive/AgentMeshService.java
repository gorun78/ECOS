package com.chinacreator.gzcm.engine.cognitive;

import java.util.Map;

public interface AgentMeshService {

    Map<String, Object> routeIntent(Map<String, Object> req);

    Map<String, Object> getMissionStatus(String missionId);
}
