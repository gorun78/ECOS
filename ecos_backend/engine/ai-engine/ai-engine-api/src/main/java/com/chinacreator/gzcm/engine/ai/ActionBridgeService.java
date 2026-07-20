package com.chinacreator.gzcm.engine.ai;

import java.util.List;
import java.util.Map;

public interface ActionBridgeService {

    Map<String, Object> matchAndExecute(Map<String, Object> req);

    List<Map<String, Object>> getAvailableActions();
}
