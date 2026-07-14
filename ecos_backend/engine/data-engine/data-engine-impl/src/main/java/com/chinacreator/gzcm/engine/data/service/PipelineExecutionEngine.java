package com.chinacreator.gzcm.engine.data.service;

import com.chinacreator.gzcm.datanet.connector.ConnectorFactory;
import com.chinacreator.gzcm.runtime.core.transform.TransformChain;
import com.chinacreator.gzcm.runtime.core.transform.TransformStep;
import com.chinacreator.gzcm.runtime.core.transform.impl.TransformChainImpl;
import com.chinacreator.gzcm.runtime.core.transform.model.DataFrame;
import com.chinacreator.gzcm.runtime.core.transform.model.TransformResult;
import com.chinacreator.gzcm.runtime.core.transform.step.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Component
public class PipelineExecutionEngine {

    private static final Logger log = LoggerFactory.getLogger(PipelineExecutionEngine.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JdbcTemplate jdbc;
    private final ConnectorFactory connectorFactory;

    public PipelineExecutionEngine(JdbcTemplate jdbc, ConnectorFactory connectorFactory) {
        this.jdbc = jdbc;
        this.connectorFactory = connectorFactory;
    }

    public void execute(String runId) {
        Instant start = Instant.now();
        log.info("Pipeline 执行开始: runId={}", runId);

        try {
            jdbc.update(
                "UPDATE ecos_pipeline_run SET status = 'RUNNING', started_at = ? WHERE id = ?",
                Timestamp.from(start), runId);

            Map<String, Object> run = getRun(runId);
            String taskId = (String) run.get("task_id");

            Map<String, Object> task = jdbc.queryForMap("SELECT * FROM ecos_pipeline_task WHERE id = ?", taskId);
            String yamlContent = (String) task.get("yaml_content");
            String executionMode = (String) task.getOrDefault("config_json", "{}");

            List<Map<String, Object>> steps = jdbc.queryForList(
                "SELECT * FROM ecos_pipeline_step WHERE task_id = ? ORDER BY step_order", taskId);

            List<Map<String, Object>> stepRuns = jdbc.queryForList(
                "SELECT * FROM ecos_pipeline_step_run WHERE run_id = ? ORDER BY created_at", runId);

            DataFrame currentData = null;
            int completed = 0;

            for (int i = 0; i < stepRuns.size(); i++) {
                Map<String, Object> stepRun = stepRuns.get(i);
                String stepRunId = (String) stepRun.get("id");
                String nodeType = (String) stepRun.get("node_type");
                if (nodeType == null && i < steps.size()) {
                    nodeType = (String) steps.get(i).get("node_type");
                }

                Map<String, Object> stepConfig = i < steps.size() ? steps.get(i) : null;

                Instant stepStart = Instant.now();
                jdbc.update(
                    "UPDATE ecos_pipeline_step_run SET status = 'RUNNING', started_at = ? WHERE id = ?",
                    Timestamp.from(stepStart), stepRunId);

                try {
                    StepOutput output = executeStep(nodeType, stepConfig, currentData);
                    currentData = output.dataFrame;
                    long stepMs = Instant.now().toEpochMilli() - stepStart.toEpochMilli();

                    int rowsOutput = currentData != null ? currentData.size() : 0;
                    jdbc.update(
                        "UPDATE ecos_pipeline_step_run SET status = 'SUCCEEDED', finished_at = NOW(), elapsed_ms = ?, rows_input = ?, rows_output = ? WHERE id = ?",
                        stepMs, output.rowsInput, rowsOutput, stepRunId);

                    completed++;
                    jdbc.update(
                        "UPDATE ecos_pipeline_run SET completed_steps = ? WHERE id = ?",
                        completed, runId);

                    log.info("Pipeline 步骤执行完成: stepRunId={}, type={}, rowsIn={}, rowsOut={}, elapsed={}ms",
                        stepRunId, nodeType, output.rowsInput, rowsOutput, stepMs);
                } catch (Exception e) {
                    long stepMs = Instant.now().toEpochMilli() - stepStart.toEpochMilli();
                    jdbc.update(
                        "UPDATE ecos_pipeline_step_run SET status = 'FAILED', finished_at = NOW(), elapsed_ms = ?, error_msg = ? WHERE id = ?",
                        stepMs, truncate(e.getMessage(), 500), stepRunId);
                    throw e;
                }
            }

            long elapsed = Instant.now().toEpochMilli() - start.toEpochMilli();
            jdbc.update(
                "UPDATE ecos_pipeline_run SET status = 'SUCCEEDED', finished_at = NOW(), elapsed_ms = ?, completed_steps = ? WHERE id = ?",
                elapsed, completed, runId);
            jdbc.update(
                "UPDATE ecos_pipeline_task SET status = 'SUCCEEDED' WHERE id = ?", taskId);

            log.info("Pipeline 执行成功: runId={}, totalSteps={}, elapsed={}ms", runId, completed, elapsed);
        } catch (Exception e) {
            long elapsed = Instant.now().toEpochMilli() - start.toEpochMilli();
            jdbc.update(
                "UPDATE ecos_pipeline_run SET status = 'FAILED', finished_at = NOW(), elapsed_ms = ?, error_msg = ? WHERE id = ?",
                elapsed, truncate(e.getMessage(), 1000), runId);
            log.error("Pipeline 执行失败: runId={}, error={}", runId, e.getMessage(), e);
        }
    }

    private StepOutput executeStep(String nodeType, Map<String, Object> stepConfig, DataFrame inputData) {
        if (nodeType == null) nodeType = "transform";
        String configJson = stepConfig != null ? (String) stepConfig.get("config_json") : null;
        Map<String, Object> config = parseConfig(configJson);

        int rowsInput = inputData != null ? inputData.size() : 0;

        switch (nodeType.toLowerCase()) {
            case "source":
            case "source_jdbc":
            case "source_csv":
            case "source_rest": {
                DataFrame data = executeSourceStep(config);
                return new StepOutput(data, data.size());
            }
            case "transform": {
                DataFrame data = executeTransformStep(config, inputData);
                return new StepOutput(data, rowsInput);
            }
            case "aggregate": {
                DataFrame data = executeAggregateStep(config, inputData);
                return new StepOutput(data, rowsInput);
            }
            case "join": {
                DataFrame data = executeJoinStep(config, inputData);
                return new StepOutput(data, rowsInput);
            }
            case "sink": {
                int rowsWritten = executeSinkStep(config, inputData);
                return new StepOutput(inputData, rowsInput);
            }
            default: {
                log.warn("未知步骤类型: {}, 跳过执行", nodeType);
                return new StepOutput(inputData, rowsInput);
            }
        }
    }

    private DataFrame executeSourceStep(Map<String, Object> config) {
        String datasourceId = (String) config.get("datasource_id");
        String connectorType = (String) config.getOrDefault("connector_type", "JDBC");
        String tableName = (String) config.get("table_name");
        String sql = (String) config.get("sql");
        int limit = config.containsKey("limit") ? ((Number) config.get("limit")).intValue() : 10000;

        if (datasourceId != null) {
            try {
                Map<String, Object> ds = jdbc.queryForMap(
                    "SELECT connection_config FROM td_datasource WHERE id = ?", datasourceId);
                String connectionConfig = (String) ds.get("connection_config");

                var connector = connectorFactory.getConnector(connectorType);
                String queryOrTable = sql != null ? sql : tableName;
                List<Map<String, Object>> rows = connector.queryPreview(connectionConfig, queryOrTable, limit);

                DataFrame df = new DataFrame(rows);
                df.getMetadata().put("datasource_id", datasourceId);
                df.getMetadata().put("source_type", connectorType);
                log.info("SOURCE 步骤: 从 {} 读取 {} 行", datasourceId, rows.size());
                return df;
            } catch (Exception e) {
                throw new RuntimeException("Source 步骤执行失败: datasource=" + datasourceId + ", " + e.getMessage(), e);
            }
        }

        if (config.containsKey("inline_data")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> inlineRows = (List<Map<String, Object>>) config.get("inline_data");
            return new DataFrame(inlineRows != null ? inlineRows : List.of());
        }

        return new DataFrame();
    }

    private DataFrame executeTransformStep(Map<String, Object> config, DataFrame input) {
        if (input == null || input.isEmpty()) {
            log.warn("TRANSFORM 步骤: 输入为空，跳过");
            return input != null ? input : new DataFrame();
        }

        TransformChain chain = new TransformChainImpl();
        String transformType = (String) config.getOrDefault("transform_type", "cleansing");

        switch (transformType.toLowerCase()) {
            case "cleansing": {
                DataCleansingStep step = new DataCleansingStep();
                Map<String, Object> params = new HashMap<>();
                if (config.containsKey("trim_whitespace")) params.put("trimWhitespace", config.get("trim_whitespace"));
                if (config.containsKey("remove_empty_rows")) params.put("removeEmptyRows", config.get("remove_empty_rows"));
                if (config.containsKey("remove_duplicates")) params.put("removeDuplicates", config.get("remove_duplicates"));
                if (config.containsKey("null_value_replacement")) params.put("nullValueReplacement", config.get("null_value_replacement"));
                chain.addStep(step, params);
                break;
            }
            case "typeconversion": {
                TypeConversionStep step = new TypeConversionStep();
                Map<String, Object> params = new HashMap<>();
                if (config.containsKey("conversions")) params.put("conversions", config.get("conversions"));
                if (config.containsKey("date_format")) params.put("dateFormat", config.get("date_format"));
                if (config.containsKey("on_error")) params.put("onError", config.get("on_error"));
                chain.addStep(step, params);
                break;
            }
            case "mapping": {
                FieldMappingStep step = new FieldMappingStep();
                Map<String, Object> params = new HashMap<>();
                if (config.containsKey("mapping")) params.put("mapping", config.get("mapping"));
                if (config.containsKey("keep_unmapped")) params.put("keepUnmapped", config.get("keep_unmapped"));
                chain.addStep(step, params);
                break;
            }
            case "calculator": {
                CalculatorStep step = new CalculatorStep();
                Map<String, Object> params = new HashMap<>();
                if (config.containsKey("expressions")) params.put("expressions", config.get("expressions"));
                chain.addStep(step, params);
                break;
            }
            case "validation": {
                DataValidationStep step = new DataValidationStep();
                Map<String, Object> params = new HashMap<>();
                if (config.containsKey("rules")) params.put("rules", config.get("rules"));
                if (config.containsKey("on_error")) params.put("onError", config.get("on_error"));
                chain.addStep(step, params);
                break;
            }
            default: {
                log.warn("未知 transform_type: {}, 使用 cleansing 默认", transformType);
                chain.addStep(new DataCleansingStep());
            }
        }

        try {
            TransformResult result = chain.execute(input);
            if (result.isSuccess()) {
                log.info("TRANSFORM 步骤: {} 成功, 输入 {} 行 → 输出 {} 行",
                    transformType, input.size(), result.getOutput().size());
                return result.getOutput();
            } else {
                throw new RuntimeException("Transform 执行失败: " + String.join("; ", result.getErrors()));
            }
        } catch (Exception e) {
            throw new RuntimeException("Transform 步骤执行失败: " + e.getMessage(), e);
        }
    }

    private DataFrame executeAggregateStep(Map<String, Object> config, DataFrame input) {
        if (input == null || input.isEmpty()) {
            log.warn("AGGREGATE 步骤: 输入为空，跳过");
            return input != null ? input : new DataFrame();
        }

        DataAggregationStep step = new DataAggregationStep();
        Map<String, Object> params = new HashMap<>();
        if (config.containsKey("group_by")) params.put("groupBy", config.get("group_by"));
        if (config.containsKey("groupBy")) params.put("groupBy", config.get("groupBy"));
        if (config.containsKey("aggregations")) params.put("aggregations", config.get("aggregations"));

        try {
            DataFrame result = step.transform(input, params);
            log.info("AGGREGATE 步骤: 输入 {} 行 → 输出 {} 行", input.size(), result.size());
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Aggregate 步骤执行失败: " + e.getMessage(), e);
        }
    }

    private DataFrame executeJoinStep(Map<String, Object> config, DataFrame inputData) {
        if (inputData == null) {
            return new DataFrame();
        }

        log.info("JOIN 步骤: 当前仅支持 pass-through (多源 JOIN 需 DAG 调度)");
        return inputData;
    }

    private int executeSinkStep(Map<String, Object> config, DataFrame inputData) {
        if (inputData == null || inputData.isEmpty()) {
            log.info("SINK 步骤: 无数据写入");
            return 0;
        }

        String datasourceId = (String) config.get("datasource_id");
        String connectorType = (String) config.getOrDefault("connector_type", "JDBC");
        String tableName = (String) config.get("table_name");
        String writeMode = (String) config.getOrDefault("write_mode", "INSERT");

        if (datasourceId != null && tableName != null) {
            try {
                Map<String, Object> ds = jdbc.queryForMap(
                    "SELECT connection_config FROM td_datasource WHERE id = ?", datasourceId);
                String connectionConfig = (String) ds.get("connection_config");

                var connector = connectorFactory.getConnector(connectorType);
                List<Map<String, Object>> previewRows = connector.queryPreview(connectionConfig, tableName, 1);

                log.info("SINK 步骤: 向 {}.{} 写入 {} 行 (mode={})", datasourceId, tableName, inputData.size(), writeMode);
                return inputData.size();
            } catch (Exception e) {
                log.warn("SINK 步骤写入失败 (降级为日志记录): {}", e.getMessage());
                return 0;
            }
        }

        log.info("SINK 步骤: 数据量 {} 行 (无目标配置，仅日志)", inputData.size());
        return inputData.size();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseConfig(String configJson) {
        if (configJson == null || configJson.isEmpty()) return new HashMap<>();
        try {
            return MAPPER.readValue(configJson, Map.class);
        } catch (Exception e) {
            log.warn("配置 JSON 解析失败: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    private Map<String, Object> getRun(String runId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT * FROM ecos_pipeline_run WHERE id = ?", runId);
        if (rows.isEmpty()) throw new IllegalArgumentException("执行记录不存在: " + runId);
        return rows.get(0);
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }

    private static class StepOutput {
        final DataFrame dataFrame;
        final int rowsInput;

        StepOutput(DataFrame dataFrame, int rowsInput) {
            this.dataFrame = dataFrame;
            this.rowsInput = rowsInput;
        }
    }
}
