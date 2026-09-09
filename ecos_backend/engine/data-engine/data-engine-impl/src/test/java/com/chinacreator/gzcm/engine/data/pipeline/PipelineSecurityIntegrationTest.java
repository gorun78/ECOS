package com.chinacreator.gzcm.engine.data.pipeline;

import com.chinacreator.gzcm.common.exception.BusinessException;
import com.chinacreator.gzcm.common.exception.NotFoundException;
import com.chinacreator.gzcm.runtime.core.task.service.ITaskManagementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PipelineSecurityIntegrationTest — 架构铁律 §2.4「安全集成强制卡」三连：
 * 审计 / 脱敏 / ABAC 默认 DENY（Wave 4 T1，Coverage of P0 安全集成硬项）。
 *
 * <p>所有 security-engine REST 用 RestTemplate mock（java.lang.reflect 注入），
 * 不触网、不触 PG。
 */
@ExtendWith(MockitoExtension.class)
class PipelineSecurityIntegrationTest {

    @Mock
    private RestTemplate restTemplate;

    private PipelineSecurityService security;

    @BeforeEach
    void setUp() throws Exception {
        security = new PipelineSecurityService();
        setField(security, "securityBaseUrl", "http://test:8080");
        setField(security, "restTemplate", restTemplate);
    }

    /**
     * 通过 java.lang.reflect 注入私有字段（避免依赖 spring-test）。
     */
    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    // =============== 1. 写操作异步审计（§2.4 ⑤） ===============

    @Test
    @DisplayName("审计调用成功 — RestTemplate.postForObject 命中 /api/security/audit/log")
    void auditWriteSuccessCallsAuthEndpoint() {
        when(restTemplate.postForObject(
                org.mockito.ArgumentMatchers.eq("http://test:8080/api/security/audit/log"),
                org.mockito.ArgumentMatchers.anyMap(),
                org.mockito.ArgumentMatchers.eq(Object.class)))
                .thenReturn(Map.of("ok", true));

        security.auditWrite("PIPELINE_CREATE", "p-123", "user-x");

        // 关键断言：action name + resource id 走对了 security 端点
        verify(restTemplate).postForObject(
                eq("http://test:8080/api/security/audit/log"),
                argThat((Map<String, Object> m) ->
                        !"http".equals(m.get("action"))              // 非误字
                        && "PIPELINE_CREATE".equals(m.get("action"))
                        && "pipeline:p-123".equals(m.get("resource"))
                        && "user-x".equals(m.get("userId"))),
                eq(Object.class));
    }

    @Test
    @DisplayName("审计不阻塞 — 端点连接失败依然 swallow (WARN 级)，不影响主流程")
    void auditWriteFailureDoesNotBlock() {
        doThrow(new ResourceAccessException("connection refused"))
                .when(restTemplate).postForObject(anyString(), anyMap(), eq(Object.class));

        // 不抛异常 = 审计失败不阻塞；这是 §2.4（"不阻塞主流程铁律"）的底线
        security.auditWrite("PIPELINE_UPDATE", "p-123", "user-x");
    }

    // =============== 2. 敏感字段脱敏（§2.4 ③） ===============

    @Test
    @DisplayName("maskNodeConfig — password/token/apikey 等 7 个敏感键 → ******")
    void maskNodeConfigMasksAll7SensitiveKeys() {
        java.util.Map<String, Object> raw = new java.util.LinkedHashMap<>();
        raw.put("password", "p@ssw0rd");
        raw.put("token", "tok-abc");
        raw.put("secret", "sec-1");
        raw.put("apikey", "ak-1");
        raw.put("api_key", "ak-2");
        raw.put("authorization", "Bearer xyz");
        raw.put("credential", "cred-1");
        raw.put("host", "10.0.0.1");        // 非敏感，保留
        raw.put("nested", java.util.Map.of("password", "n-pw"));

        java.util.Map<String, Object> masked = new java.util.LinkedHashMap<>(
                security.maskNodeConfig(new java.util.HashMap<>(raw)));

        assertEquals("******", masked.get("password"));
        assertEquals("******", masked.get("token"));
        assertEquals("******", masked.get("secret"));
        assertEquals("******", masked.get("apikey"));
        assertEquals("******", masked.get("api_key"));
        assertEquals("******", masked.get("authorization"));
        assertEquals("******", masked.get("credential"));
        // 非敏感保留
        assertEquals("10.0.0.1", masked.get("host"));
        // 嵌套递归仍然脱敏
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> nested = (java.util.Map<String, Object>) masked.get("nested");
        assertEquals("******", nested.get("password"));
    }

    @Test
    @DisplayName("maskNodeConfig — 入参 null/空 → 返回空 Map（不抛 NPE）")
    void maskNodeConfigNullSafe() {
        assertTrue(security.maskNodeConfig(null).isEmpty());
        assertTrue(security.maskNodeConfig(java.util.Collections.emptyMap()).isEmpty());
    }

    @Test
    @DisplayName("parseAndMaskConfig — JSON 字符串 → 脱敏后的 Map（强类型化）")
    void parseAndMaskConfigParsesJsonAndMasks() {
        String json = "{\"sql\":\"SELECT 1\",\"password\":\"abc\",\"datasourceId\":\"ds1\"}";
        java.util.Map<String, Object> masked = security.parseAndMaskConfig(json);
        assertEquals("SELECT 1", masked.get("sql"));        // 保留
        assertEquals("******", masked.get("password"));     // 脱敏
        assertEquals("ds1", masked.get("datasourceId"));     // 保留
    }

    @Test
    @DisplayName("parseAndMaskConfig — 非法 JSON → 空 Map + WARN 不抛")
    void parseAndMaskConfigInvalidJson() {
        assertTrue(security.parseAndMaskConfig("not-json").isEmpty());
    }

    // =============== 3. ABAC 默认 DENY（§2.4 ⑥） ===============

    @Test
    @DisplayName("ABAC — RestTemplate Connection Refused → AllowedResult(false, SECURITY_ENGINE_UNAVAILABLE)")
    void abacDenyWhenSecurityEngineUnavailable() {
        doThrow(new org.springframework.web.client.ResourceAccessException("refused"))
                .when(restTemplate).postForObject(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyMap(),
                        org.mockito.ArgumentMatchers.eq(Object.class));

        PipelineSecurityService.AllowedResult r = security.evaluateExecute("p-1");
        assertFalse(r.allowed());
        assertEquals("SECURITY_ENGINE_UNAVAILABLE", r.reason());
    }

    @Test
    @DisplayName("ABAC — 响应 allow=false → AllowedResult(false, fallback/deny)")
    void abacDenyWhenPolicyDenies() {
        when(restTemplate.postForObject(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyMap(),
                org.mockito.ArgumentMatchers.eq(Object.class)))
                .thenReturn(java.util.Map.of("allow", false, "fallback", "POLICY_DENY"));

        PipelineSecurityService.AllowedResult r = security.evaluateExecute("p-2");
        assertFalse(r.allowed());
        assertEquals("POLICY_DENY", r.reason());
    }

    @Test
    @DisplayName("ABAC — 响应 allow=true → AllowedResult(true, ALLOW)")
    void abacAllowWhenPolicyAllows() {
        when(restTemplate.postForObject(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyMap(),
                org.mockito.ArgumentMatchers.eq(Object.class)))
                .thenReturn(java.util.Map.of("allow", true));

        PipelineSecurityService.AllowedResult r = security.evaluateExecute("p-3");
        assertTrue(r.allowed());
        // reason 取 fallback 字段（此处 null → 默认 ALLOW）
        assertEquals("ALLOW", r.reason());
    }

    // =============== 4. Controller 端 ABAC 拒绝路径 ===============

    @Test
    @DisplayName("Controller executeDefinition — ABAC DENY 时 submitTask 不被调用")
    void controllerExecuteAbacDenyBlocksPipelineSubmit() throws Exception {
        PipelineController controller = new PipelineController(
                mockSecurityDeny(), new PipelineRepository(mock(JdbcTemplate.class)),
                mock(ITaskManagementService.class),
                security,
                org.mockito.Mockito.mock(PipelineNodeTypesService.class));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> controller.executeDefinition("p-deny"));
        assertEquals(403, ex.getErrorCode(),
                "ABAC 拒绝必须以 403 业务码透出，实际 errorCode=" + ex.getErrorCode());
        assertTrue(ex.getMessage().contains("POLICY_DENY"),
                "ABAC 拒绝 message 应含 policy deny 原因，实际：" + ex.getMessage());
    }

    /**
     * 构造一个永远 DENY 的 PipelineService（仅用于 ABAC 拒绝路径）。
     */
    private PipelineService mockSecurityDeny() {
        PipelineService svc = mock(PipelineService.class);
        // checkAbacBeforeExecute 是 void，必须用 doThrow(...).when(svc).method()
        org.mockito.Mockito.doThrow(new BusinessException(403, "Pipeline 执行被 ABAC 策略拒绝: POLICY_DENY"))
                .when(svc).checkAbacBeforeExecute(anyString());
        return svc;
    }

    // =============== 5. ARCHIVED 定义查询 404 语义 ===============

    @Test
    @DisplayName("Controller listExecutions — ARCHIVED 定义被当不存在（404）")
    void listExecutionsArchivedDefinitionReturns404() throws Exception {
        PipelineService svc = org.mockito.Mockito.mock(PipelineService.class);
        PipelineController controller = new PipelineController(
                svc,
                new PipelineRepository(mock(JdbcTemplate.class)),
                mock(ITaskManagementService.class),
                security,
                org.mockito.Mockito.mock(PipelineNodeTypesService.class));

        PipelineDefinition archived = new PipelineDefinition();
        archived.setId("p-arch");
        archived.setName("old");
        archived.setStatus("ARCHIVED");
        when(svc.getDefinition("p-arch")).thenReturn(archived);

        NotFoundException ex = assertThrows(NotFoundException.class,
                () -> controller.listExecutions("p-arch", 1, 20));
        assertTrue(ex.getMessage().contains("已被删除"),
                "ARCHIVED 应被 404 透出，提示文案应含'已被删除'");
        // service.listExecutions 不应被调用
        org.mockito.Mockito.verify(svc, org.mockito.Mockito.never())
                .listExecutions(org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyInt(),
                        org.mockito.ArgumentMatchers.anyInt());
    }
}
