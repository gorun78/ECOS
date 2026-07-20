package com.chinacreator.gzcm.engine.kb;

import java.util.List;
import java.util.Map;

public interface KnowledgeSettingsService {

    List<Map<String, Object>> getAllSettings();

    int batchUpdate(List<Map<String, String>> updates);

    String getSetting(String key);

    void upsertSetting(String key, String value, String group, String type, String description);
}