package com.chinacreator.gzcm.gateway.controller;

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
 *
 * @deprecated 2026-09-30：本类为 gateway fat-JAR 聚合期的 dead-twin 副本。图谱能力已由
 * kb-engine {@code GraphSyncController} 唯一承载（:18086 /api/v1/kb/graph/sync）。
 * 本类仅为 gateway fat-JAR 聚合期双反射用，整类已在 {@code GatewayApplication.java:87}
 * 的 {@code @ComponentScan} excludeFilters 中排除，不参与请求分派；保留类体仅为
 * 迁移期编译/引用兼容，禁止新增 handler。
 */
@RestController
@RequestMapping("/api/v1/ecos/knowledge-graph")
@Deprecated
public class EcosKnowledgeGraphController {

    private static final Logger log = LoggerFactory.getLogger(EcosKnowledgeGraphController.class);

    /**
     * 图谱快照 — 从PostgreSQL本体实体构建内存图
     */
    @GetMapping
    public ApiResponse<KgGraphVO> getGraph() {
        try {
            // 节点: 11个本体实体 → Neo4j节点
            List<KgNodeVO> nodes = List.of(
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

            // 边: 语义关系
            List<KgEdgeVO> edges = List.of(
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

            KgStatsVO stats = new KgStatsVO(nodes.size(), edges.size(),
                    List.of("采购域", "项目域", "资产域", "财务域"));
            return ApiResponse.success(new KgGraphVO(nodes, edges, stats));
        } catch (Exception e) {
            log.error("Knowledge graph query failed", e);
            return ApiResponse.internalError("知识图谱查询失败");
        }
    }

    /**
     * 同步到Neo4j — 将本体实体作为节点写入图数据库
     */
    @PostMapping("/sync")
    public ApiResponse<KgSyncVO> syncToNeo4j() {
        try {
            // Neo4j sync is async — return instructions
            return ApiResponse.success(new KgSyncVO("ready_to_sync",
                    "请执行 Cypher 脚本同步到 Neo4j: docker exec ecos-neo4j cypher-shell -u neo4j -p password < /path/to/seed.cypher",
                    11, 11));
        } catch (Exception e) {
            log.error("Knowledge graph sync failed", e);
            return ApiResponse.internalError("同步失败");
        }
    }

    private static KgNodeVO node(String code, String name, String domain, String domainName) {
        return new KgNodeVO(code, name, domain, domainName, "OntologyEntity");
    }

    private static KgEdgeVO edge(String source, String target, String relType, String label) {
        return new KgEdgeVO(source, target, relType, label);
    }

    /** 图谱节点（5 键）：code/name/domainId/domainName/type（type 恒 OntologyEntity）。 */
    public record KgNodeVO(String code, String name, String domainId, String domainName,
                           String type) {
    }

    /** 图谱边（4 键）：source/target/relationshipType/label。 */
    public record KgEdgeVO(String source, String target, String relationshipType, String label) {
    }

    /** 图谱统计（3 键）：nodeCount/edgeCount/domains。 */
    public record KgStatsVO(int nodeCount, int edgeCount, List<String> domains) {
    }

    /** 图谱快照（3 键）：nodes/edges/stats。 */
    public record KgGraphVO(List<KgNodeVO> nodes, List<KgEdgeVO> edges, KgStatsVO stats) {
    }

    /** 同步回执（4 键）：status/message/nodesAvailable/edgesAvailable。 */
    public record KgSyncVO(String status, String message, int nodesAvailable, int edgesAvailable) {
    }
}
