package com.chinacreator.gzcm.engine.data.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DataLineageService {

    private static final Logger log = LoggerFactory.getLogger(DataLineageService.class);

    private final JdbcTemplate jdbc;
    private final SqlLineageParser parser = new SqlLineageParser();

    /** SQL 提取正则：匹配 YAML 块中 sql: / query: 后的内容 */
    private static final Pattern SQL_EXTRACT_PAT = Pattern.compile(
        "(?i)(?:sql|query|expression)\\s*:\\s*(?:\\||>)\\s*\\n(.*?)(?=\\n\\S|\\Z)",
        Pattern.DOTALL);

    public DataLineageService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    public void init() {
        ensureSchema();
    }

    private void ensureSchema() {
        try {
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS ecos_data.ecos_data_lineage_node (
                    id VARCHAR(64) PRIMARY KEY,
                    node_type VARCHAR(20) NOT NULL,
                    name VARCHAR(200) NOT NULL,
                    schema_name VARCHAR(100),
                    table_name VARCHAR(200),
                    datasource_id VARCHAR(64),
                    layer VARCHAR(20),
                    properties JSONB DEFAULT '{}',
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS ecos_data.ecos_data_lineage_edge (
                    id VARCHAR(64) PRIMARY KEY,
                    source_node_id VARCHAR(64) NOT NULL,
                    target_node_id VARCHAR(64) NOT NULL,
                    edge_type VARCHAR(30) NOT NULL,
                    pipeline_task_id VARCHAR(64),
                    transformation VARCHAR(500),
                    properties JSONB DEFAULT '{}',
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
            log.info("Data Lineage tables ready");
        } catch (Exception e) {
            log.warn("Data Lineage table init warning: {}", e.getMessage());
        }
    }

    // ── SQL 血缘：字段级解析 ──

    /**
     * 根据 datasourceId 和 tableName 获取字段级血缘关系。
     * <p>
     * 从 ecos_pipeline_task 表的 yaml_content 中提取包含目标表名的 SQL，
     * 使用 JSqlParser 解析为 nodes/edges 图结构。
     * </p>
     *
     * @param datasourceId 数据源 ID
     * @param tableName    表名
     * @return 包含 nodes / edges / total_nodes / total_edges 的 Map
     */
    public Map<String, Object> getLineage(String datasourceId, String tableName) {
        if (tableName == null || tableName.isBlank()) {
            return emptyLineageResult();
        }

        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();
        Set<String> seenNodeIds = new LinkedHashSet<>();

        // 1. 从 pipeline_tasks 查找包含目标表名的任务
        List<Map<String, Object>> tasks;
        try {
            tasks = jdbc.queryForList(
                "SELECT id, name, yaml_content FROM ecos_pipeline_task WHERE yaml_content ILIKE ?",
                "%" + tableName + "%");
        } catch (Exception e) {
            log.warn("查询 pipeline_tasks 失败: {}", e.getMessage());
            tasks = List.of();
        }

        // 2. 如果是特定数据源，进一步过滤（通过 yaml_content 中 datasource 引用）
        List<Map<String, Object>> filtered = new ArrayList<>();
        if (datasourceId != null && !datasourceId.isBlank()) {
            for (Map<String, Object> task : tasks) {
                String yaml = (String) task.get("yaml_content");
                if (yaml != null && yaml.contains(datasourceId)) {
                    filtered.add(task);
                }
            }
        } else {
            filtered = tasks;
        }

        // 3. 对每个任务提取 SQL 并解析
        for (Map<String, Object> task : filtered) {
            String taskId = (String) task.get("id");
            String taskName = (String) task.get("name");
            String yaml = (String) task.get("yaml_content");
            if (yaml == null) continue;

            // 从 yaml 中提取 SQL 块
            List<String> sqlBlocks = extractSqlFromYaml(yaml);
            if (sqlBlocks.isEmpty()) {
                // 如果 YAML 没有显式 sql: 块，尝试将整个 yaml 作为 SQL 解析
                // （某些简单任务可能直接存储 SQL）
                parseAndMerge(taskId, taskName, yaml, tableName, nodes, edges, seenNodeIds);
            } else {
                for (String sql : sqlBlocks) {
                    if (sql.contains(tableName)) {
                        parseAndMerge(taskId, taskName, sql, tableName, nodes, edges, seenNodeIds);
                    }
                }
            }
        }

        // 4. 如果没有找到任何血缘，返回空结果（非错误）
        if (nodes.isEmpty()) {
            return emptyLineageResult();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("datasource_id", datasourceId);
        result.put("table_name", tableName);
        result.put("nodes", nodes);
        result.put("edges", edges);
        result.put("total_nodes", nodes.size());
        result.put("total_edges", edges.size());
        result.put("pipeline_count", filtered.size());
        return result;
    }

    private void parseAndMerge(String taskId, String taskName, String sql, String targetTable,
                                List<Map<String, Object>> allNodes, List<Map<String, Object>> allEdges,
                                Set<String> seenIds) {
        try {
            Map<String, Object> lineage = parser.parse(sql);
            @SuppressWarnings("unchecked")
            List<Map<String, String>> parsedNodes = (List<Map<String, String>>) lineage.get("nodes");
            @SuppressWarnings("unchecked")
            List<Map<String, String>> parsedEdges = (List<Map<String, String>>) lineage.get("edges");

            if (parsedNodes != null) {
                for (Map<String, String> n : parsedNodes) {
                    String id = n.get("id");
                    if (id == null || !seenIds.add(id)) continue;
                    Map<String, Object> node = new LinkedHashMap<>();
                    node.put("id", id);
                    node.put("type", n.get("type"));
                    node.put("table", n.get("table"));
                    node.put("pipeline_task_id", taskId);
                    node.put("pipeline_task_name", taskName);
                    allNodes.add(node);
                }
            }

            if (parsedEdges != null) {
                for (Map<String, String> e : parsedEdges) {
                    Map<String, Object> edge = new LinkedHashMap<>();
                    edge.put("source", e.get("source"));
                    edge.put("target", e.get("target"));
                    edge.put("transform", e.getOrDefault("transform", "read"));
                    edge.put("pipeline_task_id", taskId);
                    allEdges.add(edge);
                }
            }
        } catch (Exception e) {
            log.debug("SQL 解析跳过 (taskId={}): {}", taskId, e.getMessage());
        }
    }

    /**
     * 从 YAML 内容中提取 SQL 块。
     * 支持格式：
     * <pre>
     *   sql: |
     *     SELECT ...
     *   query: >
     *     SELECT ...
     *   expression: |
     *     INSERT INTO ...
     * </pre>
     */
    private List<String> extractSqlFromYaml(String yaml) {
        List<String> sqls = new ArrayList<>();
        if (yaml == null) return sqls;

        // 匹配 YAML 多行文本块
        Matcher m = SQL_EXTRACT_PAT.matcher(yaml);
        while (m.find()) {
            String block = m.group(1).trim();
            if (!block.isEmpty()) {
                // 去除 YAML 缩进
                block = block.replaceAll("(?m)^\\s{2,}", "").trim();
                if (block.length() > 10) {
                    sqls.add(block);
                }
            }
        }

        // 兼容格式：sql: "SELECT ..." (单行双引号)
        Matcher singleLine = Pattern.compile(
            "(?i)(?:sql|query|expression)\\s*:\\s*\"([^\"]+)\"",
            Pattern.DOTALL).matcher(yaml);
        while (singleLine.find()) {
            String sql = singleLine.group(1).trim();
            if (sql.length() > 10) {
                sqls.add(sql);
            }
        }

        return sqls;
    }

    // ── 已有方法 (保留不变) ──

    public Map<String, Object> getPipelineLineage(String taskId) {
        Map<String, Object> task = jdbc.queryForMap(
            "SELECT yaml_content FROM ecos_pipeline_task WHERE id = ?", taskId);
        String yaml = (String) task.get("yaml_content");
        return parseYamlLineage(yaml);
    }

    public List<Map<String, Object>> listNodes() {
        try {
            return jdbc.queryForList(
                "SELECT id, name, node_type as type, datasource_id, schema_name, table_name, layer FROM ecos_data.ecos_data_lineage_node ORDER BY name");
        } catch (Exception e) {
            return List.of();
        }
    }

    public List<Map<String, Object>> listEdges() {
        try {
            return jdbc.queryForList(
                "SELECT id, source_node_id as source_id, target_node_id as target_id, edge_type, pipeline_task_id as pipeline_id FROM ecos_data.ecos_data_lineage_edge ORDER BY source_node_id");
        } catch (Exception e) {
            return List.of();
        }
    }

    public Map<String, Object> buildTopology(List<String> pipelineIds, boolean includeDb, boolean includeTables) {
        List<Map<String, Object>> allNodes = new ArrayList<>();
        List<Map<String, Object>> allEdges = new ArrayList<>();
        Set<String> seenNodeIds = new HashSet<>();

        for (String pid : pipelineIds) {
            try {
                Map<String, Object> task = jdbc.queryForMap(
                    "SELECT name, yaml_content FROM ecos_pipeline_task WHERE id = ?", pid);
                Map<String, Object> lineage = parseYamlLineage((String) task.get("yaml_content"));
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> nodes = (List<Map<String, Object>>) lineage.get("nodes");
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> edges = (List<Map<String, Object>>) lineage.get("edges");
                if (nodes != null) {
                    for (Map<String, Object> n : nodes) {
                        if (seenNodeIds.add((String) n.get("id"))) {
                            allNodes.add(n);
                        }
                    }
                }
                if (edges != null) allEdges.addAll(edges);
            } catch (Exception ignored) {
            }
        }

        if (includeDb || includeTables) {
            try {
                List<Map<String, Object>> dbNodes = jdbc.queryForList(
                    "SELECT DISTINCT datasource_id as id, 'datasource' as type, datasource_id as datasource FROM ecos_data.ecos_data_lineage_node WHERE datasource_id IS NOT NULL LIMIT 10");
                for (Map<String, Object> n : dbNodes) {
                    if (seenNodeIds.add((String) n.get("id"))) {
                        n.put("label", n.get("id"));
                        allNodes.add(n);
                    }
                }
            } catch (Exception ignored) {
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("nodes", allNodes);
        result.put("edges", allEdges);
        result.put("total_nodes", allNodes.size());
        result.put("total_edges", allEdges.size());
        return result;
    }

    public Map<String, Object> parseYamlLineage(String yaml) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();

        String[] blocks = yaml.split("(?=- id:)");
        Map<String, String[]> nodeInfo = new LinkedHashMap<>();
        List<String> order = new ArrayList<>();

        for (String block : blocks) {
            Matcher m = Pattern.compile("- id:\\s*(\\S+)", Pattern.MULTILINE).matcher(block);
            if (!m.find()) continue;
            String nodeId = m.group(1);
            String type = extractYaml(block, "type:\\s*(\\S+)");
            String table = extractYaml(block, "table:\\s*\"?([^\"\\n]+?)\"?\\s*$");
            if (table == null) table = extractYaml(block, "table:\\s*\"?([^\"\\n]+?)\"?\\s*\\n");

            nodeInfo.put(nodeId, new String[]{type != null ? type : "unknown", table != null ? table : nodeId});
            order.add(nodeId);

            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", nodeId);
            node.put("type", type != null ? type : "unknown");
            node.put("label", table != null ? table : nodeId);
            node.put("table_name", table);
            int colCount = 0;
            Matcher colM = Pattern.compile("- field:|column:|columns:").matcher(block);
            while (colM.find()) colCount++;
            node.put("column_count", colCount > 0 ? colCount : 0);
            nodes.add(node);
        }

        for (Map.Entry<String, String[]> entry : nodeInfo.entrySet()) {
            String nodeId = entry.getKey();
            Pattern depPat = Pattern.compile("- id:\\s*" + Pattern.quote(nodeId) + ".*?dependsOn:\\s*\\[(.*?)\\]", Pattern.DOTALL);
            Matcher dm = depPat.matcher(yaml);
            if (dm.find()) {
                String deps = dm.group(1);
                for (String dep : deps.split(",")) {
                    String clean = dep.trim().replaceAll("[\\[\\]\\\"]", "");
                    if (!clean.isEmpty()) {
                        Map<String, Object> edge = new LinkedHashMap<>();
                        edge.put("source", clean);
                        edge.put("target", nodeId);
                        edges.add(edge);
                    }
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("nodes", nodes);
        result.put("edges", edges);
        result.put("total_nodes", nodes.size());
        result.put("total_edges", edges.size());
        return result;
    }

    private String extractYaml(String text, String regex) {
        Matcher m = Pattern.compile(regex, Pattern.MULTILINE).matcher(text);
        return m.find() ? m.group(1).trim() : null;
    }

    private Map<String, Object> emptyLineageResult() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("nodes", List.of());
        result.put("edges", List.of());
        result.put("total_nodes", 0);
        result.put("total_edges", 0);
        return result;
    }
}
