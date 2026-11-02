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

    /**
     * 版本历史行读面（#147V 起带 {@code git_ref}）：该列是「历史内容只进 Git」的引用载体，
     * 缺它则读侧无从判定内容所在地（列由 V305 引入）。
     */
    public List<Map<String, Object>> getVersions(String ruleId) {
        return jdbcTemplate.queryForList(
            "SELECT id, rule_id, version_number, snapshot, changed_by, changed_at, change_note, git_ref " +
            "FROM sys_rule_version WHERE rule_id = ? ORDER BY version_number DESC",
            ruleId
        );
    }

    /**
     * 归档成功后回填 git_ref（#147V；与本体 {@code OntologyVersionDao#setGitRef} 同形）。
     *
     * <p>只在 {@link com.chinacreator.gzcm.runtime.access.git.archive.ArchiveOutcome#isArchived()}
     * 为真时调用——归档未成功的行必须留空，禁写占位值冒充已归档。</p>
     */
    public int setGitRef(String versionId, String gitRef) {
        return jdbcTemplate.update(
            "UPDATE sys_rule_version SET git_ref = ? WHERE id = ?",
            gitRef,
            versionId
        );
    }
}
