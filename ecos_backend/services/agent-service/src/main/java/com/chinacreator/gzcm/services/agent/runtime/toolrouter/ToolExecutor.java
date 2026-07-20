package com.chinacreator.gzcm.services.agent.runtime.toolrouter;

import java.util.Map;

public interface ToolExecutor {
    Object execute(String toolCode, Map<String, Object> params);
    boolean supports(String toolCode);
}
