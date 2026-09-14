package com.chinacreator.gzcm.engine.ontology.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.ontology.dto.EcosMappingFullVO;
import com.chinacreator.gzcm.engine.ontology.service.OntologyMappingService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 全局本体映射 REST API — 前缀 /api/v1/ecos/mappings
 *
 * <p>PMO-51 T4：知识工作台"本体模型"Tab 一次性拉取全局所有 mapping 的<b>只读</b>端点，
 * 与 {@link OntologyMappingController}（/api/v1/ontology/mappings 单条 CRUD）不同前缀共存：
 * 本端点为"只读 + 不分页 + ETag + 缓存头"，前端无需分页。
 * 端点挂 {@code /api/v1/ecos/} 前缀是因为"前端经 gateway 走 ecos 域"的统一约定
 * （路径铁律："已有 API 路径不再改，新增优先 /api/v1/ 前缀"）。</p>
 *
 * <p>路径池：</p>
 * <ul>
 *   <li>GET /api/v1/ecos/mappings/full — 不分类返全局 mapping + If-None-Match ETag</li>
 * </ul>
 *
 * <p>前端 fetch 后通过 {@code If-None-Match} 头复用本地 state (304)，避免每次切换 Tab 都打一次全表。</p>
 *
 * <p>ETag 计算策略：JSON body 的 SHA-256，{@code W/"&lt;hash&gt;"}。</p>
 */
@RestController
@RequestMapping("/api/v1/ecos/mappings")
public class EcosMappingFullController {

    private static final Logger log = LoggerFactory.getLogger(EcosMappingFullController.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<Map<String, Object>>> ROWS_TYPE = new TypeReference<>() {};

    private final OntologyMappingService mappingService;

    public EcosMappingFullController(OntologyMappingService mappingService) {
        this.mappingService = mappingService;
    }

    /**
     * GET /api/v1/ecos/mappings/full — 全局 mapping 列表（不分页，加 ETag 缓存头）。
     *
     * @param ifNoneMatch 客户端 ETag（可选），命中 304 → 不返 body
     */
    @GetMapping("/full")
    public void full(
            @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch,
            HttpServletResponse response) {
        List<Map<String, Object>> rows;
        try {
            rows = mappingService.listMappings(null, null);
        } catch (DataAccessException e) {
            log.error("Failed to fetch all ontology mappings: {}", e.getMessage(), e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return;
        }

        List<EcosMappingFullVO> vos = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            vos.add(toVo(row));
        }

        String body;
        try {
            body = MAPPER.writeValueAsString(ApiResponse.success(vos));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize mapping responses: {}", e.getMessage(), e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return;
        }

        String etag = "W/" + sha256Hex(body);
        response.setHeader("ETag", etag);
        if (ifNoneMatch != null && !ifNoneMatch.isBlank()) {
            // 兼容带引号 / 不带引号两种 If-None-Match 值
            String normalized = ifNoneMatch.replace("W/", "").replace("\"", "").trim();
            if (normalized.equals(etag.substring(2))) {
                response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                return;
            }
        }

        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(HttpServletResponse.SC_OK);
        try {
            response.getWriter().write(body);
        } catch (Exception e) {
            log.warn("Failed to write response: {}", e.getMessage());
        }
        log.debug("EcosMappingFull: rows={}, etag={}", vos.size(), etag);
    }

    // ── 内部辅助 ─────────────────────────────────────────────────────

    /** row → EcosMappingFullVO（强类型，反 JSONB）。 */
    private EcosMappingFullVO toVo(Map<String, Object> row) {
        EcosMappingFullVO vo = new EcosMappingFullVO();
        vo.setId(str(row.get("id")));
        vo.setEntityCode(str(row.get("entity_code")));
        vo.setVersion(str(row.getOrDefault("version", "")));
        // datasource_id 同时是"来源类型"语义来源（兼容前端 sourceType 字段）
        vo.setSourceDomain(str(row.getOrDefault("domain_code", "")));
        Object fmObj = row.get("field_mappings");
        vo.setFieldMappings(parseFieldMappings(fmObj));
        return vo;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseFieldMappings(Object fmObj) {
        if (fmObj == null) {
            return new ArrayList<>();
        }
        if (fmObj instanceof List) {
            // PGobject 下的 JSONB 数组直接是 List
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object o : (List<?>) fmObj) {
                if (o instanceof Map) {
                    out.add((Map<String, Object>) o);
                }
            }
            return out;
        }
        try {
            return MAPPER.readValue(String.valueOf(fmObj), new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            // 不会发生 — SHA-256 是 JDK 标配
            return Long.toHexString(input.hashCode());
        }
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}
