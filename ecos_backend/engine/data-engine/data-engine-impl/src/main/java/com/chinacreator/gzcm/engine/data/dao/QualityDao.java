package com.chinacreator.gzcm.engine.data.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Data-access for the data quality domain:
 * ecos_quality_rule / ecos_quality_evaluation CRUD and lookup helpers.
 */
@Repository
public class QualityDao extends JdbcTemplateDaoSupport {

    // --- ecos_quality_rule ---

    public void insertRule(String ruleId, String ruleName, String ruleType, String target,
                           Object datasetId, String parameters, String severity,
                           Object enabled, String description) {
        jdbcTemplate.update(
            "INSERT INTO ecos_quality_rule (rule_id, rule_name, rule_type, target, dataset_id, parameters, severity, enabled, description) " +
            "VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?)",
            ruleId, ruleName, ruleType, target,
            datasetId, parameters, severity, enabled, description);
    }

    public void updateRule(Object ruleName, Object ruleType, Object target,
                           Object datasetId, Object parameters,
                           Object severity, Object enabled, Object description, String ruleId) {
        jdbcTemplate.update(
            "UPDATE ecos_quality_rule SET rule_name = COALESCE(?, rule_name), " +
            "rule_type = COALESCE(?, rule_type), target = COALESCE(?, target), " +
            "dataset_id = COALESCE(?, dataset_id), parameters = COALESCE(?::jsonb, parameters), " +
            "severity = COALESCE(?, severity), enabled = COALESCE(?, enabled), " +
            "description = COALESCE(?, description), updated_at = NOW() WHERE rule_id = ?",
            ruleName, ruleType, target,
            datasetId, parameters,
            severity, enabled, description, ruleId);
    }

    public void deleteEvaluationByRule(String ruleId) {
        jdbcTemplate.update("DELETE FROM ecos_quality_evaluation WHERE rule_id = ?", ruleId);
    }

    public void deleteRule(String ruleId) {
        jdbcTemplate.update("DELETE FROM ecos_quality_rule WHERE rule_id = ?", ruleId);
    }

    public List<Map<String, Object>> getRule(String ruleId) {
        return jdbcTemplate.queryForList(
            "SELECT * FROM ecos_quality_rule WHERE rule_id = ?", ruleId);
    }

    public int countRules(String datasetId, String ruleType) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM ecos_quality_rule WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (datasetId != null && !datasetId.isEmpty()) {
            sql.append(" AND dataset_id = ?");
            params.add(datasetId);
        }
        if (ruleType != null && !ruleType.isEmpty()) {
            sql.append(" AND rule_type = ?");
            params.add(ruleType);
        }
        return jdbcTemplate.queryForObject(sql.toString(), Integer.class, params.toArray());
    }

    public List<Map<String, Object>> listRuleRows(String datasetId, String ruleType, int pageSize, int offset) {
        StringBuilder sql = new StringBuilder("SELECT * FROM ecos_quality_rule WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (datasetId != null && !datasetId.isEmpty()) {
            sql.append(" AND dataset_id = ?");
            params.add(datasetId);
        }
        if (ruleType != null && !ruleType.isEmpty()) {
            sql.append(" AND rule_type = ?");
            params.add(ruleType);
        }
        sql.append(" ORDER BY updated_at DESC LIMIT ? OFFSET ?");
        params.add(pageSize);
        params.add(offset);
        return jdbcTemplate.queryForList(sql.toString(), params.toArray());
    }

    public List<Map<String, Object>> getRulesFor(String datasetId) {
        return jdbcTemplate.queryForList(
            "SELECT * FROM ecos_quality_rule WHERE dataset_id = ? AND enabled = true ORDER BY rule_type",
            datasetId);
    }

    public List<Map<String, Object>> listEnabledRules() {
        return jdbcTemplate.queryForList(
            "SELECT * FROM ecos_quality_rule WHERE enabled = true ORDER BY rule_type, rule_id");
    }

    // --- ecos_quality_evaluation ---

    public int countEvaluations(String datasetId) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM ecos_quality_evaluation WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (datasetId != null && !datasetId.isEmpty()) {
            sql.append(" AND dataset_id = ?");
            params.add(datasetId);
        }
        return jdbcTemplate.queryForObject(sql.toString(), Integer.class, params.toArray());
    }

    public List<Map<String, Object>> listEvaluationRows(String datasetId, int pageSize, int offset) {
        StringBuilder sql = new StringBuilder("SELECT * FROM ecos_quality_evaluation WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (datasetId != null && !datasetId.isEmpty()) {
            sql.append(" AND dataset_id = ?");
            params.add(datasetId);
        }
        sql.append(" ORDER BY evaluated_at DESC LIMIT ? OFFSET ?");
        params.add(pageSize);
        params.add(offset);
        return jdbcTemplate.queryForList(sql.toString(), params.toArray());
    }

    public void insertEvaluation(String evalId, String datasetId, String ruleId, boolean passed,
                                 long totalRows, long failedRows, double passRate,
                                 int sampleSize, String sampleFailures, String severity, String message) {
        jdbcTemplate.update(
            "INSERT INTO ecos_quality_evaluation (id, dataset_id, rule_id, passed, total_rows, failed_rows, " +
            "pass_rate, sample_size, sample_failures, severity, message) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?)",
            evalId, datasetId, ruleId, passed,
            totalRows, failedRows, passRate,
            sampleSize, sampleFailures, severity, message);
    }

    // --- cross-table / datasource resolution helpers ---

    public Map<String, Object> getDatasourceConfig(String datasourceId) {
        return jdbcTemplate.queryForMap(
            "SELECT connection_config, datasource_type FROM td_datasource WHERE id = ?", datasourceId);
    }

    public String resolveDatasourceFromDataset(String datasetId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT datasource_id FROM ecos_pipeline WHERE dataset_id = ? LIMIT 1", datasetId);
        if (!rows.isEmpty()) {
            return (String) rows.get(0).get("datasource_id");
        }
        return null;
    }
}
