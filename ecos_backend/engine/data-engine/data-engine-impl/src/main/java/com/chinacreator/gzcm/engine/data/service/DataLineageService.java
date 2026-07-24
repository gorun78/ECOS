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
                    String clean = dep.trim().replaceAll("[\\[\\]\"]", "");
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
}
