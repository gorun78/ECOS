package com.chinacreator.gzcm.engine.cognitive2.config;

import com.chinacreator.gzcm.engine.kb.ComplianceRuleProvider;
import com.chinacreator.gzcm.engine.kb.KnowledgeGraphService;
import com.chinacreator.gzcm.engine.kb.KnowledgeRetrievalService;
import com.chinacreator.gzcm.engine.kb.model.ComplianceRule;
import com.chinacreator.gzcm.engine.kb.model.KnowledgeArticle;
import com.chinacreator.gzcm.engine.kb.model.KnowledgeEdge;
import com.chinacreator.gzcm.engine.kb.model.KnowledgeNode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Optional;

/**
 * KB 引擎 REST 代理 Bean 配置（PMO-60 v2.0 P4 P0-1 修复）。
 *
 * <p><b>背景</b>：cognitive 并入 aiming 后（PMO-60 T0b/ADR-P0:18084），
 * {@code CausalDetector}/{@code CausalReasonerServiceImpl}/{@code KnowledgeReasonerService}
 * 构造器直注三个 KB 接口（{@link KnowledgeGraphService}/{@link KnowledgeRetrievalService}/
 * {@link ComplianceRuleProvider}），但 aiming 的 ComponentScan 不含 {@code engine.kb}，
 * 而 kb-impl 已从 cognitive-impl 依赖中拆除 → Bean 在 aiming 不存在，启动 UnsatisfiedDependency。
 *
 * <p><b>方案</b>：本配置类在 {@code engine.cognitive2.config} 包（aiming ComponentScan 覆盖范围内），
 * 用 {@code @ConditionalOnMissingBean} 按接口类型提供三个 REST 代理 Bean：
 * <ul>
 *   <li><b>aiming 进程</b>：无 kb 本地 Bean → REST 代理激活，走 kb 引擎端点（:18086）</li>
 *   <li><b>dccheng 进程</b>：classloader 只含 engine.kb 不含 engine.cognitive2
 *       → 本配置类不在 classpath → 不装载（dccheng 走 kb-impl @Service 本地 Bean，行为不变）</li>
 *   <li><b>gateway 进程</b>：同时扫 engine.kb + engine.cognitive2 →
 *       kb-impl @Service Bean 已存在 → @ConditionalOnMissingBean 抑制 REST 代理 → 零影响</li>
 * </ul>
 *
 * <p><b>降级策略</b>（铁律 §2.4-6 不阻断主流程）：每个方法 catch Exception →
 * log.warn + 返回空值（emptyList/emptyMap/null），不抛异常、不 break 调用方。
 *
 * <p><b>REST 端点对齐</b>（kb-engine-impl 现有 Controller）：
 * <pre>
 * KnowledgeGraphService  →  /api/v1/knowledge/{search|graph|nodes|path|neighbors|source}
 * KnowledgeRetrievalService →  /api/v1/knowledge/rag/rag
 * ComplianceRuleProvider  →  /api/v1/knowledge/compliance-rules
 * </pre>
 *
 * @author ECOS Cognitive Engine Team
 * @since 2026-09-21 (PMO-60 v2.0 P4 P0-1)
 */
@Configuration
public class KbRestClientConfig {

    private static final Logger log = LoggerFactory.getLogger(KbRestClientConfig.class);

    /**
     * KB 引擎 REST 基础 URL（aiming 进程内访问 kb 引擎，经 gateway :8080 转发或直连 dccheng :18086）。
     * <p>通过 {@code ecos.kb-base} 配置项覆盖，默认走 gateway。
     */
    @Value("${ecos.kb-base:http://localhost:8080/api/v1}")
    private String kbBase;

    /** REST 代理共享的 ObjectMapper（节点/规则/文章 JSON 反序列化）。 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 共享 RestTemplate（connect 3s / read 10s，与 EntityLinker 同款超时）。
     * <p>包内可见，供单元测试反射替换为 MockRestTemplate。
     */
    RestTemplate buildRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(10000);
        return new RestTemplate(factory);
    }

    // ═══════════════ Bean 1: KnowledgeGraphService ═══════════════

    /**
     * KnowledgeGraphService REST 代理 Bean。
     *
     * <p>{@code @ConditionalOnMissingBean(KnowledgeGraphService.class)}：
     * 当 dccheng 本地 kb-impl @Service Bean 或 gateway 中 kb Bean 存在时不装载。
     *
     * @return KnowledgeGraphService REST 代理实现
     */
    @Bean("kbGraphRestService")
    @ConditionalOnMissingBean(KnowledgeGraphService.class)
    public KnowledgeGraphService kbGraphRestService() {
        RestTemplate rt = buildRestTemplate();
        return new KnowledgeGraphService() {

            @Override
            @SuppressWarnings("unchecked")
            public Map<String, Object> getGraph(String domain) {
                try {
                    String url = domain != null
                            ? kbBase + "/knowledge/graph?domain=" + enc(domain)
                            : kbBase + "/knowledge/graph";
                    Map<String, Object> resp = rt.getForObject(url, Map.class);
                    Map<String, Object> data = extractData(resp, "getGraph");
                    return data != null ? data : Collections.emptyMap();
                } catch (Exception e) {
                    log.warn("KB REST getGraph 失败: domain={}, error={}", domain, e.getMessage());
                    return Collections.emptyMap();
                }
            }

            /**
             * 4 参 getGraph REST 代理 — PMO-C T3 接口扩展兼容（categoryIds 暂不参与 REST 过滤，
             * 降级到 1 参全量 + catalog 端点未覆盖时无差别）。
             */
            @Override
            @SuppressWarnings("unchecked")
            public Map<String, Object> getGraph(String domain, List<String> categoryIds) {
                log.debug("KB REST getGraph 4-param 调用，降级到 1-param REST 代理: cats={}",
                        categoryIds == null ? 0 : categoryIds.size());
                return getGraph(domain);
            }

            @Override
            public Map<String, Object> getNodeDetail(String nodeId) {
                try {
                    Map<String, Object> resp = rt.getForObject(
                            kbBase + "/knowledge/nodes/" + nodeId, Map.class);
                    Map<String, Object> data = extractData(resp, "getNodeDetail");
                    return data != null ? data : Collections.emptyMap();
                } catch (Exception e) {
                    log.warn("KB REST getNodeDetail 失败: nodeId={}, error={}", nodeId, e.getMessage());
                    return Collections.emptyMap();
                }
            }

            @Override
            @SuppressWarnings("unchecked")
            public List<KnowledgeNode> search(String query) {
                try {
                    Map<String, Object> resp = rt.getForObject(
                            kbBase + "/knowledge/search?q=" + enc(query), Map.class);
                    if (resp == null) {
                        log.warn("KB REST search 返回空 body: query={}", query);
                        return Collections.emptyList();
                    }
                    Object data = resp.get("data");
                    if (data == null) {
                        log.warn("KB REST search 返回 data=null: query={}", query);
                        return Collections.emptyList();
                    }
                    return objectMapper.convertValue(data, new TypeReference<List<KnowledgeNode>>() {});
                } catch (Exception e) {
                    log.warn("KB REST search 失败: query={}, error={}", query, e.getMessage());
                    return Collections.emptyList();
                }
            }

            @Override
            public Map<String, Object> getShortestPath(String sourceNodeId, String targetNodeId) {
                try {
                    Map<String, Object> resp = rt.getForObject(
                            kbBase + "/knowledge/path?s=" + enc(sourceNodeId) + "&t=" + enc(targetNodeId), Map.class);
                    Map<String, Object> data = extractData(resp, "getShortestPath");
                    return data != null ? data : Collections.emptyMap();
                } catch (Exception e) {
                    log.warn("KB REST getShortestPath 失败: s={}, t={}, error={}",
                            sourceNodeId, targetNodeId, e.getMessage());
                    return Collections.emptyMap();
                }
            }

            @Override
            public Map<String, Object> getNeighbors(String nodeId, int degree) {
                try {
                    Map<String, Object> resp = rt.getForObject(
                            kbBase + "/knowledge/neighbors/" + nodeId + "?d=" + degree, Map.class);
                    Map<String, Object> data = extractData(resp, "getNeighbors");
                    return data != null ? data : Collections.emptyMap();
                } catch (Exception e) {
                    log.warn("KB REST getNeighbors 失败: nodeId={}, error={}", nodeId, e.getMessage());
                    return Collections.emptyMap();
                }
            }

            @Override
            public KnowledgeNode createNode(String label, String nodeType, String description, String propertiesJson) {
                log.warn("KB REST createNode 不支持（aiming 进程只读），降级返回 null: label={}", label);
                return null;
            }

            @Override
            public KnowledgeEdge createEdge(String sourceNodeId, String targetNodeId, String relationship, double weight) {
                log.warn("KB REST createEdge 不支持（aiming 进程只读），降级返回 null: s={}, t={}", sourceNodeId, targetNodeId);
                return null;
            }

            @Override
            public String getDataSource() {
                try {
                    Map<String, Object> resp = rt.getForObject(kbBase + "/knowledge/source", Map.class);
                    Map<String, Object> data = extractData(resp, "getDataSource");
                    if (data == null) {
                        return "unavailable";
                    }
                    Object source = data.get("source");
                    return source != null ? source.toString() : "unknown";
                } catch (Exception e) {
                    log.warn("KB REST getDataSource 失败: error={}", e.getMessage());
                    return "unavailable";
                }
            }
        };
    }

    // ═══════════════ Bean 2: KnowledgeRetrievalService ═══════════════

    /**
     * KnowledgeRetrievalService REST 代理 Bean。
     *
     * <p>{@code @ConditionalOnMissingBean(KnowledgeRetrievalService.class)}：
     * 当本地 kb-impl Bean 存在时不装载。
     *
     * @return KnowledgeRetrievalService REST 代理实现
     */
    @Bean("kbRetrievalRestService")
    @ConditionalOnMissingBean(KnowledgeRetrievalService.class)
    public KnowledgeRetrievalService kbRetrievalRestService() {
        RestTemplate rt = buildRestTemplate();
        return new KnowledgeRetrievalService() {

            @Override
            public Map<String, Object> getIndexStatus() {
                log.warn("KB REST getIndexStatus 暂无对应端点，降级返回空 Map");
                return Collections.emptyMap();
            }

            @Override
            public void triggerSync() {
                log.warn("KB REST triggerSync 不支持（aiming 进程只读），降级 noop");
            }

            @Override
            public List<Object> query(String queryText) {
                log.warn("KB REST query 暂无对应端点，降级返回空列表: query={}", queryText);
                return Collections.emptyList();
            }

            @Override
            @SuppressWarnings("unchecked")
            public Map<String, Object> ragQuery(String queryText, int topK, double threshold) {
                try {
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    Map<String, Object> body = Map.of(
                            "query", queryText != null ? queryText : "",
                            "topK", topK,
                            "threshold", threshold);
                    Map<String, Object> resp = rt.postForObject(
                            kbBase + "/knowledge/rag/rag",
                            new HttpEntity<>(body, headers), Map.class);
                    if (resp == null) {
                        log.warn("KB REST ragQuery 返回空 body: query={}", queryText);
                        return Collections.emptyMap();
                    }
                    return resp;
                } catch (Exception e) {
                    log.warn("KB REST ragQuery 失败: query={}, error={}", queryText, e.getMessage());
                    return Collections.emptyMap();
                }
            }

            /**
             * 5 参 ragQuery REST 代理 — PMO-B T1 接口扩展兼容（categoryIds/tags 暂不参与 REST 收窄，
             * 退化为 4 参全量检索；catalog 端点未覆盖时无差别降级）。
             */
            @Override
            public Map<String, Object> ragQuery(String queryText, int topK, double threshold,
                                                List<String> categoryIds, List<String> tags) {
                log.debug("KB REST ragQuery 5-param 调用，降级到 4 参 REST 代理: cats={}, tags={}",
                        categoryIds == null ? 0 : categoryIds.size(),
                        tags == null ? 0 : tags.size());
                return ragQuery(queryText, topK, threshold);
            }

            @Override
            public KnowledgeArticle createArticle(KnowledgeArticle article) {
                log.warn("KB REST createArticle 不支持（aiming 进程只读），降级返回 null");
                return null;
            }

            @Override
            public KnowledgeArticle getArticle(String articleId) {
                log.warn("KB REST getArticle 暂无对应端点，降级返回 null: id={}", articleId);
                return null;
            }

            @Override
            public List<KnowledgeArticle> searchArticles(String queryText, int limit) {
                log.warn("KB REST searchArticles 暂无对应端点，降级返回空列表: query={}", queryText);
                return Collections.emptyList();
            }
        };
    }

    // ═══════════════ Bean 3: ComplianceRuleProvider ═══════════════

    /**
     * ComplianceRuleProvider REST 代理 Bean。
     *
     * <p>{@code @ConditionalOnMissingBean(ComplianceRuleProvider.class)}：
     * 当本地 kb-impl Bean 存在时不装载。
     *
     * @return ComplianceRuleProvider REST 代理实现
     */
    @Bean("kbComplianceRuleRestService")
    @ConditionalOnMissingBean(ComplianceRuleProvider.class)
    public ComplianceRuleProvider kbComplianceRuleRestService() {
        RestTemplate rt = buildRestTemplate();
        return new ComplianceRuleProvider() {

            @Override
            public List<ComplianceRule> findByDomain(String domain) {
                List<ComplianceRule> all = fetchAllRules(rt);
                if (all.isEmpty()) {
                    return Collections.emptyList();
                }
                return all.stream()
                        .filter(r -> (domain == null || domain.equals(r.getDomain())) && r.isEnabled())
                        .collect(java.util.stream.Collectors.toList());
            }

            @Override
            public List<ComplianceRule> findAll() {
                return fetchAllRules(rt);
            }

            @Override
            public Optional<ComplianceRule> findById(String id) {
                try {
                    ComplianceRule resp = rt.getForObject(kbBase + "/knowledge/compliance-rules/" + id, ComplianceRule.class);
                    if (resp == null) {
                        log.warn("KB REST findById 返回空: id={}", id);
                        return Optional.empty();
                    }
                    return Optional.of(resp);
                } catch (Exception e) {
                    log.warn("KB REST findById 失败: id={}, error={}", id, e.getMessage());
                    return Optional.empty();
                }
            }
        };
    }

    // ═══════════════ 私有辅助 ═══════════════

    /**
     * 拉取全量合规规则（GET /knowledge/compliance-rules 返回 Map 包裹层，剥 data 段）。
     *
     * @param rt RestTemplate
     * @return 规则列表（失败返回空列表，不抛异常）
     */
    private List<ComplianceRule> fetchAllRules(RestTemplate rt) {
        try {
            Map<String, Object> resp = rt.getForObject(
                    kbBase + "/knowledge/compliance-rules", Map.class);
            if (resp == null) {
                log.warn("KB REST findAll 返回空 body");
                return Collections.emptyList();
            }
            Object data = resp.get("data");
            if (data == null) {
                log.warn("KB REST findAll 返回 data=null");
                return Collections.emptyList();
            }
            return objectMapper.convertValue(data, new TypeReference<List<ComplianceRule>>() {});
        } catch (Exception e) {
            log.warn("KB REST findAll 失败: error={}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /** URL-encode 辅助（防参数字符破坏 URL）。 */
    private static String enc(String value) {
        if (value == null) {
            return "";
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * 从 Map 响应体中提取 data 段（统一剥 ApiResponse 包裹层）。
     *
     * @param map   Map 响应体
     * @param op    操作名（日志用）
     * @return data 段（Map 类型），无 data 时返回 null
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> extractData(Map<String, Object> map, String op) {
        if (map == null) {
            return null;
        }
        Object data = map.get("data");
        if (data instanceof Map) {
            return (Map<String, Object>) data;
        }
        log.debug("KB REST {} 响应无 data 段，返回 null", op);
        return null;
    }
}
