package com.chinacreator.gzcm.sysman.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.sysman.dto.PortalSearchHitVO;
import com.chinacreator.gzcm.sysman.dto.PortalSearchItemVO;
import com.chinacreator.gzcm.sysman.dto.PortalSearchResultVO;
import com.chinacreator.gzcm.sysman.iam.context.UserContext;
import com.chinacreator.gzcm.sysman.service.PortalSearchQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
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
 * 数据源只读走 {@link PortalSearchQueryService}（Controller 不直接持有数据访问模板 —
 * 硬规则，PMO-74 H9-T4 全量复核通过）。RLS：UserContext 已认证时 rlsApplied=true，
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
    public ApiResponse<PortalSearchResultVO> search(
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

        List<PortalSearchItemVO> items = new ArrayList<>();
        if (type.equals("all") || type.equals("agent")) {
            items.addAll(PortalSearchItemVO.segments(
                    searchService.searchAgents(keyword), keyword, "agent", "/app/agent_studio"));
        }
        if (type.equals("all") || type.equals("workflow")) {
            items.addAll(PortalSearchItemVO.segments(
                    searchService.searchWorkflows(keyword), keyword, "workflow", "/app/pipeline"));
        }
        if (type.equals("all") || type.equals("glossary")) {
            items.addAll(PortalSearchItemVO.segments(
                    searchService.searchGlossary(keyword), keyword, "glossary", "/app/knowledge_graph"));
        }
        if (type.equals("all") || type.equals("scenario")) {
            items.addAll(PortalSearchItemVO.segments(
                    searchService.searchScenarios(keyword), keyword, "scenario", "/app/mission_control"));
        }

        items.sort((a, b) -> Double.compare(b.score(), a.score()));
        List<PortalSearchItemVO> top = items.size() > MAX_RESULTS ? items.subList(0, MAX_RESULTS) : items;

        // 去掉内部 score 字段，对齐前端 SearchHit {type,id,name,url}
        List<PortalSearchHitVO> hits = new ArrayList<>();
        Set<String> seenIds = new LinkedHashSet<>();
        for (PortalSearchItemVO it : top) {
            String idKey = it.type() + ":" + it.id();
            if (!seenIds.add(idKey)) {
                continue;
            }
            hits.add(new PortalSearchHitVO(it.type(), it.id(), it.name(), it.url()));
        }

        return ApiResponse.success(new PortalSearchResultVO(
                keyword, type, hits.size(), hits, userId != null));
    }

    private static String safeUserId() {
        try {
            return UserContext.getCurrentUserId();
        } catch (Exception e) {
            return null;
        }
    }
}
