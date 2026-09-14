package com.chinacreator.gzcm.engine.ontology.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * KbEngineGraphSyncClient — 走 kb-engine REST 把本体结构同步到 KG, 避免 ontology 引擎直连 Neo4j。
 *
 * Wave B-2 · T14 (来源: 肖国荣 / 日期: 2026-09-12 / 责任人: fullstack-implementer)
 * 依据: 架构铁律 §2.5 (runtime-access 统一 Driver, 引擎不直连 Neo4j)
 *   + §0.3.1 (跨 service REST 同步, 不 import 别家 impl)
 *
 * 调用 kb-engine `POST :18086/api/v1/kb/graph/sync`
 * body: {
 *   "ontologyId": "...",
 *   "entities": [{id, code, name, properties: [...]}],
 *   "relationships": [{id, sourceId, targetId, code, type}],
 *   "operation": "FULL"
 * }
 *
 * 临时处理(kb-engine 该端点尚未落地):
 *   当前 kb-engine-impl 还没有 graph/sync 端点, client 在端点不可达或 404 时
 *   log.warn + 返回 false 不抛异常, 由 OntologyKgSyncService 决定是否阻断同步
 *   (T14 改造: syncOntologyToKnowledgeGraph 改用 boolean 语义, kb 未受理 = 同步失败,
 *    由调用方决定降级策略, 不再让 driver.session() 直接抛 IOException/NPE)。
 *   TODO PMO-06 T21 补 kb-engine POST /api/v1/kb/graph/sync 端点合同。
 */
@Component
public class KbEngineGraphSyncClient {

    private static final Logger log = LoggerFactory.getLogger(KbEngineGraphSyncClient.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${ecos.kbengine.base-url:http://localhost:18086}")
    private String kbEngineBaseUrl;

    public KbEngineGraphSyncClient() {
        // P1-2: 显式 3s connect/read timeout, 避免 kb-engine 宕机时阻塞主流程 (Wave B-2 P1-2 加固)
        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(3000);
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * 全量同步本体定义到知识库图谱。
     *
     * @param ontologyId    本体 id
     * @param entities      实体摘要
     * @param relationships 关系摘要
     * @return true 表示 kb-engine 受理成功(success=true); false 表示不可达/404/业务失败(不抛异常)
     */
    public boolean syncOntologyGraph(String ontologyId, List<Map<String, Object>> entities,
                                     List<Map<String, Object>> relationships) {
        String url = kbEngineBaseUrl + "/api/v1/kb/graph/sync";
        Map<String, Object> body = Map.of(
                "ontologyId", ontologyId,
                "entities", entities,
                "relationships", relationships,
                "operation", "FULL"
        );
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = restTemplate.postForObject(url, request, Map.class);
            boolean ok = resp != null && Boolean.TRUE.equals(resp.get("success"));
            if (!ok) {
                log.warn("kb-engine graph/sync 返回 success=false: {}", resp);
            }
            return ok;
        } catch (Exception e) {
            // kb-engine 不可达 / 端点 404: 不抛, 由调用方判 false
            // TODO PMO-06 T21 补 kb-engine POST /api/v1/kb/graph/sync 端点合同
            log.error("kb-engine graph/sync 调用失败: url={}, err={}", url, e.getMessage(), e);
            return false;
        }
    }
}
