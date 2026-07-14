package com.chinacreator.gzcm.datanet.pipeline;

import com.chinacreator.gzcm.datanet.connector.Connector;
import com.chinacreator.gzcm.datanet.connector.ConnectorFactory;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Pipeline 执行引擎 — 按 DAG 拓扑序串行执行节点。
 *
 * @author DataBridge Datanet Team
 */
@Service
public class PipelineExecutionService {

    private static final Logger log = LoggerFactory.getLogger(PipelineExecutionService.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final PipelineRepository repository;
    private final ConnectorFactory connectorFactory;
    private final JdbcTemplate jdbc;

    public PipelineExecutionService(PipelineRepository repository,
                                     ConnectorFactory connectorFactory,
                                     JdbcTemplate jdbc) {
        this.repository = repository;
        this.connectorFactory = connectorFactory;
        this.jdbc = jdbc;
    }

    /**
     * 执行 Pipeline：按 DAG 拓扑序串行执行节点。
     *
     * @param definitionId Pipeline 定义 ID
     * @return 执行记录
     */
    public PipelineExecution executePipeline(String definitionId) {
        PipelineDefinition def = repository.findDefinitionById(definitionId);
        if (def == null) {
            throw new IllegalArgumentException("Pipeline 定义不存在: " + definitionId);
        }

        // 创建执行记录
        PipelineExecution exec = new PipelineExecution();
        exec.setId(UUID.randomUUID().toString().replace("-", ""));
        exec.setDefinitionId(definitionId);
        exec.setStatus("PENDING");

        exec = repository.insertExecution(exec);
        log.info("Pipeline execution started: definitionId={}, executionId={}", definitionId, exec.getId());

        try {
            // 更新状态为 RUNNING
            repository.updateExecutionStatus(exec.getId(), "RUNNING", null, 0L);
            exec.setStatus("RUNNING");
            exec.setStartedAt(LocalDateTime.now());

            // 获取节点列表，按拓扑序排序
            List<PipelineNode> allNodes = repository.findNodesByDefinitionId(definitionId);
            if (allNodes.isEmpty()) {
                throw new IllegalStateException("Pipeline 没有节点");
            }

            // 拓扑排序 (Kahn's algorithm)
            List<PipelineNode> sorted = topologicalSort(allNodes);
            log.info("Pipeline nodes sorted: {} nodes, order: {}",
                    sorted.size(), sorted.stream().map(PipelineNode::getNodeId).toList());

            long totalRows = 0;
            for (PipelineNode node : sorted) {
                log.info("Executing node: nodeId={}, type={}", node.getNodeId(), node.getType());
                long nodeRows = executeNode(node);
                totalRows += nodeRows;
                log.info("Node executed: nodeId={}, rows={}", node.getNodeId(), nodeRows);
            }

            // 标记成功
            repository.updateExecutionStatus(exec.getId(), "COMPLETED", null, totalRows);
            exec.setStatus("COMPLETED");
            exec.setCompletedAt(LocalDateTime.now());
            exec.setRowsProcessed(totalRows);

            log.info("Pipeline execution completed: executionId={}, totalRows={}", exec.getId(), totalRows);
            return exec;

        } catch (Exception e) {
            log.error("Pipeline execution failed: executionId={}, error={}", exec.getId(), e.getMessage(), e);
            repository.updateExecutionStatus(exec.getId(), "FAILED", e.getMessage(), 0L);
            exec.setStatus("FAILED");
            exec.setErrorMessage(e.getMessage());
            exec.setCompletedAt(LocalDateTime.now());
            return exec;
        }
    }

    /**
     * 执行单个节点。
     */
    private long executeNode(PipelineNode node) throws Exception {
        Map<String, Object> config = parseConfig(node.getConfig());
        String type = node.getType();

        return switch (type) {
            case "SOURCE_JDBC" -> executeSourceJdbc(config);
            case "TRANSFORM_SQL" -> executeTransformSql(config);
            case "OUTPUT_OBJECT" -> executeOutputObject(config);
            default -> throw new IllegalArgumentException("Unsupported node type: " + type);
        };
    }

    /**
     * SOURCE_JDBC: 通过 ConnectorFactory 获取 JdbcConnector，执行 config.sql。
     */
    private long executeSourceJdbc(Map<String, Object> config) throws Exception {
        String sql = (String) config.get("sql");
        if (sql == null || sql.isEmpty()) {
            throw new IllegalArgumentException("SOURCE_JDBC: sql 不能为空");
        }

        // 通过 ConnectorFactory 获取 JDBC Connector
        Connector connector = connectorFactory.getConnector("JDBC");

        // 使用反射获取底层连接执行 SQL（简化实现：复用 JdbcTemplate 执行）
        // 实际上 SOURCE_JDBC 节点应连接外部数据源
        // 这里简化处理：使用系统 JdbcTemplate 执行
        jdbc.execute(sql);
        return 1; // 简化返回
    }

    /**
     * TRANSFORM_SQL: 用 JdbcTemplate 执行 config.sql。
     */
    private long executeTransformSql(Map<String, Object> config) {
        String sql = (String) config.get("sql");
        if (sql == null || sql.isEmpty()) {
            throw new IllegalArgumentException("TRANSFORM_SQL: sql 不能为空");
        }

        jdbc.execute(sql);
        return jdbc.update("SELECT 1"); // 返回影响行数占位
    }

    /**
     * OUTPUT_OBJECT: 插入结果到目标表。
     */
    @SuppressWarnings("unchecked")
    private long executeOutputObject(Map<String, Object> config) {
        String targetTable = (String) config.get("targetTable");
        if (targetTable == null || targetTable.isEmpty()) {
            throw new IllegalArgumentException("OUTPUT_OBJECT: targetTable 不能为空");
        }

        List<Map<String, Object>> rows = (List<Map<String, Object>>) config.get("rows");
        if (rows == null || rows.isEmpty()) {
            log.warn("OUTPUT_OBJECT: 没有数据行，跳过插入 targetTable={}", targetTable);
            return 0;
        }

        long count = 0;
        for (Map<String, Object> row : rows) {
            StringBuilder cols = new StringBuilder();
            StringBuilder vals = new StringBuilder();
            List<Object> params = new ArrayList<>();

            for (Map.Entry<String, Object> entry : row.entrySet()) {
                if (!cols.isEmpty()) {
                    cols.append(", ");
                    vals.append(", ");
                }
                cols.append(entry.getKey());
                vals.append("?");
                params.add(entry.getValue());
            }

            String sql = "INSERT INTO " + targetTable + " (" + cols + ") VALUES (" + vals + ")";
            jdbc.update(sql, params.toArray());
            count++;
        }

        log.info("OUTPUT_OBJECT: inserted {} rows into {}", count, targetTable);
        return count;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseConfig(String configStr) {
        try {
            if (configStr == null || configStr.isEmpty()) return Collections.emptyMap();
            return mapper.readValue(configStr, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse config JSON: {}", configStr, e);
            return Collections.emptyMap();
        }
    }

    /**
     * Kahn 拓扑排序 — 根据 depends_on (JSON数组) 构建 DAG 并返回执行顺序。
     * 无依赖的节点优先执行。存在循环依赖时抛异常。
     */
    private List<PipelineNode> topologicalSort(List<PipelineNode> nodes) {
        // nodeId → node 映射
        Map<String, PipelineNode> nodeMap = new LinkedHashMap<>();
        // nodeId → 入度 (有多少节点依赖我)
        Map<String, Integer> inDegree = new LinkedHashMap<>();
        // nodeId → [我依赖哪些节点] (反向边: A depends on B → B → A)
        Map<String, List<String>> children = new LinkedHashMap<>();

        for (PipelineNode node : nodes) {
            nodeMap.put(node.getNodeId(), node);
            inDegree.putIfAbsent(node.getNodeId(), 0);

            // 解析 depends_on
            List<String> deps = parseDependsOn(node.getDependsOn());
            for (String dep : deps) {
                children.computeIfAbsent(dep, k -> new ArrayList<>()).add(node.getNodeId());
                inDegree.merge(node.getNodeId(), 1, Integer::sum);
            }
        }

        // Kahn: 入度为0的节点入队
        Queue<String> queue = new ArrayDeque<>();
        for (PipelineNode node : nodes) {
            if (inDegree.getOrDefault(node.getNodeId(), 0) == 0) {
                queue.add(node.getNodeId());
            }
        }

        List<PipelineNode> result = new ArrayList<>();
        while (!queue.isEmpty()) {
            String current = queue.poll();
            result.add(nodeMap.get(current));

            for (String child : children.getOrDefault(current, Collections.emptyList())) {
                int newDegree = inDegree.merge(child, -1, Integer::sum);
                if (newDegree == 0) {
                    queue.add(child);
                }
            }
        }

        if (result.size() != nodes.size()) {
            // 循环依赖
            Set<String> remaining = new LinkedHashSet<>();
            for (PipelineNode n : nodes) remaining.add(n.getNodeId());
            result.forEach(r -> remaining.remove(r.getNodeId()));
            throw new IllegalStateException(
                    "Pipeline DAG 存在循环依赖，无法排序。剩余节点: " + remaining);
        }

        return result;
    }

    @SuppressWarnings("unchecked")
    private List<String> parseDependsOn(String dependsOn) {
        if (dependsOn == null || dependsOn.isEmpty() || "[]".equals(dependsOn)) {
            return Collections.emptyList();
        }
        try {
            return mapper.readValue(dependsOn, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse depends_on: {}", dependsOn, e);
            return Collections.emptyList();
        }
    }
}
