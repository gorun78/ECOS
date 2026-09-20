package com.chinacreator.gzcm.engine.kb.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.kb.dto.OntologyTreeVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 本体树查询 Controller — Wave 2 T7 批次。
 *
 * <p>端点语义（业务数据端点默认 DENY，不写 permitAll）：
 * <ul>
 *   <li><b>T7</b> {@code GET /api/v1/knowledge/extract/ontology-tree} —
 *       消费本体 engine 端点：
 *       <ul>
 *         <li>{@code GET /api/v1/ecos/ontologies}（强类型 {@code OntologyVO} 列表）—
 *             取本体 id/code/name；</li>
 *         <li>{@code GET /api/v1/ecos/versions}（强类型 {@code OntologyVersionVO} 列表）—
 *             取版本号供给展示（当前任务书要求按 entity 维度分组，version 仅作未来扩展）。</li>
 *       </ul>
 *       每个本体 entity 列表走 {@code GET /api/v1/ecos/ontologies/{id}/entities}
 *       收集 {@code entities[].code}；OWL 本体中无 {@code domain} 属性，
 *       故按 {@code domainCode} (来自 mapping 契约 {@code sourceType}) 或 {@code default} 分桶。</li>
 * </ul>
 *
 * <p>不直接查 {@code td_data_*}（铁律 §0.5-1）；不 {@code new RestTemplate}
 * （复用 {@code KbEngineRestConfig} 的 Bean）；本体 engine 不可达时返回空列表
 * 不抛 500（前端渲染空树）。</p>
 */
@RestController
@RequestMapping("/api/v1/knowledge/extract")
public class OntologyTreeController {

    private static final Logger log = LoggerFactory.getLogger(OntologyTreeController.class);

    /**
     * 同 JVM 内复用的 domain 分桶缓存（T7 一次性粗读场景，无并发写入）。
     * <p>每次 tree() 进入时清空；避免上次请求结果残留污染</p>
     */
    private static final Map<String, List<OntologyTreeVO.OntologyItem>> DOMAIN_BUCKET = new LinkedHashMap<>();

    private final RestTemplate restTemplate;

    private final ObjectMapper objectMapper;

    /** ontology-api 基址（与既有 {@code KbEntityInstanceExtractionService} 共用配置项）。 */
    private final String ontologyApiBase;

    public OntologyTreeController(RestTemplate restTemplate,
                                   ObjectMapper objectMapper,
                                   @Value("${ecos.ontology-api-base:http://localhost:18083/api/v1}")
                                             String ontologyApiBase) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.ontologyApiBase = ontologyApiBase;
    }

    /**
     * T7 — 返回本体树（按 domain 分桶，列出本体 / 实体 codes）。
     *
     * @return 域 → 本体列表；本体 engine 不可达 / 无数据均返回 {@code []}，不抛 500
     */
    @GetMapping("/ontology-tree")
    public ApiResponse<List<OntologyTreeVO>> tree() {
        DOMAIN_BUCKET.clear();
        List<Map<String, Object>> ontologies = safeGetList(
                ontologyApiBase + "/ecos/ontologies", "ontologies");
        if (ontologies.isEmpty()) {
            log.warn("T7 本体树：本体 engine /api/v1/ecos/ontologies 无数据或不可达，返回空数组");
            return ApiResponse.success(new ArrayList<>());
        }

        // 按本体逐一收集 entity codes，并按 domain 分桶
        for (Map<String, Object> ont : ontologies) {
            if (ont == null) {
                continue;
            }
            String ontologyId = asString(ont.get("id"));
            String ontologyCode = asString(ont.get("code"));
            String ontologyName = asString(ont.get("name"));
            String key = ontologyId == null ? ontologyCode : ontologyId;
            if (key == null) {
                continue;
            }
            List<String> codes = loadEntityCodes(ontologyId == null ? ontologyCode : ontologyId);

            OntologyTreeVO.OntologyItem item = new OntologyTreeVO.OntologyItem();
            item.setId(key);
            item.setCode(ontologyCode);
            item.setName(ontologyName);
            item.setEntityCodes(codes == null ? new ArrayList<>() : codes);

            // OWL 本体当前未带 domainCode 字段；统一归 default，前端可按 code 二次分桶
            DOMAIN_BUCKET.computeIfAbsent("default", k -> new ArrayList<>()).add(item);
        }

        // 组装出参
        List<OntologyTreeVO> out = new ArrayList<>(DOMAIN_BUCKET.size());
        for (Map.Entry<String, List<OntologyTreeVO.OntologyItem>> entry : DOMAIN_BUCKET.entrySet()) {
            OntologyTreeVO vo = new OntologyTreeVO();
            vo.setDomain(entry.getKey());
            vo.setOntologies(entry.getValue());
            out.add(vo);
        }
        return ApiResponse.success(out);
    }

    // ─────────────────────────────────────────────────────────────────────
    // 内部辅助
    // ─────────────────────────────────────────────────────────────────────

    /** 拉本体 engine 实体列表，提取 {@code entities[].code}；失败返回空。 */
    private List<String> loadEntityCodes(String ontologyId) {
        if (ontologyId == null || ontologyId.isBlank()) {
            return new ArrayList<>();
        }
        String url = ontologyApiBase + "/ecos/ontologies/" + encode(ontologyId) + "/entities";
        try {
            Map<String, Object> body = restTemplate.getForObject(url, Map.class);
            Object data = body == null ? null : body.get("data");
            if (data instanceof List<?> list) {
                List<String> out = new ArrayList<>(list.size());
                for (Object element : list) {
                    if (element instanceof Map<?, ?> entity) {
                        Object code = entity.get("code");
                        if (code != null && !String.valueOf(code).isBlank()) {
                            out.add(String.valueOf(code));
                        }
                    }
                }
                return out;
            }
        } catch (Exception e) {
            log.warn("T7 实体列表拉取失败 ontologyId={}: {}", ontologyId, e.getMessage());
        }
        return new ArrayList<>();
    }

    /** 拉本体 engine 本体列表，返回 raw map（失败返回空列表）。 */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> safeGetList(String url, String label) {
        try {
            Map<String, Object> body = restTemplate.getForObject(url, Map.class);
            Object data = body == null ? null : body.get("data");
            if (data instanceof List<?> list) {
                List<Map<String, Object>> out = new ArrayList<>(list.size());
                for (Object element : list) {
                    if (element instanceof Map<?, ?> m) {
                        out.add((Map<String, Object>) m);
                    }
                }
                return out;
            }
        } catch (Exception e) {
            log.warn("T7 {} 端点不可用 ({}): {}", label, url, e.getMessage());
        }
        return new ArrayList<>();
    }

    /** null 安全 String 转换。 */
    private static String asString(Object raw) {
        return raw == null ? null : String.valueOf(raw);
    }

    /** URL path 段编码（仅做 base64 安全的简化转义，与 KbEntityInstanceExtractionService.encode 同源语义）。 */
    private String encode(String pathValue) {
        return java.net.URLEncoder.encode(pathValue, java.nio.charset.StandardCharsets.UTF_8);
    }
}
