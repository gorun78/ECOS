package com.chinacreator.gzcm.engine.data.lineage;

import com.chinacreator.gzcm.engine.data.lineage.dao.EcosDataLineageMapper;
import com.chinacreator.gzcm.engine.data.pipeline.PipelineNode;
import com.chinacreator.gzcm.engine.data.pipeline.PipelineRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * F02-11 血缘自动派生 — 从 pipeline <b>definition（DAG）</b> 派生血缘，不再扫 YAML。
 *
 * <p>设计 详细设计-02 §F02-11：血缘从 definition 派生。本服务把一次管道执行成功后
 * 消费者（{@link LineagePipelineEventListener}）拿到的 definitionId 展开为
 * {@code source(table) → node → sink(table)} 的 lineage node 行与 edge 行，
 * 落入 {@code ecos_data.ecos_data_lineage_node / ecos_data.ecos_data_lineage_node}
 * （限定名 + {@code #{} } 参数化，替代旧 {@code yaml_content ILIKE} 全表扫与 LIMIT 拼接）。</p>
 *
 * <p>派生规则（1 definition → N node + (N-1 相邻) edge）：</p>
 * <ul>
 *   <li>每个 DAG 节点 → 1 个 lineage node（node_type 按前缀归一：SOURCE/TRANSFORM/TARGET）；</li>
 *   <li>每个 dependsOn 依赖 → 1 条 edge（前驱节点 → 本节点，edge_type=DERIVATION）；</li>
 *   <li>节点主键 / 边主键由 (definitionId, nodeId) 确定性派生（{@link UUID#nameUUIDFromBytes}），
 *       保证同 definition 重复派生幂等（mapper 端 ON CONFLICT DO NOTHING 兜底）。</li>
 * </ul>
 *
 * <p>不直连 JdbcTemplate（后端开发规范：Service 用自己 DAO/Mapper）；数据访问统一走
 * {@link EcosDataLineageMapper}。</p>
 */
@Service
public class LineageDerivationService {

    private static final Logger log = LoggerFactory.getLogger(LineageDerivationService.class);

    /** 类型前缀 → lineage node_type。 */
    private static final String NODE_TYPE_SOURCE = "SOURCE";
    private static final String NODE_TYPE_TRANSFORM = "TRANSFORM";
    private static final String NODE_TYPE_TARGET = "TARGET";

    private final PipelineRepository repository;
    private final EcosDataLineageMapper mapper;
    private final ObjectMapper om = new ObjectMapper();

    public LineageDerivationService(PipelineRepository repository, EcosDataLineageMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    /**
     * 从 pipeline definition 派生血缘并落库。
     *
     * <p>流程：读取 definition 的 DAG 节点 → 归一节点类型 / 抽取源表·目标表 →
     * 按取决于上溯建边 → 幂等清空该 definition 旧派生行 → 批量写入 node/edge。
     * 由调用方（事件监听器）保证在"管道执行成功"语义下调用的。</p>
     *
     * @param definitionId pipeline 定义 ID（= PipelineEvent.pipelineId）
     * @return 派生统计（nodeCount / edgeCount）
     */
    @Transactional
    public DerivationSummary deriveFromDefinition(String definitionId) {
        if (definitionId == null || definitionId.isBlank()) {
            throw new IllegalArgumentException("definitionId 必填（F02-11）");
        }
        List<PipelineNode> nodes = repository.findNodesByDefinitionId(definitionId);
        if (nodes == null || nodes.isEmpty()) {
            log.info("血缘派生跳过：definition {} 无节点", definitionId);
            return new DerivationSummary(definitionId, 0, 0);
        }

        // 1. 构建 node 行（DAG 节点 → lineage node）
        List<Map<String, Object>> nodeRows = new ArrayList<>(nodes.size());
        for (PipelineNode node : nodes) {
            String type = node.getType() == null ? "" : node.getType().toUpperCase();
            Map<String, Object> config = parseConfig(node.getConfig());
            nodeRows.add(nodeRow(definitionId, node.getNodeId(), type, config));
        }

        // 2. 构建 edge 行（每个 dependsOn 依赖 → 前驱节点 → 本节点）
        List<Map<String, Object>> edgeRows = new ArrayList<>();
        for (PipelineNode node : nodes) {
            for (String dep : parseDependsOn(node.getDependsOn())) {
                edgeRows.add(edgeRow(definitionId, dep, node.getNodeId(), node.getType()));
            }
        }

        // 3. 幂等：清理该 definition 旧派生行（重建可重复执行），再批量写入
        mapper.deleteNodesByPipelineId(definitionId);
        mapper.deleteEdgesByPipelineId(definitionId);
        if (!nodeRows.isEmpty()) {
            mapper.insertNodes(nodeRows);
        }
        if (!edgeRows.isEmpty()) {
            mapper.insertEdges(edgeRows);
        }

        log.info("血缘派生完成：definition={} nodes={} edges={}", definitionId, nodeRows.size(), edgeRows.size());
        return new DerivationSummary(definitionId, nodeRows.size(), edgeRows.size());
    }

    /** 读取当前增量水位线（无派生记录时返回 null）。 */
    public LocalDateTime currentWatermark() {
        return mapper.maxWatermark();
    }

    /** 写入增量重建水位线。 */
    @Transactional
    public void touchWatermark(LocalDateTime ts) {
        mapper.touchWatermark(ts);
    }

    // ── 派生映射 ────────────────────────────────────────────────────

    /** DAG 节点 → lineage node 行（表名按节点类型 best-effort 抽取）。 */
    private Map<String, Object> nodeRow(String definitionId, String nodeId, String type,
                                        Map<String, Object> config) {
        String table = extractTable(type, config);
        String nodeType = classifyNodeType(type);
        String ref = (table != null && !table.isBlank()) ? table : nodeId;
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("definitionId", definitionId);
        props.put("nodeId", nodeId);
        props.put("pipelineNodeType", type);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", deterministicId("lineage-node", definitionId, nodeId));
        row.put("nodeType", nodeType);
        row.put("name", truncate(ref, 200));
        row.put("schemaName", null);
        row.put("tableName", table);
        row.put("datasourceId", config.get("datasourceId") != null
                ? String.valueOf(config.get("datasourceId"))
                : (config.get("targetDatasourceId") != null ? String.valueOf(config.get("targetDatasourceId")) : null));
        row.put("layer", null);
        row.put("properties", toJson(props));
        row.put("pipelineTaskId", definitionId);
        return row;
    }

    /** dependsOn 依赖 → lineage edge 行（前驱节点 → 本节点）。 */
    private Map<String, Object> edgeRow(String definitionId, String sourceNodeId, String targetNodeId,
                                        String targetType) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("definitionId", definitionId);
        String edgeType = classifyNodeType(targetType == null ? "" : targetType.toUpperCase()).equals(NODE_TYPE_TARGET)
                ? "DATA_FLOW" : "DERIVATION";

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", deterministicId("lineage-edge", definitionId, sourceNodeId + "->" + targetNodeId));
        row.put("sourceNodeId", nodeIdToLineageId(definitionId, sourceNodeId));
        row.put("targetNodeId", nodeIdToLineageId(definitionId, targetNodeId));
        row.put("edgeType", edgeType);
        row.put("pipelineTaskId", definitionId);
        row.put("transformation", targetType == null ? null : truncate(targetType, 500));
        row.put("properties", toJson(props));
        return row;
    }

    /** 节点类型前缀 → lineage node_type（SOURCE/TRANSFORM/TARGET）。 */
    private static String classifyNodeType(String type) {
        if (type.startsWith("SOURCE")) {
            return NODE_TYPE_SOURCE;
        }
        if (type.startsWith("SINK") || type.startsWith("OUTPUT")) {
            return NODE_TYPE_TARGET;
        }
        return NODE_TYPE_TRANSFORM;
    }

    /**
     * 按节点类型 best-effort 抽取关联表名：
     * <ul>
     *   <li>SINK/OUTPUT：config.table / targetTable；</li>
     *   <li>SOURCE：config.table，其次从 config.sql 的 FROM/INTO 首个限定表名。</li>
     * </ul>
     */
    private static String extractTable(String type, Map<String, Object> config) {
        Object tableObj = config.get("table");
        if (tableObj == null) {
            tableObj = config.get("targetTable");
        }
        if (tableObj instanceof String s && !s.isBlank()) {
            return s.trim();
        }
        if (type.startsWith("SOURCE") || type.startsWith("TRANSFORM")) {
            Object sqlObj = config.get("sql");
            if (sqlObj instanceof String sql) {
                return firstTableInSql(sql);
            }
        }
        return null;
    }

    /** 从 SQL 抽首个 FROM/INTO 后的表名（best-effort，去 schema 限定后缀保留限定名）。 */
    private static String firstTableInSql(String sql) {
        if (sql == null) {
            return null;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(?i)\\b(?:FROM|INTO)\\s+([A-Za-z_][\\w.]*)")
                .matcher(sql);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    // ── 确定性 ID 与解析 ──────────────────────────────────────────────

    /** (前缀, definitionId, 键) → 确定性 lineage 主键（重派生幂等，VARCHAR(36)）。 */
    private static String deterministicId(String prefix, String definitionId, String key) {
        return UUID.nameUUIDFromBytes(
                (prefix + "|" + definitionId + "|" + key).getBytes(StandardCharsets.UTF_8)).toString();
    }

    /** nodeId → 该节点对应的 lineage node 主键（edge 端点引用）。 */
    private static String nodeIdToLineageId(String definitionId, String nodeId) {
        return deterministicId("lineage-node", definitionId, nodeId);
    }

    /** 解析节点 config JSON（空/异常 → 空 Map，不阻断派生）。 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseConfig(String configStr) {
        try {
            if (configStr == null || configStr.isBlank() || "null".equals(configStr)) {
                return new LinkedHashMap<>();
            }
            Map<String, Object> parsed = om.readValue(configStr, new TypeReference<Map<String, Object>>() {});
            return parsed != null ? parsed : new LinkedHashMap<>();
        } catch (Exception e) {
            log.debug("血缘派生节点 config 解析失败，按空配置处理: {}", e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    /** 解析 dependsOn JSON 数组（空/异常 → 空列表）。 */
    private List<String> parseDependsOn(String dependsOn) {
        try {
            if (dependsOn == null || dependsOn.isBlank() || "[]".equals(dependsOn)) {
                return List.of();
            }
            List<String> parsed = om.readValue(dependsOn, new TypeReference<List<String>>() {});
            if (parsed == null) {
                return List.of();
            }
            List<String> out = new ArrayList<>();
            for (String s : parsed) {
                if (s != null && !s.isBlank()) {
                    out.add(s);
                }
            }
            return out;
        } catch (Exception e) {
            log.debug("血缘派生 dependsOn 解析失败，按无依赖处理: {}", e.getMessage());
            return List.of();
        }
    }

    private static String toJson(Object o) {
        try {
            return new ObjectMapper().writeValueAsString(o);
        } catch (Exception e) {
            return "{}";
        }
    }

    private static String truncate(String v, int max) {
        if (v == null) {
            return null;
        }
        return v.length() <= max ? v : v.substring(0, max);
    }

    /** 派生结果统计。 */
    public record DerivationSummary(String definitionId, int nodeCount, int edgeCount) {
    }
}
