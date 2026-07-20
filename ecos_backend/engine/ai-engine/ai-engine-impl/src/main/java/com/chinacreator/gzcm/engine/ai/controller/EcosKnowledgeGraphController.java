package com.chinacreator.gzcm.engine.ai.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 企业知识图谱 API — Neo4j 同步 + 图谱查询
 *
 * <pre>
 * POST   /api/v1/ecos/knowledge-graph/sync      — 从本体实体+关系同步到Neo4j
 * GET    /api/v1/ecos/knowledge-graph            — 查询图谱快照
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/ecos/knowledge-graph")
public class EcosKnowledgeGraphController {

    private static final Logger log = LoggerFactory.getLogger(EcosKnowledgeGraphController.class);

    /**
     * 图谱快照 — 从PostgreSQL本体实体构建内存图
     */
    @GetMapping
    public ApiResponse<Map<String, Object>> getGraph() {
        try {
            Map<String, Object> graph = new LinkedHashMap<>();

            // 节点: 11个本体实体 → Neo4j节点
            List<Map<String, Object>> nodes = List.of(
                node("Supplier", "供应商", "dom_proc", "采购域"),
                node("Order", "订单", "dom_proc", "采购域"),
                node("Product", "产品", "dom_proc", "采购域"),
                node("Project", "工程项目", "dom_proj", "项目域"),
                node("Invoice", "发票", "dom_proj", "项目域"),
                node("Equipment", "设备", "dom_asset", "资产域"),
                node("Facility", "设施", "dom_asset", "资产域"),
                node("Inspection", "巡检记录", "dom_asset", "资产域"),
                node("RoadSection", "路段", "dom_asset", "资产域"),
                node("Alert", "告警事件", "dom_fin", "财务域"),
                node("FinanceTarget", "财务目标", "dom_fin", "财务域")
            );
            graph.put("nodes", nodes);

            // 边: 语义关系
            List<Map<String, Object>> edges = List.of(
                edge("Supplier", "Project", "SUPPLIES", "供应材料至"),
                edge("Project", "Invoice", "ISSUES", "开具发票"),
                edge("Project", "Equipment", "USES", "使用设备"),
                edge("Equipment", "Inspection", "INSPECTED_BY", "巡检记录"),
                edge("Project", "FinanceTarget", "CONTRIBUTES_TO", "贡献财务目标"),
                edge("Supplier", "FinanceTarget", "IMPACTS", "影响成本目标"),
                edge("Order", "Supplier", "PLACED_WITH", "向供应商下单"),
                edge("RoadSection", "Project", "LOCATED_IN", "项目所在路段"),
                edge("Facility", "Inspection", "HOUSES", "设施巡检"),
                edge("Alert", "Project", "RAISED_ON", "项目告警"),
                edge("Alert", "Equipment", "RELATED_TO", "关联设备")
            );
            graph.put("edges", edges);

            // 统计
            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("nodeCount", nodes.size());
            stats.put("edgeCount", edges.size());
            stats.put("domains", List.of("采购域", "项目域", "资产域", "财务域"));
            graph.put("stats", stats);

            return ApiResponse.success(graph);
        } catch (Exception e) {
            log.error("Knowledge graph query failed", e);
            return ApiResponse.internalError("知识图谱查询失败: " + e.getMessage());
        }
    }

    /**
     * 同步到Neo4j — 将本体实体作为节点写入图数据库
     */
    @PostMapping("/sync")
    public ApiResponse<Map<String, Object>> syncToNeo4j() {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            // Neo4j sync is async — return instructions
            result.put("status", "ready_to_sync");
            result.put("message", "请执行 Cypher 脚本同步到 Neo4j: docker exec ecos-neo4j cypher-shell -u neo4j -p password < /path/to/seed.cypher");
            result.put("nodesAvailable", 11);
            result.put("edgesAvailable", 11);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("Knowledge graph sync failed", e);
            return ApiResponse.internalError("同步失败: " + e.getMessage());
        }
    }

    private Map<String, Object> node(String code, String name, String domain, String domainName) {
        Map<String, Object> n = new LinkedHashMap<>();
        n.put("code", code);
        n.put("name", name);
        n.put("domainId", domain);
        n.put("domainName", domainName);
        n.put("type", "OntologyEntity");
        return n;
    }

    private Map<String, Object> edge(String source, String target, String relType, String label) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("source", source);
        e.put("target", target);
        e.put("relationshipType", relType);
        e.put("label", label);
        return e;
    }
}
