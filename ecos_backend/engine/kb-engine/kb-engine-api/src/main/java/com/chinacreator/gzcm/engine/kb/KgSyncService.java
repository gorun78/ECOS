package com.chinacreator.gzcm.engine.kb;

import java.util.List;
import java.util.Map;

public interface KgSyncService {

    List<Map<String, Object>> getSyncStatus();

    String getOverallStatus();

    void triggerFullSync(String syncId);

    void triggerObjectSync(String syncId, String objectType);

    List<Map<String, Object>> getSyncLogs(int limit);
}