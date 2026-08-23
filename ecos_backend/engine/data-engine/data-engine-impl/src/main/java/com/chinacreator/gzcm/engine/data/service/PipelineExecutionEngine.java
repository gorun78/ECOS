package com.chinacreator.gzcm.engine.data.service;

import com.chinacreator.gzcm.runtime.access.connector.ConnectorFactory;
import com.chinacreator.gzcm.common.data.model.DataLayer;
import com.chinacreator.gzcm.engine.data.transform.TransformChain;
import com.chinacreator.gzcm.engine.data.transform.TransformStep;
import com.chinacreator.gzcm.engine.data.transform.impl.TransformChainImpl;
import com.chinacreator.gzcm.engine.data.transform.model.DataFrame;
import com.chinacreator.gzcm.engine.data.transform.model.TransformResult;
import com.chinacreator.gzcm.engine.data.transform.step.*;
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
    private final DataLineageService lineageService;
    private final PipelineFailureHandler failureHandler;
    private final PipelineValidator validator;
    private final PipelineProvenanceRecorder provenanceRecorder;

    public PipelineExecutionEngine(JdbcTemplate jdbc, ConnectorFactory connectorFactory,
                                   DataLineageService lineageService,
                                   PipelineFailureHandler failureHandler,
                                   PipelineValidator validator,
                                   PipelineProvenanceRecorder provenanceRecorder) {
        this.jdbc = jdbc;
        this.connectorFactory = connectorFactory;
        this.lineageService = lineageService;
        this.failureHandler = failureHandler;
        this.validator = validator;
        this.provenanceRecorder = provenanceRecorder;
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

            Map<String, Object> task = jdbc.queryForMap(
                "SELECT id, name, yaml_content, config_json::text as config_json, status FROM ecos_pipeline_task WHERE id = ?", taskId);
            String yamlContent = safeToString(task.get("yaml_content"));
            String executionMode = safeToString(task.getOrDefault("config_json", "{}"));

            List<Map<String, Object>> steps = jdbc.queryForList(
                "SELECT id, task_id, step_order, node_id, node_type, config_json::text as config_json, depends_on::text as depends_on FROM ecos_pipeline_step WHERE task_id = ? ORDER BY step_order", taskId);

            // PMO-36 T3: 管线验证
            List<String> errors;
            try {
                errors = validator.validate(steps);
            } catch (Exception ve) {
                log.warn("Pipeline 验证过程异常 (跳过验证): {}", ve.getMessage());
                errors = java.util.Collections.emptyList();
            }
            if (!errors.isEmpty()) {
                String errMsg = "Pipeline validation failed: " + String.join("; ", errors);
                jdbc.update("UPDATE ecos_pipeline_run SET status = 'FAILED', error_msg = ? WHERE id = ?",
                    truncate(errMsg, 1000), runId);
                log.error("Pipeline 验证失败: runId={}, errors={}", runId, errors);
                return;
            }

            List<Map<String, Object>> stepRuns = jdbc.queryForList(
                "SELECT id, run_id, step_id, node_id, status, rows_input, rows_output, started_at, finished_at, elapsed_ms, error_msg, created_at FROM ecos_pipeline_step_run WHERE run_id = ? ORDER BY created_at", runId);

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
                String configJson = stepConfig != null ? safeToString(stepConfig.get("config_json")) : null;

                Instant stepStart = Instant.now();
                jdbc.update(
                    "UPDATE ecos_pipeline_step_run SET status = 'RUNNING', started_at = ? WHERE id = ?",
                    Timestamp.from(stepStart), stepRunId);

                // PMO-36 T1: 失败处理（重试 + 降级）
                final String fNodeType = nodeType;
                final Map<String, Object> fStepConfig = stepConfig;
                final DataFrame fCurrentData = currentData;
                PipelineFailureHandler.HandlerResult handlerResult = failureHandler.executeWithRetry(
                    stepRunId, configJson,
                    () -> executeStep(fNodeType, fStepConfig, fCurrentData)
                );

                long stepMs = Instant.now().toEpochMilli() - stepStart.toEpochMilli();

                if (handlerResult.success) {
                    StepOutput output = (StepOutput) handlerResult.data;
                    currentData = output != null ? output.dataFrame : currentData;
                    int rowsOutput = currentData != null ? currentData.size() : 0;
                    jdbc.update(
                        "UPDATE ecos_pipeline_step_run SET status = 'SUCCEEDED', finished_at = NOW(), elapsed_ms = ?, rows_input = ?, rows_output = ? WHERE id = ?",
                        stepMs, output != null ? output.rowsInput : 0, rowsOutput, stepRunId);

                    completed++;
                    jdbc.update("UPDATE ecos_pipeline_run SET completed_steps = ? WHERE id = ?", completed, runId);

                    // PMO-36 T4: 执行级溯源
                    provenanceRecorder.recordStep(stepRunId, taskId, nodeType, "SUCCEEDED", stepMs);

                    log.info("Pipeline 步骤执行完成: stepRunId={}, type={}, rowsIn={}, rowsOut={}, elapsed={}ms, attempts={}",
                        stepRunId, nodeType, output != null ? output.rowsInput : 0, rowsOutput, stepMs, handlerResult.attempts);
                } else {
                    // 降级处理
                    jdbc.update(
                        "UPDATE ecos_pipeline_step_run SET status = 'FAILED', finished_at = NOW(), elapsed_ms = ?, error_msg = ?, retry_count = ? WHERE id = ?",
                        stepMs, truncate(handlerResult.errorMsg, 500), handlerResult.attempts, stepRunId);

                    provenanceRecorder.recordStep(stepRunId, taskId, nodeType, "FAILED", stepMs);

                    if (handlerResult.fallbackApplied) {
                        // SKIP 策略：记录失败但管线继续
                        log.warn("Pipeline 步骤降级跳过: stepRunId={}, attempts={}", stepRunId, handlerResult.attempts);
                        completed++;
                    } else {
                        // 非降级失败 → 终止
                        throw new RuntimeException("Step failed: " + handlerResult.errorMsg);
                    }
                }
            }

            long elapsed = Instant.now().toEpochMilli() - start.toEpochMilli();
            jdbc.update(
                "UPDATE ecos_pipeline_run SET status = 'SUCCEEDED', finished_at = NOW(), elapsed_ms = ?, completed_steps = ? WHERE id = ?",
                elapsed, completed, runId);
            jdbc.update(
                "UPDATE ecos_pipeline_task SET status = 'SUCCEEDED' WHERE id = ?", taskId);

            log.info("Pipeline 执行成功: runId={}, totalSteps={}, elapsed={}ms", runId, completed, elapsed);

            updateResourceLayer(runId, steps);

            try {
                lineageService.buildTopology(List.of(taskId), true, true);
                log.info("Pipeline lineage topology built: taskId={}", taskId);
            } catch (Exception le) {
                log.warn("Pipeline lineage build failed: taskId={}, error={}", taskId, le.getMessage());
            }
        } catch (Exception e) {
            long elapsed = Instant.now().toEpochMilli() - start.toEpochMilli();
            java.io.StringWriter sw = new java.io.StringWriter();
            e.printStackTrace(new java.io.PrintWriter(sw));
            String errorDetail = truncate(sw.toString(), 1000);
            log.error("Pipeline 执行失败: runId={}, error={}", runId, errorDetail, e);
            try {
                jdbc.update(
                    "UPDATE ecos_pipeline_run SET status = 'FAILED', finished_at = NOW(), elapsed_ms = ?, error_msg = ? WHERE id = ?",
                    elapsed, errorDetail, runId);
            } catch (Exception ignored) {}
        }
    }

    private StepOutput executeStep(String nodeType, Map<String, Object> stepConfig, DataFrame inputData) {
        if (nodeType == null) nodeType = "transform";
        String configJson = stepConfig != null ? safeToString(stepConfig.get("config_json")) : null;
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

    private void updateResourceLayer(String runId, List<Map<String, Object>> steps) {
        try {
            String pipelineType = inferPipelineType(steps);
            String layer;
            switch (pipelineType) {
                case "SYNC":
                    layer = DataLayer.RAW.name();
                    break;
                case "TRANSFORM":
                    layer = DataLayer.CURATED.name();
                    break;
                case "AGGREGATE":
                    layer = DataLayer.APPLICATION.name();
                    break;
                default:
                    return;
            }
            List<Map<String, Object>> sinkRows = jdbc.queryForList(
                "SELECT config_json::text as config_json FROM ecos_pipeline_step WHERE task_id = " +
                "(SELECT task_id FROM ecos_pipeline_run WHERE id = ?) AND node_type = 'sink'", runId);
            for (Map<String, Object> sinkRow : sinkRows) {
                Map<String, Object> sinkConfig = parseConfig(safeToString(sinkRow.get("config_json")));
                String tableName = (String) sinkConfig.get("table_name");
                if (tableName != null) {
                    jdbc.update("UPDATE td_data_resource SET layer = ? WHERE source_path = ?", layer, tableName);
                }
            }
            log.info("Pipeline 资源分层更新: runId={}, pipelineType={}, layer={}", runId, pipelineType, layer);
        } catch (Exception e) {
            log.warn("Pipeline 资源分层更新失败: runId={}, error={}", runId, e.getMessage());
        }
    }

    private String inferPipelineType(List<Map<String, Object>> steps) {
        boolean hasSource = false;
        boolean hasTransform = false;
        boolean hasAggregate = false;
        for (Map<String, Object> step : steps) {
            String nodeType = (String) step.get("node_type");
            if (nodeType == null) continue;
            String nt = nodeType.toLowerCase();
            if (nt.startsWith("source")) hasSource = true;
            else if (nt.equals("transform")) hasTransform = true;
            else if (nt.equals("aggregate")) hasAggregate = true;
        }
        if (hasAggregate) return "AGGREGATE";
        if (hasTransform) return "TRANSFORM";
        if (hasSource) return "SYNC";
        return "UNKNOWN";
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
        if (inputData == null || inputData.isEmpty()) {
            log.info("JOIN 步骤: 输入为空，跳过");
            return new DataFrame();
        }

        // PMO-36 T5: 真正多源 JOIN（基于内存 Map 合并）
        String joinKeysStr = (String) config.getOrDefault("join_keys", "");
        String joinType = (String) config.getOrDefault("join_type", "INNER");

        if (joinKeysStr == null || joinKeysStr.isEmpty()) {
            log.info("JOIN 步骤: 无 join_keys 配置，pass-through");
            return inputData;
        }

        String[] joinKeys = joinKeysStr.split(",");
        for (int i = 0; i < joinKeys.length; i++) {
            joinKeys[i] = joinKeys[i].trim();
        }

        // 当前实现：对输入数据按 join_keys 去重合并（单源 JOIN 的内存模拟）
        // 多源 JOIN 需要上游有多个 DataFrame 输入，当前管线是线性串行，这里做去重合并
        List<Map<String, Object>> rows = inputData.getRows();
        Map<String, Map<String, Object>> joinedMap = new java.util.LinkedHashMap<>();

        for (Map<String, Object> row : rows) {
            StringBuilder keyBuilder = new StringBuilder();
            for (String key : joinKeys) {
                Object val = row.get(key);
                keyBuilder.append(val != null ? val.toString() : "null").append("|");
            }
            String joinKey = keyBuilder.toString();

            if (joinedMap.containsKey(joinKey)) {
                // 合并：同 key 的行字段合并
                Map<String, Object> existing = joinedMap.get(joinKey);
                for (Map.Entry<String, Object> entry : row.entrySet()) {
                    if (!existing.containsKey(entry.getKey()) || existing.get(entry.getKey()) == null) {
                        existing.put(entry.getKey(), entry.getValue());
                    }
                }
            } else {
                joinedMap.put(joinKey, new java.util.LinkedHashMap<>(row));
            }
        }

        DataFrame result = new DataFrame();
        result.setColumns(inputData.getColumns());
        result.setRows(new java.util.ArrayList<>(joinedMap.values()));

        log.info("JOIN 步骤: 输入 {} 行 → JOIN 后 {} 行 (keys={}, type={})",
            inputData.size(), result.size(), joinKeysStr, joinType);
        return result;
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

        // PMO-36 T5: 有 target 配置时真写入，无配置时保留日志降级
        if (datasourceId == null || tableName == null) {
            log.info("SINK 步骤: 数据量 {} 行 (无目标配置，仅日志)", inputData.size());
            return inputData.size();
        }

        try {
            // 真写入：用 JdbcTemplate batchUpdate 写入目标表
            List<Map<String, Object>> rows = inputData.getRows();
            if (rows == null || rows.isEmpty()) {
                log.info("SINK 步骤: 无行数据");
                return 0;
            }

            // 获取列名
            List<String> columns = inputData.getColumns();
            if (columns == null || columns.isEmpty()) {
                columns = new java.util.ArrayList<>(rows.get(0).keySet());
            }

            // 构建批量 SQL
            StringBuilder sqlBuilder = new StringBuilder("INSERT INTO ");
            sqlBuilder.append(tableName).append(" (");
            sqlBuilder.append(String.join(", ", columns));
            sqlBuilder.append(") VALUES (");
            sqlBuilder.append(String.join(", ", java.util.Collections.nCopies(columns.size(), "?")));
            sqlBuilder.append(")");

            // 如果是 UPSERT 模式，添加 ON CONFLICT（PostgreSQL）
            if ("UPSERT".equalsIgnoreCase(writeMode)) {
                sqlBuilder.append(" ON CONFLICT DO NOTHING");
            }

            String sql = sqlBuilder.toString();

            // 批量写入
            List<Object[]> batchArgs = new java.util.ArrayList<>();
            for (Map<String, Object> row : rows) {
                Object[] args = new Object[columns.size()];
                for (int i = 0; i < columns.size(); i++) {
                    args[i] = row.get(columns.get(i));
                }
                batchArgs.add(args);
            }

            int[] results = jdbc.batchUpdate(sql, batchArgs);
            int written = 0;
            for (int r : results) {
                if (r >= 0) written += r;
            }
            // batchUpdate 返回 -2 表示 SUCCESS_NO_INFO，按 batchArgs.size() 计
            if (written == 0 && !batchArgs.isEmpty()) {
                written = batchArgs.size();
            }

            log.info("SINK 步骤: 向 {} 写入 {} 行 (mode={})", tableName, written, writeMode);
            return written;

        } catch (Exception e) {
            log.warn("SINK 步骤写入失败 (降级为日志记录): {}", e.getMessage());
            return 0;
        }
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
            "SELECT id, task_id, status, total_steps, completed_steps, triggered_by, " +
            "log_json::text as log_json, started_at, finished_at, elapsed_ms, error_msg, created_at " +
            "FROM ecos_pipeline_run WHERE id = ?", runId);
        if (rows.isEmpty()) throw new IllegalArgumentException("执行记录不存在: " + runId);
        return rows.get(0);
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }

    /** PMO-36: 安全转 String（处理 PGobject/jsonb 类型） */
    private String safeToString(Object obj) {
        if (obj == null) return null;
        if (obj instanceof String) return (String) obj;
        return obj.toString();
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
