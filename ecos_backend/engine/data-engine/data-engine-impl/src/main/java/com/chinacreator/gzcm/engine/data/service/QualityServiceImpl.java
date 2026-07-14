package com.chinacreator.gzcm.engine.data.service;

import com.chinacreator.gzcm.datanet.connector.ConnectorFactory;
import com.chinacreator.gzcm.engine.data.QualityService;
import com.chinacreator.gzcm.runtime.core.quality.QualityResult;
import com.chinacreator.gzcm.runtime.core.quality.QualityRule;
import com.chinacreator.gzcm.runtime.core.quality.spi.QualityRuleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class QualityServiceImpl implements QualityService, QualityRuleProvider {

    private static final Logger log = LoggerFactory.getLogger(QualityServiceImpl.class);
    private final JdbcTemplate jdbc;
    private final ConnectorFactory connectorFactory;

    public QualityServiceImpl(JdbcTemplate jdbc, ConnectorFactory connectorFactory) {
        this.jdbc = jdbc;
        this.connectorFactory = connectorFactory;
    }

    @PostConstruct
    public void init() {
        ensureSchema();
    }

    private void ensureSchema() {
        try {
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS ecos_quality_rule (
                    rule_id VARCHAR(64) PRIMARY KEY,
                    rule_name VARCHAR(200) NOT NULL,
                    rule_type VARCHAR(30) NOT NULL,
                    target VARCHAR(200) NOT NULL,
                    dataset_id VARCHAR(100),
                    parameters JSONB,
                    severity VARCHAR(10) DEFAULT 'WARN',
                    enabled BOOLEAN DEFAULT true,
                    description TEXT,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS ecos_quality_evaluation (
                    id VARCHAR(36) PRIMARY KEY,
                    dataset_id VARCHAR(100),
                    rule_id VARCHAR(64),
                    passed BOOLEAN,
                    total_rows BIGINT,
                    failed_rows BIGINT,
                    pass_rate DOUBLE PRECISION,
                    sample_size INTEGER,
                    sample_failures JSONB,
                    severity VARCHAR(10),
                    message TEXT,
                    evaluated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
            log.info("ecos_quality_rule / ecos_quality_evaluation 表已就绪");
        } catch (Exception e) {
            log.warn("质量表初始化异常: {}", e.getMessage());
        }
    }

    @Override
    public Map<String, Object> createRule(Map<String, Object> body) {
        String ruleId = (String) body.getOrDefault("rule_id", UUID.randomUUID().toString());
        String ruleName = (String) body.get("rule_name");
        String ruleType = (String) body.get("rule_type");
        String target = (String) body.get("target");

        if (ruleName == null || ruleType == null || target == null) {
            throw new IllegalArgumentException("rule_name, rule_type, target 不能为空");
        }

        jdbc.update(
            "INSERT INTO ecos_quality_rule (rule_id, rule_name, rule_type, target, dataset_id, parameters, severity, enabled, description) " +
            "VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?)",
            ruleId, ruleName, ruleType, target,
            body.getOrDefault("dataset_id", null),
            body.containsKey("parameters") ? toJson(body.get("parameters")) : "{}",
            body.getOrDefault("severity", "WARN"),
            body.getOrDefault("enabled", true),
            body.getOrDefault("description", ""));

        log.info("创建质量规则: {} ({})", ruleName, ruleId);
        return getRule(ruleId);
    }

    @Override
    public Map<String, Object> updateRule(String ruleId, Map<String, Object> body) {
        getRule(ruleId);
        jdbc.update(
            "UPDATE ecos_quality_rule SET rule_name = COALESCE(?, rule_name), " +
            "rule_type = COALESCE(?, rule_type), target = COALESCE(?, target), " +
            "dataset_id = COALESCE(?, dataset_id), parameters = COALESCE(?::jsonb, parameters), " +
            "severity = COALESCE(?, severity), enabled = COALESCE(?, enabled), " +
            "description = COALESCE(?, description), updated_at = NOW() WHERE rule_id = ?",
            body.get("rule_name"), body.get("rule_type"), body.get("target"),
            body.get("dataset_id"), body.containsKey("parameters") ? toJson(body.get("parameters")) : null,
            body.get("severity"), body.get("enabled"), body.get("description"), ruleId);
        log.info("更新质量规则: {}", ruleId);
        return getRule(ruleId);
    }

    @Override
    public void deleteRule(String ruleId) {
        getRule(ruleId);
        jdbc.update("DELETE FROM ecos_quality_evaluation WHERE rule_id = ?", ruleId);
        jdbc.update("DELETE FROM ecos_quality_rule WHERE rule_id = ?", ruleId);
        log.info("删除质量规则: {}", ruleId);
    }

    @Override
    public Map<String, Object> getRule(String ruleId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT * FROM ecos_quality_rule WHERE rule_id = ?", ruleId);
        if (rows.isEmpty()) throw new IllegalArgumentException("质量规则不存在: " + ruleId);
        return rows.get(0);
    }

    @Override
    public Map<String, Object> listRules(String datasetId, String ruleType, int page, int pageSize) {
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

        String countSql = "SELECT COUNT(*) " + sql.substring(sql.indexOf("FROM") - 1);
        int total = jdbc.queryForObject(countSql, Integer.class, params.toArray());

        int offset = Math.max(0, page - 1) * pageSize;
        sql.append(" ORDER BY updated_at DESC LIMIT ? OFFSET ?");
        params.add(pageSize);
        params.add(offset);

        List<Map<String, Object>> list = jdbc.queryForList(sql.toString(), params.toArray());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("page", page);
        result.put("pageSize", pageSize);
        result.put("list", list);
        return result;
    }

    @Override
    public Map<String, Object> evaluate(String datasetId, String datasourceId, String tableName, int sampleSize) {
        List<QualityRule> rules = getRulesFor(datasetId);
        if (rules.isEmpty()) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("datasetId", datasetId);
            result.put("totalRules", 0);
            result.put("message", "无适用规则");
            return result;
        }

        List<Map<String, Object>> rows = loadSampleData(datasourceId, tableName, sampleSize);
        List<Map<String, Object>> evaluationResults = new ArrayList<>();
        int totalFailed = 0;

        for (QualityRule rule : rules) {
            List<QualityResult> ruleResults = evaluateRuleInternal(rule, rows);
            for (QualityResult qr : ruleResults) {
                Map<String, Object> evalRecord = saveEvaluation(qr, datasetId, sampleSize);
                evaluationResults.add(evalRecord);
                if (!qr.isPassed()) totalFailed++;
            }
        }

        onEvaluationComplete(datasetId, List.of());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("datasetId", datasetId);
        result.put("totalRules", rules.size());
        result.put("totalRows", rows.size());
        result.put("totalEvaluations", evaluationResults.size());
        result.put("failedEvaluations", totalFailed);
        result.put("overallPassed", totalFailed == 0);
        result.put("results", evaluationResults);
        return result;
    }

    @Override
    public Map<String, Object> evaluateRule(String ruleId, String datasourceId, String tableName, int sampleSize) {
        Map<String, Object> ruleRow = getRule(ruleId);
        QualityRule rule = mapToRule(ruleRow);
        List<Map<String, Object>> rows = loadSampleData(datasourceId, tableName, sampleSize);
        List<QualityResult> ruleResults = evaluateRuleInternal(rule, rows);

        List<Map<String, Object>> evalRecords = new ArrayList<>();
        for (QualityResult qr : ruleResults) {
            evalRecords.add(saveEvaluation(qr, ruleRow.getOrDefault("dataset_id", "").toString(), sampleSize));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ruleId", ruleId);
        result.put("ruleType", rule.getRuleType());
        result.put("target", rule.getTarget());
        result.put("totalRows", rows.size());
        result.put("evaluations", evalRecords);
        return result;
    }

    @Override
    public Map<String, Object> getEvaluationHistory(String datasetId, int page, int pageSize) {
        StringBuilder sql = new StringBuilder("SELECT * FROM ecos_quality_evaluation WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (datasetId != null && !datasetId.isEmpty()) {
            sql.append(" AND dataset_id = ?");
            params.add(datasetId);
        }

        String countSql = "SELECT COUNT(*) " + sql.substring(sql.indexOf("FROM") - 1);
        int total = jdbc.queryForObject(countSql, Integer.class, params.toArray());

        int offset = Math.max(0, page - 1) * pageSize;
        sql.append(" ORDER BY evaluated_at DESC LIMIT ? OFFSET ?");
        params.add(pageSize);
        params.add(offset);

        List<Map<String, Object>> list = jdbc.queryForList(sql.toString(), params.toArray());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("page", page);
        result.put("pageSize", pageSize);
        result.put("list", list);
        return result;
    }

    @Override
    public List<QualityRule> getRulesFor(String datasetId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT * FROM ecos_quality_rule WHERE dataset_id = ? AND enabled = true ORDER BY rule_type",
            datasetId);
        List<QualityRule> rules = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            rules.add(mapToRule(row));
        }
        return rules;
    }

    @Override
    public void onEvaluationComplete(String datasetId, List<QualityResult> results) {
        log.info("质量评估完成: datasetId={}, resultCount={}", datasetId, results.size());
    }

    private List<QualityResult> evaluateRuleInternal(QualityRule rule, List<Map<String, Object>> rows) {
        List<QualityResult> results = new ArrayList<>();
        long totalRows = rows.size();
        long failedRows = 0;
        List<String> sampleFailures = new ArrayList<>();

        for (Map<String, Object> row : rows) {
            String[] parts = rule.getTarget().split("\\.");
            String fieldName = parts.length > 1 ? parts[parts.length - 1] : rule.getTarget();
            Object value = row.get(fieldName);

            boolean passed = evaluateValue(rule, value);
            if (!passed) {
                failedRows++;
                if (sampleFailures.size() < 10) {
                    sampleFailures.add(fieldName + "=" + value + " (rule: " + rule.getRuleType() + ")");
                }
            }
        }

        boolean allPassed = failedRows == 0;
        String message = allPassed ? "PASS" : failedRows + "/" + totalRows + " rows failed";

        results.add(new QualityResult(
            rule.getRuleId(), rule.getTarget(), allPassed, message,
            totalRows, failedRows, sampleFailures));

        return results;
    }

    private boolean evaluateValue(QualityRule rule, Object value) {
        Map<String, String> params = rule.getParameters();
        switch (rule.getRuleType().toUpperCase()) {
            case "NOT_NULL":
                return value != null;
            case "NOT_EMPTY":
                return value != null && !value.toString().isEmpty();
            case "RANGE": {
                if (value == null) return true;
                try {
                    double num = Double.parseDouble(value.toString());
                    double min = params.containsKey("min") ? Double.parseDouble(params.get("min")) : Double.MIN_VALUE;
                    double max = params.containsKey("max") ? Double.parseDouble(params.get("max")) : Double.MAX_VALUE;
                    return num >= min && num <= max;
                } catch (NumberFormatException e) {
                    return false;
                }
            }
            case "REGEX": {
                if (value == null) return true;
                String pattern = params.get("pattern");
                if (pattern == null) return true;
                return Pattern.matches(pattern, value.toString());
            }
            case "UNIQUE":
                return true;
            case "LENGTH": {
                if (value == null) return true;
                int len = value.toString().length();
                int minLen = params.containsKey("min_length") ? Integer.parseInt(params.get("min_length")) : 0;
                int maxLen = params.containsKey("max_length") ? Integer.parseInt(params.get("max_length")) : Integer.MAX_VALUE;
                return len >= minLen && len <= maxLen;
            }
            case "CUSTOM":
                return true;
            default:
                return true;
        }
    }

    private List<Map<String, Object>> loadSampleData(String datasourceId, String tableName, int sampleSize) {
        if (datasourceId == null || tableName == null) {
            return List.of();
        }
        try {
            Map<String, Object> ds = jdbc.queryForMap(
                "SELECT connection_config, datasource_type FROM td_datasource WHERE id = ?", datasourceId);
            String connectionConfig = (String) ds.get("connection_config");
            String dsType = (String) ds.get("datasource_type");

            String connectorType = "JDBC";
            if (dsType != null) {
                switch (dsType.toUpperCase()) {
                    case "FILE": connectorType = "SOURCE_CSV"; break;
                    case "SERVICE": connectorType = "SOURCE_REST"; break;
                    default: connectorType = "JDBC";
                }
            }

            var connector = connectorFactory.getConnector(connectorType);
            return connector.queryPreview(connectionConfig, tableName, sampleSize);
        } catch (Exception e) {
            log.warn("加载样本数据失败: datasourceId={}, tableName={}, error={}", datasourceId, tableName, e.getMessage());
            return List.of();
        }
    }

    private Map<String, Object> saveEvaluation(QualityResult qr, String datasetId, int sampleSize) {
        String evalId = UUID.randomUUID().toString();
        jdbc.update(
            "INSERT INTO ecos_quality_evaluation (id, dataset_id, rule_id, passed, total_rows, failed_rows, " +
            "pass_rate, sample_size, sample_failures, severity, message) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?)",
            evalId, datasetId, qr.getRuleId(), qr.isPassed(),
            qr.getTotalRows(), qr.getFailedRows(), qr.getPassRate(),
            sampleSize, toJson(qr.getSampleFailures()),
            qr.isPassed() ? "INFO" : "WARN", qr.getMessage());

        Map<String, Object> record = new LinkedHashMap<>();
        record.put("id", evalId);
        record.put("ruleId", qr.getRuleId());
        record.put("target", qr.getTarget());
        record.put("passed", qr.isPassed());
        record.put("totalRows", qr.getTotalRows());
        record.put("failedRows", qr.getFailedRows());
        record.put("passRate", qr.getPassRate());
        record.put("message", qr.getMessage());
        return record;
    }

    private QualityRule mapToRule(Map<String, Object> row) {
        @SuppressWarnings("unchecked")
        Map<String, String> parameters = row.get("parameters") != null
            ? (Map<String, String>) row.get("parameters") : Map.of();
        String severityStr = (String) row.getOrDefault("severity", "WARN");
        QualityRule.Severity severity = QualityRule.Severity.valueOf(severityStr);
        return new QualityRule(
            (String) row.get("rule_id"),
            (String) row.get("rule_type"),
            (String) row.get("target"),
            parameters,
            severity);
    }

    private String toJson(Object obj) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }
}
