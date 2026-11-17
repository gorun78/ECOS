package com.chinacreator.gzcm.engine.cognitive2.service.blueprint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import com.chinacreator.gzcm.engine.cognitive2.dto.CognitiveBlueprintLayerVO;
import com.chinacreator.gzcm.runtime.access.endpoint.ServiceEndpointResolver;

/**
 * D151 契约门：认知六层蓝图聚合服务的真实口径。
 *
 * <p>钉住三条不变量（对应无假真理铁律 7 与 D151 台账）：</p>
 * <ol>
 *   <li>引擎 UP ⇒ 该层 verified=true、coverage=100、components 原样透传；</li>
 *   <li>引擎探活抛异常 ⇒ 该层 verified=false、coverage=0、reason 非空（<b>绝不编造覆盖数字</b>）；</li>
 *   <li>逐层隔离：单层失败不影响其余层，aggregate() 恒返回六层且顺序固定。</li>
 * </ol>
 */
class CognitiveBlueprintServiceTest {

    private static final String BASE = "http://gw:8080";

    @SuppressWarnings("unchecked")
    private RestTemplate rest() {
        return mock(RestTemplate.class);
    }

    private ServiceEndpointResolver resolver() {
        ServiceEndpointResolver r = mock(ServiceEndpointResolver.class);
        when(r.resolve(eq("gateway"), any())).thenReturn(Optional.of(BASE));
        return r;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private ResponseEntity<Map> okUp(Map<String, Object> components) {
        Map<String, Object> data = new HashMap<>();
        data.put("status", "UP");
        data.put("components", components);
        Map<String, Object> body = new HashMap<>();
        body.put("code", 0);
        body.put("data", data);
        return (ResponseEntity) ResponseEntity.ok(body);
    }

    @Test
    @DisplayName("引擎 UP ⇒ verified=true / coverage=100 / components 透传")
    @SuppressWarnings("unchecked")
    void upEngineYieldsVerifiedFullCoverage() {
        RestTemplate rest = rest();
        Map<String, Object> comps = new HashMap<>();
        comps.put("engine", "RUNNING");
        comps.put("db", "UP");
        when(rest.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(okUp(comps));

        CognitiveBlueprintService svc = new CognitiveBlueprintService(rest, resolver());
        List<CognitiveBlueprintLayerVO> layers = svc.aggregate();

        assertEquals(6, layers.size(), "aggregate() 必须恒返回六层");
        for (CognitiveBlueprintLayerVO l : layers) {
            assertTrue(l.isVerified(), "UP 引擎的层必须 verified=true: " + l.getId());
            assertEquals(100, l.getCoverage(), "UP 引擎的层 coverage 必须 100: " + l.getId());
            assertEquals("RUNNING", l.getComponents().get("engine"), "components 必须原样透传");
            assertNull(l.getReason(), "verified=true 时不应有 reason");
        }
        assertEquals("strategic", layers.get(0).getId(), "六层顺序固定，首层 strategic");
        assertEquals("security_infra", layers.get(5).getId(), "六层顺序固定，末层 security_infra");
    }

    @Test
    @DisplayName("探活抛异常 ⇒ verified=false / coverage=0 / reason 非空（不编造数字）")
    @SuppressWarnings("unchecked")
    void probeFailureYieldsUnverifiedZeroCoverage() {
        RestTemplate rest = rest();
        when(rest.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new org.springframework.web.client.ResourceAccessException("down"));

        CognitiveBlueprintService svc = new CognitiveBlueprintService(rest, resolver());
        List<CognitiveBlueprintLayerVO> layers = svc.aggregate();

        assertEquals(6, layers.size());
        for (CognitiveBlueprintLayerVO l : layers) {
            assertFalse(l.isVerified(), "探活异常层必须 verified=false: " + l.getId());
            assertEquals(0, l.getCoverage(), "探活异常层 coverage 必须 0（禁编造）: " + l.getId());
            assertTrue(l.getReason() != null && !l.getReason().isBlank(), "异常层必须有 reason: " + l.getId());
        }
    }

    @Test
    @DisplayName("引擎非 UP（DEGRADED）⇒ verified=true 但 coverage=0 且带原因")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void degradedEngineYieldsZeroCoverageWithReason() {
        RestTemplate rest = rest();
        Map<String, Object> data = new HashMap<>();
        data.put("status", "DEGRADED");
        data.put("components", Map.of("db", "UP"));
        Map<String, Object> body = new HashMap<>();
        body.put("code", 0);
        body.put("data", data);
        when(rest.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn((ResponseEntity) ResponseEntity.ok(body));

        CognitiveBlueprintService svc = new CognitiveBlueprintService(rest, resolver());
        List<CognitiveBlueprintLayerVO> layers = svc.aggregate();

        for (CognitiveBlueprintLayerVO l : layers) {
            assertFalse(l.isVerified(), "非 UP 引擎层 coverage 取 0，不得当真实满覆盖");
            assertEquals(0, l.getCoverage());
            assertTrue(l.getReason() != null && l.getReason().contains("DEGRADED"), "reason 应点名引擎状态");
        }
    }

    @Test
    @DisplayName("基址不可解析 ⇒ 六层全 unverified 且 reason 点名 route-manifest")
    @SuppressWarnings("unchecked")
    void unresolvableBaseYieldsAllUnverified() {
        RestTemplate rest = rest();
        ServiceEndpointResolver r = mock(ServiceEndpointResolver.class);
        when(r.resolve(eq("gateway"), any())).thenReturn(Optional.empty());

        CognitiveBlueprintService svc = new CognitiveBlueprintService(rest, r);
        List<CognitiveBlueprintLayerVO> layers = svc.aggregate();

        assertEquals(6, layers.size());
        for (CognitiveBlueprintLayerVO l : layers) {
            assertFalse(l.isVerified());
            assertEquals(0, l.getCoverage());
            assertTrue(l.getReason().contains("route-manifest"), "reason 应点名 route-manifest 缺登记");
        }
    }
}
