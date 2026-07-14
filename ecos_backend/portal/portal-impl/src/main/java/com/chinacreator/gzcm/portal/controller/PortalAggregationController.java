package com.chinacreator.gzcm.portal.controller;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import com.chinacreator.gzcm.common.base.ApiResponse;

/**
 * 门户聚合 API — 跨模块数据聚合，提供统一入口
 *
 * <pre>
 * GET /api/portal/dashboard       — 全局仪表盘 (各模块汇总统计)
 * GET /api/portal/search          — 统一搜索 (?q=keyword)
 * GET /api/portal/recent          — 最近更新 (?limit=20)
 * GET /api/portal/stats           — 系统统计 (文件数/模块数/数据量)
 * GET /api/portal/modules         — 模块清单
 * </pre>
 */
@RestController
@RequestMapping("/api/portal")
public class PortalAggregationController {

    private static final Logger log = LoggerFactory.getLogger(PortalAggregationController.class);

    private final JdbcTemplate jdbc;

    public PortalAggregationController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ═══════════════ 全局仪表盘 ═══════════════════

    @GetMapping("/dashboard")
    public ApiResponse<Map<String, Object>> dashboard() {
        Map<String, Object> data = new LinkedHashMap<>();
        try {
            data.put("goals", safeCount("ecos_wm_goal"));
            data.put("scenarios", safeCount("ecos_wm_scenario"));
            data.put("causalLinks", safeCount("ecos_wm_causal_link"));
            data.put("assets", safeCount("ecos_marketplace_asset"));
            data.put("requests", safeCount("ecos_marketplace_access_request"));
            data.put("entities", safeCount("ecos_ontology_entity"));
            data.put("objects", safeCount("ecos_object"));
            data.put("pipelines", safeCount("ecos_dq_rule"));
            data.put("agents", safeCount("ecos_agent_registry"));
            data.put("workflows", safeCount("ecos_workflow_definition"));
            data.put("auditLogs", safeCount("ecos_audit_log"));
        } catch (Exception e) {
            log.warn("Dashboard aggregation partial failure: {}", e.getMessage());
        }
        return ApiResponse.success(data);
    }

    // ═══════════════ 统一搜索 ═══════════════════

    /**
     * 跨模块统一搜索 — 覆盖 OntologyEntity / Asset / Goal / Scenario
     * / Object / Workflow / Pipeline(DQ) / Knowledge / Agent
     *
     * @param q    关键词（必填）
     * @param type 限定类型: all|ontology|asset|goal|scenario|object|workflow|pipeline|knowledge|agent
     */
    @GetMapping("/search")
    public ApiResponse<List<Map<String, Object>>> unifiedSearch(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "all") String type) {

        if (q.isBlank()) return ApiResponse.success(List.of());

        List<Map<String, Object>> results = new ArrayList<>();
        String like = "%" + q + "%";

        if (matches(type, "all", "ontology"))
            searchTable(results, "ecos_ontology_entity", "name", like, "OntologyEntity", "/app/ontology-designer");
        if (matches(type, "all", "asset"))
            searchTable(results, "ecos_marketplace_asset", "name", like, "Asset", "/app/marketplace");
        if (matches(type, "all", "goal"))
            searchTable(results, "ecos_wm_goal", "name", like, "Goal", "/app/mission-control");
        if (matches(type, "all", "scenario"))
            searchTable(results, "ecos_wm_scenario", "name", like, "Scenario", "/app/mission-control");
        if (matches(type, "all", "object"))
            searchTable(results, "ecos_object", "name", like, "Object", "/app/data-browser");
        if (matches(type, "all", "workflow"))
            searchTable(results, "ecos_workflow_definition", "name", like, "Workflow", "/app/workflow-designer");
        if (matches(type, "all", "pipeline"))
            searchTable(results, "ecos_dq_rule", "name", like, "Pipeline", "/app/pipeline-builder");
        if (matches(type, "all", "knowledge"))
            searchTable(results, "ecos_knowledge_graph_node", "label", like, "Knowledge", "/app/knowledge-graph");
        if (matches(type, "all", "agent"))
            searchTable(results, "ecos_agent_registry", "name", like, "Agent", "/app/agent-studio");

        // Sort: best match first (exact or starts-with) + limit
        String lowerQ = q.toLowerCase();
        results.sort((a, b) -> {
            String an = String.valueOf(a.getOrDefault("name", "")).toLowerCase();
            String bn = String.valueOf(b.getOrDefault("name", "")).toLowerCase();
            boolean aExact = an.equals(lowerQ), bExact = bn.equals(lowerQ);
            if (aExact != bExact) return aExact ? -1 : 1;
            boolean aStart = an.startsWith(lowerQ), bStart = bn.startsWith(lowerQ);
            if (aStart != bStart) return aStart ? -1 : 1;
            return 0;
        });
        if (results.size() > 30) results = results.subList(0, 30);

        return ApiResponse.success(results);
    }

    /** 类型匹配: type 为 "all" 或精确匹配 target 时返回 true */
    private boolean matches(String type, String allSentinel, String target) {
        return allSentinel.equals(type) || target.equals(type);
    }

    // ═══════════════ 最近更新 ═══════════════════

    @GetMapping("/recent")
    public ApiResponse<List<Map<String, Object>>> recent(@RequestParam(defaultValue = "20") int limit) {
        List<Map<String, Object>> items = new ArrayList<>();
        try {
            // Union recent items from multiple tables
            String sql = """
                (SELECT 'OntologyEntity' as type, id::text, name, created_at as ts
                 FROM ecos_ontology_entity ORDER BY created_at DESC LIMIT ?)
                UNION ALL
                (SELECT 'Asset' as type, id::text, name, created_at as ts
                 FROM ecos_marketplace_asset ORDER BY created_at DESC LIMIT ?)
                UNION ALL
                (SELECT 'Goal' as type, id::text, name, created_at as ts
                 FROM ecos_wm_goal ORDER BY created_at DESC LIMIT ?)
                ORDER BY ts DESC LIMIT ?
                """;
            items = jdbc.query(sql, (rs, rowNum) -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("type", rs.getString("type"));
                item.put("id", rs.getString("id"));
                item.put("name", rs.getString("name"));
                item.put("ts", rs.getTimestamp("ts") != null ? rs.getTimestamp("ts").toString() : null);
                return item;
            }, limit, limit, limit);
        } catch (Exception e) {
            log.warn("Recent items query failed: {}", e.getMessage());
        }
        return ApiResponse.success(items);
    }

    // ═══════════════ 系统统计 ═══════════════════

    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats() {
        Map<String, Object> data = new LinkedHashMap<>();
        try {
            data.put("moduleCount", 16);
            int totalTables = 0;
            try {
                totalTables = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public'", Integer.class);
            } catch (Exception ignored) {}
            data.put("tableCount", totalTables);

            // Sum all row counts
            long totalRows = 0;
            String[] tables = {"ecos_ontology_entity", "ecos_object", "ecos_wm_goal",
                "ecos_wm_scenario", "ecos_marketplace_asset", "ecos_dq_rule",
                "ecos_workflow_definition", "ecos_agent_registry"};
            for (String t : tables) {
                try {
                    Long c = jdbc.queryForObject("SELECT COUNT(*) FROM " + t, Long.class);
                    if (c != null) totalRows += c;
                } catch (Exception ignored) {}
            }
            data.put("totalRows", totalRows);
        } catch (Exception e) {
            log.warn("Stats aggregation failed: {}", e.getMessage());
        }
        return ApiResponse.success(data);
    }

    // ═══════════════ 模块清单 ═══════════════════

    @GetMapping("/modules")
    public ApiResponse<List<Map<String, Object>>> modules() {
        List<Map<String, Object>> list = List.of(
            module("runtime", "运行时核心", "core", 101),
            module("sysman", "系统管理", "admin", 56),
            module("workspace", "工作空间", "data", 45),
            module("dccheng", "数据编制", "data", 22),
            module("buszhi", "数据治理", "data", 35),
            module("aimod", "Agent引擎", "ai", 18),
            module("worldmodel", "世界模型", "ai", 6),
            module("market", "数据市场", "business", 8),
            module("portal", "统一门户", "business", 3),
            module("datanet", "数据网络", "data", 17),
            module("common", "公共基础", "core", 24)
        );
        return ApiResponse.success(list);
    }

    // ═══════════════ Helpers ═══════════════════

    private long safeCount(String table) {
        try {
            Long c = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
            return c != null ? c : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private void searchTable(List<Map<String, Object>> results, String table, String col, String like, String type, String url) {
        try {
            jdbc.query("SELECT id, " + col + " as name FROM " + table + " WHERE " + col + " ILIKE ? LIMIT 8",
                (rs, rowNum) -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("type", type);
                    item.put("id", rs.getObject("id"));
                    item.put("name", rs.getString("name"));
                    item.put("url", url);
                    results.add(item);
                    return null;
                }, like);
        } catch (Exception ignored) {}
    }

    private Map<String, Object> module(String name, String label, String category, int fileCount) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("label", label);
        m.put("category", category);
        m.put("fileCount", fileCount);
        return m;
    }
}
