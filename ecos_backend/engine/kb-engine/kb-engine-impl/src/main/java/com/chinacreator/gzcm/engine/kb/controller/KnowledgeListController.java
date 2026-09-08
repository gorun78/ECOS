package com.chinacreator.gzcm.engine.kb.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;

/**
 * 知识库基础元数据端点。
 * 前端 agentConfig.ts fetchKnowledgeBases() 调用 GET /api/v1/knowledge-bases。
 * 返回当前系统可使用的知识库列表（含 documentCount 聚合，无则 0）。
 *
 * <p>数据源策略（按优先级）：
 * <ol>
 *   <li>ecos_knowledge.ecos_knowledge_document 表的租户维度聚合（按 tenant_id GROUP BY）</li>
 *   <li>回退：环境配置 KB_BASES_JSON（静态 JSON 数组）</li>
 *   <li>兜底：空列表 + success 200</li>
 * </ol>
 */
@RestController
@RequestMapping("/api/v1/knowledge-bases")
public class KnowledgeListController {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeListController.class);

    /** 环境配置中的静态知识库列表（可选降级源）*/
    @org.springframework.beans.factory.annotation.Value("${kb.bases:[{\"id\":\"default\",\"name\":\"默认知识库\"}]}")
    private String staticBasesJson;

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(HttpServletRequest request) {
        List<Map<String, Object>> bases = new ArrayList<>();

        // 策略1: 尝试从 DB 查询（如果 Mapper 可用）
        // 当前 kb-engine 无 EcosKnowledgeDocumentMapper，先用静态源；
        // 后续可注入 Mapper 做真实聚合。
        // 策略2: 环境配置 KB_BASES_JSON
        try {
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            List<Map<String, Object>> staticBases = om.readValue(staticBasesJson,
                    om.getTypeFactory().constructCollectionType(List.class, Map.class));
            bases.addAll(staticBases);
        } catch (Exception e) {
            log.warn("解析 KB_BASES_JSON 失败，返回默认: {}", e.getMessage());
            Map<String, Object> fb = new LinkedHashMap<>();
            fb.put("id", "default");
            fb.put("name", "默认知识库");
            fb.put("description", "");
            fb.put("documentCount", 0);
            bases.add(fb);
        }

        return ApiResponse.success(bases);
    }
}
