package com.chinacreator.gzcm.engine.ontology.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static com.chinacreator.gzcm.common.security.abac.AbacEvaluateResponseContract.KEY_ALLOW;
import static com.chinacreator.gzcm.common.security.abac.AbacEvaluateResponseContract.KEY_FALLBACK;
import static com.chinacreator.gzcm.common.security.abac.AbacEvaluateResponseContract.KEY_SOURCE;
import static com.chinacreator.gzcm.common.security.abac.AbacEvaluateResponseContract.VALUE_FALLBACK_DENY_OPA_DOWN;
import static com.chinacreator.gzcm.common.security.abac.AbacEvaluateResponseContract.VALUE_SOURCE_FALLBACK;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import com.chinacreator.gzcm.common.security.abac.AbacEvaluateResponseContract;
import com.chinacreator.gzcm.common.security.abac.AbacEvaluateResponseContract.Verdict;
import com.chinacreator.gzcm.common.security.rls.RlsExecutionContract;

/**
 * #142/B4-3e-12（差距 <b>D-B4-44</b> 本体操作授权腿）—— 客户端读数面不变式。
 *
 * <p>收口前 {@link SecurityEngineClient#evaluatePolicy} 用
 * {@link AbacEvaluateResponseContract#isExplicitAllow} 返回 {@code boolean}，
 * 于是「{@code source=fallback} 的兜底态应答」与「{@code allow=false} 的真策略拒绝」
 * 同值，调用方统一抛 403。本文件钉住分态后的读数：只有 {@code allow=false} 才是 DENY，
 * 其余四种"护栏没说话"一律 UNEVALUATED；消费侧的 403／503 分面见
 * {@code OntologyCandidateServiceTest}。</p>
 */
@DisplayName("D-B4-44 本体腿：SecurityEngineClient 裁决三态读数")
class OntologySecurityEngineClientVerdictTest {

    @Test
    @DisplayName("allow=false ⇒ DENY；四种不可用形态 ⇒ UNEVALUATED（与 DENY 必异值）")
    void denyAndUnevaluatedAreReadApart() {
        Verdict denied = with(envelope(Map.of(KEY_ALLOW, false)));
        assertEquals(Verdict.DENY, denied, "allow=false 是真策略判定");

        Map<String, Object> fallback = new LinkedHashMap<>();
        fallback.put(KEY_SOURCE, VALUE_SOURCE_FALLBACK);
        fallback.put(KEY_FALLBACK, VALUE_FALLBACK_DENY_OPA_DOWN);
        fallback.put(KEY_ALLOW, false);

        Map<String, Verdict> unevaluated = new LinkedHashMap<>();
        unevaluated.put("source=fallback 兜底态", with(envelope(fallback)));
        unevaluated.put("应答缺 data 段", with(Map.of("code", 0)));
        unevaluated.put("HTTP 200 + security 失败信封", with(Map.of(
                "code", RlsExecutionContract.SEC_UNAVAILABLE_HTTP,
                "message", RlsExecutionContract.SEC_UNAVAILABLE_CODE + ": 安全裁决服务不可用")));
        unevaluated.put("data 段无裁决键", with(envelope(Map.of("policyState", "POLICY_NOT_LOADED"))));

        for (Map.Entry<String, Verdict> e : unevaluated.entrySet()) {
            assertEquals(Verdict.UNEVALUATED, e.getValue(),
                    e.getKey() + " 属\"护栏没说话\"，禁读成策略 DENY（§4.7.2「不可用 ≠ 无权限」）");
            assertNotEquals(denied, e.getValue(),
                    e.getKey() + " 与 DENY 同值＝D-B4-44 折叠形态复发");
        }
    }

    @Test
    @DisplayName("客户端缺失／传输异常 ⇒ UNEVALUATED（布尔折叠时代这两支与 DENY 同值）")
    void missingClientAndTransportFailureAreUnevaluated() {
        assertEquals(Verdict.UNEVALUATED,
                new SecurityEngineClient("http://localhost:18081", 100, null)
                        .evaluatePolicy("ontology.candidate.approve", new HashMap<>()),
                "RestTemplate 未装配＝护栏缺席，不得读成策略拒绝");

        RestTemplate throwing = mock(RestTemplate.class);
        when(throwing.postForEntity(anyString(), any(), eq(Map.class)))
                .thenThrow(new IllegalStateException("connect refused"));
        assertEquals(Verdict.UNEVALUATED,
                new SecurityEngineClient("http://localhost:18081", 100, throwing)
                        .evaluatePolicy("ontology.candidate.approve", new HashMap<>()),
                "传输故障＝没拿到裁决，禁折叠成 403");
    }

    private static Map<String, Object> envelope(Map<String, Object> data) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("code", 0);
        resp.put("message", "ok");
        resp.put("data", data);
        return resp;
    }

    private static Verdict with(Map<String, Object> respBody) {
        RestTemplate rt = mock(RestTemplate.class);
        @SuppressWarnings("unchecked")
        ResponseEntity<Map> ok = ResponseEntity.ok(respBody);
        when(rt.postForEntity(anyString(), any(), eq(Map.class))).thenReturn(ok);
        return new SecurityEngineClient("http://localhost:18081", 100, rt)
                .evaluatePolicy("ontology.candidate.approve", new HashMap<>());
    }
}
