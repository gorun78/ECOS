package com.chinacreator.gzcm.engine.kb.service;

import com.chinacreator.gzcm.engine.kb.KnowledgeSettingsService;
import com.chinacreator.gzcm.sysman.config.service.impl.SysConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class KnowledgeSettingsServiceImpl implements KnowledgeSettingsService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeSettingsServiceImpl.class);

    private final SysConfigService sysConfigService;

    public KnowledgeSettingsServiceImpl(SysConfigService sysConfigService) {
        this.sysConfigService = sysConfigService;
    }

    @Override
    public List<Map<String, Object>> getAllSettings() {
        return sysConfigService.listByGroup("knowledge");
    }

    @Override
    public int batchUpdate(List<Map<String, String>> updates) {
        return sysConfigService.updateBatch(updates);
    }

    @Override
    public String getSetting(String key) {
        List<Map<String, Object>> settings = sysConfigService.listByGroup("knowledge");
        for (Map<String, Object> s : settings) {
            if (key.equals(s.get("configKey"))) {
                Object val = s.get("configValue");
                return val != null ? val.toString() : null;
            }
        }
        return null;
    }

    @Override
    public void upsertSetting(String key, String value, String group, String type, String description) {
        sysConfigService.upsertValue(key, value, group, type, description);
    }
}