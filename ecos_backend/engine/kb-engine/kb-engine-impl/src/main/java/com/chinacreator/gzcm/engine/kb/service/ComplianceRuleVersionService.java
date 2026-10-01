package com.chinacreator.gzcm.engine.kb.service;

import com.chinacreator.gzcm.engine.kb.dao.ComplianceRuleDao;
import com.chinacreator.gzcm.engine.kb.model.ComplianceRule;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ComplianceRuleVersionService {

    private static final Logger log = LoggerFactory.getLogger(ComplianceRuleVersionService.class);

    private final ComplianceRuleDao complianceRuleDao;

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    public ComplianceRuleVersionService(ComplianceRuleDao complianceRuleDao) {
        this.complianceRuleDao = complianceRuleDao;
    }

    /**
     * 版本快照：将当前版本写入 sys_rule_version
     */
    public void snapshotVersion(ComplianceRule existing, String approvedBy, long now, int newVersion) {
        String versionId = UUID.randomUUID().toString();
        complianceRuleDao.insertVersion(
            versionId,
            existing.getId(),
            existing.getVersion(),
            toJson(existing),
            approvedBy,
            now,
            "Updated to version " + newVersion
        );
    }

    /**
     * 查询规则版本历史
     */
    public List<Map<String, Object>> getVersions(String ruleId) {
        return complianceRuleDao.getVersions(ruleId);
    }

    // ── 简易JSON序列化 ────────────────────────────

    private String toJson(ComplianceRule rule) {
        try {
            return MAPPER.writeValueAsString(rule);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize ComplianceRule to JSON: id={}", rule.getId(), e);
            return "{}";
        }
    }
}
