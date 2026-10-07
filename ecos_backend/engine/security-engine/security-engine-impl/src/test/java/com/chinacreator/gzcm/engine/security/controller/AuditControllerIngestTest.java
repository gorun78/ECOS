package com.chinacreator.gzcm.engine.security.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.sysman.audit.model.AuditEvent;
import com.chinacreator.gzcm.sysman.audit.service.IAuditLogService;
import com.chinacreator.gzcm.sysman.iam.context.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AuditControllerIngestTest — 审计摄取端点的主体来源与回显清零。
 *
 * <p>对应 H10-T2 / T2-1c。本文件**不套 §9.87 的 Kafka 出口四件套**：
 * 该 Controller 自己就是审计写入面（实测落 td_audit_log），再加一条
 * ecos.audit 出口会形成自写自读的环。故本节只覆盖可安全落地的两点
 * （旁录已认证主体 + 异常细节不回显），主体信任模型本身待裁 Q15。
 */
@ExtendWith(MockitoExtension.class)
class AuditControllerIngestTest {

    @Mock
    private IAuditLogService auditLogService;

    private AuditController controller;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        controller = new AuditController();
        ReflectionTestUtils.setField(controller, "auditLogService", auditLogService);
        request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.9");
        request.addHeader("User-Agent", "ecos-gateway/1.0");
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private static void authenticate(String userId, String username) {
        UserContext context = new UserContext();
        context.setUserId(userId);
        context.setUsername(username);
        UserContext.setCurrent(context);
    }

    private static Map<String, Object> body(String userId, String action) {
        Map<String, Object> body = new HashMap<>();
        body.put("userId", userId);
        body.put("action", action);
        body.put("resource", "dataset:1");
        body.put("result", "SUCCESS");
        body.put("detail", "d");
        return body;
    }

    private AuditEvent captureLoggedEvent() {
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLogService).log(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("旁录的 ingestActor 取已认证上下文，非硬编码")
    void ingestActorComesFromAuthenticatedContext() {
        authenticate("u-21", "alice");
        assertEquals("u-21", controller.resolveIngestActor());
    }

    @Test
    @DisplayName("无上下文时 ingestActor=anonymous，不伪装 admin/system")
    void missingContextYieldsAnonymousIngestActor() {
        String actor = controller.resolveIngestActor();
        assertEquals(AuditController.ANONYMOUS_OPERATOR, actor);
        assertNotEquals("admin", actor);
        assertNotEquals("system", actor);
    }

    @Test
    @DisplayName("userId 空白时退回 username")
    void ingestActorFallsBackToUsername() {
        authenticate("  ", "bob");
        assertEquals("bob", controller.resolveIngestActor());
    }

    @Test
    @DisplayName("现状基线（待裁 Q15）：入库主体仍是调用方自报，IP/UA 由服务端取")
    void writeLogStoresClaimedSubjectButServerDerivedNetworkFacts() {
        authenticate("u-21", "alice");

        ApiResponse<Map<String, Object>> resp = controller.writeLog(body("claimed-attacker-id", "export"), request);

        assertTrue(resp.isSuccess());
        AuditEvent event = captureLoggedEvent();
        // 自报主体原样入库 ⇒ 可伪造面，Q15 裁决后本断言必须改为"以 token 主体覆盖"
        assertEquals("claimed-attacker-id", event.getUserId());
        // 网络事实不可由客户端供给
        assertEquals("203.0.113.9", event.getIpAddress());
        assertEquals("ecos-gateway/1.0", event.getUserAgent());
        assertNotNull(event.getEventId());
        assertNotNull(event.getTimestamp());
        assertEquals("export", event.getEventType());
    }

    @Test
    @DisplayName("额外请求体字段原样透传进 details（Q15 需一并收紧的写入面）")
    void extraBodyFieldsPassThroughIntoDetails() {
        Map<String, Object> body = body("u-1", "login");
        body.put("injectedKey", "injectedValue");

        controller.writeLog(body, request);

        AuditEvent event = captureLoggedEvent();
        Map<String, Object> details = event.getDetails();
        assertEquals("injectedValue", details.get("injectedKey"));
        assertEquals("d", details.get("detail"));
        assertFalse(details.containsKey("userId"));
    }

    @Test
    @DisplayName("B4: details.ingestActor 持久化已认证主体（服务端字段，无论 detail 是否提供都存在）")
    void detailsIngestActorCapturesAuthenticatedSubject() {
        authenticate("u-21", "alice");

        controller.writeLog(body("u-1", "export"), request);

        AuditEvent event = captureLoggedEvent();
        Map<String, Object> details = event.getDetails();
        assertNotNull(details);
        assertEquals("u-21", details.get("ingestActor"), "服务端已认证主体写入 details");
    }

    @Test
    @DisplayName("B4: detail 为 null 时 details 仍存在（只含 ingestActor，非空引用）")
    void detailsAlwaysPresentEvenWhenDetailNull() {
        authenticate("u-9", "carol");

        Map<String, Object> body = body("u-1", "export");
        body.remove("detail");
        controller.writeLog(body, request);

        AuditEvent event = captureLoggedEvent();
        Map<String, Object> details = event.getDetails();
        assertNotNull(details, "B4 契约：details 非空调（至少含服务端字段）");
        assertEquals("u-9", details.get("ingestActor"));
    }

    @Test
    @DisplayName("B4: 客户端在 body 里伪造 ingestActor 会被剔除，服务端值内部覆盖（fail-closed）")
    void clientFakedIngestActorDoesNotOverwriteServerValue() {
        authenticate("u-21", "alice");
        Map<String, Object> body = body("u-1", "export");
        body.put("ingestActor", "self-reported-admin");

        controller.writeLog(body, request);

        AuditEvent event = captureLoggedEvent();
        Map<String, Object> details = event.getDetails();
        assertEquals("u-21", details.get("ingestActor"),
                "客户端伪造的 ingestActor 不得覆盖服务端已认证主体");
    }

    @Test
    @DisplayName("B4: 无认证上下文时 details.ingestActor=anonymous（不伪装 admin/system）")
    void detailsIngestActorDefaultsToAnonymousWhenNoContext() {
        Map<String, Object> body = body("u-1", "export");

        controller.writeLog(body, request);

        AuditEvent event = captureLoggedEvent();
        Map<String, Object> details = event.getDetails();
        assertEquals(AuditController.ANONYMOUS_OPERATOR, details.get("ingestActor"));
        assertNotEquals("admin", details.get("ingestActor"));
        assertNotEquals("system", details.get("ingestActor"));
    }

    @Test
    @DisplayName("写路径异常细节不回显客户端（N-25 本文件清零）")
    void writeFailureDoesNotLeakDetailToClient() {
        doThrow(new RuntimeException("jdbc:postgresql://10.0.0.7 password=Pa55"))
                .when(auditLogService).log(any());

        ApiResponse<Map<String, Object>> resp = controller.writeLog(body("u-1", "login"), request);

        assertFalse(resp.isSuccess());
        assertNotNull(resp.getMessage());
        assertFalse(resp.getMessage().contains("Pa55"));
        assertFalse(resp.getMessage().contains("jdbc"));
    }

    @Test
    @DisplayName("读路径（list/stats/verify）异常细节同样不回显")
    void readFailuresDoNotLeakDetailToClient() {
        when(auditLogService.query(any()))
                .thenThrow(new RuntimeException("token=eyJhbGciOi  leak-marker"));

        assertFalse(controller.list(null, null, null, null, null, 1, 20).getMessage().contains("leak-marker"));

        ApiResponse<Map<String, Object>> stats = controller.stats();
        assertFalse(stats.isSuccess());
        assertFalse(stats.getMessage().contains("leak-marker"));

        ReflectionTestUtils.setField(controller, "hashChainService", null);
        ApiResponse<Map<String, Object>> missing = controller.verifyIntegrity();
        assertTrue(missing.isSuccess());
        assertEquals(Boolean.FALSE, missing.getData().get("valid"));
    }

    @Test
    @DisplayName("服务未就绪时 list 仍返回空集而非 500（降级契约不变）")
    void listDegradesToEmptyPageWhenServiceMissing() {
        ReflectionTestUtils.setField(controller, "auditLogService", null);

        ApiResponse<Map<String, Object>> resp = controller.list("u-1", null, null, null, null, 1, 20);

        assertTrue(resp.isSuccess());
        assertEquals(0, resp.getData().get("total"));
        assertEquals(List.of(), resp.getData().get("data"));
    }
}
