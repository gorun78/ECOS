package com.chinacreator.gzcm.engine.kb.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 知识库基础元数据端点。
 * 前端 agentConfig.ts fetchKnowledgeBases() 调用 GET /api/v1/knowledge-bases
 * （bare 形态经 VersionPrefixRewriteFilter 反向重写至本 v1 正典路径）。
 * 返回当前系统可使用的知识库列表（含 documentCount 聚合，无则 0）。
 *
 * <p>数据源策略（按优先级）：
 * <ol>
 *   <li>环境配置 KB_BASES_JSON（静态 JSON 数组）</li>
 *   <li>兜底：空列表 + success 200</li>
 * </ol>
 *
 * <p>F04-02 (2026-09-30)：清理未使用的 {@code HttpServletRequest} 形参与
 * tenant_id 注释残留（K-43）；列表契约补 limit/offset（默认 50 上限 500，D.2#4）。
 *
 * @group OVERVIEW
 * @group WIKI
 */
@RestController
@RequestMapping("/api/v1/knowledge-bases")
public class KnowledgeListController {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeListController.class);

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 500;

    /** 环境配置中的静态知识库列表（可选降级源）*/
    @org.springframework.beans.factory.annotation.Value("${kb.bases:[{\"id\":\"default\",\"name\":\"默认知识库\"}]}")
    private String staticBasesJson;

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(
            @RequestParam(value = "limit", defaultValue = "" + DEFAULT_LIMIT) int limit,
            @RequestParam(value = "offset", defaultValue = "0") int offset) {
        List<Map<String, Object>> bases = new ArrayList<>();

        // 环境配置 KB_BASES_JSON（单一数据源；DB 聚合由分册 02 统一重建后再挂回）
        try {
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            List<Map<String, Object>> staticBases = om.readValue(staticBasesJson,
                    om.getTypeFactory().constructCollectionType(List.class, Map.class));
            bases.addAll(staticBases);
        } catch (Exception e) {
            log.warn("解析 KB_BASES_JSON 失败，返回默认: {}", e.getMessage(), e);
            Map<String, Object> fb = new LinkedHashMap<>();
            fb.put("id", "default");
            fb.put("name", "默认知识库");
            fb.put("description", "");
            fb.put("documentCount", 0);
            bases.add(fb);
        }

        int safeLimit = Math.min(Math.max(limit, 1), MAX_LIMIT);
        int safeOffset = Math.max(offset, 0);
        List<Map<String, Object>> page = safeOffset >= bases.size()
                ? Collections.emptyList()
                : bases.subList(safeOffset, Math.min(safeOffset + safeLimit, bases.size()));

        // wire 契约保持 data=数组（前端 agentConfig.ts 已按数组消费，API 只增不改）
        return ApiResponse.success(page);
    }
}
