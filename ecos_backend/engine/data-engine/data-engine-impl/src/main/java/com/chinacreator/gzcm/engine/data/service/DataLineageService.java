package com.chinacreator.gzcm.engine.data.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

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

    /** 血缘拓扑重建互斥锁（防止并发重建） */
    private final Object rebuildLock = new Object();

    private final com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();

    public DataLineageService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
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

    // ── 拓扑全量重建（P1）──

    /**
     * 全量重建血缘：扫描 ecs_pipeline_definition（definition JSONB 里每个节点的 config.sql）
     * 与 ecos_pipeline_task（yaml_content）中的 SQL，调用 JSqlParser 解析为表级/字段级 nodes+edges，
     * 持久化到 ecos_data.ecos_data_lineage_node/edge。
     * 重建前清空旧数据，重建后返回聚合拓扑，供前端一次渲染全景图。
     * <p>
     * ⚠️ 历史缺口（PMO-W3）：原实现仅扫 ecs_pipeline_task.yaml_content，但 PipelineBuilder 创建的
     * pipeline 真正落到 ecs_pipeline_definition.definition / ecos_pipeline_node.config 里，
     * 导致"重新生成"返回 tasks_scanned=0 + emptyHint 误导用户。本次补齐 definition 扫描，
     * 节点级 SQL（SELECT/INSERT/UPDATE）逐条喂给 JSqlParser。
     *
     * @param limit 限制处理的 pipeline 数量（用于大规模场景的性能保护，0 = 不限制）
     * @return 聚合后的 nodes/edges 拓扑
     */
    public Map<String, Object> rebuildAndPersist(int limit) {
        synchronized (rebuildLock) {
            int taskLimit = limit > 0 ? limit : 0;
            // 1. 旧表：ecs_pipeline_task（YAML 风格） — 保留兼容
            List<Map<String, Object>> tasks;
            try {
                if (taskLimit > 0) {
                    tasks = jdbc.queryForList(
                        "SELECT id, name, yaml_content FROM ecos_pipeline_task WHERE yaml_content IS NOT NULL ORDER BY updated_at DESC LIMIT " + taskLimit);
                } else {
                    tasks = jdbc.queryForList(
                        "SELECT id, name, yaml_content FROM ecos_pipeline_task WHERE yaml_content IS NOT NULL ORDER BY updated_at DESC");
                }
            } catch (Exception e) {
                log.warn("rebuildAndPersist 查询 tasks 失败: {}", e.getMessage());
                tasks = List.of();
            }

            // 2. 主表：ecs_pipeline_definition.definition (JSONB) — 节点级 SQL 全量来源
            int scannedDefinitions = 0;
            List<Map<String, Object>> nodes = new ArrayList<>();
            List<Map<String, Object>> edges = new ArrayList<>();
            Set<String> nodeIds = new LinkedHashSet<>();
            Set<String> edgeKeys = new HashSet<>();
            Map<String, Map<String, Object>> nodeMap = new LinkedHashMap<>();
            List<Map<String, Object>> edgeList = new ArrayList<>();
            int parsedTasks = 0;
            int skippedSql = 0;

            for (Map<String, Object> def : scanDefinitions()) {
                String defId = (String) def.get("id");
                String defName = (String) def.get("name");
                String defJsonRaw = (String) def.get("definition");
                if (defJsonRaw == null || defJsonRaw.isBlank() || "{}".equals(defJsonRaw)) continue;
                boolean parsedAny = false;
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> defRoot = om.readValue(defJsonRaw, Map.class);
                    Object nodesArr = defRoot.get("nodes");
                    if (nodesArr instanceof List<?> arr && !arr.isEmpty()) {
                        for (Object nraw : arr) {
                            String sql = extractSqlFromNode(nraw);
                            if (sql == null || sql.isBlank()) continue;
                            try {
                                Map<String, Object> parsed = parser.parse(sql);
                                @SuppressWarnings("unchecked")
                                List<Map<String, String>> pNodes = (List<Map<String, String>>) parsed.getOrDefault("nodes", List.of());
                                @SuppressWarnings("unchecked")
                                List<Map<String, String>> pEdges = (List<Map<String, String>>) parsed.getOrDefault("edges", List.of());
                                if (pNodes.isEmpty() && pEdges.isEmpty()) {
                                    skippedSql++;
                                    continue;
                                }
                                parsedAny = true;
                                persistParsed(defId, defName, pNodes, pEdges, nodeIds, edgeKeys, nodeMap, edgeList);
                            } catch (Exception e) {
                                log.debug("rebuildAndPersist definition node SQL 解析跳过 (def={}): {}", defId, e.getMessage());
                            }
                        }
                    } else if (defNodeHasSql(defJsonRaw)) {
                        // 兜底：definitions 内嵌 config.sql（V25+ pipeline-node 表丢弃旧数据时的兼容）
                        List<String> sqls = extractSqlFromDefinitionsJson(defJsonRaw);
                        for (String sql : sqls) {
                            try {
                                Map<String, Object> parsed = parser.parse(sql);
                                @SuppressWarnings("unchecked")
                                List<Map<String, String>> pNodes = (List<Map<String, String>>) parsed.getOrDefault("nodes", List.of());
                                @SuppressWarnings("unchecked")
                                List<Map<String, String>> pEdges = (List<Map<String, String>>) parsed.getOrDefault("edges", List.of());
                                if (pNodes.isEmpty() && pEdges.isEmpty()) continue;
                                parsedAny = true;
                                persistParsed(defId, defName, pNodes, pEdges, nodeIds, edgeKeys, nodeMap, edgeList);
                            } catch (Exception e) {
                                log.debug("rebuildAndPersist definition 兜底解析跳过 (def={}): {}", defId, e.getMessage());
                            }
                        }
                    }
                } catch (Exception e) {
                    log.debug("rebuildAndPersist 查询 definitions 的 definition JSON 解析失败 (id={}): {}", defId, e.getMessage());
                    continue;
                }
                if (parsedAny) parsedTasks++;
                scannedDefinitions++;
            }

            // 3. 补充 ecs_pipeline_task (YAML)
            for (Map<String, Object> task : tasks) {
                String taskId = (String) task.get("id");
                String taskName = (String) task.get("name");
                String yaml = (String) task.get("yaml_content");
                if (yaml == null || yaml.isBlank()) continue;

                List<String> sqlBlocks = extractSqlFromYaml(yaml);
                boolean parsedAny = false;
                for (String sql : sqlBlocks) {
                    try {
                        Map<String, Object> parsed = parser.parse(sql);
                        @SuppressWarnings("unchecked")
                        List<Map<String, String>> pNodes = (List<Map<String, String>>) parsed.getOrDefault("nodes", List.of());
                        @SuppressWarnings("unchecked")
                        List<Map<String, String>> pEdges = (List<Map<String, String>>) parsed.getOrDefault("edges", List.of());
                        if (pNodes.isEmpty() && pEdges.isEmpty()) {
                            skippedSql++;
                            continue;
                        }
                        parsedAny = true;
                        persistParsed(taskId, taskName, pNodes, pEdges, nodeIds, edgeKeys, nodeMap, edgeList);
                    } catch (Exception e) {
                        log.debug("rebuildAndPersist 单 SQL 解析跳过 (taskId={}): {}", taskId, e.getMessage());
                    }
                }
                if (parsedAny) parsedTasks++;
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("nodes", new ArrayList<>(nodeMap.values()));
            result.put("edges", edgeList);
            result.put("total_nodes", nodeMap.size());
            result.put("total_edges", edgeList.size());
            result.put("tasks_scanned", tasks.size());
            result.put("definitions_scanned", scannedDefinitions);
            result.put("tasks_parsed", parsedTasks);
            result.put("sql_failed", skippedSql);
            log.info("rebuildAndPersist 完成: defs={} tasks={} parsed={} nodes={} edges={} sql_failed={}",
                    scannedDefinitions, tasks.size(), parsedTasks, nodeMap.size(), edgeList.size(), skippedSql);
            return result;
        }
    }

    /** 扫描 ecs_pipeline_definition（ACTIVE + DRAFT，排除 ARCHIVED）。 */
    private List<Map<String, Object>> scanDefinitions() {
        try {
            return jdbc.queryForList(
                "SELECT id, name, definition::text AS definition FROM ecs_pipeline_definition WHERE status != 'ARCHIVED' ORDER BY updated_at DESC");
        } catch (Exception e) {
            log.warn("rebuildAndPersist 查询 definitions 失败（表可能不同步）: {}", e.getMessage());
            return List.of();
        }
    }

    /** 从单个 pipeline 节点 JSON 对象里提取 SQL（兼容 config.sql / config.query / sql 字段）。 */
    @SuppressWarnings("unchecked")
    private String extractSqlFromNode(Object nraw) {
        if (!(nraw instanceof Map)) return null;
        Map<String, Object> n = (Map<String, Object>) nraw;
        Object cfg = n.get("config");
        if (cfg instanceof Map<?, ?> cm) {
            Object sql = cm.get("sql");
            if (sql == null) sql = cm.get("query");
            if (sql == null) sql = cm.get("expression");
            if (sql instanceof String s && !s.isBlank()) return s;
        }
        Object topSql = n.get("sql");
        if (topSql instanceof String s && !s.isBlank()) return s;
        return null;
    }

    /** 兜底：扫整个 definitions JSON 字符串里的 SQL 片段（YAML 提取器复用，task 风格兼容）。 */
    private List<String> extractSqlFromDefinitionsJson(String json) {
        List<String> out = new ArrayList<>();
        Matcher m = Pattern.compile("(?i)\"(?:sql|query|expression)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)+)\"").matcher(json);
        while (m.find()) {
            String s = m.group(1).replaceAll("\\\\\"", "").replaceAll("\\\\n", "\n").trim();
            if (s.length() > 10) out.add(s);
        }
        return out;
    }

    /** 节点 JSON 是否含任意 SQL 字段（兜底扫描用）。 */
    private boolean defNodeHasSql(String json) {
        return json.contains("\"sql\"") || json.contains("\"query\"") || json.contains("\"expression\"");
    }

    /** 将已解析的 nodes/edges 写入持久化表，并从内存聚合 Map 中累加。 */
    @SuppressWarnings("unchecked")
    private void persistParsed(String taskId, String taskName,
                                List<Map<String, String>> pNodes, List<Map<String, String>> pEdges,
                                Set<String> nodeIds, Set<String> edgeKeys,
                                Map<String, Map<String, Object>> nodeMap, List<Map<String, Object>> edgeList) {
        // 节点写入：内存去重 + 批量 INSERT ... ON CONFLICT DO NOTHING
        List<Object[]> nodeBatch = new ArrayList<>();
        for (Map<String, String> n : pNodes) {
            String id = n.get("id");
            if (id == null) continue;
            String nodeType = n.getOrDefault("type", "field");
            String table = n.getOrDefault("table", "");
            nodeBatch.add(new Object[]{id, nodeType, nodeIdToName(id, nodeType), table, taskId});
            if (!nodeIds.add(id)) continue;
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", id);
            node.put("type", nodeType);
            node.put("label", nodeIdToName(id, nodeType));
            node.put("table", table);
            node.put("pipeline_task_id", taskId);
            node.put("pipeline_task_name", taskName);
            nodeMap.put(id, node);
        }
        if (!nodeBatch.isEmpty()) {
            String sql = "INSERT INTO ecos_data.ecos_data_lineage_node " +
                         "(id, node_type, name, table_name, datasource_id, layer, properties, created_at, updated_at) " +
                         "VALUES (?,?,?,?, 'data', 'field', '{}'::jsonb, NOW(), NOW()) ON CONFLICT (id) DO NOTHING";
            try {
                jdbc.batchUpdate(sql, nodeBatch);
            } catch (Exception e) {
                log.warn("persistParsed 批量写 nodes 失败，先尝试建表后重试: {}", e.getMessage());
                tryCreateLineageTablesSafely();
                try {
                    jdbc.batchUpdate(sql, nodeBatch);
                } catch (Exception retry) {
                    log.warn("persistParsed 重读 nodes 仍失败（持久化降级，拓扑仍可返回）: {}", retry.getMessage());
                }
            }
        }

        // 边写入：内存去重 + 批量 INSERT，properties 用 jsonb_set 注入 task_name（避免 SQL 字符串拼接注入风险）
        List<Object[]> edgeBatch = new ArrayList<>();
        for (Map<String, String> e : pEdges) {
            String src = e.get("source");
            String tgt = e.get("target");
            String trans = e.get("transform");
            if (src == null || tgt == null) continue;
            String key = src + "|" + tgt + "|" + (trans == null ? "" : trans) + "|" + taskId;
            if (!edgeKeys.add(key)) continue;
            String edgeId = "lin_" + Long.toHexString(key.hashCode());
            String edgeType = (trans == null ? "direct" : trans);
            String transName = (trans == null ? "direct" : trans);
            edgeBatch.add(new Object[]{edgeId, src, tgt, edgeType, taskId, transName, taskName == null ? "" : taskName});
            Map<String, Object> en = new LinkedHashMap<>();
            en.put("id", edgeId);
            en.put("source", src);
            en.put("target", tgt);
            en.put("transform", edgeType);
            en.put("pipeline_task_id", taskId);
            en.put("pipeline_task_name", taskName);
            edgeList.add(en);
        }
        if (!edgeBatch.isEmpty()) {
            String sql = "INSERT INTO ecos_data.ecos_data_lineage_edge " +
                         "(id, source_node_id, target_node_id, edge_type, pipeline_task_id, transformation, properties, created_at) " +
                         "VALUES (?,?,?,?,?,?, jsonb_set('{}'::jsonb, '{task_name}', to_jsonb(?::text)), NOW()) " +
                         "ON CONFLICT (id) DO NOTHING";
            try {
                jdbc.batchUpdate(sql, edgeBatch);
            } catch (Exception e) {
                log.warn("persistParsed 批量写 edges 失败，先尝试建表后重试: {}", e.getMessage());
                tryCreateLineageTablesSafely();
                try {
                    jdbc.batchUpdate(sql, edgeBatch);
                } catch (Exception retry) {
                    log.warn("persistParsed 重读 edges 仍失败（持久化降级，拓扑仍可返回）: {}", retry.getMessage());
                }
            }
        }
    }

    /** 安全创建血缘持久化表（不阻塞）。表字段精简：只存必要字段，避免 schema 管理复杂度。 */
    private void tryCreateLineageTablesSafely() {
        try {
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS ecos_data.ecos_data_lineage_node (
                    id VARCHAR(128) PRIMARY KEY,
                    node_type VARCHAR(32) NOT NULL,
                    name VARCHAR(255) NOT NULL,
                    schema_name VARCHAR(100),
                    table_name VARCHAR(255),
                    datasource_id VARCHAR(64),
                    layer VARCHAR(32),
                    properties JSONB DEFAULT '{}'::jsonb,
                    created_at TIMESTAMP DEFAULT NOW(),
                    updated_at TIMESTAMP DEFAULT NOW()
                )
                """);
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS ecos_data.ecos_data_lineage_edge (
                    id VARCHAR(128) PRIMARY KEY,
                    source_node_id VARCHAR(128) NOT NULL,
                    target_node_id VARCHAR(128) NOT NULL,
                    edge_type VARCHAR(64) NOT NULL,
                    pipeline_task_id VARCHAR(64),
                    transformation VARCHAR(500),
                    properties JSONB DEFAULT '{}'::jsonb,
                    created_at TIMESTAMP DEFAULT NOW()
                )
                """);
            log.info("rebuildAndPersist 补建血缘表完成");
        } catch (Exception e) {
            log.warn("tryCreateLineageTablesSafely 失败: {}", e.getMessage());
        }
    }

    /** 节点 id → 显示名：表级节点直接取 table 名，字段级取 "table.column"。 */
    private static String nodeIdToName(String nodeId, String nodeType) {
        if (nodeId == null) return "";
        if ("table".equalsIgnoreCase(nodeType)) return nodeId;
        int dot = nodeId.lastIndexOf('.');
        if (dot >= 0 && dot < nodeId.length() - 1) return nodeId.substring(dot + 1);
        return nodeId;
    }

    /** 重建前清空旧血缘数据（保证幂等）。表不存在时忽略。 */
    public void clearLineageData() {
        synchronized (rebuildLock) {
            try {
                jdbc.execute("DELETE FROM ecos_data.ecos_data_lineage_edge");
                jdbc.execute("DELETE FROM ecos_data.ecos_data_lineage_node");
                log.info("clearLineageData 完成");
            } catch (Exception e) {
                log.warn("clearLineageData 失败（表可能不存在，将在重建时创建）: {}", e.getMessage());
            }
        }
    }

    /** 查询持久化拓扑（前端"重新生成"后可秒开，无需重新解析）。 */
    public Map<String, Object> getTopologyFromDb(String datasourceId) {
        try {
            String nodeSql = "SELECT id AS id, node_type AS type, name AS label, table_name AS table, pipeline_task_id " +
                    "FROM ecos_data.ecos_data_lineage_node ORDER BY created_at DESC";
            List<Map<String, Object>> nodes = jdbc.queryForList(nodeSql);
            String edgeSql = "SELECT id, source_node_id AS source, target_node_id AS target, edge_type AS transform, pipeline_task_id " +
                    "FROM ecos_data.ecos_data_lineage_edge";
            List<Map<String, Object>> edges = jdbc.queryForList(edgeSql);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("nodes", nodes);
            result.put("edges", edges);
            result.put("total_nodes", nodes.size());
            result.put("total_edges", edges.size());
            result.put("from_db", true);
            return result;
        } catch (Exception e) {
            log.warn("getTopologyFromDb 失败（表可能是空白状态）: {}", e.getMessage());
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("nodes", List.of());
            result.put("edges", List.of());
            result.put("total_nodes", 0);
            result.put("total_edges", 0);
            result.put("from_db", false);
            return result;
        }
    }

    // ── 影响度分析（P2）──

    /**
     * 影响度概览：从 startNode（节点 ID，如 "orders" 或 "orders.amount_cny"）出发，
     * 在持久化边上做双向 BFS（下游 = follow edges, 上游 = reverse edges），
     * 计算 N 层内的可达节点 + 风险评分（越近越强），输出 severity 等级。
     *
     * @param startNodeId 起点节点 ID（表名 / 字段名、允许 schema.qualified 形式会自动匹配末尾段）
     * @param depth       追溯层数上限（与前端"展开 N 层"按钮年级对应，0/负 = 1）
     * @return {@code { startNode, depth, severity, totalRisk, downstream: [[...hop...]], upstream: [[...]] }}
     */
    public Map<String, Object> getImpactOverview(String startNodeId, int depth) {
        int maxDepth = Math.max(1, Math.min(depth, 8));
        if (startNodeId == null || startNodeId.isBlank()) {
            return emptyImpact(startNodeId, maxDepth);
        }
        Map<String, Object> topo = getTopologyFromDb(null);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> edges = (List<Map<String, Object>>) topo.getOrDefault("edges", List.of());

        // 邻接表：source → targets（downstream），target → sources（upstream）
        Map<String, List<String>> downAdj = new LinkedHashMap<>();
        Map<String, List<String>> upAdj = new LinkedHashMap<>();
        for (Map<String, Object> e : edges) {
            Object s = e.get("source");
            Object t = e.get("target");
            if (s == null || t == null) continue;
            downAdj.computeIfAbsent(s.toString(), k -> new ArrayList<>()).add(t.toString());
            upAdj.computeIfAbsent(t.toString(), k -> new ArrayList<>()).add(s.toString());
        }

        // startNode 匹配：允许 "schema.table" 或 "table.column" 形式，匹配同段或前缀
        String canonicalStart = matchStartNode(startNodeId, downAdj.keySet(), upAdj.keySet());
        if (canonicalStart == null) {
            // 直接按原字符串 BFS，匹配失败时返回空
            canonicalStart = startNodeId;
        }

        List<Map<String, Object>> downstream = bfsImpact(canonicalStart, downAdj, maxDepth);
        List<Map<String, Object>> upstream = bfsImpact(canonicalStart, upAdj, maxDepth);

        // severity 由下游 reach 数的对数决定（下游影响 > 上游）
        int maxReach = downstream.size();
        String severity;
        if (maxReach == 0) severity = "NONE";
        else if (maxReach >= 8) severity = "CRITICAL";
        else if (maxReach >= 4) severity = "HIGH";
        else if (maxReach >= 2) severity = "MEDIUM";
        else severity = "LOW";
        int totalRisk = Math.min(100, maxReach * 15);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("startNode", startNodeId);
        result.put("canonicalStartNode", canonicalStart);
        result.put("matched", canonicalStart.equals(startNodeId));
        result.put("depth", maxDepth);
        result.put("severity", severity);
        result.put("totalRisk", totalRisk);
        result.put("downstream", downstream);
        result.put("upstream", upstream);
        result.put("branchCount", Math.max(downstream.size(), upstream.size()));
        return result;
    }

    /** 在已知节点中查找 startNode 是否匹配 key（支持末尾段匹配，如 "orders.amount" → "orders"）。 */
    private String matchStartNode(String raw, Collection<String> downs, Collection<String> ups) {
        // 1. 全等
        if (downs.contains(raw) || ups.contains(raw)) {
            return raw;
        }
        // 2. 尾段匹配（最后一 "." 后的部分）
        int lastDot = raw.lastIndexOf('.');
        if (lastDot >= 0) {
            String tail = raw.substring(lastDot + 1);
            for (String node : downs) {
                if (node.equalsIgnoreCase(tail)) return node;
            }
            for (String node : ups) {
                if (node.equalsIgnoreCase(tail)) return node;
            }
        }
        return raw;
    }

    /** 在给定邻接表上做 BFS，返回 N 层可达节点 + 每层节点 ID。 */
    private List<Map<String, Object>> bfsImpact(String start, Map<String, List<String>> adj, int maxDepth) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (start == null) return result;
        Set<String> visited = new HashSet<>();
        visited.add(start);
        // 队列元素: [id, depth]
        Deque<doubleKey> queue = new ArrayDeque<>();
        queue.add(new doubleKey(start, 1));
        while (!queue.isEmpty()) {
            doubleKey cur = queue.poll();
            List<String> nexts = adj.getOrDefault(cur.id, List.of());
            if (cur.depth > maxDepth) continue;
            for (String n : nexts) {
                if (visited.contains(n)) continue;
                visited.add(n);
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", n);
                item.put("hop", cur.depth);
                item.put("label", n);
                item.put("riskScore", Math.max(15, (int) Math.round(100 * Math.pow(0.8, cur.depth))));
                result.add(item);
                if (cur.depth < maxDepth) {
                    queue.add(new doubleKey(n, cur.depth + 1));
                }
            }
        }
        return result;
    }

    private Map<String, Object> emptyImpact(String startNodeId, int depth) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("startNode", startNodeId);
        result.put("depth", depth);
        result.put("severity", "NONE");
        result.put("totalRisk", 0);
        result.put("downstream", new ArrayList<>());
        result.put("upstream", new ArrayList<>());
        result.put("branchCount", 0);
        return result;
    }

    /** 简易三参数队列元素（id + depth） */
    private static class doubleKey {
        String id;
        int depth;
        doubleKey(String id, int depth) { this.id = id; this.depth = depth; }
    }
}
