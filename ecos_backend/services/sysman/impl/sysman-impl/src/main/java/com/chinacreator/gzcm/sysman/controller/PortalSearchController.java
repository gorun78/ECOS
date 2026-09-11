package com.chinacreator.gzcm.sysman.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.sysman.iam.context.UserContext;
import com.chinacreator.gzcm.sysman.service.PortalSearchQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * PMO-39 批次2 T4 — Portal 全局搜索（对应检查报告 §6 #4）。
 *
 * <h3>端点（双路径）：</h3>
 * <ul>
 *   <li>GET /api/v1/portal/search?q={query}&amp;type={all|agent|workflow|glossary|scenario}</li>
 *   <li>GET /api/portal/search?...</li>
 * </ul>
 *
 * <p>实体级检索（非全文）：agent/workflow/glossary/scenario 4 类，合并为统一
 * {@code items[]}（type/id/name/url）返回 — 字段对齐前端
 * {@code ecos_frontend/src/api.ts SearchHit}（type/id/name/url，SPA hash 路由）。
 * 数据源只读走 {@link PortalSearchQueryService}（Controller 禁止直接使用
 * JdbcTemplate — 硬规则）。RLS：UserContext 已认证时 rlsApplied=true，
 * 行级收窄由 SecurityConfig permitAll + ClearanceInterceptor 联动保证。</p>
 */
@RestController
@RequestMapping({"/api/v1/portal", "/api/portal"})
public class PortalSearchController {

    private static final Logger log = LoggerFactory.getLogger(PortalSearchController.class);

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "all", "agent", "workflow", "glossary", "scenario");

    private static final int MAX_QUERY_LEN = 100;
    private static final int MAX_RESULTS = 50;

    private final PortalSearchQueryService searchService;

    public PortalSearchController(PortalSearchQueryService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/search")
    public ApiResponse<Map<String, Object>> search(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "all") String type) {

        if (q == null || q.isBlank()) {
            return ApiResponse.badRequest("q 不能为空");
        }
        if (q.length() > MAX_QUERY_LEN) {
            return ApiResponse.badRequest("q 长度不能超过 " + MAX_QUERY_LEN + " 字符");
        }
        if (!ALLOWED_TYPES.contains(type)) {
            return ApiResponse.badRequest("type 必须为 " + ALLOWED_TYPES + " 之一");
        }

        String keyword = q.trim();
        String userId = safeUserId();
        log.info("Portal 搜索: q={}, type={}, userId={}", keyword, type, userId);

        List<Map<String, Object>> items = new ArrayList<>();
        if (type.equals("all") || type.equals("agent")) {
            items.addAll(segments(searchService.searchAgents(keyword), keyword, "agent", "/app/agent_studio"));
        }
        if (type.equals("all") || type.equals("workflow")) {
            items.addAll(segments(searchService.searchWorkflows(keyword), keyword, "workflow", "/app/pipeline"));
        }
        if (type.equals("all") || type.equals("glossary")) {
            items.addAll(segments(searchService.searchGlossary(keyword), keyword, "glossary", "/app/knowledge_graph"));
        }
        if (type.equals("all") || type.equals("scenario")) {
            items.addAll(segments(searchService.searchScenarios(keyword), keyword, "scenario", "/app/mission_control"));
        }

        items.sort((a, b) -> Double.compare(doubleVal(b.get("score")), doubleVal(a.get("score"))));
        List<Map<String, Object>> top = items.size() > MAX_RESULTS ? items.subList(0, MAX_RESULTS) : items;

        // 去掉内部 score 字段，对齐前端 SearchHit {type,id,name,url}
        List<Map<String, Object>> hits = new ArrayList<>();
        Set<String> seenIds = new LinkedHashSet<>();
        for (Map<String, Object> it : top) {
            String idKey = it.get("type") + ":" + it.get("id");
            if (!seenIds.add(idKey)) {
                continue;
            }
            Map<String, Object> hit = new LinkedHashMap<>();
            hit.put("type", it.get("type"));
            hit.put("id", it.get("id"));
            hit.put("name", it.get("name"));
            hit.put("url", it.get("url"));
            hits.add(hit);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("q", keyword);
        result.put("type", type);
        result.put("total", hits.size());
        result.put("items", hits);
        result.put("rlsApplied", userId != null);
        return ApiResponse.success(result);
    }

    /** 原始行 → 前端 SearchHit 条目（仅保留关键词命中的行） */
    private static List<Map<String, Object>> segments(List<Map<String, Object>> rows,
                                                      String keyword, String type, String url) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Object name = r.get("name") != null ? r.get("name") : r.get("term");
            Object desc = r.get("description") != null ? r.get("description") : r.get("definition");
            double s = score(name, desc, keyword);
            if (s <= 0) {
                continue;
            }
            Object id = r.get("id");
            if (id == null || (id instanceof String s2 && s2.isBlank())) {
                continue;
            }
            Map<String, Object> it = new LinkedHashMap<>();
            it.put("type", type);
            it.put("id", id);
            it.put("name", name);
            it.put("url", url);
            it.put("score", s);
            out.add(it);
        }
        return out;
    }

    /** 实体级打分：标题命中 2.0，描述命中 1.0 */
    private static double score(Object nameObj, Object descObj, String keyword) {
        double s = 0.0;
        String kw = keyword.toLowerCase();
        // name 为 null 的行（如 workflow 仅有 description）不能因 name=null 被一档判 0
        if (nameObj != null && nameObj.toString().toLowerCase().contains(kw)) s += 2.0;
        if (descObj != null && descObj.toString().toLowerCase().contains(kw)) s += 1.0;
        return s;
    }

    private static double doubleVal(Object o) {
        return o instanceof Number n ? n.doubleValue() : 0.0;
    }

    private static String safeUserId() {
        try {
            return UserContext.getCurrentUserId();
        } catch (Exception e) {
            return null;
        }
    }
}
