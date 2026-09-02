package com.chinacreator.gzcm.engine.cognitive2.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * 实体链接客户端（Wave-3.2 T6）— 从 cognitive 调用 kb 的实体链接 REST 端点。
 *
 * <p>对齐 05 文档 §五，跨引擎 REST 调用：
 *   POST http://kb-engine:18086/api/v1/knowledge/entity-link/entity/link
 *   body: { entityName, entityType }
 *   resp: { ontologyPath, confidence, mappedToId, ... }
 *
 * <p>降级策略（铁律 6 默认 DENY 不阻断主流程）：
 * 调用失败 → 返回空结果 + status="fallback"，不抛异常。</p>
 *
 * <p>使用场景：
 * <ol>
 *   <li>Wave-3.2 端到端 Demo（T7）：Markdown 抽取的实体名 → 链接到本体</li>
 *   <li>MinerU 抽取后实体批量验证（与 kb-engine 共用调用链）</li>
 * </ol>
 *
 * <p>不做的事：不直接写 Neo4j（那是 kb-engine EntityLinkerService 的职责），
 * 不重复 query ontology_objects 表（走 kb REST）。</p>
 *
 * @author ECOS Cognitive Engine Team
 * @since 2026-09-02 (Wave-3.2)
 */
@Component
public class EntityLinker {

    private static final Logger log = LoggerFactory.getLogger(EntityLinker.class);

    /** kb 实体链接 REST 端点 */
    private static final String KB_ENTITY_LINK_URL =
            "http://localhost:8080/api/v1/knowledge/entity-link/entity/link";

    private final RestTemplate restTemplate;

    public EntityLinker() {
        this.restTemplate = new RestTemplate();
        this.restTemplate.setRequestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory() {{
            setConnectTimeout(3_000);
            setReadTimeout(10_000);
        }});
    }

    /**
     * 单实体链接（REST 调用 kb-engine）。
     *
     * @param entityName 实体名（如"应收账款"）
     * @param entityType 实体类型（如"财务科目"）
     * @return 输出 Map：ontologyPath / confidence / mappedToId / alternativeCandidates / status
     *         失败时返回 { status="fallback", error=... }
     */
    public Map<String, Object> linkEntity(String entityName, String entityType) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (entityName == null || entityName.isBlank()) {
            result.put("status", "invalid_input");
            result.put("entityName", "");
            result.put("message", "entityName required");
            return result;
        }
        result.put("entityName", entityName);
        result.put("entityType", entityType != null ? entityType : "unknown");

        HttpHeaders headers = jsonHeaders();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("entityName", entityName);
        body.put("entityType", entityType != null ? entityType : "unknown");

        try {
            Map<String, Object> resp = restTemplate.postForObject(
                    KB_ENTITY_LINK_URL, new HttpEntity<>(body, headers), Map.class);
            if (resp == null) {
                result.put("status", "fallback");
                result.put("error", "kb entity-link endpoint returned null");
                return result;
            }
            // ApiResponse 包装：{code,success,message,data}
            Object data = resp.get("data");
            if (data instanceof Map) {
                Map<String, Object> d = (Map<String, Object>) data;
                result.put("ontologyPath", d.getOrDefault("ontologyPath", "未匹配"));
                Object conf = d.getOrDefault("confidence", 0.0);
                result.put("confidence", conf instanceof Number ? ((Number) conf).doubleValue() : 0.0);
                result.put("mappedToId", d.getOrDefault("mappedToId", ""));
                result.put("status", "linked");
                // 候选证据（供前端点击审阅）
                if (d.containsKey("warning")) result.put("warning", d.get("warning"));
            } else {
                result.putAll(resp);
                result.put("status", "linked");
            }
        } catch (Exception e) {
            log.warn("Entity link REST call failed for [{}]: {}", entityName, e.getMessage());
            result.put("status", "fallback");
            result.put("error", e.getMessage());
            result.put("ontologyPath", "未匹配");
            result.put("confidence", 0.0);
        }
        return result;
    }

    /**
     * 批量链接（供 MinerU 抽取出的实体批量送审）。
     *
     * @param entities [{"name":"应收账款","type":"财务科目"}, ...]
     * @return 链接结果列表
     */
    public List<Map<String, Object>> linkEntities(List<Map<String, String>> entities) {
        List<Map<String, Object>> results = new ArrayList<>();
        int success = 0, fallback = 0;
        for (Map<String, String> e : entities) {
            String name = e.get("name");
            String type = e.getOrDefault("type", "unknown");
            Map<String, Object> r = linkEntity(name, type);
            if ("linked".equals(r.get("status"))) success++;
            else if ("fallback".equals(r.get("status"))) fallback++;
            results.add(r);
        }
        log.info("batch link: success={}, fallback={}, total={}", success, fallback, entities.size());
        return results;
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setAccept(List.of(MediaType.APPLICATION_JSON));
        return h;
    }
}
