package com.chinacreator.gzcm.engine.cognitive2.config;

import com.chinacreator.gzcm.engine.cognitive2.config.KbRestClientConfig;
import com.chinacreator.gzcm.engine.kb.ComplianceRuleProvider;
import com.chinacreator.gzcm.engine.kb.KnowledgeGraphService;
import com.chinacreator.gzcm.engine.kb.KnowledgeRetrievalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * KbRestClientConfig 单测（PMO-60 v2.0 P4 P0-1 验证）。
 *
 * <p>验证 3 个 REST 代理 Bean 工厂方法：
 * <ul>
 *   <li>返回非 null（Spring 构造器可注入）</li>
 *   <li>方法签名可调用（接口契约完整实现）</li>
 *   <li>降级路径：REST 不可达时返空值不抛异常（铁律 §2.4-6）</li>
 * </ul>
 *
 * <p>不依赖 Spring 上下文（new KbRestClientConfig 直接测工厂方法）。
 */
@DisplayName("KbRestClientConfig — KB REST 代理 Bean 工厂方法")
class KbRestClientConfigTest {

    private KbRestClientConfig config;

    /** 设置 kbBase 字段（绕开 Spring @Value 注入）。 */
    private void setKbBase(Object target, String value) throws Exception {
        Field f = KbRestClientConfig.class.getDeclaredField("kbBase");
        f.setAccessible(true);
        f.set(target, value);
    }

    @BeforeEach
    void setUp() throws Exception {
        config = new KbRestClientConfig();
        // 指向 localhost 一个不存在的端口，确保 REST 不可达 → 走降级路径
        setKbBase(config, "http://localhost:11999/api/v1");
    }

    // ── Bean 1: KnowledgeGraphService ──────────────────────────

    @Test
    @DisplayName("T1: kbGraphRestService() 返回非 null（可注入 CausalDetector）")
    void kbGraphRestServiceNotNull() {
        KnowledgeGraphService svc = config.kbGraphRestService();
        assertNotNull(svc, "KnowledgeGraphService Bean 必须非 null");
    }

    @Test
    @DisplayName("T2: search REST 不可达 → 降级返回 emptyList（不抛异常）")
    void searchDegradedReturnsEmptyList() {
        KnowledgeGraphService svc = config.kbGraphRestService();
        List<?> result = svc.search("test-metric");
        assertNotNull(result, "search 降级不可返回 null");
        assertTrue(result.isEmpty(), "REST 不可达时 search 应返 emptyList");
    }

    @Test
    @DisplayName("T3: getNodeDetail REST 不可达 → 降级返回 emptyMap（不抛异常）")
    void getNodeDetailDegradedReturnsEmptyMap() {
        KnowledgeGraphService svc = config.kbGraphRestService();
        Map<String, Object> result = svc.getNodeDetail("node-001");
        assertNotNull(result, "getNodeDetail 降级不可返回 null");
        assertTrue(result.isEmpty(), "REST 不可达时 getNodeDetail 应返 emptyMap");
    }

    @Test
    @DisplayName("T4: getGraph REST 不可达 → 降级返回 emptyMap（不抛异常）")
    void getGraphDegradedReturnsEmptyMap() {
        KnowledgeGraphService svc = config.kbGraphRestService();
        Map<String, Object> result = svc.getGraph("finance");
        assertNotNull(result, "getGraph 降级不可返回 null");
        assertTrue(result.isEmpty(), "REST 不可达时 getGraph 应返 emptyMap");
    }

    @Test
    @DisplayName("T5: getShortestPath + getNeighbors 降级对（不抛异常）")
    void pathAndNeighborsDegraded() {
        KnowledgeGraphService svc = config.kbGraphRestService();
        assertNotNull(svc.getShortestPath("a", "b"), "getShortestPath 不可返回 null");
        assertNotNull(svc.getNeighbors("a", 1), "getNeighbors 不可返回 null");
    }

    // ── Bean 2: KnowledgeRetrievalService ──────────────────────

    @Test
    @DisplayName("T6: kbRetrievalRestService() 返回非 null（可注入 KnowledgeReasonerService）")
    void kbRetrievalRestServiceNotNull() {
        KnowledgeRetrievalService svc = config.kbRetrievalRestService();
        assertNotNull(svc, "KnowledgeRetrievalService Bean 必须非 null");
    }

    @Test
    @DisplayName("T7: ragQuery REST 不可达 → 降级返回 emptyMap（不抛异常）")
    void ragQueryDegradedReturnsEmptyMap() {
        KnowledgeRetrievalService svc = config.kbRetrievalRestService();
        Map<String, Object> result = svc.ragQuery("test-query", 5, 0.7);
        assertNotNull(result, "ragQuery 降级不可返回 null");
        assertTrue(result.isEmpty(), "REST 不可达时 ragQuery 应返 emptyMap（KnowledgeReasonerService 可处理）");
    }

    @Test
    @DisplayName("T8: query REST 不可达 → 降级返回 emptyList（不抛异常）")
    void queryDegradedReturnsEmptyList() {
        KnowledgeRetrievalService svc = config.kbRetrievalRestService();
        List<?> result = svc.query("test");
        assertNotNull(result, "query 降级不可返回 null");
        assertTrue(result.isEmpty(), "REST 不可达时 query 应返 emptyList");
    }

    // ── Bean 3: ComplianceRuleProvider ─────────────────────────

    @Test
    @DisplayName("T9: kbComplianceRuleRestService() 返回非 null（可注入 CausalReasonerServiceImpl）")
    void kbComplianceRuleRestServiceNotNull() {
        ComplianceRuleProvider svc = config.kbComplianceRuleRestService();
        assertNotNull(svc, "ComplianceRuleProvider Bean 必须非 null");
    }

    @Test
    @DisplayName("T10: findAll REST 不可达 → 降级返回 emptyList（不抛异常）")
    void findAllDegradedReturnsEmptyList() {
        ComplianceRuleProvider svc = config.kbComplianceRuleRestService();
        List<?> result = svc.findAll();
        assertNotNull(result, "findAll 降级不可返回 null");
        assertTrue(result.isEmpty(), "REST 不可达时 findAll 应返 emptyList");
    }

    @Test
    @DisplayName("T11: findByDomain REST 不可达 → 降级返回 emptyList（不抛异常）")
    void findByDomainDegradedReturnsEmptyList() {
        ComplianceRuleProvider svc = config.kbComplianceRuleRestService();
        List<?> result = svc.findByDomain("finance");
        assertNotNull(result, "findByDomain 降级不可返回 null");
        assertTrue(result.isEmpty(), "REST 不可达时 findByDomain 应返 emptyList");
    }

    @Test
    @DisplayName("T12: findById REST 不可达 → 降级返回 Optional.empty()（不抛异常）")
    void findByIdDegradedReturnsEmptyOptional() {
        ComplianceRuleProvider svc = config.kbComplianceRuleRestService();
        var result = svc.findById("rule-001");
        assertNotNull(result, "findById 降级不可返回 null（应返 Optional.empty()）");
        assertTrue(result.isEmpty(), "REST 不可达时 findById 应返 Optional.empty()");
    }

    // ── 三 Bean 方法完整签名验证 ──────────────────────────────

    @Test
    @DisplayName("T13: 三 Bean 工厂方法全部非 null（Spring 构造器注入前提验证）")
    void allThreeBeansNotNull() {
        assertNotNull(config.kbGraphRestService(), "KnowledgeGraphService");
        assertNotNull(config.kbRetrievalRestService(), "KnowledgeRetrievalService");
        assertNotNull(config.kbComplianceRuleRestService(), "ComplianceRuleProvider");
    }

    @Test
    @DisplayName("T14: buildRestTemplate 返回非 null（RestTemplate 工厂可复用）")
    void buildRestTemplateNotNull() {
        assertNotNull(config.buildRestTemplate(), "RestTemplate 工厂方法应返回非 null");
    }
}
