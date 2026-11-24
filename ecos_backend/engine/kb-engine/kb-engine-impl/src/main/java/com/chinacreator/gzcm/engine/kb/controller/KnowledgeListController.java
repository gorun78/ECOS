package com.chinacreator.gzcm.engine.kb.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.kb.doc.mapper.KbDocMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;

/**
 * 知识库基础元数据端点。
 *
 * <p>前端 {@code agentConfig.ts fetchKnowledgeBases()} 调 {@code GET /api/v1/knowledge-bases}；
 * Context Builder 的 KNOWLEDGE_BASE reader 调 {@code GET /api/v1/knowledge-bases/{baseId}}。</p>
 *
 * <p><b>数据源（2026-11-24 订正）</b>：本端点原实现只读环境配置 {@code kb.bases} 的静态 JSON，
 * 返回写死的 {@code default/默认知识库}，与库内真实文档无关（前端因此拿到「默认知识库」却
 * 查不到任何文档）。现改为按 {@code ecos_knowledge.kb_doc.domain} 真实聚合：
 * <ul>
 *   <li>库标识 = {@code domain} 列（空/空串归 {@code default}）；</li>
 *   <li>{@code docCount} = 该库文档数；{@code chunkCount} = 分块总数；
 *       {@code updatedAt} = 库内最新更新时间（作用域版本口径）；</li>
 *   <li>库内无任何文档 ⇒ 回退静态配置并标注 {@code source=CONFIG}
 *       （避免「配置声明了库、库内没文档」时前端连库名都看不到；但明确标源，不伪装成真实库）。</li>
 * </ul>
 *
 * @group OVERVIEW
 * @group WIKI
 */
@RestController
@RequestMapping({"/api/v1/knowledge-bases", "/api/knowledge-bases"})
public class KnowledgeListController {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeListController.class);

    /** 环境配置中的静态知识库列表（仅在库内真实无文档时作为可见性回退）。*/
    @org.springframework.beans.factory.annotation.Value("${kb.bases:[{\"id\":\"default\",\"name\":\"默认知识库\"}]}")
    private String staticBasesJson;

    private final KbDocMapper kbDocMapper;

    public KnowledgeListController(@Autowired(required = false) KbDocMapper kbDocMapper) {
        this.kbDocMapper = kbDocMapper;
    }

    /**
     * 知识库列表：优先返回库内真实聚合，无真实文档时回退静态配置（标 {@code source=CONFIG}）。
     */
    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(HttpServletRequest request) {
        List<Map<String, Object>> real = realBases();
        if (real.isEmpty()) {
            List<Map<String, Object>> fallback = staticBases();
            for (Map<String, Object> b : fallback) b.put("source", "CONFIG");
            return ApiResponse.success(fallback);
        }
        return ApiResponse.success(real);
    }

    /**
     * 单个知识库详情：Context Builder 的 KNOWLEDGE_BASE reader 读此端点取版本/时点。
     *
     * <p>版本口径：{@code updatedAt}（库内最新更新时间）。库内无文档 ⇒ 404
     * （reader 侧据此抛 {@code ContextReadException}，禁以请求声明版本回填）。</p>
     */
    @GetMapping("/{baseId}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable("baseId") String baseId) {
        Map<String, Object> stat = kbDocMapper == null ? null : kbDocMapper.statByBase(baseId);
        if (stat == null) {
            // 配置声明的库（库内还没文档）允许读回元数据，但明确标源为 CONFIG 且无版本
            for (Map<String, Object> b : staticBases()) {
                if (baseId.equals(String.valueOf(b.get("id")))) {
                    b.put("source", "CONFIG");
                    return ApiResponse.success(b);
                }
            }
            return ApiResponse.error(404, "KB-404: 知识库不存在或无文档: " + baseId);
        }
        return ApiResponse.success(toVO(stat));
    }

    /** 库内真实聚合列表（按 domain 分组）。 */
    private List<Map<String, Object>> realBases() {
        List<Map<String, Object>> out = new ArrayList<>();
        if (kbDocMapper == null) return out;
        try {
            List<Map<String, Object>> rows = kbDocMapper.groupByBase();
            if (rows == null) return out;
            for (Map<String, Object> r : rows) out.add(toVO(r));
        } catch (Exception e) {
            log.warn("按 domain 聚合知识库失败，回退静态配置: {}", e.getMessage());
        }
        return out;
    }

    /** 聚合行 → 对外 VO（字段名与前端 KB 契约一致：id/name/description/documentCount）。 */
    private Map<String, Object> toVO(Map<String, Object> row) {
        String baseId = String.valueOf(row.getOrDefault("base_id", "default"));
        Map<String, Object> vo = new LinkedHashMap<>();
        vo.put("id", baseId);
        // 名称：库标识即名称（库表目前无独立 display name 列，禁自造显示名）
        vo.put("name", baseId);
        vo.put("description", "");
        vo.put("documentCount", toLong(row.get("doc_count")));
        vo.put("chunkCount", toLong(row.get("chunk_total")));
        Object updated = row.get("last_updated");
        // 版本口径：库内最新更新时间（ISO-8601 字符串，Context Builder reader 直接取用）
        vo.put("updatedAt", updated == null ? null : String.valueOf(updated));
        vo.put("source", "DB");
        return vo;
    }

    private long toLong(Object v) {
        if (v instanceof Number n) return n.longValue();
        if (v == null) return 0L;
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /** 静态配置回退（原实现口径，仅在库内无真实文档时使用）。 */
    private List<Map<String, Object>> staticBases() {
        List<Map<String, Object>> bases = new ArrayList<>();
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
        return bases;
    }
}
