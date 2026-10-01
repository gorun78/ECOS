package com.chinacreator.gzcm.engine.kb.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class ComplianceRuleDao extends JdbcTemplateDaoSupport {

    public void insertVersion(String versionId, String ruleId, int versionNumber,
                              String snapshot, String changedBy, long changedAt, String changeNote) {
        jdbcTemplate.update(
            "INSERT INTO sys_rule_version (id, rule_id, version_number, snapshot, changed_by, changed_at, change_note) " +
            "VALUES (?, ?, ?, ?::jsonb, ?, ?, ?)",
            versionId,
            ruleId,
            versionNumber,
            snapshot,
            changedBy,
            changedAt,
            changeNote
        );
    }

    public List<Map<String, Object>> getVersions(String ruleId) {
        return jdbcTemplate.queryForList(
            "SELECT id, rule_id, version_number, snapshot, changed_by, changed_at, change_note " +
            "FROM sys_rule_version WHERE rule_id = ? ORDER BY version_number DESC",
            ruleId
        );
    }
}
