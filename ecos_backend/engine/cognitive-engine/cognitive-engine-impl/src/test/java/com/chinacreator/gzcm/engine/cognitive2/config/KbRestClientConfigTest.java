package com.chinacreator.gzcm.engine.cognitive2.config;

import com.chinacreator.gzcm.engine.kb.ComplianceRuleProvider;
import com.chinacreator.gzcm.engine.kb.KnowledgeGraphService;
import com.chinacreator.gzcm.engine.kb.KnowledgeRetrievalService;
import com.chinacreator.gzcm.runtime.access.endpoint.ServiceEndpointResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * KbRestClientConfig 单测（PMO-60 v2.0 P4 P0-1 验证 + F05-15 收口后装配）。
 *
 * <p>验证 3 个 REST 代理 Bean 工厂方法：
 * <ul>
 *   <li>返回非 null（Spring 构造器可注入）</li>
 *   <li>方法签名可调用（接口契约完整实现）</li>
 *   <li>降级路径：REST 不可达时返空值不抛异常（铁律 §2.4-6）</li>
 * </ul>
 *
 * <p>不依赖 Spring 上下文（new KbRestClientConfig 直接测工厂方法）；F05-15 起客户端
 * 与寻址由调用方注入（共享 RestTemplate bean + ObjectProvider&lt;ServiceEndpointResolver&gt;），
 * 测试以 mock ObjectProvider（getIfAvailable()=null）驱动兜底基址。
 */
@DisplayName("KbRestClientConfig — KB REST 代理 Bean 工厂方法")
class KbRestClientConfigTest {

    private KbRestClientConfig config;
    private RestTemplate restTemplate;
    private ObjectProvider<ServiceEndpointResolver> emptyResolver;

    /** 设置 kbBase 字段（绕开 Spring @Value 注入）。 */
    private void setKbBase(Object target, String value) throws Exception {
        Field f = KbRestClientConfig.class.getDeclaredField("kbBase");
        f.setAccessible(true);
        f.set(target, value);
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        config = new KbRestClientConfig();
        restTemplate = new RestTemplate();
        emptyResolver = Mockito.mock(ObjectProvider.class);
        // 指向 localhost 一个不存在的端口，确保 REST 不可达 → 走降级路径
        setKbBase(config, "http://localhost:11999/api/v1");
    }

    // ── Bean 1: KnowledgeGraphService ──────────────────────────

    @Test
    @DisplayName("T1: kbGraphRestService() 返回非 null（可注入 CausalDetector）")
    void kbGraphRestServiceNotNull() {
        KnowledgeGraphService svc = config.kbGraphRestService(restTemplate, emptyResolver);
        assertNotNull(svc, "KnowledgeGraphService Bean 必须非 null");
    }

    @Test
    @DisplayName("T2: search REST 不可达 → 降级返回 emptyList（不抛异常）")
    void searchDegradedReturnsEmptyList() {
        KnowledgeGraphService svc = config.kbGraphRestService(restTemplate, emptyResolver);
        List<?> result = svc.search("test-metric");
        assertNotNull(result, "search 降级不可返回 null");
        assertTrue(result.isEmpty(), "REST 不可达时 search 应返 emptyList");
    }

    @Test
    @DisplayName("T3: getNodeDetail REST 不可达 → 降级返回 emptyMap（不抛异常）")
    void getNodeDetailDegradedReturnsEmptyMap() {
        KnowledgeGraphService svc = config.kbGraphRestService(restTemplate, emptyResolver);
        Map<String, Object> result = svc.getNodeDetail("node-001");
        assertNotNull(result, "getNodeDetail 降级不可返回 null");
        assertTrue(result.isEmpty(), "REST 不可达时 getNodeDetail 应返 emptyMap");
    }

    @Test
    @DisplayName("T4: getGraph REST 不可达 → 降级返回 emptyMap（不抛异常）")
    void getGraphDegradedReturnsEmptyMap() {
        KnowledgeGraphService svc = config.kbGraphRestService(restTemplate, emptyResolver);
        Map<String, Object> result = svc.getGraph("finance");
        assertNotNull(result, "getGraph 降级不可返回 null");
        assertTrue(result.isEmpty(), "REST 不可达时 getGraph 应返 emptyMap");
    }

    @Test
    @DisplayName("T5: getShortestPath + getNeighbors 降级对（不抛异常）")
    void pathAndNeighborsDegraded() {
        KnowledgeGraphService svc = config.kbGraphRestService(restTemplate, emptyResolver);
        assertNotNull(svc.getShortestPath("a", "b"), "getShortestPath 不可返回 null");
        assertNotNull(svc.getNeighbors("a", 1), "getNeighbors 不可返回 null");
    }

    // ── Bean 2: KnowledgeRetrievalService ──────────────────────

    @Test
    @DisplayName("T6: kbRetrievalRestService() 返回非 null（可注入 KnowledgeReasonerService）")
    void kbRetrievalRestServiceNotNull() {
        KnowledgeRetrievalService svc = config.kbRetrievalRestService(restTemplate, emptyResolver);
        assertNotNull(svc, "KnowledgeRetrievalService Bean 必须非 null");
    }

    @Test
    @DisplayName("T7: ragQuery REST 不可达 → 降级返回 emptyMap（不抛异常）")
    void ragQueryDegradedReturnsEmptyMap() {
        KnowledgeRetrievalService svc = config.kbRetrievalRestService(restTemplate, emptyResolver);
        Map<String, Object> result = svc.ragQuery("test-query", 5, 0.7);
        assertNotNull(result, "ragQuery 降级不可返回 null");
        assertTrue(result.isEmpty(), "REST 不可达时 ragQuery 应返 emptyMap（KnowledgeReasonerService 可处理）");
    }

    @Test
    @DisplayName("T8: query REST 不可达 → 降级返回 emptyList（不抛异常）")
    void queryDegradedReturnsEmptyList() {
        KnowledgeRetrievalService svc = config.kbRetrievalRestService(restTemplate, emptyResolver);
        List<?> result = svc.query("test");
        assertNotNull(result, "query 降级不可返回 null");
        assertTrue(result.isEmpty(), "REST 不可达时 query 应返 emptyList");
    }

    // ── Bean 3: ComplianceRuleProvider ─────────────────────────

    @Test
    @DisplayName("T9: kbComplianceRuleRestService() 返回非 null（可注入 CausalReasonerServiceImpl）")
    void kbComplianceRuleRestServiceNotNull() {
        ComplianceRuleProvider svc = config.kbComplianceRuleRestService(restTemplate, emptyResolver);
        assertNotNull(svc, "ComplianceRuleProvider Bean 必须非 null");
    }

    @Test
    @DisplayName("T10: findAll REST 不可达 → 降级返回 emptyList（不抛异常）")
    void findAllDegradedReturnsEmptyList() {
        ComplianceRuleProvider svc = config.kbComplianceRuleRestService(restTemplate, emptyResolver);
        List<?> result = svc.findAll();
        assertNotNull(result, "findAll 降级不可返回 null");
        assertTrue(result.isEmpty(), "REST 不可达时 findAll 应返 emptyList");
    }

    @Test
    @DisplayName("T11: findByDomain REST 不可达 → 降级返回 emptyList（不抛异常）")
    void findByDomainDegradedReturnsEmptyList() {
        ComplianceRuleProvider svc = config.kbComplianceRuleRestService(restTemplate, emptyResolver);
        List<?> result = svc.findByDomain("finance");
        assertNotNull(result, "findByDomain 降级不可返回 null");
        assertTrue(result.isEmpty(), "REST 不可达时 findByDomain 应返 emptyList");
    }

    @Test
    @DisplayName("T12: findById REST 不可达 → 降级返回 Optional.empty()（不抛异常）")
    void findByIdDegradedReturnsEmptyOptional() {
        ComplianceRuleProvider svc = config.kbComplianceRuleRestService(restTemplate, emptyResolver);
        var result = svc.findById("rule-001");
        assertNotNull(result, "findById 降级不可返回 null（应返 Optional.empty()）");
        assertTrue(result.isEmpty(), "REST 不可达时 findById 应返 Optional.empty()");
    }

    // ── 三 Bean 方法完整签名验证 ──────────────────────────────

    @Test
    @DisplayName("T13: 三 Bean 工厂方法全部非 null（Spring 构造器注入前提验证）")
    void allThreeBeansNotNull() {
        assertNotNull(config.kbGraphRestService(restTemplate, emptyResolver), "KnowledgeGraphService");
        assertNotNull(config.kbRetrievalRestService(restTemplate, emptyResolver), "KnowledgeRetrievalService");
        assertNotNull(config.kbComplianceRuleRestService(restTemplate, emptyResolver), "ComplianceRuleProvider");
    }

    @Test
    @DisplayName("T14: resolveKbBase — 显式覆盖项优先；resolver 不可得时 fail-loud（ECOS-COG-504，禁硬编码回落）")
    void resolveKbBasePriorityChain() throws Exception {
        // 1) 覆盖项优先
        setKbBase(config, "http://override-host:9999/api/v1");
        assertEquals("http://override-host:9999/api/v1", config.resolveKbBase(emptyResolver));
        // 2) 无覆盖 + resolver 不可用 → fail-loud 抛 BusinessException（F05-15/X-35：禁静默回落 localhost）
        setKbBase(config, "");
        assertThrows(com.chinacreator.gzcm.common.exception.BusinessException.class,
                () -> config.resolveKbBase(emptyResolver),
                "resolver 不可得时必须 fail-loud，而非回落硬编码基址");
        // 3) 无覆盖 + resolver 命中 route-manifest（dccheng MONOLITH）→ 解析基址 + /api/v1
        ObjectProvider<ServiceEndpointResolver> liveResolver = Mockito.mock(ObjectProvider.class);
        Mockito.when(liveResolver.getIfAvailable()).thenReturn(new ServiceEndpointResolver());
        String resolved = config.resolveKbBase(liveResolver);
        assertNotNull(resolved);
        assertTrue(resolved.endsWith("/api/v1"), "resolver 基址应补齐 /api/v1 前缀: " + resolved);
    }
}
