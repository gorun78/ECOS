package com.chinacreator.gzcm.workspace.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * 编排执行客户端契约（2026-11-23 场景智能逻辑承接批）。
 *
 * <p>锁三面：① 请求路径/方法/凭证中继齐全；② 属主 {@code data} 段原样返回；
 * ③ 三类通道故障（不可达 / 缺 data / 业务码非 0）一律抛
 * {@link AipPipelineUnavailableException}（503 语义，禁合成成功）。</p>
 */
class AipPipelineRunClientTest {

    private final RestTemplate rest = mock(RestTemplate.class);
    private AipPipelineRunClient client;

    @BeforeEach
    void setUp() {
        client = new AipPipelineRunClient(rest);
    }

    @Test
    @DisplayName("成功：POST /aip/pipelines/{id}/execute，返回 data 段（含 degraded 原值）")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void postsAndUnwrapsData() {
        when(rest.exchange(eq("/aip/pipelines/p-1/execute"), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(new ResponseEntity(Map.of("code", 0, "data", Map.of(
                        "status", "executed", "degraded", true, "executedNodes", 2)), HttpStatus.OK));

        Map<String, Object> out = client.execute("p-1", Map.of("k", "v"));

        assertEquals("executed", out.get("status"));
        assertEquals(Boolean.TRUE, out.get("degraded"));
        ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(rest).exchange(eq("/aip/pipelines/p-1/execute"), eq(HttpMethod.POST), captor.capture(), eq(Map.class));
        assertEquals("v", ((Map<?, ?>) captor.getValue().getBody()).get("k"), "运行入参须原样透传");
    }

    @Test
    @DisplayName("业务码非 0 → 503 载体（不当成功）")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void nonZeroCodeIsUnavailable() {
        when(rest.exchange(any(String.class), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(new ResponseEntity(Map.of("code", 500, "message", "boom"), HttpStatus.OK));
        AipPipelineUnavailableException e = assertThrows(AipPipelineUnavailableException.class,
                () -> client.execute("p-1", null));
        assertEquals(503, e.getHttpStatus());
        assertTrue(e.getTechnicalReason().contains("500"));
    }

    @Test
    @DisplayName("应答缺 data 段 → 503；连接异常 → 503（带 cause）")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void missingDataAndNetworkErrorAreUnavailable() {
        when(rest.exchange(any(String.class), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(new ResponseEntity(Map.of("code", 0), HttpStatus.OK));
        assertThrows(AipPipelineUnavailableException.class, () -> client.execute("p-1", null));

        when(rest.exchange(any(String.class), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new RestClientException("connect refused"));
        AipPipelineUnavailableException e = assertThrows(AipPipelineUnavailableException.class,
                () -> client.execute("p-1", null));
        assertEquals(503, e.getHttpStatus());
        assertTrue(e.getCause() instanceof RestClientException, "cause 须保留供日志定位");
    }
}
